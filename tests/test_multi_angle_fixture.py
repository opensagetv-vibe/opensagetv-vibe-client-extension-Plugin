import struct
import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from create_multi_angle_fixture import angle_ifo, angle_vob


class MultiAngleFixtureTests(unittest.TestCase):
    def ifo(self):
        data=bytearray(4096);data[:12]=b'DVDVIDEO-VTS'
        struct.pack_into('>I',data,0xcc,1)
        struct.pack_into('>H',data,2048,3)
        for i in range(3):
            relative=32+i*268;start=2048+relative
            struct.pack_into('>I',data,2060+i*8,relative)
            data[start+2:start+8]=b'\x01\x01\x00\x00\x24\xc0'
            struct.pack_into('>HHH',data,start+230,236,240,264)
            data[start+236]=1
            struct.pack_into('>I',data,start+248,i*100)
            struct.pack_into('>I',data,start+260,i*100+99)
            struct.pack_into('>HBB',data,start+264,i+1,0,1)
        struct.pack_into('>I',data,2052,32+3*268-1)
        return data

    def vob(self):
        data=bytearray(6*2048)
        for s in range(6):
            base=s*2048;pci=base+45;dsi=base+1031
            data[pci-7:pci]=b'\x00\x00\x01\xbf\x03\xd4\x00'
            data[dsi-7:dsi]=b'\x00\x00\x01\xbf\x03\xfa\x01'
            struct.pack_into('>H',data,dsi+24,s//2+1)
            data[pci+26]=s%2
        return data

    def test_three_real_cells_assembled_without_sector_relocation(self):
        result=angle_ifo(self.ifo());pgc=2080
        self.assertEqual(3,result[pgc+3])
        cell=pgc+struct.unpack_from('>H',result,pgc+232)[0]
        pos=pgc+struct.unpack_from('>H',result,pgc+234)[0]
        self.assertEqual([0x50,0x90,0xd0],[result[cell+i*24]&0xf0 for i in range(3)])
        self.assertEqual([0,100,200],[struct.unpack_from('>I',result,cell+i*24+8)[0] for i in range(3)])
        self.assertEqual([1,2,3],[struct.unpack_from('>H',result,pos+i*4)[0] for i in range(3)])

    def test_count_or_duration_only_fakes_rejected(self):
        data=self.ifo();data[2083]=2
        with self.assertRaises(ValueError):angle_ifo(data)
        data=self.ifo();data[2080+268+6]=0x20
        with self.assertRaises(ValueError):angle_ifo(data)

    def test_actual_branch_links_are_signed_relative_next_navs(self):
        result=angle_vob(self.vob())
        self.assertEqual([1,3,5],list(struct.unpack_from('>III',result,45+60)))
        self.assertEqual(0x80000003,struct.unpack_from('>I',result,4*2048+45+60)[0])
        self.assertEqual((0,0,0),struct.unpack_from('>III',result,5*2048+45+60))

    def test_unequal_or_unknown_physical_branches_rejected(self):
        data=self.vob();struct.pack_into('>H',data,5*2048+1031+24,4)
        with self.assertRaises(ValueError):angle_vob(data)
        with self.assertRaises(ValueError):angle_vob(b'not DVD sectors')

    def test_elapsed_time_mismatch_rejected(self):
        data=self.vob();data[4*2048+45+26]=2
        with self.assertRaises(ValueError):angle_vob(data)
