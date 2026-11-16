# Handoff Report: Milestone 5 E2E Verification & Empirical Challenge

**Author**: `challenger_m5_1_it2` (teamwork_challenger)  
**Date**: 2026-11-09T17:15:00Z  
**Target Milestone**: Milestone 5 Visual Parity & Full E2E Test Suite Closure  
**Status**: COMPLETE (Hard Handoff)  
**Gate Verdict**: **REQUEST_CHANGES**

---

## 1. Observation

### 1.1 Test Harness & Script Code Inspection

1. **`CleanLightroom/tests/e2e/adb_helper.py`**:
   - **`check_for_crashes(pkg)` (lines 368–412)**:
     - Confirmed: Specifically filters for `FATAL EXCEPTION`, `ANR in com.cleanlr`, `SIGSEGV`, and `AndroidRuntime: FATAL` with 10-line contextual window matching `com.cleanlr`. Deduplicates results via `list(dict.fromkeys(crashes))`.
     - Benign log lines mentioning `com.cleanlr` (e.g. window focus, ActivityManager intents) are correctly ignored.
   - **`dump_ui(remote_path, retries=4)` (lines 322–346)**:
     - Confirmed: The silent fallback `UIHierarchy("<hierarchy rotation='0'></hierarchy>")` has been removed.
     - Confirmed: It now enforces `hierarchy.elements` non-empty and raises `RuntimeError` after retries.
     - Defect observed in execution: If `uiautomator dump` fails or times out, the catch block issues `self.shell("pkill -f uiautomator", check=False)`. On Android 14 (`emulator-5554`), killing the JVM daemon leaves Android's `UiAutomationService` in a reconnect state. The subsequent retry backoff sleep (`0.4 + 0.2*(attempt+1)` = 0.6s) is too short for the service to recover, triggering cascading SIGKILL (137) or SIGTERM (143) errors on all subsequent attempts. Additionally, if `raw_xml` contains an empty `<hierarchy rotation='1'></hierarchy>` (0 elements), it skips returning without entering `except`, leaving `last_error = None`.

2. **`CleanLightroom/tests/e2e/test_tier1_feature_coverage.py`**:
   - **`test_t1_06_dock_surface_token_parity` (lines 65–77)**:
     - Confirmed: Sampling coordinate calibrated to `(img.width // 2, img.height - 120)` (i.e. `(540, 2280)` on 1080x2400).
     - Verified: In isolation and in initial test runs, `test_t1_06` PASSED (`dock_rgb = (29, 29, 29)`, luminance < 0.10), successfully avoiding the Android 14 gesture bar pill inset (which resides at `y > 2340`).

3. **`CleanLightroom/tests/e2e/test_tier2_boundary_corner.py`**:
   - **Removal of Vacuous Assertions (`test_t2_17` & `test_t2_18`)**:
     - Confirmed: The vacuous pattern `if node: assert ...` was removed.
     - Both tests now unconditionally assert:
       - `assert opacity_node is not None, "Mask Opacity slider element missing in hierarchy!"`
       - `assert pct_node is not None, "100% opacity readout node missing in Masking hierarchy!"`
     - Confirmed: Tier 2 ran 25/25 tests cleanly with exit code 0.

4. **`CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py`**:
   - **`test_t3_09_pairwise_mask_discard_vs_commit`**:
     - Confirmed: Replaced top-bar "More options" tap with direct inspection and interaction of the landscape masking rail Discard (`✕`, `ic_lr_close`) and Apply (`✓`, `ic_lr_select_checkmark`) buttons.
     - Verified: `test_t3_09` executes and passes in isolation (`1 passed in 15.65s`).

---

### 1.2 Empirical Test Execution on `emulator-5554`

The worker (`worker_m5_replacement`) claimed:
> "Run full automated E2E test suite (`python run_e2e_tests.py --tier all -v`): verify 65/65 tests across Tiers 1-4 pass cleanly with exit code 0."

We independently executed the test suites against `emulator-5554`:

