import os
import sys
import time

sys.path.insert(0, os.path.abspath(r"CleanLightroom\tests\e2e"))
from adb_helper import ADBHelper, PACKAGE_NAME, MAIN_ACTIVITY

adb = ADBHelper()
workspace_root = r"c:\Users\Siddhu\Desktop\apps testing"

print("Starting screenshot capture sequence...")

# 1. Portrait Normal Edit
adb.set_orientation(0, settle_delay_s=1.0)
adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=2.0)
ui = adb.dump_ui()
edit_btn = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
if edit_btn:
    adb.tap_element(edit_btn, delay_s=1.0)
time.sleep(1.0)
p1 = os.path.join(workspace_root, "emulator_portrait.png")
adb.capture_screenshot(p1)
print(f"Captured: {p1} ({os.path.getsize(p1)} bytes)")

# 2. Landscape Normal Edit
adb.set_orientation(1, settle_delay_s=1.5)
ui = adb.dump_ui()
edit_btn = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
if edit_btn:
    adb.tap_element(edit_btn, delay_s=1.0)
time.sleep(1.0)
p2 = os.path.join(workspace_root, "emulator_landscape.png")
adb.capture_screenshot(p2)
print(f"Captured: {p2} ({os.path.getsize(p2)} bytes)")

# 3. Landscape Masking
mask_btn = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
if mask_btn:
    adb.tap_element(mask_btn, delay_s=1.0)
time.sleep(1.0)
ui = adb.dump_ui()
fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
if fab:
    adb.tap_element(fab, delay_s=1.0)
    ui = adb.dump_ui()
    brush = ui.find_one(text="Brush") or ui.find_one(desc="Brush")
    if brush:
        adb.tap_element(brush, delay_s=1.0)
time.sleep(1.0)
p3 = os.path.join(workspace_root, "emulator_landscape_masking.png")
adb.capture_screenshot(p3)
print(f"Captured: {p3} ({os.path.getsize(p3)} bytes)")

# 4. Portrait Masking
adb.set_orientation(0, settle_delay_s=1.5)
time.sleep(1.0)
p4 = os.path.join(workspace_root, "emulator_portrait_masking.png")
adb.capture_screenshot(p4)
print(f"Captured: {p4} ({os.path.getsize(p4)} bytes)")

# Reset device orientation to portrait
adb.set_orientation(0, settle_delay_s=0.5)
print("All 4 screenshots captured successfully!")
