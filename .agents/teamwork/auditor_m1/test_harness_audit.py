import subprocess, shutil
from pathlib import Path

tmp_dir = Path("temp_test_gates_audit")
if tmp_dir.exists():
    shutil.rmtree(tmp_dir)
tmp_dir.mkdir()

results = {}

try:
    # Test 1: Empty file
    f1 = tmp_dir / "empty.svg"
    f1.write_bytes(b"")
    res1 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 1 (empty file)"] = (res1.returncode == 1) and ("Gate 1 FAIL" in res1.stdout)
    f1.unlink()

    # Test 2: Malformed XML
    f2 = tmp_dir / "malformed.svg"
    f2.write_text("<svg><path></svg>", encoding="utf-8")
    res2 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 2 (malformed XML)"] = (res2.returncode == 1) and ("Gate 2 FAIL" in res2.stdout)
    f2.unlink()

    # Test 3: Wrong root tag
    f3 = tmp_dir / "wrong_root.svg"
    f3.write_text('<vector viewBox="0 0 24 24"><path d="M0 0"/></vector>', encoding="utf-8")
    res3 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 3 (wrong root tag)"] = (res3.returncode == 1) and ("Gate 3 FAIL" in res3.stdout)
    f3.unlink()

    # Test 4: Missing viewBox and width/height
    f4 = tmp_dir / "no_coords.svg"
    f4.write_text('<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0"/></svg>', encoding="utf-8")
    res4 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 3 (missing viewBox and dims)"] = (res4.returncode == 1) and ("missing both viewBox and width/height" in res4.stdout)
    f4.unlink()

    # Test 5: No graphical elements
    f5 = tmp_dir / "no_graphics.svg"
    f5.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><desc>hello</desc></svg>', encoding="utf-8")
    res5 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 4 (no graphics)"] = (res5.returncode == 1) and ("Gate 4 FAIL" in res5.stdout)
    f5.unlink()

    # Test 6: Empty pathData
    f6 = tmp_dir / "empty_path.svg"
    f6.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d=""/></svg>', encoding="utf-8")
    res6 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 4 (empty pathData)"] = (res6.returncode == 1) and ("has empty 'd' attribute" in res6.stdout)
    f6.unlink()

    # Test 7: Batch count < 1000
    f7 = tmp_dir / "valid.svg"
    f7.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0 L10 10"/></svg>', encoding="utf-8")
    res7 = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_dir)], capture_output=True, text=True)
    results["Gate 5 (batch count < 1000)"] = (res7.returncode == 1) and ("Gate 5 (Batch Count >= 1000)     : FAILED" in res7.stdout)
    f7.unlink()

finally:
    if tmp_dir.exists():
        shutil.rmtree(tmp_dir)

print("AUDIT FAILURE INJECTION RESULTS:")
for k, v in results.items():
    print(f"  {k}: {'PASS (Defect Detected & Rejected)' if v else 'FAIL (Defect Missed!)'}")
