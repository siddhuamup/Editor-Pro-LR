package com.cleanlr.ui.editor

import android.content.res.Configuration
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.ui.components.*
import com.cleanlr.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

// Tip 22: 12.dp fading edge on scrollable panels using CompositingStrategy.Offscreen
fun Modifier.fadingEdge(
    scrollState: ScrollState,
    topEdgeHeight: Dp = 12.dp,
    bottomEdgeHeight: Dp = 12.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val topFadingPx = topEdgeHeight.toPx()
        val bottomFadingPx = bottomEdgeHeight.toPx()

        if (scrollState.value > 0) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadingPx
                ),
                blendMode = BlendMode.DstIn
            )
        }

        if (scrollState.value < scrollState.maxValue) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadingPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val view = LocalView.current
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    val coroutineScope = rememberCoroutineScope()
    val toolScrollState = rememberScrollState()

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
    val tatMode by viewModel.tatMode.collectAsState()
    val activeTatTargetName by viewModel.activeTatTargetName.collectAsState()
    val activeTatWeights by viewModel.activeTatWeights.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()
    val brushFeather by viewModel.brushFeather.collectAsState()

    val versions by viewModel.versions.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val presetAmount by viewModel.presetAmount.collectAsState()
    val selectedPresetGroup by viewModel.selectedPresetGroup.collectAsState()
    val isMaskOverlayVisible by viewModel.isMaskOverlayVisible.collectAsState()
    val activeMaskLayerIndex by viewModel.activeMaskLayerIndex.collectAsState()

    // Darkroom Ergonomics & Surface Hierarchy States (Tips 1–35)
    val canvasBackground by viewModel.canvasBackground.collectAsState()
    val isCanvasMattingActive by viewModel.isCanvasMattingActive.collectAsState()
    val canvasMattingWidthDp by viewModel.canvasMattingWidthDp.collectAsState()
    val isCleanScreenMode by viewModel.isCleanScreenMode.collectAsState()
    val isCropActive by viewModel.isCropActive.collectAsState()
    val panelSnapState by viewModel.panelSnapState.collectAsState()
    val isSliderDragging by viewModel.isSliderDragging.collectAsState()
    val bypassedTabs by viewModel.bypassedTabs.collectAsState()
    val isColorWbExpanded by viewModel.isColorWbExpanded.collectAsState()
    val isColorVibranceExpanded by viewModel.isColorVibranceExpanded.collectAsState()
    val isColorMixerExpanded by viewModel.isColorMixerExpanded.collectAsState()
    val isColorGradingExpanded by viewModel.isColorGradingExpanded.collectAsState()

    // Tips 81–125 (Loupe, Navigation, Overlays & History)
    val isSplitView by viewModel.isSplitView.collectAsState()
    val splitPosition by viewModel.splitPosition.collectAsState()
    val isSplitHorizontal by viewModel.isSplitHorizontal.collectAsState()
    val isInfoOverlayVisible by viewModel.isInfoOverlayVisible.collectAsState()
    val photoExif by viewModel.photoExif.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()
    val aspectRatioGuide by viewModel.aspectRatioGuide.collectAsState()
    val isZoomLocked by viewModel.isZoomLocked.collectAsState()

    var showHistoryMenu by remember { mutableStateOf(false) }
    var showViewOptionsMenu by remember { mutableStateOf(false) }
    var showExportConfigSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedMobileTool by remember { mutableStateOf("Edit") }

    val allPresets by viewModel.allPresets.collectAsState()
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var colorSubTab by remember { mutableStateOf("WB") }
    var effectsSubTab by remember { mutableStateOf("Presence") }
    var detailSubTab by remember { mutableStateOf("Sharpening") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val xmpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importXmpPreset(it, context) }
    }

    val activeBatchProgress by viewModel.activeBatchProgress.collectAsState()
    val isBatchExportDialogVisible by viewModel.isBatchExportDialogVisible.collectAsState()

    var pendingBatchConfig by remember { mutableStateOf<com.cleanlr.export.ExportConfig?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(it) }
    }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = canvasBackground.color,
        topBar = {
            if (!isCleanScreenMode) {
                // Tip 10: Compact 48.dp TopAppBar height
                TopAppBar(
                    modifier = Modifier.height(48.dp),
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to library",
                                    tint = SpectrumTextPrimary
                                )
                            }
                        }
                    },
                    title = {
                        if (onBack == null) {
                            Text(
                                text = "Lightroom Clean",
                                color = SpectrumTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SpectrumSurface
                    ),
                    actions = {
                        if (onBack == null) {

                        // Tip 1: Canvas Background Switcher (Pure Black, Dark Gray, Middle Gray, White)
                        IconButton(onClick = { viewModel.cycleCanvasBackground() }) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = "Canvas Background (${canvasBackground.label})",
                                tint = SpectrumIconNormal
                            )
                        }

                        // Tip 122: Undo with long-press history dropdown
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .pointerInput(canUndo) {
                                        detectTapGestures(
                                            onTap = { if (canUndo) viewModel.undo() },
                                            onLongPress = {
                                                if (canUndo) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    showHistoryMenu = true
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo (Tap) / History (Long-Press)",
                                    tint = if (canUndo) SpectrumIconNormal else SpectrumIconDisabled
                                )
                            }

                            DropdownMenu(
                                expanded = showHistoryMenu,
                                onDismissRequest = { showHistoryMenu = false },
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
                                                color = if (index == historySteps.lastIndex) SpectrumBlue else SpectrumTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (index == historySteps.lastIndex) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.restoreToHistoryStep(index)
                                            showHistoryMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Redo
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) SpectrumIconNormal else SpectrumIconDisabled
                            )
                        }

                        // Targeted Adjustment Tool (TAT)
                        IconButton(onClick = { viewModel.toggleTat() }) {
                            Icon(
                                imageVector = Icons.Default.Adjust,
                                contentDescription = "Targeted Adjustment Tool (TAT)",
                                tint = if (isTatActive) SpectrumBlue else SpectrumTextPrimary
                            )
                        }

                        // Tip 121: Dual Behavior Compare (Tap = Toggle Split View; Press & Hold = Temporary Before)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.toggleSplitView()
                                        },
                                        onPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.setComparingOriginal(true)
                                            tryAwaitRelease()
                                            viewModel.setComparingOriginal(false)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = "Compare (Tap: Split / Hold: Before)",
                                tint = if (isComparingOriginal || isSplitView) SpectrumBlue else SpectrumTextPrimary
                            )
                        }

                        // Alt / Option Key Secret Previews Toggle (Section 4.1)
                        Surface(
                            color = if (altClippingMode > 0) SpectrumBlue else SpectrumElevated,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clickable {
                                    val nextMode = when (altClippingMode) {
                                        0 -> 1 // Mode 1: Whites / Exposure
                                        1 -> 2 // Mode 2: Blacks / Shadows
                                        2 -> 3 // Mode 3: Masking
                                        else -> 0
                                    }
                                    viewModel.setAltClippingMode(nextMode)
                                }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = when (altClippingMode) {
                                    1 -> "ALT:HI"
                                    2 -> "ALT:SH"
                                    3 -> "ALT:MSK"
                                    else -> "ALT"
                                },
                                color = if (altClippingMode > 0) Color.White else SpectrumTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // View Options & Guides Dropdown (Tips 98, 100, 118, 119)
                        Box {
                            IconButton(onClick = { showViewOptionsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "View Options & Guides",
                                    tint = if (isInfoOverlayVisible || showGrid || aspectRatioGuide != null || isZoomLocked) SpectrumBlue else SpectrumTextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showViewOptionsMenu,
                                onDismissRequest = { showViewOptionsMenu = false },
                                modifier = Modifier
                                    .background(SpectrumElevated)
                                    .border(1.dp, SpectrumInnerBorder, RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (isInfoOverlayVisible) SpectrumBlue else SpectrumTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isInfoOverlayVisible) "Hide Info HUD (I)" else "Show Info HUD (I)",
                                                color = SpectrumTextPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleInfoOverlay()
                                        showViewOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.GridOn,
                                                contentDescription = null,
                                                tint = if (showGrid) SpectrumBlue else SpectrumTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (showGrid) "Hide 3x3 Grid" else "Show 3x3 Grid",
                                                color = SpectrumTextPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleGrid()
                                        showViewOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AspectRatio,
                                                contentDescription = null,
                                                tint = if (aspectRatioGuide != null) SpectrumBlue else SpectrumTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Guide: ${aspectRatioGuide ?: "None"} (Tap to Cycle)",
                                                color = SpectrumTextPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.cycleAspectRatioGuide()
                                        showViewOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isZoomLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = if (isZoomLocked) SpectrumBlue else SpectrumTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isZoomLocked) "Zoom Locked (Preserved)" else "Lock Zoom & Pan",
                                                color = SpectrumTextPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleZoomLock()
                                        showViewOptionsMenu = false
                                    }
                                )
                                HorizontalDivider(color = SpectrumInnerBorder)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy Edit Settings", color = SpectrumTextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        viewModel.copyEditSettings()
                                        showViewOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Paste Edit Settings", color = SpectrumTextPrimary, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        viewModel.pasteEditSettings()
                                        showViewOptionsMenu = false
                                    }
                                )
                                HorizontalDivider(color = SpectrumInnerBorder)
                                Text(
                                    text = "DEMO SCENES (Tip 525)",
                                    color = SpectrumTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                                EditorViewModel.DemoScene.values().forEach { scene ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(scene.title, color = SpectrumTextPrimary, fontSize = 12.sp)
                                        },
                                        onClick = {
                                            viewModel.loadDemoScene(scene)
                                            showViewOptionsMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Pick image
                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Open Photo",
                                tint = SpectrumTextPrimary
                            )
                        }

                        // Tip 120: Export (Tap: Settings Sheet; Long-Press: Instant Quick Export & Share)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { showExportConfigSheet = true },
                                        onLongPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.exportImage(com.cleanlr.export.ExportConfig(autoShare = true))
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export Image (Tap) / Quick Share (Long-Press)",
                                tint = SpectrumBlue
                            )
                        }

                        } else {
                            IconButton(onClick = { if (canUndo) viewModel.undo() }, enabled = canUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = SpectrumTextPrimary)
                            }
                            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = SpectrumTextPrimary)
                            }
                            IconButton(onClick = { viewModel.exportImage(com.cleanlr.export.ExportConfig(autoShare = true)) }) {
                                Icon(Icons.Default.Share, contentDescription = "Export and share", tint = SpectrumTextPrimary)
                            }
                            Box {
                                IconButton(onClick = { showViewOptionsMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = SpectrumTextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showViewOptionsMenu,
                                    onDismissRequest = { showViewOptionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Compare before and after") },
                                        onClick = { viewModel.toggleSplitView(); showViewOptionsMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open another photo") },
                                        onClick = { imagePickerLauncher.launch("image/*"); showViewOptionsMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export settings") },
                                        onClick = { showExportConfigSheet = true; showViewOptionsMenu = false }
                                    )
                                }
                            }
                        }
                    }
            )
        }
    },
    bottomBar = {
        if (!isCleanScreenMode) {
            MobilePrimaryToolbar(
                selectedTool = selectedMobileTool,
                onToolSelected = { tool ->
                    if (tool == "Crop") {
                        viewModel.toggleCropActive()
                        selectedMobileTool = if (viewModel.isCropActive.value) "Crop" else "Edit"
                    } else {
                        if (viewModel.isCropActive.value) viewModel.toggleCropActive()
                        selectedMobileTool = tool
                        when (tool) {
                            "Actions" -> { viewModel.autoTone(); selectedMobileTool = "Edit" }
                            "Presets" -> viewModel.setActiveTab(EditorToolTab.PRESETS)
                            "Edit" -> viewModel.setActiveTab(EditorToolTab.LIGHT)
                            "Masking" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                            "Remove" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                        }
                    }
                }
            )
        }
    }
) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Loupe Canvas (Preview Area) with Canvas Background & 1.dp Inner Border (Tips 1, 2, 4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
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
                    .border(1.dp, SpectrumInnerBorder)
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
                    modifier = Modifier.fillMaxSize()
                )

                // Tip 35: Floating Pill Dark Toast (#2C2C2C, #3E3E3E border, 12.dp radius)
                exportStatus?.let { status ->
                    val lastExportedUri by viewModel.lastExportedUri.collectAsState()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        color = SpectrumToastSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumToastBorder),
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

                            if (lastExportedUri != null && status.contains("Exported")) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SpectrumBlue)
                                        .clickable { viewModel.shareLastExport(context) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Share",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isCleanScreenMode) {
                // Interactive Live Histogram with 5 drag zones (Tips 126–160)
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
                            com.cleanlr.ui.components.HistogramZone.BLACKS -> viewModel.updateRecipe("Reset Blacks") { it.copy(basic = it.basic.copy(blacks = 0f)) }
                            com.cleanlr.ui.components.HistogramZone.SHADOWS -> viewModel.updateRecipe("Reset Shadows") { it.copy(basic = it.basic.copy(shadows = 0f)) }
                            com.cleanlr.ui.components.HistogramZone.EXPOSURE -> viewModel.updateRecipe("Reset Exposure") { it.copy(basic = it.basic.copy(exposure = 0f)) }
                            com.cleanlr.ui.components.HistogramZone.HIGHLIGHTS -> viewModel.updateRecipe("Reset Highlights") { it.copy(basic = it.basic.copy(highlights = 0f)) }
                            com.cleanlr.ui.components.HistogramZone.WHITES -> viewModel.updateRecipe("Reset Whites") { it.copy(basic = it.basic.copy(whites = 0f)) }
                            else -> {}
                        }
                    },
                    onJumpToZone = {
                        viewModel.setActiveTab(EditorToolTab.LIGHT)
                    },
                    onDragStart = { viewModel.onSliderTouchStart() },
                    onDragEnd = { zoneName -> viewModel.onSliderTouchEnd("Histogram $zoneName") },
                    onToggleShadowClipping = {
                        viewModel.setAltClippingMode(if (altClippingMode == 2) 0 else 2)
                    },
                    onToggleHighlightClipping = {
                        viewModel.setAltClippingMode(if (altClippingMode == 1) 0 else 1)
                    },
                    isShadowClippingActive = altClippingMode == 2,
                    isHighlightClippingActive = altClippingMode == 1
                )

                // Tip 5: 8.dp Subtle Top Shadow between Canvas & Tool Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x66000000))
                            )
                        )
                )

                // Category Tool Tabs Bar — Adobe Lightroom Mobile Signature Style
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpectrumSurface)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    // Adobe Signature "Auto" One-Touch Button
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.autoTone()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Auto Tone",
                                tint = SpectrumIconNormal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Auto",
                                color = SpectrumTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    items(EditorToolTab.values()) { tab ->
                        val isSelected = tab == activeTab
                        val isModified = viewModel.isTabModified(tab)
                        val isBypassed = bypassedTabs.contains(tab)
                        val tabIcon = when (tab) {
                            EditorToolTab.CROP -> Icons.Default.Crop
                            EditorToolTab.LIGHT -> Icons.Default.Brightness6
                            EditorToolTab.COLOR -> Icons.Default.Palette
                            EditorToolTab.EFFECTS -> Icons.Default.AutoAwesome
                            EditorToolTab.DETAIL -> Icons.Default.Tune
                            EditorToolTab.CURVES -> Icons.Default.Timeline
                            EditorToolTab.PRESETS -> Icons.Default.Style
                            EditorToolTab.VERSIONS -> Icons.Default.HistoryEdu
                            EditorToolTab.MASKS -> Icons.Default.Layers
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .pointerInput(tab) {
                                    detectTapGestures(
                                        onTap = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.setActiveTab(tab)
                                        },
                                        onLongPress = {
                                            // Tip 25: Long-press to reset tab
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.resetTab(tab)
                                        },
                                        onDoubleTap = {
                                            // Tip 26: Double-tap to bypass tab
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            viewModel.toggleBypassTab(tab)
                                        }
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = tab.title,
                                tint = if (isSelected) SpectrumBlue else if (isBypassed) SpectrumIconDisabled else SpectrumIconNormal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = if (isSelected) SpectrumBlue else if (isBypassed) SpectrumIconDisabled else SpectrumTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            // Tips 23 & 24: Active Modification Indicator Dot
                            if (isModified) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (isBypassed) Color.Gray else SpectrumActiveDot)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    // Reset Action Button
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showResetDialog = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset All",
                                tint = SpectrumIconNormal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Reset",
                                color = SpectrumTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                HorizontalDivider(color = SpectrumBorder, thickness = 1.dp)

                // Tip 6: 3-Snap Panel Height Animation
                val animatedPanelHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = when (panelSnapState) {
                        PanelSnapState.COLLAPSED -> 0.dp
                        PanelSnapState.COMPACT -> 210.dp
                        PanelSnapState.EXPANDED -> 380.dp
                    },
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.8f,
                        stiffness = 400f
                    )
                )

                // Tips 8 & 9: 150ms opacity transition on slider drag
                val animatedPanelAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSliderDragging) 0.15f else 1.0f,
                    animationSpec = if (isSliderDragging) {
                        androidx.compose.animation.core.tween(150)
                    } else {
                        androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    }
                )

                if (animatedPanelHeight > 0.dp) {
                    // Tip 7: Pill Drag Handle (32.dp x 4.dp, #484848, corner radius 2.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpectrumSurface)
                            .clickable { viewModel.cyclePanelSnapState() }
                            .padding(top = 4.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SpectrumPillHandle)
                        )
                    }

                    // Active Panel Controls (Scrollable) with 12.dp fading edge (Tip 22)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedPanelHeight)
                            .background(SpectrumSurface)
                            .graphicsLayer {
                                alpha = animatedPanelAlpha
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(toolScrollState)
                                .padding(bottom = 16.dp)
                        ) {
                    when {
                        isCropActive -> CropAndExpandPanel(
                            aspectRatioGuide = aspectRatioGuide,
                            onAspectRatioSelected = { viewModel.setAspectRatioGuide(it) },
                            onCancel = {
                                viewModel.setAspectRatioGuide(null)
                                viewModel.toggleCropActive()
                                selectedMobileTool = "Edit"
                            },
                            onApply = {
                                viewModel.toggleCropActive()
                                selectedMobileTool = "Edit"
                            }
                        )
                        selectedMobileTool == "Remove" -> RemoveToolPanel()
                        else -> when (activeTab) {
                        EditorToolTab.CROP -> {
                            CropToolsPanel(
                                crop = recipe.crop,
                                onRotate = { viewModel.rotateCrop(it) },
                                onFlip = { viewModel.flipCrop(it) },
                                onStraighten = { viewModel.setStraightenAngle(it) },
                                onAspectRatio = { viewModel.setCropAspectRatio(it) },
                                onResetCrop = { viewModel.resetCrop() },
                                onPerspectiveVertical = { vert ->
                                    viewModel.updateRecipe { it.copy(crop = it.crop.copy(verticalPerspective = vert)) }
                                },
                                onPerspectiveHorizontal = { horiz ->
                                    viewModel.updateRecipe { it.copy(crop = it.crop.copy(horizontalPerspective = horiz)) }
                                },
                                onTouchStart = { viewModel.onSliderTouchStart() },
                                onTouchEnd = { viewModel.onSliderTouchEnd(it) }
                            )
                        }

                        EditorToolTab.LIGHT -> {
                            // Tip 52: Exposure track — subtle luminance gradient + EV stop marks
                            val exposureTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF1A1A1A), Color(0xFFE0E0E0)))
                            }
                            // Tip 53: Contrast track — high-contrast dark-to-light bands
                            val contrastTrackBrush = remember {
                                Brush.horizontalGradient(listOf(
                                    Color(0xFF555555), Color(0xFF1A1A1A), Color(0xFF555555), Color(0xFFE0E0E0), Color(0xFF555555)
                                ))
                            }
                            // Tip 54: Highlights/Whites — matte grey to bright white
                            val highlightsTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF606060), Color(0xFFFFFFFF)))
                            }
                            // Tip 55: Shadows/Blacks — deep blue-black to light grey
                            val shadowsTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF0A1118), Color(0xFF909090)))
                            }

                            RampedSlider(
                                label = "Exposure",
                                value = recipe.basic.exposure,
                                minVal = -5.0f,
                                maxVal = 5.0f,
                                formatPattern = "%.2f",
                                unitSuffix = "EV",
                                trackBrush = exposureTrackBrush,
                                showEvStops = true,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(exposure = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Exposure") }
                            )
                            RampedSlider(
                                label = "Contrast",
                                value = recipe.basic.contrast,
                                minVal = -100f,
                                maxVal = 100f,
                                trackBrush = contrastTrackBrush,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(contrast = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Contrast") }
                            )
                            RampedSlider(
                                label = "Highlights",
                                value = recipe.basic.highlights,
                                minVal = -100f,
                                maxVal = 100f,
                                trackBrush = highlightsTrackBrush,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(highlights = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Highlights") }
                            )
                            RampedSlider(
                                label = "Shadows",
                                value = recipe.basic.shadows,
                                minVal = -100f,
                                maxVal = 100f,
                                trackBrush = shadowsTrackBrush,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(shadows = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Shadows") }
                            )
                            RampedSlider(
                                label = "Whites",
                                value = recipe.basic.whites,
                                minVal = -100f,
                                maxVal = 100f,
                                trackBrush = highlightsTrackBrush,
                                onValueChangeStart = {
                                    viewModel.onSliderTouchStart()
                                    viewModel.setAltClippingMode(1)
                                },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(whites = it)) } },
                                onValueChangeEnd = {
                                    viewModel.setAltClippingMode(0)
                                    viewModel.onSliderTouchEnd("Whites")
                                }
                            )
                            RampedSlider(
                                label = "Blacks",
                                value = recipe.basic.blacks,
                                minVal = -100f,
                                maxVal = 100f,
                                trackBrush = shadowsTrackBrush,
                                onValueChangeStart = {
                                    viewModel.onSliderTouchStart()
                                    viewModel.setAltClippingMode(2)
                                },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(blacks = it)) } },
                                onValueChangeEnd = {
                                    viewModel.setAltClippingMode(0)
                                    viewModel.onSliderTouchEnd("Blacks")
                                }
                            )
                        }

                        EditorToolTab.COLOR -> {
                            val tempTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF2B70E0), Color(0xFFE8E8E8), Color(0xFFFF9800)))
                            }
                            val tintTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF2EBD59), Color(0xFFE8E8E8), Color(0xFFE040FB)))
                            }
                            val vibranceSatTrackBrush = remember {
                                Brush.horizontalGradient(listOf(
                                    Color(0xFF757575), Color(0xFFE53935), Color(0xFFFF9800),
                                    Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0)
                                ))
                            }

                            val isWbModified = recipe.basic.temperature != 5500f || recipe.basic.tint != 0f || recipe.presence.vibrance != 0f || recipe.presence.saturation != 0f
                            val isMixerModified = recipe.hsl != com.cleanlr.model.HslAdjustments()
                            val isGradingModified = recipe.colorGrading != com.cleanlr.model.ColorGrading()

                            // Tip 239 & 240: Sub-Tab Navigation Bar inside COLOR Tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("WB", "White Balance", isWbModified),
                                    Triple("MIXER", "Color Mixer", isMixerModified),
                                    Triple("GRADING", "Color Grading", isGradingModified)
                                ).forEach { (id, label, modified) ->
                                    val isSelected = colorSubTab == id
                                    Surface(
                                        color = if (isSelected) SpectrumBlue else SpectrumElevated,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                colorSubTab = id
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else SpectrumTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (modified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isSelected) Color.White else SpectrumActiveDot)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                            when (colorSubTab) {
                                "WB" -> {
                                    // Tip 234: Eyedropper + Tip 471 Auto WB + Tip 470 B&W Mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    viewModel.updateRecipe("Auto Neutral WB") {
                                                        it.copy(basic = it.basic.copy(temperature = 5500f, tint = 0f)).syncLegacyFields()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Colorize, contentDescription = "White Balance Eyedropper", tint = SpectrumIconNormal, modifier = Modifier.size(18.dp))
                                            }
                                            Surface(
                                                color = SpectrumElevated,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    viewModel.updateRecipe("Auto WB") {
                                                        it.copy(basic = it.basic.copy(temperature = 5500f, tint = 2f)).syncLegacyFields()
                                                    }
                                                }
                                            ) {
                                                Text("Auto WB", color = SpectrumTextPrimary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }

                                        val isBw = recipe.presence.saturation <= -99f
                                        Surface(
                                            color = if (isBw) SpectrumBlue else SpectrumElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe(if (isBw) "Exit B&W" else "Enter B&W") {
                                                    val nextSat = if (isBw) 0f else -100f
                                                    it.copy(
                                                        basic = it.basic.copy(saturation = nextSat),
                                                        presence = it.presence.copy(saturation = nextSat)
                                                    ).syncLegacyFields()
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = if (isBw) "B&W: ON" else "B&W",
                                                color = if (isBw) Color.White else SpectrumTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isBw) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Tip 235: White Balance Preset Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            Triple("As Shot", 5500f, 0f),
                                            Triple("Daylight", 5500f, 10f),
                                            Triple("Cloudy", 6500f, 10f),
                                            Triple("Tungsten", 3200f, 0f),
                                            Triple("Fluorescent", 4000f, -10f),
                                            Triple("Shade", 7500f, 10f)
                                        ).forEach { (label, temp, tint) ->
                                            val isSelected = abs(recipe.basic.temperature - temp) < 50f && abs(recipe.basic.tint - tint) < 5f
                                            Surface(
                                                color = if (isSelected) SpectrumBlue else SpectrumElevated,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    viewModel.updateRecipe("WB: $label") {
                                                        it.copy(basic = it.basic.copy(temperature = temp, tint = tint)).syncLegacyFields()
                                                    }
                                                }
                                            ) {
                                                Text(label, color = if (isSelected) Color.White else SpectrumTextPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }

                                    // Tip 519: Kelvin Subtitle Description
                                    val kelvinDesc = when {
                                        recipe.basic.temperature < 3500f -> "(Tungsten / Warm indoor)"
                                        recipe.basic.temperature in 5000f..6000f -> "(Daylight / Neutral)"
                                        recipe.basic.temperature > 7000f -> "(Shade / Cool daylight)"
                                        else -> ""
                                    }
                                    if (kelvinDesc.isNotEmpty()) {
                                        Text(
                                            text = kelvinDesc,
                                            color = SpectrumTextSecondary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                        )
                                    }

                                    RampedSlider(
                                        label = "Temperature",
                                        value = recipe.basic.temperature,
                                        minVal = 2000f,
                                        maxVal = 10000f,
                                        defaultValue = 5500f,
                                        isBipolar = false,
                                        formatPattern = "%.0f",
                                        unitSuffix = "K",
                                        trackBrush = tempTrackBrush,
                                        useMiredScale = true,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setSliderDragging(true)
                                        },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(temperature = it)).syncLegacyFields() } },
                                        onValueChangeEnd = {
                                            viewModel.setSliderDragging(false)
                                            viewModel.onSliderTouchEnd("White Balance Temp")
                                        }
                                    )
                                    RampedSlider(
                                        label = "Tint",
                                        value = recipe.basic.tint,
                                        minVal = -150f,
                                        maxVal = 150f,
                                        defaultValue = 0f,
                                        isBipolar = true,
                                        trackBrush = tintTrackBrush,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setSliderDragging(true)
                                        },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(tint = it)).syncLegacyFields() } },
                                        onValueChangeEnd = {
                                            viewModel.setSliderDragging(false)
                                            viewModel.onSliderTouchEnd("Tint")
                                        }
                                    )

                                    // Vibrance & Saturation (Tip 233: Skin Safe badge on Vibrance)
                                    RampedSlider(
                                        label = "Vibrance (Skin Safe)",
                                        value = recipe.presence.vibrance,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        trackBrush = vibranceSatTrackBrush,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setSliderDragging(true)
                                        },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    basic = r.basic.copy(vibrance = it),
                                                    presence = r.presence.copy(vibrance = it)
                                                ).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = {
                                            viewModel.setSliderDragging(false)
                                            viewModel.onSliderTouchEnd("Vibrance")
                                        }
                                    )
                                    RampedSlider(
                                        label = "Saturation",
                                        value = recipe.presence.saturation,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        trackBrush = vibranceSatTrackBrush,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setSliderDragging(true)
                                        },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    basic = r.basic.copy(saturation = it),
                                                    presence = r.presence.copy(saturation = it)
                                                ).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = {
                                            viewModel.setSliderDragging(false)
                                            viewModel.onSliderTouchEnd("Saturation")
                                        }
                                    )
                                }
                                "MIXER" -> {
                                    HslMixerPanel(
                                        hsl = recipe.hsl,
                                        onHslChanged = { updated ->
                                            viewModel.updateRecipe("Color Mixer") { it.copy(hsl = updated) }
                                        },
                                        activeTatWeights = activeTatWeights,
                                        activeTatTargetName = activeTatTargetName,
                                        isMonochrome = recipe.presence.saturation <= -99f,
                                        onTouchStart = { viewModel.onSliderTouchStart() },
                                        onTouchEnd = { viewModel.onSliderTouchEnd(it) }
                                    )
                                }
                                "GRADING" -> {
                                    ColorGradingWheelWidget(
                                        colorGrading = recipe.colorGrading,
                                        onColorGradingChanged = { updated ->
                                            viewModel.updateRecipe("Color Grading") { it.copy(colorGrading = updated) }
                                        },
                                        onTouchStart = { viewModel.onSliderTouchStart() },
                                        onTouchEnd = { viewModel.onSliderTouchEnd(it) }
                                    )
                                }
                            }
                        }

                        EditorToolTab.EFFECTS -> {
                            val dehazeTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF0D47A1)))
                            }
                            val vignetteTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF000000), Color(0xFF404040), Color(0xFFFFFFFF)))
                            }

                            val isPresenceModified = recipe.effects.clarity != 0f || recipe.effects.texture != 0f || recipe.effects.dehaze != 0f
                            val isVignetteModified = recipe.effects.vignetteAmount != 0f
                            val isGrainModified = recipe.effects.grainAmount != 0f

                            // Tip 304: Sub-Tabs inside EFFECTS Tab (Presence | Vignette | Grain)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("Presence", "Presence", isPresenceModified),
                                    Triple("Vignette", "Vignette", isVignetteModified),
                                    Triple("Grain", "Grain", isGrainModified)
                                ).forEach { (id, label, modified) ->
                                    val isSelected = effectsSubTab == id
                                    Surface(
                                        color = if (isSelected) SpectrumBlue else SpectrumElevated,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                effectsSubTab = id
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else SpectrumTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (modified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isSelected) Color.White else SpectrumActiveDot)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                            when (effectsSubTab) {
                                "Presence" -> {
                                    // Tip 317: Quick Effect Preset Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = SpectrumElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe("Skin Soften") {
                                                    it.copy(effects = it.effects.copy(texture = -25f, clarity = -10f)).syncLegacyFields()
                                                }
                                            }
                                        ) {
                                            Text("Skin Soften", color = SpectrumTextPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                        Surface(
                                            color = SpectrumElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe("Crisp Architecture") {
                                                    it.copy(effects = it.effects.copy(texture = 30f, clarity = 25f)).syncLegacyFields()
                                                }
                                            }
                                        ) {
                                            Text("Crisp Architecture", color = SpectrumTextPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                        Surface(
                                            color = SpectrumElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe("35mm Grain") {
                                                    it.copy(effects = it.effects.copy(texture = 10f, grainAmount = 35f)).syncLegacyFields()
                                                }
                                            }
                                        ) {
                                            Text("35mm Grain", color = SpectrumTextPrimary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    RampedSlider(
                                        label = "Clarity (Midtones)",
                                        value = recipe.effects.clarity,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    effects = r.effects.copy(clarity = it),
                                                    presence = r.presence.copy(clarity = it)
                                                ).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Clarity") }
                                    )
                                    RampedSlider(
                                        label = "Texture (Fine Details)",
                                        value = recipe.effects.texture,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    effects = r.effects.copy(texture = it),
                                                    presence = r.presence.copy(texture = it)
                                                ).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Texture") }
                                    )
                                    RampedSlider(
                                        label = "Dehaze (Haze Removal)",
                                        value = recipe.effects.dehaze,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        trackBrush = dehazeTrackBrush,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    effects = r.effects.copy(dehaze = it),
                                                    presence = r.presence.copy(dehaze = it)
                                                ).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Dehaze") }
                                    )
                                }
                                "Vignette" -> {
                                    // Tips 291 & 296: Vignette Amount & Conditional Sub-sliders
                                    RampedSlider(
                                        label = "Vignette Amount",
                                        value = recipe.effects.vignetteAmount,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
                                        trackBrush = vignetteTrackBrush,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(effects = r.effects.copy(vignetteAmount = it)).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette") }
                                    )

                                    AnimatedVisibility(visible = recipe.effects.vignetteAmount != 0f) {
                                        Column {
                                            RampedSlider(
                                                label = "Midpoint",
                                                value = recipe.effects.vignetteMidpoint,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 50f,
                                                isBipolar = false,
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = {
                                                    viewModel.updateRecipe { r ->
                                                        r.copy(effects = r.effects.copy(vignetteMidpoint = it)).syncLegacyFields()
                                                    }
                                                },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Midpoint") }
                                            )
                                            RampedSlider(
                                                label = "Feather",
                                                value = recipe.effects.vignetteFeather,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 50f,
                                                isBipolar = false,
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = {
                                                    viewModel.updateRecipe { r ->
                                                        r.copy(effects = r.effects.copy(vignetteFeather = it)).syncLegacyFields()
                                                    }
                                                },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Feather") }
                                            )
                                        }
                                    }
                                }
                                "Grain" -> {
                                    // Tips 292 & 297: Film Grain Amount & Conditional Sub-sliders
                                    RampedSlider(
                                        label = "Grain Amount",
                                        value = recipe.effects.grainAmount,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(effects = r.effects.copy(grainAmount = it)).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain") }
                                    )

                                    AnimatedVisibility(visible = recipe.effects.grainAmount > 0f) {
                                        Column {
                                            RampedSlider(
                                                label = "Grain Size",
                                                value = recipe.effects.grainSize,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 25f,
                                                isBipolar = false,
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = {
                                                    viewModel.updateRecipe { r ->
                                                        r.copy(effects = r.effects.copy(grainSize = it)).syncLegacyFields()
                                                    }
                                                },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain Size") }
                                            )
                                            RampedSlider(
                                                label = "Grain Roughness",
                                                value = recipe.effects.grainRoughness,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 50f,
                                                isBipolar = false,
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = {
                                                    viewModel.updateRecipe { r ->
                                                        r.copy(effects = r.effects.copy(grainRoughness = it)).syncLegacyFields()
                                                    }
                                                },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain Roughness") }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolTab.DETAIL -> {
                            val isSharpeningModified = recipe.detail.sharpening != 40f && recipe.detail.sharpening != 0f
                            val isNoiseModified = recipe.detail.luminanceNoise != 0f || recipe.detail.colorNoise != 25f

                            // Tip 305: Sub-Tabs inside DETAIL Tab (Sharpening | Noise Reduction)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("Sharpening", "Sharpening", isSharpeningModified),
                                    Triple("Noise", "Noise Reduction", isNoiseModified)
                                ).forEach { (id, label, modified) ->
                                    val isSelected = detailSubTab == id
                                    Surface(
                                        color = if (isSelected) SpectrumBlue else SpectrumElevated,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                detailSubTab = id
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else SpectrumTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            if (modified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isSelected) Color.White else SpectrumActiveDot)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                            when (detailSubTab) {
                                "Sharpening" -> {
                                    RampedSlider(
                                        label = "Sharpening",
                                        value = recipe.detail.sharpening,
                                        minVal = 0f,
                                        maxVal = 150f,
                                        defaultValue = 40f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpening = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpening") }
                                    )

                                    AnimatedVisibility(visible = recipe.detail.sharpening > 0f) {
                                        Column {
                                            // Tip 331: Sharpen Radius formatted as "%.1f px"
                                            RampedSlider(
                                                label = "Radius",
                                                value = recipe.detail.sharpenRadius,
                                                minVal = 0.5f,
                                                maxVal = 3.0f,
                                                defaultValue = 1.0f,
                                                isBipolar = false,
                                                formatPattern = "%.1f",
                                                unitSuffix = "px",
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenRadius = it)).syncLegacyFields() } },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpen Radius") }
                                            )
                                            RampedSlider(
                                                label = "Detail",
                                                value = recipe.detail.sharpenDetail,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 25f,
                                                isBipolar = false,
                                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenDetail = it)).syncLegacyFields() } },
                                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpen Detail") }
                                            )
                                            // Tip 302 & 320: Masking with Alt secret edge preview hint
                                            RampedSlider(
                                                label = "Masking (Edges Only)",
                                                value = recipe.detail.sharpenMasking,
                                                minVal = 0f,
                                                maxVal = 100f,
                                                defaultValue = 0f,
                                                isBipolar = false,
                                                onValueChangeStart = {
                                                    viewModel.onSliderTouchStart()
                                                    viewModel.setAltClippingMode(3) // Section 4.1 Alt + Sharpen Masking
                                                },
                                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenMasking = it)).syncLegacyFields() } },
                                                onValueChangeEnd = {
                                                    viewModel.setAltClippingMode(0)
                                                    viewModel.onSliderTouchEnd("Detail Masking")
                                                }
                                            )
                                        }
                                    }
                                }
                                "Noise" -> {
                                    // Tips 294, 295, 332: Bilateral Noise Reduction
                                    RampedSlider(
                                        label = "Luminance Noise",
                                        value = recipe.detail.luminanceNoise,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(luminanceNoise = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Luminance Noise") }
                                    )
                                    RampedSlider(
                                        label = "Color Noise",
                                        value = recipe.detail.colorNoise,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 25f, // Tip 332
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(colorNoise = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Color Noise") }
                                    )

                                    // Tip 318: Quick Reset Detail Button
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        TextButton(onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.updateRecipe("Reset Detail") {
                                                it.copy(detail = com.cleanlr.model.DetailAdjustments()).syncLegacyFields()
                                            }
                                        }) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = SpectrumIconNormal, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reset Detail to Default", color = SpectrumTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        EditorToolTab.CURVES -> {
                            ToneCurveWidget(
                                curves = recipe.curves,
                                onCurveChanged = { updated ->
                                    viewModel.updateRecipe("Tone Curve") { it.copy(curves = updated) }
                                }
                            )
                        }

                        EditorToolTab.PRESETS -> {
                            PresetsPanel(
                                groups = viewModel.presetGroups,
                                selectedGroup = selectedPresetGroup,
                                onSelectGroup = { viewModel.selectPresetGroup(it) },
                                presets = allPresets,
                                activePreset = activePreset,
                                presetAmount = presetAmount,
                                onSelectPreset = { viewModel.applyPreset(it) },
                                onAmountChange = { viewModel.adjustPresetAmount(it) },
                                onAmountChangeEnd = { viewModel.commitPresetAmount() },
                                onResetToOriginal = { viewModel.resetPreset() },
                                onSaveCurrentAsPreset = {
                                    newPresetName = ""
                                    showSavePresetDialog = true
                                },
                                onImportXmpPreset = {
                                    xmpPickerLauncher.launch("*/*")
                                }
                            )
                        }

                        EditorToolTab.VERSIONS -> {
                            VersionsPanel(
                                versions = versions,
                                onCreateVersion = { viewModel.createVersion(it) },
                                onApplyVersion = { viewModel.applyVersion(it) },
                                onDeleteVersion = { viewModel.deleteVersion(it) }
                            )
                        }

                        EditorToolTab.MASKS -> {
                            MaskingPanel(
                                layers = recipe.masks,
                                activeLayerIndex = activeMaskLayerIndex,
                                onSelectLayer = { viewModel.selectMaskLayer(it) },
                                onAddLayer = { subMask -> viewModel.addMaskLayer(subMask) },
                                onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) },
                                onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                                onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                                isOverlayVisible = isMaskOverlayVisible,
                                onToggleOverlay = { viewModel.toggleMaskOverlay() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Adjustments",
                    color = SpectrumTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose reset level for this image:",
                        color = SpectrumTextSecondary,
                        fontSize = 13.sp
                    )
                    Surface(
                        color = SpectrumElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.resetSlidersOnly()
                                showResetDialog = false
                            }
                    ) {
                        Text(
                            text = "Reset Sliders Only (Keep Masks & Crop)",
                            color = SpectrumTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Surface(
                        color = Color(0x22E02B2B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.resetAll()
                                showResetDialog = false
                            }
                    ) {
                        Text(
                            text = "Reset All (Everything)",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = SpectrumTextSecondary)
                }
            },
            containerColor = SpectrumSurface
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = {
                Text(
                    text = "Save as Custom Preset",
                    color = SpectrumTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a name for your custom preset:",
                        color = SpectrumTextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("e.g. Cinematic Warm", color = SpectrumTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SpectrumTextPrimary,
                            unfocusedTextColor = SpectrumTextPrimary,
                            focusedBorderColor = SpectrumBlue,
                            unfocusedBorderColor = SpectrumBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.saveCustomPreset(newPresetName.trim())
                            showSavePresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrumBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = SpectrumTextSecondary)
                }
            },
            containerColor = SpectrumSurface
        )
    }

        if (showExportConfigSheet) {
            com.cleanlr.ui.export.ExportConfigBottomSheet(
                onDismiss = { showExportConfigSheet = false },
                onExportConfirmed = { config ->
                    viewModel.exportImage(config)
                },
                onBatchExportRequested = { config ->
                    pendingBatchConfig = config
                    batchPickerLauncher.launch("image/*")
                }
            )
        }

        if (isBatchExportDialogVisible && activeBatchProgress != null) {
            com.cleanlr.ui.export.BatchExportProgressDialog(
                progress = activeBatchProgress!!,
                onCancel = { viewModel.cancelActiveBatch() },
                onDismiss = { viewModel.dismissBatchExportDialog() }
            )
        }
    }
}
}

