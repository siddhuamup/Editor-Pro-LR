# Dispatch: explorer_m5_it4_masking

## Identity
- Role: Masking UI Elements & Timing Explorer
- Working Directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\explorer_m5_it4_masking\
- Orchestrator: orchestrator_gen2 (940af036-e815-4281-aa60-73e24df6e7c3)

## Task Objective
Investigate the root cause of `test_t1_18_create_mask_modal_sheet_open` and `test_t1_19_mask_creation_sheet_three_tool_groups` failures on `emulator-5554` during the independent Victory Audit execution.
Failure details from `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\victory_auditor_m5\handoff.md`:
```
test_t1_18_create_mask_modal_sheet_open:
  adb.tap_element(fab)
  time.sleep(1.0)
  ui_sheet = adb.dump_ui()
  assert ui_sheet.exists(text="Create new Mask") or ui_sheet.exists(desc="Create new Mask"), (
      "Create new Mask sheet did not open!"
  )
  AssertionError: Create new Mask sheet did not open!
  assert (False or False)

test_t1_19_mask_creation_sheet_three_tool_groups:
  ui = adb.dump_ui()
  assert ui.exists(text="Select Subject") or ui.exists(desc="Select Subject"), "Subject tool missing!"
  AssertionError: Subject tool missing!
  assert (False or False)
```

## Mandatory Inputs to Read
1. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md` (Verbatim user request)
2. `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\victory_auditor_m5\handoff.md` (Full audit evidence report)
3. `CleanLightroom/tests/e2e/test_tier1_feature_coverage.py` lines 180–230 (`test_t1_16`, `test_t1_17`, `test_t1_18`, `test_t1_19`, `test_t1_20`)
4. `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/MaskingPanel.kt` (Create Mask FAB, ModalBottomSheet, tool groups, text strings, content descriptions)
5. `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt` (Masking mode entry, button bindings)

## Scope & Constraints
- Read-only investigation: DO NOT edit source code or test files directly.
- DO NOT recommend cheating, mock returns, or vacuous assertions (assert True).
- Investigate:
  1. In `MaskingPanel.kt`, what are the exact text strings and content descriptions used for:
     - The Create New Mask FAB (`+` button).
     - The Modal Bottom Sheet title ("Create new Mask" vs "Add new mask" vs "Add Mask", etc.).
     - The AI / Subject tool item ("Select Subject" vs "Subject" vs "Subject (AI)", Sky, Background).
     - Other tool groups (Manual tools, Range tools).
  2. Why did `test_t1_18` fail with `time.sleep(1.0)`? Is it an animation latency issue, UIAutomator dump timing, or did the tap miss the FAB due to bounds / scrolling?
  3. Does `test_t1_19` depend on `test_t1_18` keeping the sheet open, or can `test_t1_19` be made self-contained with proper `wait_for_ui` loops?
- Produce a structured handoff report in `handoff.md` in your working directory with verified root cause, logic chain, caveats, and exact fix recipe for the worker.
