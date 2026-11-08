package com.cleanlr.ui.editor

import android.content.res.Configuration
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.model.*
import com.cleanlr.preset.Preset
import com.cleanlr.ui.components.*
import com.cleanlr.ui.theme.*
import java.util.UUID
import kotlin.math.abs

/**
 * CleanLightroom Responsive Landscape Editor (Milestone 4).
 * Replicates Adobe Lightroom Mobile landscape UI layout with exact visual parity:
 * - Left Canvas Workspace (weight 1f) with overlaid transparent action bar, LoupeCanvas,
 *   floating mask docks / centroid badges, and straighten dial in Crop mode.
 * - Right Workspace Rails:
 *   1. Adjustment Column (~320dp width): Houses active tool controls in a scrollable column.
 *   2. Sub-tool Vertical Strip (~60dp width): For tools with sub-categories (Auto, Light, Color,
 *      Blur, Effects, Detail, Optics, Profiles) with active selection and 4dp blue modified dots.
 *   3. Primary Dock Vertical Rail (~64dp width): Pinned to the far right edge of the screen,
 *      displaying primary tools (Actions, Presets, Profiles, Crop, Edit, Masking, Remove).
 *      In Crop mode, displays authentic Cancel and Confirm commit action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreenLandscape(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    selectedMobileTool: String? = "Edit",
    onSelectMobileTool: (String?) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    val recipe by viewModel.recipe.collectAsState()
    val isCleanScreenMode by viewModel.isCleanScreenMode.collectAsState()
    val isSliderDragging by viewModel.isSliderDragging.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val aspectRatioGuide by viewModel.aspectRatioGuide.collectAsState()

    var showHistoryMenu by remember { mutableStateOf(false) }
    var showViewOptionsMenu by remember { mutableStateOf(false) }
    var showEditorOverflowMenu by remember { mutableStateOf(false) }
    var showExportConfigSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCreateMaskSheet by remember { mutableStateOf(false) }

    var selectedMaskCategory by remember { mutableStateOf(LocalAdjustmentCategory.LIGHT) }
    var isLensBlurDepthDetecting by remember { mutableStateOf(true) }

    var isHdrModeActive by remember { mutableStateOf(recipe.isHdrEnabled) }
    var hdrLimitEv by remember { mutableFloatStateOf(1.0f) }
    var lensBlurAmount by remember { mutableFloatStateOf(21f) }
    var selectedBokehShape by remember { mutableStateOf("Circle") }
    var selectedProfileGroup by remember { mutableStateOf("Basic") }
    var selectedProfile by remember { mutableStateOf("Artistic 01") }

    var colorSubTab by remember { mutableStateOf("WB") }
    var selectedCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var effectsSubTab by remember { mutableStateOf("Effects") }
    var detailSubTab by remember { mutableStateOf("Sharpening") }
    var cropPage by remember { mutableStateOf("Aspect") }
    var isAspectLocked by remember { mutableStateOf(false) }
    var selectedAspectCategory by remember { mutableStateOf(if (aspectRatioGuide == null) "Original" else "Ratios") }

    var pendingBatchConfig by remember { mutableStateOf<com.cleanlr.export.ExportConfig?>(null) }
    val batchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val config = pendingBatchConfig ?: com.cleanlr.export.ExportConfig()
            val currentRecipe = viewModel.recipe.value
            val items = uris.mapIndexed { idx, uri ->
                com.cleanlr.export.BatchExportItem(
                    id = "batch_item_${System.currentTimeMillis()}_${idx + 1}",
                    sourceUriString = uri.toString(),
                    recipe = currentRecipe,
                    config = config
                )
            }
            viewModel.startBatchExport(items)
        }
    }

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

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LandscapeCanvasWorkspace(
            viewModel = viewModel,
            selectedMobileTool = selectedMobileTool,
            selectedCurveChannel = selectedCurveChannel,
            isHdrModeActive = isHdrModeActive,
            isCleanScreenMode = isCleanScreenMode,
            onBack = onBack,
            showHistoryMenu = showHistoryMenu,
            onShowHistoryMenu = { showHistoryMenu = true },
            onDismissHistoryMenu = { showHistoryMenu = false },
            onShowViewOptionsMenu = { showViewOptionsMenu = true },
            showEditorOverflowMenu = showEditorOverflowMenu,
            onShowEditorOverflowMenu = { showEditorOverflowMenu = true },
            onDismissEditorOverflowMenu = { showEditorOverflowMenu = false },
            onShowExportConfigSheet = { showExportConfigSheet = true },
            onShowResetDialog = { showResetDialog = true },
            onShowCreateMaskSheet = { showCreateMaskSheet = true },
            isAspectLocked = isAspectLocked,
            onToggleAspectLocked = { isAspectLocked = !isAspectLocked },
            isLensBlurDepthDetecting = isLensBlurDepthDetecting,
            onCancelLensBlurDetecting = { isLensBlurDepthDetecting = false },
            modifier = Modifier.weight(1f)
        )

        if (!isCleanScreenMode) {
            VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)

            LandscapeAdjustmentColumn(
                viewModel = viewModel,
                selectedMobileTool = selectedMobileTool,
                activeTab = activeTab,
                isSliderDragging = isSliderDragging,
                selectedMaskCategory = selectedMaskCategory,
                onSelectMaskCategory = { selectedMaskCategory = it },
                cropPage = cropPage,
                onSelectCropPage = { cropPage = it },
                selectedAspectCategory = selectedAspectCategory,
                onSelectAspectCategory = { selectedAspectCategory = it },
                colorSubTab = colorSubTab,
                onSelectColorSubTab = { colorSubTab = it },
                effectsSubTab = effectsSubTab,
                onSelectEffectsSubTab = { effectsSubTab = it },
                detailSubTab = detailSubTab,
                onSelectDetailSubTab = { detailSubTab = it },
                selectedProfileGroup = selectedProfileGroup,
                onSelectProfileGroup = { selectedProfileGroup = it },
                selectedProfile = selectedProfile,
                onSelectProfile = { selectedProfile = it },
                isHdrModeActive = isHdrModeActive,
                onToggleHdrMode = { isHdrModeActive = it },
                hdrLimitEv = hdrLimitEv,
                onHdrLimitEvChange = { hdrLimitEv = it },
                lensBlurAmount = lensBlurAmount,
                onLensBlurAmountChange = { lensBlurAmount = it },
                selectedBokehShape = selectedBokehShape,
                onSelectBokehShape = { selectedBokehShape = it },
                onShowCreateMaskSheet = { showCreateMaskSheet = true },
                modifier = Modifier.width(320.dp)
            )

            VerticalDivider(color = SpectrumInnerBorder, thickness = 1.dp)

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

            LandscapePrimaryDockRail(
                viewModel = viewModel,
                selectedMobileTool = selectedMobileTool,
                onSelectMobileTool = onSelectMobileTool,
                modifier = Modifier.width(64.dp)
            )
        }
    }

    LandscapeDialogsAndSheets(
        viewModel = viewModel,
        showCreateMaskSheet = showCreateMaskSheet,
        onDismissCreateMaskSheet = { showCreateMaskSheet = false },
        showViewOptionsMenu = showViewOptionsMenu,
        onDismissViewOptionsMenu = { showViewOptionsMenu = false },
        showResetDialog = showResetDialog,
        onDismissResetDialog = { showResetDialog = false },
        showExportConfigSheet = showExportConfigSheet,
        onDismissExportConfigSheet = { showExportConfigSheet = false },
        batchPickerLauncher = batchPickerLauncher
    )
}

// =============================================================================
// 1. LEFT CANVAS WORKSPACE COMPONENT
// =============================================================================
@Composable
internal fun LandscapeCanvasWorkspace(
    viewModel: EditorViewModel,
    selectedMobileTool: String?,
    selectedCurveChannel: CurveChannel,
    isHdrModeActive: Boolean,
    isCleanScreenMode: Boolean,
    onBack: (() -> Unit)?,
    showHistoryMenu: Boolean,
    onShowHistoryMenu: () -> Unit,
    onDismissHistoryMenu: () -> Unit,
    onShowViewOptionsMenu: () -> Unit,
    showEditorOverflowMenu: Boolean,
    onShowEditorOverflowMenu: () -> Unit,
    onDismissEditorOverflowMenu: () -> Unit,
    onShowExportConfigSheet: () -> Unit,
    onShowResetDialog: () -> Unit,
    onShowCreateMaskSheet: () -> Unit,
    isAspectLocked: Boolean,
    onToggleAspectLocked: () -> Unit,
    isLensBlurDepthDetecting: Boolean,
    onCancelLensBlurDetecting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val recipe by viewModel.recipe.collectAsState()
    val bitmap by viewModel.bitmap.collectAsState()
    val histogramData by viewModel.histogramData.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val altClippingMode by viewModel.altClippingMode.collectAsState()
    val isComparingOriginal by viewModel.isComparingOriginal.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val isTatActive by viewModel.isTatActive.collectAsState()
    val activeTatTargetName by viewModel.activeTatTargetName.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()
    val brushFeather by viewModel.brushFeather.collectAsState()
    val activeMaskLayerIndex by viewModel.activeMaskLayerIndex.collectAsState()
    val canvasBackground by viewModel.canvasBackground.collectAsState()
    val isCanvasMattingActive by viewModel.isCanvasMattingActive.collectAsState()
    val canvasMattingWidthDp by viewModel.canvasMattingWidthDp.collectAsState()
    val isCropActive by viewModel.isCropActive.collectAsState()
    val isSplitView by viewModel.isSplitView.collectAsState()
    val splitPosition by viewModel.splitPosition.collectAsState()
    val isSplitHorizontal by viewModel.isSplitHorizontal.collectAsState()
    val isInfoOverlayVisible by viewModel.isInfoOverlayVisible.collectAsState()
    val photoExif by viewModel.photoExif.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()
    val aspectRatioGuide by viewModel.aspectRatioGuide.collectAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(canvasBackground.color)
            .then(
                if (isCanvasMattingActive) {
                    Modifier
                        .padding(canvasMattingWidthDp.dp)
                        .border(1.dp, Color(0xFF505050))
                } else {
                    Modifier
                }
            )
    ) {
        LoupeCanvas(
            bitmap = bitmap,
            recipe = recipe,
            altClippingMode = altClippingMode,
            isComparingOriginal = isComparingOriginal,
            onComparingOriginalChanged = { viewModel.setComparingOriginal(it) },
            onDoubleTapZoom = { viewModel.toggleZoom100Percent() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onHistogramUpdated = { viewModel.updateHistogram(it) },
            isTatActive = isTatActive,
            tatTargetName = activeTatTargetName,
            onTatSample = { x, y -> viewModel.sampleTatPixel(x, y) },
            onTatDrag = { dy -> viewModel.dragTat(dy) },
            onTatDismiss = { viewModel.toggleTat() },
            activeTool = activeTool,
            onToolDismiss = { viewModel.setActiveTool(null) },
            brushSize = brushSize,
            brushFeather = brushFeather,
            onSingleTap = { viewModel.toggleCleanScreenMode() },
            onTwoFingerLongPress = { viewModel.toggleCanvasMatting() },
            isSplitView = isSplitView,
            splitPosition = splitPosition,
            onSplitPositionChanged = { viewModel.setSplitPosition(it) },
            isSplitHorizontal = isSplitHorizontal,
            onToggleSplitOrientation = { viewModel.toggleSplitOrientation() },
            isInfoOverlayVisible = isInfoOverlayVisible,
            photoExif = photoExif,
            showGrid = showGrid,
            aspectRatioGuide = aspectRatioGuide,
            isCropActive = (selectedMobileTool == "Crop" || isCropActive),
            isExpandEnabled = recipe.crop.isExpandEnabled,
            isCurveActive = (activeTab == EditorToolTab.CURVES),
            curvePoints = when (selectedCurveChannel) {
                CurveChannel.RGB -> recipe.curves.composite
                CurveChannel.RED -> recipe.curves.red
                CurveChannel.GREEN -> recipe.curves.green
                CurveChannel.BLUE -> recipe.curves.blue
            },
            curveChannelColor = selectedCurveChannel.color,
            onCurvePointChanged = { updated ->
                val updatedCurves = when (selectedCurveChannel) {
                    CurveChannel.RGB -> recipe.curves.copy(composite = updated, pointCurve = updated)
                    CurveChannel.RED -> recipe.curves.copy(red = updated)
                    CurveChannel.GREEN -> recipe.curves.copy(green = updated)
                    CurveChannel.BLUE -> recipe.curves.copy(blue = updated)
                }
                viewModel.updateRecipe("Tone Curve") { it.copy(curves = updatedCurves) }
            },
            onCurveTouchStart = { viewModel.onSliderTouchStart() },
            onCurveTouchEnd = { viewModel.onSliderTouchEnd(it) },
            modifier = Modifier.fillMaxSize()
        )

        if (!isCleanScreenMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onBack?.invoke() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_back_chevron),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (selectedMobileTool == "Crop") {
                    Surface(
                        color = Color(0xFF262628),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.resetCrop()
                        }
                    ) {
                        Text(
                            text = "Original",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                } else if (isHdrModeActive || recipe.isHdrEnabled) {
                    Surface(
                        color = SpectrumBlue,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "HDR",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedMobileTool == "Crop") {
                        IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_context_help),
                                contentDescription = "Help",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .pointerInput(canUndo) {
                                        detectTapGestures(
                                            onTap = { if (canUndo) viewModel.undo() },
                                            onLongPress = {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                onShowHistoryMenu()
                                            }
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_undo),
                                    contentDescription = "Undo",
                                    tint = if (canUndo) Color.White else SpectrumIconDisabled,
                                    modifier = Modifier.size(20.dp)
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_chevron_down),
                                    contentDescription = null,
                                    tint = if (canUndo) Color.White else SpectrumIconDisabled,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showHistoryMenu,
                                onDismissRequest = onDismissHistoryMenu,
                                modifier = Modifier
                                    .background(SpectrumElevated)
                                    .border(1.dp, SpectrumInnerBorder, RoundedCornerShape(8.dp))
                            ) {
                                val historySteps = viewModel.getHistorySteps()
                                Text(
                                    text = "Edit History (${historySteps.size} steps)",
                                    color = SpectrumTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = SpectrumInnerBorder)
                                historySteps.forEachIndexed { index, stepName ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${index + 1}. $stepName",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            viewModel.restoreToHistoryStep(index)
                                            onDismissHistoryMenu()
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { if (canRedo) viewModel.redo() },
                            enabled = canRedo,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_redo),
                                contentDescription = "Redo",
                                tint = if (canRedo) Color.White else SpectrumIconDisabled,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(36.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.setComparingOriginal(true)
                                            tryAwaitRelease()
                                            viewModel.setComparingOriginal(false)
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_hold_original_gesture),
                                contentDescription = "Before / After Compare",
                                tint = if (isComparingOriginal) SpectrumBlue else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onShowExportConfigSheet,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_share_export),
                                contentDescription = "Share & Export",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = onShowEditorOverflowMenu,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_more_vert),
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showEditorOverflowMenu,
                                onDismissRequest = onDismissEditorOverflowMenu,
                                modifier = Modifier
                                    .background(SpectrumElevated)
                                    .border(1.dp, SpectrumInnerBorder, RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Options...", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_context_help),
                                            contentDescription = null,
                                            tint = SpectrumIconNormal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        onDismissEditorOverflowMenu()
                                        onShowViewOptionsMenu()
                                    }
                                )
                                HorizontalDivider(color = SpectrumInnerBorder)
                                DropdownMenuItem(
                                    text = { Text("Reset Adjustments", color = Color(0xFFFF5252), fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_generative_refresh),
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        onDismissEditorOverflowMenu()
                                        onShowResetDialog()
                                    }
                                )
                            }
                        }
                    }
                }
            }

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

            if (selectedMobileTool == "Remove") {
                Surface(
                    color = Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF3E3E3E)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_extra_people),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Remove extra people",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (activeTab == EditorToolTab.BLUR && isLensBlurDepthDetecting) {
                Surface(
                    color = Color(0xFF1473E6),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.7f)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Detecting depth using AI...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Cancel",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onCancelLensBlurDetecting() }
                        )
                    }
                }
            }

            if (selectedMobileTool == "Crop") {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CurvedStraightenDialLandscape(
                        angle = recipe.crop.straightenAngle,
                        onAngleChange = { viewModel.setStraightenAngle(it) }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF262626),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lr_hold_original_gesture),
                                        contentDescription = "Preview",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF262626),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onToggleAspectLocked()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(if (isAspectLocked) R.drawable.ic_lr_lock else R.drawable.ic_lr_unlock),
                                        contentDescription = "Aspect lock",
                                        tint = if (isAspectLocked) Color(0xFF1686FF) else Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF262626),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.rotateCrop(true)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lr_generative_refresh),
                                        contentDescription = "Rotate 90deg",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF262626),
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.flipCrop(true)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lr_flip_horizontal),
                                        contentDescription = "Flip",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isInfoOverlayVisible) {
                InteractiveHistogram(
                    histogramData = histogramData,
                    basicAdjustments = recipe.basic,
                    exifInfo = photoExif,
                    onAdjustBlacks = { delta ->
                        viewModel.updateRecipe { it.copy(basic = it.basic.copy(blacks = (it.basic.blacks + delta).coerceIn(-100f, 100f))) }
                    },
                    onAdjustShadows = { delta ->
                        viewModel.updateRecipe { it.copy(basic = it.basic.copy(shadows = (it.basic.shadows + delta).coerceIn(-100f, 100f))) }
                    },
                    onAdjustExposure = { delta ->
                        viewModel.updateRecipe { it.copy(basic = it.basic.copy(exposure = (it.basic.exposure + delta).coerceIn(-5f, 5f))) }
                    },
                    onAdjustHighlights = { delta ->
                        viewModel.updateRecipe { it.copy(basic = it.basic.copy(highlights = (it.basic.highlights + delta).coerceIn(-100f, 100f))) }
                    },
                    onAdjustWhites = { delta ->
                        viewModel.updateRecipe { it.copy(basic = it.basic.copy(whites = (it.basic.whites + delta).coerceIn(-100f, 100f))) }
                    },
                    onResetZone = { zone ->
                        when (zone) {
                            HistogramZone.BLACKS -> viewModel.updateRecipe("Reset Blacks") { it.copy(basic = it.basic.copy(blacks = 0f)) }
                            HistogramZone.SHADOWS -> viewModel.updateRecipe("Reset Shadows") { it.copy(basic = it.basic.copy(shadows = 0f)) }
                            HistogramZone.EXPOSURE -> viewModel.updateRecipe("Reset Exposure") { it.copy(basic = it.basic.copy(exposure = 0f)) }
                            HistogramZone.HIGHLIGHTS -> viewModel.updateRecipe("Reset Highlights") { it.copy(basic = it.basic.copy(highlights = 0f)) }
                            HistogramZone.WHITES -> viewModel.updateRecipe("Reset Whites") { it.copy(basic = it.basic.copy(whites = 0f)) }
                            else -> {}
                        }
                    },
                    onJumpToZone = { viewModel.setActiveTab(EditorToolTab.LIGHT) },
                    onDragStart = { viewModel.onSliderTouchStart() },
                    onDragEnd = { zoneName -> viewModel.onSliderTouchEnd("Histogram $zoneName") },
                    onToggleShadowClipping = { viewModel.setAltClippingMode(if (altClippingMode == 2) 0 else 2) },
                    onToggleHighlightClipping = { viewModel.setAltClippingMode(if (altClippingMode == 1) 0 else 1) },
                    isShadowClippingActive = altClippingMode == 2,
                    isHighlightClippingActive = altClippingMode == 1
                )
            }

            exportStatus?.let { status ->
                Surface(
                    color = SpectrumToastSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SpectrumToastBorder),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = status,
                            color = SpectrumTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { viewModel.clearExportStatus() }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2A. ADJUSTMENT COLUMN COMPONENT
// =============================================================================
@Composable
internal fun LandscapeAdjustmentColumn(
    viewModel: EditorViewModel,
    selectedMobileTool: String?,
    activeTab: EditorToolTab,
    isSliderDragging: Boolean,
    selectedMaskCategory: LocalAdjustmentCategory,
    onSelectMaskCategory: (LocalAdjustmentCategory) -> Unit,
    cropPage: String,
    onSelectCropPage: (String) -> Unit,
    selectedAspectCategory: String,
    onSelectAspectCategory: (String) -> Unit,
    colorSubTab: String,
    onSelectColorSubTab: (String) -> Unit,
    effectsSubTab: String,
    onSelectEffectsSubTab: (String) -> Unit,
    detailSubTab: String,
    onSelectDetailSubTab: (String) -> Unit,
    selectedProfileGroup: String,
    onSelectProfileGroup: (String) -> Unit,
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    isHdrModeActive: Boolean,
    onToggleHdrMode: (Boolean) -> Unit,
    hdrLimitEv: Float,
    onHdrLimitEvChange: (Float) -> Unit,
    lensBlurAmount: Float,
    onLensBlurAmountChange: (Float) -> Unit,
    selectedBokehShape: String,
    onSelectBokehShape: (String) -> Unit,
    onShowCreateMaskSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recipe by viewModel.recipe.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val presetAmount by viewModel.presetAmount.collectAsState()
    val allPresets by viewModel.allPresets.collectAsState()
    val activeMaskLayerIndex by viewModel.activeMaskLayerIndex.collectAsState()
    val aspectRatioGuide by viewModel.aspectRatioGuide.collectAsState()
    val adjustmentScrollState = rememberScrollState()

    val animatedPanelAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging) 0.15f else 1.0f,
        animationSpec = if (isSliderDragging) tween(150) else tween(200, easing = FastOutSlowInEasing),
        label = "panelAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(SpectrumSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(adjustmentScrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (selectedMobileTool) {
                "Crop" -> {
                    CropLandscapeAdjustmentPanel(
                        crop = recipe.crop,
                        cropPage = cropPage,
                        onCropPageSelected = onSelectCropPage,
                        selectedAspectCategory = selectedAspectCategory,
                        onAspectCategorySelected = onSelectAspectCategory,
                        aspectRatioGuide = aspectRatioGuide,
                        onAspectRatioSelected = { viewModel.setAspectRatioGuide(it) },
                        isExpandEnabled = recipe.crop.isExpandEnabled,
                        onExpandToggled = { viewModel.setExpandEnabled(it) },
                        constrainCrop = recipe.crop.constrainCrop,
                        onConstrainCropToggled = { viewModel.setConstrainCrop(it) },
                        uprightMode = recipe.crop.uprightMode,
                        onUprightModeSelected = { viewModel.setUprightMode(it) },
                        distortion = recipe.crop.distortion,
                        onDistortionChanged = { viewModel.updateRecipe("Distortion") { r -> r.copy(crop = r.crop.copy(distortion = it)) } },
                        vertical = recipe.crop.verticalPerspective,
                        onVerticalChanged = { viewModel.updateRecipe("Vertical") { r -> r.copy(crop = r.crop.copy(verticalPerspective = it)) } },
                        horizontal = recipe.crop.horizontalPerspective,
                        onHorizontalChanged = { viewModel.updateRecipe("Horizontal") { r -> r.copy(crop = r.crop.copy(horizontalPerspective = it)) } },
                        rotate = recipe.crop.geometryRotate,
                        onRotateChanged = { viewModel.updateRecipe("Geometry Rotate") { r -> r.copy(crop = r.crop.copy(geometryRotate = it)) } },
                        aspect = recipe.crop.geometryAspect,
                        onAspectChanged = { viewModel.updateRecipe("Geometry Aspect") { r -> r.copy(crop = r.crop.copy(geometryAspect = it)) } },
                        scale = recipe.crop.geometryScale,
                        onScaleChanged = { viewModel.updateRecipe("Geometry Scale") { r -> r.copy(crop = r.crop.copy(geometryScale = it)) } },
                        xOffset = recipe.crop.geometryXOffset,
                        onXOffsetChanged = { viewModel.updateRecipe("Geometry X") { r -> r.copy(crop = r.crop.copy(geometryXOffset = it)) } },
                        yOffset = recipe.crop.geometryYOffset,
                        onYOffsetChanged = { viewModel.updateRecipe("Geometry Y") { r -> r.copy(crop = r.crop.copy(geometryYOffset = it)) } },
                        onTouchStart = { viewModel.onSliderTouchStart() },
                        onTouchEnd = { name -> viewModel.onSliderTouchEnd(name) }
                    )
                }

                "Masking" -> {
                    MaskingSlidersPanel(
                        layers = recipe.masks,
                        activeLayerIndex = activeMaskLayerIndex,
                        selectedCategory = selectedMaskCategory,
                        onCategorySelected = onSelectMaskCategory,
                        onUpdateLayerAdjustments = { layer: MaskLayer -> viewModel.updateMaskLayer(layer) },
                        onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                        onAddSubMaskToActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        onSubtractSubMaskFromActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        onIntersectSubMaskWithActive = { subMask: SubMask -> viewModel.addSubMaskToActive(subMask) },
                        showCategoryTabs = false
                    )
                }

                "Remove" -> {
                    RemoveLandscapeAdjustmentPanel(
                        brushSize = viewModel.brushSize.collectAsState().value,
                        onBrushSizeChange = { viewModel.setBrushSize(it) }
                    )
                }

                "Presets" -> {
                    PresetsLandscapeAdjustmentPanel(
                        allPresets = allPresets,
                        activePreset = activePreset,
                        presetAmount = presetAmount,
                        onSelectPreset = { viewModel.applyPreset(it) },
                        onPresetAmountChange = { viewModel.adjustPresetAmount(it) }
                    )
                }

                "Actions" -> {
                    ActionsLandscapeAdjustmentPanel(
                        onAutoTone = { viewModel.autoTone() },
                        onQuickAction = { action ->
                            when (action) {
                                "B&W" -> {
                                    val isBw = (recipe.basic.saturation <= -99.9f)
                                    viewModel.updateRecipe(if (isBw) "Exit B&W" else "Enter B&W") {
                                        val nextSat = if (isBw) 0f else -100f
                                        it.copy(
                                            basic = it.basic.copy(saturation = nextSat),
                                            presence = it.presence.copy(saturation = nextSat)
                                        ).syncLegacyFields()
                                    }
                                }
                                else -> {}
                            }
                        }
                    )
                }

                else -> {
                    when (activeTab) {
                        EditorToolTab.LIGHT -> {
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

                        EditorToolTab.COLOR -> {
                            ColorLandscapeAdjustmentPanel(
                                recipe = recipe,
                                colorSubTab = colorSubTab,
                                onColorSubTabSelected = onSelectColorSubTab,
                                viewModel = viewModel
                            )
                        }

                        EditorToolTab.CURVES -> {
                            CurvesLandscapeAdjustmentPanel(
                                recipe = recipe,
                                viewModel = viewModel
                            )
                        }

                        EditorToolTab.BLUR -> {
                            BlurLandscapeAdjustmentPanel(
                                blurAmount = lensBlurAmount,
                                selectedBokehShape = selectedBokehShape,
                                onBlurAmountChange = onLensBlurAmountChange,
                                onBokehShapeChange = onSelectBokehShape
                            )
                        }

                        EditorToolTab.EFFECTS -> {
                            EffectsLandscapeAdjustmentPanel(
                                recipe = recipe,
                                subTab = effectsSubTab,
                                onSubTabChange = onSelectEffectsSubTab,
                                viewModel = viewModel
                            )
                        }

                        EditorToolTab.DETAIL -> {
                            DetailLandscapeAdjustmentPanel(
                                recipe = recipe,
                                subTab = detailSubTab,
                                onSubTabChange = onSelectDetailSubTab,
                                viewModel = viewModel
                            )
                        }

                        EditorToolTab.OPTICS -> {
                            OpticsLandscapeAdjustmentPanel(
                                recipe = recipe,
                                removeCa = recipe.optics.removeChromaticAberration,
                                enableLensCorrections = recipe.optics.enableLensCorrections,
                                onRemoveCaChange = { rca ->
                                    viewModel.updateRecipe { it.copy(optics = it.optics.copy(removeChromaticAberration = rca)) }
                                },
                                onEnableLensCorrectionsChange = { elc ->
                                    viewModel.updateRecipe { it.copy(optics = it.optics.copy(enableLensCorrections = elc)) }
                                },
                                viewModel = viewModel
                            )
                        }

                        EditorToolTab.PROFILES -> {
                            ProfilesLandscapeAdjustmentPanel(
                                selectedGroup = selectedProfileGroup,
                                selectedProfile = selectedProfile,
                                onGroupSelected = onSelectProfileGroup,
                                onProfileSelected = onSelectProfile
                            )
                        }

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
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2B. SUB-TOOL VERTICAL STRIP COMPONENT
// =============================================================================
@Composable
internal fun LandscapeSubToolRail(
    viewModel: EditorViewModel,
    activeTab: EditorToolTab,
    modifier: Modifier = Modifier
) {
    val recipe by viewModel.recipe.collectAsState()
    val bypassedTabs by viewModel.bypassedTabs.collectAsState()

    val subTabs = listOf(
        EditorToolTab.LIGHT,
        EditorToolTab.COLOR,
        EditorToolTab.BLUR,
        EditorToolTab.EFFECTS,
        EditorToolTab.DETAIL,
        EditorToolTab.OPTICS,
        EditorToolTab.PROFILES
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SpectrumSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        subTabs.forEach { tab ->
            val isSelected = (activeTab == tab)
            val isModified = remember(recipe, tab) { viewModel.isTabModified(tab) }
            val isBypassed = bypassedTabs.contains(tab)

            val iconRes = when (tab) {
                EditorToolTab.LIGHT -> R.drawable.ic_lr_light
                EditorToolTab.COLOR -> R.drawable.ic_lr_color
                EditorToolTab.BLUR -> R.drawable.ic_lr_blur
                EditorToolTab.EFFECTS -> R.drawable.ic_lr_effects
                EditorToolTab.DETAIL -> R.drawable.ic_lr_detail
                EditorToolTab.OPTICS -> R.drawable.ic_lr_optics
                EditorToolTab.PROFILES -> R.drawable.ic_lr_profiles
                else -> R.drawable.ic_lr_light
            }

            SubToolRailItem(
                iconRes = iconRes,
                label = tab.title,
                isSelected = isSelected,
                isModified = isModified,
                isBypassed = isBypassed,
                onClick = { viewModel.setActiveTab(tab) },
                onLongClick = { viewModel.toggleBypassTab(tab) },
                onDoubleClick = { viewModel.resetTab(tab) }
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

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

// =============================================================================
// 2C. PRIMARY DOCK VERTICAL RAIL COMPONENT
// =============================================================================
@Composable
internal fun LandscapePrimaryDockRail(
    viewModel: EditorViewModel,
    selectedMobileTool: String?,
    onSelectMobileTool: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SpectrumSurface)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                "Masking" to R.drawable.ic_lr_masking,
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
    }
}

// =============================================================================
// 3. DIALOGS & BOTTOM SHEETS COMPONENT
// =============================================================================
@Composable
internal fun LandscapeDialogsAndSheets(
    viewModel: EditorViewModel,
    showCreateMaskSheet: Boolean,
    onDismissCreateMaskSheet: () -> Unit,
    showViewOptionsMenu: Boolean,
    onDismissViewOptionsMenu: () -> Unit,
    showResetDialog: Boolean,
    onDismissResetDialog: () -> Unit,
    showExportConfigSheet: Boolean,
    onDismissExportConfigSheet: () -> Unit,
    batchPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val isInfoOverlayVisible by viewModel.isInfoOverlayVisible.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()
    val isZoomLocked by viewModel.isZoomLocked.collectAsState()

    if (showCreateMaskSheet) {
        CreateNewMaskBottomSheet(
            onDismissRequest = onDismissCreateMaskSheet,
            onSelectSubMask = { subMask ->
                viewModel.addMaskLayer(subMask)
                onDismissCreateMaskSheet()
            }
        )
    }

    if (showViewOptionsMenu) {
        AlertDialog(
            onDismissRequest = onDismissViewOptionsMenu,
            title = {
                Text("View Options", color = SpectrumTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Info Overlay", color = SpectrumTextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = isInfoOverlayVisible,
                            onCheckedChange = { viewModel.toggleInfoOverlay() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grid & Composition Guide", color = SpectrumTextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = showGrid,
                            onCheckedChange = { viewModel.toggleGrid() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lock 100% Zoom", color = SpectrumTextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = isZoomLocked,
                            onCheckedChange = { viewModel.toggleZoomLock() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissViewOptionsMenu) {
                    Text("Done", color = SpectrumBlue)
                }
            },
            containerColor = SpectrumElevated
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = onDismissResetDialog,
            title = { Text("Reset Adjustments?", color = SpectrumTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore all adjustments, color grading, curves, and crop back to original import state.",
                    color = SpectrumTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAll()
                        onDismissResetDialog()
                    }
                ) {
                    Text("Reset", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissResetDialog) {
                    Text("Cancel", color = SpectrumTextSecondary)
                }
            },
            containerColor = SpectrumElevated
        )
    }

    if (showExportConfigSheet) {
        com.cleanlr.ui.export.ExportConfigBottomSheet(
            onDismiss = onDismissExportConfigSheet,
            onExportConfirmed = { config ->
                viewModel.exportImage(config)
                onDismissExportConfigSheet()
            },
            onBatchExportRequested = { config ->
                batchPickerLauncher.launch("image/*")
                onDismissExportConfigSheet()
            }
        )
    }
}

// =============================================================================
// SUB-TOOL RAIL ITEM (Column 2B)
// =============================================================================
@Composable
private fun SubToolRailItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    isModified: Boolean,
    isBypassed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {}
) {
    Surface(
        color = if (isSelected) Color(0xFF2C2C2C) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(label) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isSelected) Color.White else if (isBypassed) SpectrumIconDisabled else SpectrumIconNormal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else if (isBypassed) SpectrumIconDisabled else SpectrumTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isModified) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(SpectrumBlue, CircleShape)
                    )
                }
            }
        }
    }
}

// =============================================================================
// PRIMARY DOCK RAIL ITEM (Column 2C)
// =============================================================================
@Composable
private fun PrimaryDockRailItem(
    iconRes: Int,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2680EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFF2680EB) else Color(0xFF9E9E9E),
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
