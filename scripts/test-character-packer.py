#!/usr/bin/env python3
"""Importer regression tests. Synthetic coloured fixtures stay in a temporary directory."""
import importlib.util
import sys
sys.dont_write_bytecode = True
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location("character_packer", Path(__file__).with_name("pack-character-animations.py"))
packer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packer)


class CharacterPackerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.config = {"an.idle.frames": "an_idle_00,an_idle_01", "an.idle.canvas": "16,16"}
        self.original_out = packer.OUT
        packer.OUT = self.root

    def tearDown(self):
        packer.OUT = self.original_out
        self.temp.cleanup()

    def fixture(self, name, colour):
        image = Image.new("RGBA", (16, 16))
        for x in range(4, 12):
            for y in range(2, 14):
                image.putpixel((x, y), colour)
        image.save(self.root / f"{name}.png")
        return image

    def test_pack_preserves_pixels_and_original_canvas(self):
        first = self.fixture("an_idle_00", (60, 200, 255, 255))
        self.fixture("an_idle_01", (255, 60, 180, 255))
        frames, complete, missing = packer.collect(self.root, self.config)
        self.assertEqual(["an.idle"], complete)
        self.assertEqual([], missing)
        packer.pack(frames)
        with Image.open(self.root / "characters-0.png") as page:
            self.assertEqual(first.tobytes(), page.crop((2, 2, 18, 18)).tobytes())
        atlas = (self.root / "characters.atlas").read_text()
        self.assertIn("filter: Nearest, Nearest", atlas)
        self.assertIn("orig: 16, 16", atlas)
        self.assertIn("offset: 0, 0", atlas)

    def test_partial_clip_and_duplicated_drawing_are_rejected(self):
        self.fixture("an_idle_00", (60, 200, 255, 255))
        with self.assertRaisesRegex(ValueError, "incomplete"):
            packer.collect(self.root, self.config)
        self.fixture("an_idle_01", (60, 200, 255, 255))
        with self.assertRaisesRegex(ValueError, "duplicate pixels"):
            packer.collect(self.root, self.config)

    def test_wrong_canvas_or_opaque_background_is_rejected(self):
        self.fixture("an_idle_00", (60, 200, 255, 255))
        Image.new("RGBA", (8, 16), (255, 255, 255, 255)).save(self.root / "an_idle_01.png")
        with self.assertRaisesRegex(ValueError, "size"):
            packer.collect(self.root, self.config)
        Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(self.root / "an_idle_01.png")
        with self.assertRaisesRegex(ValueError, "transparent"):
            packer.collect(self.root, self.config)


if __name__ == "__main__":
    unittest.main()
