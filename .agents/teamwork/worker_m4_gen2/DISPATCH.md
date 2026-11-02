# Task Assignment: Milestone 4 (Replacement Worker: worker_m4_gen2)

## Mission & Architecture Strategy
Implement responsive landscape (horizontal) orientation adaptation matching the 66 landscape reference screenshots in `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/`.

### Key Strategy to Avoid Timeouts:
Do NOT try to rewrite all 4,852 lines of `EditorScreen.kt` in one single edit.
Instead:
1. Create a dedicated composable file: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` containing `EditorScreenLandscape(...)` and its dedicated vertical rail components:
   - `LandscapeTopActionBar` (overlaid transparent top bar on canvas)
   - `LandscapePrimaryToolDock` (vertical rail pinned to right edge: 64dp, `#1D1D1D`, 7 tools: Actions, Presets, Masking, Crop, Edit, Remove)
   - `LandscapeSubToolStrip` (vertical rail beside dock for sub-tools: Light, Color, Effects, Detail, Optics, etc. with active blue indicator and 4dp modified blue dots)
   - `LandscapeAdjustmentColumn` (scrollable column 300..360dp housing active sliders, wheels, curves, or masking panel)
   - `LandscapeDedicatedCommitRail` (vertical rail for commit/discard when in Crop, Grading, Mixer, Blur, etc.)
2. In `EditorScreen.kt`, integrate the orientation branch:
   ```kotlin
   val configuration = LocalConfiguration.current
   val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
   if (isLandscape) {
       EditorScreenLandscape(...)
   } else {
       // Existing portrait Scaffold
   }
   ```

## Context & Inputs
- Authoritative User Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md` (Read this first!)
- Project Specifications & Contracts: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- Design Reference Screenshots: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\horizontal screen UI(same as verticle)\`
- Target Source Files:
  - `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` (New file)
  - `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt` (Hook in the branch)

## Exclusive Write Scope
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt`
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`
- `CleanLightroom/app/src/main/res/drawable/**` (if drawables needed)
Do NOT edit other locked files.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Verification
Worker must build and compile using:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
.\gradlew.bat compileDebugKotlin
```
Ensure build passes with 0 errors.

## Handoff Deliverable
Write your complete report to `.agents/teamwork/worker_m4_gen2/handoff.md` following the Handoff Protocol (Observation, Logic Chain, Caveats, Conclusion, Verification Method).


## 2026-10-29T10:27:52Z
You are assigned as worker_m4_gen2 (UI & Responsive Landscape Adaptation Worker) for Milestone 4 of CleanLightroom Mobile UI Ultra-Refinement.
