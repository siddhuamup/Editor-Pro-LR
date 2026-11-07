# Project: CleanLightroom Mobile UI Ultra-Refinement

## Architecture
CleanLightroom is a high-fidelity Android photo editing application built with Jetpack Compose and an OpenGL ES 3.0 GPU rendering pipeline. The UI replicates Adobe Lightroom Mobile with exact visual parity, authentic iconography, interactive masking capabilities, and responsive layout adaptation across portrait and landscape screen orientations.

### Core Modules & Packages
- **App Module (`:app`)**:
  - `com.cleanlr.MainActivity`: Entry point activity configured for configuration changes (`orientation|screenSize|screenLayout|keyboardHidden`).
  - `com.cleanlr.ui.editor.EditorScreen`: Central editing workspace housing Loupe canvas, top action bar, primary tool dock, sub-tool strips, and tool-specific adjustment panels.
  - `com.cleanlr.ui.editor.MaskingPanel`: Dedicated Masking subsystem (creation bottom sheet, floating layer dock, combine operations, and local adjustments across Light, Color, Effects, Detail, and Optics).
  - `com.cleanlr.ui.theme.Color`: Adobe Spectrum Darkroom color design tokens (`#1D1D1D` surfaces, `#2680EB` active accents, `#066CE7` buttons, `#38A9FF` modified values).
  - `com.cleanlr.ui.editor.EditorViewModel`: Central state management coordinating `EditRecipe`, tool selections, undo/redo history, mask layers, and rendering parameters.
  - `app/src/main/res/drawable`: 156 authentic Lightroom Mobile 24px VectorDrawables.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Build Unblock | Fix zero-byte XML drawables (`ic_lr_close.xml`, `ic_lr_more_vert.xml`, `ic_lr_plus.xml`) | M1 | Survey 1 |
| 2 | MaskingPanel Synchronization | Sync `write_masking.py` into `MaskingPanel.kt` to define missing `FloatingMaskDock` and `MaskingSlidersPanel` | M1 | Survey 1 |
| 3 | Design Token Parity | Standardize `Color.kt` surfaces to `#1D1D1D`, accents to `#2680EB`, and canvas to `#000000` | M1 | Survey 2 |
| 4 | SVG Icon Replacement | Replace 7 Material `imageVector` leaks in `EditorScreen.kt` with authentic `ic_lr_*` drawables | M2 | Survey 1, Survey 2 |
| 5 | Primary Dock Icon Parity | Update "Edit" icon to `ic_lr_light` and "Remove" icon to `ic_lr_generative_remove` | M2 | Survey 2 |
| 6 | Sub-tool Modified Dots | Render 4dp blue indicator dot (`#2680EB`) next to sub-tool labels with active adjustments | M2 | Survey 2 |
| 7 | Tabular Slider Values | Ensure slider values use monospace/tabular layout, coloring `#38A9FF` on modified and `#8E8E8E` on default | M2 | Survey 2 |
| 8 | Mask Creation Bottom Sheet | Modal sheet with 3 separated groups: Cloud AI (Subject, Sky, Background), Manual (Brush, Linear, Radial), Range (Color, Luminance) | M3 | Survey 3 |
| 9 | Mask Ruby Red Overlay | Render high-contrast Ruby Red mask overlay (`#E02B2B` / `#FF0033`) and centroid type badge on canvas | M3 | Survey 3 |
| 10 | Floating Mask Dock & Layers | Collapsed pill dock with (+) FAB vs expanded vertical layer cards (thumbnail, eye toggle, invert `◐`, context menu) | M3 | Survey 3 |
| 11 | Mask Combine Modes | Support Add, Subtract, and Intersect operations on sub-masks | M3 | Survey 3 |
| 12 | Mask Opacity Slider | Master opacity multiplier (0% - 100%) for active mask layer | M3 | Survey 3 |
| 13 | Mask 5 Adjustment Tabs | Dedicated tabs for Light, Color (Temp/Tint/Hue/Swatch), Effects, Detail, and Optics local sliders | M3 | Survey 3 |
| 14 | Mask Commit / Discard Bar | Top bar actions with Discard (`✕`), Commit (`✓`), Undo, Redo, and Overflow | M3 | Survey 3 |
| 15 | Responsive Landscape Orientation | Detect `ORIENTATION_LANDSCAPE` and pivot from vertical `Column` into multi-column responsive `Row` | M4 | Survey 1, Survey 2 |
| 16 | Landscape Canvas Workspace | Left-anchored canvas (`X = 0..1780`) with overlaid transparent top bar | M4 | Survey 2 |
| 17 | Landscape Vertical Rails | Adjustment column (`X = 1780..2380`), sub-tool vertical rail (`X = 2380..2550`), and primary dock vertical rail (`X = 2550..2712`) | M4 | Survey 2 |
| 18 | E2E Automated Verification | Automated build, install, orientation rotation, and screenshot comparison on `emulator-5554` | M5 | Survey 3 |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Build Unblock & Core Contracts | Fix 0-byte XML files, sync MaskingPanel composables, standardize Color tokens, verify clean compilation | none | DONE |
| M2 | Authentic SVG Icons & UI Styling | Replace Material icon fallbacks, correct primary dock icons, add sub-tool modified dots, unify surface backgrounds | M1 | DONE |
| M3 | Complete Masking Panel Subsystem | Complete Create Mask sheet, ruby red overlay, floating layer dock, combine modes, opacity slider, 5 adjustment tabs | M1 | DONE |
| M4 | Responsive Landscape Adaptation | Implement Compose orientation handling, multi-column landscape layout (canvas left, vertical rails right) | M2, M3 | DONE |
| M5 | E2E Testing & Visual Parity Verification | Run comprehensive automated test suite, capture emulator-5554 screenshots in portrait & landscape, verify visual parity | M4 | IN_PROGRESS |

