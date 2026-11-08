# Technical Exploration & Architecture Handoff Report: UI State Desynchronization & Landscape Masking Layout

**Author**: `explorer_m5_state_layout` (teamwork_explorer)  
**Date**: 2026-11-07T18:20:00Z  
**Status**: COMPLETE (Read-Only Investigation)  
**Target Milestone**: Milestone 5 Remediation  

---

## 1. Observation

Direct code and visual inspection was conducted across the CleanLightroom codebase and authentic reference assets:

### 1.1 Issue 1: Primary Dock & ViewModel State Desynchronization
- **In `EditorScreen.kt` lines 240–248**:
  ```kotlin
  if (isLandscape) {
      EditorScreenLandscape(
          viewModel = viewModel,
          modifier = modifier,
          selectedMobileTool = selectedMobileTool,
          onSelectMobileTool = { selectedMobileTool = it },
          onBack = onBack
      )
      return
  }
  ```
  `onSelectMobileTool` only updates the local Compose state `selectedMobileTool = it`. It **never invokes** `viewModel.setActiveTab(...)`.
- **In `EditorScreenLandscape.kt` lines 1227–1239**:
  ```kotlin
  primaryTools.forEach { (toolName, iconRes) ->
      val isActive = (selectedMobileTool == toolName)
      PrimaryDockRailItem(
          iconRes = iconRes,
          label = toolName,
          isActive = isActive,
          onClick = {
              view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
              onSelectMobileTool(toolName)
          }
      )
      Spacer(Modifier.height(8.dp))
  }
  ```
  Tapping a tool in the landscape primary dock rail calls `onSelectMobileTool(toolName)`. Because `onSelectMobileTool` only sets `selectedMobileTool`, `viewModel.activeTab` remains stuck at its previous value (e.g. `EditorToolTab.MASKS`).
- **In `EditorScreenLandscape.kt` lines 1006–1094 (`LandscapeAdjustmentColumn`)**:
  When `selectedMobileTool == "Edit"`, execution branches to `else -> when (activeTab)`. If `activeTab` remained `EditorToolTab.MASKS` from a previous masking session, it falls through to line 1087:
  ```kotlin
  else -> {
      Text(
          text = "${activeTab.title} controls",
          color = SpectrumTextSecondary,
          modifier = Modifier.padding(16.dp)
      )
  }
  ```
  This verbatim produces `"Masks controls"` and renders an empty 320dp adjustment column with no editing sliders (as observed in `emulator_landscape.png`).
- **In `EditorScreen.kt` lines 653–675 (`MobilePrimaryToolbar`)**:
  ```kotlin
  when (tool) {
      "Actions" -> { }
      "Presets" -> viewModel.setActiveTab(EditorToolTab.PRESETS)
      "Edit" -> viewModel.setActiveTab(EditorToolTab.LIGHT)
      "Masking" -> viewModel.setActiveTab(EditorToolTab.MASKS)
      "Remove" -> viewModel.setActiveTab(EditorToolTab.MASKS)
  }
  ```
  - `"Remove"` was erroneously mapped to `EditorToolTab.MASKS` (line 671).
  - `"Actions"` (line 667) left `viewModel.activeTab` untouched.
  - In `EditorScreen.kt` lines 1078–1090, when `selectedMobileTool == "Edit"`, the panel selection is evaluated via `when { ... else -> when (activeTab) { ... EditorToolTab.MASKS -> MaskingSlidersPanel(...) } }`. If `activeTab == MASKS`, the upper panel renders `Mask 1`, `Invert`, `+ Combine`, `Mask Opacity 100%`, and `Exposure 0.00 EV` instead of Light controls, even though "Edit" is highlighted in the bottom dock.
- **In `EditorScreenLandscape.kt` line 595 & `EditorScreen.kt` line 811**:
  While `FloatingMaskDock` is present when `selectedMobileTool == "Masking"`, `MaskOverlayCanvas` (which renders the ruby red mask overlay `#80FF0033` and the centroid active pin) was omitted from `LandscapeCanvasWorkspace` and portrait loupe canvas, meaning mask overlay state wasn't cleanly rendered when active or cleared when inactive.

