"""
Verification script for Milestone 1 Color tokens in Color.kt:
- spectrum_surface_dark strictly == Color(0xFF1D1D1D)
- SpectrumBlue strictly == Color(0xFF2680EB)
- loupe_background strictly == Color(0xFF000000)

Also checks aliases:
- SpectrumSurface == spectrum_surface_dark
- SpectrumCanvas == loupe_background
- SpectrumBlueActive == Color(0xFF2680EB)
- SpectrumActiveDot == Color(0xFF2680EB)
"""

import os
import re
import sys

COLOR_FILE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "CleanLightroom",
    "app",
    "src",
    "main",
    "kotlin",
    "com",
    "cleanlr",
    "ui",
    "theme",
    "Color.kt"
)

def verify_colors():
    print("=" * 60)
    print("Milestone 1 Color Tokens Empirical Verification")
    print("=" * 60)

    if not os.path.exists(COLOR_FILE):
        print(f"FAIL: Color.kt not found at: {COLOR_FILE}")
        sys.exit(1)

    with open(COLOR_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    patterns = {
        "spectrum_surface_dark": r"val\s+spectrum_surface_dark\s*=\s*Color\(0xFF1D1D1D\)",
        "SpectrumBlue": r"val\s+SpectrumBlue\s*=\s*Color\(0xFF2680EB\)",
        "loupe_background": r"val\s+loupe_background\s*=\s*Color\(0xFF000000\)",
        "SpectrumSurface": r"val\s+SpectrumSurface\s*=\s*spectrum_surface_dark",
        "SpectrumCanvas": r"val\s+SpectrumCanvas\s*=\s*loupe_background",
        "SpectrumBlueActive": r"val\s+SpectrumBlueActive\s*=\s*Color\(0xFF2680EB\)",
        "SpectrumActiveDot": r"val\s+SpectrumActiveDot\s*=\s*Color\(0xFF2680EB\)"
    }

    errors = []
    for name, pattern in patterns.items():
        match = re.search(pattern, content)
        if match:
            print(f"PASS: {name} matches expected definition: '{match.group(0)}'")
        else:
            # Find what it actually is
            actual = re.search(rf"val\s+{name}\s*=\s*([^\r\n]+)", content)
            actual_val = actual.group(0) if actual else "NOT FOUND"
            print(f"FAIL: {name} did not match pattern! Actual: {actual_val}")
            errors.append(f"{name}: actual was '{actual_val}'")

    print("\n" + "=" * 60)
    if errors:
        print(f"FAILED: {len(errors)} color token checks failed")
        sys.exit(1)
    else:
        print("ALL COLOR TOKENS PASSED (7/7)")
        sys.exit(0)

if __name__ == "__main__":
    verify_colors()
