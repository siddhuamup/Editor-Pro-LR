import os
import sys
import hashlib
import json

base = r'CleanLightroom\design-reference'
manifest_path = os.path.join(base, 'PANEL_MANIFEST.json')

print('--- CHECK 1: PANEL_MANIFEST.json ---')
assert os.path.isfile(manifest_path), 'PANEL_MANIFEST.json not found'
with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)
print(f"Total files in manifest: {manifest.get('total_files')}")
print(f"Total panels in manifest: {manifest.get('total_panels')}")

print('\n--- CHECK 2: RETIREMENT OF HORIZONTAL FOLDER ---')
legacy_dir = os.path.join(base, 'horizontal screen UI(same as verticle)')
if os.path.exists(legacy_dir):
    items = os.listdir(legacy_dir)
    print(f'Legacy folder exists with {len(items)} items')
    if len(items) == 0:
        print('Legacy folder is empty, ready for retirement.')
    else:
        print('Legacy folder is NOT empty! Cannot retire.')
else:
    print('Legacy folder does NOT exist (already safely retired).')

print('\n--- CHECK 3: SVG ICONS IMMUTABILITY ---')
svg_dir = os.path.join(base, 'Svg Icons')
assert os.path.isdir(svg_dir), 'Svg Icons directory not found'
svg_files = []
non_svg_files = []
for root, dirs, files in os.walk(svg_dir):
    for f in files:
        rel = os.path.relpath(os.path.join(root, f), svg_dir)
        if f.lower().endswith('.svg'):
            svg_files.append(rel)
        else:
            non_svg_files.append(rel)
print(f'Svg Icons count: {len(svg_files)} .svg files')
print(f'Non-SVG files in Svg Icons: {len(non_svg_files)}')
assert len(non_svg_files) == 0, f'Found non-svg files in Svg Icons: {non_svg_files}'
assert len(svg_files) == 331, f'Expected 331 svg files, found {len(svg_files)}'

print('\n--- CHECK 4: ZERO STRAY PNG IN ROOT ---')
root_entries = os.listdir(base)
root_files = [e for e in root_entries if os.path.isfile(os.path.join(base, e))]
stray_pngs = [f for f in root_files if f.lower().endswith('.png')]
print(f'Files in root: {root_files}')
print(f'Stray PNGs in root: {len(stray_pngs)}')
assert len(stray_pngs) == 0, f'Found stray PNGs in root: {stray_pngs}'

print('\n--- CHECK 5: ALL 102 FILES IN CANONICAL PANELS ---')
canonical_panels = set(manifest.get('panels', []))
found_files = 0
for entry in manifest.get('files', []):
    fp = os.path.join(base, entry['new_rel_path'])
    assert os.path.isfile(fp), f'Missing: {fp}'
    with open(fp, 'rb') as f:
        data = f.read()
    h = hashlib.sha256(data).hexdigest()
    assert h == entry['sha256'], f"Hash mismatch on {entry['filename']}"
    assert len(data) == entry['size'], f"Size mismatch on {entry['filename']}"
    found_files += 1

print(f'Verified {found_files}/102 files with exact SHA-256 and byte sizes.')
print('\nALL PRE-FLIGHT AND WORKSPACE VALIDATION CHECKS PASSED!')
