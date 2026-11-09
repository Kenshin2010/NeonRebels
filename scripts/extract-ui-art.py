#!/usr/bin/env python3
"""Cuts the menu backgrounds out of ui/01_UI_Menu_va_tien_trinh.png and the battle HUD pieces
(portraits, boss icon, Linh hologram) out of ui/02_UI_Chien_dau.png.

Each mock-up panel becomes one 1280x720 JPEG backdrop in app/src/main/assets/ui/. The
illustration (characters, city, logo) is kept; baked buttons, plates and panels are erased
(rows interpolated between box edges) or blurred, because the game draws every interactive
widget itself. Portraits and stat icons are cut out as separate images. Boxes are in output
pixels with the origin top-left; the screens use the same numbers with y flipped.

    python3 scripts/extract-ui-art.py [--grid DIR]

Requires Pillow. --grid also writes un-erased copies with a 40px grid, for measuring.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "ui/01_UI_Menu_va_tien_trinh.png"
BATTLE_SOURCE = ROOT / "ui/02_UI_Chien_dau.png"
OUT_DIR = ROOT / "app/src/main/assets/ui"
WIDTH, HEIGHT = 1280, 720
# The panels are ~1.95:1; a slight vertical stretch to 690px leaves thin bars instead of cropping.
ART_HEIGHT = 690
TOP = (HEIGHT - ART_HEIGHT) // 2
BACK = (6, 9, 22)

PANELS = {
    "menu": (42, 53, 825, 461),
    "team": (847, 53, 1630, 461),
    "map": (35, 494, 824, 891),
    "shop": (846, 494, 1637, 892),
}

# Baked text and widgets are removed: every button, card, plate and panel is drawn by the game.
ERASE = {
    "menu": [(1150, 32, 1256, 120)],  # sound button
    "team": [],
    "map": [
        (25, 34, 152, 116), (458, 24, 895, 92),  # back button, title
        (66, 374, 298, 436), (340, 348, 552, 402), (590, 368, 792, 422),
        (822, 366, 1022, 420), (1040, 338, 1252, 394),  # zone name plates
        (140, 313, 210, 383), (412, 268, 478, 334), (655, 305, 721, 371),
        (892, 305, 958, 371), (1107, 263, 1173, 329),  # zone nodes and locks
    ],
    "shop": [(25, 34, 152, 116), (430, 24, 832, 92), (930, 34, 1262, 104)],  # back, title, counter
}

# Areas behind code-drawn UI: blurred and darkened so the old widgets read as backdrop only.
# (left, top, right, bottom, blur radius, brightness)
BLUR = {
    "menu": [(0, 268, 610, 720, 16, .5)],  # old buttons under the new menu column
    "team": [(0, 0, 1280, 720, 9, .45),  # cards are rebuilt; keep only the mood
             (760, 560, 1280, 720, 30, .35)],  # old gold DEPLOY button would glow beside the new one
    "map": [(0, 456, 1280, 720, 12, .45)],  # old detail panel under the new one
    "shop": [(440, 96, 1280, 640, 14, .45)],  # old upgrade panels under the new ones
}

# Cut from the untouched art before anything is erased.
ICONS = {
    "shop": {"icon_heart": (498, 260, 552, 307), "icon_sword": (903, 193, 962, 252),
             "icon_bolt": (502, 453, 552, 514)},
    "team": {"icon_gun": (522, 482, 608, 528)},
}

PORTRAITS = {"team": {
    "portrait_an": (146, 142, 452, 432),
    "portrait_vy": (484, 142, 798, 436),
    "portrait_khang": (818, 140, 1140, 392),
}}
# Erased on a copy before cutting portraits: the cards' check badges and Khang's lock.
PORTRAIT_ERASE = {"portrait_an": [(396, 126, 473, 200)], "portrait_vy": [(726, 126, 803, 200)],
                  "portrait_khang": [(948, 266, 1018, 346)]}  # baked lock; the game draws the lock state


def erase(image, box):
    x0, y0, x1, y1 = box
    px = image.load()
    for y in range(y0, y1):
        left = px[x0 - 1, y]
        right = px[x1, y]
        span = x1 - x0 + 1
        for x in range(x0, x1):
            t = (x - x0 + 1) / span
            px[x, y] = tuple(round(left[c] * (1 - t) + right[c] * t) for c in range(3))


# Battle HUD pieces. Faces come from the squad cards (higher resolution, in team-panel output
# pixels); the boss icon and Linh's hologram come straight from the battle mock-up (source pixels).
HUD_FACES = {"hud_an": (178, 146, 378, 346), "hud_vy": (536, 150, 736, 350)}
BATTLE_PIECES = {"hud_boss": (305, 514, 363, 568)}
HOLOGRAMS = {"linh": (864, 718, 1042, 890)}
HUD_SIZE = 128


def hex_mask(size):
    """Hexagon with flat top and bottom, like the portrait frames in the battle mock-up."""
    mask = Image.new("L", (size, size), 0)
    s = size
    ImageDraw.Draw(mask).polygon([(s * .25, 0), (s * .75, 0), (s, s / 2), (s * .75, s), (s * .25, s), (0, s / 2)], fill=255)
    return mask


def hud_piece(image, box):
    piece = image.crop(box).resize((HUD_SIZE, HUD_SIZE), Image.LANCZOS).convert("RGBA")
    piece.putalpha(hex_mask(HUD_SIZE))
    return piece


def hologram(image, box):
    """Keys the dark panel out by brightness so only the glowing cyan figure remains."""
    piece = image.crop(box).convert("RGBA")
    px = piece.load()
    w, h = piece.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            lum = (r * 3 + g * 6 + b) / 10
            alpha = max(0, min(255, int((lum - 45) * 2.2)))
            # Drop the dialog frame lines around her (left edge, name tag, bottom rule) and fade out.
            if x < 42 or x > w - 12 or y > h - 16 or (x < 60 and 58 <= y <= 72):
                alpha = 0
            elif y > h - 46:
                alpha = alpha * (h - 16 - y) // 30
            px[x, y] = (r, g, b, alpha)
    return piece.resize((w * 2, h * 2), Image.LANCZOS)


def blur(image, box):
    left, top, right, bottom, radius, brightness = box
    region = image.crop((left, top, right, bottom)).filter(ImageFilter.GaussianBlur(radius))
    region = ImageEnhance.Brightness(region).enhance(brightness)
    feather = 28
    mask = Image.new("L", region.size, 0)
    ImageDraw.Draw(mask).rectangle((feather, feather, region.size[0] - feather, region.size[1] - feather), fill=255)
    # Edges that touch the canvas border need no fade.
    if left == 0 or top == 0 or right == WIDTH or bottom == HEIGHT:
        inner = (0 if left == 0 else feather, 0 if top == 0 else feather,
                 region.size[0] - (0 if right == WIDTH else feather), region.size[1] - (0 if bottom == HEIGHT else feather))
        mask = Image.new("L", region.size, 0)
        ImageDraw.Draw(mask).rectangle(inner, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(feather / 2))
    image.paste(region, (left, top), mask)


def keyed(image, box):
    crop = image.crop(box).convert("RGBA")
    px = crop.load()
    w, h = crop.size
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
    bg = tuple(sorted(c[i] for c in corners)[1] for i in range(3))
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            distance = abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2])
            px[x, y] = (r, g, b, max(0, min(255, (distance - 40) * 4)))
    return crop


def panel(source, name):
    art = source.crop(PANELS[name]).resize((WIDTH, ART_HEIGHT), Image.LANCZOS)
    canvas = Image.new("RGB", (WIDTH, HEIGHT), BACK)
    canvas.paste(art, (0, TOP))
    return canvas


def main():
    source = Image.open(SOURCE).convert("RGB")
    grid_dir = Path(sys.argv[sys.argv.index("--grid") + 1]) if "--grid" in sys.argv else None
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name in PANELS:
        image = panel(source, name)
        if grid_dir:
            debug = image.copy()
            draw = ImageDraw.Draw(debug)
            for x in range(0, WIDTH, 40):
                draw.line([(x, 0), (x, HEIGHT)], fill=(255, 255, 0) if x % 200 == 0 else (90, 90, 0))
            for y in range(0, HEIGHT, 40):
                draw.line([(0, y), (WIDTH, y)], fill=(255, 255, 0) if y % 200 == 0 else (90, 90, 0))
            debug.save(grid_dir / f"{name}_grid.png")
        for sprite, box in ICONS.get(name, {}).items():
            keyed(image, box).save(OUT_DIR / f"{sprite}.png", optimize=True)
        if name == "team":
            for sprite, box in HUD_FACES.items():
                hud_piece(image, box).save(OUT_DIR / f"{sprite}.png", optimize=True)
        for sprite, box in PORTRAITS.get(name, {}).items():
            copy = image.copy()
            for hole in PORTRAIT_ERASE.get(sprite, []):
                erase(copy, hole)
            copy.crop(box).save(OUT_DIR / f"{sprite}.jpg", quality=90, optimize=True)
        for box in ERASE[name]:
            erase(image, box)
        for box in BLUR.get(name, []):
            blur(image, box)
        image.save(OUT_DIR / f"{name}.jpg", quality=90, optimize=True)
        print(f"ui/{name}.jpg")
    battle = Image.open(BATTLE_SOURCE).convert("RGB")
    for sprite, box in BATTLE_PIECES.items():
        hud_piece(battle, box).save(OUT_DIR / f"{sprite}.png", optimize=True)
    for sprite, box in HOLOGRAMS.items():
        hologram(battle, box).save(OUT_DIR / f"{sprite}.png", optimize=True)
    print("ui/hud_*.png, ui/linh.png")


if __name__ == "__main__":
    main()