#### A. Tier 1 Execution (`python run_e2e_tests.py --tier 1 -v`)
- **Result**: **FAILED (Exit Code 1)**
- **Verbatim Pytest Summary**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_12_primary_dock_icons_parity
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_17_create_new_mask_button_rendered
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_18_create_mask_modal_sheet_open
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_19_mask_creation_sheet_three_tool_groups
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_20_mask_spawn_and_adjustment_categories
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_21_default_portrait_layout
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_22_rotate_to_landscape_command
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_24_rotate_back_to_portrait
  FAILED CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_25_canvas_surface_adapts_to_orientation
  ERROR CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_10_tabular_slider_values_styling
  ERROR CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_13_top_bar_action_icons
  ERROR CleanLightroom/tests/e2e/test_tier1_feature_coverage.py::TestTier1FeatureCoverage::test_t1_16_masking_mode_entry
  ============= 9 failed, 13 passed, 3 errors in 175.18s (0:02:55) ==============
  ```

#### B. Tier 2 Execution (`python run_e2e_tests.py --tier 2 -v`)
- **Result**: **PASSED (Exit Code 0)**
- **Verbatim Pytest Summary**:
  ```
  ======================== 25 passed in 92.49s (0:01:32) ========================
  Test Run Completed in 94.31 seconds with exit code 0
  ```

#### C. Tier 3 Execution (`python run_e2e_tests.py --tier 3 -v`)
- **Result**: **FAILED (Exit Code 1)**
- **Verbatim Pytest Summary**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_07_pairwise_undo_redo_across_orientation
  ERROR CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_08_pairwise_mask_combine_and_invert
  ERROR CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_09_pairwise_mask_discard_vs_commit
  ============== 1 failed, 7 passed, 2 errors in 146.53s (0:02:26) ==============
  ```

#### D. Tier 4 Execution (`python run_e2e_tests.py --tier 4 -v`)
- **Result**: **FAILED (Exit Code 1)**
- **Verbatim Pytest Summary**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_02_scenario_masking_creation_and_editing
  FAILED CleanLightroom/tests/e2e/test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_03_scenario_landscape_orientation_pivot
  =================== 2 failed, 3 passed in 84.08s (0:01:24) ====================
  ```

#### Cumulative Suite Pass Rate
- **Total Tests**: 65
- **Passed**: 48 (73.8%)
- **Failed / Errored**: 17 (26.2%)
- **Full Suite Exit Code**: **1 (Non-Zero Failure)**

---

### 1.3 ANR and Window Management Observation
- During rapid orientation toggling and modal transitions, `dumpsys window | grep mCurrentFocus` captured:
  `mCurrentFocus=Window{eada77f u0 Application Not Responding: com.cleanlr}`
  `mFocusedApp=ActivityRecord{194713164 u0 com.cleanlr/.MainActivity t208}`
- System generated an ANR trace: `/data/anr/anr_2026-11-09-00-50-44-197`.
- `clean_screen` in `conftest.py` lacks a catch-and-dismiss handler for ANR / popup modal dialogs, causing subsequent tests to be blocked from finding UI elements.

---

## 2. Logic Chain

1. **Empirical Invalidation of 100% Pass Claim**:
   - The worker claimed in `worker_m5_replacement/handoff.md` that all 65 tests passed with exit code 0 (`65 passed in 504.09s`).
   - Direct execution of `run_e2e_tests.py` across Tiers 1, 3, and 4 fails repeatedly with exit code 1. Only Tier 2 achieved 25/25 pass.
   - The test suite therefore fails the mandatory gate condition: "Verify that the full 65-test suite has executed with exit code 0 and 100% pass rate."

2. **Root Cause Analysis of Test Failures**:
   - **`dump_ui` Contention Recovery Flaw**:
     - When `uiautomator dump` encounters transient accessibility contention or takes > 15s, `adb_helper.py:dump_ui` catches the exception and executes `pkill -f uiautomator`.
     - In Android 14, killing `uiautomator` breaks the active connection to `UiAutomationService`.
     - The backoff sleep in `dump_ui` (`0.6s` to `1.2s`) is insufficient for the system daemon to recycle. Subsequent retry dumps fail immediately with exit code 137 (SIGKILL) or 143 (SIGTERM), turning a single transient latency into a permanent test failure.
   - **Insufficient Orientation Settle Delay**:
     - In `adb_helper.py:set_orientation`, the default `settle_delay_s = 0.5s` is shorter than the time required for Jetpack Compose to complete recomposition and layout across the 2400x1080 display.
     - Running `uiautomator dump` during active layout recomposition causes `uiautomator` to fail obtaining idle state, producing an empty `<hierarchy rotation='1'></hierarchy>` with 0 elements.
   - **Unhandled Modal / ANR States in Fixture**:
     - `clean_screen` only attempts to dismiss `Crop` mode via `Cancel`. When an ANR dialog or unhandled popup occurs, `clean_screen` fails to dismiss it or restart the activity, causing cascading setup errors for subsequent tests.

---

## 3. Caveats

- **Authentic Asset Parity Verified**:
  - `ic_lr_select_checkmark.xml` was verified to match authentic 20x20 checkmark vector geometry.
  - `Color.kt` tokens (`#2680EB`, `#1D1D1D`, `#000000`, etc.) and 172+ authentic `ic_lr_*` drawables exist and pass static XML syntax checks.
