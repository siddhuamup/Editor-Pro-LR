#!/usr/bin/env python3
"""
Lightroom Mobile Screenshot Migration Engine (Milestone 1)
==========================================================
Target: CleanLightroom/design-reference/migrate_screenshots.py

Executes atomic, non-destructive, and idempotent relocation of 102 mobile reference
screenshots into 20 canonical panel folders according to PANEL_MANIFEST.json.
Guarantees 100% byte integrity, zero file loss, zero filename renames, and clean workspace.
"""

import os
import sys
import json
import hashlib
from typing import Dict, List, Set, Tuple

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MANIFEST_PATH = os.path.join(BASE_DIR, "PANEL_MANIFEST.json")
HORIZONTAL_DIR_NAME = "horizontal screen UI(same as verticle)"
HORIZONTAL_DIR_PATH = os.path.join(BASE_DIR, HORIZONTAL_DIR_NAME)
EXCLUDED_DIRS = {"Svg Icons", "__pycache__", ".git"}


def sha256_file(filepath: str, chunk_size: int = 65536) -> str:
    """Compute SHA-256 checksum of a file in 64KB streaming chunks."""
    h = hashlib.sha256()
    with open(filepath, "rb") as fp:
        while chunk := fp.read(chunk_size):
            h.update(chunk)
    return h.hexdigest()


