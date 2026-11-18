import json
import os
import hashlib

base = r'CleanLightroom\design-reference'
with open(os.path.join(base, 'PANEL_MANIFEST.json')) as f:
    m = json.load(f)

assert m['total_files'] == 102, f"Expected 102 files, got {m['total_files']}"
assert m['total_panels'] == 20, f"Expected 20 panels, got {m['total_panels']}"
found = 0
for item in m['files']:
    p = os.path.join(base, item['new_rel_path'])
    assert os.path.isfile(p), f"Missing file: {p}"
    with open(p, 'rb') as fp:
        h = hashlib.sha256(fp.read()).hexdigest()
    assert h == item['sha256'], f"Hash mismatch on {p}"
    found += 1

print(f"Verification SUCCESS: {found}/102 files verified with 100% hash integrity across 20 panels.")
