#!/usr/bin/env python3
"""
Independent Verification Oracle for Milestone 1
 LightRoom Mobile Design Reference Screenshot Sorting Audit

Author: challenger_m1_2 (teamwork_challenger)
Role: critic, specialist
Target: CleanLightroom/design-reference

Independent clean-room implementation - zero worker imports.
"""

import os
import sys
import json
import re
import struct
import zlib
import hashlib
from io import BytesIO
from PIL import Image

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DESIGN_REF_DIR = os.path.join(PROJECT_ROOT, "CleanLightroom", "design-reference")
MANIFEST_PATH = os.path.join(DESIGN_REF_DIR, "PANEL_MANIFEST.json")
EXPLORER_HANDOFF = os.path.join(PROJECT_ROOT, ".agents", "teamwork", "explorer_survey_1", "handoff.md")

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"

CANONICAL_PANELS = [
    "Actions", "Camera", "Color", "Color_Grading", "Color_Mix",
    "Community", "Crop", "Detail", "Editor_and_Export", "Effects",
    "Gallery", "Lens_Blur", "Light", "Masking", "Optics",
    "Presets", "Profiles", "Remove", "Settings_and_Account", "Tone_Curve"
]

def parse_explorer_baseline(handoff_path):
    """
    Parses explorer_survey_1/handoff.md markdown tables to extract baseline records.
    """
    if not os.path.isfile(handoff_path):
        raise FileNotFoundError(f"Explorer handoff not found at: {handoff_path}")
    
    with open(handoff_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Match rows like:
    # | 1 | `Screenshot_20261019-082951_Lightroom.png` | `...` | 3,177,183 | PNG | RGBA | 1220x2712 | `e58bfeb...` |
    # Regex pattern to match table rows
    pattern = re.compile(
        r"\|\s*\d+\s*\|\s*`([^`]+)`\s*\|\s*`([^`]+)`\s*\|\s*([0-9,]+)\s*\|\s*(\w+)\s*\|\s*(\w+)\s*\|\s*(\d+)x(\d+)\s*\|\s*`([a-f0-9]{64})`\s*\|",
        re.IGNORECASE
    )
    
    matches = pattern.findall(content)
    baseline_records = {}
    for filename, rel_path, size_str, fmt, mode, width_str, height_str, sha256 in matches:
        size = int(size_str.replace(",", ""))
        width = int(width_str)
        height = int(height_str)
        baseline_records[filename] = {
            "filename": filename,
            "rel_path": rel_path,
            "size": size,
            "format": fmt,
            "mode": mode,
            "width": width,
            "height": height,
            "sha256": sha256.lower(),
            "source_type": "horizontal" if "horizontal" in rel_path else "vertical"
        }
    return baseline_records

def parse_png_ihdr(data):
    """
    Low-level struct parsing of PNG header and IHDR chunk.
    Validates magic bytes, chunk length, type, dimensions, bit depth, color type, and CRC32.
    """
    if len(data) < 33:
        raise ValueError(f"Data too short for PNG: {len(data)} bytes")
    
    if data[:8] != PNG_MAGIC:
        raise ValueError("Invalid PNG magic header")
    
    length, chunk_type = struct.unpack(">I4s", data[8:16])
    if chunk_type != b"IHDR":
        raise ValueError(f"First chunk must be IHDR, got {chunk_type}")
    if length != 13:
        raise ValueError(f"IHDR length must be 13, got {length}")
    
    ihdr_data = data[16:29]
    crc_bytes = data[29:33]
    expected_crc = struct.unpack(">I", crc_bytes)[0]
    computed_crc = zlib.crc32(data[12:29]) & 0xFFFFFFFF
    
    if computed_crc != expected_crc:
        raise ValueError(f"IHDR CRC mismatch: computed {computed_crc:#x} != expected {expected_crc:#x}")
    
    width, height, bit_depth, color_type, comp_method, filter_method, interlace_method = struct.unpack(">IIBBBBB", ihdr_data)
    
    return {
        "width": width,
        "height": height,
        "bit_depth": bit_depth,
        "color_type": color_type,
        "compression_method": comp_method,
        "filter_method": filter_method,
        "interlace_method": interlace_method
    }

def run_empirical_oracle():
    results = {
        "passed": False,
        "checks": {},
        "errors": [],
        "warnings": [],
        "disk_inventory": {},
        "panel_distribution": {},
        "metrics": {}
    }

    print("=" * 80)
    print("CHALLENGER_M1_2: INDEPENDENT EMPIRICAL VERIFICATION ORACLE")
    print(f"Target Directory: {DESIGN_REF_DIR}")
    print("=" * 80)

    # -------------------------------------------------------------
    # Check 1: Root directory cleanliness & Legacy folder retirement
    # -------------------------------------------------------------
    print("\n[Check 1] Verifying Root Directory Cleanliness & Workspace Isolation...")
    root_entries = os.listdir(DESIGN_REF_DIR)
    stray_pngs = [e for e in root_entries if e.lower().endswith(".png")]
    legacy_folder = "horizontal screen UI(same as verticle)"
    legacy_exists = os.path.exists(os.path.join(DESIGN_REF_DIR, legacy_folder))

    if stray_pngs:
        results["errors"].append(f"Check 1 Failed: Found {len(stray_pngs)} stray PNGs in root: {stray_pngs}")
        results["checks"]["root_cleanliness"] = False
    else:
        results["checks"]["root_cleanliness"] = True
        print(f"  -> OK: 0 stray PNG files in root directory.")

    if legacy_exists:
        results["errors"].append(f"Check 1 Failed: Legacy folder '{legacy_folder}' still exists on disk.")
        results["checks"]["legacy_retirement"] = False
    else:
        results["checks"]["legacy_retirement"] = True
        print(f"  -> OK: Legacy directory '{legacy_folder}' safely retired.")

    # -------------------------------------------------------------
    # Check 2: Canonical Panel Folders Provisioning
    # -------------------------------------------------------------
    print("\n[Check 2] Verifying 20 Canonical Panel Directories...")
    disk_dirs = [d for d in root_entries if os.path.isdir(os.path.join(DESIGN_REF_DIR, d)) and d != "Svg Icons"]
    missing_panels = set(CANONICAL_PANELS) - set(disk_dirs)
    extra_dirs = set(disk_dirs) - set(CANONICAL_PANELS)

    if missing_panels:
        results["errors"].append(f"Check 2 Failed: Missing panel folders: {missing_panels}")
    if extra_dirs:
        results["errors"].append(f"Check 2 Failed: Unexpected directories found: {extra_dirs}")
    
    if not missing_panels and not extra_dirs and len(disk_dirs) == 20:
        results["checks"]["canonical_panels_structure"] = True
        print(f"  -> OK: Exactly 20 canonical panel directories exist on disk.")
    else:
        results["checks"]["canonical_panels_structure"] = False

    # -------------------------------------------------------------
    # Check 3: Raw Byte Scan, Hashing, Struct Unpacking, and PIL Verify
    # -------------------------------------------------------------
    print("\n[Check 3] Scanning disk bytes across 20 panels, computing SHA-256 & parsing IHDR...")
    disk_files = {}
    panel_counts = {}
    total_payload_bytes = 0
    unique_hashes = set()
    hash_collisions = []
    filename_collisions = []
    seen_filenames = set()

    for panel in CANONICAL_PANELS:
        panel_path = os.path.join(DESIGN_REF_DIR, panel)
        if not os.path.isdir(panel_path):
            continue
        entries = sorted(os.listdir(panel_path))
        png_files = [f for f in entries if f.lower().endswith(".png")]
        panel_counts[panel] = len(png_files)
        
        # Check for non-png or unexpected files inside panel folders
        non_png = [f for f in entries if not f.lower().endswith(".png")]
        if non_png:
            results["warnings"].append(f"Panel '{panel}' contains non-PNG files: {non_png}")

        for fname in png_files:
            file_path = os.path.join(panel_path, fname)
            rel_path = f"{panel}/{fname}"

            # Check duplicate filename across panels
            if fname in seen_filenames:
                filename_collisions.append((fname, rel_path))
            seen_filenames.add(fname)

            # Read raw bytes directly
            with open(file_path, "rb") as f:
                data = f.read()

            file_size = len(data)
            total_payload_bytes += file_size

            # Compute SHA-256
            sha256 = hashlib.sha256(data).hexdigest().lower()
            if sha256 in unique_hashes:
                hash_collisions.append((fname, sha256))
            unique_hashes.add(sha256)

            # Low-level struct parsing
            try:
                ihdr = parse_png_ihdr(data)
            except Exception as e:
                results["errors"].append(f"IHDR parse error for {rel_path}: {e}")
                continue

            # PIL Image Verification
            try:
                with Image.open(BytesIO(data)) as pil_img:
                    pil_img.verify()
                # Reopen to inspect properties
                with Image.open(BytesIO(data)) as pil_img:
                    pil_format = pil_img.format
                    pil_mode = pil_img.mode
                    pil_size = pil_img.size
            except Exception as e:
                results["errors"].append(f"PIL verification failed for {rel_path}: {e}")
                continue

            # Orientation check
            w, h = ihdr["width"], ihdr["height"]
            if (w, h) == (1220, 2712):
                orientation = "portrait"
            elif (w, h) == (2712, 1220):
                orientation = "landscape"
            else:
                orientation = "unknown"
                results["errors"].append(f"Unexpected dimensions {w}x{h} in {rel_path}")

            disk_files[fname] = {
                "filename": fname,
                "panel": panel,
                "rel_path": rel_path,
                "size": file_size,
                "sha256": sha256,
                "width": w,
                "height": h,
                "orientation": orientation,
                "bit_depth": ihdr["bit_depth"],
                "color_type": ihdr["color_type"],
                "pil_format": pil_format,
                "pil_mode": pil_mode,
                "pil_size": pil_size
            }

    results["disk_inventory"] = disk_files
    results["panel_distribution"] = panel_counts
    results["metrics"]["total_files_on_disk"] = len(disk_files)
    results["metrics"]["total_payload_bytes"] = total_payload_bytes
    results["metrics"]["unique_sha256_count"] = len(unique_hashes)

    print(f"  -> Discovered {len(disk_files)} screenshot files across 20 panels.")
    print(f"  -> Total payload: {total_payload_bytes:,} bytes.")
    print(f"  -> Unique SHA-256 digests: {len(unique_hashes)}.")

    if len(disk_files) == 102:
        results["checks"]["disk_count_102"] = True
    else:
        results["checks"]["disk_count_102"] = False
        results["errors"].append(f"Expected 102 files on disk, found {len(disk_files)}")

    if len(unique_hashes) == 102:
        results["checks"]["unique_hashes_102"] = True
    else:
        results["checks"]["unique_hashes_102"] = False
        results["errors"].append(f"Expected 102 unique hashes, found {len(unique_hashes)}. Collisions: {hash_collisions}")

    if not filename_collisions:
        results["checks"]["zero_filename_collisions"] = True
    else:
        results["checks"]["zero_filename_collisions"] = False
        results["errors"].append(f"Filename collisions detected: {filename_collisions}")

    # Check color mode and bit depth for all images
    non_8bit = [f["rel_path"] for f in disk_files.values() if f["bit_depth"] != 8]
    non_rgba = [f["rel_path"] for f in disk_files.values() if f["color_type"] != 6 or f["pil_mode"] != "RGBA"]
    
    if not non_8bit and not non_rgba:
        results["checks"]["all_images_8bit_rgba"] = True
        print("  -> OK: 100% of images are 8-bit RGBA (color_type=6, mode=RGBA).")
    else:
        results["checks"]["all_images_8bit_rgba"] = False
        if non_8bit:
            results["errors"].append(f"Non 8-bit images: {non_8bit}")
        if non_rgba:
            results["errors"].append(f"Non RGBA images: {non_rgba}")

    # -------------------------------------------------------------
    # Check 4: Bijective Reconciliation with explorer_survey_1 Baseline
    # -------------------------------------------------------------
    print("\n[Check 4] Cross-checking disk inventory against explorer_survey_1 baseline...")
    try:
        baseline_explorer = parse_explorer_baseline(EXPLORER_HANDOFF)
        print(f"  -> Successfully parsed {len(baseline_explorer)} records from {EXPLORER_HANDOFF}")
    except Exception as e:
        results["errors"].append(f"Failed to parse explorer_survey_1 baseline: {e}")
        baseline_explorer = {}

    if len(baseline_explorer) != 102:
        results["errors"].append(f"Explorer baseline count expected 102, got {len(baseline_explorer)}")
        results["checks"]["explorer_baseline_parsed"] = False
    else:
        results["checks"]["explorer_baseline_parsed"] = True

    explorer_missing_on_disk = set(baseline_explorer.keys()) - set(disk_files.keys())
    disk_extra_vs_explorer = set(disk_files.keys()) - set(baseline_explorer.keys())
    hash_mismatches_explorer = []
    size_mismatches_explorer = []
    dim_mismatches_explorer = []

    for fname, base in baseline_explorer.items():
        if fname in disk_files:
            disk = disk_files[fname]
            if base["sha256"] != disk["sha256"]:
                hash_mismatches_explorer.append((fname, base["sha256"], disk["sha256"]))
            if base["size"] != disk["size"]:
                size_mismatches_explorer.append((fname, base["size"], disk["size"]))
            if (base["width"], base["height"]) != (disk["width"], disk["height"]):
                dim_mismatches_explorer.append((fname, (base["width"], base["height"]), (disk["width"], disk["height"])))

    if not explorer_missing_on_disk and not disk_extra_vs_explorer and not hash_mismatches_explorer and not size_mismatches_explorer and not dim_mismatches_explorer:
        results["checks"]["bijective_explorer_baseline"] = True
        print("  -> OK: 100% Bijective Match with explorer_survey_1 baseline (0 missing, 0 extra, 0 hash mismatches).")
    else:
        results["checks"]["bijective_explorer_baseline"] = False
        if explorer_missing_on_disk:
            results["errors"].append(f"Files in explorer baseline missing from disk: {explorer_missing_on_disk}")
        if disk_extra_vs_explorer:
            results["errors"].append(f"Files on disk not in explorer baseline: {disk_extra_vs_explorer}")
        if hash_mismatches_explorer:
            results["errors"].append(f"Hash mismatches against explorer baseline: {hash_mismatches_explorer}")
        if size_mismatches_explorer:
            results["errors"].append(f"Size mismatches against explorer baseline: {size_mismatches_explorer}")
        if dim_mismatches_explorer:
            results["errors"].append(f"Dimension mismatches against explorer baseline: {dim_mismatches_explorer}")

    # -------------------------------------------------------------
    # Check 5: Bijective Reconciliation with PANEL_MANIFEST.json
    # -------------------------------------------------------------
    print("\n[Check 5] Cross-checking disk inventory against PANEL_MANIFEST.json...")
    try:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)
        manifest_files = {item["filename"]: item for item in manifest_data["files"]}
        manifest_counts = manifest_data.get("panel_counts", {})
        print(f"  -> Successfully loaded manifest: {len(manifest_files)} files, {len(manifest_counts)} panel categories.")
    except Exception as e:
        results["errors"].append(f"Failed to load PANEL_MANIFEST.json: {e}")
        manifest_files = {}
        manifest_counts = {}

    if len(manifest_files) == 102 and manifest_data.get("total_files") == 102 and manifest_data.get("total_panels") == 20:
        results["checks"]["manifest_header_valid"] = True
    else:
        results["checks"]["manifest_header_valid"] = False
        results["errors"].append("PANEL_MANIFEST.json metadata invalid or totals != 102/20")

    manifest_missing_on_disk = set(manifest_files.keys()) - set(disk_files.keys())
    disk_extra_vs_manifest = set(disk_files.keys()) - set(manifest_files.keys())
    manifest_hash_mismatches = []
    manifest_panel_mismatches = []
    manifest_size_mismatches = []

    for fname, m_entry in manifest_files.items():
        if fname in disk_files:
            disk = disk_files[fname]
            if m_entry["sha256"].lower() != disk["sha256"]:
                manifest_hash_mismatches.append((fname, m_entry["sha256"], disk["sha256"]))
            if m_entry["panel"] != disk["panel"]:
                manifest_panel_mismatches.append((fname, m_entry["panel"], disk["panel"]))
            if m_entry["size"] != disk["size"]:
                manifest_size_mismatches.append((fname, m_entry["size"], disk["size"]))

    # Compare panel distribution
    panel_count_mismatches = {}
    for panel in CANONICAL_PANELS:
        exp_c = manifest_counts.get(panel, 0)
        act_c = panel_counts.get(panel, 0)
        if exp_c != act_c:
            panel_count_mismatches[panel] = (exp_c, act_c)

    if (not manifest_missing_on_disk and not disk_extra_vs_manifest and
        not manifest_hash_mismatches and not manifest_panel_mismatches and
        not manifest_size_mismatches and not panel_count_mismatches):
        results["checks"]["bijective_manifest_match"] = True
        print("  -> OK: 100% Bijective Match with PANEL_MANIFEST.json (0 missing, 0 extra, 0 hash mismatches).")
    else:
        results["checks"]["bijective_manifest_match"] = False
        if manifest_missing_on_disk:
            results["errors"].append(f"Files in manifest missing from disk: {manifest_missing_on_disk}")
        if disk_extra_vs_manifest:
            results["errors"].append(f"Files on disk not in manifest: {disk_extra_vs_manifest}")
        if manifest_hash_mismatches:
            results["errors"].append(f"Hash mismatches against manifest: {manifest_hash_mismatches}")
        if manifest_panel_mismatches:
            results["errors"].append(f"Panel folder placement mismatches: {manifest_panel_mismatches}")
        if manifest_size_mismatches:
            results["errors"].append(f"Size mismatches against manifest: {manifest_size_mismatches}")
        if panel_count_mismatches:
            results["errors"].append(f"Panel count discrepancies: {panel_count_mismatches}")

    # -------------------------------------------------------------
    # Check 6: Edge Case - 11 Portrait-in-Horizontal Screenshots
    # -------------------------------------------------------------
    print("\n[Check 6] Auditing the 11 Portrait-in-Horizontal screenshots...")
    portrait_in_horiz_fnames = [
        "Screenshot_20261024-201235_Lightroom.png",
        "Screenshot_20261024-201248_Lightroom.png",
        "Screenshot_20261024-201254_Lightroom.png",
        "Screenshot_20261024-201303_Lightroom.png",
        "Screenshot_20261024-201307_Lightroom.png",
        "Screenshot_20261024-201313_Lightroom.png",
        "Screenshot_20261024-201413_Lightroom.png",
        "Screenshot_20261024-201419_Lightroom.png",
        "Screenshot_20261024-201426_Lightroom.png",
        "Screenshot_20261024-201435_Lightroom.png",
        "Screenshot_20261024-201449_Lightroom.png",
    ]
    edge_case_ok = True
    edge_details = {}
    for fname in portrait_in_horiz_fnames:
        if fname not in disk_files:
            results["errors"].append(f"Edge case file {fname} missing from disk!")
            edge_case_ok = False
            continue
        item = disk_files[fname]
        edge_details[fname] = {
            "panel": item["panel"],
            "dims": f"{item['width']}x{item['height']}",
            "orientation": item["orientation"],
            "sha256": item["sha256"]
        }
        if item["orientation"] != "portrait" or (item["width"], item["height"]) != (1220, 2712):
            results["errors"].append(f"Edge case file {fname} has invalid dimensions {item['width']}x{item['height']}")
            edge_case_ok = False

    results["checks"]["edge_case_11_portrait_in_horizontal"] = edge_case_ok
    results["metrics"]["edge_case_11_details"] = edge_details
    if edge_case_ok:
        print("  -> OK: All 11 portrait-in-horizontal files verified with correct portrait dimensions (1220x2712).")
        for fn, d in edge_details.items():
            print(f"     * {fn} -> Panel: {d['panel']}, Dims: {d['dims']}")

    # -------------------------------------------------------------
    # Check 7: Single-Item & Max-Capacity Boundary Panels
    # -------------------------------------------------------------
    print("\n[Check 7] Auditing Boundary Panels (Single-Item & High-Capacity)...")
    boundary_ok = True
    # Single item panels: Community (1), Optics (1)
    if panel_counts.get("Community") != 1:
        results["errors"].append(f"Boundary fail: Community panel expected 1 file, got {panel_counts.get('Community')}")
        boundary_ok = False
    if panel_counts.get("Optics") != 1:
        results["errors"].append(f"Boundary fail: Optics panel expected 1 file, got {panel_counts.get('Optics')}")
        boundary_ok = False

    # High capacity panels: Crop (13), Masking (11), Editor_and_Export (9)
    if panel_counts.get("Crop") != 13:
        results["errors"].append(f"Boundary fail: Crop panel expected 13 files, got {panel_counts.get('Crop')}")
        boundary_ok = False
    if panel_counts.get("Masking") != 11:
        results["errors"].append(f"Boundary fail: Masking panel expected 11 files, got {panel_counts.get('Masking')}")
        boundary_ok = False
    if panel_counts.get("Editor_and_Export") != 9:
        results["errors"].append(f"Boundary fail: Editor_and_Export panel expected 9 files, got {panel_counts.get('Editor_and_Export')}")
        boundary_ok = False

    results["checks"]["boundary_panels_valid"] = boundary_ok
    if boundary_ok:
        print("  -> OK: Boundary panel capacities verified (Community: 1, Optics: 1, Crop: 13, Masking: 11, Editor_and_Export: 9).")

    # -------------------------------------------------------------
    # Check 8: Svg Icons Vector Repository Protection
    # -------------------------------------------------------------
    print("\n[Check 8] Auditing Svg Icons directory for unintended contamination...")
    svg_icons_dir = os.path.join(DESIGN_REF_DIR, "Svg Icons")
    svg_count = 0
    non_svg_files = []
    if os.path.isdir(svg_icons_dir):
        for root, _, files in os.walk(svg_icons_dir):
            for file in files:
                if file.lower().endswith(".svg"):
                    svg_count += 1
                elif file.lower().endswith(".png"):
                    non_svg_files.append(os.path.join(root, file))
    
    if non_svg_files:
        results["errors"].append(f"Found PNG files contaminating Svg Icons: {non_svg_files}")
        results["checks"]["svg_icons_protected"] = False
    else:
        results["checks"]["svg_icons_protected"] = True
        print(f"  -> OK: Svg Icons contains {svg_count} SVG assets, 0 contaminating PNGs.")

    # -------------------------------------------------------------
    # Final Verdict Assessment
    # -------------------------------------------------------------
    all_checks_passed = all(results["checks"].values()) and len(results["errors"]) == 0
    results["passed"] = all_checks_passed

    print("\n" + "=" * 80)
    print("INDEPENDENT ORACLE AUDIT SUMMARY:")
    print("=" * 80)
    for check_name, status in results["checks"].items():
        status_str = "PASS" if status else "FAIL"
        print(f"  [{status_str}] {check_name}")

    print(f"\nTotal Errors: {len(results['errors'])}")
    print(f"Total Warnings: {len(results['warnings'])}")
    print("=" * 80)

    if all_checks_passed:
        print("VERDICT: APPROVE (100% Cryptographic & Structural Integrity Confirmed)")
        print("=" * 80)
        return 0, results
    else:
        print("VERDICT: REQUEST_CHANGES (Defects Detected)")
        for err in results["errors"]:
            print(f"  - ERROR: {err}")
        print("=" * 80)
        return 1, results

if __name__ == "__main__":
    exit_code, audit_results = run_empirical_oracle()
    # Also write JSON summary report to Challenger directory
    output_json_path = os.path.join(PROJECT_ROOT, ".agents", "teamwork", "challenger_m1_2", "oracle_audit_results.json")
    # Clean non-serializable objects
    serializable_results = {
        "passed": audit_results["passed"],
        "checks": audit_results["checks"],
        "errors": audit_results["errors"],
        "warnings": audit_results["warnings"],
        "metrics": audit_results["metrics"],
        "panel_distribution": audit_results["panel_distribution"],
        "total_files": len(audit_results["disk_inventory"])
    }
    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(serializable_results, f, indent=2)
    print(f"Audit results JSON written to: {output_json_path}")
    sys.exit(exit_code)
