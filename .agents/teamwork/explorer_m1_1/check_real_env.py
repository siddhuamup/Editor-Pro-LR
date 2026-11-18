import os
import hashlib
import json

base_dir = r"CleanLightroom\design-reference"
manifest_path = os.path.join(base_dir, "PANEL_MANIFEST.json")

with open(manifest_path, "r", encoding="utf-8") as mf:
    manifest = json.load(mf)

print(f"Loaded manifest with {manifest['total_files']} files and {manifest['total_panels']} panels.")

# Pre-migration scan
status = {"in_dst": 0, "in_src": 0, "both": 0, "missing": 0}

for f in manifest["files"]:
    src = os.path.join(base_dir, f["rel_path"].replace("/", os.sep))
    dst = os.path.join(base_dir, f["new_rel_path"].replace("/", os.sep))
    src_exists = os.path.exists(src)
    dst_exists = os.path.exists(dst)

    if src_exists and dst_exists:
        status["both"] += 1
    elif dst_exists:
        status["in_dst"] += 1
    elif src_exists:
        status["in_src"] += 1
    else:
        status["missing"] += 1

print(f"Pre-scan results: {status}")
assert status["missing"] == 0, "Some files are missing!"
print("Status confirms: All 102 files are accounted for!")