def execute_migration() -> int:
    print("=" * 76)
    print(" LIGHTROOM MOBILE SCREENSHOT MIGRATION ENGINE (MILESTONE 1)")
    print("=" * 76)

    # -------------------------------------------------------------
    # PHASE 1: MANIFEST INGESTION & VALIDATION
    # -------------------------------------------------------------
    if not os.path.isfile(MANIFEST_PATH):
        print(f"FATAL: Manifest not found at {MANIFEST_PATH}", file=sys.stderr)
        return 1

    try:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as fp:
            manifest = json.load(fp)
    except Exception as e:
        print(f"FATAL: Failed to parse manifest JSON: {e}", file=sys.stderr)
        return 1

    files_catalog = manifest.get("files", [])
    expected_panels = manifest.get("panel_counts", {})
    total_files = manifest.get("total_files", 102)
    total_panels = manifest.get("total_panels", 20)

    if len(files_catalog) != total_files or total_files != 102:
        print(
            f"FATAL: Manifest file count mismatch: expected 102, found {len(files_catalog)}",
            file=sys.stderr
        )
        return 1

    if len(expected_panels) != total_panels or total_panels != 20:
        print(
            f"FATAL: Manifest panel count mismatch: expected 20, found {len(expected_panels)}",
            file=sys.stderr
        )
        return 1

    filenames = [f["filename"] for f in files_catalog]
    if len(filenames) != len(set(filenames)):
        print("FATAL: Duplicate filenames detected in manifest catalog!", file=sys.stderr)
        return 1

    print(f"[Phase 1 OK] Manifest verified: 102 files across {len(expected_panels)} canonical panels.")

    # -------------------------------------------------------------
    # PHASE 2: CANONICAL DIRECTORY PROVISIONING
    # -------------------------------------------------------------
    for panel in expected_panels.keys():
        panel_path = os.path.join(BASE_DIR, panel)
        os.makedirs(panel_path, exist_ok=True)
    print(f"[Phase 2 OK] Verified / created all {len(expected_panels)} canonical panel folders.")

    # -------------------------------------------------------------
    # PHASE 3: IDEMPOTENT ATOMIC RELOCATION & HASH GUARDING
    # -------------------------------------------------------------
    moved_count = 0
    verified_in_place_count = 0
    cleaned_source_duplicates = 0
    total_bytes_verified = 0

    for idx, entry in enumerate(files_catalog, start=1):
        filename = entry["filename"]
        rel_src = entry["rel_path"].replace("/", os.sep)
        rel_dst = entry["new_rel_path"].replace("/", os.sep)
        src_path = os.path.join(BASE_DIR, rel_src)
        dst_path = os.path.join(BASE_DIR, rel_dst)
        expected_sha = entry["sha256"]
        expected_size = entry["size"]

        dst_exists = os.path.isfile(dst_path)
        src_exists = os.path.isfile(src_path)

        if dst_exists:
            # Case A: Destination exists. Verify its cryptographic integrity.
            dst_size = os.path.getsize(dst_path)
            if dst_size != expected_size:
                print(
                    f"FATAL: Size mismatch at destination {rel_dst}: expected {expected_size}, got {dst_size}",
                    file=sys.stderr
                )
                return 1
            dst_sha = sha256_file(dst_path)
            if dst_sha != expected_sha:
                print(
                    f"FATAL: Hash mismatch at destination {rel_dst}: expected {expected_sha}, got {dst_sha}",
                    file=sys.stderr
                )
                return 1

            # Destination is sound. Check if redundant copy remains in source.
            if src_exists:
                src_sha = sha256_file(src_path)
                if src_sha == expected_sha:
                    os.remove(src_path)
                    cleaned_source_duplicates += 1
                else:
                    print(
                        f"FATAL: Source copy mismatch for {rel_src} while destination is populated!",
                        file=sys.stderr
                    )
                    return 1

            verified_in_place_count += 1
            total_bytes_verified += dst_size

        elif src_exists:
            # Case B: Destination missing, source exists. Verify source, then atomic move.
            src_size = os.path.getsize(src_path)
            if src_size != expected_size:
                print(
                    f"FATAL: Size mismatch at source {rel_src}: expected {expected_size}, got {src_size}",
                    file=sys.stderr
                )
                return 1
            src_sha = sha256_file(src_path)
            if src_sha != expected_sha:
                print(
                    f"FATAL: Hash mismatch at source {rel_src}: expected {expected_sha}, got {src_sha}",
                    file=sys.stderr
                )
                return 1

            os.makedirs(os.path.dirname(dst_path), exist_ok=True)
            # Atomic rename on NTFS
            os.replace(src_path, dst_path)

            # Immediate post-move verification
            post_size = os.path.getsize(dst_path)
            post_sha = sha256_file(dst_path)
            if post_size != expected_size or post_sha != expected_sha:
                print(
                    f"FATAL: Integrity failed immediately post-move for {rel_dst}!",
                    file=sys.stderr
                )
                return 1

            moved_count += 1
            total_bytes_verified += post_size

        else:
            # Case C: Neither source nor destination exists.
            print(
                f"FATAL: File {filename} not found at source ({rel_src}) or target ({rel_dst})!",
                file=sys.stderr
            )
            return 1

    print(f"[Phase 3 OK] File operations completed successfully:")
    print(f"  - Newly moved: {moved_count}")
    print(f"  - Verified in-place: {verified_in_place_count}")
    print(f"  - Cleaned redundant sources: {cleaned_source_duplicates}")
    print(f"  - Verified payload: {total_bytes_verified:,} bytes (~{total_bytes_verified / (1024*1024):.2f} MB)")

    # -------------------------------------------------------------
    # PHASE 4: WORKSPACE CLEANUP & QUARANTINE VERIFICATION
    # -------------------------------------------------------------
    if os.path.isdir(HORIZONTAL_DIR_PATH):
        leftover = os.listdir(HORIZONTAL_DIR_PATH)
        if len(leftover) == 0:
            os.rmdir(HORIZONTAL_DIR_PATH)
            print(f"[Phase 4 OK] Safely retired empty legacy directory: '{HORIZONTAL_DIR_NAME}'.")
        else:
            print(f"FATAL: Horizontal folder not empty: {leftover}", file=sys.stderr)
            return 1
    else:
        print(f"[Phase 4 OK] Legacy directory '{HORIZONTAL_DIR_NAME}' confirmed retired/absent.")

    # Verify no stray PNG files in root
    stray_pngs = [
        f for f in os.listdir(BASE_DIR)
        if os.path.isfile(os.path.join(BASE_DIR, f)) and f.lower().endswith(".png")
    ]
    if stray_pngs:
        print(f"FATAL: Stray PNG files remain in root: {stray_pngs}", file=sys.stderr)
        return 1

    # Verify Svg Icons preserved
    svg_path = os.path.join(BASE_DIR, "Svg Icons")
    if not (os.path.isdir(svg_path) and len(os.listdir(svg_path)) > 0):
        print("FATAL: Svg Icons directory missing or empty!", file=sys.stderr)
        return 1
    print("[Phase 4 OK] Workspace clean: 0 stray PNGs in root, Svg Icons intact.")

    # -------------------------------------------------------------
    # PHASE 5: POST-FLIGHT CRYPTOGRAPHIC RECONCILIATION
    # -------------------------------------------------------------
    actual_counts = {p: 0 for p in expected_panels}
    actual_hashes = set()

    for entry in os.listdir(BASE_DIR):
        full = os.path.join(BASE_DIR, entry)
        if not os.path.isdir(full) or entry in EXCLUDED_DIRS:
            continue
        for fname in os.listdir(full):
            if fname.lower().endswith(".png"):
                fpath = os.path.join(full, fname)
                actual_counts[entry] = actual_counts.get(entry, 0) + 1
                actual_hashes.add(sha256_file(fpath))

    total_actual_files = sum(actual_counts.values())
    if total_actual_files != 102:
        print(f"FATAL: Post-flight count mismatch: expected 102, found {total_actual_files}", file=sys.stderr)
        return 1

    if len(actual_hashes) != 102:
        print(f"FATAL: Duplicate hash detected on disk: {102 - len(actual_hashes)} duplicate hashes", file=sys.stderr)
        return 1

    # Compare panel-by-panel
    for panel, exp_c in expected_panels.items():
        act_c = actual_counts.get(panel, 0)
        if exp_c != act_c:
            print(f"FATAL: Panel '{panel}' count mismatch: expected {exp_c}, found {act_c}", file=sys.stderr)
            return 1

    print("=" * 76)
    print(" MIGRATION EXECUTION & AUDIT: 100% SUCCESS")
    print(f"  - 102/102 files verified across {len(expected_panels)} canonical panels")
    print("  - 0 missing, 0 duplicates, 0 corrupted, 0 renames")
    print("=" * 76)
    return 0


if __name__ == "__main__":
    sys.exit(execute_migration())
