import json
import os

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

panels = {}
for item in manifest['files']:
    p = item['panel']
    if p not in panels:
        panels[p] = []
    panels[p].append(item)

for p, items in sorted(panels.items()):
    v_items = [it for it in items if it['source_dir'] == 'vertical']
    h_items = [it for it in items if it['source_dir'] == 'horizontal']
    print(f"=== Panel: {p} (Total: {len(items)}, Vertical: {len(v_items)}, Horizontal: {len(h_items)}) ===")
    for it in items:
        src = it['source_dir'][:4].upper()
        fn = it['filename']
        dim = f"{it['width']}x{it['height']}"
        desc = it['description']
        print(f"  [{src}] {fn} ({dim}) -> {desc}")
