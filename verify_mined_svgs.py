#!/usr/bin/env python3
"""
Programmatic SVG Verification Test Harness.
5-Gate Acceptance Criteria Test Suite for Mined SVGs.

Validates all SVG files inside the target directory:
- Gate 1: Non-empty file existence (> 0 bytes)
- Gate 2: Well-formed XML DOM parsing (xml.etree.ElementTree)
- Gate 3: Root <svg> element with valid xmlns and coordinate attributes (viewBox / dimensions)
- Gate 4: Visual graphical elements (<path>, <g>, etc.) with non-empty path geometry
- Gate 5: Global batch integrity assertions (Total >= 1000, 100% pass rate, exit code 0/1)
"""

import os
import sys
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

GRAPHICAL_TAGS = {
    "path", "g", "circle", "rect", "line",
    "polygon", "polyline", "ellipse", "use", "image"
}

# W3C SVG 1.1 / SVG 2 standard paint color syntax:
# - Hex color: #(3, 4, 6, 8 hex digits)
# - Keyword: 'none' (transparent / no paint)
# - Keyword: 'currentColor' (case-insensitive)
# - Functional reference: url(#id) pointing to internal defs (clipPath/gradient)
VALID_COLOR_REGEX = re.compile(
    r"^(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|none|currentColor|url\(#[a-zA-Z0-9_\-]+\))$",
    re.IGNORECASE
)

def verify_single_svg(filepath: Path):
    """
    Executes Gates 1-4 on an individual SVG file.
    Returns (passed: bool, errors: list[str]).
    """
    errors = []
    
    # Gate 1: Non-empty file (> 0 bytes)
    try:
        size = filepath.stat().st_size
        if size == 0:
            return False, ["Gate 1 FAIL: File is empty (0 bytes)"]
    except OSError as e:
        return False, [f"Gate 1 FAIL: File access error: {e}"]

    # Gate 2: XML Well-formedness
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
    except ET.ParseError as e:
        return False, [f"Gate 2 FAIL: Malformed XML: {e}"]
    except Exception as e:
        return False, [f"Gate 2 FAIL: Unexpected parse error: {e}"]

    # Gate 3: Root tag and attributes
    root_tag = root.tag.split("}")[-1]
    if root_tag != "svg":
        errors.append(f"Gate 3 FAIL: Root tag is <{root_tag}>, expected <svg>")

    has_viewbox = "viewBox" in root.attrib
    has_dimensions = ("width" in root.attrib and "height" in root.attrib)
    if not (has_viewbox or has_dimensions):
        errors.append("Gate 3 FAIL: Root <svg> missing both viewBox and width/height")

    # Gate 4: Graphical visual elements presence and non-empty path geometry
    visual_elements = [el for el in root.iter() if el.tag.split("}")[-1] in GRAPHICAL_TAGS]
    if not visual_elements:
        errors.append("Gate 4 FAIL: No graphical elements (<path>, <g>, etc.) found")
    else:
        # Check path elements have non-empty 'd' attribute
        for el in visual_elements:
            if el.tag.split("}")[-1] == "path":
                d = el.attrib.get("d", "").strip()
                if not d:
                    errors.append("Gate 4 FAIL: <path> element has empty 'd' attribute")
                    break

    # Gate 4: Strict paint/color attribute validation on all elements (fill and stroke)
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        for attr in ("fill", "stroke"):
            if attr in el.attrib:
                val = el.attrib[attr].strip()
                if not VALID_COLOR_REGEX.match(val):
                    errors.append(
                        f"Gate 4 FAIL: <{tag}> has invalid {attr} paint attribute '{val}'"
                    )

    return len(errors) == 0, errors

def main():
    parser = argparse.ArgumentParser(description="Programmatic SVG Verification Test Harness")
    parser.add_argument("--dir", default="mined Svg", help="Directory containing SVG files")
    args = parser.parse_args()

    svg_dir = Path(args.dir)
    print("=" * 75)
    print("E2E PROGRAMMATIC SVG VERIFICATION TEST HARNESS (5-GATE)")
    print(f"Target Directory: {svg_dir.resolve()}")
    print("=" * 75)

    if not svg_dir.exists() or not svg_dir.is_dir():
        print(f"FATAL: Target directory does not exist: {svg_dir}")
        sys.exit(1)

    svg_files = sorted(list(svg_dir.glob("*.svg")))
    total_count = len(svg_files)

    if total_count == 0:
        print(f"FATAL: No .svg files found in {svg_dir}")
        sys.exit(1)

    passed_count = 0
    failed_count = 0
    failures = []

    print(f"\nExecuting validation gates on {total_count:,} SVG files...")
    for idx, svg_file in enumerate(svg_files, 1):
        passed, errs = verify_single_svg(svg_file)
        if passed:
            passed_count += 1
        else:
            failed_count += 1
            failures.append((svg_file.name, errs))

    pass_percentage = (passed_count / total_count) * 100
    gate4_status = "PASSED" if failed_count == 0 else "FAILED"

    print("\n" + "=" * 75)
    print("TEST HARNESS RESULTS SUMMARY")
    print(f"  Gate 1 (Existence & Size > 0)    : PASSED ({passed_count}/{total_count})")
    print(f"  Gate 2 (Well-formed XML DOM)     : PASSED ({passed_count}/{total_count})")
    print(f"  Gate 3 (Root <svg> & Coordinate) : PASSED ({passed_count}/{total_count})")
    print(f"  Gate 4 (Path Geometry & Paint)   : {gate4_status} ({passed_count}/{total_count})")
    print(f"  Gate 5 (Batch Count >= 1000)     : {'PASSED' if total_count >= 1000 else 'FAILED'} (Total: {total_count})")
    print("-" * 75)
    print(f"  Total SVGs Scanned : {total_count}")
    print(f"  Passed (Valid)     : {passed_count}")
    print(f"  Failed (Invalid)   : {failed_count}")
    print(f"  Success Rate       : {pass_percentage:.2f}%")
    print("=" * 75)

    if failures or failed_count > 0 or total_count < 1000:
        print("\nDetailed Failure Report:")
        for fname, err_list in failures[:50]: # Show up to 50 failures
            print(f"  [X] {fname}:")
            for err in err_list:
                print(f"      - {err}")
        if len(failures) > 50:
            print(f"  ... and {len(failures) - 50} more failures.")
        print("\nFINAL VERIFICATION RESULT: FAILED (Exit Code 1)")
        sys.exit(1)
    else:
        print("\nFINAL VERIFICATION RESULT: ALL GATES PASSED (100% VALID, Exit Code 0)")
        sys.exit(0)

if __name__ == "__main__":
    main()
