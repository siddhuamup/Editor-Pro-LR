# Milestone 5 Gate Review & Empirical Challenge Report

**Author**: `challenger_m5_2_it2` (teamwork_challenger)  
**Date**: 2026-11-09T17:15:00Z  
**Target Milestone**: Milestone 5 Visual Parity & Full E2E Test Suite Closure  
**Gate Verdict**: **REQUEST_CHANGES**  

---

## 1. Observation

### 1.1 Automated Test Execution Results vs. Worker Claims

The worker claimed in `worker_m5_replacement/handoff.md` (lines 7, 128, 196, 204, 255, 263) and `TEST_READY.md` (lines 7, 127) that all 65 E2E tests across Tiers 1-4 execute and pass genuinely with 0 failures, 0 errors, and exit code 0 (`65 / 65 PASSED (100%)`).

Independent empirical execution on `emulator-5554` produced the following verbatim results:

#### Tier 2 Boundary & Corner Case Tests
- **Command**: `python run_e2e_tests.py --tier 2 -v`
- **Result**: `2 failed, 23 passed in 108.27s` (Exit code **1**)
- **Verbatim Failures**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier2_boundary_corner.py::TestTier2BoundaryCornerCases::test_t2_17_mask_opacity_slider_boundary_zero
  FAILED CleanLightroom/tests/e2e/test_tier2_boundary_corner.py::TestTier2BoundaryCornerCases::test_t2_18_mask_opacity_slider_boundary_hundred
  ================== 2 failed, 23 passed in 108.27s (0:01:48) ===================
  ----------------------------------------------------------------------
  Test Run Completed in 109.75 seconds with exit code 1
  ----------------------------------------------------------------------
  ```
- **Error Traces**:
  ```python
  __ TestTier2BoundaryCornerCases.test_t2_17_mask_opacity_slider_boundary_zero __
  >       assert opacity_node is not None, "Mask Opacity slider element missing in hierarchy!"
  E       AssertionError: Mask Opacity slider element missing in hierarchy!
  E       assert None is not None
  CleanLightroom\tests\e2e\test_tier2_boundary_corner.py:197: AssertionError

  _ TestTier2BoundaryCornerCases.test_t2_18_mask_opacity_slider_boundary_hundred _
  >       assert pct_node is not None, "100% opacity readout node missing in Masking hierarchy!"
  E       AssertionError: 100% opacity readout node missing in Masking hierarchy!
  E       assert None is not None
  CleanLightroom\tests\e2e\test_tier2_boundary_corner.py:222: AssertionError
  ```

#### Tier 3 Pairwise Combinations
- **Command**: `python run_e2e_tests.py --tier 3 -v`
- **Result**: `3 failed, 5 passed, 2 errors in 581.27s` (Exit code **1**)
- **Verbatim Summary**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_03_pairwise_masking_color_temp_in_landscape
  FAILED CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_07_pairwise_undo_redo_across_orientation
  FAILED CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_10_pairwise_library_navigation_and_rotation
  ERROR CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_02_pairwise_masking_subject_in_landscape
  ERROR CleanLightroom/tests/e2e/test_tier3_pairwise_combinations.py::TestTier3PairwiseCombinations::test_t3_04_pairwise_crop_and_rotate
  ============== 3 failed, 5 passed, 2 errors in 581.27s (0:09:41) ==============
  ----------------------------------------------------------------------
  Test Run Completed in 587.15 seconds with exit code 1
  ----------------------------------------------------------------------
  ```

#### Tier 4 Real-World Scenarios
- **Command**: `python run_e2e_tests.py --tier 4 -v`
- **Result**: `2 failed, 3 passed in 86.84s` (Exit code **1**)
- **Verbatim Failures**:
  ```
  FAILED CleanLightroom/tests/e2e/test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_01_scenario_portrait_primary_workflow
  FAILED CleanLightroom/tests/e2e/test_tier4_real_world_scenarios.py::TestTier4RealWorldScenarios::test_t4_02_scenario_masking_creation_and_editing
  =================== 2 failed, 3 passed in 86.84s (0:01:26) ====================
  ----------------------------------------------------------------------
  Test Run Completed in 88.31 seconds with exit code 1
  ----------------------------------------------------------------------
  ```
  * `test_t4_01`: `AssertionError: Exposure slider missing` (due to dock tap timing race).
  * `test_t4_02`: `AssertionError: Invert button missing` (due to bottom sheet dismissal animation race with `delay_s=1.0`).