---

## Interface Contracts

### 1. `MaskingPanel.kt` ↔ `EditorScreen.kt`
```kotlin
// Floating dock shown on canvas
@Composable
fun FloatingMaskDock(
    masks: List<MaskLayer>,
    activeMaskIndex: Int,
    onSelectMask: (Int) -> Unit,
    onToggleLayer: (Int) -> Unit,
    onInvertMask: (Int) -> Unit,
    onDuplicateMask: (Int) -> Unit,
    onDeleteMask: (Int) -> Unit,
    onAddNewMaskClick: () -> Unit,
    modifier: Modifier = Modifier
)

// Sliders and adjustment tabs panel for active mask
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onCategorySelected: (MaskAdjustmentCategory) -> Unit,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    onSubtractSubMaskFromActive: (SubMask) -> Unit,
    onIntersectSubMaskWithActive: (SubMask) -> Unit,
    modifier: Modifier = Modifier
)

// Bottom sheet for creating new masks
@Composable
fun CreateNewMaskBottomSheet(
    onDismissRequest: () -> Unit,
    onSelectSubMask: (SubMask) -> Unit
)
```

### 2. Orientation & Adaptive Layout Contract
```kotlin
// In EditorScreen.kt:
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

if (isLandscape) {
    EditorScreenLandscape(...)
} else {
    EditorScreenPortrait(...)
}
```

---

## Code Layout & File Ownership Matrix

Following the `parallel-dev-team` strict 4 Golden Rules:

| Stream Name | Role | Exclusive Write Scope | Read-Only Access |
|:------------|:-----|:----------------------|:-----------------|
| `stream-core` | Build & Contracts Engineer | `app/src/main/res/drawable/ic_lr_close.xml`, `ic_lr_more_vert.xml`, `ic_lr_plus.xml`, `app/src/main/kotlin/com/cleanlr/ui/theme/Color.kt` | All files |
| `stream-ui` | UI & SVG Styling Engineer | `app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt`, `app/src/main/res/drawable/**` | All files |
| `stream-masking` | Masking Panel Engineer | `app/src/main/kotlin/com/cleanlr/ui/editor/MaskingPanel.kt` | All files |
| `stream-qa` | E2E Testing & Verification Engineer | `tests/**`, verification scripts in workspace root | All files |

**Protected Core Files (LOCKED)**:
`CleanLightroom/build.gradle.kts`, `CleanLightroom/settings.gradle.kts`, `CleanLightroom/app/build.gradle.kts`, `CleanLightroom/app/src/main/AndroidManifest.xml`.
No worker stream may modify these locked files without explicit orchestrator delegation.