### 1.2 Issue 2: Landscape Masking Architecture & Typography
- **In `EditorScreenLandscape.kt` lines 204–213**:
  ```kotlin
  if (selectedMobileTool == "Edit") {
      LandscapeSubToolRail(
          viewModel = viewModel,
          activeTab = activeTab,
          modifier = Modifier.width(60.dp)
      )
      VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
  }
  ```
  When `selectedMobileTool == "Masking"`, `LandscapeSubToolRail` is omitted completely.
- **In `MaskingPanel.kt` lines 924–948 (`MaskingSlidersPanel`)**:
  Because the vertical sub-tool rail was omitted in landscape, `MaskingSlidersPanel` attempted to render all 5 local adjustment categories horizontally:
  `[LIGHT] [COLOR] [EFFECTS] [DETAIL] [OPTICS]`
  Inside a 320dp column with padding, this horizontal strip lacks sufficient horizontal layout budget, causing "OPTICS" to be truncated to "OPTI..." in `emulator_landscape_masking.png`.
- **Authentic Lightroom Mobile Landscape Reference (`Screenshot_20261024-201104_Lightroom.png`)**:
  Visual inspection of the authentic reference image reveals:
  1. **Left Canvas Area**: Shows the photo with high-contrast ruby red overlay, centroid mask badge pin, floating mask dock/thumbnail in lower canvas, and top transparent action bar (Undo, Redo, Help, Eye/Overlay toggle, Overflow).
  2. **Middle Adjustment Column (320dp)**: Displays a clean uppercase header (`LIGHT`) with NO horizontal category tabs, followed directly by the category adjustment sliders (`Exposure`, `Contrast`, `Highlights`, `Shadows`, `Whites`, `Blacks`).
  3. **Vertical Sub-Tool Rail (60dp)**: Positioned immediately right of the sliders column, displaying the 5 masking sub-categories:
     - `Light` (`ic_lr_light`)
     - `Color` (`ic_lr_color`)
     - `Effects` (`ic_lr_effects`)
     - `Detail` (`ic_lr_detail`)
     - `Optics` (`ic_lr_optics`)
     The active sub-category (`Light`) is highlighted with a rounded dark-surface card (`#2C2C2C`).
  4. **Far Right Vertical Rail (64dp)**: Displays authentic Discard (`✕`, `ic_lr_close`) and Commit (`✓`, `ic_lr_select_checkmark`) buttons instead of the primary editing dock.

---

## 2. Logic Chain

1. **State Desynchronization Logic**:
   - The primary dock and `EditorViewModel.activeTab` represent two views of the same editing tool state. When a user selects a primary tool, both the active tool string (`selectedMobileTool`) and the canonical tab (`viewModel.activeTab`) must be updated in tandem.
   - Passing a standalone lambda `synchronizeSelectedTool: (String?) -> Unit` to both `EditorScreenLandscape` and `MobilePrimaryToolbar` ensures that selection in either portrait or landscape always maps to the canonical `EditorToolTab`:
     - `"Edit"` -> `EditorToolTab.LIGHT` (or maintains active edit subtab if already on `COLOR`, `BLUR`, etc.)
     - `"Masking"` -> `EditorToolTab.MASKS`
     - `"Crop"` -> `EditorToolTab.CROP`
     - `"Presets"` -> `EditorToolTab.PRESETS`
     - `"Actions"` -> `EditorToolTab.ACTIONS`
     - `"Remove"` -> `EditorToolTab.REMOVE`
   - Adding a `LaunchedEffect(selectedMobileTool)` in `EditorScreenLandscape` ensures that any orientation pivot (portrait -> landscape or landscape -> portrait) immediately reconciles `viewModel.activeTab` if a mismatch exists.
   - Adding a defensive fallback in `LandscapeAdjustmentColumn` ensures that even if `activeTab` is unexpectedly non-edit when `selectedMobileTool == "Edit"`, it gracefully renders `LightLandscapeAdjustmentPanel` rather than `"Masks controls"` and an empty column.

