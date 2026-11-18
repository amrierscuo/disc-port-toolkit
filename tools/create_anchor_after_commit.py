"""Create an offline commit/tree receipt after the owner makes the first commit."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def main():
    try:
        commit = git("rev-parse", "--verify", "HEAD^{commit}").decode().strip()
        tree = git("rev-parse", "HEAD^{tree}").decode().strip()
        manifest = git("show", "HEAD:PUBLICATION_MANIFEST.json")
    except subprocess.CalledProcessError:
        raise SystemExit("A local commit containing PUBLICATION_MANIFEST.json is required.")
    out = ROOT / "build" / "anchors" / f"{commit}.json"
    out.parent.mkdir(exist_ok=True, parents=True)
    with out.open("x", encoding="utf-8") as stream:
        json.dump({"commit": commit, "tree": tree, "manifest_sha256": hashlib.sha256(manifest).hexdigest(),
                   "recorded_utc": datetime.now(timezone.utc).isoformat(),
                   "scope": "Local receipt only. No external timestamp requested."}, stream, indent=2)
    print(out)


if __name__ == "__main__":
    main()
