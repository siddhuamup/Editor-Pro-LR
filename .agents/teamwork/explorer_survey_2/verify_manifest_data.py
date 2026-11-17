import json
import os

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

print(f"Total entries: {len(manifest['files'])}")

# Check each panel
panels = sorted(list(set(f['panel'] for f in manifest['files'])))
print("Panels:", panels)

for p in panels:
    files_in_p = [f for f in manifest['files'] if f['panel'] == p]
    print(f"\n--- Panel: {p} ({len(files_in_p)} files) ---")
    for f in files_in_p:
        print(f"  {f['filename']} | {f['source_dir']} | {f['orientation']} | {f['description']}")