2. **Landscape Masking Architecture Logic**:
   - In landscape orientation, screen width (2400px / ~914dp) allows a dedicated multi-rail architecture.
   - Masking sub-categories (`Light`, `Color`, `Effects`, `Detail`, `Optics`) are distinct sub-tools. Displaying them in a dedicated vertical `LandscapeMaskingSubToolRail` (60dp) directly replicates Lightroom Mobile's UX and frees the 320dp adjustment column from horizontal tab clutter.
   - Adding a `showCategoryTabs: Boolean = true` parameter to `MaskingSlidersPanel` allows landscape mode (`showCategoryTabs = false`) to display the clean category header `Text(text = currentCategory.title.uppercase(), ...)` matching `Screenshot_20261024-201104_Lightroom.png`. This completely eliminates the horizontal text clipping ("OPTI...").
   - Displaying Discard (`✕`) and Commit (`✓`) buttons in `LandscapePrimaryDockRail` during Masking mode mirrors the existing Crop mode UX and matches `Screenshot_20261024-201104_Lightroom.png`. Tapping either button returns to "Edit" and sets `viewModel.activeTab` to `LIGHT`.
   - Rendering `MaskOverlayCanvas` on top of `LoupeCanvas` when `selectedMobileTool == "Masking" && !isCleanScreenMode` ensures the ruby red overlay and centroid pin appear during masking and disappear completely when exiting to "Edit".

---

## 3. Caveats

1. **Protecting Against DEX Register Overflow (`VerifyError`)**:
   Milestone 4 required decomposition into small functions to avoid Dalvik method size and register limits. In our proposed changes, `LandscapeMaskingSubToolRail` is created as an independent, compact `@Composable` function (~35 lines) and reuse of `SubToolRailItem` keeps method sizes far below any DEX compiler limits.
2. **Backward Compatibility**:
   `MaskingSlidersPanel` retains its existing 7-parameter and 10-parameter signatures via default arguments (`showCategoryTabs: Boolean = true`), ensuring no breaks for existing tests or callers.
3. **Primary Dock Icons**:
   Reviewer finding 4 identified that `ic_lr_mask_brush` is used in landscape dock vs `ic_lr_masking` in portrait dock. While this exploration focuses strictly on state synchronization and landscape masking layout architecture, the Worker should note that `ic_lr_masking` can be unified across both docks.

---

## 4. Conclusion & Exact Proposed Kotlin Code Snippets

The proposed changes are structured across 4 files:
1. `EditorViewModel.kt`: Expand `EditorToolTab` with `ACTIONS` and `REMOVE` with corresponding handlers in `isTabModified` and `resetTab`.
2. `EditorScreen.kt`: Define `synchronizeSelectedTool`, pass it to both landscape and portrait toolbars, and integrate `MaskOverlayCanvas`.
3. `EditorScreenLandscape.kt`: Add `LaunchedEffect` state reconciliation, integrate `LandscapeMaskingSubToolRail`, add Masking Commit/Discard to `LandscapePrimaryDockRail`, embed `MaskOverlayCanvas`, and update `MaskingSlidersPanel` invocation.
4. `MaskingPanel.kt`: Add `showCategoryTabs` parameter to `MaskingSlidersPanel` to conditionally replace the horizontal tab strip with the authentic category header.

---

### Detailed Code Changes for Worker

#### Component 1: `EditorViewModel.kt`

**Location**: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorViewModel.kt`

1. Update `EditorToolTab` enum (lines 36–49):
```kotlin
// BEFORE (lines 36-49):
enum class EditorToolTab(val title: String) {
    CROP("Crop"),
    LIGHT("Light"),
    COLOR("Color"),
    BLUR("Blur"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    OPTICS("Optics"),
    PROFILES("Profiles"),
    CURVES("Curves"),
    PRESETS("Presets"),
    VERSIONS("Versions"),
    MASKS("Masks")
}

// AFTER:
enum class EditorToolTab(val title: String) {
    CROP("Crop"),
    LIGHT("Light"),
    COLOR("Color"),
    BLUR("Blur"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    OPTICS("Optics"),
    PROFILES("Profiles"),
    CURVES("Curves"),
    PRESETS("Presets"),
    VERSIONS("Versions"),
    MASKS("Masks"),
    ACTIONS("Actions"),
    REMOVE("Remove")
}
```

2. Update `isTabModified` (lines 1287–1290):
```kotlin
// BEFORE:
            EditorToolTab.MASKS -> {
                r.masks.isNotEmpty()
            }
        }
    }

// AFTER:
            EditorToolTab.MASKS -> {
                r.masks.isNotEmpty()
            }
            EditorToolTab.ACTIONS -> false
            EditorToolTab.REMOVE -> false
        }
    }
