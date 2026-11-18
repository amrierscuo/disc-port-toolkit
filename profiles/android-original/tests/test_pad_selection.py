"""Control selection and integrity tests with synthetic inputs only."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pad_assets


class PadSelectionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.disc = self.root / "disc.iso"
        self.work = self.root / "work"
        self.original = self.root / "inputs/android-original/controls"
        self.full = self.root / "inputs/android-full/resources"
        self.images = {n: n.encode("ascii") for n in pad_assets.BITMAPS + pad_assets.FRAMES}
        records = [{"target": n, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                   for n, data in self.images.items()]
        inventory = self.root / "profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json"
        inventory.parent.mkdir(parents=True)
        inventory.write_text(json.dumps({"effective_inputs": records}), "utf-8")
        self.enterContext(patch.dict(os.environ, {"BLINX_ORIGINAL_PAD": "project"}))

    def populate(self, directory):
        for name, data in self.images.items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

    def select(self):
        return pad_assets.read_pad(self.root, self.disc, self.work)

    def test_default_generates_from_disc(self):
        with patch.object(pad_assets, "generate_pad", return_value=self.images) as render:
            self.assertEqual(self.select(), self.images)
            render.assert_called_once_with(self.disc, self.work)

    def test_verified_private_layouts(self):
        self.populate(self.full)
        with patch.object(pad_assets, "generate_pad", side_effect=AssertionError):
            self.assertEqual(self.select(), self.images)
            self.populate(self.original)
            self.assertEqual(self.select(), self.images)

    def test_partial_and_modified_override_stops_selection(self):
        self.populate(self.full)
        self.original.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, "missing"):
            self.select()
        self.populate(self.original)
        (self.original / pad_assets.BITMAPS[0]).write_bytes(b"invalid")
        with self.assertRaisesRegex(ValueError, "Unexpected touch artwork"):
            self.select()

    def test_generator_failure_is_reported(self):
        with patch.object(pad_assets, "generate_pad", side_effect=ValueError("missing Pillow")):
            with self.assertRaisesRegex(ValueError, "missing Pillow"):
                self.select()

    def test_neutral_is_explicit(self):
        with patch.dict(os.environ, {"BLINX_ORIGINAL_PAD": "neutral"}):
            self.assertEqual(self.select(), {})
        with patch.dict(os.environ, {"BLINX_ORIGINAL_PAD": "typo"}):
            with self.assertRaisesRegex(ValueError, "accepts project"):
                self.select()


if __name__ == "__main__":
    unittest.main()
