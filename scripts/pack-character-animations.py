#!/usr/bin/env python3
"""Validate independent transparent PNGs and pack optional character clips without posing images.

Usage: python3 scripts/pack-character-animations.py --source /path/to/frames [--check]
Only complete clips are accepted. Missing clips retain the runtime legacy fallback.
Requires Pillow only when importing art; normal builds do not invoke this script.
"""
import argparse
import hashlib
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/sprites"
MANIFEST = OUT / "animations.properties"
PAGE_SIZE = 2048
PADDING = 2


def properties(path):
    return dict(line.split("=", 1) for raw in path.read_text(encoding="utf-8").splitlines()
                if (line := raw.strip()) and not line.startswith("#") and "=" in line)


def collect(source, config):
    frames = {}
    complete = []
    missing = []
    hashes = {}
    for key, value in config.items():
        if not key.endswith(".frames"):
            continue
        clip = key.removesuffix(".frames")
        names = [s.strip() for s in value.split(",")]
        present = [(source / f"{name}.png").is_file() for name in names]
        if not any(present):
            missing.append(clip)
            continue
        if not all(present):
            raise ValueError(f"{clip}: incomplete clip, missing " + ", ".join(n for n, ok in zip(names, present) if not ok))
        canvas = tuple(map(int, config[f"{clip}.canvas"].split(",")))
        if len(set(names)) != len(names) or len(names) < 2:
            raise ValueError(f"{clip}: need at least two distinct names")
        for name in names:
            with Image.open(source / f"{name}.png") as raw:
                if raw.mode != "RGBA":
                    raise ValueError(f"{name}: expected RGBA")
                image = raw.copy()
            if image.size != canvas:
                raise ValueError(f"{name}: size {image.size}, expected original canvas {canvas}")
            lo, hi = image.getchannel("A").getextrema()
            if lo != 0 or hi == 0:
                raise ValueError(f"{name}: expected visible drawing and transparent background")
            digest = hashlib.sha256(image.tobytes()).hexdigest()
            if digest in hashes:
                raise ValueError(f"{name}: duplicate pixels of {hashes[digest]}; provide independent drawings")
            hashes[digest] = name
            frames[name] = image
        complete.append(clip)
    return frames, complete, missing


def pack(frames):
    pages = []
    entries = []
    image = Image.new("RGBA", (PAGE_SIZE, PAGE_SIZE))
    x = y = row_height = 0
    for name, frame in frames.items():
        w, h = frame.size
        if w + 2 * PADDING > PAGE_SIZE or h + 2 * PADDING > PAGE_SIZE:
            raise ValueError(f"{name}: canvas exceeds page")
        if x + w + 2 * PADDING > PAGE_SIZE:
            x = 0
            y += row_height
            row_height = 0
        if y + h + 2 * PADDING > PAGE_SIZE:
            pages.append(image)
            image = Image.new("RGBA", (PAGE_SIZE, PAGE_SIZE))
            x = y = row_height = 0
        # No crop/rotation/resizing or colour edits: original anchor and character scale survive.
        image.paste(frame, (x + PADDING, y + PADDING))
        entries.append((len(pages), name, x + PADDING, y + PADDING, w, h))
        x += w + 2 * PADDING
        row_height = max(row_height, h + 2 * PADDING)
    pages.append(image)
    atlas = []
    for page, image in enumerate(pages):
        filename = f"characters-{page}.png"
        image.save(OUT / filename)
        atlas += ["", filename, f"size: {PAGE_SIZE}, {PAGE_SIZE}", "format: RGBA8888",
                  "filter: Nearest, Nearest", "repeat: none"]
        for number, name, x, y, w, h in entries:
            if number == page:
                atlas += [name, "  rotate: false", f"  xy: {x}, {y}", f"  size: {w}, {h}",
                          f"  orig: {w}, {h}", "  offset: 0, 0", "  index: -1"]
    (OUT / "characters.atlas").write_text("\n".join(atlas) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--check", action="store_true", help="Validate only; keep existing atlas unchanged")
    args = parser.parse_args()
    try:
        if not args.source.is_dir():
            raise ValueError(f"Source directory does not exist: {args.source}")
        frames, complete, missing = collect(args.source, properties(MANIFEST))
        print("Independent clips supplied:", ", ".join(complete) or "none")
        print("Missing clips (legacy fallback):", ", ".join(missing))
        if not frames:
            raise ValueError("No complete clips supplied; existing atlas unchanged")
        if not args.check:
            pack(frames)
            print(f"Packed {len(frames)} unchanged drawings into {OUT / 'characters.atlas'}")
    except (ValueError, OSError) as error:
        parser.exit(1, f"ERROR: {error}\n")


if __name__ == "__main__":
    main()
