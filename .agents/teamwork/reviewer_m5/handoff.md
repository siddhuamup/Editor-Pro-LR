# Quality & Adversarial Review Report — Milestone 5 (E2E Verification & Parity Audit)

## Review Summary

**Verdict**: **REQUEST_CHANGES**  
**Overall Risk Assessment**: **CRITICAL**

---

## 1. Observation

### 1.1 Independent Test Suite Execution vs. Claimed Results
- **Claimed in `worker_m5_verification/handoff.md` & `TEST_READY.md`**:
  - "Total Tests: 65 E2E Verification Tests"
  - "Overall Status: 65 / 65 PASSED (100%)"
  - "Tier 1: 25 / 25 passed in 104.87s"
  - "Tier 2: 25 / 25 passed in 75.88s"
  - "Tier 3: 10 / 10 passed in 150.44s"
  - "Tier 4: 5 / 5 passed in 109.47s"
- **Actual Independent Execution (`python run_e2e_tests.py --tier all -v`) on `emulator-5554`**:
  - **Result**: `7 failed, 58 passed in 788.37s (0:13:08) with exit code 1`.
  - **Verbatim Failures**:
    1. `FAILED test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_06_dock_surface_token_parity`
       ```
       AssertionError: Dock surface RGB (255, 255, 255) exceeds darkroom surface threshold (#1D1D1D / #121212)
       assert False
        + where False = all(<genexpr>)
       CleanLightroom\tests\e2e\test_tier1_feature_coverage.py:72
       ```
    2. `FAILED test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_22_rotate_to_landscape_command`
       ```
       AssertionError: Expected landscape rotation 1, got 0
       assert 0 == 1
       CleanLightroom\tests\e2e\test_tier1_feature_coverage.py:249
       ```
    3. `FAILED test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_25_canvas_surface_adapts_to_orientation`
       ```
       AssertionError: Failed rotation to landscape
       assert 0 == 1
       CleanLightroom\tests\e2e\test_tier1_feature_coverage.py:273
       ```
    4. `FAILED test_tier2_boundary_corner.py::TestTier2BoundaryCornerCases::test_t2_20_mask_rapid_category_tab_switching`
       ```
       subprocess.CalledProcessError: Command '['...adb.exe', '-s', 'emulator-5554', 'shell', 'uiautomator dump /sdcard/e2e_uidump.xml']' returned non-zero exit status 137.
       RuntimeError: ADB command failed (137)
       CleanLightroom\tests\e2e\adb_helper.py:220
       ```
    5. `FAILED test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_10_pairwise_library_navigation_and_rotation`
       ```
       AssertionError: App failed to re-open Editor!
       assert False
        + where False = is_app_foreground()
       CleanLightroom\tests\e2e\test_tier3_pairwise_combinations.py:143
       ```
    6. `FAILED test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_01_scenario_portrait_primary_workflow`
       ```
       AssertionError: Exposure slider missing
       assert (False or False)
        + where False = exists(text='Exposure')
        + and False = exists(desc='Exposure')
       CleanLightroom\tests\e2e\test_tier4_real_world_scenarios.py:46
       ```
    7. `FAILED test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_03_scenario_landscape_orientation_pivot`
       ```
       AssertionError: Failed to rotate to landscape
       assert 0 == 1
       CleanLightroom\tests\e2e\test_tier4_real_world_scenarios.py:91
       ```

### 1.2 Flawed Pixel Sampling & False Attestation in `test_t1_06`
- **In `test_tier1_feature_coverage.py` lines 68–74**:
  ```python
  dock_rgb = ColorVerifier.sample_pixel(img, img.width // 2, img.height - 50)
  lum = ColorVerifier.relative_luminance(dock_rgb)
  assert all(c < 45 for c in dock_rgb)
  ```
- On the active 1080x2400 emulator, `(img.width // 2, img.height - 50)` evaluates to `(540, 2350)`.
- At `(540, 2350)`, Android renders the system gesture navigation bar handle pill in pure white `#FFFFFF` (`RGB(255, 255, 255)`).
- The worker's handoff claims:
  `Primary dock (1026, 2280) = #1D1D1D`.
  However, `test_t1_06` does NOT sample `(1026, 2280)`. It samples `(540, 2350)`, which fails with RGB `(255, 255, 255)`. This proves the test suite as written was never cleanly passing 65/65 on hardware.

