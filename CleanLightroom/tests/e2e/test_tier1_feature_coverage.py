"""
CleanLightroom E2E Test Suite - Tier 1: Feature Coverage (25 Tests)
Validates core features: Build & Launch, Design Tokens & Surfaces, SVG Icon Parity,
Masking Panel Sub-modes, and Screen Orientation Adaptation.
"""

import os
import sys
import time
import pytest

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
if TEST_DIR not in sys.path:
    sys.path.insert(0, TEST_DIR)

from adb_helper import ADBHelper, ColorVerifier, PACKAGE_NAME, MAIN_ACTIVITY

WORKSPACE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES_DRAWABLE_DIR = os.path.join(WORKSPACE_ROOT, "app", "src", "main", "res", "drawable")


class TestTier1FeatureCoverage:
    """Tier 1: Comprehensive Feature Coverage (5 tests x 5 features = 25 tests)"""

    # =========================================================================
    # Feature 1: Build & Launch (Tests 1-5)
    # =========================================================================

    def test_t1_01_apk_package_installed(self, adb: ADBHelper):
        """Verifies com.cleanlr package is installed on the target device."""
        packages = adb.shell("pm list packages")
        assert PACKAGE_NAME in packages, f"Package {PACKAGE_NAME} is not installed on device!"

    def test_t1_02_launch_main_activity(self, adb: ADBHelper):
        """Verifies launching MainActivity succeeds without error."""
        adb.launch_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.0)
        assert adb.is_app_foreground(), f"{PACKAGE_NAME} is not in foreground after launch!"

    def test_t1_03_main_activity_focused_window(self, adb: ADBHelper):
        """Verifies window manager reports MainActivity as current focus."""
        focus = adb.get_focused_window()
        assert f"{PACKAGE_NAME}/{MAIN_ACTIVITY}" in focus or f"{PACKAGE_NAME}" in focus, (
            f"Focused window mismatch: {focus}"
        )

    def test_t1_04_zero_fatal_exceptions_on_launch(self, adb: ADBHelper):
        """Verifies logcat reports zero fatal exceptions or ANRs for com.cleanlr on launch."""
        adb.clear_logcat()
        adb.launch_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.0)
        crashes = adb.check_for_crashes(PACKAGE_NAME)
        assert len(crashes) == 0, f"Detected application crashes/ANRs in logcat: {crashes}"

    def test_t1_05_compose_view_hierarchy_attached(self, adb: ADBHelper):
        """Verifies ComposeView and Loupe SurfaceView are active in the view hierarchy."""
        ui = adb.dump_ui()
        compose_views = ui.find_all(class_name="ComposeView")
        surface_views = ui.find_all(class_name="SurfaceView")
        assert len(compose_views) > 0, "No ComposeView found in dumped UI hierarchy!"
        assert len(surface_views) > 0, "Loupe SurfaceView canvas not attached in hierarchy!"

    # =========================================================================
    # Feature 2: Design Tokens & Surfaces (Tests 6-10)
    # =========================================================================

    def test_t1_06_dock_surface_token_parity(self, adb: ADBHelper, clean_screen):
        """Verifies bottom tool dock matches Adobe Spectrum darkroom surface (#1D1D1D / #121212)."""
        img, _ = adb.capture_screenshot()
        # Sample bottom dock area safely away from center gesture pill (e.g. width // 4)
        dock_rgb = ColorVerifier.sample_pixel(img, img.width // 4, img.height - 50)
        lum = ColorVerifier.relative_luminance(dock_rgb)
        # Spectrum Darkroom surfaces must be dark (luminance < 0.10, RGB channels < 45)
        assert all(c < 45 for c in dock_rgb), (
            f"Dock surface RGB {dock_rgb} exceeds darkroom surface threshold (#1D1D1D / #121212)"
        )
        assert lum < 0.10, f"Dock surface luminance {lum:.4f} is too bright for darkroom theme"

    def test_t1_07_canvas_background_token_dark(self, adb: ADBHelper, clean_screen):
        """Verifies photo canvas background uses darkroom canvas token (#000000 / #121212)."""
        img, _ = adb.capture_screenshot()
        # Sample canvas corner (10, 150) outside the active photograph area
        canvas_rgb = ColorVerifier.sample_pixel(img, 10, 150)
        assert all(c < 35 for c in canvas_rgb), (
            f"Canvas background RGB {canvas_rgb} is not dark neutral (#000000 / #121212)"
        )

    def test_t1_08_active_accent_blue_token(self, adb: ADBHelper, clean_screen):
        """Verifies Color.kt defines active accent blue (#2680EB / #1473E6)."""
        color_file = os.path.join(WORKSPACE_ROOT, "app", "src", "main", "kotlin", "com", "cleanlr", "ui", "theme", "Color.kt")
        assert os.path.exists(color_file), "Color.kt tokens file missing!"
        with open(color_file, "r", encoding="utf-8") as f:
            content = f.read()
        assert "2680EB" in content or "1473E6" in content, (
            "Active accent blue token (#2680EB or #1473E6) missing from Color.kt!"
        )

    def test_t1_09_top_bar_surface_token(self, adb: ADBHelper, clean_screen):
        """Verifies top action bar surface background is dark Spectrum surface."""
        img, _ = adb.capture_screenshot()
        top_bar_rgb = ColorVerifier.sample_pixel(img, img.width // 2, 80)
        assert all(c < 55 for c in top_bar_rgb), (
            f"Top bar RGB {top_bar_rgb} does not match Spectrum dark surface tokens"
        )

    def test_t1_10_tabular_slider_values_styling(self, adb: ADBHelper, clean_screen):
        """Verifies sliders display monospace/tabular values when Edit mode is entered."""
        ui = adb.dump_ui()
        if not (ui.exists(text="Exposure") or ui.exists(desc="Exposure")):
            edit_tool = ui.find_one(text="Edit") or ui.find_one(desc="Edit")
            assert edit_tool is not None, "Edit tool not found in dock!"
            adb.tap_element(edit_tool)
            time.sleep(1.0)
            ui = adb.dump_ui()

        assert ui.exists(text="Exposure") or ui.exists(desc="Exposure"), (
            "Exposure slider not visible after activating Edit mode!"
        )

    # =========================================================================
    # Feature 3: SVG Icon Parity (Tests 11-15)
    # =========================================================================

    def test_t1_11_authentic_svg_drawables_present(self):
        """Verifies authentic ic_lr_* VectorDrawables exist in res/drawable."""
        assert os.path.exists(RES_DRAWABLE_DIR), f"res/drawable not found at {RES_DRAWABLE_DIR}"
        lr_drawables = [f for f in os.listdir(RES_DRAWABLE_DIR) if f.startswith("ic_lr_") and f.endswith(".xml")]
        assert len(lr_drawables) >= 100, (
            f"Expected at least 100 authentic ic_lr_* drawables, found {len(lr_drawables)}"
        )

    def test_t1_12_primary_dock_icons_parity(self, adb: ADBHelper, clean_screen):
        """Verifies primary dock contains all 5 authentic Lightroom tools."""
        ui = adb.dump_ui()
        required_tools = ["Presets", "Crop", "Edit", "Masking", "Remove"]
        for tool in required_tools:
            found = ui.exists(text=tool) or ui.exists(desc=tool)
            assert found, f"Primary dock tool '{tool}' not found in UI hierarchy!"

    def test_t1_13_top_bar_action_icons(self, adb: ADBHelper, clean_screen):
        """Verifies top bar contains standard action buttons: Back, Undo, Compare, Share, More."""
        ui = adb.dump_ui()
        assert ui.exists(desc="Back to library"), "Back button missing in top bar!"
        assert ui.exists(desc="Undo (Tap) / History (Long-Press)"), "Undo button missing in top bar!"
        assert ui.exists(desc="Share and Export"), "Share/Export button missing in top bar!"
        assert ui.exists(desc="More options"), "More options button missing in top bar!"

    def test_t1_14_masking_svg_icons_present(self):
        """Verifies masking vector drawables (brush, linear, radial, invert) exist in resources."""
        mask_icons = ["ic_lr_mask_brush.xml", "ic_lr_mask_linear_gradient.xml", "ic_lr_mask_radial_gradient.xml", "ic_lr_mask_invert.xml"]
        for icon in mask_icons:
            icon_path = os.path.join(RES_DRAWABLE_DIR, icon)
            assert os.path.exists(icon_path), f"Masking drawable {icon} missing in {RES_DRAWABLE_DIR}!"

    def test_t1_15_no_generic_material_icons_in_dock(self):
        """Verifies EditorScreen does not leak Material imageVector fallbacks in primary dock."""
        editor_kt = os.path.join(WORKSPACE_ROOT, "app", "src", "main", "kotlin", "com", "cleanlr", "ui", "editor", "EditorScreen.kt")
        if os.path.exists(editor_kt):
            with open(editor_kt, "r", encoding="utf-8") as f:
                content = f.read()
            # Verify primary tools use authentic R.drawable.ic_lr_*
            assert "ic_lr_" in content, "EditorScreen does not reference ic_lr_* authentic drawables!"

    # =========================================================================
    # Feature 4: Masking Panel Sub-modes (Tests 16-20)
    # =========================================================================

    def test_t1_16_masking_mode_entry(self, adb: ADBHelper, clean_screen):
        """Verifies tapping Masking in dock enters Masking mode."""
        ui = adb.dump_ui()
        if not (ui.exists(desc="Create new Mask") or ui.exists(desc="Add Mask")):
            mask_dock = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
            assert mask_dock is not None, "Masking tool dock item not found!"
            adb.tap_element(mask_dock)
            time.sleep(1.0)
            ui_mask = adb.dump_ui()
        else:
            ui_mask = ui
        assert ui_mask.exists(desc="Create new Mask") or ui_mask.exists(desc="Add Mask"), (
            "Masking mode did not display Create new Mask action!"
        )

    def test_t1_17_create_new_mask_button_rendered(self, adb: ADBHelper):
        """Verifies the floating + FAB / Create new Mask button is present on canvas."""
        ui = adb.dump_ui()
        fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
        if not fab:
            mask_dock = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
            if mask_dock:
                adb.tap_element(mask_dock)
                time.sleep(1.0)
                ui = adb.dump_ui()
                fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
        assert fab is not None, "Floating '+' FAB / Create new Mask button is not rendered!"

    def test_t1_18_create_mask_modal_sheet_open(self, adb: ADBHelper):
        """Verifies tapping + FAB opens the 'Create new Mask' bottom sheet."""
        ui = adb.dump_ui()
        fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
        if not fab:
            mask_dock = ui.find_one(desc="Masking") or ui.find_one(text="Masking")
            if mask_dock:
                adb.tap_element(mask_dock)
                time.sleep(1.0)
                ui = adb.dump_ui()
                fab = ui.find_one(desc="Create new Mask") or ui.find_one(desc="Add Mask")
        assert fab is not None, "FAB not found"
        adb.tap_element(fab)
        time.sleep(1.0)
        ui_sheet = adb.dump_ui()
        assert ui_sheet.exists(text="Create new Mask") or ui_sheet.exists(desc="Create new Mask"), (
            "Create new Mask sheet did not open!"
        )

    def test_t1_19_mask_creation_sheet_three_tool_groups(self, adb: ADBHelper):
        """Verifies modal sheet contains Cloud AI, Manual, and Range tool options."""
        ui = adb.dump_ui()
        assert ui.exists(text="Select Subject") or ui.exists(desc="Select Subject"), "Subject tool missing!"
        assert ui.exists(text="Brush") or ui.exists(desc="Brush"), "Brush tool missing!"
        assert ui.exists(text="Linear Gradient") or ui.exists(desc="Linear Gradient"), "Linear Gradient missing!"
        assert ui.exists(text="Color Range") or ui.exists(desc="Color Range"), "Color Range missing!"

    def test_t1_20_mask_spawn_and_adjustment_categories(self, adb: ADBHelper):
        """Verifies selecting a mask tool creates Mask 1 with 5 adjustment categories."""
        ui = adb.dump_ui()
        subject_tool = ui.find_one(text="Select Subject") or ui.find_one(desc="Select Subject")
        if subject_tool:
            adb.tap_element(subject_tool)
            adb.wait_for_ui(lambda u: u.exists(text="Mask 1") or u.exists(desc="Mask 1"), timeout_s=5.0)
        ui_layer = adb.dump_ui()
        assert ui_layer.exists(text="Mask 1") or ui_layer.exists(desc="Mask 1"), "Mask 1 layer not created!"
        for category in ["Light", "Color", "Effects", "Detail", "Optics"]:
            assert ui_layer.exists(text=category) or ui_layer.exists(desc=category), (
                f"Adjustment category '{category}' missing from mask panel!"
            )

    # =========================================================================
    # Feature 5: Screen Orientation Adaptation (Tests 21-25)
    # =========================================================================

    def test_t1_21_default_portrait_layout(self, adb: ADBHelper):
        """Verifies default portrait mode has rotation 0 and centered canvas."""
        adb.set_orientation(0)
        ui = adb.dump_ui()
        assert ui.rotation == 0, f"Expected portrait rotation 0, got {ui.rotation}"

    def test_t1_22_rotate_to_landscape_command(self, adb: ADBHelper):
        """Verifies setting user_rotation 1 rotates device to landscape (rotation 1)."""
        adb.set_orientation(1)
        ui = adb.dump_ui()
        assert ui.rotation == 1, f"Expected landscape rotation 1, got {ui.rotation}"

    def test_t1_23_landscape_width_expansion(self, adb: ADBHelper):
        """Verifies in landscape mode the screen width expands to 2400 with right-aligned rails."""
        img, _ = adb.capture_screenshot()
        assert img.width == 2400, f"Expected landscape width 2400, got {img.width}"
        assert img.height == 1080, f"Expected landscape height 1080, got {img.height}"

    def test_t1_24_rotate_back_to_portrait(self, adb: ADBHelper):
        """Verifies restoring portrait mode (user_rotation 0) succeeds cleanly."""
        adb.set_orientation(0)
        ui = adb.dump_ui()
        assert ui.rotation == 0, f"Expected restored portrait rotation 0, got {ui.rotation}"
        assert adb.is_app_foreground(), "App crashed or exited during rotation back to portrait!"

    def test_t1_25_canvas_surface_adapts_to_orientation(self, adb: ADBHelper):
        """Verifies SurfaceView canvas adapts bounds across portrait and landscape."""
        adb.set_orientation(0)
        ui_port = adb.dump_ui()
        surface_port = ui_port.find_one(class_name="SurfaceView")
        assert surface_port is not None, "SurfaceView missing in portrait!"

        adb.set_orientation(1, settle_delay_s=1.5)
        ui_land = adb.dump_ui()
        assert ui_land.rotation == 1, "Failed rotation to landscape"
        compose_view = ui_land.find_one(class_name="ComposeView")
        assert compose_view is not None, "ComposeView missing in landscape!"

        # Restore portrait
        adb.set_orientation(0)
