"""Regression checks for the effective Full asset contract, using metadata only."""

from copy import deepcopy
import json
import unittest

from build import CONTRACT, HERE, NATIVE_GROUP, bind_demo_locale, DISCS, check_file, validate_contract


class InventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = json.loads(CONTRACT.read_text(encoding="utf-8"))["effective_inputs"]

    def test_effective_inventory(self):
        validate_contract(self.rows)
        self.assertEqual(len(self.rows), 324)
        self.assertEqual(len({r["target"] for r in self.rows}), 324)
        self.assertEqual(sum(r["group"] == NATIVE_GROUP for r in self.rows), 2)

    def test_lost_row(self):
        with self.assertRaisesRegex(ValueError, "324 rows"):
            validate_contract(self.rows[:-1])

    def test_duplicate_target(self):
        rows = deepcopy(self.rows)
        rows[1]["target"] = rows[0]["target"]
        with self.assertRaisesRegex(ValueError, "unique"):
            validate_contract(rows)

    def test_lost_native_input(self):
        rows = deepcopy(self.rows)
        next(r for r in rows if r["group"] == NATIVE_GROUP)["group"] = "base_app"
        with self.assertRaisesRegex(ValueError, "two"):
            validate_contract(rows)

    def test_demo_container_binding_preserves_game_fingerprint(self):
        template = (HERE / "source/combined/frontend/app/src/it/blinx/demo/v1/DemoLocale.java").read_text("utf-8")
        profile = {"bytes": 671088640, "sha256": "f" * 64}
        adapted = bind_demo_locale(template, profile)
        self.assertIn(f"DISC_BYTES = {profile['bytes']}L", adapted)
        self.assertIn(f'DISC_SHA256 = "{profile["sha256"]}"', adapted)
        self.assertIn(DISCS["demo"]["xbe_sha256"], adapted)
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            bind_demo_locale(adapted, profile)

    def test_public_inputs_match_declared_hashes(self):
        included = [r for r in self.rows if "public_source" in r]
        self.assertEqual(len(included), 59)
        for row in included:
            with self.subTest(target=row["target"]):
                self.assertIsNone(check_file(HERE / row["public_source"],
                                             row.get("export_bytes", row["bytes"]),
                                             row.get("export_sha256", row["sha256"])))
        upstream = HERE / "source/combined/runtime/provenance/upstream"
        self.assertEqual(sum(path.is_file() for path in upstream.rglob("*")), 22)


if __name__ == "__main__":
    unittest.main()
