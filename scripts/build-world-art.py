#!/usr/bin/env python3
"""Builds the in-run world art from the UI mock-ups, plus pixel-art props.

Actors and scenery are cut straight out of the design images so the game matches them:

  ui/04_Gameplay_An.png   An's sword stance, shield robot, blade sentry, city backdrop, bridge
  ui/05_Gameplay_Vy.png   Vy's shooting stance
  ui/02_UI_Chien_dau.png  the Armored Gatekeeper lunging

Characters are separated from the painted background with OpenCV GrabCut (rectangle pass,
then a pass that forces the saturated blue backdrop to background). The design shows one pose
per actor, so the game animates them with transforms (see view/WorldRenderer.kt); an artist can
later drop in a TexturePacker atlas with more frames under the same region names.

Props that the mock-ups do not show (repair pod, arena barrier, radio tower, drone) are drawn
here as pixel art in the same palette.

Output in app/src/main/assets/sprites/:
  world.png + world.atlas    TextureAtlas (legacy text format), names in view/SpriteNames.kt
  actors.properties          per-region anchor (body centre, 0..1 of width), draw height in
                             world units, and whether the art faces right
  bg_far.png                 1024x256 backdrop, mirrored so it repeats seamlessly
  ground.png                 1024x512 bridge deck and underside, mirrored, repeats
  ledge.png                  256x32 stone ledge for floating platforms

Requires: Pillow, numpy, opencv-python-headless (dev only; outputs are committed).
    python3 scripts/build-world-art.py
"""
import math
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "ui"
OUT = ROOT / "app/src/main/assets/sprites"

# name: (source, crop box, floor row or None, anchor x override or None, height in world units, faces right)
ACTORS = {
    "an_stance": ("04_Gameplay_An.png", (432, 420, 722, 590), None, None, 2.5, True),
    "vy_stance": ("05_Gameplay_Vy.png", (448, 428, 668, 586), None, None, 2.4, True),
    "robot_shield": ("04_Gameplay_An.png", (922, 418, 1168, 590), None, None, 2.6, False),
    "robot_blade": ("04_Gameplay_An.png", (1212, 415, 1398, 566), None, None, 2.3, False),
    "boss_stance": ("02_UI_Chien_dau.png", (372, 560, 770, 724), None, None, 5.0, False),
}

# Sword blade and lightning on An: cyan on a blue backdrop, restored by brightness inside this box.
AN_BLADE = (150, 80, 290, 140)


def hsv_planes(bgr):
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
    return hsv[..., 0] * 2, hsv[..., 1] / 255, hsv[..., 2] / 255


def cut(name, source, box):
    img = cv2.imread(str(UI / source))
    x0, y0, x1, y1 = box
    crop = img[y0:y1, x0:x1].copy()
    h, s, v = hsv_planes(crop)
    mask = np.zeros(crop.shape[:2], np.uint8)
    bgd, fgd = np.zeros((1, 65)), np.zeros((1, 65))
    cv2.grabCut(crop, mask, (3, 3, crop.shape[1] - 6, crop.shape[0] - 4), bgd, fgd, 6, cv2.GC_INIT_WITH_RECT)
    blue = (h >= 195) & (h <= 230) & (s > .74) & (v > .56) & (v < .9)
    # The lit walkway edge under the feet: pale, low-saturation rows at the very bottom.
    floor = np.zeros_like(blue)
    floor[-7:] = (s[-7:] < .4) & (v[-7:] > .55)
    blue |= floor
    mask[blue & ((mask == cv2.GC_PR_FGD) | (mask == cv2.GC_PR_BGD))] = cv2.GC_BGD
    sure = (v < .2) | (((h >= 290) | (h < 25)) & (s > .4) & (v > .35)) | ((s < .22) & (v > .6))
    mask[sure & (mask == cv2.GC_PR_BGD)] = cv2.GC_PR_FGD
    cv2.grabCut(crop, mask, None, bgd, fgd, 4, cv2.GC_INIT_WITH_MASK)
    alpha = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    if name == "an_stance":
        bx0, by0, bx1, by1 = AN_BLADE
        region = (v[by0:by1, bx0:bx1] > .72) & ((s[by0:by1, bx0:bx1] < .7) | (v[by0:by1, bx0:bx1] > .88))
        alpha[by0:by1, bx0:bx1][region] = 255
    # Drop specks and fill pinholes.
    alpha = cv2.morphologyEx(alpha, cv2.MORPH_CLOSE, np.ones((2, 2), np.uint8))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(alpha)
    keep = np.zeros_like(alpha)
    largest = max((stats[i, cv2.CC_STAT_AREA] for i in range(1, count)), default=0)
    for i in range(1, count):
        # Keep the body and sizable attached pieces (blades, scarf); drop floor glints.
        if stats[i, cv2.CC_STAT_AREA] >= max(40, largest * .01):
            keep[labels == i] = 255
    rgba = cv2.cvtColor(crop, cv2.COLOR_BGR2RGBA)
    rgba[..., 3] = keep
    image = Image.fromarray(rgba)
    return image.crop(image.getbbox())


