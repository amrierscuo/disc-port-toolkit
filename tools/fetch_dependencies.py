"""Explicitly fetch the selected public source commits; no binary downloads."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

from build_progress import Progress

ROOT = Path(__file__).resolve().parents[1]
PINS = json.loads((ROOT / "third_party/pins.json").read_text("utf-8"))["sources"]


def git(folder, *args):
    env = {**os.environ, "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "never"}
    command = ["git", "-C", str(folder), "-c", "credential.helper=", *args]
    return subprocess.check_output(command, env=env, text=True).strip()


def fetch(name, check):
    spec = PINS[name]
    target = ROOT / "cache" / "src" / name
    if not target.exists():
        if check:
            raise ValueError(f"missing cache/src/{name} at {spec['commit']}")
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=target.parent) as scratch:
            candidate = Path(scratch) / "tree"
            candidate.mkdir()
            git(candidate, "init", "-q")
            with Progress(f"Download {name} sources", estimate_seconds=35):
                git(candidate, "fetch", "--quiet", "--depth=1", spec["url"], spec["commit"])
            with Progress(f"Check out {name} sources", estimate_seconds=5):
                git(candidate, "checkout", "--detach", "-q", spec["commit"])
                if git(candidate, "rev-parse", "HEAD") != spec["commit"]:
                    raise ValueError(f"{name}: fetched commit mismatch")
            candidate.rename(target)
    if (not (target / ".git").is_dir() or git(target, "rev-parse", "HEAD") != spec["commit"] or
            git(target, "status", "--porcelain", "--untracked-files=all")):
        raise ValueError(f"{name}: unexpected or changed source cache at {target}")
    print(f"{name} {spec['commit']}  cache/src/{name}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("names", nargs="+", choices=sorted(PINS))
    parser.add_argument("--check", action="store_true", help="inspect existing caches offline")
    args = parser.parse_args()
    try:
        for name in args.names:
            fetch(name, args.check)
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        parser.exit(2, f"{exc}\n")
