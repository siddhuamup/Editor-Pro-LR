import os
import json

base_dir = r'C:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference'
manifest_path = os.path.join(base_dir, 'PANEL_MANIFEST.json')

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

# Sort files: first vertical (alphabetical), then horizontal (alphabetical)
v_files = sorted([f for f in manifest['files'] if f['source_dir'] == 'vertical'], key=lambda x: x['filename'])
h_files = sorted([f for f in manifest['files'] if f['source_dir'] == 'horizontal'], key=lambda x: x['filename'])
all_files = v_files + h_files

# Define rich control catalog and correspondence mapping for each file
def get_file_metadata(f):
    fn = f['filename']
    panel = f['panel']
    dim = f"{f['width']}x{f['height']}"
    ori = f['orientation']
    src = f['source_dir']
    sha = f['sha256']
    
    # Specific UI controls & correspondences
    # We will build rich, verbatim observations for every file
    return fn, panel, dim, ori, src, sha

print(f"Loaded {len(all_files)} files.")
