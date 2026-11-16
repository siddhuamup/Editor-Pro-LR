"""
Pytest configuration and shared fixtures for CleanLightroom E2E test suite.
"""

import os
import sys
import pytest

# Ensure tests/e2e directory is in sys.path
TEST_DIR = os.path.dirname(os.path.abspath(__file__))
if TEST_DIR not in sys.path:
    sys.path.insert(0, TEST_DIR)

from adb_helper import ADBHelper, ColorVerifier, PACKAGE_NAME, MAIN_ACTIVITY


@pytest.fixture(scope="session")
def adb():
    helper = ADBHelper()
    assert helper.is_device_online(), "Target emulator-5554 is offline or not found via ADB!"
    # Ensure default portrait orientation
    helper.set_orientation(0)
    # Ensure application is running
    if not helper.is_app_foreground():
        helper.launch_app(PACKAGE_NAME, MAIN_ACTIVITY)
    yield helper
    # Tear down session back to portrait
    try:
        helper.set_orientation(0)
    except Exception:
        pass


@pytest.fixture
def clean_screen(adb):
    """Ensures device is in portrait mode and editor is active before test."""
    adb.set_orientation(0, settle_delay_s=1.5)
    if not adb.is_app_foreground():
        adb.launch_app(PACKAGE_NAME, MAIN_ACTIVITY)
    try:
        ui = adb.dump_ui()
    except Exception:
        # If app is stuck in an ANR or unhandled popup, restart MainActivity
        adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)
        adb.wait_for_ui(lambda u: u.exists(desc="Edit") or u.exists(text="Edit"), timeout_s=6.0)
        ui = adb.dump_ui()

    # If an ANR or unhandled System UI popup is present in hierarchy, restart MainActivity
    if (
        ui.find_one(text="isn't responding")
        or ui.find_one(text="Application Not Responding")
        or ui.find_one(text="Wait")
        or ui.find_one(text="Close app")
    ):
        adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)
        adb.wait_for_ui(lambda u: u.exists(desc="Edit") or u.exists(text="Edit"), timeout_s=6.0)
        ui = adb.dump_ui()

    # If stranded in LibraryScreen (e.g. after back button navigation), restart to enter Editor
    if not ui.find_one(desc="Edit") and not ui.find_one(text="Edit"):
        adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)
        adb.wait_for_ui(lambda u: u.exists(desc="Edit") or u.exists(text="Edit"), timeout_s=6.0)
        ui = adb.dump_ui()
    # If in Crop or a modal mode with Cancel/Discard button, dismiss it
    dismiss_btn = ui.find_one(desc="Cancel") or ui.find_one(text="Cancel") or ui.find_one(desc="Discard mask")
    if dismiss_btn:
        adb.tap_element(dismiss_btn, delay_s=0.5)
        ui = adb.dump_ui()
    # Ensure default Edit mode is active so top-bar controls are available
    if not ui.find_one(desc="Undo"):
        edit_tool = ui.find_one(desc="Edit") or ui.find_one(text="Edit")
        if edit_tool:
            adb.tap_element(edit_tool, delay_s=0.5)
    yield adb


@pytest.fixture
def color_verifier():
    return ColorVerifier
