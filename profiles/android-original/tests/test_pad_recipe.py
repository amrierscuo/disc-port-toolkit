"""Check the source-only controller recipe, optionally with a user's own verified PAL image."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import sys

PROFILE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROFILE))
from generate_pad import FRAME, FONT, generate_pad, render_pad


def check_generational_contract(disc):
    from PIL import Image
    result = generate_pad(disc, disc.parent)
    rows = json.loads((PROFILE.parent / "android-full" / "dependencies" / "DEPENDENCY_INVENTORY.json").read_bytes())["effective_inputs"]
    expected = {row["target"]: row for row in rows if row["target"].startswith("assets/blinx_ui/touch_")
                or row["target"] in ("res/drawable-xhdpi/launcher_amber.9.png", "res/drawable-xhdpi/launcher_steel.9.png")}
    if len(expected) != 20 or set(expected) != set(result):
        raise ValueError("Controller targets differ from the Full reference.")
    for path, data in result.items():
        if len(data) != expected[path]["bytes"] or hashlib.sha256(data).hexdigest() != expected[path]["sha256"]:
            raise ValueError(f"Controller fingerprint differs: {path}")
        if path.endswith(".9.png"):
            with Image.open(io.BytesIO(data)) as image:
                rgba = image.convert("RGBA")
                if rgba.size != (130, 130):
                    raise ValueError(f"Unexpected nine-patch size: {path}")
                for position in range(130):
                    for point, visible in (((position, 0), 45 <= position <= 84),
                                           ((0, position), 45 <= position <= 84),
                                           ((position, 129), 22 <= position <= 107),
                                           ((129, position), 22 <= position <= 107)):
                        expected_pixel = (0, 0, 0, 255) if visible else (0, 0, 0, 0)
                        if rgba.getpixel(point) != expected_pixel:
                            raise ValueError(f"Incorrect stretch or content guide in {path} at {point}")
    print("20 targets match the historical Full inventory byte-for-byte. Both nine-patch guide borders verified.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disc", type=Path)
    args = parser.parse_args()
    for bad in ({}, {FONT: b"invalid", FRAME: b"invalid"}):
        try:
            render_pad(bad)
        except ValueError as exc:
            if "Unsupported PAL controller texture" not in str(exc):
                raise
        else:
            raise ValueError("Unverified texture was accepted.")
    print("Missing and invalid textures rejected.")
    if args.disc is not None:
        check_generational_contract(args.disc)


if __name__ == "__main__":
    main()
