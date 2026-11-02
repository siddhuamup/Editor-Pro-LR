# DISPATCH — explorer_m4_rem_2

## Task
You are Explorer 2 for Milestone 4 Integrity Remediation (ART DEX Register Limit & Architecture).

## Context & Audit Failure Report
The Milestone 4 Gate 5 Forensic Auditor reported an **INTEGRITY VIOLATION**:
- Report file: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\auditor_m4_gate5_gen2\handoff.md`
- Original Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md`
- Project Document: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- Gate Status: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\orchestrator_gen2\GATE_STATUS.md`

### Background on Android Runtime DEX Register Overflow:
When running on `emulator-5554`, Android ART threw:
`java.lang.VerifyError: register v273 exceeds 255`
This was caused by the monolithic 2,650-line `EditorScreenLandscape.kt` exceeding the 255 16-bit register limit of Dalvik/ART bytecodes for a single synthetic Compose function.
An external attempt to split sub-panels into `LandscapeAdjustmentPanels.kt` broke compilation.

## Objectives
1. Read `ORIGINAL_REQUEST.md`, `PROJECT.md`, and the auditor handoff.
2. Investigate how Compose functions generate bytecode registers in ART. Analyze how `EditorScreenLandscape.kt` and sub-panel composables (Light, Color, Effects, Detail, Optics, Geometry, Profiles, Presets, Versions, Info) should be modularized.
3. Determine:
   - Should sub-panels live in a dedicated `LandscapeAdjustmentPanels.kt` or multiple modular files?
   - How to ensure clean file ownership boundaries per `PROJECT.md § Code Layout` without introducing symbol conflicts or orphan files.
   - How to avoid ART register overflow while keeping 100% genuine implementation logic (no dummy code, no stubs).
4. Deliver your architectural blueprint in `.agents/teamwork/explorer_m4_rem_2/handoff.md` and notify parent via `send_message`.
