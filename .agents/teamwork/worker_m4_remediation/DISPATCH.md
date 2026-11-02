# Task Assignment: Milestone 4 Remediation — Primary Dock Vertical Scroll in EditorScreenLandscape.kt

## Identity
- Name: worker_m4_remediation
- Role: UI Remediation Worker
- Working Directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m4_remediation\
- Parent: orchestrator_gen2 (Conversation ID: 940af036-e815-4281-aa60-73e24df6e7c3)

## Objective
Apply the specific touch boundary clipping fix identified by `challenger_m4_2` in `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`.

## Inputs & Context Files (Read before coding)
1. Authoritative User Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md` (MANDATORY: Read first)
2. Project Scope: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
3. Challenger Feedback Report: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\challenger_m4_2\handoff.md`
4. Target File to Edit: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`

## Exclusive Write Scope
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`
- `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m4_remediation\**`
- FORBIDDEN: Do NOT touch any other source or configuration files.

## Specific Task Instructions
In `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` (around lines 1104–1111):
Locate the Primary Dock Rail Column:
```kotlin
// 2C. PRIMARY DOCK VERTICAL RAIL (~64dp width)
Column(
    modifier = Modifier
        .width(64.dp)
        .fillMaxHeight()
        .background(SpectrumSurface)
        .navigationBarsPadding()
        .padding(vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
)
```
Add `.verticalScroll(rememberScrollState())` to the modifier chain (exactly as done on line 1035 for the Sub-tool rail):
```kotlin
// 2C. PRIMARY DOCK VERTICAL RAIL (~64dp width)
Column(
    modifier = Modifier
        .width(64.dp)
        .fillMaxHeight()
        .background(SpectrumSurface)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
)
```
Ensure that `rememberScrollState` is imported from `androidx.compose.foundation.rememberScrollState` and `verticalScroll` is imported from `androidx.compose.foundation.verticalScroll`.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Verification
Execute the compilation command and verify it passes with 0 errors:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
.\gradlew.bat compileDebugKotlin
```

## Completion Criteria
1. `EditorScreenLandscape.kt` modified to include `.verticalScroll(rememberScrollState())` on the Primary Dock Column.
2. `./gradlew.bat compileDebugKotlin` succeeds with 0 errors.
3. Detailed `handoff.md` created in your working directory following Handoff Protocol.
4. Notify orchestrator via `send_message`.

