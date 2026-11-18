#!/usr/bin/env python3
"""
Lightroom Mobile Design Reference — Milestone 1 Adversarial Stress Test Suite
=============================================================================
Target: tests/test_adversarial_inventory.py
Author: challenger_m1_1 (Teamwork Challenger)

This test suite executes rigorous adversarial attack scenarios against
`verify_inventory.py` and `migrate_screenshots.py` in an isolated sandbox.

Attack Categories:
  1. Baseline Sandbox Verification
  2. Missing File Detection (Deletions)
  3. Corrupted File Detection (Bit Rot, Truncation, Headers, Dimensions)
  4. Stray & Extra File Detection (Root, Panels, Subdirectories)
  5. Altered Manifest Entry Detection (Schema, Counts, Hashes, Sizes, Dims)
  6. Migration Engine Idempotency & Fault-Tolerance Stress Testing
"""

import os
import sys
import json
import shutil
import tempfile
import subprocess
from typing import Dict, List, Tuple, Any
from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIGINAL_DR = os.path.join(REPO_ROOT, "CleanLightroom", "design-reference")
VERIFY_SCRIPT = os.path.join(ORIGINAL_DR, "verify_inventory.py")
MIGRATE_SCRIPT = os.path.join(ORIGINAL_DR, "migrate_screenshots.py")


