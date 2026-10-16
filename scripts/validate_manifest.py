#!/usr/bin/env python3
"""Validate development and approved metadata without a private Sage.jar."""
from __future__ import annotations

import argparse
import datetime as dt
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE_ID = "opensagetv-vibe-client-extension-Plugin"


def metadata(root: Path = ROOT, require_approved: bool = False) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in (root / "release.properties").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        if key.strip() in values:
            raise ValueError("Duplicate release property: " + key)
        values[key.strip()] = value.strip()
    release = values.get("VERSION", "")
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", release):
        raise ValueError("VERSION must be an immutable numeric major.minor.patch")
    if (root / "VERSION").read_text(encoding="utf-8").strip() != release:
        raise ValueError("VERSION and release.properties disagree")
    if values.get("PACKAGE_ID") != PACKAGE_ID or values.get("REQUIRES_BUILD") != "true":
        raise ValueError("Unexpected package identity or build requirement")
    approval = values.get("PUBLISH_APPROVED")
    if approval not in {"true", "false"} or (require_approved and approval != "true"):
        raise ValueError("Publication requires explicit PUBLISH_APPROVED=true")
    template = ET.fromstring((root / "manifests/VibeClientExtensionPlugin.xml.template").read_text(encoding="utf-8"))
    # Pin development builds to creation; publication must state its reviewed date.
    date = values.get("RELEASE_DATE", template.findtext("CreationDate", ""))
    if require_approved and "RELEASE_DATE" not in values:
        raise ValueError("Approved publication requires RELEASE_DATE")
    parsed = dt.datetime.strptime(date, "%Y.%m.%d")
    if parsed.strftime("%Y.%m.%d") != date or parsed.year < 1980:
        raise ValueError("RELEASE_DATE must be YYYY.MM.DD and ZIP-compatible")
    values["RELEASE_DATE"] = date
    return values


def validate(root: Path = ROOT, require_approved: bool = False) -> dict[str, str]:
    values = metadata(root, require_approved)
    template = (root / "manifests/VibeClientExtensionPlugin.xml.template").read_text(encoding="utf-8")
    plugin = ET.fromstring(template.replace("@DATE@", values["RELEASE_DATE"])
                           .replace("@VERSION@", values["VERSION"])
                           .replace("@JAR_ZIP_MD5@", "0" * 32))
    expected = {"PluginType": "Standard", "ImplementationClass": "org.opensagetv.vibe.clientextension.ClientExtensionPlugin",
                "ServerOnly": "true", "Desktop": "false", "Dependency/MinVersion": "9.2.2",
                "Identifier": "OpenSageTVVibeClientExtensionPlugin", "Version": values["VERSION"],
                "ModificationDate": values["RELEASE_DATE"]}
    for field, value in expected.items():
        if plugin.findtext(field) != value:
            raise ValueError("Invalid manifest field: " + field)
    if {node.text for node in plugin.findall("OS")} != {"Linux", "Win"}:
        raise ValueError("Plugin must support Linux and Windows")
    packages = plugin.findall("Package")
    location = (f"https://github.com/opensagetv-vibe/{PACKAGE_ID}/releases/download/"
                f"v{values['VERSION']}/VibeClientExtensionPlugin-jar-{values['VERSION']}.zip")
    if len(packages) != 1 or packages[0].findtext("PackageType") != "JAR" or packages[0].findtext("Location") != location:
        raise ValueError("Unexpected payload or release location")
    if "@" in ET.tostring(plugin, encoding="unicode"):
        raise ValueError("Unexpanded manifest placeholder")
    return values


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-approved", action="store_true")
    args = parser.parse_args()
    result = validate(require_approved=args.require_approved)
    print(f"PASS: stock-compatible metadata v{result['VERSION']}; publish approved={result['PUBLISH_APPROVED']}")
