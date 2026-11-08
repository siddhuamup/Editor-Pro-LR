#!/usr/bin/env python3
"""
Adversarial Verification & Empirical Stress-Testing Suite for Mined SVGs.
Author: challenger_m1_2 (teamwork_challenger)

Evaluates:
1. Multi-parser conformance (xml.etree.ElementTree, xml.dom.minidom, xml.sax)
2. Visual integrity & solid black rectangle / zero-alpha defect detection
3. Complex drawable geometry (valid transforms on <g>, valid <clipPath> definitions & references)
4. Full pipeline reproducibility & SHA-256 hash idempotence
"""

import os
import sys
import tempfile
import hashlib
import re
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import xml.dom.minidom as minidom
import xml.sax

WORKSPACE_DIR = Path(r"c:\Users\Siddhu\Desktop\apps testing")
MINED_DIR = WORKSPACE_DIR / "mined Svg"
APK_PATH = Path(r"C:\Users\Siddhu\Downloads\Lightroom.apk")

TRANSFORM_PATTERN = re.compile(
    r'^(?:(?:translate|rotate|scale|matrix|skewX|skewY)\s*\(\s*[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?(?:\s*,\s*|\s+[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?)*\s*\)\s*)+$'
)

class AdversarialStressTester:
    def __init__(self, target_dir: Path):
        self.target_dir = target_dir
        self.files = sorted(list(target_dir.glob("*.svg")))
        self.results = {}

    def test_multi_parser(self):
        """Stress-test parsing across 3 distinct XML parsers: ElementTree, minidom, SAX."""
        et_errors = []
        dom_errors = []
        sax_errors = []

        class SimpleContentHandler(xml.sax.ContentHandler):
            pass

        for f in self.files:
            # 1. ElementTree
            try:
                tree = ET.parse(f)
                root = tree.getroot()
                if root.tag.split("}")[-1] != "svg":
                    et_errors.append((f.name, f"Root tag is not svg: {root.tag}"))
            except Exception as e:
                et_errors.append((f.name, str(e)))

            # 2. minidom
            try:
                dom = minidom.parse(str(f))
                if dom.documentElement.tagName.split(":")[-1] != "svg":
                    dom_errors.append((f.name, f"Root tag is not svg: {dom.documentElement.tagName}"))
            except Exception as e:
                dom_errors.append((f.name, str(e)))

            # 3. SAX
            try:
                parser = xml.sax.make_parser()
                parser.setContentHandler(SimpleContentHandler())
                parser.parse(str(f))
            except Exception as e:
                sax_errors.append((f.name, str(e)))

        return {
            "total": len(self.files),
            "et_errors": et_errors,
            "dom_errors": dom_errors,
            "sax_errors": sax_errors,
        }

    def test_visual_integrity_and_alpha(self):
        """
        Detect solid black box conversion or missing fill fallbacks.
        Specifically tests:
        - Rendered visual paths (outside <clipPath>) missing both fill and stroke (defaults to black in SVG spec)
        - Viewport-spanning bounding boxes filled with solid opaque black
        - Transparent color (#00000000 / zero-alpha) improperly emitted as opaque black
        - Opacity values out of bounds [0.0, 1.0] or NaN
        - Malformed color strings
        """
        missing_fill_stroke_defects = []
        full_black_rectangles = []
        opacity_anomalies = []
        malformed_colors = []

        for f in self.files:
            try:
                tree = ET.parse(f)
                root = tree.getroot()
                parent_map = {c: p for p in tree.iter() for c in p}
                
                # Extract viewport / viewBox
                vb = root.attrib.get("viewBox", "")
                vb_w, vb_h = None, None
                if vb:
                    parts = [float(p) for p in re.findall(r'[-+]?(?:\d*\.\d+|\d+)', vb)]
                    if len(parts) == 4:
                        vb_w, vb_h = parts[2], parts[3]

                for elem in root.iter():
                    tag = elem.tag.split("}")[-1]
                    parent = parent_map.get(elem)
                    parent_tag = parent.tag.split("}")[-1] if parent is not None else ""

                    if tag == "path":
                        fill = elem.attrib.get("fill")
                        stroke = elem.attrib.get("stroke")
                        fill_opacity = elem.attrib.get("fill-opacity")
                        stroke_opacity = elem.attrib.get("stroke-opacity")

                        # Only visual paths outside <clipPath> require explicit fill/stroke
                        if parent_tag != "clipPath":
                            if fill is None and stroke is None:
                                missing_fill_stroke_defects.append((f.name, "Rendered <path> missing both fill and stroke"))

                        # Opacity bounds check
                        for op_name, op_val in [("fill-opacity", fill_opacity), ("stroke-opacity", stroke_opacity)]:
                            if op_val is not None:
                                try:
                                    v = float(op_val)
                                    if v < 0.0 or v > 1.0 or math.isnan(v):
                                        opacity_anomalies.append((f.name, f"{op_name} out of bounds [0, 1]: {op_val}"))
                                except ValueError:
                                    opacity_anomalies.append((f.name, f"Invalid float for {op_name}: {op_val}"))

                        # Hex color format check
                        for col_name, col_val in [("fill", fill), ("stroke", stroke)]:
                            if col_val and col_val != "none":
                                if col_val.startswith("#"):
                                    if not re.match(r"^#[0-9a-fA-F]{6}$", col_val):
                                        malformed_colors.append((f.name, f"Malformed hex color in {col_name}: {col_val}"))

                    elif tag == "rect":
                        rw = float(elem.attrib.get("width", 0))
                        rh = float(elem.attrib.get("height", 0))
                        r_fill = elem.attrib.get("fill", "")
                        r_op = elem.attrib.get("fill-opacity", "1")
                        if vb_w and vb_h and rw >= vb_w and rh >= vb_h and r_fill in ("#000000", "black") and float(r_op) >= 0.99:
                            full_black_rectangles.append(f.name)

            except Exception as e:
                missing_fill_stroke_defects.append((f.name, f"Inspection error: {e}"))

        return {
            "missing_fill_stroke": missing_fill_stroke_defects,
            "full_black_rectangles": full_black_rectangles,
            "opacity_anomalies": opacity_anomalies,
            "malformed_colors": malformed_colors
        }

    def test_complex_drawables(self):
        """
        Stress-tests complex drawables:
        - <g> elements with 'transform' attribute: syntactic validity, number of groups
        - <clipPath> definitions and 'clip-path' attribute references: no dangling references
        """
        group_count = 0
        transformed_group_count = 0
        transform_syntax_errors = []

        clip_def_count = 0
        clip_ref_count = 0
        dangling_clip_refs = []
        empty_clip_defs = []

        for f in self.files:
            try:
                tree = ET.parse(f)
                root = tree.getroot()

                # Collect clipPath IDs defined
                defined_clip_ids = set()
                defs_elems = [el for el in root.iter() if el.tag.split("}")[-1] == "defs"]
                for d in defs_elems:
                    for c in d.iter():
                        if c.tag.split("}")[-1] == "clipPath":
                            clip_def_count += 1
                            cid = c.attrib.get("id")
                            if cid:
                                defined_clip_ids.add(cid)
                            # Check if clipPath has child path
                            paths = [p for p in c.iter() if p.tag.split("}")[-1] == "path"]
                            if not paths or not any(p.attrib.get("d") for p in paths):
                                empty_clip_defs.append((f.name, cid))

                # Check all elements in the tree
                for elem in root.iter():
                    tag = elem.tag.split("}")[-1]
                    if tag == "g":
                        group_count += 1
                        trans = elem.attrib.get("transform")
                        if trans:
                            transformed_group_count += 1
                            # Validate transform syntax
                            if not TRANSFORM_PATTERN.match(trans.strip()):
                                transform_syntax_errors.append((f.name, trans))

                    # Check clip-path reference
                    clip_ref = elem.attrib.get("clip-path")
                    if clip_ref:
                        clip_ref_count += 1
                        # Format is url(#id)
                        m = re.match(r'^url\(#([^)]+)\)$', clip_ref.strip())
                        if not m:
                            dangling_clip_refs.append((f.name, f"Malformed clip-path reference: {clip_ref}"))
                        else:
                            target_id = m.group(1)
                            if target_id not in defined_clip_ids:
                                dangling_clip_refs.append((f.name, f"Dangling clip-path reference: {target_id} not in {defined_clip_ids}"))

            except Exception as e:
                transform_syntax_errors.append((f.name, f"Tree inspection error: {e}"))

        return {
            "group_count": group_count,
            "transformed_group_count": transformed_group_count,
            "transform_syntax_errors": transform_syntax_errors,
            "clip_def_count": clip_def_count,
            "clip_ref_count": clip_ref_count,
            "dangling_clip_refs": dangling_clip_refs,
            "empty_clip_defs": empty_clip_defs,
        }

    def test_reproducibility(self):
        """
        Run the extraction pipeline into a clean temporary directory and verify 100% hash identity with mined Svg/.
        """
        import importlib.util
        mine_script_path = WORKSPACE_DIR / "mine_apk_svgs.py"
        spec = importlib.util.spec_from_file_location("mine_apk_svgs", mine_script_path)
        mine_mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mine_mod)

        with tempfile.TemporaryDirectory() as temp_dir_str:
            temp_dir = Path(temp_dir_str)
            
            # Temporarily redirect OUTPUT_DIR
            original_output = mine_mod.OUTPUT_DIR
            mine_mod.OUTPUT_DIR = temp_dir
            try:
                mine_mod.mine_all_svgs()
            finally:
                mine_mod.OUTPUT_DIR = original_output

            # Compare outputs
            temp_files = sorted(list(temp_dir.glob("*.svg")))
            orig_files = sorted(list(self.target_dir.glob("*.svg")))

            count_match = len(temp_files) == len(orig_files)
            
            temp_hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in temp_files}
            orig_hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in orig_files}

            mismatched_hashes = []
            missing_in_temp = []
            extra_in_temp = []

            for name, h in orig_hashes.items():
                if name not in temp_hashes:
                    missing_in_temp.append(name)
                elif temp_hashes[name] != h:
                    mismatched_hashes.append(name)

            for name in temp_hashes:
                if name not in orig_hashes:
                    extra_in_temp.append(name)

            return {
                "orig_count": len(orig_files),
                "temp_count": len(temp_files),
                "count_match": count_match,
                "missing": missing_in_temp,
                "extra": extra_in_temp,
                "mismatched_hashes": mismatched_hashes,
                "all_hashes_identical": (len(missing_in_temp) == 0 and len(extra_in_temp) == 0 and len(mismatched_hashes) == 0)
            }