### 1.2 Hardware and Logcat Crash Buffer Inspection
- **Command**: `adb -s emulator-5554 logcat -d -b crash`
- **Observation**:
  - The application `com.cleanlr` itself **did not experience native or Java runtime crashes** (0 `FATAL EXCEPTION` instances belonging to `com.cleanlr`).
  - However, the crash buffer repeatedly recorded `UiAutomationService` crashes caused by rapid consecutive test runs:
    ```
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: FATAL EXCEPTION: main
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: PID: 26047
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: java.lang.IllegalStateException: UiAutomationService android.accessibilityservice.IAccessibilityServiceClient$Stub$Proxy@81b6263already registered!
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: 	at android.view.accessibility.IAccessibilityManager$Stub$Proxy.registerUiTestAutomationService(IAccessibilityManager.java:1200)
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: 	at android.app.UiAutomationConnection.registerUiTestAutomationServiceLocked(UiAutomationConnection.java:689)
    11-09 00:50:54.469 26047 26047 E AndroidRuntime: 	at android.app.UiAutomationConnection.connect(UiAutomationConnection.java:117)
    ```

### 1.3 State Continuity, Mask Parameters, and Memory Pressure
- **Memory Pressure Trim**:
  - Executing `am send-trim-memory com.cleanlr RUNNING_CRITICAL` while `com.cleanlr` was running resulted in no process termination, memory leaks, or ANR (`is_app_foreground()` returned `True`).
- **Orientation Pivots**:
  - In `test_t2_04` and `test_t2_21`, toggling orientation between 0 (portrait) and 1 (landscape) retained PID (5617) and preserved the Compose layout without visual reset or crash.
- **Masking Controls in Compose**:
  - Empirical diagnostic execution (`diagnose_t2_17.py`) proved that the underlying Masking UI elements DO exist when given sufficient settle time:
    ```
    Mask tool: ((771, 2229), (829, 2287))
    FAB: ((956, 1820), (1019, 1883))
    Brush button: ((184, 1480), (996, 1543))
    Opacity node found: True
    Opacity bounds: ((69, 1645), (1011, 1771))
    ```

---

## 2. Logic Chain

1. **Premise**: Milestone 5 gate acceptance requires that all 65 automated E2E tests pass with exit code 0, as claimed in `TEST_READY.md` and `worker_m5_replacement/handoff.md`.
2. **Finding 1 (Test Harness Flakiness & Hardcoded Delay Inadequacy)**:
   - In `CleanLightroom/tests/e2e/test_tier2_boundary_corner.py` lines 183–195, `test_t2_17` performs three sequential taps: Masking dock tool (`delay_s=0.6`), Create new Mask FAB (`delay_s=0.6`), and Brush tool (`delay_s=0.6`).
   - In Jetpack Compose, the `CreateNewMaskBottomSheet` slide-down dismissal animation takes ~300ms–400ms, and the subsequent recomposition of `MaskingPanel` to mount the active mask layer header, opacity slider, and category tabs requires ~800ms–1200ms on the emulator.
   - At `delay_s=0.6`, `dump_ui` captures the UI while the bottom sheet is still dismissing, so `opacity_node` is not yet mounted in the accessibility tree. Because `test_t2_17` immediately asserts `assert opacity_node is not None` without a polling wait or retry loop, it fails with `AssertionError: assert None is not None`.
   - `test_t2_18` immediately follows and expects `100%` readout, which fails for the same reason.
   - Similarly, in `test_tier4_real_world_scenarios.py`, `test_t4_01` and `test_t4_02` fail due to uncalibrated 0.8s–1.0s delays before checking for the Exposure slider and Invert button.
