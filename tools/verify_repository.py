"""Audit the physical contents of a source-only checkout, including ignored files."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = "PUBLICATION_MANIFEST.json"
PROOF = "provenance/LEGACY_ANCHOR.json.ots"
PROOF_DIGEST = "3ed2daea868cb1ca1ac0dbca012e8521745d58a637783ec27054d3f1c6142f5f"
ANCHOR_DIGEST = "1c1d35f426182f08c44ae28f0a4d272116446261e0a3360b2ce12a4048a989aa"
CODE_PREVIEW = "preview/code-verification.png"
CODE_PREVIEW_DIGEST = "952d4c2da95acff04f73a6fd7af017c9849275450b6c0c37d04f9c324bcc65ba"
PAL_PATCH_DIR = "profiles/windows-clone1/"
PAL_PATCHES = {"game-pal.patch": "game", "toolkit-pal.patch": "toolkit"}
TEXT = {".py", ".md", ".txt", ".cmd", ".json", ".java", ".xml", ".cpp", ".h", ".inc", ".rs",
        ".js", ".css", ".html", ".properties", ".kt", ".toml"}
EXACT = {".gitignore", ".gitattributes", ".gitmodules", "LICENSE", "COPYING", "NOTICE"}
LOCAL = {"build", "cache", "dist", "generated", "upstream"}
PRIVATE_PATH = re.compile(r"(?i)(?:[a-z]:[/\\]+(?:users|documents and settings)[/\\]+|/(?:home|Users)/[a-z0-9_.-]+/)")
SECRET = re.compile(r"-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def linked(path):
    return path.is_symlink() or bool(getattr(path.lstat(), "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)


def physical(root):
    result = []
    for folder, dirs, files in os.walk(root):
        at = Path(folder)
        if at == root and ".git" in dirs:
            dirs.remove(".git")
        if at == root and ".git" in files:
            files.remove(".git")
        for name in dirs + files:
            p = at / name
            if linked(p) or name.casefold() == ".git":
                raise ValueError(f"linked or nested Git path: {p.relative_to(root)}")
        for name in files:
            result.append((at / name).relative_to(root).as_posix())
    return sorted(result)


def safe_file(name, raw):
    p = Path(name)
    parts = name.split("/")
    if parts[0].casefold() in LOCAL or any(s.casefold() in {"__pycache__", ".github-private"} for s in parts):
        raise ValueError(f"private output: {name}")
    if parts[0] == "inputs" and name != "inputs/README.md" and not re.fullmatch(
            r"inputs/(?:windows|android-original|android-full/(?:pal|demo))/PLACE_[A-Z_]+_HERE\.txt", name):
        raise ValueError(f"private input: {name}")
    if name == PROOF:
        if sha(raw) != PROOF_DIGEST:
            raise ValueError("legacy proof differs")
        return
    if name == CODE_PREVIEW:
        if sha(raw) != CODE_PREVIEW_DIGEST:
            raise ValueError("reviewed source screenshot differs")
        return
    is_pal_patch = name.startswith(PAL_PATCH_DIR) and name[len(PAL_PATCH_DIR):] in PAL_PATCHES
    launcher_script = name in {"tools/start_here.ps1", "tools/start_dependencies.ps1",
                               "tools/start_android.ps1", "tools/start_progress.ps1"}
    if p.suffix.lower() not in TEXT and p.name not in EXACT and not is_pal_patch and not launcher_script:
        raise ValueError(f"unapproved file type: {name}")
    if name == "provenance/LEGACY_ANCHOR.json" and sha(raw) != ANCHOR_DIGEST:
        raise ValueError("legacy anchor differs")
    if b"\0" in raw:
        raise ValueError(f"binary content: {name}")
    body = raw.decode("utf-8-sig")
    if PRIVATE_PATH.search(body) or SECRET.search(body):
        raise ValueError(f"personal path or credential pattern in {name}")
    if p.suffix == ".py":
        ast.parse(body, name)
    elif p.suffix == ".json":
        json.loads(body)


def git_paths(root):
    process = subprocess.run(["git", "--no-optional-locks", "-C", str(root), "ls-files",
                              "--cached", "--others", "--exclude-standard", "-z"],
                             check=True, capture_output=True)
    return sorted(os.fsdecode(x) for x in process.stdout.split(b"\0") if x)


def audit(root, no_git=False):
    manifest = json.loads((root / MANIFEST).read_bytes())
    rows = manifest["files"]
    listed = {r["path"]: r for r in rows}
    if len(listed) != len(rows):
        raise ValueError("duplicate path in manifest")
    names = physical(root)
    if set(listed) | {MANIFEST} != set(names) or MANIFEST in listed:
        raise ValueError(f"manifest/physical file difference: {sorted((set(listed) | {MANIFEST}) ^ set(names))[:12]}")
    if not no_git and names != git_paths(root):
        raise ValueError("physical files differ from Git-visible files")
    for name in names:
        raw = (root / name).read_bytes()
        safe_file(name, raw)
        if name != MANIFEST:
            row = listed[name]
            if (len(raw) != row["size"] or sha(raw) != row["sha256"] or row["public_safe"] is not True
                    or any(not row.get(k) for k in ("category", "kind", "origin", "license", "reason"))):
                raise ValueError(f"file metadata mismatch: {name}")
    inventory = json.loads((root / "profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json").read_bytes())["effective_inputs"]
    if len(inventory) != 324 or len({x["target"] for x in inventory}) != 324 or sum(x["target"].endswith(".so") for x in inventory) != 2:
        raise ValueError("full profile inventory mismatch")
    upstream = "profiles/android-full/source/combined/runtime/provenance/upstream/"
    if sum(n.startswith(upstream) for n in names) != 22:
        raise ValueError("expected 22 public upstream notices/metadata files")
    pins = json.loads((root / "third_party/pins.json").read_bytes())["sources"]
    lock = json.loads((root / "profiles/android-original/NATIVE_SOURCE.lock.json").read_bytes())
    if pins["hakux"]["commit"] != lock["commit"] or pins["fex"]["commit"] != lock["fex_commit"]:
        raise ValueError("native source pin mismatch")
    if sha((root / "third_party/licenses/HAKUX_LICENSE.txt").read_bytes()) != "bcdb0da4a1742895011ba6f50bb7e3e0d10c72961490bc188d419964eae65b2b":
        raise ValueError("upstream license digest mismatch")
    pal_pins = json.loads((root / PAL_PATCH_DIR / "pins.json").read_bytes())
    if pal_pins["pal_sha256"] != json.loads((root / "tools/disc_fingerprints.json").read_bytes())["pal"]["xbe"]:
        raise ValueError("Windows PAL fingerprint differs from the disc inventory")
    for patch_name, upstream in PAL_PATCHES.items():
        if sha((root / PAL_PATCH_DIR / patch_name).read_bytes()) != pal_pins[upstream]["patch_sha256"]:
            raise ValueError(f"Clone 1 patch digest mismatch: {patch_name}")
    print(f"PASS: {len(names)} physical files, {len(rows)} file digests; manifest SHA-256 {sha((root / MANIFEST).read_bytes())}")
    return names


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-git", action="store_true", help="audit an exported source directory without Git")
    args = parser.parse_args()
    try:
        audit(ROOT, args.no_git)
    except (OSError, ValueError, KeyError, SyntaxError, subprocess.SubprocessError) as exc:
        sys.exit(f"FAIL: {exc}")
