#!/usr/bin/env python3
"""Owned VCE-014 fixture: relocate Spanish SPU0x21 to0x22, preserving logical1.

Only accepts the hash-verified generated canonical DVD. Never edits the source,
existing destinations, commercial media, navigation addresses or packet sizes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct


def relocate_vob(data: bytes) -> tuple[bytes, int]:
    if len(data) % 2048:
        raise ValueError("Expected sector-aligned owned VOB")
    result = bytearray(data)
    changed = 0
    offset = 0
    while True:
        offset = data.find(b"\x00\x00\x01\xbd", offset)
        if offset < 0:
            break
        if offset + 9 > len(data):
            raise ValueError("Truncated private PES")
        length = int.from_bytes(data[offset+4:offset+6], "big")
        end = offset + 6 + length
        payload = offset + 9 + data[offset+8]
        if end > len(data) or payload >= end or data[offset+6] & 0xc0 != 0x80:
            raise ValueError("Invalid private PES")
        if data[payload] == 0x22:
            raise ValueError("Destination SPU already occupied")
        if data[payload] == 0x21:
            result[payload] = 0x22
            changed += 1
        offset = end
    return bytes(result), changed


def relocate_ifo(data: bytes) -> tuple[bytes, int]:
    if data[:12] != b"DVDVIDEO-VTS":
        raise ValueError("Expected generated title IFO")
    table = struct.unpack_from(">I", data, 0xcc)[0] * 2048
    count = struct.unpack_from(">H", data, table)[0]
    if not 1 <= count <= 8:
        raise ValueError("Unexpected generated title PGC count")
    result = bytearray(data)
    changed = 0
    for index in range(count):
        pgc = table + struct.unpack_from(">I", data, table + 12 + index * 8)[0]
        start = pgc + 28 + 4  # subp_control[1]; logical Spanish remains index1.
        control = data[start:start+4]
        if len(control) != 4 or not control[0] & 0x80:
            raise ValueError("Expected enabled logical Spanish mapping")
        for mode, byte in enumerate(control):
            if byte & 0x1f == 1:
                result[start + mode] = (byte & 0xe0) | 2
                changed += 1
    if not changed:
        raise ValueError("No Spanish physical mapping")
    return bytes(result), changed


def create(source: Path, target: Path) -> dict:
    source = source.resolve(strict=True)
    if target.exists() or target.is_symlink():
        raise ValueError("Destination must not exist")
    target = target.resolve()
    if source == target or source in target.parents or target in source.parents:
        raise ValueError("Separate source and output required")
    manifest = json.loads((source / "VIBE_DVD_TEST_MANIFEST.json").read_text())
    if manifest.get("fixtureId") != "vibe-authored-dvd-v2" or manifest.get("buildMode") != "authored":
        raise ValueError("Only owned authored fixture accepted")
    checks = (source / "VIDEO_TS_SHA256SUMS.txt").read_text().splitlines()
    files = []
    for check in checks:
        digest, name = check.split(None, 1)
        path = Path(name.strip())
        if len(path.parts) != 2 or path.parts[0] != "VIDEO_TS" or path.name not in {
            "VIDEO_TS.IFO", "VIDEO_TS.BUP", "VTS_01_0.IFO", "VTS_01_0.BUP", "VTS_01_0.VOB", "VTS_01_1.VOB"
        }:
            raise ValueError("Unexpected generated source path")
        item = source / path
        if item.is_symlink() or hashlib.sha256(item.read_bytes()).hexdigest() != digest:
            raise ValueError("Original fixture hash mismatch")
        files.append(path)
    if len(set(files)) != 6:
        raise ValueError("Expected six unique source files")
    target.mkdir(parents=True, exist_ok=False)
    (target / "VIDEO_TS").mkdir()
    rows, packets = [], 0
    for path in files:
        data = (source / path).read_bytes()
        if path.name in ("VTS_01_0.IFO", "VTS_01_0.BUP"):
            data, count = relocate_ifo(data)
        elif path.suffix == ".VOB":
            data, count = relocate_vob(data)
            packets += count
        output = target / path
        output.write_bytes(data)
        rows.append({"file": path.as_posix(), "sha256": hashlib.sha256(data).hexdigest()})
    if not packets:
        raise ValueError("No relocated subtitle packets")
    report = {"task":"VCE-014", "sourceFixture":str(source), "logicalSpanish":1,
              "physicalSpuIds":[32,34], "physicalMiniClientSelectors":[64,66],
              "relocatedPesPackets":packets, "files":rows}
    (target / "SPARSE_SPU_FIXTURE.json").write_text(json.dumps(report, indent=2))
    (target / "VIDEO_TS_SHA256SUMS.txt").write_text("".join(
        row["sha256"] + "  " + row["file"] + "\n" for row in rows))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(create(args.source, args.output), indent=2))
