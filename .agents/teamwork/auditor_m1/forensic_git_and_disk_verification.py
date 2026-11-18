import os
import sys
import subprocess
import hashlib
import json
from PIL import Image

REPO_ROOT = r"c:\Users\Siddhu\Desktop\apps testing"
DESIGN_REF_DIR = os.path.join(REPO_ROOT, "CleanLightroom", "design-reference")
MANIFEST_PATH = os.path.join(DESIGN_REF_DIR, "PANEL_MANIFEST.json")

def get_git_blobs_at_commit(commit="1dbeebc"):
    """
    Get all PNG files in CleanLightroom/design-reference at the specified commit.
    Returns dict: filename -> (git_path, blob_id)
    """
    cmd = ["git", "ls-tree", "-r", commit, "CleanLightroom/design-reference"]
    res = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    
    blobs = {}
    for line in res.stdout.strip().splitlines():
        if not line:
            continue
        parts = line.split(maxsplit=3)
        if len(parts) < 4:
            continue
        mode, obj_type, blob_id, git_path = parts
        if obj_type == "blob" and git_path.lower().endswith(".png"):
            filename = os.path.basename(git_path)
            blobs[filename] = (git_path, blob_id)
    return blobs

def get_blob_content(blob_id):
    """Retrieve raw bytes of git blob."""
    cmd = ["git", "cat-file", "-p", blob_id]
    res = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, check=True)
    return res.stdout

def compute_sha256_bytes(data: bytes) -> str:
    h = hashlib.sha256()
    h.update(data)
    return h.hexdigest()

def compute_sha256_file(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def main():
    print("=" * 80)
    print("FORENSIC GIT-TO-DISK BITSTREAM INTEGRITY AUDIT")
    print("=" * 80)
    
    # 1. Load Manifest
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    manifest_files = {item["filename"]: item for item in manifest["files"]}
    
    # 2. Get git blobs before migration (commit 1dbeebc)
    git_blobs = get_git_blobs_at_commit("1dbeebc")
    print(f"Git blobs found in design-reference at 1dbeebc: {len(git_blobs)}")
    print(f"Manifest records found: {len(manifest_files)}")
    
    if len(git_blobs) != 102:
        print(f"FAIL: Expected 102 git blobs, found {len(git_blobs)}")
        sys.exit(1)
        
    if len(manifest_files) != 102:
        print(f"FAIL: Expected 102 manifest records, found {len(manifest_files)}")
        sys.exit(1)
        
    # Check that filenames match exactly
    if set(git_blobs.keys()) != set(manifest_files.keys()):
        diff = set(git_blobs.keys()) ^ set(manifest_files.keys())
        print(f"FAIL: Filename mismatch between git and manifest: {diff}")
        sys.exit(1)
        
    # 3. Check each file on disk against git blob and manifest
    mismatches = []
    corruption_errors = []
    dimension_errors = []
    
    print("\nVerifying each of the 102 files bit-for-bit against Git blob and Disk...")
    for idx, (filename, (git_path, blob_id)) in enumerate(sorted(git_blobs.items()), 1):
        m_item = manifest_files[filename]
        disk_rel = m_item["new_rel_path"]
        disk_path = os.path.join(DESIGN_REF_DIR, disk_rel)
        
        # Check disk existence
        if not os.path.isfile(disk_path):
            mismatches.append(f"MISSING ON DISK: {disk_path}")
            continue
            
        # Get git blob bytes
        blob_bytes = get_blob_content(blob_id)
        git_size = len(blob_bytes)
        git_sha = compute_sha256_bytes(blob_bytes)
        
        # Get disk bytes
        disk_size = os.path.getsize(disk_path)
        disk_sha = compute_sha256_file(disk_path)
        
        # Manifest expectations
        man_size = m_item["size"]
        man_sha = m_item["sha256"]
        
        # Invariant: git == disk == manifest
        if git_size != disk_size or git_size != man_size:
            mismatches.append(f"SIZE MISMATCH {filename}: git={git_size}, disk={disk_size}, manifest={man_size}")
            
        if git_sha != disk_sha or git_sha != man_sha:
            mismatches.append(f"SHA256 MISMATCH {filename}:\n  git={git_sha}\n  disk={disk_sha}\n  manifest={man_sha}")
            
        # Verify PIL decodability directly on disk file
        try:
            with Image.open(disk_path) as img:
                if img.format != "PNG":
                    corruption_errors.append(f"NON-PNG FORMAT {filename}: {img.format}")
                img.verify()
            # reopen for size
            with Image.open(disk_path) as img:
                w, h = img.size
                if (w, h) not in [(1220, 2712), (2712, 1220)]:
                    dimension_errors.append(f"INVALID DIMS {filename}: ({w}, {h})")
                if (w, h) != (m_item["width"], m_item["height"]):
                    dimension_errors.append(f"DIM MISMATCH {filename}: actual=({w}, {h}), manifest=({m_item['width']}, {m_item['height']})")
        except Exception as e:
            corruption_errors.append(f"CORRUPTION {filename}: {e}")
            
    print("\n" + "=" * 80)
    print("AUDIT RESULTS SUMMARY")
    print("=" * 80)
    print(f"Total files checked: {len(git_blobs)}")
    print(f"Bitstream / size mismatches: {len(mismatches)}")
    print(f"Image decodability errors: {len(corruption_errors)}")
    print(f"Dimension errors: {len(dimension_errors)}")
    
    if mismatches:
        for m in mismatches[:10]:
            print(f"  [ERROR] {m}")
        sys.exit(1)
    if corruption_errors:
        for c in corruption_errors[:10]:
            print(f"  [ERROR] {c}")
        sys.exit(1)
    if dimension_errors:
        for d in dimension_errors[:10]:
            print(f"  [ERROR] {d}")
        sys.exit(1)
        
    print("\nSUCCESS: 100% of all 102 screenshot files on disk are BIT-FOR-BIT IDENTICAL")
    print("to the original Git objects stored at commit 1dbeebc.")
    print("Zero files altered, zero files truncated, zero files corrupted, zero files omitted.")
    print("=" * 80)

if __name__ == "__main__":
    main()
