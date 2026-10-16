"""Navigation fixture mutations do not alter unrelated DVD fields."""
import struct
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from create_navigation_fixture import invalid_previous_pgc


class FixtureTests(unittest.TestCase):
    def fixture(self):
        data = bytearray(8192)
        data[:12] = b"DVDVIDEO-VTS"
        struct.pack_into(">I", data, 0xCC, 2)
        struct.pack_into(">H", data, 4096, 2)
        struct.pack_into(">I", data, 4108, 24)
        data[4122:4124] = bytes([4, 4])
        return bytes(data)

    def test_only_previous_link_changes(self):
        source = self.fixture()
        result, report = invalid_previous_pgc(source)
        self.assertEqual(report["previousPGCAfter"], 3)
        self.assertEqual(report["pgcOffset"], 4120)
        self.assertEqual([i for i, pair in enumerate(zip(source, result)) if pair[0] != pair[1]], [4279])
        self.assertEqual(source[4279], 0)

    def test_existing_link_and_wrong_input_rejected(self):
        modified, _ = invalid_previous_pgc(self.fixture())
        with self.assertRaisesRegex(ValueError, "existing previous"):
            invalid_previous_pgc(modified)
        with self.assertRaisesRegex(ValueError, "VTS IFO"):
            invalid_previous_pgc(b"Not a DVD")
