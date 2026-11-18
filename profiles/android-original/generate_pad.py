"""Re-create the private touch skin from two textures on the verified PAL disc.

The drawing and composition code is GPL-3.0-or-later. Rendered game-derived
glyphs and frames are for the user's local APK. No image data is embedded.
"""
import hashlib
import io
from pathlib import Path
import sys

PROFILE = Path(__file__).resolve().parent
TOOLS = PROFILE.parents[1] / "tools"
FONT = "media/font_cgothic_bold.dds"
FRAME = "media/h_waku_1a.dds"
TEXTURE_DIGESTS = {
    FONT: "b6607b89edde84b870b63b6db05c4212ffe09a2e8ad1502c1c3d04c4bdbba9f0",
    FRAME: "a8148eba2db0d997d6a9031c8e9f520f09c6e1a98a87c27660e6e6529d0a1339",
}
# SHA-256 over sorted target, byte-count and SHA-256 rows (tabs, LF).
TARGETS_DIGEST = "508f70d923b64e84909d1542cebec686f8ed69cf04eafabd6c160818816104a7"
LABELS = ("A", "B", "X", "Y", "Back", "Start", "LT", "RT", "LS", "RS")
# Left, top and right edges in the PAL font atlas; all glyph cells are 24 px high.
GLYPHS = {
    "A": (20, 48, 38), "B": (39, 48, 52), "L": (182, 48, 192),
    "R": (34, 72, 48), "S": (48, 72, 59), "T": (59, 72, 71),
    "X": (124, 72, 140), "Y": (140, 72, 155), "a": (6, 96, 22),
    "c": (38, 96, 53), "k": (137, 96, 150), "r": (31, 120, 39), "t": (50, 120, 58),
}


def require_pillow():
    try:
        import PIL
        from PIL import Image, ImageDraw, ImageOps
    except ImportError as exc:
        raise ValueError("Pad generation requires Pillow 12.0.0 (or supply the 20 verified private pad images).") from exc
    if PIL.__version__ != "12.0.0":
        raise ValueError(f"Pad generator tested with Pillow 12.0.0; found {PIL.__version__}. Use the pinned package or supply the private pad images.")
    return Image, ImageDraw, ImageOps


