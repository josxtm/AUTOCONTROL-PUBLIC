import sys
import tempfile
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from gui_parity_gate import check


class PublicGuiParityTest(unittest.TestCase):
    def test_missing_component_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertTrue(check(root, {"ui/Screen.kt": ["Scaffold"]}))

    def test_placeholder_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            candidate = Path(root) / "ui/Screen.kt"
            candidate.parent.mkdir()
            candidate.write_text("class Placeholder", encoding="utf-8")
            self.assertTrue(check(root, {"ui/Screen.kt": ["Scaffold"]}))

    def test_structural_landmark_present(self):
        with tempfile.TemporaryDirectory() as root:
            candidate = Path(root) / "ui/Screen.kt"
            candidate.parent.mkdir()
            candidate.write_text("Scaffold originalScreen", encoding="utf-8")
            self.assertEqual(check(root, {"ui/Screen.kt": ["Scaffold"]}), [])


if __name__ == "__main__":
    unittest.main()
