import json
from collections import Counter

manifest_path = r"CleanLightroom\design-reference\PANEL_MANIFEST.json"
with open(manifest_path, "r", encoding="utf-8") as f:
    m = json.load(f)

h_files = [f for f in m["files"] if f["source_dir"] == "horizontal"]
h_portrait = [f for f in h_files if f["orientation"] == "portrait"]
h_landscape = [f for f in h_files if f["orientation"] == "landscape"]

print(f"Total horizontal source files: {len(h_files)}")
print(f"Landscape files: {len(h_landscape)}")
print(f"Portrait files: {len(h_portrait)}")

print("\nBreakdown of 11 portrait files in horizontal folder by panel:")
c = Counter(f["panel"] for f in h_portrait)
for p, count in c.items():
    print(f"  {p}: {count}")

print("\nDetail of 11 portrait files in horizontal folder:")
for f in h_portrait:
    print(f"  {f['filename']} -> panel: {f['panel']}, dim: {f['width']}x{f['height']}, desc: {f['description']}")
