# Task Assignment: Milestone 4 (Replacement Worker: worker_m4_gen3)

## Objective & Priority
Implement responsive landscape (horizontal) orientation adaptation matching the 66 landscape reference screenshots in `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/`.

## Architecture Strategy (CRITICAL TO AVOID TIMEOUT):
1. **DO NOT rewrite or edit all 4,852 lines of `EditorScreen.kt` in one massive chunk**.
2. **Create a new companion file**: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`.
3. In `EditorScreenLandscape.kt`, define:
   ```kotlin
   @Composable
   fun EditorScreenLandscape(
       viewModel: EditorViewModel,
       onBack: (() -> Unit)?,
       modifier: Modifier = Modifier
   )
   ```
   Together with all its landscape composables:
   - `LandscapeTopActionBar`: overlaid on canvas (Back, Undo/Redo with history popup, Compare before/after, Share/Export, Mask overlay toggle, Overflow menu).
   - `LandscapeLoupeCanvas`: left workspace preview canvas (`weight(1f)`), with support for image rendering, zoom/pan gestures, and `FloatingMaskDock` when in Masking mode.
   - `LandscapeAdjustmentColumn`: scrollable column (`width(330.dp)`) rendering active adjustment panels (Light, Color, Curves, HSL Mixer, Color Grading, Effects, Detail, Optics, Blur, Crop, Presets, Actions, Masking adjustments, Remove).
   - `LandscapeSubToolRail`: vertical rail (`width(56.dp)`) for sub-tools (Auto, Light, Color, Blur, Effects, Detail, Optics, Profiles) showing authentic 24dp icons, titles, and 4dp blue indicator dots (`#2680EB`) on modified tabs.
   - `LandscapePrimaryToolDock`: vertical rail (`width(64.dp)`) pinned to right edge housing the 6 primary tools (Actions, Presets, Crop, Edit, Masking, Remove) with active blue container (`#1473E6`) and labels.
   - `LandscapeDedicatedCommitRail`: vertical rail with Discard (`ic_lr_close`), Title, and Commit (`ic_lr_select_checkmark`) used during dedicated sub-modes (Crop, Blur, Curves, Mixer, Grading).
4. In `EditorScreen.kt`, introduce the orientation check at the root:
   ```kotlin
   val configuration = LocalConfiguration.current
   val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

   if (isLandscape) {
       EditorScreenLandscape(
           viewModel = viewModel,
           onBack = onBack,
           modifier = modifier
       )
   } else {
       // Existing portrait Scaffold content remains exactly as is
   }
   ```
5. Run Gradle compile to verify:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
   .\gradlew.bat compileDebugKotlin
   ```
6. Write handoff report to `.agents/teamwork/worker_m4_gen3/handoff.md` and message the orchestrator.

## Exclusive Write Scope
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` (New file)
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt` (Hook in the branch)
- `CleanLightroom/app/src/main/res/drawable/**` (if any extra drawables needed)
Do NOT touch other locked files.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.
