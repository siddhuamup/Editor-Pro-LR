# Task Assignment: Milestone 4 — Responsive Landscape Adaptation

## Objective
Implement responsive landscape (horizontal) orientation adaptation in `EditorScreen.kt`, matching the 66 landscape reference screenshots in `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/`.

## Context & Inputs
- Authoritative User Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md` (Read this first!)
- Project Specifications & Contracts: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- Design Reference Screenshots:
  - Vertical: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\`
  - Horizontal: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\horizontal screen UI(same as verticle)\`
- Target Source File: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/editor/EditorScreen.kt`

## Exclusive Write Scope
- You ONLY have permission to modify:
  `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt`
  `CleanLightroom/app/src/main/res/drawable/**` (if any drawable needed)
- DO NOT modify:
  `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/MaskingPanel.kt`
  `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/theme/Color.kt`
  `CleanLightroom/build.gradle.kts`
  `CleanLightroom/settings.gradle.kts`
  `CleanLightroom/app/build.gradle.kts`
  `CleanLightroom/app/src/main/AndroidManifest.xml`

## Required Features & Technical Requirements
1. **Orientation Branching**:
   - Detect screen orientation via `val configuration = LocalConfiguration.current; val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE`.
   - In `EditorScreen`, branch cleanly: when portrait, keep the polished vertical layout (`EditorScreenPortrait`); when landscape, display `EditorScreenLandscape`.
2. **Landscape Workspace Geometry**:
   - In landscape (`2712 x 1220 px` or wide aspect ratios):
     - **Left Workspace (Loupe Canvas)**: Occupies the entire left area (`Modifier.weight(1f)` / `X = 0..1780`), containing the full image preview, Loupe canvas / SurfaceView, and an overlaid transparent top action bar (Back, Undo, Redo, Versions, Share, Overflow).
     - **Right Workspace (Vertical Rails & Panels)**: Anchored to the right side of the screen (`Row(modifier = Modifier.fillMaxHeight())`):
       - **Adjustment Column** (Width ~300..360dp / `X = 1780..2380`): Houses the active tool adjustment panel (Sliders for Light, Color Mix/Grading, Effects, Detail, Optics, Masking sliders, or Crop controls) in a clean scrollable column.
       - **Sub-tool Vertical Strip** (Width ~56..64dp / `X = 2380..2550`): For tools with sub-tabs (like Light, Color, Effects, Detail, Optics under Edit), renders a vertical strip/rail of sub-tool buttons with the active indicator and 4dp blue modified dots.
       - **Primary Tool Dock Vertical Rail** (Width ~64..72dp / `X = 2550..2712`): Pinned to the far right edge of the screen, vertically displaying the primary tools (Edit/Sun, Masking, Remove/Sparkle, Crop, Profiles, Presets, Actions) with authentic 24dp icons, active blue tint (`#2680EB`), labels, and `#1D1D1D` background.
3. **Smooth Interactive Continuity**:
   - Preserve state when rotating: active primary tool, active sub-tab, slider values, zoom/pan transform, mask selection.
   - Masking panel integration: When Masking tool is selected in landscape, floating mask dock anchors near canvas right, and Masking adjustment sliders render in the Adjustment Column.
4. **Authentic Styling & Tokens**:
   - Backgrounds unified to `spectrum_surface_dark` (`#1D1D1D`).
   - Active accent: `#2680EB`.
   - Dividers: subtle 0.5dp `#242424` or `#1D1D1D`.
   - Slider numbers: tabular layout (`tnum`), modified `#38A9FF`, default `#8E8E8E`.
   - Zero Material icon leaks.

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
Write your complete report to `.agents/teamwork/worker_m4/handoff.md` following the Handoff Protocol (Observation, Logic Chain, Caveats, Conclusion, Verification Method).

## 2026-10-29T09:35:54Z
[Message] timestamp=2026-10-29T09:35:54Z sender=44240a21-53d3-40ab-9781-d6b2d37ac308 priority=MESSAGE_PRIORITY_HIGH content=You are assigned as worker_m4 (UI & Responsive Landscape Adaptation Worker) for Milestone 4 of CleanLightroom Mobile UI Ultra-Refinement.

