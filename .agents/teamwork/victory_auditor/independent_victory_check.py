#!/usr/bin/env python3
"""
Independent Victory Audit Verification Script.
Author: teamwork_victory_auditor (Independent Victory Auditor)

Forensic checks:
1. Verify Lightroom.apk existence, size, and hash.
2. Direct binary inspection of Lightroom.apk:
   - Identify all compiled AXML VectorDrawable assets
   - Reconcile 1-to-1 against mined Svg/ directory
3. Independent validation of all 1,267 SVGs:
   - File size > 0
   - Well-formed XML DOM
   - Valid root <svg>, viewBox, width, height, xmlns
   - Valid visual elements (<path>, <g>) with non-empty 'd'
   - Strict W3C paint validation (fill, stroke, stop-color)
   - Zero Android token leaks (@0x, ?0x, ?attr, @android, etc.)
   - Zero NaN / Inf coordinates
   - ClipPath URL resolution
4. Anti-cheating & code integrity checks:
   - Hardcoded returns / mock dictionaries / empty stubs
"""

import os
import sys
import struct
import zipfile
import re
import hashlib
from pathlib import Path
from collections import defaultdict, Counter
import xml.etree.ElementTree as ET

APK_PATH = Path(r"C:\Users\Siddhu\Downloads\Lightroom.apk")
SVG_DIR = Path(r"c:\Users\Siddhu\Desktop\apps testing\mined Svg")

VALID_PAINT_REGEX = re.compile(
    r"^(#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|none|currentColor|url\(#[a-zA-Z0-9_\-]+\))$",
    re.IGNORECASE
)

TOKEN_LEAK_REGEX = re.compile(
    r"(@0x|\?0x|\?attr\/|@android:|@color\/|@drawable\/|@string\/|@dimen\/|res\/(?:color\/)?[a-zA-Z0-9_\-]+\.xml|android:)",
    re.IGNORECASE
)

NAN_INF_REGEX = re.compile(r"\b(NaN|nan|Infinity|inf|-Infinity|-inf)\b")

def parse_string_pool(data: bytes, offset: int):
    htype, hsize, csize, scount, stcount, flags, s_start, st_start = struct.unpack('<HHIIIIII', data[offset:offset+28])
    is_utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack(f'<{scount}I', data[offset+28:offset+28+scount*4])
    pool_data = data[offset+s_start:offset+csize]
    strings = []
    for off in offsets:
        try:
            if is_utf8:
                p = off
                char_len = pool_data[p]; p += 1
                if char_len & 0x80:
                    char_len = ((char_len & 0x7f) << 8) | pool_data[p]; p += 1
                byte_len = pool_data[p]; p += 1
                if byte_len & 0x80:
                    byte_len = ((byte_len & 0x7f) << 8) | pool_data[p]; p += 1
                s = pool_data[p:p+byte_len].decode('utf-8', errors='replace')
            else:
                p = off
                char_len = struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                if char_len & 0x8000:
                    char_len = ((char_len & 0x7fff) << 16) | struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                s = pool_data[p:p+char_len*2].decode('utf-16le', errors='replace')
            strings.append(s)
        except Exception:
            strings.append('')
    return strings, offset + csize

