# Dispatch: worker_m5_harness_final

You are the Milestone 5 Final Harness & E2E Verification Worker (`teamwork_worker`).
Your working directory is: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_harness_final\`

## Mandatory Inputs (Read First)
1. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md`
2. `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
3. `c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md`
4. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\handoff.md`
5. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_2_it2\handoff.md`
6. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\reviewer_m5_1_it2\handoff.md`
7. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\auditor_m5_it2\handoff.md`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## File Ownership
You have exclusive write ownership over:
- `CleanLightroom/tests/e2e/adb_helper.py`
- `CleanLightroom/tests/e2e/conftest.py`
- `CleanLightroom/tests/e2e/test_tier2_boundary_corner.py`
- `CleanLightroom/tests/e2e/test_tier4_real_world_scenarios.py`
- Your working directory: `.agents/teamwork/worker_m5_harness_final/`

DO NOT modify build scripts, AndroidManifest.xml, or any files in `res/drawable/`.

## Tasks
1. Calibrate `adb_helper.py`:
   - In `dump_ui`: use `uiautomator dump --compressed /sdcard/e2e_uidump.xml`. Increase dump timeout from 15s to 25s. Do NOT call `pkill` on transient failures; use 1.5s–2.5s backoff between retries. If `raw_xml` returns 0 elements, set `last_error = RuntimeError(...)` and retry with backoff.
   - In `set_orientation`: set default `settle_delay_s = 1.5s` to allow Compose to finish recomposition across 2400x1080 display.
2. Harden `conftest.py`:
   - In `clean_screen`: if app is stuck in an ANR or unhandled popup, restart MainActivity (`adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)`).
3. Synchronize tests:
   - In `test_tier2_boundary_corner.py` (`test_t2_17` & `test_t2_18`): add `wait_for_ui` to ensure sheet dismissal finishes before slider lookup.
   - In `test_tier4_real_world_scenarios.py` (`test_t4_01` & `test_t4_02`): add `wait_for_ui` for tool sheet transitions.
4. Verify execution on `emulator-5554`:
   - Ensure `emulator-5554` is responsive (`adb devices`).
   - Run master E2E test runner:
     `python run_e2e_tests.py --tier all -v`
     Verify 65/65 tests across all 4 tiers pass with exit code 0.
   - Run JVM unit tests:
     `.\gradlew.bat testDebugUnitTest`
     Verify 100% pass rate.
5. Write complete 5-component handoff report to `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_harness_final\handoff.md` and message parent when complete.

## 2026-11-09T17:39:06Z
Received dispatch message:
Objectives:
1. Calibrate `CleanLightroom/tests/e2e/adb_helper.py`:
   - In `dump_ui`: use `uiautomator dump --compressed /sdcard/e2e_uidump.xml`. Increase dump timeout from 15s to 25s. Do NOT call `pkill` on transient failures; use 1.5s–2.5s backoff between retries. If `raw_xml` returns 0 elements, set `last_error = RuntimeError(...)` and retry with backoff.
   - In `set_orientation`: set default `settle_delay_s = 1.5s` to allow Compose to finish recomposition across 2400x1080 display.
2. Harden `CleanLightroom/tests/e2e/conftest.py`:
   - In `clean_screen`: if app is stuck in an ANR or unhandled popup, restart MainActivity (`adb.restart_app(PACKAGE_NAME, MAIN_ACTIVITY, wait_s=1.5)`).
3. Synchronize tests:
   - In `test_tier2_boundary_corner.py` (`test_t2_17` & `test_t2_18`): add `wait_for_ui` to ensure sheet dismissal finishes before slider lookup.
   - In `test_tier4_real_world_scenarios.py` (`test_t4_01` & `test_t4_02`): add `wait_for_ui` for tool sheet transitions.
4. Verify execution on `emulator-5554`:
   - Ensure `emulator-5554` is responsive (`adb devices`).
   - Run master E2E test runner:
     `python run_e2e_tests.py --tier all -v`
     Verify all 65 tests across all 4 tiers pass cleanly with exit code 0.
   - Run JVM unit tests:
     `.\gradlew.bat testDebugUnitTest`
     Verify 100% pass rate.
5. Write complete 5-component handoff report to `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_harness_final\handoff.md` and message parent when complete.