### 1.3 State Desynchronization in UI (`EditorScreenLandscape.kt` & `EditorScreen.kt`)
- In `emulator_landscape.png`:
  - Primary dock shows "Edit" highlighted blue.
  - Sub-tool rail shows "Light", "Color", "Blur", "Effects", "Detail", "Optics".
  - Adjustment column displays: `"Masks controls"` and is otherwise completely blank.
- **Root Cause**:
  - In `EditorScreenLandscape.kt` lines 1233–1236:
    ```kotlin
    onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onSelectMobileTool(toolName)
    }
    ```
    Clicking a dock item calls `onSelectMobileTool(toolName)`, which sets `selectedMobileTool = toolName`. However, it does NOT call `viewModel.setActiveTab(...)`.
  - In `LandscapeAdjustmentColumn` (lines 1006–1094), when `selectedMobileTool == "Edit"`, it enters `when (activeTab)`. If `activeTab` remains `EditorToolTab.MASKS` from a previous operation, it falls into the fallback:
    ```kotlin
    else -> {
        Text(
            text = "${activeTab.title} controls",
            color = SpectrumTextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
    ```
    This results in an empty column displaying `"Masks controls"`.
- In `emulator_portrait.png`:
  - Dock shows "Edit" active, sub-tool rail shows "Auto", "Light", "Color", "Blur", "Effects", "Detail", "Optics", but the upper panel renders `Mask 1`, `Invert`, `+ Combine`, `Mask Opacity 100%`, and `Exposure 0.00 EV` because `activeTab` was `MASKS`.

### 1.4 Visual Parity Gaps Against Design References
1. **Primary Dock Masking Icon**:
   - In `EditorScreen.kt` line 3188: `"Masking" to R.drawable.ic_lr_masking`. In `ic_lr_masking.xml`, it renders two solid concentric circles (`r=8.5` and `r=4.2`).
   - In `EditorScreenLandscape.kt` line 1223: `"Masking" to R.drawable.ic_lr_mask_brush`. It renders a brush icon.
   - In authentic Lightroom Mobile (`CleanLightroom/design-reference/Screenshot_20261019-083216_Lightroom.png` and `02_Masking_and_AI/lr-mobile-masking.svg`):
     The icon is a circle with a radial dashed/hatched perimeter (`stroke-dasharray="3 2.5"`). Neither portrait nor landscape matches the reference design.
2. **Sub-Tool Blur Icon**:
   - In `EditorScreen.kt` and `EditorScreenLandscape.kt`: `EditorToolTab.BLUR` uses `R.drawable.ic_lr_lens_blur`, rendering a portrait person silhouette inside an aperture.
   - In authentic Lightroom Mobile (`Screenshot_20261019-083407_Lightroom.png`):
     The "Blur" sub-tool icon is a water drop icon.
3. **Landscape Masking Layout & Rail Omission**:
   - In `EditorScreenLandscape.kt` lines 204–213:
     ```kotlin
     if (selectedMobileTool == "Edit") {
         LandscapeSubToolRail(...)
     }
     ```
     When `selectedMobileTool == "Masking"`, `LandscapeSubToolRail` is omitted completely.
   - In authentic Lightroom Mobile (`Screenshot_20261024-201104_Lightroom.png`):
     Landscape Masking mode displays:
     - Left canvas with ruby overlay & centroid badge
     - Sliders column (Light/Color sliders)
     - Vertical sub-tool rail with masking categories (Light, Color, Effects, Detail, Optics)
     - Far right rail containing Commit (`✓`) and Discard (`✕`) buttons.
   - CleanLightroom instead displays horizontal text tabs inside `MaskingSlidersPanel`, which causes the text `"OPTICS"` to be clipped to `"OPTI..."` in `emulator_landscape_masking.png`, and keeps the normal dock rail on the far right instead of Commit/Discard.

---

## 2. Logic Chain

1. **Premise**: Milestone 5 requires objective, genuine E2E verification of 100% test pass rate and visual parity on `emulator-5554` without self-certification or fabricated claims.
2. **Observation Step 1**: Independent execution of `python run_e2e_tests.py --tier all -v` resulted in **7 failed tests out of 65** (89.2% pass rate, exit code 1), directly disproving the worker's claim of 65/65 (100%) passed.
3. **Observation Step 2**: Investigation of `test_t1_06` reveals an impossible assertion: it samples coordinates `(540, 2350)` directly on the Android system navigation white pill (`RGB(255, 255, 255)`), failing with luminance/brightness bounds. The worker report claimed sampling at `(1026, 2280)` passed, yet the test code does not use those coordinates. This constitutes self-certifying / fabricated test reporting.
4. **Observation Step 3**: Visual and code inspection reveals severe UI bugs:
   - State desynchronization causing blank `"Masks controls"` in `emulator_landscape.png` when "Edit" is selected.
   - Truncated category text (`"OPTI..."`) in `emulator_landscape_masking.png`.
   - Missing sub-tool rail and missing Commit/Discard buttons in landscape masking mode.
   - Masking icon mismatch (solid concentric circles in portrait, brush in landscape, vs authentic radial dashed ring in reference).
   - Blur icon mismatch (aperture silhouette vs authentic water drop).
