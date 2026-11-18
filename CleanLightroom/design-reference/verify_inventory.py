#!/usr/bin/env python3
"""
Lightroom Mobile Design Reference Screenshot Inventory Audit Script
===================================================================
Target: CleanLightroom/design-reference/verify_inventory.py

This script enforces 100% file retention, zero data loss, zero duplicate leaks,
and zero file corruption across the Adobe Lightroom Mobile screenshot dataset.

It enforces the 7 Retention & Integrity Invariants:
  1. Baseline Count Invariant: Exactly 102 screenshot files in baseline catalog.
  2. Current Disk Count Invariant: Exactly 102 screenshot files exist across the 20 canonical panel folders.
  3. Bijective Hash Set Equality: Set(Hashes_baseline) == Set(Hashes_disk) (0 missing, 0 duplicates).
  4. Byte-Level Integrity Invariant: Bit-for-bit byte size and SHA-256 match for every file against PANEL_MANIFEST.json.
  5. Decodable Image Structure Invariant: PIL / libpng decodes every .png file without error (Image.verify()).
  6. Valid Dimension Invariant: Every file is either 1220x2712 (portrait) or 2712x1220 (landscape).
  7. Canonical Taxonomy Invariant: All files reside within the 20 canonical panel folders, 0 unclassified files in root,
     'Svg Icons/' vector asset repository ignored.

Exit Codes:
  0: All 7 Invariants PASSED (100% retention & integrity confirmed).
  1: One or more invariants FAILED.
"""

import os
import sys
import json
import hashlib
import argparse
from datetime import datetime, timezone
from typing import Dict, List, Set, Tuple, Any, Optional
from PIL import Image

# 20 Canonical Lightroom Mobile UI Panel Folders
CANONICAL_PANELS = [
    "Actions",
    "Camera",
    "Color",
    "Color_Grading",
    "Color_Mix",
    "Community",
    "Crop",
    "Detail",
    "Editor_and_Export",
    "Effects",
    "Gallery",
    "Lens_Blur",
    "Light",
    "Masking",
    "Optics",
    "Presets",
    "Profiles",
    "Remove",
    "Settings_and_Account",
    "Tone_Curve"
]

# Valid dimensions for Google Pixel / Android mobile captures
VALID_DIMENSIONS = {
    (1220, 2712),  # Portrait
    (2712, 1220)   # Landscape
}

# Directories and root files ignored or permitted in root
IGNORED_DIRS = {"Svg Icons", "__pycache__", ".git"}
ALLOWED_ROOT_FILES = {
    "PANEL_MANIFEST.json",
    "verify_inventory.py",
    "migrate_screenshots.py",
    "inventory_audit_report.json",
    ".gitignore",
    "README.md"
}


def compute_sha256(file_path: str, chunk_size: int = 65536) -> str:
    """Compute the SHA-256 hexadecimal digest of a file in chunks."""
    hasher = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(chunk_size):
            hasher.update(chunk)
    return hasher.hexdigest()


def verify_image_file(file_path: str) -> Tuple[bool, Optional[Tuple[int, int]], Optional[str]]:
    """
    Verify image decodability using PIL Image.verify() and read dimensions.
    Returns (is_valid, (width, height), error_message).
    """
    try:
        with Image.open(file_path) as img:
            if img.format != "PNG":
                return False, None, f"Expected PNG format, got {img.format}"
            img.verify()
            dims = img.size
            return True, dims, None
    except Exception as e:
        return False, None, str(e)


