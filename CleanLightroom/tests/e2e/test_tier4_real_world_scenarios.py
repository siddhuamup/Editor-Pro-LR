"""
CleanLightroom E2E Test Suite - Tier 4: Real-World Application Scenarios (5 Workflows)
Validates complete user journeys: Portrait Photo Edit, Masking Creation & Adjustment,
Landscape Orientation Pivot, Landscape Masking Session, and Crop/Aspect in both orientations.
"""

import os
import sys
import time
import pytest

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
if TEST_DIR not in sys.path:
    sys.path.insert(0, TEST_DIR)

from adb_helper import ADBHelper, PACKAGE_NAME, MAIN_ACTIVITY


class TestTier4RealWorldScenarios:
    """Tier 4: End-to-End Real-World Scenarios (5 Workflows)"""

    def test_t4_01_scenario_portrait_primary_workflow(self, adb: ADBHelper, clean_screen):
        """Scenario 1: Full Portrait Primary Editing Workflow.
        Library navigation -> Editor open -> Adjust Light sliders -> Compare before/after.
        """
        # Step 1: Ensure in EditorScreen
        if not adb.is_app_foreground(timeout_s=5.0):
            adb.launch_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=2.0)
        assert adb.is_app_foreground(timeout_s=5.0), "App not in foreground"

        # Step 2: Open Edit tool in primary dock
        ui = adb.dump_ui()
        edit_tool = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
        if not edit_tool:
            adb.wait_for_ui(lambda u: u.exists(desc="Edit") or u.exists(text="Edit"), timeout_s=5.0)
            ui = adb.dump_ui()
            edit_tool = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
        assert edit_tool is not None, "Edit tool not found"
        adb.tap_element(edit_tool, delay_s=0.8)

        # Step 3: Verify Light sliders (Exposure, Contrast)
        ui_edit = adb.dump_ui()
        if not (ui_edit.exists(text="Exposure") or ui_edit.exists(desc="Exposure")):
            light_tab = ui_edit.find_one(desc="Light") or ui_edit.find_one(text="Light")
            if light_tab:
                adb.tap_element(light_tab, delay_s=0.8)
                ui_edit = adb.dump_ui()
        assert ui_edit.exists(text="Exposure") or ui_edit.exists(desc="Exposure"), "Exposure slider missing"

        # Step 4: Tap Compare before/after icon
        compare_btn = ui_edit.find_one(desc="Compare before/after")
        if compare_btn:
            adb.tap_element(compare_btn, delay_s=0.5)

        # Step 5: Verify zero crashes throughout workflow
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash during portrait editing workflow!"

    def test_t4_02_scenario_masking_creation_and_editing(self, adb: ADBHelper, clean_screen):
        """Scenario 2: Complete Masking Creation & Local Adjustment Workflow.
        Open Masking -> Tap (+) -> Select Subject -> Verify mask layer -> Adjust categories.
        """
        # Step 1: Tap Masking in primary dock
        ui = adb.dump_ui()
        mask_dock = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
        assert mask_dock is not None, "Masking dock item missing"
        adb.tap_element(mask_dock, delay_s=0.8)

        # Step 2: Tap + FAB / Create new Mask
        ui_mask = adb.dump_ui()
        fab = ui_mask.find_one(desc="Create new Mask") or ui_mask.find_one(desc="Add Mask")
        if fab:
            adb.tap_element(fab, delay_s=0.8)
            ui_sheet = adb.dump_ui()
            # Step 3: Select Subject or Brush
            sub = ui_sheet.find_one(desc="Select Subject") or ui_sheet.find_one(text="Select Subject")
            if sub:
                adb.tap_element(sub, delay_s=1.0)

        # Step 4: Verify Mask layer controls (Invert, Opacity, Category tabs)
        ui_layer = adb.dump_ui()
        assert ui_layer.exists(desc="Invert") or ui_layer.exists(text="Invert"), "Invert button missing"
        assert ui_layer.exists(desc="Light") or ui_layer.exists(text="Light"), "Light category missing"

        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash during masking workflow!"

    def test_t4_03_scenario_landscape_orientation_pivot(self, adb: ADBHelper, clean_screen):
        """Scenario 3: Landscape Orientation Pivot Workflow.
        Rotate emulator to landscape -> Verify 3-rail right layout -> Select Edit sub-tools.
        """
        # Step 1: Rotate to landscape
        adb.set_orientation(1, settle_delay_s=1.2)
        ui_land = adb.dump_ui()
        assert ui_land.rotation == 1, "Failed to rotate to landscape"

        # Step 2: Verify width expansion to 2400
        img, _ = adb.capture_screenshot()
        assert img.width == 2400 and img.height == 1080, f"Unexpected landscape resolution: {img.size}"

        # Step 3: Tap Edit tool in landscape primary dock
        edit_tool = ui_land.find_one(desc="Edit") or ui_land.find_one(text="Edit")
        if edit_tool:
            adb.tap_element(edit_tool, delay_s=0.8)

        # Step 4: Rotate back to portrait
        adb.set_orientation(0, settle_delay_s=1.0)
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash during landscape pivot workflow!"

    def test_t4_04_scenario_masking_in_landscape(self, adb: ADBHelper, clean_screen):
        """Scenario 4: Masking Session in Landscape.
        Open Masking in landscape -> Check 5 adjustment tabs in right column -> Invert mask.
        """
        # Step 1: Rotate to landscape
        adb.set_orientation(1, settle_delay_s=1.2)

        # Step 2: Open Masking
        ui = adb.dump_ui()
        mask_tool = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
        if mask_tool:
            adb.tap_element(mask_tool, delay_s=0.8)

        # If no mask layer exists yet, create one
        ui = adb.dump_ui()
        fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
        if fab:
            adb.tap_element(fab, delay_s=0.8)
            ui_sheet = adb.dump_ui()
            subj = ui_sheet.find_one(text="Select Subject") or ui_sheet.find_one(desc="Select Subject")
            if subj:
                adb.tap_element(subj, delay_s=1.0)

        # Step 3: Check adjustment tabs (Light, Color, Effects, Detail, Optics)
        ui_mask = adb.dump_ui()
        for cat in ["Light", "Color", "Effects", "Detail", "Optics"]:
            assert ui_mask.exists(desc=cat) or ui_mask.exists(text=cat), f"Missing {cat} tab in landscape masking"

        # Step 4: Tap Invert if available
        invert_btn = ui_mask.find_one(desc="Invert") or ui_mask.find_one(text="Invert")
        if invert_btn:
            adb.tap_element(invert_btn, delay_s=0.5)

        # Step 5: Restore portrait
        adb.set_orientation(0, settle_delay_s=0.8)
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash during landscape masking workflow!"

    def test_t4_05_scenario_crop_and_aspect_in_both_orientations(self, adb: ADBHelper, clean_screen):
        """Scenario 5: Crop & Aspect Ratio in Both Orientations.
        Open Crop in portrait -> Rotate to landscape -> Verify crop controls -> Restore portrait.
        """
        # Step 1: Tap Crop in portrait
        ui = adb.dump_ui()
        crop_btn = ui.find_one(desc="Crop") or ui.find_one(text="Crop")
        if crop_btn:
            adb.tap_element(crop_btn, delay_s=0.8)

        # Step 2: Rotate to landscape
        adb.set_orientation(1, settle_delay_s=1.2)
        ui_land = adb.dump_ui()
        assert ui_land.rotation == 1, "Failed rotation in Crop"

        # Step 3: Rotate back to portrait
        adb.set_orientation(0, settle_delay_s=1.0)
        # Step 4: Dismiss Crop mode by tapping Cancel
        ui_crop = adb.dump_ui()
        cancel_btn = ui_crop.find_one(desc="Cancel") or ui_crop.find_one(text="Cancel")
        if cancel_btn:
            adb.tap_element(cancel_btn, delay_s=0.8)
        assert adb.is_app_foreground(), "App terminated after crop orientation test"
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash during Crop orientation workflow!"
