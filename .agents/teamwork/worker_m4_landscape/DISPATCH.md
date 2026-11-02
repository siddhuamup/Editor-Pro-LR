# Task Assignment: Milestone 4 — Responsive Landscape Adaptation (EditorScreenLandscape.kt)

## Identity
- Name: worker_m4_landscape
- Role: UI & Responsive Landscape Adaptation Worker
- Working Directory: c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m4_landscape\
- Parent: orchestrator_gen2 (Conversation ID: 940af036-e815-4281-aa60-73e24df6e7c3)

## Objective
Implement responsive landscape (horizontal) orientation adaptation in `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` matching the 66 landscape reference captures in `CleanLightroom/design-reference/horizontal screen UI(same as verticle)/`, and hook the orientation check in `EditorScreen.kt` to seamlessly branch between `EditorScreenLandscape` and the portrait layout while preserving all ViewModel state, adjustments, slider values, and mask layers.

## Inputs & Context Files (Read before coding)
- Authoritative User Request: `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\ORIGINAL_REQUEST.md` (MANDATORY: Read first)
- Project Scope: `c:\Users\Siddhu\Desktop\apps testing\PROJECT.md`
- Design Reference Screenshots:
  - Landscape: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\horizontal screen UI(same as verticle)\`
  - Portrait: `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\design-reference\`
- Key Implementation Files to inspect:
  - `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/editor/EditorScreen.kt`
  - `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/editor/EditorViewModel.kt`
  - `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/editor/MaskingPanel.kt`
  - `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/editor/LoupeCanvas.kt`
  - `c:\Users\Siddhu\Desktop\apps testing\CleanLightroom\app\src\main\kotlin\com/cleanlr/ui/theme/Color.kt`

## Exclusive Write Scope
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt` (NEW file)
- `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt` (Only hook the `isLandscape` orientation check)
- `c:\Users\Siddhu\Desktop\apps testing\.agents\teamwork\worker_m4_landscape\**` (your metadata, progress.md, handoff.md)
- FORBIDDEN: Do NOT touch any other source or configuration files.

## Technical & Architectural Requirements
1. **Architecture & Geometry of EditorScreenLandscape**:
   - In landscape mode (`LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`):
   - **Left Canvas Area (`weight(1f)`)**:
     - House the LoupeCanvas / SurfaceView canvas for interactive preview and gesture handling.
     - Transparent top action bar overlaid on top of canvas (Back button, Undo, Redo, Versions, Share, Overflow).
     - When Masking tool is active, overlay `FloatingMaskDock` or centroid pin badges cleanly on the canvas workspace.
   - **Right Workspace Vertical Rails**:
     - **Adjustment Column** (~300..360dp width): Houses active tool controls (e.g. Light sliders, Color Mix/Grading, Effects, Detail, Optics, Masking adjustment sliders, Crop controls, Presets) in a scrollable column.
     - **Sub-tool Vertical Strip** (~56..64dp width): For tools with sub-categories (Light, Color, Effects, Detail, Optics), renders vertical strip with active state and 4dp blue indicator dots for modified sub-tools.
     - **Primary Dock Vertical Rail** (~64..72dp width): Pinned to the far right edge of the screen, displaying primary tools (Edit, Masking, Remove, Crop, Profiles, Presets, Actions) with authentic 24dp VectorDrawables, `#1D1D1D` background, `#2680EB` active tint, labels.
2. **Hooking into EditorScreen.kt**:
   - Check `val configuration = LocalConfiguration.current; val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE`.
   - If `isLandscape`: invoke `EditorScreenLandscape(...)` passing needed state and callbacks from `EditorViewModel`.
   - If portrait: retain existing `Scaffold` portrait layout.
   - Ensure all editing state, sliders, active tab, mask layers, and undo/redo history remain unified and unbroken across orientation transitions.
3. **Design Tokens & Authentic Parity**:
   - Surfaces unified to `#1D1D1D`.
   - Active blue accents: `#2680EB`.
   - Tabular slider readouts (`tnum`), modified `#38A9FF`, default `#8E8E8E`.
   - Zero Material icon leaks.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Verification
You must execute the compilation command and verify it passes with 0 errors:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "c:\Users\Siddhu\Desktop\apps testing\CleanLightroom"
.\gradlew.bat compileDebugKotlin
```

## Completion Criteria
1. `EditorScreenLandscape.kt` implemented and compiling cleanly.
2. `EditorScreen.kt` hooked to display landscape vs portrait layout.
3. `./gradlew.bat compileDebugKotlin` succeeds with 0 errors.
4. Detailed `handoff.md` created in your working directory following Handoff Protocol.
5. Notify orchestrator via `send_message`.

