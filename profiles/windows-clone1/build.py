"""Regenerate and build the playable PAL Clone 1 privately, from pinned source trees."""
import argparse
import hashlib
from importlib.metadata import version
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import uuid
import zipfile


PROFILE = Path(__file__).resolve().parent
ROOT = PROFILE.parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_progress import Progress
from verify_inputs import verified_disc

PINS = json.loads((PROFILE / "pins.json").read_bytes())


def sha(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def tool(name, explicit):
    found = shutil.which(explicit or name)
    if not found:
        raise ValueError(f"Missing {name}. Add it to PATH or supply --{name}.")
    return Path(found).resolve()


def source_dir(name, options, check):
    pin = PINS[name]
    folder = options or ROOT / "cache/src" / ("clone1-" + name)
    folder = folder.resolve()

    def require_commit(repo, recovery):
        # Do not resolve a broken cache against a Git repository above it.
        env = {**os.environ, "GIT_CEILING_DIRECTORIES": str(repo.parent), "GIT_NO_LAZY_FETCH": "1"}
        result = subprocess.run(["git", "-C", repo, "cat-file", "-e", pin["commit"] + "^{commit}"],
                                env=env, capture_output=True)
        if result.returncode:
            raise ValueError(f"{name}: missing pinned commit {pin['commit']} at {repo}. {recovery}")

    existing_help = ("Supply a Git repository containing the pinned commit." if options else
                     "Move this cache aside and run the build again to fetch a fresh copy.")
    if folder.exists():
        require_commit(folder, existing_help)
        return folder
    if options or check:
        raise ValueError(f"Missing pinned {name} Git source: {folder}.")
    folder.parent.mkdir(parents=True, exist_ok=True)
    # The context owns only its new sibling directory. A failed fetch can be
    # retried without touching an existing cache or a user-supplied checkout.
    with tempfile.TemporaryDirectory(dir=folder.parent, prefix=".fetch-") as temporary:
        scratch = Path(temporary)
        if scratch.resolve().parent != folder.parent:
            raise ValueError(f"Unexpected Git staging directory: {scratch}")
        fresh = scratch / "repo"
        with Progress(f"Fetch Windows {name} sources", estimate_seconds=20):
            subprocess.run(["git", "clone", "--quiet", "--bare", "--filter=blob:none", pin["url"], fresh], check=True)
            require_commit(fresh, "Check the remote URL and pinned revision.")
        try:
            fresh.rename(folder)
        except OSError:
            # Another build may have published the same cache in the meantime.
            if not folder.is_dir():
                raise
            require_commit(folder, existing_help)
    return folder


def export(name, source, destination, job):
    archive = job / (name + ".zip")
    subprocess.run(["git", "-C", source, "archive", "--format=zip", "-o", archive,
                    PINS[name]["commit"]], check=True)
    with zipfile.ZipFile(archive) as content:
        content.extractall(destination)
    if name in ("game", "toolkit"):
        patch = PROFILE / (name + "-pal.patch")
        if sha(patch) != PINS[name]["patch_sha256"]:
            raise ValueError(f"{patch.name} does not match its pin")
        env = {**os.environ, "GIT_CEILING_DIRECTORIES": str(destination.parent)}
        for option in (["--check"], []):
            subprocess.run(["git", "apply", *option, str(patch)], cwd=destination, env=env, check=True)


def show_stage_line(label, line, progress):
    counter = re.match(r"^\[(\d+)/(\d+)\]", line)
    if counter and label in ("compile", "recomp"):
        done, total = map(int, counter.groups())
        if total and label == "compile":
            progress.update(100 * done / total, f"{done}/{total} Ninja steps")
        elif total and "Translating " in line:
            # The reference PAL run spent about half its time loading analysis before translation.
            progress.update(50 + 46 * done / total, f"Translating function {done}/{total}", estimated=True)
        return
    if label == "analyze":
        # These boundaries reflect the relative durations of the PAL analysis stages.
        milestones = {"== parse ==": (0, "Reading the Xbox executable"),
                      "== disasm ==": (1, "Disassembling the executable"),
                      "== funcid ==": (93, "Identifying functions"),
                      "== abi ==": (98, "Checking calling conventions")}
        if line in milestones:
            percent, detail = milestones[line]
            progress.update(percent, detail, estimated=True)
        elif line.startswith("Phase ") and ": " in line:
            detail = line.split(": ", 1)[1].split(" to ", 1)[0].rstrip(".")
            progress.set_detail(detail)
        else:
            section = re.match(r"^(\S+): 0x[\dA-Fa-f]+ \([\d.]+ KB\)\.\.\. (\d+)%$", line)
            if section:
                progress.set_detail(f"Scanning {section[1]} ({section[2]}% of section)")
    elif label == "recomp" and line.startswith("Loading data files"):
        progress.set_detail("Loading functions, symbols and analysis")
    elif label == "recomp" and line.startswith("Wrote ") and "recomp_" in line:
        progress.update(97, "Writing generated code", estimated=True)
    elif label == "configure" and line.startswith("-- "):
        progress.set_detail(line[3:].split(": ", 1)[0])


def run_stage(label, command, job, env):
    # PAL reference durations in seconds. Ninja supplies a measured count instead.
    stages = {"analyze": ("1/4 Analyze", 345), "recomp": ("2/4 Translate", 252),
              "configure": ("3/4 Configure", 4), "compile": ("4/4 Compile", None)}
    title, duration = stages[label]
    log = job / (label + ".log")
    with Progress(title, estimate_seconds=duration) as progress:
        with log.open("wb") as out, subprocess.Popen(
            command, cwd=job, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
        ) as child:
            try:
                # Preserve the original bytes in the live log. Disassembly uses carriage
                # returns for in-place section counters, while Ninja writes full lines.
                pending = b""
                while chunk := child.stdout.read1(16384):
                    out.write(chunk)
                    out.flush()
                    *complete, pending = re.split(br"[\r\n]", pending + chunk)
                    for raw in complete:
                        if raw:
                            show_stage_line(label, raw.decode("utf-8", "replace").strip(), progress)
                if pending:
                    show_stage_line(label, pending.decode("utf-8", "replace").strip(), progress)
                rc = child.wait()
            except BaseException:
                child.kill()
                raise
        if rc:
            with log.open("rb") as stream:
                stream.seek(max(0, log.stat().st_size - 3200))
                tail = stream.read().decode("utf-8", errors="replace")
            raise ValueError(f"{tail}\n{label} exited {rc}; see {log}")


def build(args):
    disc = (args.disc or ROOT / "inputs/windows/disc").resolve()
    if not (disc / "default.xbe").is_file() or not (disc / "media").is_dir():
        raise ValueError("Supply the complete PAL disc with default.xbe and media/ under inputs/windows/disc/.")
    verified_disc(disc, "pal")
    if sha(disc / "default.xbe") != PINS["pal_sha256"]:
        raise ValueError("PAL XBE SHA-256 does not match this profile")
    if os.name != "nt" or sys.version_info < (3, 12):
        raise ValueError("Use 64-bit Windows and Python 3.12 or newer")
    for name, expected in PINS["python_packages"].items():
        if version(name) != expected:
            raise ValueError(f"Install {name}=={expected} in the Python environment running this command")
    cmake = tool("cmake", args.cmake)
    ninja = tool("ninja", args.ninja)
    llvm = args.llvm or os.environ.get("LLVM_MINGW_ROOT")
    if not llvm:
        raise ValueError("Set LLVM_MINGW_ROOT or supply --llvm with the llvm-mingw toolchain directory")
    llvm = Path(llvm).resolve()
    cc = llvm / "bin/x86_64-w64-mingw32-clang.exe"
    if not cc.is_file():
        raise ValueError(f"Missing 64-bit Windows cross compiler: {cc}")
    sources = {name: source_dir(name, getattr(args, name), args.check)
               for name in ("game", "toolkit", "cli")}
    if args.check:
        print("Verified the PAL disc, build environment, and three pinned source commits.")
        return

    work = (args.work or ROOT / "build/windows-clone1").resolve()
    work.mkdir(parents=True, exist_ok=True)
    job = work / ("job-" + uuid.uuid4().hex[:12])
    job.mkdir()
    print(f"Private clean build: {job}", flush=True)
    game, toolkit, cli = job / "game", job / "toolkit", job / "cli"
    with Progress("Prepare Windows sources", estimate_seconds=8) as progress:
        for name, folder in (("game", game), ("toolkit", toolkit), ("cli", cli)):
            progress.set_detail(f"Exporting {name}")
            export(name, sources[name], folder, job)
    generated = game / "src/recomp/gen"
    if generated.exists() or (job / "obj").exists():
        raise ValueError("Guest C and previous objects must be absent at the start")
    (game / "game_files").mkdir(exist_ok=True)
    shutil.copyfile(disc / "default.xbe", game / "game_files/default.xbe")
    env = os.environ.copy()
    env.update({"PYTHONPATH": str(cli / "src"), "PYTHONDONTWRITEBYTECODE": "1",
                "PYTHONUTF8": "1", "PYTHONUNBUFFERED": "1", "XBOXRECOMP_PYTHON": sys.executable,
                "NINJA_STATUS": "[%f/%t] ",
                "GIT_CEILING_DIRECTORIES": str(job),
                "XBOXRECOMP_DIR": str(toolkit), "XDG_CACHE_HOME": str(job / "cache"),
                "LLVM_MINGW_ROOT": str(llvm)})
    commands = []

    def run(label, argv):
        command = [str(s) for s in argv]
        commands.append(command)
        run_stage(label, command, job, env)

    for stage in ("analyze", "recomp"):
        run(stage, [sys.executable, "-B", "-m", "xboxrecomp_cli.main", "--game", game, stage])
    objects = job / "obj"
    run("configure", [cmake, "-S", game, "-B", objects, "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
                      f"-DCMAKE_MAKE_PROGRAM={ninja}", f"-DLLVM_MINGW_ROOT={llvm}",
                      f"-DCMAKE_TOOLCHAIN_FILE={cli / 'src/xboxrecomp_cli/cmake/llvm-mingw-x86_64.cmake'}",
                      f"-DXBOXRECOMP_DIR={toolkit}", "-DXBOXRECOMP_ENHANCE=ON", "-DXBOXRECOMP_APU_DSP=OFF",
                      "-DXBOXRECOMP_LIBRASHADER=ON", "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"])
    run("compile", [cmake, "--build", objects, "--target", "blinx1_recomp", "--parallel", args.jobs])
    output = (args.output or ROOT / "dist/windows").resolve()
    output.mkdir(parents=True, exist_ok=True)
    exe = objects / "blinx1_recomp.exe"
    for f in (exe, objects / "config.default.toml"):
        with tempfile.NamedTemporaryFile(dir=output, delete=False) as temp, f.open("rb") as src:
            shutil.copyfileobj(src, temp)
        Path(temp.name).replace(output / f.name)
    guest_inputs = {p.name: sha(p) for p in sorted(generated.iterdir()) if p.suffix in (".c", ".h")}
    compiled_objects = [p for p in objects.rglob("*.obj")
                        if any(part.endswith(".dir") for part in p.relative_to(objects).parts)]
    receipt = {"pal_sha256": sha(game / "game_files/default.xbe"),
               "guest_source_sha256": guest_inputs, "inputs": PINS, "commands": commands,
               "compiler_sha256": sha(cc), "exe_sha256": sha(exe),
               "output_executable": str(output / exe.name),
               "compiled_units": len(compiled_objects),
               "configured_compile_commands": len(json.loads((objects / "compile_commands.json").read_bytes())),
               "clean_start": True, "reused_guest_or_host_objects": 0}
    if sha(output / exe.name) != receipt["exe_sha256"]:
        raise ValueError("Copied executable differs from the clean build")
    (job / "build-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(f"Built {output / exe.name}\nSHA-256 {receipt['exe_sha256']}\nReceipt {job / 'build-receipt.json'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Offline validation only")
    for key in ("disc", "game", "toolkit", "cli", "llvm", "work", "output"):
        parser.add_argument("--" + key, type=Path)
    parser.add_argument("--cmake")
    parser.add_argument("--ninja")
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    try:
        build(args)
    except (OSError, ValueError, subprocess.CalledProcessError, ImportError) as exc:
        parser.exit(2, f"{exc}\n")
