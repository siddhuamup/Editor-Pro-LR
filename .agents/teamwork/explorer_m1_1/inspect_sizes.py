import json

manifest_path = r"CleanLightroom\design-reference\PANEL_MANIFEST.json"
with open(manifest_path, "r", encoding="utf-8") as f:
    m = json.load(f)

sizes = [f["size"] for f in m["files"]]
total_bytes = sum(sizes)
min_sz = min(sizes)
max_sz = max(sizes)
min_file = [f for f in m["files"] if f["size"] == min_sz][0]
max_file = [f for f in m["files"] if f["size"] == max_sz][0]

print(f"Total files: {len(sizes)}")
print(f"Total size: {total_bytes:,} bytes ({total_bytes / (1024*1024):.2f} MB)")
print(f"Min size: {min_sz:,} bytes ({min_file['filename']}, {min_file['panel']})")
print(f"Max size: {max_sz:,} bytes ({max_file['filename']}, {max_file['panel']})")
