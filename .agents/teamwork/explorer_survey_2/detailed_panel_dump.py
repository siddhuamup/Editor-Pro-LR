import json
import os

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

# Build a dictionary of items by filename
items = {item['filename']: item for item in manifest['files']}

# Group by panel
by_panel = {}
for item in manifest['files']:
    p = item['panel']
    if p not in by_panel:
        by_panel[p] = []
    by_panel[p].append(item)

print(f"Total panels: {len(by_panel)}")
for p, list_items in sorted(by_panel.items()):
    print(f"\n### {p} ({len(list_items)} files)")
    for it in list_items:
        print(f"- **{it['filename']}** | {it['source_dir']} | {it['orientation']} ({it['width']}x{it['height']}) | {it['description']}")