def feet_anchor(image):
    """Horizontal body centre: middle of the opaque pixels in the lowest fifth (the feet)."""
    a = np.array(image)[..., 3]
    rows = a[int(a.shape[0] * .8):]
    cols = np.where(rows.max(axis=0) > 0)[0]
    return float((cols.min() + cols.max()) / 2 / a.shape[1]) if len(cols) else .5


# ---------------------------------------------------------------- scenery

def mirrored(image):
    w, h = image.size
    out = Image.new(image.mode, (w * 2, h))
    out.paste(image, (0, 0))
    out.paste(image.transpose(Image.FLIP_LEFT_RIGHT), (w, 0))
    return out


def scenery():
    an = Image.open(UI / "04_Gameplay_An.png").convert("RGB")
    # City, holo tower, pink moon, ruined bridge with skull banners (above the HUD and actors).
    far = mirrored(an.crop((540, 148, 1672, 420))).resize((1024, 256), Image.LANCZOS)
    far.save(OUT / "bg_far.png", optimize=True)
    # Bridge deck, pipes, vines and rubble under the walkway, between the actors and the buttons.
    ground = mirrored(an.crop((450, 586, 1040, 940))).resize((1024, 512), Image.LANCZOS)
    ground.save(OUT / "ground.png", optimize=True)
    ledge = mirrored(an.crop((450, 586, 1040, 640))).resize((256, 32), Image.LANCZOS)
    ledge.save(OUT / "ledge.png", optimize=True)
    # Sky colour just above the crop, used to fill the top of the view.
    return far.getpixel((300, 2))


# ---------------------------------------------------------------- pixel-art props

OUTLINE = (10, 10, 26, 255)
METAL, METAL_SH, METAL_HI, METAL_DK = (104, 110, 140, 255), (64, 68, 96, 255), (156, 164, 196, 255), (34, 36, 56, 255)
CYAN, PINK, RED, ORANGE, GREEN = (56, 232, 255, 255), (255, 63, 164, 255), (255, 51, 85, 255), (255, 140, 60, 255), (80, 255, 150, 255)


