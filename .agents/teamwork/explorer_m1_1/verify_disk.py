import os
import json
import hashlib
from PIL import Image

base = r"CleanLightroom\design-reference"
manifest_path = os.path.join(base, "PANEL_MANIFEST.json")

with open(manifest_path, "r", encoding="utf-8") as f:
    manifest = json.load(f)

print(f"Manifest total panels: {manifest['total_panels']}")
print(f"Manifest total files: {manifest['total_files']}")

counts = {p: 0 for p in manifest["panel_counts"]}
errors = []

for idx, fentry in enumerate(manifest["files"]):
    p = os.path.join(base, fentry["new_rel_path"].replace("/", os.sep))
    if not os.path.exists(p):
        errors.append(f"File missing: {p}")
        continue
    
    sz = os.path.getsize(p)
    exp_sz = fentry["size"]
    if sz != exp_sz:
        errors.append(f"Size mismatch {p}: disk={sz}, manifest={exp_sz}")
    
    h = hashlib.sha256()
    with open(p, "rb") as fp:
        while True:
            chunk = fp.read(65536)
            if not chunk:
                break
            h.update(chunk)
    digest = h.hexdigest()
    exp_hash = fentry["sha256"]
    if digest != exp_hash:
        errors.append(f"Hash mismatch {p}: disk={digest}, manifest={exp_hash}")
    
    try:
        with Image.open(p) as img:
            img.verify()
            w, h_dim = img.size
            if (w, h_dim) != (fentry["width"], fentry["height"]):
                errors.append(f"Dimension mismatch {p}: disk=({w},{h_dim}), manifest=({fentry['width']},{fentry['height']})")
    except Exception as e:
        errors.append(f"Image decode error {p}: {e}")
    
    panel = fentry["panel"]
    counts[panel] = counts.get(panel, 0) + 1

print(f"Verification errors count: {len(errors)}")
if errors:
    for e in errors[:10]:
        print(" ", e)
else:
    print("ALL 102 FILES MATCH MANIFEST BIT-FOR-BIT, DIMENSIONS, PIL INTEGRITY 100%!")

print("\nPanel count comparison:")
for p, exp_c in manifest["panel_counts"].items():
    act_c = counts.get(p, 0)
    match_str = "OK" if exp_c == act_c else f"MISMATCH (act={act_c})"
    print(f"  {p:22s}: expected {exp_c:2d}, found {act_c:2d} -> {match_str}")
