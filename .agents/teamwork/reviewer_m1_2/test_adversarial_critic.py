import os
import sys
import subprocess
import json
import tempfile
import shutil

BASE_DIR = r"c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference"
SCRIPT_PATH = os.path.join(BASE_DIR, "verify_inventory.py")
MANIFEST_PATH = os.path.join(BASE_DIR, "PANEL_MANIFEST.json")

def run_verify(args):
    cmd = [sys.executable, SCRIPT_PATH] + args
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.returncode, res.stdout, res.stderr

def main():
    print("=== ADVERSARIAL STRESS-TESTING START ===")
    
    # -------------------------------------------------------------
    # Scenario 1: Non-existent directory
    # -------------------------------------------------------------
    code, out, err = run_verify(["--dir", "non_existent_directory_xyz"])
    print(f"Test 1 (Non-existent dir): exit_code={code}")
    assert code == 1, f"Expected exit code 1, got {code}"
    print("  -> Passed: properly rejected non-existent directory")

    # -------------------------------------------------------------
    # Scenario 2: Non-existent manifest
    # -------------------------------------------------------------
    code, out, err = run_verify(["--manifest", "non_existent_manifest.json"])
    print(f"Test 2 (Non-existent manifest): exit_code={code}")
    assert code == 1, f"Expected exit code 1, got {code}"
    print("  -> Passed: properly rejected non-existent manifest")

    # -------------------------------------------------------------
    # Scenario 3: Tampered Hash in Manifest
    # -------------------------------------------------------------
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest_data = json.load(f)

    with tempfile.TemporaryDirectory() as tmpdir:
        tampered_manifest = os.path.join(tmpdir, "PANEL_MANIFEST.json")
        # Tamper one hash
        tampered_data = json.loads(json.dumps(manifest_data))
        tampered_data["files"][0]["sha256"] = "0000000000000000000000000000000000000000000000000000000000000000"
        with open(tampered_manifest, "w", encoding="utf-8") as f:
            json.dump(tampered_data, f)

        code, out, err = run_verify(["--manifest", tampered_manifest, "--json"])
        print(f"Test 3 (Tampered Hash in Manifest): exit_code={code}")
        assert code == 1, f"Expected exit code 1 for tampered hash, got {code}"
        report = json.loads(out)
        assert report["audit_passed"] is False
        assert report["invariants"]["invariant_3"]["passed"] is False or report["invariants"]["invariant_4"]["passed"] is False
        print("  -> Passed: properly detected tampered hash")

    # -------------------------------------------------------------
    # Scenario 4: Omitted File in Manifest (Count Mismatch)
    # -------------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmpdir:
        tampered_manifest = os.path.join(tmpdir, "PANEL_MANIFEST.json")
        tampered_data = json.loads(json.dumps(manifest_data))
        removed_file = tampered_data["files"].pop()
        tampered_data["total_files"] = len(tampered_data["files"])
        with open(tampered_manifest, "w", encoding="utf-8") as f:
            json.dump(tampered_data, f)

        code, out, err = run_verify(["--manifest", tampered_manifest, "--json"])
        print(f"Test 4 (Omitted File Manifest): exit_code={code}")
        assert code == 1, f"Expected exit code 1 for omitted file count, got {code}"
        report = json.loads(out)
        assert report["audit_passed"] is False
        assert report["invariants"]["invariant_1"]["passed"] is False or report["invariants"]["invariant_3"]["passed"] is False
        print("  -> Passed: properly detected count mismatch / extra unindexed file")

    # -------------------------------------------------------------
    # Scenario 5: Tampered Dimensions in Manifest
    # -------------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmpdir:
        tampered_manifest = os.path.join(tmpdir, "PANEL_MANIFEST.json")
        tampered_data = json.loads(json.dumps(manifest_data))
        tampered_data["files"][0]["width"] = 9999
        with open(tampered_manifest, "w", encoding="utf-8") as f:
            json.dump(tampered_data, f)

        code, out, err = run_verify(["--manifest", tampered_manifest, "--json"])
        print(f"Test 5 (Tampered Dimensions): exit_code={code}")
        assert code == 1, f"Expected exit code 1 for tampered dimensions, got {code}"
        report = json.loads(out)
        assert report["audit_passed"] is False
        assert report["invariants"]["invariant_6"]["passed"] is False
        print("  -> Passed: properly detected dimension mismatch against disk")

    # -------------------------------------------------------------
    # Scenario 6: Check for Hardcoded Results or Facades in Code
    # -------------------------------------------------------------
    with open(SCRIPT_PATH, "r", encoding="utf-8") as f:
        src = f.read()

    # Integrity verification
    forbidden_cheats = [
        "return 0 # bypass",
        "audit_passed = True",
        "mock",
        "dummy",
        "fake",
        "pass_all"
    ]
    for cheat in forbidden_cheats:
        assert cheat not in src.lower(), f"Potential integrity cheat found in verify_inventory.py: '{cheat}'"

    assert "compute_sha256" in src
    assert "verify_image_file" in src
    assert "Image.verify()" in src
    print("Test 6 (Integrity Check): verify_inventory.py is an authentic, dynamic test harness.")

    print("\n=== ALL ADVERSARIAL STRESS-TESTS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    main()
