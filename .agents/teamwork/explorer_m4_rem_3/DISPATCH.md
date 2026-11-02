# DISPATCH — explorer_m4_rem_3

## Task
You are Explorer 3 for Milestone 4 Integrity Remediation (Verification Plan & Test Conformance).

## Context & Audit Failure Report
The Milestone 4 Gate 5 Forensic Auditor reported an **INTEGRITY VIOLATION**:
- Report file: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\auditor_m4_gate5_gen2\handoff.md`
- Original Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md`
- Project Document: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- Gate Status: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\orchestrator_gen2\GATE_STATUS.md`

## Objectives
1. Read `ORIGINAL_REQUEST.md`, `PROJECT.md`, `TEST_INFRA.md`, and `TEST_READY.md`.
2. Inspect the existing test suite:
   - `LandscapeOrientationAdaptationTest.kt` (6 unit tests covering orientation switch, scroll state, crop fixed spacers, layer selection, slider updates, reset buttons).
   - E2E Test Suite in `tests/` and `run_e2e_tests.py`.
3. Design the exact verification procedure that the Worker, Reviewers, and Forensic Auditor must follow:
   - Step 1: Clean build verification (`compileDebugKotlin`).
   - Step 2: Unit test verification (`testDebugUnitTest`).
   - Step 3: APK assembly and installation on `emulator-5554` (`installDebug`).
   - Step 4: Runtime activity launch and orientation switch verification without `VerifyError`.
4. Deliver your comprehensive test conformance plan in `.agents/teamwork/explorer_m4_rem_3/handoff.md` and notify parent via `send_message`.


## 2026-11-02T17:48:56Z
You are Explorer 3 for Milestone 4 Integrity Remediation (Verification Plan & Test Conformance).
Working directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\explorer_m4_rem_3\
Read DISPATCH.md in your working directory first, as well as:
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md
- c:\Users\Siddhu\Desktop\apps testing\PROJECT.md
- c:\Users\Siddhu\Desktop\apps testing\TEST_INFRA.md
- c:\Users\Siddhu\Desktop\apps testing\TEST_READY.md
- c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\auditor_m4_gate5_gen2\handoff.md

Design the full verification roadmap for Milestone 4 and Milestone 5 (clean compilation, unit tests in LandscapeOrientationAdaptationTest.kt, emulator-5554 installation, and VerifyError-free orientation runtime check). Deliver your test plan in your handoff.md and send_message back to parent.