```

3. Update `resetTab` (lines 1350–1354):
```kotlin
// BEFORE:
            EditorToolTab.MASKS -> {
                updateRecipe("Reset Masks") { it.copy(masks = emptyList()) }
            }
        }
    }

// AFTER:
            EditorToolTab.MASKS -> {
                updateRecipe("Reset Masks") { it.copy(masks = emptyList()) }
            }
            EditorToolTab.ACTIONS -> {}
            EditorToolTab.REMOVE -> {}
        }
    }
```

---

#### Component 2: `EditorScreen.kt`

**Location**: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreen.kt`

1. Define `synchronizeSelectedTool` and pass to `EditorScreenLandscape` (replace lines 236–248):
```kotlin
// BEFORE (lines 236-248):
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        EditorScreenLandscape(
            viewModel = viewModel,
            modifier = modifier,
            selectedMobileTool = selectedMobileTool,
            onSelectMobileTool = { selectedMobileTool = it },
            onBack = onBack
        )
        return
    }

// AFTER:
    val synchronizeSelectedTool: (String?) -> Unit = { tool ->
        if (selectedMobileTool == tool) {
            if (tool == "Crop" && viewModel.isCropActive.value) {
                viewModel.toggleCropActive()
            }
            selectedMobileTool = null
        } else {
            if (tool == "Crop") {
                if (!viewModel.isCropActive.value) viewModel.toggleCropActive()
                selectedMobileTool = "Crop"
                viewModel.setActiveTab(EditorToolTab.CROP)
            } else {
                if (viewModel.isCropActive.value) viewModel.toggleCropActive()
                selectedMobileTool = tool
                when (tool) {
                    "Actions" -> viewModel.setActiveTab(EditorToolTab.ACTIONS)
                    "Presets" -> viewModel.setActiveTab(EditorToolTab.PRESETS)
                    "Edit" -> {
                        val currentTab = viewModel.activeTab.value
                        val isEditSubTab = currentTab in listOf(
                            EditorToolTab.LIGHT,
                            EditorToolTab.COLOR,
                            EditorToolTab.BLUR,
                            EditorToolTab.EFFECTS,
                            EditorToolTab.DETAIL,
                            EditorToolTab.OPTICS,
                            EditorToolTab.PROFILES,
                            EditorToolTab.CURVES
                        )
                        if (!isEditSubTab) {
                            viewModel.setActiveTab(EditorToolTab.LIGHT)
                        }
                    }
                    "Masking" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                    "Remove" -> viewModel.setActiveTab(EditorToolTab.REMOVE)
                    else -> viewModel.setActiveTab(EditorToolTab.LIGHT)
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        EditorScreenLandscape(
            viewModel = viewModel,
            modifier = modifier,
            selectedMobileTool = selectedMobileTool,
            onSelectMobileTool = synchronizeSelectedTool,
            onBack = onBack
        )
        return
    }
```

2. Wire `MobilePrimaryToolbar` to `synchronizeSelectedTool` (replace lines 651–677):
```kotlin
// BEFORE (lines 651-677):
                    else -> MobilePrimaryToolbar(
                        selectedTool = selectedMobileTool,
                        onToolSelected = { tool ->
                            if (selectedMobileTool == tool) {
                                if (tool == "Crop" && viewModel.isCropActive.value) {
                                    viewModel.toggleCropActive()
                                }
                                selectedMobileTool = null
                            } else {
                                if (tool == "Crop") {
                                    if (!viewModel.isCropActive.value) viewModel.toggleCropActive()
                                    selectedMobileTool = "Crop"
                                } else {
                                    if (viewModel.isCropActive.value) viewModel.toggleCropActive()
                                    selectedMobileTool = tool
                                    when (tool) {
                                        "Actions" -> { }
                                        "Presets" -> viewModel.setActiveTab(EditorToolTab.PRESETS)
                                        "Edit" -> viewModel.setActiveTab(EditorToolTab.LIGHT)
                                        "Masking" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                                        "Remove" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                                    }
                                }
                            }
                        }
                    )

// AFTER:
                    else -> MobilePrimaryToolbar(
                        selectedTool = selectedMobileTool,
                        onToolSelected = synchronizeSelectedTool
                    )
```