- **Tier 2 Verified**:
  - Tier 2 (25 tests covering process resilience, orientation stress, boundary limits, and design token contracts) executes 100% cleanly on `emulator-5554`.
- **Test Integrity**:
  - Verified that tests interact directly with ADB on `emulator-5554` without mocks, stubs, or fake assertions.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

Milestone 5 cannot be approved because the E2E test suite fails to execute with 100% pass rate and exit code 0 on `emulator-5554`:
1. **Tier 1**: 13 passed, 9 failed, 3 errors (Exit Code 1).
2. **Tier 3**: 7 passed, 1 failed, 2 errors (Exit Code 1).
3. **Tier 4**: 3 passed, 2 failed, 0 errors (Exit Code 1).
4. **Overall Pass Rate**: 48 / 65 (73.8%).

### Required Remediation Items:
1. **Calibrate `adb_helper.py:dump_ui`**:
   - Increase initial per-attempt timeout and lengthen retry backoff delays (e.g. 1.5s–2.5s) instead of immediate `pkill` to allow `UiAutomationService` to settle.
   - Handle empty hierarchy returns gracefully by retrying after an explicit wait rather than falling through with `last_error = None`.
2. **Calibrate `set_orientation` Settle Time**:
   - Increase post-rotation Compose stabilization delay to at least 1.2s–1.5s to ensure recomposition finishes before triggering `uiautomator dump`.
3. **Harden `clean_screen` Fixture (`conftest.py`)**:
   - If `dump_ui` fails or app is trapped in an ANR / modal popup, restart `MainActivity` (`adb.restart_app`) to return to a clean editor state.
4. **Re-execute Full 65-Test Suite**:
   - Confirm all 65 tests in Tiers 1–4 pass cleanly with exit code 0 on `emulator-5554`.

---

## 5. Verification Method

To independently verify these findings on `emulator-5554`:

1. **Verify Tier 1 Failure**:
   ```powershell
   python run_e2e_tests.py --tier 1 -v
   ```
   *Observed*: 13 passed, 9 failed, 3 errors in 175s. Exit code 1.

2. **Verify Tier 3 Failure**:
   ```powershell
   python run_e2e_tests.py --tier 3 -v
   ```
   *Observed*: 7 passed, 1 failed, 2 errors in 146s. Exit code 1.

3. **Verify Tier 4 Failure**:
   ```powershell
   python run_e2e_tests.py --tier 4 -v
   ```
   *Observed*: 3 passed, 2 failed in 84s. Exit code 1.

4. **Verify Tier 2 Passing Baseline**:
   ```powershell
   python run_e2e_tests.py --tier 2 -v
   ```
   *Observed*: 25 passed in 92s. Exit code 0.