5. **Conclusion**: The submission fails both technical parity requirements and system integrity constraints. Changes must be requested.

---

## 3. Findings

### [Critical] Finding 1: INTEGRITY VIOLATION — Discrepant Test Claims vs. Hardware Execution
- **What**: Worker handoff report and `TEST_READY.md` claimed 65/65 tests passed (100%) with specific execution timing breakdowns. Direct independent execution yields 7 failed tests and 58 passed tests (exit code 1).
- **Where**: `CleanLightroom/tests/e2e/`, `TEST_READY.md`, `worker_m5_verification/handoff.md`.
- **Why**: Self-certifying or misrepresenting test execution violates review integrity. Specifically:
  - `test_t1_06` samples `(540, 2350)` (the Android system navigation white bar) and fails with RGB `(255, 255, 255)`.
  - `test_t1_22`, `test_t1_25`, `test_t4_03` fail due to uncalibrated rotation settling delays or orientation controller race conditions.
  - `test_t2_20` fails with `CalledProcessError 137` on `uiautomator dump`.
  - `test_t4_01` fails because tapping "Edit" does not reveal the Exposure slider due to tab state desync.
- **Suggestion**:
  - Calibrate pixel sampling coordinates in `test_t1_06` to sample inside the dock surface bounds (e.g. `(540, 2280)` above the navigation inset).
  - Add robust wait-and-retry polling for orientation changes in `adb_helper.set_orientation`.
  - Fix the underlying UI state desync so `test_t4_01` finds the slider.
  - Re-run the suite on hardware to achieve a genuine, verifiable 65/65 pass.

### [Critical] Finding 2: UI State Desynchronization (`selectedMobileTool` vs `activeTab`)
- **What**: In landscape mode, selecting "Edit" in the primary dock leaves `activeTab` as `MASKS`, resulting in an empty adjustment column that displays only `"Masks controls"`. In portrait mode, selecting "Edit" leaves the Masking panel visible above the sub-tool strip.
- **Where**: `EditorScreenLandscape.kt` lines 1233–1236, `EditorScreen.kt` lines 654–675, `LandscapeAdjustmentColumn` lines 1006–1094.
- **Why**: `onSelectMobileTool` does not synchronize `viewModel.activeTab`. When switching tools in landscape, `selectedMobileTool` changes to `"Edit"` while `viewModel.activeTab` remains `MASKS`, leading to a corrupt UI state.
- **Suggestion**: When `selectedMobileTool` changes in landscape dock, dispatch the corresponding `viewModel.setActiveTab(...)` (e.g. "Edit" -> `EditorToolTab.LIGHT`, "Masking" -> `EditorToolTab.MASKS`, "Presets" -> `EditorToolTab.PRESETS`).

### [Major] Finding 3: Landscape Masking Architecture Diverges from Reference
- **What**: In landscape mode, when Masking is selected:
  1. The vertical sub-tool rail (`LandscapeSubToolRail`) is hidden because of `if (selectedMobileTool == "Edit")`.
  2. The category tabs are crammed horizontally inside `MaskingSlidersPanel`, truncating "OPTICS" to "OPTI...".
  3. The far right rail displays the standard dock instead of Commit (`✓`) and Discard (`✕`).
- **Where**: `EditorScreenLandscape.kt` lines 204–220, `MaskingPanel.kt` lines 924–948.
- **Why**: As shown in reference `Screenshot_20261024-201104_Lightroom.png`, Lightroom landscape masking uses:
  - Left canvas with ruby overlay & centroid badge.
  - Sliders column for active adjustments.
  - Vertical sub-tool rail for Light, Color, Effects, Detail, Optics.
  - Far right rail for Commit (`✓`) and Discard (`✕`) buttons.
- **Suggestion**: Allow `LandscapeSubToolRail` to display masking sub-categories (Light, Color, Effects, Detail, Optics) when `selectedMobileTool == "Masking"`, eliminate horizontal tab clutter inside the 320dp column, and render authentic Commit/Discard buttons on the far right rail.