@Composable
private fun MobilePrimaryToolbar(
    selectedTool: String,
    onToolSelected: (String) -> Unit
) {
    val tools = listOf(
        "Actions" to Icons.Default.AutoFixHigh,
        "Presets" to Icons.Default.Style,
        "Crop" to Icons.Default.Crop,
        "Edit" to Icons.Default.Tune,
        "Masking" to Icons.Default.Layers,
        "Remove" to Icons.Default.RemoveCircleOutline
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpectrumSurface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = SpectrumBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { (label, icon) ->
                val active = selectedTool == label
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) SpectrumBlue else Color.Transparent)
                        .clickable { onToolSelected(label) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) Color.White else SpectrumIconNormal,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        color = if (active) Color.White else SpectrumTextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
@Composable
private fun CropAndExpandPanel(
    aspectRatioGuide: String?,
    onAspectRatioSelected: (String?) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    var page by remember { mutableStateOf("Aspect") }
    var geometryMode by remember { mutableStateOf("Auto") }
    val ratios = listOf("Original" to null, "1:1" to "1:1", "4:5" to "4:5", "9:16" to "9:16")
    val geometryModes = listOf("Auto", "Level", "Vertical", "Full", "Guided")

    Column(modifier = Modifier.fillMaxWidth().background(SpectrumSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("Aspect", "Geometry").forEach { item ->
                Column(
                    modifier = Modifier
                        .clickable { page = item }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(item, color = if (page == item) Color.White else SpectrumTextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(72.dp).height(2.dp).background(if (page == item) Color.White else Color.Transparent))
                }
            }
        }

        if (page == "Aspect") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ratios.forEach { (label, ratio) ->
                    val selected = (ratio == null && aspectRatioGuide == null) || ratio == aspectRatioGuide
                    Surface(
                        color = if (selected) SpectrumBlue else SpectrumElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onAspectRatioSelected(ratio) }
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                geometryModes.forEach { mode ->
                    val selected = geometryMode == mode
                    Surface(
                        color = if (selected) SpectrumBlue else SpectrumElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { geometryMode = mode }
                    ) {
                        Text(mode, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                    }
                }
            }
        }

        HorizontalDivider(color = SpectrumBorder)
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel crop", tint = SpectrumTextPrimary) }
            Text("Crop and expand", color = SpectrumTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onApply) { Icon(Icons.Default.Check, contentDescription = "Apply crop", tint = SpectrumTextPrimary) }
        }
    }
}