3. Include `MaskOverlayCanvas` on portrait canvas overlay (lines 810–833):
```kotlin
// BEFORE (lines 810-833):
                // Floating Mask Dock (Reference Screenshot 083741)
                if (selectedMobileTool == "Masking" && !isCleanScreenMode) {
                    FloatingMaskDock(
                        masks = recipe.masks,
                        activeMaskIndex = activeMaskLayerIndex,
                        onSelectMask = { viewModel.selectMaskLayer(it) },
                        onToggleLayer = { idx ->
                            val layer = recipe.masks.getOrNull(idx)
                            if (layer != null) {
                                viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                            }
                        },
                        onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                        onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                        onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                        onAddNewMaskClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCreateMaskSheet = true
                        },
                        modifier = Modifier
                            .align(if (recipe.masks.isEmpty()) Alignment.BottomEnd else Alignment.CenterEnd)
                            .padding(bottom = if (recipe.masks.isEmpty()) 16.dp else 0.dp)
                    )
                }

// AFTER:
                // Floating Mask Dock & Canvas Overlay (Reference Screenshots 083741 & 201104)
                if (selectedMobileTool == "Masking" && !isCleanScreenMode) {
                    MaskOverlayCanvas(
                        masks = recipe.masks,
                        activeMaskIndex = activeMaskLayerIndex,
                        isOverlayVisible = true,
                        onPinClick = { },
                        modifier = Modifier.fillMaxSize()
                    )

                    FloatingMaskDock(
                        masks = recipe.masks,
                        activeMaskIndex = activeMaskLayerIndex,
                        onSelectMask = { viewModel.selectMaskLayer(it) },
                        onToggleLayer = { idx ->
                            val layer = recipe.masks.getOrNull(idx)
                            if (layer != null) {
                                viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                            }
                        },
                        onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                        onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                        onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                        onAddNewMaskClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCreateMaskSheet = true
                        },
                        modifier = Modifier
                            .align(if (recipe.masks.isEmpty()) Alignment.BottomEnd else Alignment.CenterEnd)
                            .padding(bottom = if (recipe.masks.isEmpty()) 16.dp else 0.dp)
                    )
                }
```

4. Add `ACTIONS` and `REMOVE` branches in `tabIconRes` (lines 2484–2497):
```kotlin
                            val tabIconRes = when (tab) {
                                EditorToolTab.CROP -> R.drawable.ic_lr_crop
                                EditorToolTab.LIGHT -> R.drawable.ic_lr_light
                                EditorToolTab.COLOR -> R.drawable.ic_lr_color
                                EditorToolTab.BLUR -> R.drawable.ic_lr_lens_blur
                                EditorToolTab.EFFECTS -> R.drawable.ic_lr_effects
                                EditorToolTab.DETAIL -> R.drawable.ic_lr_detail
                                EditorToolTab.OPTICS -> R.drawable.ic_lr_optics
                                EditorToolTab.PROFILES -> R.drawable.ic_lr_profiles
                                EditorToolTab.CURVES -> R.drawable.ic_lr_curve
                                EditorToolTab.PRESETS -> R.drawable.ic_lr_presets
                                EditorToolTab.VERSIONS -> R.drawable.ic_lr_versions
                                EditorToolTab.MASKS -> R.drawable.ic_lr_masking
                                EditorToolTab.ACTIONS -> R.drawable.ic_lr_quick_actions
                                EditorToolTab.REMOVE -> R.drawable.ic_lr_generative_remove
                            }
```

---

#### Component 3: `EditorScreenLandscape.kt`