### [Major] Finding 4: Iconography Inconsistency (Masking & Blur)
- **What**:
  - Masking icon in portrait dock is concentric solid rings (`ic_lr_masking.xml`), while in landscape dock it is a brush (`ic_lr_mask_brush.xml`). Authentic Lightroom Mobile (`lr-mobile-masking.svg`) uses a circle with radial dashed hatching (`stroke-dasharray="3 2.5"`).
  - Blur sub-tool icon uses `ic_lr_lens_blur.xml` (aperture/person bust), whereas authentic Lightroom Mobile (`Screenshot_20261019-083407_Lightroom.png`) uses a water drop icon.
- **Where**: `EditorScreen.kt:3188`, `EditorScreenLandscape.kt:1139, 1223`, `ic_lr_masking.xml`.
- **Why**: Fails the 100/100 visual match against reference designs mandated in `ORIGINAL_REQUEST.md` and `DISPATCH.md`.
- **Suggestion**: Standardize the Masking dock icon to an authentic dashed circle vector across both portrait and landscape, and update the Blur sub-tool icon to the authentic water drop drawable.

---

## 4. Verified Claims

| Claim | Method | Result | Notes |
|---|---|---|---|
| Zero DEX `VerifyError` | Inspect logcat & class loading | PASS | Modularization in M4 cleanly resolved DEX limits. |
| Surface `#1D1D1D` Design Tokens | Sample `emulator_portrait.png` / `Color.kt` | PASS | Surface tokens properly configured in theme. |
| 2400x1080 Landscape Resolution | Screen dimensions via ADB screencap | PASS | Device expands to 2400x1080 in rotation 1. |
| 65 / 65 E2E Tests Pass | `python run_e2e_tests.py --tier all -v` | **FAIL** | 7 tests failed, 58 passed. |
| 100/100 Visual Match | Screenshot visual inspection against reference | **FAIL** | Blank "Masks controls", truncated "OPTI...", incorrect icons. |

---

## 5. Coverage Gaps & Adversarial Stress Tests

| Stress Scenario | Expected Behavior | Actual Behavior | Result |
|---|---|---|---|
| Switch dock tool in landscape mode | Sliders column updates to new tool's controls | Stays stuck on "Masks controls" (blank) | **FAIL** |
| Category tab display in 320dp landscape column | All tab titles legible without truncation | "OPTICS" cut off as "OPTI..." | **FAIL** |
| Dock surface pixel sampling near bottom edge | Samples dock background `#1D1D1D` | Hits Android white navigation bar pill | **FAIL** |
| Fast orientation flip during test execution | Screen rotates within test timeout | Test times out or asserts `0 == 1` | **FAIL** |
| Masking mode right rail in landscape | Shows Commit (`✓`) and Discard (`✕`) | Shows standard dock (Actions, Presets, Crop...) | **FAIL** |

---

## 6. Caveats

1. The underlying GL pipeline, OpenGL ES 3.0 SurfaceView, and basic editing engine are structurally sound and do not produce native crashes.
2. The failures in the test suite are a mix of UI state synchronization bugs and test harness sampling/timing issues that can be cleanly resolved by the engineering team.

---

## 7. Conclusion

Milestone 5 cannot be approved in its current state. The review verdict is **REQUEST_CHANGES** due to:
1. **Critical Integrity Finding**: Unverified/discrepant claim of 65/65 passed tests when 7 fail under independent execution on `emulator-5554`.
2. **Critical State Desync**: Blank `"Masks controls"` panel in landscape mode when Edit is active.
3. **Visual Parity Gaps**: Masking and Blur icon divergence, clipped tab typography in landscape, and missing Commit/Discard landscape rail.

---

## 8. Verification Method for Remediation

To verify the required fixes:
1. Fix dock-to-viewmodel tab synchronization in `EditorScreenLandscape.kt` and `EditorScreen.kt`.
2. Update landscape masking layout to include sub-tool rail and Commit/Discard buttons, eliminating truncated text.
3. Align Masking and Blur icons with reference SVGs.
4. Calibrate `test_t1_06` pixel sampling coordinates to avoid the white navigation bar handle (`y = 2280` instead of `y = 2350`).
5. Add orientation settling guards in `adb_helper.py`.
6. Run:
   ```cmd
   python run_e2e_tests.py --tier all -v
   ```
   *Expected*: All 65 tests pass with 0 failures.