@Composable
private fun RemoveToolPanel() {
    var brushSize by remember { mutableFloatStateOf(10f) }
    var detectObjects by remember { mutableStateOf(false) }
    var generativeAI by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(SpectrumSurface).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Remove", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 1f..100f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Detect objects", color = SpectrumTextPrimary, modifier = Modifier.weight(1f))
            Switch(checked = detectObjects, onCheckedChange = { detectObjects = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Generative AI", color = SpectrumTextPrimary, modifier = Modifier.weight(1f))
            Switch(checked = generativeAI, onCheckedChange = { generativeAI = it })
        }
    }
}

/**
 * Section 11: Crop, Straighten, Rotate, Flip & Geometry (Tips 401–420)
 */
@Composable
private fun CropToolsPanel(
    crop: com.cleanlr.model.CropAdjustments,
    onRotate: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
    onStraighten: (Float) -> Unit,
    onAspectRatio: (String) -> Unit,
    onResetCrop: () -> Unit,
    onPerspectiveVertical: (Float) -> Unit,
    onPerspectiveHorizontal: (Float) -> Unit,
    onTouchStart: () -> Unit,
    onTouchEnd: (String) -> Unit
) {
    val view = LocalView.current
    var cropSubTab by remember { mutableStateOf("Transform") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // Sub-Tab Switcher: Transform | Perspective (Tip 401, 413)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Transform", "Perspective").forEach { tab ->
                val isSelected = cropSubTab == tab
                Surface(
                    color = if (isSelected) SpectrumBlue else SpectrumElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            cropSubTab = tab
                        }
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else SpectrumTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        when (cropSubTab) {
            "Transform" -> {
                // Tip 402: 4 Action Buttons: Rotate CCW, Rotate CW, Flip Horizontal, Flip Vertical
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onRotate(false)
                        }
                    ) {
                        Icon(Icons.Default.RotateLeft, contentDescription = "Rotate 90° CCW", tint = SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onRotate(true)
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90° CW", tint = SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onFlip(true)
                        }
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = if (crop.flipHorizontal) SpectrumBlue else SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onFlip(false)
                        }
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Flip Vertical", tint = if (crop.flipVertical) SpectrumBlue else SpectrumIconNormal)
                    }
                }

                // Tip 403, 404, 416: Straighten Dial (-45°..+45°) with Double-Tap to Reset
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Straighten Angle",
                            color = SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            color = SpectrumElevated,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onStraighten(0f)
                            }
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f°", crop.straightenAngle),
                                color = if (crop.straightenAngle != 0f) SpectrumBlue else SpectrumTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    RampedSlider(
                        label = "Angle",
                        value = crop.straightenAngle,
                        minVal = -45f,
                        maxVal = 45f,
                        defaultValue = 0f,
                        isBipolar = true,
                        formatPattern = "%.1f",
                        unitSuffix = "°",
                        onValueChangeStart = { onTouchStart() },
                        onValueChange = { onStraighten(it) },
                        onValueChangeEnd = { onTouchEnd("Straighten Angle") }
                    )
                }

                // Tip 406 & 407: Aspect Ratio Preset Pills
                Text(
                    text = "ASPECT RATIO",
                    color = SpectrumTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )

                val aspectRatios = listOf(
                    "Free", "Original", "1:1", "4:5", "16:9", "9:16", "3:2", "4:3", "2:3"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aspectRatios.forEach { ratio ->
                        val isSelected = (crop.aspectRatio == ratio) || (crop.aspectRatio.isEmpty() && ratio == "Free")
                        Surface(
                            color = if (isSelected) SpectrumBlue else SpectrumElevated,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val newRatio = if (isSelected) {
                                    // Tip 407: Swap orientation on tap
                                    when (ratio) {
                                        "4:5" -> "5:4"
                                        "5:4" -> "4:5"
                                        "16:9" -> "9:16"
                                        "9:16" -> "16:9"
                                        "3:2" -> "2:3"
                                        "2:3" -> "3:2"
                                        "4:3" -> "3:4"
                                        "3:4" -> "4:3"
                                        else -> ratio
                                    }
                                } else ratio
                                onAspectRatio(newRatio)
                            }
                        ) {
                            Text(
                                text = ratio,
                                color = if (isSelected) Color.White else SpectrumTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            "Perspective" -> {
                // Tip 413: Vertical & Horizontal Perspective Keystone Sliders
                RampedSlider(
                    label = "Vertical Perspective",
                    value = crop.verticalPerspective,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = true,
                    onValueChangeStart = { onTouchStart() },
                    onValueChange = { onPerspectiveVertical(it) },
                    onValueChangeEnd = { onTouchEnd("Vertical Perspective") }
                )
                RampedSlider(
                    label = "Horizontal Perspective",
                    value = crop.horizontalPerspective,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = true,
                    onValueChangeStart = { onTouchStart() },
                    onValueChange = { onPerspectiveHorizontal(it) },
                    onValueChangeEnd = { onTouchEnd("Horizontal Perspective") }
                )
            }
        }

        // Tip 419: Reset Crop Button
        val isCropModified = crop.rotationDegrees != 0f || crop.straightenAngle != 0f ||
                crop.flipHorizontal || crop.flipVertical || crop.aspectRatio.isNotEmpty() ||
                crop.verticalPerspective != 0f || crop.horizontalPerspective != 0f

        if (isCropModified) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onResetCrop()
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = SpectrumIconNormal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Crop & Geometry", color = SpectrumTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
