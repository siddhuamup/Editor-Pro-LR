import tempfile
import subprocess
from pathlib import Path

with tempfile.TemporaryDirectory() as tmpdir:
    tmp_path = Path(tmpdir)
    
    # 1. Test empty file
    f1 = tmp_path / "empty.svg"
    f1.write_text("", encoding="utf-8")
    
    # 2. Test malformed XML
    f2 = tmp_path / "bad_xml.svg"
    f2.write_text("<svg><path>", encoding="utf-8")
    
    # 3. Test invalid paint
    f3 = tmp_path / "bad_paint.svg"
    f3.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M0,0L1,1" fill="2130970235"/></svg>', encoding="utf-8")
    
    res = subprocess.run(["python", "verify_mined_svgs.py", "--dir", str(tmp_path)], capture_output=True, text=True)
    print("Return code:", res.returncode)
    print("STDOUT:\n", res.stdout)
    assert res.returncode == 1, "Test harness MUST fail on invalid files"
    assert "Gate 1 FAIL" in res.stdout, "Gate 1 must detect empty file"
    assert "Gate 2 FAIL" in res.stdout, "Gate 2 must detect malformed XML"
    assert "Gate 4 FAIL" in res.stdout, "Gate 4 must detect invalid paint"
    assert "Gate 5 (Batch Count >= 1000)     : FAILED" in res.stdout, "Gate 5 must detect count < 1000"
    print("ALL HARNESS SENSITIVITY ASSERTIONS PASSED!")
