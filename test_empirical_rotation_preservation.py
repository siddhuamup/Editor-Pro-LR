"""
Empirical Challenger: Active Rotation State Preservation Stress Harness
"""
import time
from CleanLightroom.tests.e2e.adb_helper import ADBHelper

def test_rotation_preservation():
    adb = ADBHelper()
    assert adb.is_device_online(), "Device offline"
    
    print("[1] Ensuring clean portrait state...")
    adb.set_orientation(0, settle_delay_s=1.0)
    
    ui = adb.dump_ui()
    if not (ui.exists(text="Exposure") or ui.exists(desc="Exposure")):
        edit_tool = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
        if edit_tool:
            print("[2] Tapping Edit tool...")
            adb.tap_element(edit_tool, delay_s=0.5)
            adb.wait_for_ui(lambda u: u.exists(text="Exposure") or u.exists(desc="Exposure") or u.exists(desc="Light"), timeout_s=4.0)
            ui = adb.dump_ui()
            
    if not (ui.exists(text="Exposure") or ui.exists(desc="Exposure")):
        light_btn = ui.find_one(desc="Light") or ui.find_one(text="Light")
        if light_btn:
            print(" -> Tapping Light sub-tool...")
            adb.tap_element(light_btn, delay_s=0.5)
            adb.wait_for_ui(lambda u: u.exists(text="Exposure") or u.exists(desc="Exposure"), timeout_s=4.0)
            ui = adb.dump_ui()
            
    assert ui.exists(text="Exposure") or ui.exists(desc="Exposure"), "Exposure not found in Edit mode"
    print(" -> Edit mode active with Exposure slider verified.")
    
    # Enter Masking mode
    mask_dock = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
    assert mask_dock is not None, "Masking dock button not found"
    print("[3] Tapping Masking mode...")
    adb.tap_element(mask_dock, delay_s=0.8)
    adb.wait_for_ui(lambda u: u.exists(desc="Create new Mask") or u.exists(desc="Add Mask"), timeout_s=5.0)
    
    ui_mask = adb.dump_ui()
    fab = ui_mask.find_one(desc="Create new Mask") or ui_mask.find_one(desc="Add Mask")
    assert fab is not None, "+ FAB / Create new Mask not found"
    print(" -> Masking dock active with + FAB verified.")
    
    # Open sheet and create Subject mask
    print("[4] Opening Create Mask sheet...")
    adb.tap_element(fab, delay_s=0.8)
    adb.wait_for_ui(lambda u: u.exists(text="Select Subject") or u.exists(desc="Select Subject"), timeout_s=4.0)
    ui_sheet = adb.dump_ui()
    subj = ui_sheet.find_one(text="Select Subject") or ui_sheet.find_one(desc="Select Subject")
    assert subj is not None, "Select Subject option missing from bottom sheet"
    print(" -> Tapping Select Subject...")
    adb.tap_element(subj, delay_s=1.0)
    
    # Verify Mask 1 in Portrait
    adb.wait_for_ui(lambda u: u.exists(text="Mask 1") or u.exists(desc="Mask 1"), timeout_s=5.0)
    ui_layer_port = adb.dump_ui()
    has_mask_port = ui_layer_port.exists(text="Mask 1") or ui_layer_port.exists(desc="Mask 1")
    assert has_mask_port, "Mask 1 was not created in Portrait!"
    print(" -> Mask 1 successfully created in Portrait mode.")
    
    # Rotate to Landscape
    print("[5] Rotating emulator to Landscape (user_rotation 1)...")
    adb.set_orientation(1, settle_delay_s=1.5)
    
    # Verify Landscape state
    ui_land = adb.dump_ui()
    img_land, land_path = adb.capture_screenshot()
    print(f" -> Landscape screen captured: {img_land.width}x{img_land.height}")
    assert img_land.width == 2400 and img_land.height == 1080, f"Unexpected landscape dimensions: {img_land.size}"
    
    # Check if Mask 1 layer survived rotation
    has_mask_land = ui_land.exists(text="Mask 1") or ui_land.exists(desc="Mask 1")
    print(f" -> Mask 1 exists in Landscape: {has_mask_land}")
    assert has_mask_land, "FAIL: Mask 1 was LOST during rotation to landscape!"
    
    # Check adjustment categories in Landscape
    for cat in ["Light", "Color", "Effects", "Detail", "Optics"]:
        cat_found = ui_land.exists(desc=cat) or ui_land.exists(text=cat)
        print(f" -> Category '{cat}' in landscape: {cat_found}")
        assert cat_found, f"FAIL: Category {cat} missing in landscape layout!"
        
    # Rotate back to Portrait
    print("[6] Rotating emulator back to Portrait (user_rotation 0)...")
    adb.set_orientation(0, settle_delay_s=1.5)
    
    ui_port_return = adb.dump_ui()
    img_port, port_path = adb.capture_screenshot()
    print(f" -> Restored Portrait screen captured: {img_port.width}x{img_port.height}")
    assert img_port.width == 1080 and img_port.height == 2400, f"Unexpected portrait dimensions: {img_port.size}"
    
    has_mask_port_return = ui_port_return.exists(text="Mask 1") or ui_port_return.exists(desc="Mask 1")
    print(f" -> Mask 1 exists after returning to Portrait: {has_mask_port_return}")
    assert has_mask_port_return, "FAIL: Mask 1 was LOST after returning to portrait!"
    
    crashes = adb.check_for_crashes()
    print(f"[7] Crashes check: {len(crashes)} crashes detected.")
    assert len(crashes) == 0, f"Crashes found: {crashes}"
    
    print("\n>>> EMPIRICAL PARITY VERIFIED: Mask state is 100% preserved across bidirectional rotation! <<<")

if __name__ == "__main__":
    test_rotation_preservation()
