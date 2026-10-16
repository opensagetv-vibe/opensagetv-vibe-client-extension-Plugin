"""Host-only release contracts using disposable fake Java 8 payloads."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import package
import project_manifest
import validate_manifest


def fake_jar(extra: dict[str, bytes] | None = None, major: int = 52) -> bytes:
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as jar:
        for name in sorted(package.REQUIRED_CLASSES):
            jar.writestr(name, b"\xca\xfe\xba\xbe" + struct.pack(">HH", 0, major))
        for name, payload in (extra or {}).items():
            jar.writestr(name, payload)
    return data.getvalue()


class PackageTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="vibe-extension-package-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "manifests").mkdir()
        template = (ROOT / "manifests/VibeClientExtensionPlugin.xml.template").read_text(encoding="utf-8")
        (self.root / "manifests/VibeClientExtensionPlugin.xml.template").write_text(template, encoding="utf-8")
        (self.root / "VERSION").write_text("0.1.0\n", encoding="utf-8")
        self.properties = "VERSION=0.1.0\nPACKAGE_ID=opensagetv-vibe-client-extension-Plugin\nREQUIRES_BUILD=true\nPUBLISH_APPROVED=false\nRELEASE_DATE=2026.10.09\n"
        (self.root / "release.properties").write_text(self.properties, encoding="utf-8")
        self.jar = self.root / "VibeClientExtensionPlugin.jar"
        self.jar.write_bytes(fake_jar())

    def test_development_and_approved_metadata_have_distinct_gates(self) -> None:
        validate_manifest.validate(self.root)
        with self.assertRaisesRegex(ValueError, "explicit PUBLISH_APPROVED"):
            validate_manifest.validate(self.root, require_approved=True)
        (self.root / "release.properties").write_text(self.properties.replace("false", "true"), encoding="utf-8")
        validate_manifest.validate(self.root, require_approved=True)

    def test_version_mismatch_and_non_numeric_version_rejected(self) -> None:
        (self.root / "VERSION").write_text("0.1.1", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "disagree"):
            validate_manifest.validate(self.root)
        (self.root / "release.properties").write_text(self.properties.replace("0.1.0", "0.1.0-DEV"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "numeric"):
            validate_manifest.validate(self.root)

    def test_publication_requires_explicit_date(self) -> None:
        (self.root / "release.properties").write_text(self.properties.replace("false", "true").replace("RELEASE_DATE=2026.10.09\n", ""), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "requires RELEASE_DATE"):
            validate_manifest.validate(self.root, require_approved=True)

    def test_private_core_secrets_and_non_java8_classes_rejected(self) -> None:
        for name in ("sage/Sage.class", "config/credentials.toml", "org/opensagetv/vibe/clientextension/../password.class"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                package.inspect_jar(fake_jar({name: b"secret"}))
        with self.assertRaisesRegex(ValueError, "Java 8"):
            package.inspect_jar(fake_jar(major=61))

    def test_package_is_reproducible_with_verified_md5_sha256_and_date(self) -> None:
        first, second = self.root / "first", self.root / "second"
        payload = package.package(self.root, first, self.jar)
        twin = package.package(self.root, second, self.jar)
        self.assertEqual(payload.read_bytes(), twin.read_bytes())
        with zipfile.ZipFile(payload) as archive:
            self.assertEqual(archive.namelist(), [self.jar.name])
            self.assertEqual(archive.getinfo(self.jar.name).date_time, (2026, 10, 9, 0, 0, 0))
        xml = ET.parse(first / "VibeClientExtensionPlugin.xml")
        self.assertEqual(xml.findtext("Package/MD5"), hashlib.md5(payload.read_bytes()).hexdigest())
        self.assertEqual(xml.findtext("ModificationDate"), "2026.10.09")
        report = json.loads((first / "github-release-manifest.json").read_text())
        self.assertEqual(report["jarSha256"], hashlib.sha256(self.jar.read_bytes()).hexdigest())
        for line in (first / "SHA256SUMS").read_text().splitlines():
            sha, name = line.split("  ")
            self.assertEqual(sha, hashlib.sha256((first / name).read_bytes()).hexdigest())
        self.assertNotIn("-dev.xml", (first / "SHA256SUMS").read_text())
        self.assertFalse(report["publishApproved"])

    def test_old_package_does_not_leak_into_new_checksums(self) -> None:
        output = self.root / "output"
        output.mkdir()
        (output / "obsolete.zip").write_bytes(b"old")
        package.package(self.root, output, self.jar)
        self.assertNotIn("obsolete", (output / "SHA256SUMS").read_text())

    def test_source_manifest_normalizes_text_and_rejects_private_files(self) -> None:
        first, second = self.root / "a", self.root / "b"
        first.write_bytes(b"hello\r\n")
        second.write_bytes(b"hello\n")
        self.assertEqual(project_manifest.digest(first), project_manifest.digest(second))
        for name in (".deps/stock/Sage.jar", "output/package.zip", "../adbkey", "adb/adbkey", "credentials.toml", "config/signing.p12"):
            self.assertFalse(project_manifest.allowed(name), name)
        self.assertTrue(project_manifest.allowed("src/main/java/org/Example.java"))
        self.assertTrue(project_manifest.allowed("artifacts/downloads/.gitkeep"))
        # The scanner's own source must not match its private-key sentinel.
        project_manifest.digest(ROOT / "scripts/project_manifest.py")
        first.write_bytes(b"-----BEGIN " + b"PRIVATE KEY-----\nsecret")
        with self.assertRaisesRegex(ValueError, "Private signing"):
            project_manifest.digest(first)

    def test_ci_is_source_only_and_runs_the_new_contracts(self) -> None:
        workflow = (ROOT / ".github/workflows/repository-checks.yml").read_text(encoding="utf-8")
        for requirement in ("actions/checkout@v7", "contents: read", "scripts/project_manifest.py --check", "unittest discover"):
            self.assertIn(requirement, workflow)
        self.assertNotIn("SAGETV_JAR", workflow)


if __name__ == "__main__":
    unittest.main()
