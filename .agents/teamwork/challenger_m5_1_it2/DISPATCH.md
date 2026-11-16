# Dispatch Instructions — challenger_m5_1_it2

You are `challenger_m5_1_it2` (teamwork_challenger).
Your working directory: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\`

## Mandatory Inputs (read before starting):
- `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md`
- `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- `c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md`
- `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_replacement\handoff.md`

## Your Task: E2E Automation & Harness Verification
1. Inspect the E2E test harness and test scripts:
   - `CleanLightroom/tests/e2e/adb_helper.py`: verify `check_for_crashes` filters specifically for real crashes (`FATAL EXCEPTION`, `ANR in com.cleanlr`, `SIGSEGV`, `AndroidRuntime: FATAL`) with contextual checks; verify `dump_ui` contention recovery and strict non-empty UI hierarchy check.
   - `CleanLightroom/tests/e2e/test_tier1_feature_coverage.py`: verify `test_t1_06` sampling coordinate (`height - 120`) avoiding Android 14 gesture bar pill.
   - `CleanLightroom/tests/e2e/test_tier2_boundary_corner.py`: verify removal of vacuous assertions (`if node: assert ...` converted to unconditional validation with `wait_for_ui`).
2. Verify test execution on `emulator-5554`:
   - Run `python run_e2e_tests.py --tier 1 -v` (verify 25/25 pass).
   - Verify that the full 65-test suite has executed with exit code 0 and 100% pass rate.
3. Verify that all 65 tests genuinely execute on `emulator-5554` without skips, mocks, or bypasses.
4. Output your gate verdict: **APPROVE** or **REQUEST_CHANGES** in `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\handoff.md` and message parent (conversation ID: 940af036-e815-4281-aa60-73e24df6e7c3).

## 2026-11-08T18:58:02Z
You are challenger_m5_1_it2 (teamwork_challenger).
Your working directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\

Read your dispatch instructions in:
c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\DISPATCH.md

MANDATORY INPUTS (read before starting):
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md
- c:\Users\Siddhu\Desktop\apps testing\PROJECT.md
- c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m5_replacement\handoff.md

TASK:
1. Inspect the E2E test harness and test scripts:
   - CleanLightroom/tests/e2e/adb_helper.py: verify check_for_crashes filters specifically for real crashes (FATAL EXCEPTION, ANR in com.cleanlr, SIGSEGV, AndroidRuntime: FATAL) with contextual checks; verify dump_ui contention recovery and strict non-empty UI hierarchy check.
   - CleanLightroom/tests/e2e/test_tier1_feature_coverage.py: verify test_t1_06 sampling coordinate (height - 120) avoiding Android 14 gesture bar pill.
   - CleanLightroom/tests/e2e/test_tier2_boundary_corner.py: verify removal of vacuous assertions (if node: assert ... converted to unconditional validation with wait_for_ui).
2. Verify test execution on emulator-5554:
   - Run python run_e2e_tests.py --tier 1 -v (verify 25/25 pass).
   - Verify that the full 65-test suite has executed with exit code 0 and 100% pass rate.
3. Verify that all 65 tests genuinely execute on emulator-5554 without skips, mocks, or bypasses.
4. Document your findings and deliver your gate verdict (APPROVE or REQUEST_CHANGES) to:
   c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m5_1_it2\handoff.md
   and notify parent via send_message(Recipient="940af036-e815-4281-aa60-73e24df6e7c3", Message=...).

## 2026-11-08T19:18:59Z
Context: Milestone 5 Challenger 1 verification following server restart.
Content: Please report your current status, whether your execution is still running, or if you have completed and need to write handoff.md.
Action: Reply with your current progress and status.

## 2026-11-09T16:48:38Z
Context: Milestone 5 Challenger 1 verification following server restart.
Content: The updated debug APK has deployed, ic_lr_select_checkmark.xml is updated, and test_t3_09 is hardened.
Action: Please verify the hardened E2E suite / test_t3_09 on emulator-5554, finalize your findings, write handoff.md, and report your gate verdict.
