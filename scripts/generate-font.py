#!/usr/bin/env python3
"""Bakes the UI bitmap font (AngelCode .fnt + PNG) used by libGDX.

The game ships a pre-rendered font instead of gdx-freetype, so no extra native
libraries are needed. Re-run after changing the character set or size:

    python3 scripts/generate-font.py [path/to/NotoSans-Bold.ttf] [path/to/NotoSans-BoldItalic.ttf]

Two fonts are baked: neon-ui (upright, HUD and body text) and neon-italic (menu titles and
buttons, matching the slanted lettering of the UI mock-ups).

Requires Pillow. Output goes to app/src/main/assets/fonts/. The source font must
allow redistribution (Noto Sans is SIL OFL 1.1; its license is copied alongside).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# Must match FontSpec.PIXEL_SIZE in core/.../view/FontSpec.kt.
PIXEL_SIZE = 42
PADDING = 2
PAGE_WIDTH = 1024
FONTS = [
    # (face, default source, output name, italic flag)
    ("NotoSans-Bold", "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf", "neon-ui", 0),
    ("NotoSans-BoldItalic", "/usr/share/fonts/truetype/noto/NotoSans-BoldItalic.ttf", "neon-italic", 1),
]
OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/fonts"

VIETNAMESE_LOWER = (
    "àáảãạăằắẳẵặâầấẩẫậđèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵ"
)
EXTRA = "—…→←·°"


def charset():
    chars = [chr(c) for c in range(32, 127)]
    chars += list(VIETNAMESE_LOWER) + list(VIETNAMESE_LOWER.upper()) + list(EXTRA)
    seen = []
    for ch in chars:
        if ch not in seen:
            seen.append(ch)
    return seen


def main():
    for index, (face, default, out_name, italic) in enumerate(FONTS):
        source = sys.argv[index + 1] if len(sys.argv) > index + 1 else default
        bake(face, source, out_name, italic)


def bake(face, source, out_name, italic):
    font = ImageFont.truetype(source, PIXEL_SIZE)
    ascent, descent = font.getmetrics()
    line_height = ascent + descent
    glyphs = []
    for ch in charset():
        left, top, right, bottom = font.getbbox(ch, anchor="ls")
        if right <= left or bottom <= top:  # whitespace
            left, top, right, bottom = 0, 0, 0, 0
        width = right - left + 2 * PADDING if right > left else 0
        height = bottom - top + 2 * PADDING if bottom > top else 0
        glyphs.append({
            "ch": ch, "left": left, "top": top, "w": width, "h": height,
            "advance": round(font.getlength(ch)),
        })

    # Simple shelf packing.
    x = y = shelf = 0
    for g in glyphs:
        if x + g["w"] > PAGE_WIDTH:
            x, y, shelf = 0, y + shelf + 1, 0
        g["x"], g["y"] = x, y
        x += g["w"] + 1
        shelf = max(shelf, g["h"])
    page_height = 1
    while page_height < y + shelf + 1:
        page_height *= 2

    image = Image.new("RGBA", (PAGE_WIDTH, page_height), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    for g in glyphs:
        if g["w"]:
            origin = (g["x"] + PADDING - g["left"], g["y"] + PADDING - g["top"])
            draw.text(origin, g["ch"], font=font, fill=(255, 255, 255, 255), anchor="ls")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    image.save(OUT_DIR / f"{out_name}.png", optimize=True)
    # libGDX's parser expects exactly this field order on the info/common lines.
    lines = [
        f'info face="{face}" size={PIXEL_SIZE} bold=1 italic={italic} charset="" unicode=1 '
        f'stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',
        f"common lineHeight={line_height} base={ascent} scaleW={PAGE_WIDTH} scaleH={page_height} "
        f"pages=1 packed=0",
        f'page id=0 file="{out_name}.png"',
        f"chars count={len(glyphs)}",
    ]
    for g in glyphs:
        xoffset = g["left"] - PADDING if g["w"] else 0
        yoffset = ascent + g["top"] - PADDING if g["h"] else 0
        lines.append(
            f"char id={ord(g['ch'])} x={g['x']} y={g['y']} width={g['w']} height={g['h']} "
            f"xoffset={xoffset} yoffset={yoffset} xadvance={g['advance']} page=0 chnl=15"
        )
    (OUT_DIR / f"{out_name}.fnt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{out_name}: {len(glyphs)} glyphs, page {PAGE_WIDTH}x{page_height}, lineHeight {line_height}")


if __name__ == "__main__":
    main()
