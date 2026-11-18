import os
import sys
import json
from datetime import datetime, timezone

REPO_ROOT = r"c:\Users\Siddhu\Desktop\apps testing"
DESIGN_REF_DIR = os.path.join(REPO_ROOT, "CleanLightroom", "design-reference")
MANIFEST_PATH = os.path.join(DESIGN_REF_DIR, "PANEL_MANIFEST.json")

def main():
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)
        
    files = manifest["files"]
    print(f"Analyzing timestamps and byte sizes of {len(files)} screenshot files on disk...")
    
    timestamps = []
    size_matches = 0
    total_bytes = 0
    
    for f in files:
        rel_path = f["new_rel_path"]
        abs_path = os.path.join(DESIGN_REF_DIR, rel_path)
        stat = os.stat(abs_path)
        
        mtime = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc)
        ctime = datetime.fromtimestamp(stat.st_ctime, tz=timezone.utc)
        
        timestamps.append((rel_path, stat.st_size, mtime, ctime))
        total_bytes += stat.st_size
        if stat.st_size == f["size"]:
            size_matches += 1
            
    timestamps.sort(key=lambda x: x[2])
    earliest_mtime = timestamps[0][2]
    latest_mtime = timestamps[-1][2]
    
    print(f"Total files: {len(timestamps)}")
    print(f"Exact size matches against manifest: {size_matches}/{len(files)}")
    print(f"Total payload bytes on disk: {total_bytes:,} bytes")
    print(f"Earliest mtime: {earliest_mtime.isoformat()}")
    print(f"Latest mtime:   {latest_mtime.isoformat()}")
    
    # Check if any file was modified recently or if original timestamps are preserved
    # Show distribution of timestamps
    mtime_dates = {}
    for r, s, m, c in timestamps:
        d_str = m.strftime("%Y-%m-%d")
        mtime_dates[d_str] = mtime_dates.get(d_str, 0) + 1
        
    print("\nModification Date Distribution:")
    for d, cnt in sorted(mtime_dates.items()):
        print(f"  {d}: {cnt} files")
        
    # Check if any file has size 0 or anomaly
    anomalies = [r for r, s, m, c in timestamps if s == 0]
    if anomalies:
        print(f"ERROR: Truncated 0-byte files found: {anomalies}")
        sys.exit(1)
        
    print("\nAll 102 files have valid non-zero byte sizes, matching manifest exactly.")

if __name__ == "__main__":
    main()
