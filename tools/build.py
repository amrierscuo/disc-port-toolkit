"""Run the profile selected by a root build wrapper."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

from verify_inputs import ROOT, verify_profile
from fetch_dependencies import fetch
from start_disc import prepare_disc

PROFILES = {"windows": "windows-clone1", "android-original": "android-original", "android-full": "android-full"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=PROFILES)
    parser.add_argument("--check", action="store_true", help="preflight only")
    parser.add_argument("--disc", type=Path, help="external PAL disc path for Windows or Android Original")
    args, profile_options = parser.parse_known_args()
    if profile_options and args.profile != "windows":
        parser.error("unrecognized arguments: " + " ".join(profile_options))
    try:
        if args.profile == "windows":
            discs = {}
            if args.disc is not None:
                profile_options.extend(["--disc", str(args.disc.resolve())])
        elif args.disc is None:
            discs = verify_profile(args.profile, args.check)
        elif args.profile == "android-original":
            discs = {"BLINX_PAL_DISC": str(prepare_disc(args.profile, args.disc, check=args.check))}
        else:
            parser.error("--disc supports Windows and Android Original")
        if args.profile.startswith("android-") and not args.check:
            for name in ("hakux", "fex"):
                if not (ROOT / "cache" / "src" / name).exists():
                    fetch(name, check=False)
        entry = ROOT / "profiles" / PROFILES[args.profile] / "build.py"
        return subprocess.run([sys.executable, "-B", str(entry), *(["--check"] if args.check else []), *profile_options],
                              cwd=ROOT, env={**os.environ, **discs, "PYTHONDONTWRITEBYTECODE":"1"}).returncode
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"{args.profile}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
