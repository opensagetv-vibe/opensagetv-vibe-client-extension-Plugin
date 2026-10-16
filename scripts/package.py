#!/usr/bin/env python3
"""Build deterministic inspected packages; never publish automatically."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import stat
import struct
import subprocess
import zipfile

from validate_manifest import ROOT, validate

OUTPUT = ROOT / "output" / "packages"
JAR = ROOT / "dist" / "dev" / "VibeClientExtensionPlugin.jar"
REQUIRED_CLASSES = {"org/opensagetv/vibe/clientextension/ClientExtensionPlugin.class",
                    "org/opensagetv/vibe/clientextension/ClientExtensionDvdPlayer.class"}


def inspect_jar(data: bytes) -> None:
    """Allow only our Java 8 class payload and its manifest, never Core/secrets."""
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        entries = jar.namelist()
        if len(entries) != len(set(entries)) or not REQUIRED_CLASSES.issubset(entries):
            raise ValueError("Missing or duplicate plugin classes")
        for name in entries:
            if name == "META-INF/MANIFEST.MF":
                continue
            if (not name.startswith("org/opensagetv/vibe/clientextension/") or not name.endswith(".class")
                    or ".." in PurePosixPath(name).parts or "\\" in name):
                raise ValueError("Foreign Core/resource/credential payload: " + name)
            class_data = jar.read(name)
            if len(class_data) < 8 or class_data[:4] != b"\xca\xfe\xba\xbe" or struct.unpack(">H", class_data[6:8])[0] != 52:
                raise ValueError("Plugin classes must target Java 8: " + name)


def archive(path: Path, name: str, data: bytes, date: str) -> None:
    timestamp = dt.datetime.strptime(date, "%Y.%m.%d")
    info = zipfile.ZipInfo(name, (timestamp.year, timestamp.month, timestamp.day, 0, 0, 0))
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | 0o644) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    with zipfile.ZipFile(path, "w") as target:
        target.writestr(info, data)


def package(root: Path = ROOT, output: Path = OUTPUT, jar: Path = JAR,
            require_approved: bool = False) -> Path:
    values = validate(root, require_approved)
    if not jar.is_file() or jar.is_symlink():
        raise ValueError("Build the stock-JAR-compatible plugin first")
    data = jar.read_bytes()
    inspect_jar(data)
    output.mkdir(parents=True, exist_ok=True)
    release = values["VERSION"]
    payload = output / f"VibeClientExtensionPlugin-jar-{release}.zip"
    archive(payload, jar.name, data, values["RELEASE_DATE"])
    template = (root / "manifests/VibeClientExtensionPlugin.xml.template").read_text(encoding="utf-8")
    rendered = (template.replace("@DATE@", values["RELEASE_DATE"])
               .replace("@VERSION@", release)
               .replace("@JAR_ZIP_MD5@", hashlib.md5(payload.read_bytes()).hexdigest()))
    public = output / "VibeClientExtensionPlugin.xml"
    public.write_text(rendered, encoding="utf-8", newline="\n")
    dev = rendered.replace(
        f"https://github.com/opensagetv-vibe/{values['PACKAGE_ID']}/releases/download/v{release}/{payload.name}",
        f"file:/opt/sagetv/server/SageTVPluginsDev.d/{payload.name}",
    )
    (output / "VibeClientExtensionPlugin-dev.xml").write_text(dev, encoding="utf-8", newline="\n")
    # Only this version's public assets belong in its manifest/checksum list.
    # Exclude old packages and the local-development file: URL descriptor.
    assets = [payload, public]
    manifest = {"schemaVersion": 1, "project": values["PACKAGE_ID"], "version": release,
                "releaseDate": values["RELEASE_DATE"], "publishApproved": values["PUBLISH_APPROVED"] == "true",
                "jarSha256": hashlib.sha256(data).hexdigest(),
                "assets": {path.name: {"size": path.stat().st_size,
                                      "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in assets}}
    source_manifest = root / "PROJECT_MANIFEST.sha256"
    if source_manifest.is_file():
        # Integrity was checked in CI/preflight; tie the downloadable package
        # to the exact source manifest without copying private build inputs.
        manifest["sourceManifestSha256"] = hashlib.sha256(source_manifest.read_bytes()).hexdigest()
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True)
    if revision.returncode == 0:
        manifest["sourceRevision"] = revision.stdout.strip()
    descriptor = output / "github-release-manifest.json"
    descriptor.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    assets.append(descriptor)
    sums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n" for path in sorted(assets))
    (output / "SHA256SUMS").write_text(sums, encoding="utf-8", newline="\n")
    return payload


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-approved", action="store_true")
    args = parser.parse_args()
    print(f"PASS: packaged {package(require_approved=args.require_approved).name}")