**Location**: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/EditorScreenLandscape.kt`

1. Add state reconciliation `LaunchedEffect(selectedMobileTool)` around line 105:
```kotlin
    LaunchedEffect(selectedMobileTool) {
        when (selectedMobileTool) {
            "Edit" -> {
                if (activeTab !in listOf(
                        EditorToolTab.LIGHT, EditorToolTab.COLOR, EditorToolTab.BLUR,
                        EditorToolTab.EFFECTS, EditorToolTab.DETAIL, EditorToolTab.OPTICS,
                        EditorToolTab.PROFILES, EditorToolTab.CURVES
                    )
                ) {
                    viewModel.setActiveTab(EditorToolTab.LIGHT)
                }
            }
            "Masking" -> if (activeTab != EditorToolTab.MASKS) viewModel.setActiveTab(EditorToolTab.MASKS)
            "Crop" -> if (activeTab != EditorToolTab.CROP) viewModel.setActiveTab(EditorToolTab.CROP)
            "Presets" -> if (activeTab != EditorToolTab.PRESETS) viewModel.setActiveTab(EditorToolTab.PRESETS)
            "Actions" -> if (activeTab != EditorToolTab.ACTIONS) viewModel.setActiveTab(EditorToolTab.ACTIONS)
            "Remove" -> if (activeTab != EditorToolTab.REMOVE) viewModel.setActiveTab(EditorToolTab.REMOVE)
        }
    }
```

2. Render `LandscapeMaskingSubToolRail` when `selectedMobileTool == "Masking"` (replace lines 204–213):
```kotlin
// BEFORE (lines 204-213):
            if (selectedMobileTool == "Edit") {
                LandscapeSubToolRail(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            }

// AFTER:
            if (selectedMobileTool == "Edit") {
                LandscapeSubToolRail(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            } else if (selectedMobileTool == "Masking") {
                LandscapeMaskingSubToolRail(
                    selectedCategory = selectedMaskCategory,
                    onSelectCategory = { selectedMaskCategory = it },
                    modifier = Modifier.width(60.dp)
                )

                VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)
            }
```

3. Include `MaskOverlayCanvas` in `LandscapeCanvasWorkspace` (replace lines 595–617):
```kotlin
// BEFORE (lines 595-617):
            if (selectedMobileTool == "Masking") {
                FloatingMaskDock(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    onSelectMask = { viewModel.selectMaskLayer(it) },
                    onToggleLayer = { idx ->
                        val layer = recipe.masks.getOrNull(idx)
                        if (layer != null) {
                            viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                        }
                    },
                    onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                    onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                    onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                    onAddNewMaskClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onShowCreateMaskSheet()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }

// AFTER:
            if (selectedMobileTool == "Masking" && !isCleanScreenMode) {
                MaskOverlayCanvas(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    isOverlayVisible = true,
                    onPinClick = { },
                    modifier = Modifier.fillMaxSize()
                )

                FloatingMaskDock(
                    masks = recipe.masks,
                    activeMaskIndex = activeMaskLayerIndex,
                    onSelectMask = { viewModel.selectMaskLayer(it) },
                    onToggleLayer = { idx ->
                        val layer = recipe.masks.getOrNull(idx)
                        if (layer != null) {
                            viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
                        }
                    },
                    onInvertMask = { idx -> viewModel.invertMaskLayer(idx) },
                    onDuplicateMask = { idx -> viewModel.duplicateMaskLayer(idx) },
                    onDeleteMask = { idx -> viewModel.deleteMaskLayer(idx) },
                    onAddNewMaskClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onShowCreateMaskSheet()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }
```

4. Pass `showCategoryTabs = false` and `onCategorySelected = onSelectMaskCategory` to `MaskingSlidersPanel`, and replace the fallback in `LandscapeAdjustmentColumn` (lines 957–966 & 1087–1094):
```kotlin
// BEFORE (lines 957-966):
                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) }
                    )
                }

// AFTER:
                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onCategorySelected = onSelectMaskCategory,
                        onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) },
                        showCategoryTabs = false
                    )
                }
