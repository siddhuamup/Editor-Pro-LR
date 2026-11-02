# Task Assignment: Milestone 4 (worker_m4_gen4)

## Objective & Direct Execution Plan
Implement responsive landscape (horizontal) orientation adaptation matching the 66 landscape reference screenshots in `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/`.

## Strict Strategy to avoid any timeout:
Do NOT spend turns searching through large files or viewing lots of images. Everything you need is specified here.
1. Create `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`.
2. Implement:
   - `EditorScreenLandscape(viewModel: EditorViewModel, onBack: (() -> Unit)?, modifier: Modifier = Modifier)`
   - `LandscapeTopActionBar`: Left-aligned back button (`ic_lr_back_chevron`), undo/redo (`ic_lr_undo`, `ic_lr_redo`), compare before/after (`ic_lr_compare_before_after`), share/export (`ic_lr_share_export`), mask eye toggle (`ic_lr_mask_hide_eye`), help (`ic_lr_context_help`), and overflow menu (`ic_lr_more_vert`).
   - Left preview workspace (`weight(1f)`): LoupeCanvas with `FloatingMaskDock` when in Masking mode.
   - Right adjustment workspace:
     - `LandscapeAdjustmentColumn`: scrollable `width(330.dp)` column rendering tool adjustment controls:
       - For `selectedMobileTool == "Edit"`:
         - When `activeTab == EditorToolTab.LIGHT`: exposure, contrast, highlights, shadows, whites, blacks sliders + Curve button.
         - When `activeTab == EditorToolTab.COLOR`: temp, tint, vibrance, saturation sliders + B&W toggle, Color Mixer button, Color Grading button.
         - When `activeTab == EditorToolTab.EFFECTS`: texture, clarity, dehaze, vignette sliders.
         - When `activeTab == EditorToolTab.DETAIL`: sharpening, noise reduction sliders.
         - When `activeTab == EditorToolTab.OPTICS`: chromatic aberration, lens correction toggles.
       - For `selectedMobileTool == "Masking"`:
         - `MaskingSlidersPanel` (Light, Color, Effects, Detail, Optics tabs with local sliders, opacity slider, invert).
       - For `selectedMobileTool == "Crop"`:
         - Aspect ratio controls, straighten angle dial, rotate 90°, flip.
       - For `selectedMobileTool == "Presets"`:
         - `PresetsPanel` with preset list.
       - For `selectedMobileTool == "Actions"`:
         - Auto, Enhance, Subject, Sky, Background quick action buttons.
       - For `selectedMobileTool == "Remove"`:
         - Generative remove brush size, refine button, remove button.
     - `LandscapeSubToolRail` (`width(56.dp)`): vertical rail displaying sub-tool buttons with 24dp icons, titles, and 4dp blue indicator dots (`#2680EB`) for modified tabs.
     - `LandscapePrimaryToolDock` (`width(64.dp)`): pinned to far right edge housing Actions, Presets, Crop, Edit, Masking, Remove with active blue background (`#1473E6`) and labels.
     - `LandscapeDedicatedCommitRail` (`width(64.dp)`): Discard (`ic_lr_close`), mode Title, Commit (`ic_lr_select_checkmark`) used during dedicated sub-modes (Crop, Blur, Curves, Mixer, Grading).
3. In `EditorScreen.kt`, add the orientation check near line 235 (right before `Scaffold`):
   ```kotlin
   val configuration = LocalConfiguration.current
   val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
   if (isLandscape) {
       EditorScreenLandscape(
           viewModel = viewModel,
           onBack = onBack,
           modifier = modifier
       )
       return
   }
   ```
4. Compile and verify:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
   .\gradlew.bat compileDebugKotlin
   ```
5. Deliver handoff report to `.agents/teamwork/worker_m4_gen4/handoff.md` and message the orchestrator.

## Exclusive Write Scope:
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt`
- `CleanLightroom/app/src/main/res/drawable/**` (if drawables needed)

## Mandatory Integrity Warning:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.


## 2026-10-31T17:04:36Z
[Message] timestamp=2026-10-31T17:04:36Z sender=44240a21-53d3-40ab-9781-d6b2d37ac308 priority=MESSAGE_PRIORITY_HIGH content=You are assigned as worker_m4_gen4 (Responsive Landscape Worker) for Milestone 4 of CleanLightroom Mobile UI Ultra-Refinement.
