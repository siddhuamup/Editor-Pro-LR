import os
import shutil
import hashlib
import json
import tempfile

def test_migration_logic():
    print("=== TESTING MIGRATION ENGINE LOGIC IN ISOLATED SANDBOX ===")
    sandbox = os.path.join(".agents", "teamwork", "explorer_m1_1", "mock_sandbox")
    if os.path.exists(sandbox):
        shutil.rmtree(sandbox)
    os.makedirs(sandbox)

    # Setup mock structure
    h_dir = os.path.join(sandbox, "horizontal screen UI(same as verticle)")
    os.makedirs(h_dir)
    svg_dir = os.path.join(sandbox, "Svg Icons")
    os.makedirs(svg_dir)
    with open(os.path.join(svg_dir, "test.svg"), "w") as f:
        f.write("<svg></svg>")

    # Create dummy files
    f1_content = b"Vertical Screenshot 1 Test Content"
    f2_content = b"Horizontal Screenshot 2 Test Content"
    
    f1_path = os.path.join(sandbox, "v1.png")
    f2_path = os.path.join(h_dir, "h1.png")
    
    with open(f1_path, "wb") as f:
        f.write(f1_content)
    with open(f2_path, "wb") as f:
        f.write(f2_content)

    f1_hash = hashlib.sha256(f1_content).hexdigest()
    f2_hash = hashlib.sha256(f2_content).hexdigest()

    mock_manifest = {
        "total_files": 2,
        "total_panels": 2,
        "panel_counts": {
            "Gallery": 1,
            "Crop": 1
        },
        "files": [
            {
                "filename": "v1.png",
                "source_dir": "vertical",
                "rel_path": "v1.png",
                "size": len(f1_content),
                "sha256": f1_hash,
                "panel": "Gallery",
                "new_rel_path": "Gallery/v1.png"
            },
            {
                "filename": "h1.png",
                "source_dir": "horizontal",
                "rel_path": "horizontal screen UI(same as verticle)/h1.png",
                "size": len(f2_content),
                "sha256": f2_hash,
                "panel": "Crop",
                "new_rel_path": "Crop/h1.png"
            }
        ]
    }

    manifest_file = os.path.join(sandbox, "PANEL_MANIFEST.json")
    with open(manifest_file, "w") as f:
        json.dump(mock_manifest, f, indent=2)

    # Now define migration function
    def run_migration(base_dir):
        with open(os.path.join(base_dir, "PANEL_MANIFEST.json"), "r") as mf:
            m = json.load(mf)
        
        # 1. Create panel directories
        for panel in m["panel_counts"].keys():
            os.makedirs(os.path.join(base_dir, panel), exist_ok=True)
            
        moved_count = 0
        already_in_place = 0

        # 2. Process each file
        for f in m["files"]:
            src = os.path.join(base_dir, f["rel_path"].replace("/", os.sep))
            dst = os.path.join(base_dir, f["new_rel_path"].replace("/", os.sep))
            exp_sha = f["sha256"]
            exp_sz = f["size"]

            if os.path.exists(dst):
                # Verify dst
                with open(dst, "rb") as fp:
                    h = hashlib.sha256(fp.read()).hexdigest()
                assert h == exp_sha, f"Destination hash mismatch: {dst}"
                assert os.path.getsize(dst) == exp_sz, f"Destination size mismatch: {dst}"
                
                # Check if src also exists
                if os.path.exists(src):
                    with open(src, "rb") as fp:
                        src_h = hashlib.sha256(fp.read()).hexdigest()
                    assert src_h == exp_sha, f"Source duplicate hash mismatch: {src}"
                    os.remove(src)
                already_in_place += 1
            elif os.path.exists(src):
                # Verify src before moving
                with open(src, "rb") as fp:
                    h = hashlib.sha256(fp.read()).hexdigest()
                assert h == exp_sha, f"Source hash mismatch: {src}"
                assert os.path.getsize(src) == exp_sz, f"Source size mismatch: {src}"

                # Atomic move
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                os.replace(src, dst)

                # Verify dst after moving
                with open(dst, "rb") as fp:
                    dst_h = hashlib.sha256(fp.read()).hexdigest()
                assert dst_h == exp_sha, f"Target hash mismatch post-move: {dst}"
                assert os.path.getsize(dst) == exp_sz, f"Target size mismatch post-move: {dst}"
                moved_count += 1
            else:
                raise FileNotFoundError(f"Neither src nor dst found for {f['filename']}")

        # 3. Clean up horizontal folder if empty
        h_dir_path = os.path.join(base_dir, "horizontal screen UI(same as verticle)")
        if os.path.exists(h_dir_path):
            if len(os.listdir(h_dir_path)) == 0:
                os.rmdir(h_dir_path)

        # 4. Verify no stray png files in root
        root_pngs = [fn for fn in os.listdir(base_dir) if fn.lower().endswith(".png")]
        assert len(root_pngs) == 0, f"Stray PNGs in root: {root_pngs}"

        return moved_count, already_in_place

    # Execution 1: Initial migration
    m_count, a_count = run_migration(sandbox)
    print(f"Run 1: moved={m_count}, already_in_place={a_count}")
    assert m_count == 2
    assert a_count == 0
    assert not os.path.exists(h_dir), "horizontal dir should be removed"
    assert os.path.exists(os.path.join(sandbox, "Gallery", "v1.png"))
    assert os.path.exists(os.path.join(sandbox, "Crop", "h1.png"))
    assert os.path.exists(os.path.join(svg_dir, "test.svg")), "Svg Icons must remain"

    # Execution 2: Idempotent rerun
    m_count2, a_count2 = run_migration(sandbox)
    print(f"Run 2 (idempotent): moved={m_count2}, already_in_place={a_count2}")
    assert m_count2 == 0
    assert a_count2 == 2

    print("ALL MIGRATION LOGIC TESTS PASSED IN SANDBOX!")
    shutil.rmtree(sandbox)

if __name__ == "__main__":
    test_migration_logic()
