"""Verify the public source revisions and historical ARM64 library fingerprints."""
import hashlib
import json
from pathlib import Path
import subprocess

LOCK = json.loads(Path(__file__).with_name("NATIVE_SOURCE.lock.json").read_text("utf-8"))


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git(directory, *arguments):
    result = subprocess.run(["git", "--no-optional-locks", "-C", str(directory), *arguments],
                            capture_output=True, text=True, errors="replace")
    if result.returncode:
        raise ValueError(f"Cannot verify Git checkout {directory.name}: {result.stderr.strip()}")
    return result.stdout.strip()


def require_native(root):
    """Return verified local libraries; never fetch, compile or substitute a binary."""
    source = root / "cache/src/hakux"
    fex = root / "cache/src/fex"
    for directory, revision in [(source, LOCK["commit"]), (fex, LOCK["fex_commit"])]:
        if not directory.is_dir():
            raise ValueError(f"Missing pinned source {directory.relative_to(root)} at {revision}")
        if (Path(git(directory, "rev-parse", "--show-toplevel")).resolve() != directory.resolve()
                or git(directory, "rev-parse", "HEAD") != revision):
            raise ValueError(f"Wrong pinned revision in {directory.name} (expected {revision})")
        if git(directory, "status", "--porcelain=v1", "--untracked-files=no", "--ignore-submodules=none"):
            raise ValueError(f"Local modifications in pinned source {directory.name}")
    link = git(source, "ls-tree", "HEAD", "external/fex").split()
    if link != ["160000", "commit", LOCK["fex_commit"], "external/fex"]:
        raise ValueError("Pinned hakuX tree references a different FEX commit")
    license_file = source / "LICENSE"
    if not license_file.is_file() or digest(license_file) != "bcdb0da4a1742895011ba6f50bb7e3e0d10c72961490bc188d419964eae65b2b":
        raise ValueError("Pinned hakuX LICENSE is missing or differs")
    libraries = {}
    for name, expected in LOCK["libraries"].items():
        path = root / "cache/native/arm64-v8a" / name
        if not path.is_file():
            raise ValueError(f"Missing {path.relative_to(root)} (SHA-256 {expected}); native source rebuild unverified")
        if digest(path) != expected:
            raise ValueError(f"Wrong SHA-256 for {name}, expected {expected}")
        libraries[name] = path
    return libraries
