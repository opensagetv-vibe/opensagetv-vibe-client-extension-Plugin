#!/usr/bin/env python3
"""Create a class-only, timestamp-pinned SageTV plugin JAR."""
from __future__ import annotations

from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ROOT / "build" / "classes"
DESTINATION = ROOT / "dist" / "dev" / "VibeClientExtensionPlugin.jar"


def add(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, (2026, 10, 4, 0, 0, 0))
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | 0o644) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data)


def main() -> None:
    sources = sorted(CLASSES.rglob("*.class"))
    if not sources:
        raise RuntimeError("No compiled plugin classes")
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    temp = DESTINATION.with_suffix(".jar.tmp")
    with zipfile.ZipFile(temp, "w") as archive:
        add(archive, "META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\r\n\r\n")
        for source in sources:
            name = source.relative_to(CLASSES).as_posix()
            if name.startswith("sage/"):
                raise RuntimeError("Stock SageTV class leaked into plugin JAR")
            add(archive, name, source.read_bytes())
    temp.replace(DESTINATION)


if __name__ == "__main__":
    main()
