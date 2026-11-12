"""Regression: the APK audit excludes only exact, inert library format markers."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from inspect_apk import SAFE_DEX_CONSTANTS
from privacy_guard import IP, KEY


class ApkPrivacyConstantsTest(unittest.TestCase):
    def test_only_exact_nonoperational_ip_markers(self):
        local = ".".join(("127", "0", "0", "1"))
        wildcard = ".".join(("0", "0", "0", "0"))
        external = ".".join(("198", "51", "100", "45"))
        self.assertIn(local, SAFE_DEX_CONSTANTS)
        self.assertIn(wildcard, SAFE_DEX_CONSTANTS)
        self.assertTrue(IP.search(external))
        self.assertNotIn(external, SAFE_DEX_CONSTANTS)

    def test_key_format_markers_are_not_actual_key_material(self):
        rsa_header = "-----BEGIN " + "RSA" + " PRIVATE KEY-----"
        self.assertIn(rsa_header, SAFE_DEX_CONSTANTS)
        self.assertTrue(KEY.search(rsa_header))
        self.assertNotIn(rsa_header + "\nsynthetic-key-data", SAFE_DEX_CONSTANTS)
        self.assertNotIn("github_" + "pat_synthetic_not_allowed", SAFE_DEX_CONSTANTS)


if __name__ == "__main__":
    unittest.main()
