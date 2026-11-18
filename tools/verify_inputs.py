"""Identify a supported executable and the complete private Android disc payload."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import stat
import sys
import uuid

from build_progress import Progress

ROOT = Path(__file__).resolve().parents[1]
SECTOR = 2048
MIB = 1024 * 1024
DISC_MAGIC = b"MICROSOFT*XBOX*MEDIA"
REFERENCE = json.loads((ROOT / "tools" / "disc_fingerprints.json").read_text("utf-8"))
PACKER_SHA = "7e97536f978735d586a2f78327aa337b9a19b3e364fa715316fb42d07d33e0b7"


def file_hash(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def check_xbe(header, digest, edition):
    ref = REFERENCE[edition]
    if len(header) < 0x11c or header[:4] != b"XBEH":
        raise ValueError(f"{edition}: invalid Xbox executable header")
    offset = struct.unpack_from("<I", header, 0x118)[0] - struct.unpack_from("<I", header, 0x104)[0]
    if offset < 0 or offset + 12 > len(header):
        raise ValueError(f"{edition}: invalid XBE certificate address")
    title = struct.unpack_from("<I", header, offset + 8)[0]
    if f"{title:08X}" != ref["title"] or digest != ref["xbe"]:
        raise ValueError(f"{edition}: unsupported default.xbe (title {title:08X}, SHA-256 {digest})")


def payload_digest(rows):
    digest = hashlib.sha256()
    for name, count, sha in sorted(rows):
        digest.update(f"{name}\0{count}\0{sha}\n".encode("utf-8"))
    return digest.hexdigest()


def report_bytes(progress, done, total):
    if progress is not None:
        percent = 100 * done // max(total, 1)
        progress.update(percent, f"{done / MIB:.1f} / {total / MIB:.1f} MiB")


def inspect_image(path, edition, *, capture=None, file_extents=None, progress=None):
    size = path.stat().st_size
    with path.open("rb") as image:
        def read_at(offset, count):
            if min(offset, count) < 0 or offset + count > size:
                raise ValueError("XISO extent outside image")
            image.seek(offset)
            data = image.read(count)
            if len(data) != count:
                raise ValueError("incomplete XISO read")
            return data

        desc = read_at(32 * SECTOR, SECTOR)
        if desc[:20] != DISC_MAGIC or desc[-20:] != DISC_MAGIC:
            raise ValueError("expected a bare game-partition XISO at sector 32")
        stack = [("", *struct.unpack_from("<II", desc, 20))]
        visited, names, files = set(), set(), []
        while stack:
            folder, sector, count = stack.pop()
            if sector in visited or count > 16 * 1024 * 1024 or len(names) > 100000:
                raise ValueError("invalid directory tree")
            visited.add(sector)
            entries = read_at(sector * SECTOR, count)
            for start in range(0, count, SECTOR):
                off, end = start, min(start + SECTOR, count)
                while off + 14 <= end:
                    left, right, first, length, flags, n = struct.unpack_from("<HHIIBB", entries, off)
                    if (left == right == 0xffff) or n == 0:
                        break
                    if off + 14 + n > end:
                        raise ValueError("truncated XISO directory entry")
                    name = entries[off+14:off+14+n].decode("ascii").casefold()
                    if name in (".", "..") or any(c in name for c in "/\\:\0"):
                        raise ValueError("invalid XISO filename")
                    name = folder + name
                    if name in names:
                        raise ValueError("duplicate XISO entry")
                    names.add(name)
                    if flags & 0x10:
                        if length:
                            stack.append((name + "/", first, length))
                    else:
                        files.append((name, first * SECTOR, length))
                    off = (off + 14 + n + 3) & ~3
        if not any(name == "default.xbe" for name, _, _ in files):
            raise ValueError(f"{edition}: missing default.xbe in XISO")
        total = sum(length for _, _, length in files)
        rows, done = [], 0
        for name, offset, length in files:
            digest = hashlib.sha256()
            if capture is not None and name in capture:
                if length > MIB:
                    raise ValueError(f"oversized control texture: {name}")
                capture[name] = read_at(offset, length)
                digest.update(capture[name])
                done += length
                report_bytes(progress, done, total)
            else:
                for pos in range(0, length, MIB):
                    block = read_at(offset + pos, min(length - pos, MIB))
                    digest.update(block)
                    done += len(block)
                    report_bytes(progress, done, total)
            sha = digest.hexdigest()
            if name == "default.xbe":
                check_xbe(read_at(offset, min(length, 8192)), sha, edition)
            rows.append((name, length, sha))
            if file_extents is not None:
                file_extents[name] = (offset, length)
    return len(rows), payload_digest(rows)


def read_verified_files(disc, edition, names):
    """Return small private inputs only after checking the complete disc payload."""
    capture = dict.fromkeys(names)
    if len(capture) > 2:
        raise ValueError("the control skin expects at most two texture files")
    verified_disc(disc, edition, capture=capture, label="Verify disc for touch controls")
    for name, data in capture.items():
        if data is None:
            raise ValueError(f"missing control texture: {name}")
    return capture


def inspect_tree(folder, edition, *, progress=None):
    if not (folder / "default.xbe").is_file():
        raise ValueError(f"missing {folder / 'default.xbe'}")
    if not (folder / "media").is_dir():
        raise ValueError(f"missing {folder / 'media'} (supply the full extracted disc)")
    files, names = [], set()
    for entry in folder.rglob("*"):
        info = entry.lstat()
        if entry.is_symlink() or getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise ValueError(f"linked input is unsupported: {entry}")
        if entry.is_dir():
            continue
        name = entry.relative_to(folder).as_posix().casefold()
        if name in names:
            raise ValueError(f"duplicate input: {name}")
        names.add(name)
        files.append((entry, name, info.st_size))
    total = sum(size for _, _, size in files)
    rows, done = [], 0
    for entry, name, size in files:
        digest = hashlib.sha256()
        read = 0
        with entry.open("rb") as stream:
            while block := stream.read(MIB):
                digest.update(block)
                read += len(block)
                done += len(block)
                report_bytes(progress, done, total)
        if read != size:
            raise ValueError("The selected disc changed while it was being read.")
        sha = digest.hexdigest()
        if name == "default.xbe":
            with entry.open("rb") as stream:
                check_xbe(stream.read(8192), sha, edition)
        rows.append((name, size, sha))
    return len(rows), payload_digest(rows)


def verify_payload(result, edition, *, expected=None):
    ref = REFERENCE[edition]
    inventories = [ref, *ref.get("alternate_inventories", [])]
    if not any(result == (row["files"], row["payload_sha256"]) for row in inventories):
        raise ValueError(f"{edition}: disc files differ from the reference ({result[0]} entries, digest {result[1]}). "
                         f"The reference has {ref['files']} files. Select the complete extracted disc or use your original game-partition XISO.")
    if expected is not None and result != expected:
        raise ValueError(f"{edition}: the prepared copy differs from the selected disc inventory.")


def verified_disc(path, edition, *, expected=None, capture=None, file_extents=None, label=None):
    with Progress(label or f"Verify {edition.upper()} disc") as progress:
        progress.set_detail("Reading the file index")
        if path.is_dir():
            result = inspect_tree(path, edition, progress=progress)
        else:
            result = inspect_image(path, edition, capture=capture, file_extents=file_extents, progress=progress)
        verify_payload(result, edition, expected=expected)
        return result


def build_disc_image(tool, folder, output):
    with Progress("Create disc image", estimate_seconds=15):
        subprocess.run([str(tool), "-q", "-m", "-c", str(folder), str(output)], check=True,
                       stdout=sys.stderr)


def prepare_image(slot, edition, *, check):
    image, folder = slot / "disc.iso", slot / "disc"
    if image.is_file() and folder.exists():
        raise ValueError(f"ambiguous input: choose {image} or {folder}")
    if image.is_file():
        verified_disc(image, edition)
        return image
    if not folder.is_dir():
        raise ValueError(f"missing {image} or complete extracted tree at {folder}")
    selected = verified_disc(folder, edition)
    # Use a verified packer on an already verified full tree, then re-read the result.
    cache = ROOT / "build" / "discs" / edition
    if edition == "pal":
        cache = cache / selected[1][:12]
    out = cache / "disc.iso"
    if out.is_file():
        verified_disc(out, edition, expected=selected, label="Verify cached disc")
        return out
    tool = ROOT / "cache" / "tools" / "extract-xiso.exe"
    if not tool.is_file() or file_hash(tool) != PACKER_SHA:
        raise ValueError(f"missing verified packer {tool} (SHA-256 {PACKER_SHA}); alternatively supply disc.iso")
    if check:
        raise ValueError(f"input tree verified; run the build without --check to generate {out}")
    out.parent.mkdir(parents=True, exist_ok=True)
    work = out.parent / uuid.uuid4().hex
    work.mkdir()
    tmp = work / "disc.iso"
    try:
        build_disc_image(tool, folder, tmp)
        verified_disc(tmp, edition, expected=selected, label="Verify generated disc")
        tmp.replace(out)
    finally:
        tmp.unlink(missing_ok=True)
        work.rmdir()
    return out


def verify_profile(profile, check):
    slot = ROOT / "inputs" / profile
    if profile == "windows":
        xbe = slot / "default.xbe"
        if not xbe.is_file():
            raise ValueError(f"missing {xbe}; supply the extracted PAL disc including media/")
        with xbe.open("rb") as stream:
            check_xbe(stream.read(8192), file_hash(xbe), "pal")
        for name in ("media/sndtest.dds", "media/sseltex.mit"):
            if not (slot / name).is_file():
                raise ValueError(f"missing {slot / name}; supply the complete extracted PAL disc")
        return {}
    if profile == "android-original":
        return {"BLINX_PAL_DISC": str(prepare_image(slot, "pal", check=check))}
    return {"BLINX_" + edition.upper() + "_DISC": str(prepare_image(slot / edition, edition, check=check))
            for edition in ("pal", "demo")}
