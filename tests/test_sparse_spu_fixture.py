import struct
import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from create_sparse_spu_fixture import relocate_ifo, relocate_vob


class SparseSpuTests(unittest.TestCase):
    def test_only_actual_pes_substream_id_changes(self):
        data = bytearray(2048)
        data[:10] = b"\x00\x00\x01\xbd\x00\x06\x80\x00\x00\x21"
        data[10:12] = b"\x21\x21"
        result, count = relocate_vob(bytes(data))
        self.assertEqual(1, count)
        self.assertEqual([9], [i for i in range(len(data)) if data[i] != result[i]])
        self.assertEqual(34, result[9])
        with self.assertRaisesRegex(ValueError, "occupied"):
            relocate_vob(result)

    def test_logical_one_maps_to_physical_two_in_each_display_mode(self):
        data = bytearray(8192)
        data[:12] = b"DVDVIDEO-VTS"
        struct.pack_into(">I", data, 0xcc, 2)
        struct.pack_into(">H", data, 4096, 1)
        struct.pack_into(">I", data, 4108, 24)
        data[4152:4156] = bytes([0x81,1,1,1])
        result, count = relocate_ifo(bytes(data))
        self.assertEqual(4, count)
        self.assertEqual(bytes([0x82,2,2,2]), result[4152:4156])
        self.assertEqual(data[:4152], result[:4152])
        self.assertEqual(data[4156:], result[4156:])

    def test_bad_sector_or_header_rejected(self):
        with self.assertRaises(ValueError):
            relocate_vob(b"bad")
        with self.assertRaises(ValueError):
            relocate_ifo(b"not an IFO")
