#!/usr/bin/env python3
"""Blocks APK publication when the approved GUI source is missing.

A structural smoke check only, not visual or functional equivalence.
Public source files only: never accesses private repositories.
"""
from pathlib import Path
import sys

REQUIRED = {
    "app/src/main/java/com/josxtm/autocontrol/community/gui/GuiShell.kt": [
        "GuiApp",
        "SNIFFER",
        "PES"
    ],
    "app/src/main/java/com/josxtm/autocontrol/community/gui/GuiDesign.kt": [
        "VisualTheme"
    ],
    "app/src/main/java/com/josxtm/autocontrol/community/gui/GuiPanels.kt": []
}


def check(root, required=REQUIRED):
    root = Path(root)
    errors = []
    for relative, markers in required.items():
        target = root / relative
        if not target.is_file():
            errors.append(relative + ": original GUI component not present")
            continue
        if markers:
            try:
                source = target.read_text(encoding="utf-8")
            except UnicodeError:
                errors.append(relative + ": unreadable GUI component")
                continue
            for marker in markers:
                if marker not in source:
                    errors.append(relative + ": expected UI landmark absent")
    return errors


if __name__ == "__main__":
    problems = check(sys.argv[1] if len(sys.argv) > 1 else ".")
    for problem in problems:
        print("GUI PARITY BLOCK:", problem)
    if problems:
        print("NOT APPROVED: restore original GUI and verify it visually.")
        sys.exit(1)
    print("Structural landmarks found; manual visual and functional approval still required.")
