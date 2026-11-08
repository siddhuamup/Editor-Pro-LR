#!/usr/bin/env python3
"""
Independent Deep Adversarial Stress Harness.
Author: challenger_m1_1_gen2 (Teamwork Challenger / Empirical Adversarial Verifier)

Probes all 1,267 mined SVGs across 12 independent adversarial stress dimensions:
1. File Existence, Size, and UTF-8 Encoding
2. Non-ASCII and Unprintable Control Characters
3. XML Well-Formedness and DOM Structure
4. Android Token & Namespace Leakage
5. Re-testing of the 62 Iteration-1 Defective Files
6. Exhaustive Paint & Color Syntax Conformance (W3C standard)
7. Opacity and Numeric Bounds
8. Path Geometry and Command Syntax
9. ViewBox, Dimensions, and Coordinate Systems
10. Group Transformations and Parentheses Balance
11. ClipPath and Local URL Reference Resolution
12. Raw String APK Inventory Reconciliation against Lightroom.apk
"""

import os
import sys
import re
import struct
import zipfile
import subprocess
from pathlib import Path
from collections import defaultdict, Counter
import xml.etree.ElementTree as ET

ROOT_DIR = Path(__file__).resolve().parent.parent
SVG_DIR = ROOT_DIR / "mined Svg"
APK_PATH = Path(r"C:\Users\Siddhu\Downloads\Lightroom.apk")

# Regex definitions
RE_ANDROID_TOKEN = re.compile(
    r'(@0x[0-9a-fA-F]+|\?0x[0-9a-fA-F]+|\?attr\/|@android:|@color\/|@drawable\/|@string\/|@dimen\/|@null|res\/(?:color\/)?[a-zA-Z0-9_\-]+\.xml)',
    re.IGNORECASE
)
RE_ANDROID_NS = re.compile(r'\b(android:[a-zA-Z0-9_]+|tools:[a-zA-Z0-9_]+|app:[a-zA-Z0-9_]+)\b')
RE_NAN_INF = re.compile(r'\b(NaN|nan|Infinity|inf|-Infinity|-inf)\b')
RE_W3C_PAINT = re.compile(
    r'^(#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|none|currentColor|url\(#[a-zA-Z0-9_\-]+\)|white|black|transparent)$',
    re.IGNORECASE
)
RE_VALID_PATH_CHARS = re.compile(r'^[MmLlHhVvCcSsQqTtAaZz0-9\s\.,\-\+]+$')
RE_VALID_TRANSFORM = re.compile(r'^(?:(?:translate|scale|rotate|skewX|skewY|matrix)\s*\([^)]*\)\s*)+$')

VALID_CAPS = {'butt', 'round', 'square', 'inherit'}
VALID_JOINS = {'miter', 'round', 'bevel', 'inherit'}
VALID_FILL_RULES = {'nonzero', 'evenodd', 'inherit'}

def get_iteration_1_defective_files():
    """
    Retrieves the 62 files that had invalid paints in HEAD commit.
    Uses git show to inspect HEAD commit for each modified file.
    """
    cmd = ["git", "diff", "--name-only", "HEAD~1..HEAD"]
    # Alternatively find files in HEAD with raw integer or .xml in fill/stroke
    cmd_files = ["git", "ls-tree", "-r", "--name-only", "HEAD", "mined Svg/"]
    try:
        proc = subprocess.run(cmd_files, capture_output=True, text=True, cwd=str(ROOT_DIR), check=True)
        all_head_files = proc.stdout.strip().splitlines()
    except Exception as e:
        print(f"Warning: git ls-tree failed: {e}")
        all_head_files = []

    bad_in_head = []
    # Test each file in HEAD
    for fpath in all_head_files:
        try:
            show_proc = subprocess.run(["git", "show", f"HEAD:{fpath}"], capture_output=True, text=True, cwd=str(ROOT_DIR), check=True)
            content = show_proc.stdout
            # Check for bad paint
            root = ET.fromstring(content)
            has_bad = False
            for el in root.iter():
                for a in ("fill", "stroke"):
                    if a in el.attrib:
                        val = el.attrib[a].strip()
                        if not RE_W3C_PAINT.match(val):
                            has_bad = True
                            break
                if has_bad:
                    break
            if has_bad:
                bad_in_head.append(Path(fpath).name)
        except Exception:
            pass

    return sorted(bad_in_head)


