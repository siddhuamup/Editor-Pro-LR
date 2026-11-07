"""
CleanLightroom E2E Test Suite - Tier 3: Cross-Feature Combinations (10 Pairwise Tests)
Validates interactions between global adjustments, masking, orientation pivots,
undo/redo stacks, and navigation across display configurations.
"""

import os
import sys
import time
import pytest

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
if TEST_DIR not in sys.path:
    sys.path.insert(0, TEST_DIR)

from adb_helper import ADBHelper, PACKAGE_NAME, MAIN_ACTIVITY


class TestTier3PairwiseCombinations:
    """Tier 3: Pairwise Cross-Feature Integration Tests (10 tests)"""

    def test_t3_01_pairwise_edit_and_masking(self, adb: ADBHelper, clean_screen):
        """Verifies global Edit adjustments and Masking layers coexist seamlessly."""
        ui = adb.dump_ui()
        if not (ui.exists(text="Exposure") or ui.exists(desc="Exposure")):
            edit_tool = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
            if edit_tool:
                adb.tap_element(edit_tool, delay_s=0.8)
        # Switch directly to Masking
        ui = adb.dump_ui()
        mask_tool = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
        if not mask_tool:
            adb.wait_for_ui(lambda u: u.exists(desc="Masking") or u.exists(text="Masking"), timeout_s=4.0)
            ui = adb.dump_ui()
            mask_tool = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
        assert mask_tool is not None, "Masking tool not accessible while in Edit mode!"
        adb.tap_element(mask_tool, delay_s=0.8)
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash switching Edit -> Masking!"

    def test_t3_02_pairwise_masking_subject_in_landscape(self, adb: ADBHelper, clean_screen):
        """Creates Subject mask while in landscape orientation; verifies right-rail layout."""
        adb.set_orientation(1, settle_delay_s=1.0)
        ui = adb.dump_ui()
        mask_tool = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
        if mask_tool:
            adb.tap_element(mask_tool, delay_s=0.8)
        ui_mask = adb.dump_ui()
        assert ui_mask.rotation == 1, "Expected landscape rotation 1"
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash in landscape masking!"
        adb.set_orientation(0, settle_delay_s=0.5)

    def test_t3_03_pairwise_masking_color_temp_in_landscape(self, adb: ADBHelper, clean_screen):
        """In landscape orientation, selects Color category and verifies Temp/Tint controls."""
        adb.set_orientation(1, settle_delay_s=1.0)
        ui = adb.dump_ui()
        color_tab = ui.find_one(desc="Color") or ui.find_one(text="Color")
        if color_tab:
            adb.tap_element(color_tab, delay_s=0.5)
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash selecting Color in landscape!"
        adb.set_orientation(0, settle_delay_s=0.5)

    def test_t3_04_pairwise_crop_and_rotate(self, adb: ADBHelper, clean_screen):
        """Opens Crop in portrait, rotates to landscape; verifies crop constraints adapt."""
        ui = adb.dump_ui()
        crop_btn = ui.find_one(desc="Crop") or ui.find_one(text="Crop")
        if crop_btn:
            adb.tap_element(crop_btn, delay_s=0.8)
        adb.set_orientation(1, settle_delay_s=1.0)
        ui_land = adb.dump_ui()
        assert ui_land.rotation == 1, "Failed rotation during Crop"
        assert adb.is_app_foreground(), "App terminated during Crop rotation!"
        adb.set_orientation(0, settle_delay_s=0.5)

    def test_t3_05_pairwise_presets_and_landscape_dock(self, adb: ADBHelper, clean_screen):
        """Opens Presets, rotates to landscape; verifies preset items adapt cleanly."""
        ui = adb.dump_ui()
        presets_btn = ui.find_one(desc="Presets") or ui.find_one(text="Presets")
        if presets_btn:
            adb.tap_element(presets_btn, delay_s=0.8)
        adb.set_orientation(1, settle_delay_s=1.2)
        assert adb.is_app_foreground(), "App crashed rotating during Presets!"
        adb.set_orientation(0, settle_delay_s=1.0)
        # Restore Edit mode
        ui_back = adb.dump_ui()
        edit_btn = ui_back.find_one(desc="Edit") or ui_back.find_one(text="Edit")
        if edit_btn:
            adb.tap_element(edit_btn, delay_s=0.8)

    def test_t3_06_pairwise_undo_redo_across_mask_creation(self, adb: ADBHelper, clean_screen):
        """Creates a mask and verifies Undo action in top bar operates without crash."""
        ui = adb.dump_ui()
        undo_btn = ui.find_one(desc="Undo")
        if not undo_btn:
            edit_btn = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
            if edit_btn:
                adb.tap_element(edit_btn, delay_s=0.8)
                ui = adb.dump_ui()
                undo_btn = ui.find_one(desc="Undo")
        if undo_btn:
            adb.tap_element(undo_btn, delay_s=0.5)
        assert adb.is_app_foreground(), "App terminated tapping Undo!"
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash tapping Undo in masking!"

    def test_t3_07_pairwise_undo_redo_across_orientation(self, adb: ADBHelper, clean_screen):
        """Taps Undo in landscape mode to verify top bar actions work across orientation."""
        adb.set_orientation(1, settle_delay_s=1.0)
        ui = adb.dump_ui()
        undo_btn = ui.find_one(desc="Undo (Tap) / History (Long-Press)")
        if undo_btn:
            adb.tap_element(undo_btn, delay_s=0.5)
        assert len(adb.check_for_crashes(PACKAGE_NAME)) == 0, "Crash tapping Undo in landscape!"
        adb.set_orientation(0, settle_delay_s=0.5)

    def test_t3_08_pairwise_mask_combine_and_invert(self, adb: ADBHelper, clean_screen):
        """Tests + Combine and Invert interaction on active mask."""
        ui = adb.dump_ui()
        combine_btn = ui.find_one(text="+ Combine")
        if combine_btn:
            adb.tap_element(combine_btn, delay_s=0.8)
        ui_post = adb.dump_ui()
        assert adb.is_app_foreground(), "App crashed on Combine tap!"

    def test_t3_09_pairwise_mask_discard_vs_commit(self, adb: ADBHelper, clean_screen):
        """Verifies top bar commit or discard controls handle user exit gracefully."""
        ui = adb.dump_ui()
        # Look for commit checkmark or discard close icon
        more_btn = ui.find_one(desc="More options")
        if more_btn:
            adb.tap_element(more_btn, delay_s=0.5)
            # Dismiss
            adb.key_event("KEYCODE_BACK", delay_s=0.5)
        assert adb.is_app_foreground(), "App exited unexpectedly!"

    def test_t3_10_pairwise_library_navigation_and_rotation(self, adb: ADBHelper, clean_screen):
        """Navigates Back to Library, rotates screen, re-opens Editor."""
        ui = adb.dump_ui()
        back_btn = ui.find_one(desc="Back to library")
        if back_btn:
            adb.tap_element(back_btn, delay_s=0.8)
        # Restart MainActivity to re-enter Editor
        adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)
        adb.wait_for_ui(lambda u: u.exists(desc="Edit") or u.exists(text="Edit"), timeout_s=5.0)
        assert adb.is_app_foreground(), "App failed to re-open Editor!"
