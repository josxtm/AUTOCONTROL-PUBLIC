import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import zipfile
import struct

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from inspect_apk import inspect


class ApkInspectionTest(unittest.TestCase):
    def run_inspection(self, extra=None, permission="com.josxtm.autocontrol.community.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", version="2", resource="safe synthetic text", constants=None):
        def command(args, **kwargs):
            if "apksigner" in args[0]:
                return "Signer #1 certificate SHA-256 digest: synthetic-test-only"
            if "badging" in args:
                return ("package: name='com.josxtm.autocontrol.community' versionCode='" + version
                        + "'\nuses-permission: name='" + permission + "'")
            if "xmltree" in args:
                return "allowBackup(0x01010280)=(type 0x12)0x0"
            return resource
        with tempfile.TemporaryDirectory() as root:
            apk = Path(root) / "synthetic.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", "synthetic")
                if extra:
                    archive.writestr(extra, "synthetic")
                if constants is not None:
                    # Minimal synthetic DEX string table; external APK tools are mocked here.
                    dex = bytearray(112 + 4 * len(constants)); dex[:8] = b"dex\n035\x00"
                    struct.pack_into("<II", dex, 56, len(constants), 112)
                    for i, value in enumerate(constants):
                        encoded = value.encode("utf-8"); size = len(encoded)
                        struct.pack_into("<I", dex, 112 + 4 * i, len(dex))
                        while size >= 128:
                            dex.append((size & 127) | 128); size >>= 7
                        dex.append(size); dex.extend(encoded + b"\x00")
                    archive.writestr("classes.dex", dex)
            with patch("inspect_apk.subprocess.check_output", side_effect=command):
                return inspect(apk, root)

    def test_reviewed_package_passes(self):
        self.assertIn("PASS APK", self.run_inspection())

    def test_assets_and_unknown_payloads_blocked(self):
        for name in ["assets/catalog.txt", "res/raw/catalog.xml", "unknown.bin"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.run_inspection(extra=name)

    def test_network_permission_and_wrong_version_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(permission="android.permission.INTERNET")
        with self.assertRaises(ValueError):
            self.run_inspection(version="1")

    def test_sensitive_resource_blocked(self):
        with self.assertRaises(ValueError):
            self.run_inspection(resource=".".join(["192", "168", "77", "12"]))

    def test_exact_public_library_markers_and_loopbacks_pass(self):
        constants = ["-" * 5 + "BEGIN " + kind + " PRIVATE KEY" + "-" * 5
                     for kind in ("RSA", "DSA", "EC", "OPENSSH")]
        constants += [".".join(parts) for parts in (("0", "0", "0", "0"), ("127", "0", "0", "1"))]
        self.assertIn("PASS APK", self.run_inspection(constants=constants))

    def test_material_or_address_appended_to_marker_remains_blocked(self):
        marker = "-" * 5 + "BEGIN RSA PRIVATE KEY" + "-" * 5
        for value in [marker + "\nsynthetic-key-body", marker + " extra", ".".join(["192", "168", "77", "12"])]:
            with self.subTest(kind="unreviewed constant"), self.assertRaises(ValueError):
                self.run_inspection(constants=[value])
        with self.assertRaises(ValueError):
            self.run_inspection(resource=marker)

    def test_workflow_propagates_apk_inspection_failure(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/public-android.yml").read_text()
        verification = workflow.split("- name: Verify APK identity", 1)[1].split("- name:", 1)[0]
        self.assertIn("set -euo pipefail", verification)