```
And defensive fallback (lines 1087–1094):
```kotlin
// BEFORE (lines 1087-1094):
                        else -> {
                            Text(
                                text = "${activeTab.title} controls",
                                color = SpectrumTextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

// AFTER:
                        else -> {
                            // Defensive recovery: default to Light controls when activeTab is not an edit tab
                            LightLandscapeAdjustmentPanel(
                                recipe = recipe,
                                isHdrModeActive = isHdrModeActive,
                                hdrLimitEv = hdrLimitEv,
                                onHdrToggle = onToggleHdrMode,
                                onHdrLimitChange = onHdrLimitEvChange,
                                onOpenCurves = { viewModel.setActiveTab(EditorToolTab.CURVES) },
                                viewModel = viewModel
                            )
                        }
```

5. Define `LandscapeMaskingSubToolRail` right below `LandscapeSubToolRail` (around line 1162):
```kotlin
// =============================================================================
// 2B-2. MASKING SUB-TOOL VERTICAL STRIP COMPONENT (Landscape Masking Mode)
// =============================================================================
@Composable
internal fun LandscapeMaskingSubToolRail(
    selectedCategory: LocalAdjustmentCategory,
    onSelectCategory: (LocalAdjustmentCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        LocalAdjustmentCategory.LIGHT to Pair(R.drawable.ic_lr_light, "Light"),
        LocalAdjustmentCategory.COLOR to Pair(R.drawable.ic_lr_color, "Color"),
        LocalAdjustmentCategory.EFFECTS to Pair(R.drawable.ic_lr_effects, "Effects"),
        LocalAdjustmentCategory.DETAIL to Pair(R.drawable.ic_lr_detail, "Detail"),
        LocalAdjustmentCategory.OPTICS to Pair(R.drawable.ic_lr_optics, "Optics")
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SpectrumSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        categories.forEach { (cat, info) ->
            val (iconRes, label) = info
            val isSelected = (selectedCategory == cat)

            SubToolRailItem(
                iconRes = iconRes,
                label = label,
                isSelected = isSelected,
                isModified = false,
                isBypassed = false,
                onClick = { onSelectCategory(cat) }
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}
```

6. Add Discard and Commit buttons to `LandscapePrimaryDockRail` for `"Masking"` (lines 1184–1240):
```kotlin
// BEFORE (lines 1184-1240):
        if (selectedMobileTool == "Crop") {
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.resetCrop()
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Cancel crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            val primaryTools = listOf(
                "Actions" to R.drawable.ic_lr_quick_actions,
                "Presets" to R.drawable.ic_lr_presets,
                "Crop" to R.drawable.ic_lr_crop,
                "Edit" to R.drawable.ic_lr_edit_sliders,
                "Masking" to R.drawable.ic_lr_mask_brush,
                "Remove" to R.drawable.ic_lr_generative_remove
            )

            primaryTools.forEach { (toolName, iconRes) ->
                val isActive = (selectedMobileTool == toolName)
                PrimaryDockRailItem(
                    iconRes = iconRes,
                    label = toolName,
                    isActive = isActive,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectMobileTool(toolName)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

// AFTER:
        if (selectedMobileTool == "Crop") {
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.resetCrop()
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Cancel crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply crop",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else if (selectedMobileTool == "Masking") {
            // Authentic Lightroom Mobile Landscape Masking Rail (Screenshot 201104)
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Discard mask",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSelectMobileTool("Edit")
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply mask",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            val primaryTools = listOf(
                "Actions" to R.drawable.ic_lr_quick_actions,
                "Presets" to R.drawable.ic_lr_presets,
                "Crop" to R.drawable.ic_lr_crop,
                "Edit" to R.drawable.ic_lr_edit_sliders,
                "Masking" to R.drawable.ic_lr_mask_brush,
                "Remove" to R.drawable.ic_lr_generative_remove
            )

            primaryTools.forEach { (toolName, iconRes) ->
                val isActive = (selectedMobileTool == toolName)
                PrimaryDockRailItem(
                    iconRes = iconRes,
                    label = toolName,
                    isActive = isActive,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectMobileTool(toolName)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
```

---

#### Component 4: `MaskingPanel.kt`

**Location**: `CleanLightroom/app/src/main/kotlin/com/cleanlr/ui/editor/MaskingPanel.kt`

1. Update `MaskingSlidersPanel` function signatures (lines 680–724):
```kotlin
// BEFORE (lines 680-724):
/**
 * 7-parameter overload called by EditorScreen.kt (backward compatible)
 */
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    modifier: Modifier = Modifier
) {
    MaskingSlidersPanel(
        layers = layers,
        activeLayerIndex = activeLayerIndex,
        selectedCategory = selectedCategory,
        onCategorySelected = {},
        onUpdateLayerAdjustments = onUpdateLayerAdjustments,
        onDeleteActiveLayer = onDeleteActiveLayer,
        onAddSubMaskToActive = onAddSubMaskToActive,
        onSubtractSubMaskFromActive = onAddSubMaskToActive,
        onIntersectSubMaskWithActive = onAddSubMaskToActive,
        modifier = modifier
    )
}

/**
 * Complete 10-parameter MaskingSlidersPanel adhering strictly to PROJECT.md § Interface Contracts.
 * Supports all 5 categories: Light, Color, Effects, Detail, Optics.
 * Reference Screenshots: 201104 (Light), 201109 (Color), 201112 (Effects), 201114 (Detail), 201117 (Optics).
 */
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

// AFTER:
/**
 * 7-parameter overload called by EditorScreen.kt (backward compatible)
 */
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
) {
    MaskingSlidersPanel(
        layers = layers,
        activeLayerIndex = activeLayerIndex,
        selectedCategory = selectedCategory,
        onCategorySelected = {},
        onUpdateLayerAdjustments = onUpdateLayerAdjustments,
        onDeleteActiveLayer = onDeleteActiveLayer,
        onAddSubMaskToActive = onAddSubMaskToActive,
        onSubtractSubMaskFromActive = onAddSubMaskToActive,
        onIntersectSubMaskWithActive = onAddSubMaskToActive,
        showCategoryTabs = showCategoryTabs,
        modifier = modifier
    )
}

/**
 * Complete 11-parameter MaskingSlidersPanel adhering strictly to PROJECT.md § Interface Contracts.
 * Supports all 5 categories: Light, Color, Effects, Detail, Optics.
 * Reference Screenshots: 201104 (Light), 201109 (Color), 201112 (Effects), 201114 (Detail), 201117 (Optics).
 */
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
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
)
```

2. Conditionally render the category tab strip (lines 924–948):
```kotlin
// BEFORE (lines 924-948):
        // 3. Category Header & Tab Selector Strip (Light | Color | Effects | Detail | Optics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalAdjustmentCategory.values().forEach { cat ->
                val isCatSelected = cat == currentCategory
                Text(
                    text = cat.title.uppercase(),
                    color = if (isCatSelected) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                    fontSize = 11.sp,
                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            currentCategory = cat
                            onCategorySelected(cat)
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }

// AFTER:
        if (showCategoryTabs) {
            // 3. Category Header & Tab Selector Strip (Light | Color | Effects | Detail | Optics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LocalAdjustmentCategory.values().forEach { cat ->
                    val isCatSelected = cat == currentCategory
                    Text(
                        text = cat.title.uppercase(),
                        color = if (isCatSelected) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                        fontSize = 11.sp,
                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                currentCategory = cat
                                onCategorySelected(cat)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        } else {
            // Authentic Landscape Category Title (Reference Screenshot 201104)
            Text(
                text = currentCategory.title.uppercase(),
                color = SpectrumTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
```

---

## 5. Verification Method

Once the Worker implements the snippets above, verify independently:

1. **Clean Kotlin Compilation**:
   ```powershell
   ./gradlew.bat compileDebugKotlin
   ```
   *Expected*: BUILD SUCCESSFUL with 0 compiler errors.

2. **DEX Register Verification & Unit Tests**:
   ```powershell
   ./gradlew.bat testDebugUnitTest --tests com.cleanlr.ui.editor.LandscapeOrientationAdaptationTest
   ```
   *Expected*: BUILD SUCCESSFUL with 0 errors and zero `VerifyError`.

3. **E2E Tool Synchronization & UI State Verification**:
   Execute the E2E verification test targeting primary workflow and orientation:
   ```powershell
   python run_e2e_tests.py -k "test_t4_01 or test_t4_03 or test_t2_20" -v
   ```
   *Expected*: All pass cleanly. Specifically:
   - `test_t4_01`: Tapping "Edit" reveals the Exposure slider immediately because `viewModel.activeTab` is synchronized to `LIGHT`.
   - `test_t2_20`: Rapid category switching across Light, Color, Effects, Detail, and Optics succeeds via both portrait strip and landscape rail.
   - `emulator_landscape.png`: The adjustment column renders `LightLandscapeAdjustmentPanel` with Exposure, Contrast, Highlights, Shadows, Whites, Blacks sliders when "Edit" is selected. Never displays `"Masks controls"`.
   - `emulator_landscape_masking.png`: "OPTICS" text is no longer truncated as "OPTI..."; 5 categories render vertically on the 60dp sub-tool rail; far right rail shows Discard (`✕`) and Commit (`✓`).
