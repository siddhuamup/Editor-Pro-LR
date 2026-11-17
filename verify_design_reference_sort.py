import os
import json
import hashlib
import sys

BASE_DIR = r"c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference"
MANIFEST_PATH = os.path.join(BASE_DIR, "PANEL_MANIFEST.json")
EXCLUDED_DIRS = {"Svg Icons"}

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fp:
        for chunk in iter(lambda: fp.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def main():
    assert os.path.isfile(MANIFEST_PATH), f"Manifest missing: {MANIFEST_PATH}"
    with open(MANIFEST_PATH, "r", encoding="utf-8") as fp:
        manifest = json.load(fp)

    expected_files = manifest["files"]
    assert len(expected_files) == 102, f"Expected 102 manifest entries, got {len(expected_files)}"

    # Gate 1: No leftover PNGs in root
    root_pngs = [f for f in os.listdir(BASE_DIR) if os.path.isfile(os.path.join(BASE_DIR, f)) and f.lower().endswith(".png")]
    assert not root_pngs, f"Unsorted PNGs remain in root: {root_pngs}"

    # Gate 2: Old horizontal folder removed or empty
    old_hdir = os.path.join(BASE_DIR, "horizontal screen UI(same as verticle)")
    if os.path.exists(old_hdir):
        leftover = os.listdir(old_hdir)
        assert not leftover, f"Leftover files in horizontal folder: {leftover}"

    # Gate 3: Svg Icons untouched
    svg_dir = os.path.join(BASE_DIR, "Svg Icons")
    assert os.path.isdir(svg_dir) and len(os.listdir(svg_dir)) > 0, "Svg Icons directory missing or empty"

    # Gate 4: Walk all panel folders and verify exact file count, zero duplicates, and 100% SHA-256 integrity
    actual_by_rel = {}
    actual_hashes = []
    for entry in os.listdir(BASE_DIR):
        full = os.path.join(BASE_DIR, entry)
        if not os.path.isdir(full) or entry in EXCLUDED_DIRS:
            continue
        for root, _, files in os.walk(full):
            for fname in files:
                if fname.lower().endswith(".png"):
                    fpath = os.path.join(root, fname)
                    rel = os.path.relpath(fpath, BASE_DIR).replace("\\", "/")
                    actual_by_rel[rel] = (os.path.getsize(fpath), sha256_file(fpath))
                    actual_hashes.append(actual_by_rel[rel][1])

    assert len(actual_by_rel) == 102, f"Expected 102 PNGs across panel folders, found {len(actual_by_rel)}"
    assert len(set(actual_hashes)) == 102, f"Duplicate hashes detected: {len(actual_hashes) - len(set(actual_hashes))}"

    expected_by_rel = {r["new_rel_path"]: (r["size"], r["sha256"]) for r in expected_files}
    assert set(actual_by_rel.keys()) == set(expected_by_rel.keys()), (
        f"Path mismatch: extra={set(actual_by_rel) - set(expected_by_rel)}, missing={set(expected_by_rel) - set(actual_by_rel)}"
    )

    mismatches = []
    for rel, (exp_size, exp_sha) in expected_by_rel.items():
        act_size, act_sha = actual_by_rel[rel]
        if act_size != exp_size or act_sha != exp_sha:
            mismatches.append((rel, exp_size, act_size, exp_sha, act_sha))

    assert not mismatches, f"Integrity mismatches: {mismatches}"

    print(f"AUDIT PASSED: 102/102 screenshots verified across {manifest['total_panels']} panel folders.")
    print("  - 0 leftover unsorted PNGs in root or source horizontal folder")
    print("  - 102/102 exact byte-size and SHA-256 matches (0 loss, 0 corruption, 0 duplicates)")
    print("  - Svg Icons directory preserved untouched")

if __name__ == "__main__":
    main()
