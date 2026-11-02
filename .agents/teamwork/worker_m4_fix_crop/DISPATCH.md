# Task Assignment: Fix Crop Mode Spacer.weight in EditorScreenLandscape.kt

## Identity
- Name: worker_m4_fix_crop
- Role: Compose Layout Bugfix Worker
- Working Directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m4_fix_crop\
- Parent: orchestrator_gen2 (Conversation ID: 940af036-e815-4281-aa60-73e24df6e7c3)

## Objective
Fix the Compose runtime exception hazard in `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` where `Spacer(Modifier.weight(1f))` is used inside the scrollable Primary Dock column during Crop mode (lines 1115 & 1146).

## Specific Fix
In `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`:
In the Primary Dock column (around lines 1115–1150) under `if (selectedMobileTool == "Crop")`:
Replace `Spacer(modifier = Modifier.weight(1f))` (or `Spacer(Modifier.weight(1f))`) with `Spacer(modifier = Modifier.height(32.dp))` so that `Modifier.weight` is never evaluated inside a vertically scrollable container.

## Verification
Execute compilation and unit tests:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest --tests com.cleanlr.ui.editor.LandscapeOrientationAdaptationTest
```

## Completion Criteria
1. `Modifier.weight(1f)` removed from inside the scrollable column in `EditorScreenLandscape.kt`.
2. Both compilation and tests pass cleanly with 0 errors.
3. Handoff report created and message sent to parent.


## 2026-11-02T08:03:34Z
**Context**: Milestone 4 Fix Crop
**Content**: Status check on spacer replacement and test execution.
**Action**: Please report status or finish handoff.