def outlined(img):
    a = np.array(img)
    alpha = a[..., 3] > 0
    grown = cv2.dilate(alpha.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    edge = grown & ~alpha
    a[edge] = OUTLINE
    return Image.fromarray(a)


def glow(img, color, radius=4):
    """Soft additive-looking halo behind bright parts."""
    a = np.array(img)
    bright = (a[..., :3].max(axis=2) > 200) & (a[..., 3] > 0)
    halo = Image.fromarray((bright * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    layer = Image.new("RGBA", img.size, color[:3] + (0,))
    layer.putalpha(halo.point(lambda p: int(p * .55)))
    out = layer.copy()
    out.alpha_composite(img)
    return out


def repair_pod(active):
    w, h = 72, 104
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    light = GREEN if active else (40, 90, 70, 255)
    d.rectangle((8, 88, 64, 103), fill=METAL_SH)
    d.rectangle((4, 96, 68, 103), fill=METAL_DK)
    d.rectangle((12, 86, 60, 90), fill=light)
    d.rounded_rectangle((14, 14, 58, 88), 18, fill=(20, 60, 50, 255) if active else (20, 30, 34, 255))
    d.rounded_rectangle((18, 18, 54, 84), 14, fill=(60, 200, 140, 120) if active else (40, 60, 60, 120))
    for y in range(24, 80, 8):
        d.line((22, y, 50, y), fill=(140, 255, 200, 90) if active else (70, 90, 90, 80))
    d.rectangle((32, 36, 40, 64), fill=WHITE_A(active))
    d.rectangle((22, 46, 50, 54), fill=WHITE_A(active))
    d.rectangle((10, 4, 62, 16), fill=METAL)
    d.rectangle((10, 4, 62, 6), fill=METAL_HI)
    d.rectangle((26, 0, 46, 4), fill=METAL_SH)
    d.ellipse((30, 7, 42, 13), fill=light)
    d.line((4, 30, 12, 30), fill=METAL_SH, width=3)
    d.line((60, 60, 68, 60), fill=METAL_SH, width=3)
    img = outlined(img)
    return glow(img, GREEN, 5) if active else img


def WHITE_A(active):
    return (230, 255, 240, 255) if active else (90, 110, 110, 255)


def barrier(color):
    """Arena energy barrier: metal pylon caps with a column of laser bars (drawn stretched)."""
    w, h = 32, 256
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    d.rectangle((10, 0, 22, h - 1), fill=color[:3] + (70,))
    for y in range(10, h - 18, 14):
        d.rectangle((4, y, 28, y + 4), fill=color)
        d.rectangle((4, y + 1, 28, y + 2), fill=(255, 230, 230, 255))
    d.rectangle((0, h - 18, 31, h - 1), fill=METAL_SH)
    d.rectangle((0, h - 18, 31, h - 15), fill=METAL_HI)
    for x in range(0, 32, 8):
        d.polygon([(x, h - 10), (x + 4, h - 10), (x + 8, h - 4), (x + 4, h - 4)], fill=(255, 200, 40, 255))
    d.rectangle((2, 0, 29, 8), fill=METAL_SH)
    return outlined(img)


def radio_tower(active):
    w, h = 96, 232
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    lit = PINK if active else (90, 40, 70, 255)
    lit2 = CYAN if active else (40, 70, 90, 255)
    # Lattice mast.
    d.polygon([(30, h - 30), (66, h - 30), (52, 40), (44, 40)], fill=None, outline=METAL)
    for y in range(h - 30, 40, -14):
        t = (h - 30 - y) / (h - 70)
        half = 18 - 14 * t
        d.line((48 - half, y, 48 + half, y - 14), fill=METAL, width=2)
        d.line((48 + half, y, 48 - half, y - 14), fill=METAL_SH, width=2)
    d.line((30, h - 30, 44, 40), fill=METAL_HI, width=3)
    d.line((66, h - 30, 52, 40), fill=METAL, width=3)
    # Dish and beacon.
    d.pieslice((16, 54, 64, 102), 120, 300, fill=METAL_HI)
    d.pieslice((22, 60, 58, 96), 120, 300, fill=METAL_SH)
    d.line((40, 78, 60, 70), fill=METAL_HI, width=2)
    d.rectangle((46, 8, 50, 40), fill=METAL)
    d.ellipse((42, 0, 54, 12), fill=lit)
    for y in (60, 110, 160):
        d.rectangle((44, y, 52, y + 5), fill=lit2)
    # Control station at the base.
    d.rectangle((8, h - 34, 88, h - 1), fill=METAL_SH)
    d.rectangle((8, h - 34, 88, h - 30), fill=METAL_HI)
    d.rectangle((18, h - 24, 46, h - 8), fill=(20, 24, 44, 255))
    d.rectangle((20, h - 22, 44, h - 10), fill=lit2[:3] + (200,))
    d.rectangle((54, h - 24, 78, h - 18), fill=lit)
    d.rectangle((54, h - 14, 78, h - 8), fill=METAL_DK)
    img = outlined(img)
    if active:
        g = ImageDraw.Draw(img)
        for r in (18, 30, 42):
            g.arc((48 - r, 6 - r, 48 + r, 6 + r), 200, 340, fill=PINK, width=2)
        img = glow(img, PINK, 6)
    return img


def drone(frame, firing=False):
    w, h = 44, 30
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    span = 18 if frame == 0 else 12
    d.line((22 - span, 6, 22 + span, 6), fill=METAL_HI, width=2)
    d.rectangle((20, 6, 24, 10), fill=METAL_SH)
    d.polygon([(8, 12), (36, 12), (32, 24), (12, 24)], fill=METAL)
    d.polygon([(8, 12), (36, 12), (34, 15), (10, 15)], fill=METAL_HI)
    d.rectangle((14, 17, 30, 20), fill=METAL_DK)
    d.ellipse((24, 14, 32, 22), fill=ORANGE if firing else RED)
    d.point((29, 17), fill=(255, 255, 255, 255))
    d.line((12, 24, 8, 29), fill=METAL_SH, width=2)
    d.line((32, 24, 36, 29), fill=METAL_SH, width=2)
    img = outlined(img)
    return glow(img, RED, 3)


def hazard_sign():
    img = Image.new("RGBA", (24, 32))
    d = ImageDraw.Draw(img)
    d.rectangle((10, 14, 13, 31), fill=METAL_SH)
    d.polygon([(12, 0), (23, 18), (0, 18)], fill=(255, 190, 40, 255))
    d.rectangle((11, 6, 12, 12), fill=OUTLINE)
    d.rectangle((11, 14, 12, 15), fill=OUTLINE)
    return outlined(img)


# ---------------------------------------------------------------- packing

def pack(regions, page_w=1024):
    x = y = shelf = 0
    placed = []
    for name, index, img in sorted(regions, key=lambda r: -r[2].height):
        w, h = img.size
        if x + w > page_w:
            x, y, shelf = 0, y + shelf + 2, 0
        placed.append((name, index, img, x, y))
        x += w + 2
        shelf = max(shelf, h)
    page_h = 1
    while page_h < y + shelf:
        page_h *= 2
    page = Image.new("RGBA", (page_w, page_h))
    for name, index, img, px, py in placed:
        page.alpha_composite(img, (px, py))
    return page, sorted(placed, key=lambda p: (p[0], p[1]))


def write_atlas(page, placed):
    lines = ["", "world.png", f"size: {page.width}, {page.height}", "format: RGBA8888",
             "filter: Linear, Linear", "repeat: none"]
    for name, index, img, x, y in placed:
        w, h = img.size
        lines += [name, "  rotate: false", f"  xy: {x}, {y}", f"  size: {w}, {h}", f"  orig: {w}, {h}",
                  "  offset: 0, 0", f"  index: {index}"]
    (OUT / "world.atlas").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in ("bg_sky.png", "bg_mid.png", "tile_street.png", "tile_fill.png", "tile_platform.png"):
        (OUT / old).unlink(missing_ok=True)
    regions = []
    meta = ["# name=anchorX(0..1 of width),heightInWorldUnits,facesRight  (generated by build-world-art.py)"]
    for name, (source, box, _floor, anchor, height, faces_right) in ACTORS.items():
        image = cut(name, source, box)
        regions.append((name, 0, image))
        ax = anchor if anchor is not None else feet_anchor(image)
        meta.append(f"{name}={ax:.3f},{height},{str(faces_right).lower()}")
    props = {
        "repair_on": repair_pod(True), "repair_off": repair_pod(False),
        "barrier": barrier(RED), "door": barrier(ORANGE),
        "tower_on": radio_tower(True), "tower_off": radio_tower(False),
        "hazard": hazard_sign(),
    }
    for name, image in props.items():
        regions.append((name, 0, image))
    regions += [("drone_fly", 0, drone(0)), ("drone_fly", 1, drone(1)), ("drone_fire", 0, drone(0, True))]
    page, placed = pack(regions)
    page.save(OUT / "world.png", optimize=True)
    write_atlas(page, placed)
    sky = scenery()
    meta.append(f"sky={sky[0]},{sky[1]},{sky[2]}")
    (OUT / "actors.properties").write_text("\n".join(meta) + "\n", encoding="utf-8")
    print(f"world.png {page.width}x{page.height}, {len(placed)} regions")


if __name__ == "__main__":
    main()