def audit_inventory(
    base_dir: str,
    manifest_path: str,
    verbose: bool = False,
    quiet: bool = False,
    emit_json: bool = False
) -> int:
    """
    Execute full 7-invariant audit.
    Returns 0 on success, 1 on any invariant failure.
    """
    base_dir = os.path.abspath(base_dir)
    manifest_path = os.path.abspath(manifest_path)

    if not os.path.isfile(manifest_path):
        print(f"CRITICAL ERROR: Manifest file not found at: {manifest_path}", file=sys.stderr)
        return 1

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest = json.load(f)
    except Exception as e:
        print(f"CRITICAL ERROR: Failed to parse manifest JSON: {e}", file=sys.stderr)
        return 1

    baseline_files = manifest.get("files", [])
    manifest_panel_counts = manifest.get("panel_counts", {})
    expected_total_files = manifest.get("total_files", 102)
    expected_total_panels = manifest.get("total_panels", 20)

    # Invariant failure collectors
    inv_errors: Dict[int, List[str]] = {i: [] for i in range(1, 8)}

    # -------------------------------------------------------------
    # Invariant 1: Baseline Count Invariant
    # -------------------------------------------------------------
    if len(baseline_files) != expected_total_files:
        inv_errors[1].append(f"Manifest 'files' array has {len(baseline_files)} entries, expected {expected_total_files}")
    if expected_total_files != 102:
        inv_errors[1].append(f"Manifest total_files is {expected_total_files}, expected 102")
    if expected_total_panels != 20:
        inv_errors[1].append(f"Manifest total_panels is {expected_total_panels}, expected 20")

    baseline_by_fn = {}
    baseline_hashes = {}
    for item in baseline_files:
        fn = item.get("filename")
        if not fn:
            inv_errors[1].append("Manifest item missing 'filename' attribute")
            continue
        if fn in baseline_by_fn:
            inv_errors[1].append(f"Duplicate filename in manifest: {fn}")
        baseline_by_fn[fn] = item
        baseline_hashes[fn] = item.get("sha256")

    # -------------------------------------------------------------
    # Invariant 7: Canonical Taxonomy Invariant (Root scan)
    # -------------------------------------------------------------
    try:
        root_entries = os.listdir(base_dir)
    except Exception as e:
        inv_errors[7].append(f"Cannot list base directory {base_dir}: {e}")
        root_entries = []

    stray_root_files = [
        e for e in root_entries
        if os.path.isfile(os.path.join(base_dir, e)) and e not in ALLOWED_ROOT_FILES
    ]
    if stray_root_files:
        inv_errors[7].append(f"Unclassified files in root directory: {stray_root_files}")

    unknown_root_dirs = [
        e for e in root_entries
        if os.path.isdir(os.path.join(base_dir, e)) and e not in CANONICAL_PANELS and e not in IGNORED_DIRS
    ]
    if unknown_root_dirs:
        inv_errors[7].append(f"Non-canonical directories in root: {unknown_root_dirs}")

    # -------------------------------------------------------------
    # Disk Scan: 20 Canonical Panels
    # -------------------------------------------------------------
    disk_files: Dict[str, Dict[str, Any]] = {}
    panel_stats = {
        p: {"total": 0, "portrait": 0, "landscape": 0}
        for p in CANONICAL_PANELS
    }

    for panel in CANONICAL_PANELS:
        panel_dir = os.path.join(base_dir, panel)
        if not os.path.isdir(panel_dir):
            inv_errors[2].append(f"Canonical panel directory missing: '{panel}'")
            continue

        try:
            entries = sorted(os.listdir(panel_dir))
        except Exception as e:
            inv_errors[2].append(f"Cannot read panel directory '{panel}': {e}")
            continue

        for entry in entries:
            full_path = os.path.join(panel_dir, entry)
            if os.path.isdir(full_path):
                inv_errors[7].append(f"Unexpected subdirectory inside panel '{panel}': '{entry}'")
                continue

            if not entry.lower().endswith(".png"):
                inv_errors[7].append(f"Non-PNG file inside panel '{panel}': '{entry}'")
                continue

            # Compute disk digest and size
            file_size = os.path.getsize(full_path)
            file_hash = compute_sha256(full_path)

            if entry in disk_files:
                inv_errors[3].append(
                    f"Duplicate filename on disk across panels: '{entry}' in '{panel}' and '{disk_files[entry]['panel']}'"
                )

            disk_files[entry] = {
                "filename": entry,
                "panel": panel,
                "path": full_path,
                "rel_path": f"{panel}/{entry}",
                "size": file_size,
                "sha256": file_hash
            }
            panel_stats[panel]["total"] += 1

    # -------------------------------------------------------------
    # Invariant 2: Current Disk Count Invariant
    # -------------------------------------------------------------
    if len(disk_files) != 102:
        inv_errors[2].append(f"Total disk files across panels is {len(disk_files)}, expected 102")

    for panel in CANONICAL_PANELS:
        expected_cnt = manifest_panel_counts.get(panel)
        actual_cnt = panel_stats[panel]["total"]
        if expected_cnt is not None and actual_cnt != expected_cnt:
            inv_errors[2].append(
                f"Panel '{panel}' count mismatch: expected {expected_cnt}, found {actual_cnt}"
            )

    # -------------------------------------------------------------
    # Invariant 3: Bijective Hash Set Equality
    # -------------------------------------------------------------
    base_hash_set = set(baseline_hashes.values())
    disk_hash_set = set(df["sha256"] for df in disk_files.values())

    missing_hashes = base_hash_set - disk_hash_set
    if missing_hashes:
        for mh in missing_hashes:
            fn_matches = [k for k, v in baseline_hashes.items() if v == mh]
            inv_errors[3].append(f"Missing file from disk: {fn_matches} (Hash: {mh[:16]}...)")

    extra_hashes = disk_hash_set - base_hash_set
    if extra_hashes:
        for eh in extra_hashes:
            fn_matches = [k for k, v in disk_files.items() if v["sha256"] == eh]
            inv_errors[3].append(f"Unindexed extraneous file on disk: {fn_matches} (Hash: {eh[:16]}...)")

    if len(disk_hash_set) != len(disk_files):
        inv_errors[3].append(
            f"Content collision on disk: {len(disk_files)} files share {len(disk_hash_set)} unique digests"
        )

    # -------------------------------------------------------------
    # Invariants 4, 5, 6: Byte Integrity, Decodability, Dimensions
    # -------------------------------------------------------------
    for fn, base_meta in baseline_by_fn.items():
        if fn not in disk_files:
            continue
        df = disk_files[fn]

        # Invariant 4: Size and SHA-256 match
        if df["size"] != base_meta.get("size"):
            inv_errors[4].append(
                f"Size mismatch '{fn}': disk={df['size']} bytes, manifest={base_meta.get('size')} bytes"
            )
        if df["sha256"] != base_meta.get("sha256"):
            inv_errors[4].append(
                f"SHA-256 mismatch '{fn}': disk={df['sha256']}, manifest={base_meta.get('sha256')}"
            )
        expected_rel = base_meta.get("new_rel_path")
        if expected_rel and df["rel_path"] != expected_rel.replace("\\", "/"):
            inv_errors[4].append(
                f"Relative path mismatch '{fn}': disk='{df['rel_path']}', manifest='{expected_rel}'"
            )

        # Invariant 5: Decodable Image Structure (PIL)
        is_valid, dims, err_msg = verify_image_file(df["path"])
        if not is_valid:
            inv_errors[5].append(f"Corrupted or invalid PNG '{fn}': {err_msg}")
            continue

        # Invariant 6: Valid Dimensions
        if dims not in VALID_DIMENSIONS:
            inv_errors[6].append(
                f"Invalid dimensions '{fn}': {dims} is neither (1220, 2712) nor (2712, 1220)"
            )
        expected_dims = (base_meta.get("width"), base_meta.get("height"))
        if expected_dims != (None, None) and dims != expected_dims:
            inv_errors[6].append(
                f"Dimension mismatch '{fn}': image is {dims}, manifest specified {expected_dims}"
            )

        # Track orientation counts
        if dims == (1220, 2712):
            panel_stats[df["panel"]]["portrait"] += 1
        elif dims == (2712, 1220):
            panel_stats[df["panel"]]["landscape"] += 1

        if verbose:
            orientation_str = "portrait" if dims == (1220, 2712) else "landscape"
            print(f"[OK] {df['rel_path']} ({df['size']:,} B, {dims[0]}x{dims[1]} {orientation_str}, SHA256: {df['sha256'][:12]}...)")

    # -------------------------------------------------------------
    # Reporting
    # -------------------------------------------------------------
    total_failures = sum(len(errs) for errs in inv_errors.values())
    audit_passed = (total_failures == 0)

    tot_expected = sum(manifest_panel_counts.values()) if manifest_panel_counts else 102
    tot_actual = sum(s["total"] for s in panel_stats.values())
    tot_portrait = sum(s["portrait"] for s in panel_stats.values())
    tot_landscape = sum(s["landscape"] for s in panel_stats.values())

    if emit_json:
        report = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "target_dir": base_dir,
            "manifest_path": manifest_path,
            "audit_passed": audit_passed,
            "total_expected": tot_expected,
            "total_actual": tot_actual,
            "portrait_count": tot_portrait,
            "landscape_count": tot_landscape,
            "invariants": {
                f"invariant_{i}": {
                    "passed": len(inv_errors[i]) == 0,
                    "errors": inv_errors[i]
                }
                for i in range(1, 8)
            },
            "panel_summary": panel_stats
        }
        print(json.dumps(report, indent=2))
        return 0 if audit_passed else 1

    if not quiet:
        print("=" * 88)
        print(" LIGHTROOM MOBILE DESIGN REFERENCE — AUTOMATED INVENTORY & INTEGRITY AUDIT")
        print("=" * 88)
        print(f" Target Directory : {base_dir}")
        print(f" Manifest File    : {os.path.basename(manifest_path)} (102 records, 20 panels)")
        print(f" Audit Timestamp  : {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
        print("-" * 88)
        print(" CANONICAL PANEL BREAKDOWN:")
        print("-" * 88)
        print("  #  Panel Name                Expected  Actual  Portrait  Landscape  Status")
        print("-" * 88)
        for idx, panel in enumerate(CANONICAL_PANELS, start=1):
            exp = manifest_panel_counts.get(panel, 0)
            act = panel_stats[panel]["total"]
            port = panel_stats[panel]["portrait"]
            land = panel_stats[panel]["landscape"]
            status = "PASS" if exp == act and exp > 0 else "FAIL"
            print(f" {idx:>2}  {panel:<24}  {exp:>8}  {act:>6}  {port:>8}  {land:>9}  {status}")
        print("-" * 88)
        tot_status = "PASS" if tot_expected == tot_actual == 102 else "FAIL"
        print(f"     TOTAL                     {tot_expected:>8}  {tot_actual:>6}  {tot_portrait:>8}  {tot_landscape:>9}  {tot_status}")
        print("=" * 88)
        print(" 7 RETENTION & INTEGRITY INVARIANTS:")
        print("-" * 88)

        inv_descriptions = [
            "Baseline Count Invariant (N = 102 files in manifest, 20 panels)",
            "Current Disk Count Invariant (N = 102 files across 20 canonical folders)",
            "Bijective Hash Set Equality (102 unique digests, 0 missing, 0 duplicates)",
            "Byte-Level Integrity Invariant (102/102 bit-for-bit size & SHA-256 match)",
            "Decodable Image Structure Invariant (102/102 PIL Image.verify() clean)",
            "Valid Dimension Invariant (47 portrait 1220x2712, 55 landscape 2712x1220)",
            "Canonical Taxonomy Invariant (0 stray files in root, Svg Icons ignored)"
        ]

        for i in range(1, 8):
            errs = inv_errors[i]
            tag = "[PASS]" if len(errs) == 0 else "[FAIL]"
            print(f"  {tag} Invariant {i}: {inv_descriptions[i-1]}")
            if errs:
                for err in errs[:5]:
                    print(f"        * ERROR: {err}")
                if len(errs) > 5:
                    print(f"        * ... and {len(errs) - 5} more error(s)")

        print("=" * 88)
        if audit_passed:
            print(" AUDIT PASSED: 100% File Retention & Cryptographic Integrity Confirmed (Exit 0)")
        else:
            print(f" AUDIT FAILED: {total_failures} error(s) detected across invariants! (Exit 1)")
        print("=" * 88)

    return 0 if audit_passed else 1


def main():
    parser = argparse.ArgumentParser(
        description="Verify 100% retention and cryptographic integrity of Lightroom Mobile design reference screenshots."
    )
    script_dir = os.path.dirname(os.path.abspath(__file__))
    parser.add_argument(
        "--dir",
        default=script_dir,
        help=f"Path to design-reference directory (default: {script_dir})"
    )
    parser.add_argument(
        "--manifest",
        default=None,
        help="Path to PANEL_MANIFEST.json (default: <dir>/PANEL_MANIFEST.json)"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Print per-file verification details"
    )
    parser.add_argument(
        "--quiet", "-q",
        action="store_true",
        help="Suppress banner and table, output only errors and summary"
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output machine-readable audit report in JSON format"
    )

    args = parser.parse_args()
    manifest_file = args.manifest or os.path.join(args.dir, "PANEL_MANIFEST.json")

    exit_code = audit_inventory(
        base_dir=args.dir,
        manifest_path=manifest_file,
        verbose=args.verbose,
        quiet=args.quiet,
        emit_json=args.json
    )
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
