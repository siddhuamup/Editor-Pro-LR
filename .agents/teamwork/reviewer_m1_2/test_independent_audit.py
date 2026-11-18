import os
import sys
import json
import hashlib
from PIL import Image

BASE_DIR = r"c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference"
MANIFEST_PATH = os.path.join(BASE_DIR, "PANEL_MANIFEST.json")

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    print("=== INDEPENDENT AUDIT START ===")
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    manifest_files = manifest["files"]
    print(f"Total manifest files: {len(manifest_files)}")
    assert len(manifest_files) == 102, f"Expected 102 manifest files, got {len(manifest_files)}"

    # Check 11 portrait screenshots from horizontal folder
    portrait_in_horizontal = [
        f for f in manifest_files
        if f.get("source_dir") == "horizontal" and f.get("orientation") == "portrait"
    ]
    print(f"\nPortrait in horizontal count: {len(portrait_in_horizontal)}")
    assert len(portrait_in_horizontal) == 11, f"Expected 11 portrait in horizontal, got {len(portrait_in_horizontal)}"

    print("\nThe 11 Portrait Screenshots from Horizontal Source:")
    panel_dist = {}
    for f in portrait_in_horizontal:
        p = f['panel']
        panel_dist[p] = panel_dist.get(p, 0) + 1
        print(f"  - {f['filename']} -> Panel: {p:<20} Dims: {f['width']}x{f['height']} Desc: {f.get('description', '')}")

    print(f"\nDistribution of the 11 portrait screenshots across panels: {panel_dist}")

    # Cryptographic & Decodability Verification
    disk_files_seen = {}
    corrupted_images = []
    dimension_mismatches = []
    hash_mismatches = []
    size_mismatches = []

    for item in manifest_files:
        fn = item["filename"]
        panel = item["panel"]
        expected_rel = item["new_rel_path"].replace("\\", "/")
        full_path = os.path.join(BASE_DIR, expected_rel)

        assert os.path.isfile(full_path), f"File missing from disk: {full_path}"
        actual_size = os.path.getsize(full_path)
        actual_hash = sha256_file(full_path)

        if actual_size != item["size"]:
            size_mismatches.append((fn, item["size"], actual_size))
        if actual_hash != item["sha256"]:
            hash_mismatches.append((fn, item["sha256"], actual_hash))

        # Decode image
        try:
            with Image.open(full_path) as img:
                if img.format != "PNG":
                    corrupted_images.append((fn, f"Format {img.format} != PNG"))
                img.verify()
                w, h = img.size
                if (w, h) != (item["width"], item["height"]):
                    dimension_mismatches.append((fn, (item["width"], item["height"]), (w, h)))
                if (w, h) not in [(1220, 2712), (2712, 1220)]:
                    dimension_mismatches.append((fn, "Invalid standard dims", (w, h)))
        except Exception as e:
            corrupted_images.append((fn, str(e)))

        disk_files_seen[fn] = full_path

    print(f"\nVerified {len(disk_files_seen)} files from manifest.")
    print(f"Corrupted images: {len(corrupted_images)}")
    print(f"Dimension mismatches: {len(dimension_mismatches)}")
    print(f"Hash mismatches: {len(hash_mismatches)}")
    print(f"Size mismatches: {len(size_mismatches)}")

    assert len(corrupted_images) == 0
    assert len(dimension_mismatches) == 0
    assert len(hash_mismatches) == 0
    assert len(size_mismatches) == 0

    # Scan disk directly to verify no extra/unaccounted files
    disk_total = 0
    panels_found = []
    for entry in os.listdir(BASE_DIR):
        p_path = os.path.join(BASE_DIR, entry)
        if os.path.isdir(p_path) and entry not in ["Svg Icons", "__pycache__", ".git"]:
            panels_found.append(entry)
            for sub in os.listdir(p_path):
                sub_path = os.path.join(p_path, sub)
                if os.path.isfile(sub_path):
                    assert sub in disk_files_seen, f"Stray or unindexed file found on disk: {sub_path}"
                    disk_total += 1

    print(f"Total disk screenshot files across panels: {disk_total}")
    assert disk_total == 102, f"Expected 102 files on disk, found {disk_total}"
    assert len(panels_found) == 20, f"Expected 20 panels, found {len(panels_found)}"

    print("\n=== ALL INDEPENDENT CHECKS PASSED WITH 100% PARITY ===")

if __name__ == "__main__":
    main()
