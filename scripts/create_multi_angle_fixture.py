#!/usr/bin/env python3
"""Generate three REAL DVD video branches, then author a non-seamless angle PGC.

Not a commercial-rip repair or a count-only fake. dvdauthor creates all MPEG,
NAV, VOB IDs, cell addresses and three independently playable PGCs. We assemble
their equal-duration cells into title1's DVD angle block inside the existing
reserved PGCIT sector. All three encoded branches remain on disc. Qualification
MUST additionally decode title1 with an independent reader at angles1/2/3.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess


def angle_vob(data: bytes) -> bytes:
    """Author actual non-seamless next-unit links among equal encoded branches."""
    if len(data)%2048 or len(data)>67108864:
        raise ValueError('Bounded generated VOB only')
    branches=[[],[],[]]
    for sector in range(len(data)//2048):
        base=sector*2048; block=data[base:base+2048]
        packets=[];start=0
        while True:
            found=block.find(b'\x00\x00\x01\xbf',start)
            if found<0: break
            packets.append(found+7);start=found+7
        if len(packets)!=2: continue
        pci,dsi=[base+p for p in packets]
        if data[pci-1]!=0 or data[dsi-1]!=1:
            raise ValueError('Unknown generated NAV layout')
        vob=struct.unpack_from('>H',data,dsi+24)[0]
        if vob not in (1,2,3): raise ValueError('Unexpected VOB identity')
        branches[vob-1].append((sector,pci,dsi))
    if not branches[0] or len({len(b) for b in branches})!=1:
        raise ValueError('Three equal NAV sequences required')
    result=bytearray(data)
    for branch in branches:
        for i,(sector,pci,dsi) in enumerate(branch):
            for other in branches:
                if data[pci+24:pci+28]!=data[other[i][1]+24:other[i][1]+28]:
                    raise ValueError('Angle elapsed times differ')
            struct.pack_into('>H',result,dsi+32,0x5000)
            struct.pack_into('>I',result,dsi+34,struct.unpack_from('>I',data,dsi+8)[0])
            for j,other in enumerate(branches):
                delta=other[i+1][0]-sector if i+1<len(branch) else 0
                encoded=abs(delta) | (0x80000000 if delta<0 else 0)
                struct.pack_into('>I',result,pci+60+j*4,encoded)
    return bytes(result)


def angle_ifo(data: bytes) -> bytes:
    data=bytes(data)
    if data[:12] != b'DVDVIDEO-VTS':
        raise ValueError('Generated VTS only')
    u16 = lambda n: struct.unpack_from('>H', data, n)[0]
    u32 = lambda n: struct.unpack_from('>I', data, n)[0]
    table = u32(0xcc) * 2048
    if u16(table) != 3:
        raise ValueError('Exactly three generated PGCs required')
    old_end = u32(table+4)+1
    starts = [u32(table+12+i*8) for i in range(3)]
    chains = [data[table+starts[i]:table+(starts[i+1] if i<2 else old_end)] for i in range(3)]
    if any(len(c)<236 or c[2:4]!=b'\x01\x01' for c in chains):
        raise ValueError('One program and one cell per source branch required')
    if len({c[4:8] for c in chains}) != 1:
        raise ValueError('Angle durations differ')
    cells, positions = [], []
    for i,c in enumerate(chains):
        cell_offset = struct.unpack_from('>H', c, 232)[0]
        pos_offset = struct.unpack_from('>H', c, 234)[0]
        cell = bytearray(c[cell_offset:cell_offset+24])
        pos = c[pos_offset:pos_offset+4]
        if len(cell)!=24 or len(pos)!=4 or struct.unpack_from('>H',pos)[0]!=i+1:
            raise ValueError('Missing distinct physical VOB branch')
        cell[0] = (cell[0]&15) | ((i+1)<<6) | 0x10
        cells.append(cell); positions.append(pos)
    first = bytearray(chains[0][:236])
    # Preserve authored pre/post commands and program map, replacing only cell
    # tables. Offsets are relative to this PGC, never absolute disc sectors.
    original_cells = struct.unpack_from('>H', first, 232)[0]
    first.extend(chains[0][236:original_cells])
    first[3] = 3
    struct.pack_into('>H',first,232,len(first))
    first.extend(b''.join(cells))
    struct.pack_into('>H',first,234,len(first))
    first.extend(b''.join(positions))
    header=bytearray(data[table:table+32])
    payload=bytearray(header)
    for i,c in enumerate([bytes(first),chains[1],chains[2]]):
        struct.pack_into('>I',payload,12+i*8,len(payload))
        payload.extend(c)
    struct.pack_into('>I',payload,4,len(payload)-1)
    reserved=((old_end+2047)//2048)*2048
    if len(payload)>reserved or table+reserved>len(data):
        raise ValueError('PGCIT growth exceeds existing reserved sector')
    result=bytearray(data)
    result[table:table+reserved]=payload+b'\0'*(reserved-len(payload))
    return bytes(result)


def create(output: Path, work: Path, ffmpeg: str, dvdauthor: str) -> dict:
    if output.exists() or work.exists():
        raise ValueError('New owned output/work directories only; never overwrite')
    work.mkdir(parents=True)
    output.parent.mkdir(parents=True,exist_ok=True)
    logs=[]
    def run(command):
        env=dict(os.environ,VIDEO_FORMAT='NTSC')
        done=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
        logs.append(done.stdout)
        if done.returncode:
            raise RuntimeError(done.stdout[-5000:])
    for angle,color in ((1,'red'),(2,'green'),(3,'blue')):
        run([ffmpeg,'-hide_banner','-loglevel','warning','-f','lavfi','-i',
             'color=c='+color+':s=720x480:r=30000/1001',
             '-f','lavfi','-i',f'sine=frequency={angle*440}:sample_rate=48000',
             '-t','24','-vf',f"drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='REAL ANGLE {angle}':fontcolor=white:fontsize=54:x=50:y=170,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='%{{pts\\:hms}}':fontcolor=white:fontsize=38:x=50:y=250",
             '-target','ntsc-dvd','-aspect','4:3','-b:v','2500k','-g','15','-c:a','ac3','-b:a','192k','-ac','2',str(work/f'angle{angle}.vob')])
    xml='<dvdauthor dest="'+str(output)+'"><vmgm/><titleset><titles><video format="ntsc" aspect="4:3"/>'
    xml+=''.join('<pgc><vob file="'+str(work/f'angle{i}.vob')+'"/></pgc>' for i in (1,2,3))
    xml+='</titles></titleset></dvdauthor>'
    control=work/'author.xml';control.write_text(xml)
    run([dvdauthor,'-x',str(control)])
    title=output/'VIDEO_TS/VTS_01_0.IFO'
    title_bytes=angle_ifo(title.read_bytes())
    title.write_bytes(title_bytes); title.with_suffix('.BUP').write_bytes(title_bytes)
    vmg=output/'VIDEO_TS/VIDEO_TS.IFO'; data=bytearray(vmg.read_bytes())
    table=struct.unpack_from('>I',data,0xc4)[0]*2048
    if struct.unpack_from('>H',data,table)[0]!=3:
        raise ValueError('Expected exactly three authored title references')
    data[table+9]=3
    vmg.write_bytes(data);vmg.with_suffix('.BUP').write_bytes(data)
    vob=output/'VIDEO_TS/VTS_01_1.VOB'
    vob.write_bytes(angle_vob(vob.read_bytes()))
    report={'schemaVersion':1,'task':'VCE-014','kind':'real-three-branch-nonseamless-angle',
            'title':1,'angles':3,'durationSeconds':24,'physicalVobIds':[1,2,3],
            'videoSystem':'NTSC','independentReaderRequired':True,
            'files':{p.relative_to(output).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
                     for p in sorted((output/'VIDEO_TS').iterdir()) if p.is_file()}}
    (output/'fixture.json').write_text(json.dumps(report,indent=2)+'\n')
    (work/'author.log').write_text('\n'.join(logs))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--ffmpeg',required=True)
    parser.add_argument('--dvdauthor',default='dvdauthor')
    args=parser.parse_args()
    print(json.dumps(create(args.output,args.work,args.ffmpeg,args.dvdauthor),indent=2))
