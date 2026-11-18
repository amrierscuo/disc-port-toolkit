"""Resolve the user's PAL disc to the input required by a build profile."""
import argparse
from contextlib import contextmanager
import hashlib
import io
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile
from urllib.request import urlopen

from build_progress import Progress
from verify_inputs import MIB, PACKER_SHA, ROOT, build_disc_image, file_hash, report_bytes, verified_disc


PACKER_RELEASE = "build-202609111233"
PACKER_URL = (
    f"https://github.com/XboxDev/extract-xiso/releases/download/{PACKER_RELEASE}/"
    "extract-xiso-Win64_Release.zip"
)
PACKER_ZIP_SHA = "fc891dfa72f08e3838a2f8b7ec34a8e4d61678da1cc8fee879677f00bf114ce3"


@contextmanager
def staging_directory(parent):
    parent.mkdir(parents=True, exist_ok=True)
    stage = parent / (".t-" + uuid.uuid4().hex[:16])
    stage.mkdir()
    try:
        yield stage
    finally:
        if stage.exists():
            if stage.resolve().parent != parent.resolve() or stage.is_symlink():
                raise ValueError(f"Unexpected staging path: {stage}")
            shutil.rmtree(stage)


def publish_directory(stage, destination):
    """On Windows a directory rename publishes the complete result without replacing an existing one."""
    try:
        stage.rename(destination)
    except OSError:
        if not destination.is_dir():
            raise
        # A concurrent run may have won the rename. Callers verify the winner.


def get_packer():
    provided = ROOT / "cache/tools/extract-xiso.exe"
    if provided.is_file() and file_hash(provided) == PACKER_SHA:
        return provided
    folder = ROOT / "cache/start-here" / ("extract-xiso-" + PACKER_RELEASE)
    exe = folder / "extract-xiso.exe"
    if not folder.exists():
        with Progress("Download and verify the Xbox disc packer", estimate_seconds=5):
            with urlopen(PACKER_URL, timeout=90) as response:
                archive = response.read(23295 + 1)
            if hashlib.sha256(archive).hexdigest() != PACKER_ZIP_SHA:
                raise ValueError("The XboxDev release archive differs from the expected SHA-256.")
        with staging_directory(folder.parent) as stage, zipfile.ZipFile(io.BytesIO(archive)) as bundle:
            tool_bytes = bundle.read("artifacts/extract-xiso.exe")
            if hashlib.sha256(tool_bytes).hexdigest() != PACKER_SHA:
                raise ValueError("The disc packer differs from the expected SHA-256.")
            (stage / exe.name).write_bytes(tool_bytes)
            (stage / "LICENSE.TXT").write_bytes(bundle.read("artifacts/LICENSE.TXT"))
            publish_directory(stage, folder)
    if not exe.is_file() or file_hash(exe) != PACKER_SHA:
        raise ValueError(f"Repair the disc tool cache at {folder}, or select your PAL XISO.")
    return exe


def extract_verified_image(source, target, extents):
    # Entries come from a full inventory match against the supported PAL disc.
    total = sum(length for _, length in extents.values())
    done = 0
    with Progress("Extract disc files") as progress, source.open("rb") as image:
        for name, (offset, length) in extents.items():
            destination = target / name
            if not destination.resolve().is_relative_to(target.resolve()):
                raise ValueError(f"Invalid output path inside the disc: {name}")
            destination.parent.mkdir(parents=True, exist_ok=True)
            image.seek(offset)
            with destination.open("xb") as out:
                while length:
                    block = image.read(min(length, MIB))
                    if not block:
                        raise ValueError("The selected XISO changed while it was being read.")
                    out.write(block)
                    length -= len(block)
                    done += len(block)
                    report_bytes(progress, done, total)


def prepare_disc(profile, selected, *, check=False):
    source = selected.resolve(strict=True)
    if source.is_file() and source.name.casefold() == "default.xbe":
        source = source.parent
    tree = source.is_dir()
    if not tree and not source.is_file():
        raise ValueError("Select a PAL XISO or the folder containing default.xbe and media.")
    want_tree = profile == "windows"
    extents = {} if want_tree and not tree else None
    result = verified_disc(source, "pal", file_extents=extents)
    if tree == want_tree:
        return source

    # Keep room for game filenames on hosts with the traditional Win32 path limit.
    cache = ROOT / "build/discs" / ("pal-" + result[1][:12])
    folder = cache / ("extracted" if want_tree else "image")
    def verify_cached(location):
        prepared = location if want_tree else location / "disc.iso"
        verified_disc(prepared, "pal", expected=result, label="Verify prepared disc")
        return prepared

    if folder.exists():
        return verify_cached(folder)
    if check:
        raise ValueError("Disc verified. Run the build without --check to prepare the disc cache.")
    print("Preparing a private disc copy for this profile...", file=sys.stderr)
    with staging_directory(cache) as stage:
        if want_tree:
            extract_verified_image(source, stage, extents)
        else:
            build_disc_image(get_packer(), source, stage / "disc.iso")
        verify_cached(stage)
        publish_directory(stage, folder)
        if stage.exists():
            return verify_cached(folder)
    return folder if want_tree else folder / "disc.iso"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=("windows", "android-original"))
    parser.add_argument("disc", type=Path)
    args = parser.parse_args()
    try:
        print(prepare_disc(args.profile, args.disc))
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        sys.exit(f"Disc setup: {exc}")