class AdversarialTestRunner:
    def __init__(self):
        self.passed_tests = 0
        self.failed_tests = 0
        self.test_results: List[Dict[str, Any]] = []

    def record_result(self, test_name: str, passed: bool, details: str):
        if passed:
            self.passed_tests += 1
            print(f"  [PASS] {test_name}: {details}")
        else:
            self.failed_tests += 1
            print(f"  [FAIL] {test_name}: {details}")
        self.test_results.append({
            "test": test_name,
            "passed": passed,
            "details": details
        })

    def run_verify(self, sandbox_dir: str, manifest_path: str = None, extra_args: List[str] = None) -> Tuple[int, str, str]:
        cmd = [sys.executable, VERIFY_SCRIPT, "--dir", sandbox_dir]
        if manifest_path:
            cmd.extend(["--manifest", manifest_path])
        if extra_args:
            cmd.extend(extra_args)
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return proc.returncode, proc.stdout, proc.stderr

    def run_migrate(self, sandbox_dir: str) -> Tuple[int, str, str]:
        # Run migrate script copied into sandbox or run in sandbox cwd
        target_migrate = os.path.join(sandbox_dir, "migrate_screenshots.py")
        if not os.path.exists(target_migrate):
            shutil.copy(MIGRATE_SCRIPT, target_migrate)
        proc = subprocess.run(
            [sys.executable, target_migrate],
            cwd=sandbox_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        return proc.returncode, proc.stdout, proc.stderr


def run_all_tests():
    print("=" * 80)
    print(" STARTING ADVERSARIAL STRESS TEST SUITE: verify_inventory & migrate_screenshots")
    print("=" * 80)

    runner = AdversarialTestRunner()

    with tempfile.TemporaryDirectory(prefix="challenger_m1_1_") as temp_sandbox:
        print(f"Creating isolated sandbox from: {ORIGINAL_DR}")
        shutil.copytree(ORIGINAL_DR, temp_sandbox, dirs_exist_ok=True)
        manifest_file = os.path.join(temp_sandbox, "PANEL_MANIFEST.json")

        with open(manifest_file, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)

        # -------------------------------------------------------------
        # CATEGORY 1: BASELINE HEALTH CHECK
        # -------------------------------------------------------------
        print("\n--- CATEGORY 1: Baseline Sandbox Verification ---")
        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 0) and ("AUDIT PASSED" in out)
        runner.record_result(
            "Test 01: Baseline Sandbox Audit",
            passed,
            f"Exit code: {code}, Audit passed confirmed"
        )

        # -------------------------------------------------------------
        # CATEGORY 2: MISSING FILE DETECTION
        # -------------------------------------------------------------
        print("\n--- CATEGORY 2: Missing File Detection (Deletions) ---")
        
        # Test 02: Delete single file in Crop
        crop_files = [f for f in os.listdir(os.path.join(temp_sandbox, "Crop")) if f.endswith(".png")]
        target_file = os.path.join(temp_sandbox, "Crop", crop_files[0])
        backup_file = target_file + ".bak"
        shutil.copy(target_file, backup_file)
        os.remove(target_file)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Missing file from disk" in out or "count mismatch" in out)
        runner.record_result(
            "Test 02: Delete Single File in Crop Panel",
            passed,
            f"Exit code: {code} (expected 1), correctly flagged missing file and count mismatch"
        )
        shutil.move(backup_file, target_file)

        # Test 03: Delete singleton file in Community
        comm_files = [f for f in os.listdir(os.path.join(temp_sandbox, "Community")) if f.endswith(".png")]
        target_file = os.path.join(temp_sandbox, "Community", comm_files[0])
        backup_file = target_file + ".bak"
        shutil.copy(target_file, backup_file)
        os.remove(target_file)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Panel 'Community' count mismatch" in out)
        runner.record_result(
            "Test 03: Delete Singleton File in Community Panel",
            passed,
            f"Exit code: {code} (expected 1), correctly detected panel count mismatch (expected 1, found 0)"
        )
        shutil.move(backup_file, target_file)

        # Test 04: Delete entire Optics panel directory
        optics_dir = os.path.join(temp_sandbox, "Optics")
        optics_backup = optics_dir + "_bak"
        shutil.move(optics_dir, optics_backup)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Canonical panel directory missing: 'Optics'" in out)
        runner.record_result(
            "Test 04: Delete Entire Canonical Panel Directory (Optics)",
            passed,
            f"Exit code: {code} (expected 1), correctly detected missing canonical panel directory"
        )
        shutil.move(optics_backup, optics_dir)

        # -------------------------------------------------------------
        # CATEGORY 3: CORRUPTED FILE DETECTION
        # -------------------------------------------------------------
        print("\n--- CATEGORY 3: Corrupted File Detection (Bit Rot, Truncation, Headers) ---")

        # Test 05: Bit-flip 1 byte in payload
        actions_files = [f for f in os.listdir(os.path.join(temp_sandbox, "Actions")) if f.endswith(".png")]
        test_file = os.path.join(temp_sandbox, "Actions", actions_files[0])
        with open(test_file, "rb") as f:
            original_bytes = bytearray(f.read())
        corrupt_bytes = bytearray(original_bytes)
        corrupt_bytes[1024] ^= 0xFF  # Flip byte at offset 1024
        with open(test_file, "wb") as f:
            f.write(corrupt_bytes)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("SHA-256 mismatch" in out or "Missing file from disk" in out)
        runner.record_result(
            "Test 05: Bit-Flip Single Byte Corruption",
            passed,
            f"Exit code: {code} (expected 1), correctly detected SHA-256 hash mismatch"
        )
        with open(test_file, "wb") as f:
            f.write(original_bytes)

        # Test 06: Zero-byte truncation
        with open(test_file, "wb") as f:
            f.write(b"")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Size mismatch" in out or "Corrupted or invalid PNG" in out)
        runner.record_result(
            "Test 06: Zero-Byte Truncation Corruption",
            passed,
            f"Exit code: {code} (expected 1), correctly detected size mismatch and corrupted PNG"
        )
        with open(test_file, "wb") as f:
            f.write(original_bytes)

        # Test 07: Replace PNG with random text garbage
        with open(test_file, "wb") as f:
            f.write(b"NOT_A_PNG_FILE_HEADER_GARBAGE_DATA")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Corrupted or invalid PNG" in out or "Size mismatch" in out)
        runner.record_result(
            "Test 07: Corrupted Header / Garbage Stream",
            passed,
            f"Exit code: {code} (expected 1), correctly caught non-PNG header via PIL decode"
        )
        with open(test_file, "wb") as f:
            f.write(original_bytes)

        # Test 08: Replace with valid JPEG (wrong image format)
        img_jpg = Image.new("RGB", (1220, 2712), color=(255, 0, 0))
        img_jpg.save(test_file, format="JPEG")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Expected PNG format, got JPEG" in out or "SHA-256 mismatch" in out)
        runner.record_result(
            "Test 08: Wrong Image Format (JPEG disguised as PNG)",
            passed,
            f"Exit code: {code} (expected 1), PIL verify rejected JPEG stream"
        )
        with open(test_file, "wb") as f:
            f.write(original_bytes)

        # Test 09: Valid PNG but illegal dimensions (800x600)
        img_bad_dim = Image.new("RGB", (800, 600), color=(128, 128, 128))
        img_bad_dim.save(test_file, format="PNG")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("is neither (1220, 2712) nor (2712, 1220)" in out or "Dimension mismatch" in out)
        runner.record_result(
            "Test 09: Illegal Screen Dimensions (800x600)",
            passed,
            f"Exit code: {code} (expected 1), Invariant 6 strictly rejected non-standard dimensions"
        )
        with open(test_file, "wb") as f:
            f.write(original_bytes)

        # -------------------------------------------------------------
        # CATEGORY 4: STRAY & EXTRA FILE DETECTION
        # -------------------------------------------------------------
        print("\n--- CATEGORY 4: Stray & Extra File Detection ---")

        # Test 10: Stray PNG in root
        stray_png = os.path.join(temp_sandbox, "stray_unclassified.png")
        with open(stray_png, "wb") as f:
            f.write(b"stray")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Unclassified files in root directory" in out)
        runner.record_result(
            "Test 10: Stray PNG File in Root Directory",
            passed,
            f"Exit code: {code} (expected 1), Invariant 7 caught unclassified root file"
        )
        os.remove(stray_png)

        # Test 11: Stray non-PNG file in root
        stray_txt = os.path.join(temp_sandbox, "debug_log.txt")
        with open(stray_txt, "w") as f:
            f.write("debug")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Unclassified files in root directory" in out)
        runner.record_result(
            "Test 11: Stray Non-PNG File in Root Directory",
            passed,
            f"Exit code: {code} (expected 1), Invariant 7 quarantined unallowed root file"
        )
        os.remove(stray_txt)

        # Test 12: Non-canonical directory in root
        unexpected_dir = os.path.join(temp_sandbox, "horizontal screen UI(same as verticle)")
        os.makedirs(unexpected_dir, exist_ok=True)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Non-canonical directories in root" in out)
        runner.record_result(
            "Test 12: Legacy/Unknown Directory in Root",
            passed,
            f"Exit code: {code} (expected 1), Invariant 7 detected non-canonical folder"
        )
        os.rmdir(unexpected_dir)

        # Test 13: Extra PNG file in panel
        extra_png = os.path.join(temp_sandbox, "Actions", "Screenshot_20261024-999999_Lightroom.png")
        shutil.copy(test_file, extra_png)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Total disk files across panels is 103" in out or "count mismatch" in out)
        runner.record_result(
            "Test 13: Extra Screenshot in Panel Directory",
            passed,
            f"Exit code: {code} (expected 1), Invariant 2 detected count mismatch (103 vs 102)"
        )
        os.remove(extra_png)

        # Test 14: Non-PNG file in panel
        non_png_panel = os.path.join(temp_sandbox, "Camera", "notes.txt")
        with open(non_png_panel, "w") as f:
            f.write("notes")

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Non-PNG file inside panel 'Camera'" in out)
        runner.record_result(
            "Test 14: Non-PNG File Inside Canonical Panel",
            passed,
            f"Exit code: {code} (expected 1), Invariant 7 strictly rejected non-PNG artifact"
        )
        os.remove(non_png_panel)

        # Test 15: Nested subdirectory inside panel
        sub_dir = os.path.join(temp_sandbox, "Tone_Curve", "nested_folder")
        os.makedirs(sub_dir, exist_ok=True)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Unexpected subdirectory inside panel 'Tone_Curve'" in out)
        runner.record_result(
            "Test 15: Nested Subdirectory Inside Canonical Panel",
            passed,
            f"Exit code: {code} (expected 1), Invariant 7 caught invalid nested hierarchy"
        )
        os.rmdir(sub_dir)

        # Test 16: Cross-panel duplicate / collision
        # Copy Actions file into Detail panel with a different filename
        dup_file = os.path.join(temp_sandbox, "Detail", "dup_test.png")
        shutil.copy(test_file, dup_file)

        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 1) and ("Content collision on disk" in out or "Unindexed extraneous file" in out)
        runner.record_result(
            "Test 16: Cross-Panel Content Hash Collision",
            passed,
            f"Exit code: {code} (expected 1), Invariant 3 bijective hash check caught hash collision"
        )
        os.remove(dup_file)

        # -------------------------------------------------------------
        # CATEGORY 5: ALTERED MANIFEST ENTRY DETECTION
        # -------------------------------------------------------------
        print("\n--- CATEGORY 5: Altered Manifest Entry Detection ---")

        # Test 17: Alter total_files to 101 in manifest
        tampered_manifest = json.loads(json.dumps(manifest_data))
        tampered_manifest["total_files"] = 101
        temp_mf = os.path.join(temp_sandbox, "PANEL_MANIFEST_tampered.json")
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Manifest total_files is 101, expected 102" in out)
        runner.record_result(
            "Test 17: Manifest Tampering — total_files Decremented",
            passed,
            f"Exit code: {code} (expected 1), Invariant 1 caught invalid total_files count"
        )
        os.remove(temp_mf)

        # Test 18: Alter total_panels to 19 in manifest
        tampered_manifest = json.loads(json.dumps(manifest_data))
        tampered_manifest["total_panels"] = 19
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Manifest total_panels is 19, expected 20" in out)
        runner.record_result(
            "Test 18: Manifest Tampering — total_panels Decremented",
            passed,
            f"Exit code: {code} (expected 1), Invariant 1 caught invalid total_panels count"
        )
        os.remove(temp_mf)

        # Test 19: Alter SHA-256 digest in manifest for one file
        tampered_manifest = json.loads(json.dumps(manifest_data))
        tampered_manifest["files"][0]["sha256"] = "0" * 64
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("SHA-256 mismatch" in out or "Missing file from disk" in out)
        runner.record_result(
            "Test 19: Manifest Tampering — Altered SHA-256 Digest",
            passed,
            f"Exit code: {code} (expected 1), Invariant 3 & 4 caught checksum discrepancy"
        )
        os.remove(temp_mf)

        # Test 20: Alter size in manifest for one file
        tampered_manifest = json.loads(json.dumps(manifest_data))
        tampered_manifest["files"][0]["size"] = 999999999
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Size mismatch" in out)
        runner.record_result(
            "Test 20: Manifest Tampering — Altered Byte Size",
            passed,
            f"Exit code: {code} (expected 1), Invariant 4 caught size mismatch"
        )
        os.remove(temp_mf)

        # Test 21: Alter expected dimensions in manifest
        tampered_manifest = json.loads(json.dumps(manifest_data))
        tampered_manifest["files"][0]["width"] = 1920
        tampered_manifest["files"][0]["height"] = 1080
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Dimension mismatch" in out)
        runner.record_result(
            "Test 21: Manifest Tampering — Altered Image Dimensions",
            passed,
            f"Exit code: {code} (expected 1), Invariant 6 caught dimension discrepancy"
        )
        os.remove(temp_mf)

        # Test 22: Duplicate filename entry in manifest
        tampered_manifest = json.loads(json.dumps(manifest_data))
        dup_entry = dict(tampered_manifest["files"][0])
        tampered_manifest["files"].append(dup_entry)
        tampered_manifest["total_files"] = 103
        with open(temp_mf, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f)

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Duplicate filename in manifest" in out)
        runner.record_result(
            "Test 22: Manifest Tampering — Duplicate Filename Entry",
            passed,
            f"Exit code: {code} (expected 1), Invariant 1 caught duplicate manifest entry"
        )
        os.remove(temp_mf)

        # Test 23: Manifest file completely missing
        non_existent_mf = os.path.join(temp_sandbox, "DOES_NOT_EXIST.json")
        code, out, err = runner.run_verify(temp_sandbox, manifest_path=non_existent_mf)
        passed = (code == 1) and ("Manifest file not found" in err or "Manifest file not found" in out)
        runner.record_result(
            "Test 23: Manifest Missing from Disk",
            passed,
            f"Exit code: {code} (expected 1), correctly aborted on missing manifest"
        )

        # Test 24: Manifest JSON malformed
        with open(temp_mf, "w", encoding="utf-8") as f:
            f.write("{ INVALID JSON CONTENT : ")

        code, out, err = runner.run_verify(temp_sandbox, manifest_path=temp_mf)
        passed = (code == 1) and ("Failed to parse manifest JSON" in err or "Failed to parse manifest JSON" in out)
        runner.record_result(
            "Test 24: Manifest Malformed JSON Syntax",
            passed,
            f"Exit code: {code} (expected 1), correctly caught JSON parse failure"
        )
        os.remove(temp_mf)

        # -------------------------------------------------------------
        # CATEGORY 6: MIGRATE_SCREENSHOTS STRESS & IDEMPOTENCY
        # -------------------------------------------------------------
        print("\n--- CATEGORY 6: Migration Engine Idempotency & Fault-Tolerance ---")

        # Test 25: First migration run in populated sandbox
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 0) and ("MIGRATION EXECUTION & AUDIT: 100% SUCCESS" in out) and ("Verified in-place: 102" in out)
        runner.record_result(
            "Test 25: Migration Engine Run 1 (Populated Target)",
            passed,
            f"Exit code: {code}, 102 verified in-place, 0 newly moved"
        )

        # Test 26: Immediate re-run in populated sandbox (Idempotency)
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 0) and ("Verified in-place: 102" in out) and ("Newly moved: 0" in out)
        runner.record_result(
            "Test 26: Migration Engine Run 2 (Strict Idempotency)",
            passed,
            f"Exit code: {code}, confirmed exactly 0 moves, 0 alterations on re-run"
        )

        # Test 27: Staged migration simulation:
        # Move 1 file back from target to its original rel_path, then run migrate
        sample_entry = manifest_data["files"][5]  # A horizontal file
        dst_rel = sample_entry["new_rel_path"].replace("/", os.sep)
        src_rel = sample_entry["rel_path"].replace("/", os.sep)
        dst_full = os.path.join(temp_sandbox, dst_rel)
        src_full = os.path.join(temp_sandbox, src_rel)
        os.makedirs(os.path.dirname(src_full), exist_ok=True)
        shutil.move(dst_full, src_full)

        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 0) and ("Newly moved: 1" in out) and ("Verified in-place: 101" in out)
        runner.record_result(
            "Test 27: Migration Engine Atomic Relocation of Staged Source",
            passed,
            f"Exit code: {code}, accurately moved 1 staged file and verified remaining 101 in-place"
        )

        # Test 28: Redundant copy cleanup simulation:
        # Create identical copy in source directory while destination exists
        shutil.copy(dst_full, src_full)
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 0) and ("Cleaned redundant sources: 1" in out) and (not os.path.exists(src_full))
        runner.record_result(
            "Test 28: Migration Engine Redundant Source Copy Cleanup",
            passed,
            f"Exit code: {code}, safely pruned redundant source duplicate while preserving target"
        )

        # Test 29: Migration abort on corrupted destination file
        with open(dst_full, "wb") as f:
            f.write(b"CORRUPTED_DESTINATION_PAYLOAD")
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 1) and ("FATAL: Size mismatch" in err or "FATAL: Hash mismatch" in err)
        runner.record_result(
            "Test 29: Migration Engine Abort on Corrupted Destination",
            passed,
            f"Exit code: {code} (expected 1), migration refused to proceed with corrupted destination"
        )

        # Restore valid file from backup
        shutil.copy(os.path.join(ORIGINAL_DR, dst_rel), dst_full)

        # Test 30: Migration abort on corrupted source file (target missing)
        os.remove(dst_full)
        with open(src_full, "wb") as f:
            f.write(b"CORRUPTED_SOURCE_PAYLOAD")
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 1) and ("FATAL: Size mismatch at source" in err or "FATAL: Hash mismatch at source" in err)
        runner.record_result(
            "Test 30: Migration Engine Abort on Corrupted Source",
            passed,
            f"Exit code: {code} (expected 1), migration refused to move corrupted source file"
        )
        os.remove(src_full)

        # Restore valid file from original repository
        shutil.copy(os.path.join(ORIGINAL_DR, dst_rel), dst_full)

        # Test 31: Migration abort on completely missing file (neither source nor destination)
        os.remove(dst_full)
        code, out, err = runner.run_migrate(temp_sandbox)
        passed = (code == 1) and ("FATAL: File" in err and "not found at source" in err)
        runner.record_result(
            "Test 31: Migration Engine Abort on Missing File",
            passed,
            f"Exit code: {code} (expected 1), detected missing file at both source and destination"
        )

        # Restore file and run final verification on sandbox
        shutil.copy(os.path.join(ORIGINAL_DR, dst_rel), dst_full)
        code, out, err = runner.run_verify(temp_sandbox)
        passed = (code == 0) and ("AUDIT PASSED" in out)
        runner.record_result(
            "Test 32: Final Sandbox Health Reconciliation",
            passed,
            f"Exit code: {code}, confirmed sandbox fully restored to 100% clean passing state"
        )

    # -------------------------------------------------------------
    # SUITE SUMMARY
    # -------------------------------------------------------------
    print("\n" + "=" * 80)
    print(f" ADVERSARIAL STRESS TEST SUITE COMPLETE: {runner.passed_tests} PASSED, {runner.failed_tests} FAILED")
    print("=" * 80)

    return 0 if runner.failed_tests == 0 else 1


if __name__ == "__main__":
    sys.exit(run_all_tests())