def make_symbols(font, image, draw):
    symbols = {}
    for label in LABELS:
        letters = []
        for char in label:
            left, top, right = GLYPHS[char]
            letters.append(font.crop((left, top, right, top + 24)))
        glyph = image.new("RGBA", (sum(letter.width + 1 for letter in letters), 24))
        x = 0
        for letter in letters:
            glyph.alpha_composite(letter, (x, 0))
            x += letter.width + 1
        symbols[label] = glyph.crop(glyph.getchannel("A").getbbox())
    for name, color in (("White", "#f4efe0"), ("Black", "#15202b")):
        glyph = image.new("RGBA", (96, 96))
        draw.Draw(glyph).ellipse((8, 8, 87, 87), fill=color, outline="#d5dbe1", width=8)
        symbols[name] = glyph.resize((24, 24), image.Resampling.LANCZOS)
    arrow = image.new("RGBA", (96, 96))
    draw.Draw(arrow).polygon(((48, 14), (86, 66), (65, 66), (65, 84), (31, 84),
                              (31, 66), (10, 66)), fill="#f4efe0")
    for name, rotation in (("Up", 0), ("Left", 90), ("Down", 180), ("Right", 270)):
        symbols[name] = arrow.rotate(rotation).resize((24, 24), image.Resampling.LANCZOS)
    layers = {}
    for name, original in symbols.items():
        glyph = original.crop(original.getchannel("A").getbbox())
        bound = (150, 106) if name in ("Start", "Back") else (114, 114)
        glyph.thumbnail(bound, image.Resampling.LANCZOS)
        if glyph.width < bound[0] and glyph.height < bound[1]:
            scale = min(bound[0] / glyph.width, bound[1] / glyph.height)
            glyph = glyph.resize((round(glyph.width * scale), round(glyph.height * scale)), image.Resampling.LANCZOS)
        layer = image.new("RGBA", (256, 256))
        layer.alpha_composite(glyph, ((256 - glyph.width) // 2, (256 - glyph.height) // 2))
        layers[f"assets/blinx_ui/touch_symbol_{name}.png"] = layer
    return layers


def make_frames(source, image, draw, ops):
    filtered = image.Resampling.LANCZOS
    tex = source.crop(source.getchannel("A").getbbox()).resize((64, 64), filtered)
    center = ops.grayscale(tex.crop((20, 20, 43, 43)).resize((256, 256), filtered))
    mask = image.new("L", (256, 256))
    draw.Draw(mask).rounded_rectangle((13, 13, 242, 242), 40, fill=255)
    rim = image.new("RGBA", (256, 256))
    outline = draw.Draw(rim)
    outline.rounded_rectangle((13, 13, 242, 242), 40, outline="#758aa2", width=8)
    outline.rounded_rectangle((23, 23, 232, 232), 32, outline="#1a2332", width=7)
    outline.line((51, 28, 192, 28), fill="#b0c0cf", width=3)
    outline.line((54, 227, 202, 227), fill="#090e17", width=5)
    trim = image.new("RGBA", (256, 256))
    for box, location in [((0, 0, 28, 28), (8, 9)), ((39, 40, 64, 64), (208, 208))]:
        detail = tex.crop(box)
        detail.putdata([(r, g, b, round(a * .88) if max(r, g, b) - min(r, g, b) <= 35 else 0)
                        for r, g, b, a in detail.getdata()])
        trim.alpha_composite(detail.resize((41, 41), filtered), location)
    frames = {}
    for name, dark, light in (("amber", "#492b13", "#d88c24"), ("steel", "#121b2a", "#46566c")):
        plate = ops.colorize(center, dark, light).convert("RGBA")
        plate.putalpha(mask)
        plate.alpha_composite(rim)
        plate.alpha_composite(trim)
        patch = image.new("RGBA", (130, 130))
        patch.alpha_composite(plate.crop((6, 6, 250, 250)).resize((128, 128), filtered), (1, 1))
        guides = draw.Draw(patch)
        for segment in [(45, 0, 84, 0), (0, 45, 0, 84), (22, 129, 107, 129), (129, 22, 129, 107)]:
            guides.line(segment, fill="black")
        frames[f"res/drawable-xhdpi/launcher_{name}.9.png"] = patch
    return frames


def make_sticks(image, draw):
    base = image.new("RGBA", (256, 256))
    draw.Draw(base).ellipse((9, 9, 246, 246), fill="#182535")
    border = image.new("RGBA", (256, 256))
    pen = draw.Draw(border)
    pen.ellipse((9, 9, 246, 246), outline="#758aa2", width=5)
    pen.ellipse((22, 22, 233, 233), outline="#2d4054", width=3)
    for start, end in [((128, 9), (128, 20)), ((128, 234), (128, 246)),
                       ((9, 128), (20, 128)), ((234, 128), (246, 128))]:
        pen.line((start, end), fill="#ce9f51", width=4)
    base.alpha_composite(border)
    puck = image.new("RGBA", (256, 256))
    pen = draw.Draw(puck)
    pen.ellipse((84, 91, 172, 179), fill="#0c1420")
    pen.ellipse((83, 83, 173, 173), fill="#b57522", outline="#e6b657", width=4)
    pen.ellipse((91, 91, 165, 165), fill="#d39432", outline="#f1c15d", width=2)
    return {"assets/blinx_ui/touch_stick.png": base,
            "assets/blinx_ui/touch_thumb.png": puck.crop(puck.getchannel("A").getbbox())}


def render_pad(textures):
    image, draw, ops = require_pillow()
    for path, digest in TEXTURE_DIGESTS.items():
        if path not in textures or hashlib.sha256(textures[path]).hexdigest() != digest:
            raise ValueError(f"Unsupported PAL controller texture: {path}")
    with image.open(io.BytesIO(textures[FONT])) as source:
        layers = make_symbols(source.convert("RGBA"), image, draw)
    with image.open(io.BytesIO(textures[FRAME])) as source:
        layers.update(make_frames(source.convert("RGBA"), image, draw, ops))
    layers.update(make_sticks(image, draw))
    result = {}
    identity = hashlib.sha256()
    for path in sorted(layers):
        stream = io.BytesIO()
        layers[path].save(stream, format="PNG", optimize=True)
        data = stream.getvalue()
        result[path] = data
        digest = hashlib.sha256(data).hexdigest()
        identity.update(f"{path}\t{len(data)}\t{digest}\n".encode("utf-8"))
    if len(result) != 20 or identity.hexdigest() != TARGETS_DIGEST:
        raise ValueError("Generated controller skin differs from the 20-file reference. Use the verified Pillow build or the private pad inputs.")
    return result


def generate_pad(disc: Path, scratch: Path) -> dict[str, bytes]:
    """Return APK paths and PNG bytes; generation is in-memory and needs no scratch files."""
    if str(TOOLS) not in sys.path:
        sys.path.insert(0, str(TOOLS))
    from verify_inputs import read_verified_files
    require_pillow()
    return render_pad(read_verified_files(disc, "pal", (FONT, FRAME)))