def run_all_challenges():
    print("=" * 80)
    print("EMPIRICAL ADVERSARIAL STRESS TEST SUITE FOR MINED SVGS")
    print(f"Target Directory: {MINED_DIR}")
    print("=" * 80)

    tester = AdversarialStressTester(MINED_DIR)

    # 1. Multi-parser stress test
    print("\n[Task 1] Multi-Parser Conformance Stress Testing...")
    res_parser = tester.test_multi_parser()
    print(f"  Total SVG Files Analyzed: {res_parser['total']}")
    print(f"  xml.etree.ElementTree Errors: {len(res_parser['et_errors'])}")
    print(f"  xml.dom.minidom Errors      : {len(res_parser['dom_errors'])}")
    print(f"  xml.sax Errors              : {len(res_parser['sax_errors'])}")

    # 2. Visual Integrity & Transparency / Alpha Testing
    print("\n[Task 2] Visual Integrity & Alpha Stress Testing...")
    res_visual = tester.test_visual_integrity_and_alpha()
    print(f"  Rendered Missing Fill/Stroke: {len(res_visual['missing_fill_stroke'])}")
    print(f"  Solid Black Rectangles      : {len(res_visual['full_black_rectangles'])}")
    print(f"  Opacity Anomalies           : {len(res_visual['opacity_anomalies'])}")
    print(f"  Malformed Hex Colors        : {len(res_visual['malformed_colors'])}")

    # 3. Complex Drawables (Groups, Transforms, ClipPaths)
    print("\n[Task 3] Complex Drawables (Groups, Transforms, ClipPaths)...")
    res_complex = tester.test_complex_drawables()
    print(f"  Total <g> Elements Found    : {res_complex['group_count']}")
    print(f"  Transformed <g> Elements    : {res_complex['transformed_group_count']}")
    print(f"  Transform Syntax Errors     : {len(res_complex['transform_syntax_errors'])}")
    print(f"  ClipPath Definitions        : {res_complex['clip_def_count']}")
    print(f"  ClipPath References         : {res_complex['clip_ref_count']}")
    print(f"  Dangling Clip References    : {len(res_complex['dangling_clip_refs'])}")
    print(f"  Empty ClipPath Defs         : {len(res_complex['empty_clip_defs'])}")

    # 4. Pipeline Reproducibility & SHA-256 Idempotence
    print("\n[Task 4] Full Pipeline Reproducibility & Idempotence Testing...")
    res_repro = tester.test_reproducibility()
    print(f"  Original SVG Count          : {res_repro['orig_count']}")
    print(f"  Re-extracted SVG Count      : {res_repro['temp_count']}")
    print(f"  Count Match                 : {res_repro['count_match']}")
    print(f"  Hash Mismatches             : {len(res_repro['mismatched_hashes'])}")
    print(f"  Missing Files in Re-run     : {len(res_repro['missing'])}")
    print(f"  Extra Files in Re-run       : {len(res_repro['extra'])}")
    print(f"  100% SHA-256 Identical      : {res_repro['all_hashes_identical']}")

    # Overall Verdict Calculation
    total_failures = (
        len(res_parser['et_errors']) +
        len(res_parser['dom_errors']) +
        len(res_parser['sax_errors']) +
        len(res_visual['missing_fill_stroke']) +
        len(res_visual['full_black_rectangles']) +
        len(res_visual['opacity_anomalies']) +
        len(res_visual['malformed_colors']) +
        len(res_complex['transform_syntax_errors']) +
        len(res_complex['dangling_clip_refs']) +
        len(res_complex['empty_clip_defs']) +
        (0 if res_repro['all_hashes_identical'] else 1)
    )

    print("\n" + "=" * 80)
    print("STRESS TEST SUMMARY & VERDICT")
    print(f"  Total Empirical Defects Discovered: {total_failures}")
    if total_failures == 0 and res_parser['total'] >= 1000:
        print("  VERDICT: APPROVE")
        sys.exit(0)
    else:
        print("  VERDICT: REQUEST_CHANGES")
        sys.exit(1)

if __name__ == "__main__":
    run_all_challenges()
