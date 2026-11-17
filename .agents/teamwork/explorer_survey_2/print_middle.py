import json
with open(r'CleanLightroom\design-reference\PANEL_MANIFEST.json', 'r', encoding='utf-8') as f:
    manifest = json.load(f)
by_panel = {}
for item in manifest['files']:
    p = item['panel']
    if p not in by_panel:
        by_panel[p] = []
    by_panel[p].append(item)

for p in ['Detail', 'Editor_and_Export', 'Effects', 'Gallery', 'Lens_Blur']:
    print(f"=== {p} ({len(by_panel[p])} files) ===")
    for it in by_panel[p]:
        print(f"  {it['filename']} :: {it['source_dir']} :: {it['orientation']} :: {it['description']}")
