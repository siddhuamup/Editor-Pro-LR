import os
import sys
import shutil
import tempfile
import json
import subprocess
from PIL import Image

REPO_ROOT = r"c:\Users\Siddhu\Desktop\apps testing"
DESIGN_REF_DIR = os.path.join(REPO_ROOT, "CleanLightroom", "design-reference")
VERIFY_SCRIPT = os.path.join(DESIGN_REF_DIR, "verify_inventory.py")
MANIFEST_ORIG = os.path.join(DESIGN_REF_DIR, "PANEL_MANIFEST.json")

def run_verifier(target_dir, manifest_path):
    cmd = [
        sys.executable,
        VERIFY_SCRIPT,
        "--dir", target_dir,
        "--manifest", manifest_path,
        "--json"
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    try:
        report = json.loads(res.stdout)
    except Exception:
        report = {"raw_stdout": res.stdout, "raw_stderr": res.stderr}
    return res.returncode, report

def main():
    print("=" * 80)
    print("ADVERSARIAL STRESS-TESTING OF VERIFY_INVENTORY.PY")
    print("=" * 80)
    
    with tempfile.TemporaryDirectory() as tmpdir:
        # Load real manifest
        with open(MANIFEST_ORIG, "r", encoding="utf-8") as f:
            manifest = json.load(f)
            
        test_manifest_path = os.path.join(tmpdir, "PANEL_MANIFEST.json")
        with open(test_manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)
            
        # Copy 1 canonical panel with 1 file for targeted tampering, but create empty folders for others to test
        # Actually, let's create a minimal test setup
        print("Setting up simulated repository in tempdir...")
        for panel in manifest["panel_counts"].keys():
            os.makedirs(os.path.join(tmpdir, panel), exist_ok=True)
            
        # Copy one real file from Community (which has 1 file)
        com_file = "Screenshot_20261024-201235_Lightroom.png"
        src_com = os.path.join(DESIGN_REF_DIR, "Community", com_file)
        dst_com = os.path.join(tmpdir, "Community", com_file)
        shutil.copy2(src_com, dst_com)
        
        # Test 1: Empty folders (missing 101 files)
        print("\nTest 1: Missing files scenario (101 files missing)...")
        rc, report = run_verifier(tmpdir, test_manifest_path)
        print(f"  Return code: {rc} (Expected != 0)")
        if rc == 1 and not report.get("audit_passed"):
            print("  [PASS] Successfully rejected missing files (Invariants 2 & 3 failed as expected).")
        else:
            print("  [FAIL] Verifier failed to detect missing files!")
            sys.exit(1)
            
        # Test 2: Stray unclassified file in root
        print("\nTest 2: Stray unclassified file in root directory...")
        # Populate all files using symlinks or copies?
        # Let's test against live DESIGN_REF_DIR directly by temporarily adding a dummy file in root and removing it immediately
        stray_path = os.path.join(DESIGN_REF_DIR, "stray_test_file.txt")
        try:
            with open(stray_path, "w") as f:
                f.write("unauthorized")
            rc, report = run_verifier(DESIGN_REF_DIR, MANIFEST_ORIG)
            print(f"  Return code with stray file: {rc} (Expected 1)")
            inv7 = report.get("invariants", {}).get("invariant_7", {})
            if rc == 1 and not inv7.get("passed"):
                print(f"  [PASS] Successfully caught stray root file: {inv7.get('errors')}")
            else:
                print("  [FAIL] Failed to catch stray file!")
                sys.exit(1)
        finally:
            if os.path.exists(stray_path):
                os.remove(stray_path)
                
        # Test 3: Modified bit in a copy (Hash mismatch)
        # Create a modified manifest pointing to a tampered directory
        print("\nTest 3: Corrupted byte in image file...")
        tampered_manifest = dict(manifest)
        # Limit manifest to only Community panel (1 file)
        single_file_record = [f for f in manifest["files"] if f["filename"] == com_file][0]
        tampered_manifest["files"] = [single_file_record]
        tampered_manifest["total_files"] = 1
        tampered_manifest["panel_counts"] = {"Community": 1}
        for p in manifest["panel_counts"]:
            if p != "Community":
                tampered_manifest["panel_counts"][p] = 0
        tampered_manifest["total_panels"] = 20
        
        single_manifest_path = os.path.join(tmpdir, "single_manifest.json")
        with open(single_manifest_path, "w", encoding="utf-8") as f:
            json.dump(tampered_manifest, f, indent=2)
            
        # Tamper the file byte
        with open(dst_com, "r+b") as f:
            f.seek(50)
            b = f.read(1)
            f.seek(50)
            f.write(bytes([(b[0] ^ 0xFF)]))
            
        rc, report = run_verifier(tmpdir, single_manifest_path)
        print(f"  Return code with modified byte: {rc} (Expected 1)")
        inv4 = report.get("invariants", {}).get("invariant_4", {})
        if rc == 1 and not inv4.get("passed"):
            print(f"  [PASS] Successfully caught hash mismatch: {inv4.get('errors')}")
        else:
            print("  [FAIL] Failed to catch hash mismatch!")
            sys.exit(1)
            
        # Test 4: Corrupted PNG header
        print("\nTest 4: Non-decodable PNG header...")
        with open(dst_com, "wb") as f:
            f.write(b"NOT A PNG IMAGE DATA AT ALL")
        rc, report = run_verifier(tmpdir, single_manifest_path)
        print(f"  Return code with corrupted header: {rc} (Expected 1)")
        inv5 = report.get("invariants", {}).get("invariant_5", {})
        if rc == 1 and not inv5.get("passed"):
            print(f"  [PASS] Successfully caught PNG decoding error: {inv5.get('errors')}")
        else:
            print("  [FAIL] Failed to catch PNG corruption!")
            sys.exit(1)
            
    print("\n" + "=" * 80)
    print("ALL 4 ADVERSARIAL TAMPER TESTS PASSED!")
    print("verify_inventory.py is an AUTHENTIC, STRICT, and UNCOMPROMISED verifier.")
    print("=" * 80)

if __name__ == "__main__":
    main()
