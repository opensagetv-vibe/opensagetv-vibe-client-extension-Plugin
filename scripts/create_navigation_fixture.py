#!/usr/bin/env python3
"""Copy an owned authored DVD with one invalid previous-PGC link for VCE-007.

Never run against commercial/user media. Source remains unchanged; destination
must not exist. This is an opt-in robustness fixture, not an install operation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct


def invalid_previous_pgc(data: bytes) -> tuple[bytes, dict[str, int]]:
    if data[:12] != b"DVDVIDEO-VTS":
        raise ValueError("Expected the generated DVD's VTS IFO")
    table = struct.unpack_from(">I", data, 0xCC)[0] * 2048
    count = struct.unpack_from(">H", data, table)[0]
    if not 1 <= count < 10:
        raise ValueError("Unexpected generated fixture's PGC count")
    pgc = table + struct.unpack_from(">I", data, table + 12)[0]
    if not (1 <= data[pgc + 2] <= 10 and 1 <= data[pgc + 3] <= 20):
        raise ValueError("Unexpected generated title structure")
    # DVD-Video PGC layout: next/previous/go-up unsigned16 at156/158/160.
    before = struct.unpack_from(">H", data, pgc + 158)[0]
    if before != 0:
        raise ValueError("Refusing to overwrite an existing previous-PGC link")
    result = bytearray(data)
    struct.pack_into(">H", result, pgc + 158, count + 1)
    return bytes(result), {"pgcitOffset": table, "pgcOffset": pgc,
        "declaredPGCCount": count, "previousPGCBefore": before,
        "previousPGCAfter": count + 1}


def create(source: Path, target: Path) -> dict:
    source = source.resolve(strict=True)
    # Reject symlinks before resolving so a user cannot accidentally target
    # the original disc or reuse an existing output tree.
    if target.exists() or target.is_symlink():
        raise ValueError("Destination must not exist")
    target = target.resolve()
    if target == source or source in target.parents or target in source.parents:
        raise ValueError("Source and destination must be separate trees")
    files = sorted(source.iterdir())
    expected = ["VIDEO_TS.BUP", "VIDEO_TS.IFO", "VTS_01_0.BUP", "VTS_01_0.IFO",
                "VTS_01_0.VOB", "VTS_01_1.VOB"]
    if [p.name for p in files] != expected or any(p.is_symlink() or not p.is_file() for p in files):
        raise ValueError("Expected the owned generated six-file VIDEO_TS fixture")
    original = (source / "VTS_01_0.IFO").read_bytes()
    if (source / "VTS_01_0.BUP").read_bytes() != original:
        raise ValueError("Original IFO and backup differ")
    modified, report = invalid_previous_pgc(original)
    target.mkdir(parents=True, exist_ok=False)
    rows = []
    for item in files:
        output = target / item.name
        if item.name in ("VTS_01_0.IFO", "VTS_01_0.BUP"):
            output.write_bytes(modified)
        else:
            shutil.copyfile(item, output)
        rows.append({"file": item.name, "sha256": hashlib.sha256(output.read_bytes()).hexdigest()})
    return {"task": "VCE-007", **report, "files": rows,
            "trigger": "Main feature chapter1 then supported DVD Prev Chapter"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-video-ts", required=True, type=Path)
    parser.add_argument("--output-video-ts", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(create(args.source_video_ts, args.output_video_ts), indent=2))
