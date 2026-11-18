"""Select the Full touch layout for the PAL-only app and verify its local art."""
from pathlib import Path
import hashlib
import json
import os

from generate_pad import generate_pad

SYMBOLS = "LT Back Up Left Right Down LS RT Start White Black Y B X A RS".split()
BITMAPS = ["assets/blinx_ui/touch_symbol_" + name + ".png" for name in SYMBOLS]
BITMAPS.extend(["assets/blinx_ui/touch_stick.png", "assets/blinx_ui/touch_thumb.png"])
FRAMES = ["res/drawable-xhdpi/launcher_" + name + ".9.png" for name in ("amber", "steel")]
FULL_PACKAGE = "package it.blinx.demo.v1;"
BASE_PACKAGE = "package org.blinx.android.base;"


def read_pad(root, disc, scratch):
    mode = os.environ.get("BLINX_ORIGINAL_PAD", "project").strip().lower()
    if mode == "neutral":
        print("Touch controls: neutral layout explicitly selected with BLINX_ORIGINAL_PAD=neutral.")
        return {}
    if mode != "project":
        raise ValueError("BLINX_ORIGINAL_PAD accepts project (default) or neutral.")
    original = root / "inputs/android-original/controls"
    full = root / "inputs/android-full/resources"
    directory = original if original.exists() else full
    if not directory.exists():
        artwork = generate_pad(disc, scratch)
        print("Touch controls: reproduced and verified all 20 Full control images from the PAL disc.")
        return artwork
    inventory = root / "profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json"
    records = {row["target"]: row for row in json.loads(inventory.read_bytes())["effective_inputs"]}
    verified = {}
    for name in BITMAPS + FRAMES:
        source = directory / name
        if not source.is_file():
            raise ValueError(f"Incomplete touch layout: missing {source}. Supply all 20 Full control resources.")
        content = source.read_bytes()
        row = records[name]
        if len(content) != row["bytes"] or hashlib.sha256(content).hexdigest() != row["sha256"]:
            raise ValueError(f"Unexpected touch artwork in {source}; expected SHA-256 {row['sha256']}")
        verified[name] = content
    print(f"Touch controls: verified Full artwork (16 buttons, 2 stick images, 2 frames) from {directory}.")
    return verified


def compile_inputs(root, scratch, pad, tools, run):
    """Return the Java inputs and AAPT2 link arguments for the chosen layout."""
    base = root / "profiles/android-original/app/src"
    sources = sorted(base.rglob("*.java"))
    if not pad:
        return sources, []
    full = root / "profiles/android-full/source"
    original_touch = base / "org/blinx/android/base/TouchControls.java"
    sources.remove(original_touch)
    adapted = scratch / "pad-java"
    adapted.mkdir()
    for source in (full / "combined/runtime/java/it/blinx/demo/v1/TouchControls.java",
                   full / "app/src/it/blinx/demo/v1/BlinxArt.java"):
        text = source.read_text("utf-8")
        if text.count(FULL_PACKAGE) != 1:
            raise ValueError(f"Unexpected source package in {source.name}")
        target = adapted / source.name
        target.write_text(text.replace(FULL_PACKAGE, BASE_PACKAGE), encoding="utf-8")
        sources.append(target)
    for name in FRAMES:
        target = scratch / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(pad[name])
    resources = scratch / "pad-resources.zip"
    run(tools["aapt2"], "compile", "--dir", scratch / "res", "-o", resources)
    generated = scratch / "resource-java"
    generated.mkdir()
    sources.append(generated / "org/blinx/android/base/R.java")
    return sources, ["--java", generated, resources]


def verify_packaged_pad(apk, pad):
    if not pad:
        return
    for name in BITMAPS:
        if apk.read(name) != pad[name]:
            raise ValueError(f"Packaged touch image differs: {name}")
    for name in FRAMES:
        filename = "/" + Path(name).name
        matches = [n for n in apk.namelist() if n.startswith("res/") and n.endswith(filename)]
        if len(matches) != 1 or b"npTc" not in apk.read(matches[0]):
            raise ValueError(f"Missing compiled Android nine-patch: {name}")
