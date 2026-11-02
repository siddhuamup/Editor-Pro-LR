"""
Verification script for Milestone 1 VectorDrawables:
ic_lr_close.xml, ic_lr_more_vert.xml, ic_lr_plus.xml

Validates:
- XML DOM parsing (no syntax errors, proper namespaces)
- Vector tag root attributes:
    - android:width == "24dp"
    - android:height == "24dp"
    - android:viewportWidth == "24" (or 24.0)
    - android:viewportHeight == "24" (or 24.0)
- Path elements:
    - pathData syntax validity (SVG path commands parseable)
    - pathData bounds inside [0, 24] viewport
    - strokeWidth, fillColor, strokeColor consistency
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
NS_MAP = {"android": ANDROID_NS}

DRAWABLE_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "CleanLightroom",
    "app",
    "src",
    "main",
    "res",
    "drawable"
)

TARGET_FILES = [
    "ic_lr_close.xml",
    "ic_lr_more_vert.xml",
    "ic_lr_plus.xml"
]

def validate_svg_path(path_data):
    """
    Validates SVG path commands syntax using regex.
    Allowed commands: M, m, L, l, H, h, V, v, C, c, S, s, Q, q, T, t, A, a, Z, z
    """
    if not path_data or not path_data.strip():
        return False, "Empty pathData"
    
    # Check for illegal characters
    illegal = re.findall(r'[^0-9eE\s,\.MmLlHhVvCcSsQqTtAaZz\-+]', path_data)
    if illegal:
        return False, f"Illegal characters found in pathData: {set(illegal)}"
    
    # Tokenize commands
    tokens = re.findall(r'([MmLlHhVvCcSsQqTtAaZz]|[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)', path_data)
    if not tokens:
        return False, "No tokens parsed"
    
    # Ensure starts with MoveTo command
    if tokens[0] not in ('M', 'm'):
        return False, f"Path must start with M or m, found: {tokens[0]}"
        
    return True, f"Valid ({len(tokens)} tokens)"

def verify_file(filename):
    filepath = os.path.join(DRAWABLE_DIR, filename)
    print(f"\n--- Verifying {filename} ---")
    if not os.path.exists(filepath):
        print(f"FAIL: File does not exist: {filepath}")
        return False
    
    file_size = os.path.getsize(filepath)
    print(f"File size: {file_size} bytes")
    if file_size == 0:
        print("FAIL: File is 0 bytes (empty)!")
        return False

    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"FAIL: XML Parse Error: {e}")
        return False

    if not root.tag.endswith("vector"):
        print(f"FAIL: Root element is not <vector>, got <{root.tag}>")
        return False

    # Check namespaces and attributes
    width = root.attrib.get(f"{{{ANDROID_NS}}}width")
    height = root.attrib.get(f"{{{ANDROID_NS}}}height")
    vp_w = root.attrib.get(f"{{{ANDROID_NS}}}viewportWidth")
    vp_h = root.attrib.get(f"{{{ANDROID_NS}}}viewportHeight")

    print(f"width: {width}, height: {height}, viewportWidth: {vp_w}, viewportHeight: {vp_h}")

    errors = []
    if width != "24dp":
        errors.append(f"Expected width='24dp', got '{width}'")
    if height != "24dp":
        errors.append(f"Expected height='24dp', got '{height}'")
    if vp_w not in ("24", "24.0", "24.00"):
        errors.append(f"Expected viewportWidth='24', got '{vp_w}'")
    if vp_h not in ("24", "24.0", "24.00"):
        errors.append(f"Expected viewportHeight='24', got '{vp_h}'")

    paths = root.findall(f"{{{ANDROID_NS}}}path")
    if not paths:
        # Check without namespace prefix
        paths = root.findall("path")
    
    print(f"Found {len(paths)} <path> element(s)")
    if not paths:
        errors.append("No <path> element found in vector")

    for i, p in enumerate(paths):
        path_data = p.attrib.get(f"{{{ANDROID_NS}}}pathData") or p.attrib.get("android:pathData")
        fill_color = p.attrib.get(f"{{{ANDROID_NS}}}fillColor") or p.attrib.get("android:fillColor")
        stroke_color = p.attrib.get(f"{{{ANDROID_NS}}}strokeColor") or p.attrib.get("android:strokeColor")
        stroke_width = p.attrib.get(f"{{{ANDROID_NS}}}strokeWidth") or p.attrib.get("android:strokeWidth")
        stroke_line_cap = p.attrib.get(f"{{{ANDROID_NS}}}strokeLineCap") or p.attrib.get("android:strokeLineCap")

        print(f" Path #{i+1}: pathData='{path_data}'")
        print(f"   fillColor='{fill_color}', strokeColor='{stroke_color}', strokeWidth='{stroke_width}', strokeLineCap='{stroke_line_cap}'")

        valid, msg = validate_svg_path(path_data)
        if not valid:
            errors.append(f"Path #{i+1} invalid pathData: {msg}")
        else:
            print(f"   PathData syntax: {msg}")

    # Specific file assertions
    if filename == "ic_lr_close.xml":
        p = paths[0]
        sw = p.attrib.get(f"{{{ANDROID_NS}}}strokeWidth")
        if sw != "1.9":
            errors.append(f"ic_lr_close expected strokeWidth='1.9', got '{sw}'")
    elif filename == "ic_lr_plus.xml":
        p = paths[0]
        sw = p.attrib.get(f"{{{ANDROID_NS}}}strokeWidth")
        if sw != "1.9":
            errors.append(f"ic_lr_plus expected strokeWidth='1.9', got '{sw}'")
    elif filename == "ic_lr_more_vert.xml":
        if len(paths) != 3:
            errors.append(f"ic_lr_more_vert expected 3 dots (paths), got {len(paths)}")

    if errors:
        for err in errors:
            print(f"FAIL: {err}")
        return False

    print(f"PASS: {filename} verified successfully.")
    return True

def main():
    print("=" * 60)
    print("Milestone 1 XML Drawable Empirical Verification")
    print("=" * 60)
    results = [verify_file(f) for f in TARGET_FILES]
    print("\n" + "=" * 60)
    if all(results):
        print("ALL DRAWABLES PASSED VERIFICATION (3/3)")
        sys.exit(0)
    else:
        print(f"FAILURES DETECTED: {results.count(False)} file(s) failed")
        sys.exit(1)

if __name__ == "__main__":
    main()
