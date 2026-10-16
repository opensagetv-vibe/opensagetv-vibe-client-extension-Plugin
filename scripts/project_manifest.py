#!/usr/bin/env python3
"""Create/check the Vibe tracked-source manifest; exclude private build inputs."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import subprocess
import sys

MANIFEST = "PROJECT_MANIFEST.sha256"
PRIVATE_DIRECTORIES = {".deps", "build", "dist", "output", "__pycache__", ".venv", "adb"}
PRIVATE_NAMES = {"Sage.jar", "adbkey", "adbkey.pub", ".env", "credentials.toml"}


def allowed(name: str) -> bool:
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or "\\" in name:
        return False
    if PRIVATE_DIRECTORIES.intersection(path.parts) or path.name in PRIVATE_NAMES:
        return False
    if path.suffix.lower() in {".pem", ".p12", ".pfx", ".jks", ".keystore"}:
        return False
    if path.parts[0] == "artifacts" and name != "artifacts/downloads/.gitkeep":
        return False
    return True


def tracked(root: Path) -> list[str]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True)
    names = sorted(item.decode("utf-8") for item in result.stdout.split(b"\0") if item)
    for name in names:
        if not allowed(name) or (root / name).is_symlink():
            raise ValueError("Private/generated/unsafe tracked source: " + name)
    return [name for name in names if name != MANIFEST]


def digest(path: Path) -> str:
    data = path.read_bytes()
    # Describe logical UTF-8 source; Git may materialize CRLF on Windows.
    if b"\0" not in data:
        try:
            data.decode("utf-8")
        except UnicodeDecodeError:
            pass
        else:
            data = data.replace(b"\r\n", b"\n")
    key_headers = (b"-----BEGIN " + b"PRIVATE KEY-----", b"-----BEGIN " + b"RSA PRIVATE KEY-----")
    if any(header in data for header in key_headers):
        raise ValueError("Private signing/authorization key in tracked source")
    return hashlib.sha256(data).hexdigest()


def render(root: Path) -> str:
    return "".join(f"{digest(root / name)}  {name}\n" for name in tracked(root))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    expected = render(root)
    if not expected:
        raise ValueError("Stage source before generating the first source manifest")
    path = root / MANIFEST
    if args.write:
        path.write_text(expected, encoding="utf-8", newline="\n")
        print(f"WROTE: {MANIFEST} ({len(expected.splitlines())} files)")
        return 0
    if not path.is_file() or path.read_text(encoding="utf-8") != expected:
        print(f"ERROR: {MANIFEST} missing or stale", file=sys.stderr)
        return 1
    print(f"PASS: {MANIFEST} ({len(expected.splitlines())} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