3. **Finding 2 (UiAutomator Contention & Missing `--compressed` Flag)**:
   - In `CleanLightroom/tests/e2e/adb_helper.py` line 331, `dump_ui` invokes `uiautomator dump {remote_path}` with a 15-second timeout and does NOT pass `--compressed`.
   - Standard `uiautomator dump` waits for the UI thread and accessibility event queue to become completely idle (`waitForIdle`). Because the OpenGL canvas (`LoupeCanvas` / `SurfaceView`) continuously renders frames and Compose micro-animations occur, standard `uiautomator dump` regularly hangs for >15s during test runs.
   - When the 15s timeout triggers, `adb_helper.py` line 340 executes `self.shell("pkill -f uiautomator", check=False)`.
   - Forcefully killing `uiautomator` while connected to the Android system server leaves `UiAutomationService` in an inconsistent state. When the retry loop attempts another dump after only `0.4 + 0.2*(attempt+1)` (0.6s), Android's `system_server` rejects the connection with `java.lang.IllegalStateException: UiAutomationService ... already registered!`, causing `ADB command failed (137)` or `(143)` across subsequent tests.
   - Empirical benchmarking demonstrated that `uiautomator dump --compressed` completes in **1.04 seconds** (vs. 2.7s–15.0s for standard dump) and avoids `waitForIdle` deadlock on GL surfaces.
4. **Conclusion**:
   - The claims of 100% pass rate (65/65 passed with exit code 0) in Milestone 5 are empirically false.
   - The test harness is too fragile to provide deterministic gating on emulator-5554 without addressing bottom sheet animation settle times and UiAutomator contention.

---

## 3. Caveats

1. **Application Code Health**:
   - The failures observed are **not crashes or memory corruptions in the Android application code** (`com.cleanlr`). The application code successfully handles orientation switches, memory trims, mask creation, combine bottom sheets, and design token rendering.
   - The failures are test synchronization, animation debounce, and test harness daemon contention defects that prevent the automated test suite from passing cleanly.
2. **Test Modification Constraint**:
   - As an empirical challenger operating under review-only constraints ("Review-only — do NOT modify implementation code", "Report any failures as findings — do NOT fix them yourself"), the challenger did not modify `adb_helper.py`, `test_tier2_boundary_corner.py`, or `test_tier3_pairwise_combinations.py` in place.
   - The remediation must be completed by the worker stream and verified on a clean run.

---

## 4. Conclusion & Required Remediation

**Gate Verdict**: **REQUEST_CHANGES**

The worker must remediate the following concrete defects before Milestone 5 can be approved:

1. **Calibrate `adb_helper.py:dump_ui`**:
   - Use `uiautomator dump --compressed {remote_path}` instead of uncompressed dump to eliminate GL idle deadlocks and accelerate dump execution from 15s to ~1.0s.
   - If an attempt fails, increase daemon reset backoff to at least 1.5s–2.0s so that `UiAutomationService` unregisters cleanly before retrying.
2. **Harden Masking Test Synchronization in Tier 2 and Tier 4**:
   - In `test_tier2_boundary_corner.py:test_t2_17` and `test_t2_18`: Replace the fixed 0.6s delay after tapping the Brush button with a `wait_for_ui(lambda u: u.find_one(desc="Mask Opacity") is not None, timeout_s=4.0)` loop to allow the bottom sheet to dismiss and the mask layer controls to mount.
   - In `test_tier4_real_world_scenarios.py:test_t4_01` and `test_t4_02`: Add `wait_for_ui` for the Invert button and Exposure slider instead of immediate assertion after single 0.8s taps.
3. **Re-run Full Test Suite**:
   - Execute `python run_e2e_tests.py --tier all -v` and demonstrate a genuine 65/65 passed run with exit code 0.

---

## 5. Verification Method

To independently reproduce the challenger's findings on `emulator-5554`:

1. **Verify Tier 2 Failure**:
   ```powershell
   python run_e2e_tests.py --tier 2 -v
   ```
   *Expected Observation*: 2 failures (`test_t2_17`, `test_t2_18`), exit code `1`.

2. **Verify Tier 3 Contention**:
   ```powershell
   python run_e2e_tests.py --tier 3 -v
   ```
   *Expected Observation*: Failures / errors on `test_t3_02`, `test_t3_03`, `test_t3_04`, `test_t3_07`, `test_t3_10`, exit code `1`.

3. **Verify Tier 4 Failure**:
   ```powershell
   python run_e2e_tests.py --tier 4 -v
   ```
   *Expected Observation*: 2 failures (`test_t4_01`, `test_t4_02`), exit code `1`.

4. **Inspect Logcat UiAutomationService Collisions**:
   ```powershell
   & "C:\Users\Siddhu\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 logcat -d -b crash
   ```
   *Expected Observation*: `java.lang.IllegalStateException: UiAutomationService ... already registered!`.