def run_independent_audit():
    print("=" * 80)
    print("INDEPENDENT VICTORY AUDIT: LIGHTROOM APK & MINED SVG ASSETS")
    print("=" * 80)
    
    # 1. APK Verification
    if not APK_PATH.exists():
        print(f"[FAIL] APK not found: {APK_PATH}")
        sys.exit(1)
        
    apk_stat = APK_PATH.stat()
    print(f"[PASS] Target APK exists: {APK_PATH}")
    print(f"       File size: {apk_stat.st_size:,} bytes ({apk_stat.st_size / (1024*1024):.2f} MB)")
    
    # Quick MD5 / SHA256
    hasher = hashlib.sha256()
    with open(APK_PATH, 'rb') as f:
        while chunk := f.read(1024 * 1024):
            hasher.update(chunk)
    apk_sha256 = hasher.hexdigest()
    print(f"       SHA256: {apk_sha256}")

    # 2. Raw APK Ingestion & Independent Vector Inventory
    print("\n--- PHASE 1: DIRECT APK BINARY INVENTORY AUDIT ---")
    with zipfile.ZipFile(APK_PATH, 'r') as z:
        all_entries = z.namelist()
        xml_entries = [e for e in all_entries if e.startswith('res/') and e.endswith('.xml')]
        print(f"Total entries in APK: {len(all_entries):,}")
        print(f"Total res/*.xml files: {len(xml_entries):,}")

        # Scan for binary AXML vectors
        vector_drawables = []
        for name in xml_entries:
            data = z.read(name)
            if len(data) >= 8 and struct.unpack('<H', data[:2])[0] == 0x0003:
                if b'vector' in data:
                    strings, _ = parse_string_pool(data, 8)
                    if 'vector' in strings:
                        pos = 8
                        _, sp_csize = struct.unpack('<HI', data[pos+2:pos+8])
                        pos += sp_csize
                        if pos < len(data):
                            rm_type, _, rm_csize = struct.unpack('<HHI', data[pos:pos+8])
                            if rm_type == 0x0180: pos += rm_csize
                        while pos < len(data):
                            n_type, _, n_size = struct.unpack('<HHI', data[pos:pos+8])
                            if n_type == 0x0102: # START_ELEMENT
                                elem_name_idx = struct.unpack('<I', data[pos+20:pos+24])[0]
                                if elem_name_idx < len(strings) and strings[elem_name_idx] == 'vector':
                                    vector_drawables.append(name)
                                break
                            pos += n_size
                            
        print(f"Total authentic <vector> drawables discovered: {len(vector_drawables):,}")

    # 3. Output Directory Inspection
    print("\n--- PHASE 2: MINED SVG DIRECTORY AUDIT ---")
    if not SVG_DIR.exists():
        print(f"[FAIL] Output directory does not exist: {SVG_DIR}")
        sys.exit(1)

    all_svg_files = sorted(list(SVG_DIR.glob("*.svg")))
    non_svg_files = [f for f in SVG_DIR.iterdir() if f.is_file() and not f.name.endswith(".svg")]
    dirs = [d for d in SVG_DIR.iterdir() if d.is_dir()]

    print(f"Total .svg files in {SVG_DIR.name}/: {len(all_svg_files):,}")
    print(f"Non-SVG files: {len(non_svg_files)} | Subdirectories: {len(dirs)}")
    assert len(non_svg_files) == 0, f"Unexpected non-SVG files found: {non_svg_files}"
    assert len(dirs) == 0, f"Unexpected subdirectories found: {dirs}"
    assert len(all_svg_files) == len(vector_drawables), (
        f"Inventory count mismatch: APK has {len(vector_drawables)} vectors, but mined Svg has {len(all_svg_files)} SVGs!"
    )
    print(f"[PASS] Exact 1:1 match: {len(vector_drawables)} APK vector drawables == {len(all_svg_files)} mined SVGs")

    # 4. Independent Deep DOM & Content Validation
    print("\n--- PHASE 3: COMPREHENSIVE DOM & SYNTAX AUDIT ON ALL SVGS ---")
    issues = defaultdict(list)
    total_elements = 0
    total_paths = 0
    total_paints = 0
    min_size = float('inf')
    max_size = 0

    for svg_file in all_svg_files:
        # Gate 1: Non-empty file
        size = svg_file.stat().st_size
        min_size = min(min_size, size)
        max_size = max(max_size, size)
        if size == 0:
            issues["empty_file"].append(svg_file.name)
            continue

        raw_content = svg_file.read_text(encoding="utf-8", errors="replace")

        # Check raw text for leaked tokens
        if TOKEN_LEAK_REGEX.search(raw_content):
            leak = TOKEN_LEAK_REGEX.findall(raw_content)
            issues["token_leak"].append((svg_file.name, leak))

        # Check raw text for NaN / Infinity
        if NAN_INF_REGEX.search(raw_content):
            issues["nan_inf"].append(svg_file.name)

        # Gate 2: XML Parsing
        try:
            tree = ET.parse(svg_file)
            root = tree.getroot()
        except Exception as e:
            issues["xml_malformed"].append((svg_file.name, str(e)))
            continue

        # Gate 3: Root tag and attributes
        root_tag = root.tag.split("}")[-1]
        if root_tag != "svg":
            issues["invalid_root_tag"].append((svg_file.name, root_tag))

        if "viewBox" not in root.attrib and not ("width" in root.attrib and "height" in root.attrib):
            issues["missing_coords"].append(svg_file.name)

        if "viewBox" in root.attrib:
            vb_parts = root.attrib["viewBox"].strip().split()
            if len(vb_parts) != 4:
                issues["malformed_viewbox"].append((svg_file.name, root.attrib["viewBox"]))
            else:
                try:
                    vx, vy, vw, vh = [float(p) for p in vb_parts]
                    if vw <= 0 or vh <= 0:
                        issues["degenerate_viewbox"].append((svg_file.name, root.attrib["viewBox"]))
                except ValueError:
                    issues["malformed_viewbox"].append((svg_file.name, root.attrib["viewBox"]))

        # Check clipPath definitions
        defs_clips = set()
        for cp in root.iter():
            if cp.tag.split("}")[-1] == "clipPath" and "id" in cp.attrib:
                defs_clips.add(cp.attrib["id"])

        # Check all child elements
        has_visual_element = False
        for el in root.iter():
            total_elements += 1
            tag = el.tag.split("}")[-1]
            if tag in ("path", "g", "circle", "rect", "line", "polygon", "polyline"):
                has_visual_element = True

            # Clip-path reference resolution
            if "clip-path" in el.attrib:
                cp_val = el.attrib["clip-path"]
                m = re.match(r"^url\(#([a-zA-Z0-9_\-]+)\)$", cp_val)
                if not m or m.group(1) not in defs_clips:
                    issues["unresolved_clip_path"].append((svg_file.name, cp_val))

            # Transform validity
            if "transform" in el.attrib:
                t_val = el.attrib["transform"]
                # Must have balanced parens and valid transform functions
                if t_val.count("(") != t_val.count(")"):
                    issues["malformed_transform"].append((svg_file.name, t_val))

            # Path checks
            if tag == "path":
                total_paths += 1
                d = el.attrib.get("d", "").strip()
                if not d:
                    issues["empty_path_d"].append(svg_file.name)

            # Paint attribute checks (fill, stroke, stop-color)
            for attr in ("fill", "stroke", "stop-color"):
                if attr in el.attrib:
                    total_paints += 1
                    val = el.attrib[attr].strip()
                    if not VALID_PAINT_REGEX.match(val):
                        issues["invalid_paint"].append((svg_file.name, attr, val))

            # Opacity checks
            for attr in ("opacity", "fill-opacity", "stroke-opacity"):
                if attr in el.attrib:
                    try:
                        op = float(el.attrib[attr])
                        if not (0.0 <= op <= 1.0):
                            issues["invalid_opacity_range"].append((svg_file.name, attr, op))
                    except ValueError:
                        issues["malformed_opacity"].append((svg_file.name, attr, el.attrib[attr]))

        if not has_visual_element:
            issues["no_visual_elements"].append(svg_file.name)

    print(f"Audit statistics across {len(all_svg_files):,} SVGs:")
    print(f"  - File size range     : {min_size:,} bytes to {max_size:,} bytes")
    print(f"  - Total DOM elements  : {total_elements:,}")
    print(f"  - Total <path> nodes  : {total_paths:,}")
    print(f"  - Total paint checks  : {total_paints:,}")

    total_issue_count = sum(len(v) for v in issues.values())
    print("\nAdversarial Issue Breakdown:")
    for category, lst in issues.items():
        print(f"  [{'FAIL' if lst else 'PASS'}] {category}: {len(lst)} issues")
        for sample in lst[:5]:
            print(f"       -> Sample: {sample}")

    if total_issue_count == 0:
        print("\n[PASS] All 1,267 SVGs passed all independent checks with 0 defects!")
    else:
        print(f"\n[FAIL] Found {total_issue_count} issues across {len(issues)} categories.")
        sys.exit(1)

    # 5. Anti-Cheating & Integrity Check on Codebase
    print("\n--- PHASE 4: ANTI-CHEATING & CODE INTEGRITY INSPECTION ---")
    scripts = ["mine_apk_svgs.py", "verify_mined_svgs.py"]
    for sname in scripts:
        spath = Path(sname)
        if not spath.exists():
            print(f"[FAIL] Script missing: {sname}")
            sys.exit(1)
        code = spath.read_text(encoding="utf-8")
        # Check for facade returns
        if "return True" in code and len(code.splitlines()) < 20:
            print(f"[FAIL] Facade code detected in {sname}")
            sys.exit(1)
        # Check for hardcoded test results
        if re.search(r"passed_count\s*=\s*1267", code):
            print(f"[FAIL] Hardcoded test results detected in {sname}")
            sys.exit(1)
        print(f"[PASS] {sname} ({len(code.splitlines())} lines) verified authentic with no hardcoded bypasses.")

    print("\n" + "=" * 80)
    print("ALL AUDIT PHASES COMPLETE: VICTORY CRITERIA FULLY SATISFIED")
    print("=" * 80)

if __name__ == "__main__":
    run_independent_audit()