def run_adversarial_suite():
    print("=" * 80)
    print("INDEPENDENT DEEP ADVERSARIAL STRESS HARNESS")
    print(f"Target Directory: {SVG_DIR}")
    print(f"Lightroom APK:   {APK_PATH}")
    print("=" * 80)

    findings = defaultdict(list)
    stats = Counter()

    # Step 1: Discover files
    svg_files = sorted(list(SVG_DIR.glob("*.svg")))
    stats['total_svgs'] = len(svg_files)
    print(f"\n[1] Discovered {len(svg_files)} .svg files in {SVG_DIR}")
    if len(svg_files) != 1267:
        findings['count_mismatch'].append(f"Expected 1,267 files, found {len(svg_files)}")

    # Step 2: Ingest APK and reconcile
    print("\n[2] Reconciling against Lightroom.apk binary XMLs...")
    apk_vector_count = 0
    if not APK_PATH.exists():
        findings['apk_missing'].append(str(APK_PATH))
    else:
        with zipfile.ZipFile(APK_PATH, 'r') as z:
            for name in z.namelist():
                if name.endswith('.xml'):
                    try:
                        raw = z.read(name)
                        if len(raw) >= 8 and struct.unpack('<H', raw[:2])[0] == 0x0003 and b'vector' in raw:
                            # Verify root element is vector
                            # String pool
                            scount = struct.unpack('<I', raw[12:16])[0]
                            flags = struct.unpack('<I', raw[20:24])[0]
                            is_utf8 = bool(flags & (1 << 8))
                            s_start = struct.unpack('<I', raw[24:28])[0]
                            sp_csize = struct.unpack('<I', raw[16:20])[0]
                            offsets = struct.unpack(f'<{scount}I', raw[36:36 + scount * 4])
                            pool_data = raw[8 + s_start:8 + sp_csize]
                            strings = []
                            for off in offsets:
                                try:
                                    if is_utf8:
                                        p = off
                                        cl = pool_data[p]; p += 1
                                        if cl & 0x80: cl = ((cl & 0x7f) << 8) | pool_data[p]; p += 1
                                        bl = pool_data[p]; p += 1
                                        if bl & 0x80: bl = ((bl & 0x7f) << 8) | pool_data[p]; p += 1
                                        s = pool_data[p:p+bl].decode('utf-8', errors='replace')
                                    else:
                                        p = off
                                        cl = struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                                        if cl & 0x8000: cl = ((cl & 0x7fff) << 16) | struct.unpack('<H', pool_data[p:p+2])[0]; p += 2
                                        s = pool_data[p:p+cl*2].decode('utf-16le', errors='replace')
                                    strings.append(s)
                                except Exception:
                                    strings.append('')
                            if 'vector' in strings:
                                pos = 8 + sp_csize
                                if pos < len(raw):
                                    rmt, _, rmc = struct.unpack('<HHI', raw[pos:pos+8])
                                    if rmt == 0x0180: pos += rmc
                                while pos < len(raw):
                                    nt, _, ns = struct.unpack('<HHI', raw[pos:pos+8])
                                    if nt == 0x0102: # start elem
                                        elem_idx = struct.unpack('<I', raw[pos+20:pos+24])[0]
                                        if elem_idx < len(strings) and strings[elem_idx] == 'vector':
                                            apk_vector_count += 1
                                        break
                                    pos += ns
                    except Exception:
                        pass
        stats['apk_vectors'] = apk_vector_count
        print(f"    Discovered {apk_vector_count} vector drawables in APK.")
        if apk_vector_count != 1267:
            findings['apk_vector_discrepancy'].append(f"APK vectors: {apk_vector_count}, expected 1267")

    # Step 3: Identify the 62 files from Iteration 1
    print("\n[3] Identifying Iteration 1 defective files...")
    iter1_defective = get_iteration_1_defective_files()
    print(f"    Identified {len(iter1_defective)} defective files in HEAD commit history.")

    # Step 4: Full adversarial probe across ALL files
    print("\n[4] Scanning all 1,267 SVGs across adversarial dimensions...")
    total_elements_scanned = 0
    total_paths_scanned = 0
    total_paints_scanned = 0

    for svg_file in svg_files:
        fname = svg_file.name

        # A. File Existence & Size
        try:
            size = svg_file.stat().st_size
            if size == 0:
                findings['empty_file'].append(fname)
                continue
        except Exception as e:
            findings['io_error'].append((fname, str(e)))
            continue

        # B. Raw Content & Encoding Checks
        try:
            raw_bytes = svg_file.read_bytes()
            content = raw_bytes.decode('utf-8')
        except UnicodeDecodeError as e:
            findings['non_utf8'].append((fname, str(e)))
            continue

        # Check for unprintable control characters
        for idx_b, b in enumerate(raw_bytes):
            if b < 0x09 or (0x0A < b < 0x0D) or (0x0D < b < 0x20) or b == 0x7F:
                findings['unprintable_ctrl_chars'].append((fname, f"byte 0x{b:02x} at offset {idx_b}"))
                break

        # Check for Android Token Leakage
        tokens = RE_ANDROID_TOKEN.findall(content)
        if tokens:
            findings['android_token_leak'].append((fname, tokens))

        # Check for Android/Tools/App Namespace attributes
        ns_attrs = RE_ANDROID_NS.findall(content)
        if ns_attrs:
            findings['android_ns_leak'].append((fname, ns_attrs))

        # Check for NaN / Inf
        nan_matches = RE_NAN_INF.findall(content)
        if nan_matches:
            findings['nan_inf'].append((fname, nan_matches))

        # Check for unescaped raw '<' in attributes or text
        # (ET will catch syntax errors, but let's also check)
        
        # C. XML DOM Parsing
        try:
            root = ET.fromstring(content)
        except ET.ParseError as e:
            findings['xml_malformed'].append((fname, str(e)))
            continue

        # D. Root element inspection
        root_tag = root.tag.split("}")[-1]
        if root_tag != "svg":
            findings['invalid_root_tag'].append((fname, root.tag))

        if 'xmlns="http://www.w3.org/2000/svg"' not in content:
            findings['missing_xmlns'].append(fname)

        # ViewBox checks
        vb = root.attrib.get('viewBox', '')
        if not vb:
            findings['missing_viewbox'].append(fname)
        else:
            parts = vb.strip().split()
            if len(parts) != 4:
                findings['malformed_viewbox'].append((fname, vb))
            else:
                try:
                    vx, vy, vw, vh = [float(x) for x in parts]
                    if vw <= 0 or vh <= 0:
                        findings['invalid_viewbox_dims'].append((fname, f"w={vw}, h={vh}"))
                except ValueError:
                    findings['malformed_viewbox'].append((fname, vb))

        # Width & Height checks
        w = root.attrib.get('width', '')
        h = root.attrib.get('height', '')
        if not w or not h:
            findings['missing_dimensions'].append(fname)
        else:
            try:
                wf, hf = float(w), float(h)
                if wf <= 0 or hf <= 0:
                    findings['invalid_dimensions'].append((fname, f"w={wf}, h={hf}"))
            except ValueError:
                findings['malformed_dimensions'].append((fname, f"w={w}, h={h}"))

        # Collect local IDs defined in SVG (<clipPath id="...">, <path id="...">, etc.)
        defined_ids = set()
        for el in root.iter():
            if 'id' in el.attrib:
                defined_ids.add(el.attrib['id'].strip())

        # E. Element Iteration
        for el in root.iter():
            total_elements_scanned += 1
            tag = el.tag.split("}")[-1]

            # 1. Path geometry checks
            if tag == 'path':
                total_paths_scanned += 1
                if 'd' not in el.attrib:
                    findings['path_missing_d'].append(fname)
                else:
                    d = el.attrib['d'].strip()
                    if not d:
                        findings['empty_path_d'].append(fname)
                    else:
                        # Path must start with move command
                        if not d[0] in ('M', 'm'):
                            findings['path_not_starting_with_move'].append((fname, d[:20]))
                        # Check valid path chars
                        if not RE_VALID_PATH_CHARS.match(d):
                            findings['invalid_path_chars'].append((fname, d[:40]))
                        # Check for NaN in d
                        if 'nan' in d.lower():
                            findings['nan_in_path_d'].append((fname, d[:40]))

            # 2. Paint attributes (fill, stroke, stop-color)
            for attr in ('fill', 'stroke', 'stop-color'):
                if attr in el.attrib:
                    total_paints_scanned += 1
                    val = el.attrib[attr].strip()
                    if not RE_W3C_PAINT.match(val):
                        findings['invalid_paint_syntax'].append((fname, tag, attr, val))
                    # If local url reference, check if ID exists
                    if val.startswith('url(#'):
                        ref_id = val[5:-1]
                        if ref_id not in defined_ids:
                            findings['unresolved_paint_url'].append((fname, val))

            # 3. ClipPath reference check
            if 'clip-path' in el.attrib:
                cp_val = el.attrib['clip-path'].strip()
                if cp_val.startswith('url(#'):
                    ref_id = cp_val[5:-1]
                    if ref_id not in defined_ids:
                        findings['unresolved_clippath_ref'].append((fname, cp_val))
                else:
                    findings['malformed_clippath_attr'].append((fname, cp_val))

            # 4. Opacity attributes
            for op_attr in ('opacity', 'fill-opacity', 'stroke-opacity'):
                if op_attr in el.attrib:
                    op_str = el.attrib[op_attr].strip()
                    try:
                        op_val = float(op_str)
                        if op_val < 0.0 or op_val > 1.0:
                            findings['out_of_range_opacity'].append((fname, op_attr, op_val))
                    except ValueError:
                        findings['malformed_opacity'].append((fname, op_attr, op_str))

            # 5. Stroke presentation attributes
            if 'stroke-width' in el.attrib:
                sw_str = el.attrib['stroke-width'].strip()
                try:
                    sw_val = float(sw_str)
                    if sw_val < 0.0:
                        findings['negative_stroke_width'].append((fname, sw_str))
                except ValueError:
                    findings['malformed_stroke_width'].append((fname, sw_str))

            if 'stroke-linecap' in el.attrib:
                sc = el.attrib['stroke-linecap'].strip()
                if sc not in VALID_CAPS:
                    findings['invalid_stroke_linecap'].append((fname, sc))

            if 'stroke-linejoin' in el.attrib:
                sj = el.attrib['stroke-linejoin'].strip()
                if sj not in VALID_JOINS:
                    findings['invalid_stroke_linejoin'].append((fname, sj))

            # 6. Fill rule
            if 'fill-rule' in el.attrib:
                fr = el.attrib['fill-rule'].strip()
                if fr not in VALID_FILL_RULES:
                    findings['invalid_fill_rule'].append((fname, fr))

            # 7. Transforms
            if 'transform' in el.attrib:
                tf = el.attrib['transform'].strip()
                # Check balanced parens
                if tf.count('(') != tf.count(')'):
                    findings['unbalanced_transform_parens'].append((fname, tf))
                if not RE_VALID_TRANSFORM.match(tf):
                    findings['invalid_transform_syntax'].append((fname, tf))

    # Step 5: Specifically re-test the 62 files from Iteration 1
    print("\n[5] Explicit verification of the 62 Iteration 1 defective files...")
    iter1_results = []
    for fname in iter1_defective:
        fpath = SVG_DIR / fname
        if not fpath.exists():
            iter1_results.append((fname, "FILE_MISSING", []))
            continue
        try:
            root = ET.parse(fpath).getroot()
            file_paints = []
            file_bad = []
            for el in root.iter():
                for a in ('fill', 'stroke', 'stop-color'):
                    if a in el.attrib:
                        v = el.attrib[a].strip()
                        file_paints.append((el.tag.split("}")[-1], a, v))
                        if not RE_W3C_PAINT.match(v) or RE_ANDROID_TOKEN.search(v):
                            file_bad.append((el.tag.split("}")[-1], a, v))
            status = "PASS" if len(file_bad) == 0 else "FAIL"
            iter1_results.append((fname, status, file_paints, file_bad))
        except Exception as e:
            iter1_results.append((fname, f"PARSE_ERROR: {e}", []))

    print(f"    Scanned all {len(iter1_results)} previously defective files.")
    iter1_fails = [r for r in iter1_results if r[1] != 'PASS']
    print(f"    Pass: {len(iter1_results) - len(iter1_fails)} / {len(iter1_results)}")
    print(f"    Fail: {len(iter1_fails)} / {len(iter1_results)}")

    # Print Summary Table
    print("\n" + "=" * 80)
    print("ADVERSARIAL HARNESS RESULTS BREAKDOWN")
    print("=" * 80)
    print(f"  Total SVG Files Analyzed       : {stats['total_svgs']:,}")
    print(f"  Total DOM Elements Examined    : {total_elements_scanned:,}")
    print(f"  Total Paths Examined           : {total_paths_scanned:,}")
    print(f"  Total Paint Attributes Checked : {total_paints_scanned:,}")
    print("-" * 80)

    categories = [
        ('count_mismatch', 'Asset Count Integrity (1267)'),
        ('apk_vector_discrepancy', 'APK Inventory Reconciliation'),
        ('empty_file', 'File Size > 0'),
        ('non_utf8', 'Valid UTF-8 Encoding'),
        ('unprintable_ctrl_chars', 'Unprintable Control Chars'),
        ('xml_malformed', 'XML Well-Formedness'),
        ('invalid_root_tag', 'Root <svg> Tag'),
        ('missing_xmlns', 'SVG XMLNS Declaration'),
        ('missing_viewbox', 'ViewBox Presence'),
        ('malformed_viewbox', 'ViewBox Numeric Format'),
        ('invalid_viewbox_dims', 'ViewBox Positive Dims'),
        ('missing_dimensions', 'Width/Height Attributes'),
        ('invalid_dimensions', 'Positive Dimensions'),
        ('malformed_dimensions', 'Dimensions Numeric Format'),
        ('android_token_leak', 'Android Token Leaks (@0x, ?0x, @color)'),
        ('android_ns_leak', 'Android Namespace Leaks (android:*)'),
        ('nan_inf', 'NaN / Inf Coordinates'),
        ('empty_path_d', 'Empty Path Geometry'),
        ('path_not_starting_with_move', 'Path Starting with Move Command'),
        ('invalid_path_chars', 'Valid Path Characters'),
        ('nan_in_path_d', 'NaN in Path Data'),
        ('invalid_paint_syntax', 'Valid W3C Paint Syntax (fill/stroke)'),
        ('unresolved_paint_url', 'Dangling Paint url(#id) Refs'),
        ('unresolved_clippath_ref', 'Dangling clip-path url(#id) Refs'),
        ('out_of_range_opacity', 'Opacity Range [0.0, 1.0]'),
        ('malformed_opacity', 'Opacity Numeric Format'),
        ('negative_stroke_width', 'Non-negative Stroke Width'),
        ('invalid_stroke_linecap', 'Valid Stroke Linecap'),
        ('invalid_stroke_linejoin', 'Valid Stroke Linejoin'),
        ('invalid_fill_rule', 'Valid Fill Rule'),
        ('unbalanced_transform_parens', 'Balanced Transform Parentheses'),
        ('invalid_transform_syntax', 'Valid Transform Syntax')
    ]

    total_issues = 0
    for cat_key, cat_label in categories:
        err_list = findings[cat_key]
        cnt = len(err_list)
        total_issues += cnt
        status = "[PASS]" if cnt == 0 else "[FAIL]"
        print(f"  {status:7} {cat_label:<45}: {cnt} issues")
        if cnt > 0:
            for item in err_list[:3]:
                print(f"           - {item}")
            if cnt > 3:
                print(f"           ... and {cnt - 3} more")

    print("-" * 80)
    print(f"TOTAL ADVERSARIAL ISSUES FOUND: {total_issues}")
    print(f"PREVIOUSLY DEFECTIVE 62 FILES RESIDUAL FLAWS: {len(iter1_fails)}")
    print("=" * 80)

    if total_issues == 0 and len(iter1_fails) == 0:
        print("\n>>> ALL ADVERSARIAL CHALLENGES PASSED! FINAL VERDICT: APPROVE <<<\n")
        return 0
    else:
        print("\n>>> ADVERSARIAL CHALLENGES FAILED! FINAL VERDICT: REQUEST_CHANGES <<<\n")
        return 1

if __name__ == "__main__":
    sys.exit(run_adversarial_suite())
