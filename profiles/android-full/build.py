"""Validate the Full 0.2.7 assembly and package only explicit private inputs."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_progress import Progress, stream_sha256
from verify_inputs import verified_disc

INPUTS = ROOT / "inputs/android-full"
CONTRACT = HERE / "dependencies/DEPENDENCY_INVENTORY.json"
DISCS = {
    "pal": {
        "bytes": 1018363904,
        "sha256": "16a98320a2435f1932c2dde35a8eb735d5dec3e1b1dd955231d1d78e60dd6551",
        "xbe_sha256": "3e4b51ed5e5a772b15e7d4112f12d37d89dd8bc77ad2528c89e287c32f43c1ab",
    },
    "demo": {
        "bytes": 672202752,
        "sha256": "d743eb9ecee49272f85f03be1ecd497a5c5ff4a266c30ad564b3923c5fa75a74",
        "xbe_sha256": "2a54363381adacbe8ea6362e8ae613e6049bb19928c6caa25c6b98ede521d26a",
    },
}
NATIVE_GROUP = "native_from_hakux_apk"
NATIVE_TARGETS = {"lib/arm64-v8a/libSDL3.so", "lib/arm64-v8a/libhakuxcore.so"}
SDK_TOOLS = ("build-tools/36.0.0/aapt2.exe", "build-tools/36.0.0/aapt.exe",
             "build-tools/36.0.0/zipalign.exe", "build-tools/36.0.0/lib/d8.jar",
             "build-tools/36.0.0/lib/apksigner.jar", "platforms/android-36/android.jar")


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_contract(rows):
    if len(rows) != 324:
        raise ValueError("Full inventory must contain exactly 324 rows.")
    targets = [row["target"] for row in rows]
    if len({name.casefold() for name in targets}) != 324:
        raise ValueError("Full inventory must contain 324 unique destination paths.")
    groups = [row["target"] for row in rows if row["group"] == NATIVE_GROUP]
    if len(groups) != 2 or set(groups) != NATIVE_TARGETS:
        raise ValueError("Full inventory must specify exactly the two expected ARM64 libraries.")
    for row in rows:
        for path in (row["target"], row.get("public_source")):
            if path is None:
                continue
            if not path or any(part in ("", ".", "..") for part in path.split("/")) or "\\" in path or ":" in path:
                raise ValueError("Unsafe inventory path: " + path)


def check_file(path, size, digest):
    if not path.is_file():
        return "missing"
    if path.stat().st_size != size:
        return "wrong size"
    if sha256(path) != digest:
        return "wrong SHA-256"
    return None


def input_files(rows):
    errors = []
    selected = {}
    resources = [row for row in rows if row["group"] != NATIVE_GROUP]
    with Progress("Scan Full resources") as progress:
        for i, row in enumerate(resources, 1):
            public = row.get("public_source")
            path = HERE / public if public else INPUTS / "resources" / row["target"]
            size = row.get("export_bytes", row["bytes"])
            expected = row.get("export_sha256", row["sha256"])
            error = check_file(path, size, expected)
            if error:
                errors.append(f"{error}: {path.relative_to(ROOT).as_posix()}")
            else:
                selected[row["target"]] = path
            progress.update(100 * i / len(resources), f"{i}/{len(resources)} files")
    return selected, errors


def tool_path(var, alternative, executables, problems):
    supplied = os.environ.get(var) or os.environ.get(alternative)
    if not supplied:
        problems.append("set " + var + " for " + executables[0])
        return None
    base = Path(supplied)
    for rel in executables:
        if not (base / rel).is_file():
            problems.append(f"missing: {var}/{rel}")
    return base


def copy_item(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)


def qualify_disc(path, edition):
    if not path.is_file():
        raise ValueError(f"missing: {path}")
    reference = DISCS[edition]
    size = path.stat().st_size
    with path.open("rb") as stream:
        actual = stream_sha256(stream, size, f"Read Full {edition} disc")
    # Exact historical images identify their payload directly. Every alternate layout
    # must match the independently recorded complete file inventory and embedded XBE.
    identical = (size, actual) == (reference["bytes"], reference["sha256"])
    if not identical:
        verified_disc(path, edition, label=f"Verify Full {edition} disc contents")
    return {"path": str(path.resolve()), "id": edition, "asset": f"discs/{edition}.iso",
            "bytes": size, "sha256": actual, "xbe_sha256": reference["xbe_sha256"],
            "historical_container": identical}


def bind_demo_locale(source, profile):
    # This checked Java source has two compile-time assertions about the installed Demo
    # container. Keep the XBE check and bind both assertions to the qualified input.
    old = DISCS["demo"]
    substitutions = [
        (f"static final long DISC_BYTES = {old['bytes']}L;", f"static final long DISC_BYTES = {profile['bytes']}L;"),
        (f'static final String DISC_SHA256 = "{old["sha256"]}";',
         f'static final String DISC_SHA256 = "{profile["sha256"]}";'),
    ]
    for before, after in substitutions:
        if source.count(before) != 1:
            raise ValueError("Unexpected DemoLocale source for the container binding.")
        source = source.replace(before, after)
    return source


def assemble(work, selected, rows, discs, native, sdk, jdk, keystore, alias):
    app = work / "app"
    runtime = work / "runtime"
    packaging = work / "packaging"
    notices = []
    with Progress("Stage Full resources") as progress:
        for i, row in enumerate(rows):
            progress.update(100 * i / len(rows), f"{i}/{len(rows)} files")
            target = row["target"]
            group = row["group"]
            if group == NATIVE_GROUP:
                name = PurePosixPath(target).name
                destination = runtime / "jniLibs/arm64-v8a" / name
                copy_item(native[name], destination)
                continue
            source = selected[target]
            if group == "license_notice":
                dest = runtime / target
                notices.append({"source": str(dest), "apk_entry": target, "sha256": row["sha256"]})
            elif group in ("sdl_java", "runtime_java"):
                subdir = "sdl_java" if group == "sdl_java" else "java"
                dest = runtime / subdir / target.removeprefix("src/")
            elif group == "installer_java":
                dest = packaging / "java" / target.removeprefix("src/")
            else:
                dest = app / target
            if target == "src/it/blinx/demo/v1/DemoLocale.java" and not discs["demo"]["historical_container"]:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(bind_demo_locale(source.read_text(encoding="utf-8"), discs["demo"]), encoding="utf-8")
            else:
                copy_item(source, dest)
    (runtime / "NOTICE_FILES.json").write_text(json.dumps(notices), encoding="utf-8")
    payloads = list(discs.values())
    metadata = packaging / "PAYLOADS.json"
    metadata.write_text(json.dumps({"profiles": payloads}), encoding="utf-8")
    builder = packaging / "build_apk.py"
    copy_item(HERE / "source/combined/packaging/build_apk.py", builder)
    copy_item(ROOT / "tools/build_progress.py", packaging / "build_progress.py")
    signed = packaging / "artifacts/blinx-full.apk"
    subprocess.run([sys.executable, "-B", str(builder), "--app", str(app),
                    "--runtime", str(runtime), "--sdk", str(sdk), "--jdk", str(jdk),
                    "--keystore", str(keystore), "--alias", alias, "--payloads", str(metadata),
                    "--output", str(signed)], check=True)
    return signed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate inputs without building or signing")
    args = parser.parse_args()
    rows = json.loads(CONTRACT.read_text(encoding="utf-8"))["effective_inputs"]
    validate_contract(rows)
    discs = {}
    problems = []
    for edition in DISCS:
        path = Path(os.environ.get("BLINX_" + edition.upper() + "_DISC", INPUTS / edition / "disc.iso"))
        try:
            discs[edition] = qualify_disc(path, edition)
        except (OSError, ValueError) as error:
            problems.append(str(error))
    selected, invalid = input_files(rows)
    problems.extend(invalid)
    sdk = tool_path("ANDROID_SDK_ROOT", "ANDROID_HOME", SDK_TOOLS, problems)
    jdk = tool_path("JAVA_HOME", "JDK_HOME", ("bin/java.exe", "bin/javac.exe"), problems)
    keystore = INPUTS / "signing/local.p12"
    alias = os.environ.get("BLINX_FULL_KEY_ALIAS")
    if not keystore.is_file():
        problems.append("missing: inputs/android-full/signing/local.p12 (your own signing key)")
    if not alias or not os.environ.get("BLINX_FULL_STOREPASS"):
        problems.append("set BLINX_FULL_KEY_ALIAS and BLINX_FULL_STOREPASS for your own key")
    sys.path.insert(0, str(ROOT / "profiles/android-original"))
    from native_inputs import require_native
    try:
        native = require_native(ROOT)
    except (ValueError, OSError, RuntimeError, subprocess.CalledProcessError) as error:
        problems.append(str(error))
    if problems:
        for item in problems[:5]:
            print(item, file=sys.stderr)
        if len(problems) > 5:
            print(f"{len(problems)} missing or invalid prerequisites in total. Resource list: {CONTRACT.name}.", file=sys.stderr)
            for item in problems[max(5, len(problems)-5):]:
                print(item, file=sys.stderr)
        return 2
    if args.check:
        print("Validated the Full contract, both edition images, source pins, ARM64 library hashes, assets and tool paths.")
        return 0
    if os.name != "nt":
        raise ValueError("This local Android packager requires Windows.")
    work = ROOT / "build/android-full" / uuid.uuid4().hex[:12]
    work.mkdir(parents=True)
    signed = assemble(work, selected, rows, discs, native, sdk, jdk, keystore, alias)
    output = ROOT / "dist/android-full/blinx-full.apk"
    output.parent.mkdir(parents=True, exist_ok=True)
    pending = output.with_name(work.name + ".apk.part")
    try:
        size = signed.stat().st_size
        with signed.open("rb") as stream, pending.open("wb") as out:
            digest = stream_sha256(stream, size, "Copy Full APK to dist", destination=out)
        with pending.open("rb") as stream:
            stream_sha256(stream, size, "Verify Full APK in dist", expected=digest)
        os.replace(pending, output)
    finally:
        pending.unlink(missing_ok=True)
    print("Created dist/android-full/blinx-full.apk for private use.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(2)
