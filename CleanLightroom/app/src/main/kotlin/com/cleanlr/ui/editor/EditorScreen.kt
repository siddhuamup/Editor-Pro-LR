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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
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
    var showEditorOverflowMenu by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showExportConfigSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedMobileTool by remember { mutableStateOf<String?>(null) }

    var isHdrModeActive by remember { mutableStateOf(false) }
    var hdrLimitEv by remember { mutableFloatStateOf(1.0f) }
    var lensBlurAmount by remember { mutableFloatStateOf(0f) }
    var selectedBokehShape by remember { mutableStateOf("Circle") }
    var removeChromaticAberration by remember { mutableStateOf(false) }
    var enableLensCorrections by remember { mutableStateOf(false) }
    var selectedProfileGroup by remember { mutableStateOf("Adobe Raw") }
    var selectedProfile by remember { mutableStateOf("Adobe Color") }

    val allPresets by viewModel.allPresets.collectAsState()
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var colorSubTab by remember { mutableStateOf("WB") }
    var effectsSubTab by remember { mutableStateOf("Effects") }
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
                TopAppBar(
                    modifier = Modifier.height(50.dp),
                    navigationIcon = {
                        IconButton(onClick = { onBack?.invoke() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to library",
                                tint = SpectrumTextPrimary
                            )
                        }
                    },
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SpectrumSurface
                    ),
                    actions = {
                        // Mask overlay toggle in Masking mode (Screenshot 34)
                        if (selectedMobileTool == "Masking") {
                            IconButton(onClick = { viewModel.toggleMaskOverlay() }) {
                                Icon(
                                    imageVector = if (isMaskOverlayVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle mask overlay",
                                    tint = if (isMaskOverlayVisible) SpectrumBlue else SpectrumIconDisabled
                                )
                            }
                        }

                        // HDR Mode Pill Badge (Screenshot 14)
                        if (isHdrModeActive || recipe.isHdrEnabled) {
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
                        }

                        // Undo with dropdown arrow & long-press history (Screenshots 14, 34)
                        if (canUndo) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .pointerInput(canUndo) {
                                            detectTapGestures(
                                                onTap = { viewModel.undo() },
                                                onLongPress = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    showHistoryMenu = true
                                                }
                                            )
                                        }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo (Tap) / History (Long-Press)",
                                        tint = SpectrumTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = SpectrumTextPrimary,
                                        modifier = Modifier.size(16.dp)
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
                        }

                        // Share / Export Icon (Screenshots 4, 6)
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share and Export",
                                tint = SpectrumTextPrimary
                            )
                        }

                        // 3-dots Overflow Menu (Screenshots 4, 5)
                        Box {
                            IconButton(onClick = { showEditorOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = SpectrumTextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showEditorOverflowMenu,
                                onDismissRequest = { showEditorOverflowMenu = false },
                                modifier = Modifier
                                    .background(SpectrumElevated)
                                    .border(1.dp, SpectrumInnerBorder, RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.SaveAlt, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Save copy to device", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        viewModel.exportImage(com.cleanlr.export.ExportConfig())
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Tune, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("View options", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        showViewOptionsMenu = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AddBox, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Create Preset", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        showSavePresetDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Copy Settings", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        viewModel.copyEditSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Paste Settings", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        viewModel.pasteEditSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.History, contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Apply from previous", color = SpectrumTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        showEditorOverflowMenu = false
                                        viewModel.pasteEditSettings()
                                    }
                                )
                                HorizontalDivider(color = SpectrumInnerBorder)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Help, contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Help", color = SpectrumTextSecondary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = { showEditorOverflowMenu = false }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Your profile", color = SpectrumTextSecondary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = { showEditorOverflowMenu = false }
                                )
                            }
                        }
                    }
                )
            }
        },
    bottomBar = {
        if (!isCleanScreenMode) {
            when (selectedMobileTool) {
                "Crop" -> DedicatedCommitBottomBar(
                    title = "Crop and expand",
                    onCancel = {
                        viewModel.setAspectRatioGuide(null)
                        if (viewModel.isCropActive.value) viewModel.toggleCropActive()
                        selectedMobileTool = null
                    },
                    onConfirm = {
                        if (viewModel.isCropActive.value) viewModel.toggleCropActive()
                        selectedMobileTool = null
                    }
                )
                "Presets" -> DedicatedCommitBottomBar(
                    title = "Presets",
                    onCancel = {
                        viewModel.resetPreset()
                        selectedMobileTool = null
                    },
                    onConfirm = {
                        selectedMobileTool = null
                    }
                )
                "Masking" -> DedicatedCommitBottomBar(
                    title = "Masking",
                    onCancel = {
                        selectedMobileTool = null
                    },
                    onConfirm = {
                        selectedMobileTool = null
                    }
                )
                "Remove" -> DedicatedCommitBottomBar(
                    title = "Remove",
                    onCancel = {
                        selectedMobileTool = null
                    },
                    onConfirm = {
                        selectedMobileTool = null
                    }
                )
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
                                    "Actions" -> {
                                        // Actions stays selected and opens Actions panel and strip
                                    }
                                    "Presets" -> viewModel.setActiveTab(EditorToolTab.PRESETS)
                                    "Edit" -> viewModel.setActiveTab(EditorToolTab.LIGHT)
                                    "Masking" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                                    "Remove" -> viewModel.setActiveTab(EditorToolTab.MASKS)
                                }
                            }
                        }
                    }
                )
            }
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
                // Interactive Live Histogram with 5 drag zones (Tips 126–160) — visible in Info Overlay mode
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
                }

                // Tip 5: 8.dp Subtle Top Shadow between Canvas & Controls Panel
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

                // Tip 6: 3-Snap Panel Height Animation
                val animatedPanelHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (selectedMobileTool != null) {
                        when (panelSnapState) {
                            PanelSnapState.COLLAPSED -> 0.dp
                            PanelSnapState.COMPACT -> if (selectedMobileTool in listOf("Crop", "Presets")) 260.dp else 210.dp
                            PanelSnapState.EXPANDED -> 380.dp
                        }
                    } else 0.dp,
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

                if (animatedPanelHeight > 0.dp && selectedMobileTool != null) {
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
                        selectedMobileTool == "Actions" -> ActionsPanel(
                            onAutoTone = { viewModel.autoTone() },
                            onQuickMask = {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            }
                        )
                        isCropActive || selectedMobileTool == "Crop" -> CropAndExpandPanel(
                            crop = recipe.crop,
                            onRotate = { viewModel.rotateCrop(it) },
                            onFlip = { viewModel.flipCrop(it) },
                            onStraighten = { viewModel.setStraightenAngle(it) },
                            aspectRatioGuide = aspectRatioGuide,
                            onAspectRatioSelected = { viewModel.setAspectRatioGuide(it) }
                        )
                        selectedMobileTool == "Remove" -> RemoveToolPanel(
                            brushSize = brushSize,
                            onBrushSizeChange = { viewModel.setBrushSize(it) }
                        )
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
                            // Curve Navigation Button (Screenshot 13)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = SpectrumElevated,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, SpectrumInnerBorder),
                                    modifier = Modifier.clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.setActiveTab(EditorToolTab.CURVES)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timeline,
                                            contentDescription = "Tone Curve",
                                            tint = SpectrumBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Curve",
                                            color = SpectrumTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

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

                            // Edit in HDR Mode Card (Screenshot 14)
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isHdrModeActive) SpectrumBlue else SpectrumInnerBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (isHdrModeActive) SpectrumBlue else Color(0xFF333333),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "HDR",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Edit in HDR Mode",
                                                    color = SpectrumTextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Visualize and edit high dynamic range photos",
                                                    color = SpectrumTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = isHdrModeActive,
                                            onCheckedChange = {
                                                isHdrModeActive = it
                                                viewModel.updateRecipe("Toggle HDR Mode") { r -> r.copy(isHdrEnabled = it) }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = SpectrumBlue
                                            )
                                        )
                                    }

                                    if (isHdrModeActive) {
                                        Spacer(Modifier.height(10.dp))
                                        RampedSlider(
                                            label = "HDR Limit",
                                            value = hdrLimitEv,
                                            minVal = 1.0f,
                                            maxVal = 4.0f,
                                            defaultValue = 1.0f,
                                            isBipolar = false,
                                            formatPattern = "+%.1f",
                                            unitSuffix = "EV",
                                            onValueChangeStart = { viewModel.onSliderTouchStart() },
                                            onValueChange = { hdrLimitEv = it },
                                            onValueChangeEnd = { viewModel.onSliderTouchEnd("HDR Limit") }
                                        )
                                    }
                                }
                            }
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tone Curve",
                                    color = SpectrumTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.setActiveTab(EditorToolTab.LIGHT)
                                    }
                                ) {
                                    Text(
                                        text = "DONE",
                                        color = SpectrumBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            ToneCurveWidget(
                                curves = recipe.curves,
                                onCurveChanged = { updated ->
                                    viewModel.updateRecipe("Tone Curve") { it.copy(curves = updated) }
                                }
                            )
                        }

                        EditorToolTab.BLUR -> {
                            LensBlurPanel(
                                blurAmount = recipe.blur.amount,
                                onBlurAmountChange = { amt ->
                                    viewModel.updateRecipe { it.copy(blur = it.blur.copy(amount = amt)) }
                                },
                                selectedBokehShape = recipe.blur.bokehShape,
                                onBokehShapeSelected = { shape ->
                                    viewModel.updateRecipe { it.copy(blur = it.blur.copy(bokehShape = shape)) }
                                }
                            )
                        }

                        EditorToolTab.OPTICS -> {
                            OpticsPanel(
                                removeChromaticAberration = recipe.optics.removeChromaticAberration,
                                onRemoveChromaticAberrationChange = { rca ->
                                    viewModel.updateRecipe { it.copy(optics = it.optics.copy(removeChromaticAberration = rca)) }
                                },
                                enableLensCorrections = recipe.optics.enableLensCorrections,
                                onEnableLensCorrectionsChange = { elc ->
                                    viewModel.updateRecipe { it.copy(optics = it.optics.copy(enableLensCorrections = elc)) }
                                }
                            )
                        }

                        EditorToolTab.PROFILES -> {
                            ProfilesPanel(
                                selectedGroup = selectedProfileGroup,
                                onGroupSelected = { selectedProfileGroup = it },
                                selectedProfile = recipe.profile,
                                onProfileSelected = { prof ->
                                    viewModel.updateRecipe { it.copy(profile = prof) }
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

            // Category Tool Tabs Bar — Adobe Lightroom Mobile Signature Style (Screenshots 7, 13, 20)
            // Rendered directly beneath the active controls panel, right above the primary bottom navigation
            if (selectedMobileTool in listOf("Edit", "Actions")) {
                HorizontalDivider(color = SpectrumBorder, thickness = 1.dp)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpectrumSurface)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    if (selectedMobileTool == "Actions") {
                        val actions = listOf(
                            Triple("Auto", Icons.Default.AutoFixHigh) { viewModel.autoTone() },
                            Triple("Enhance", Icons.Default.AutoAwesome) { viewModel.autoTone() },
                            Triple("Subject", Icons.Default.Person) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Background", Icons.Default.Wallpaper) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Sky", Icons.Default.Cloud) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Animate", Icons.Default.Animation) { }
                        )
                        items(actions) { (label, icon, action) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        action()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = SpectrumIconNormal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = SpectrumTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    } else {
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

                        val visibleTabs = listOf(
                            EditorToolTab.LIGHT,
                            EditorToolTab.COLOR,
                            EditorToolTab.BLUR,
                            EditorToolTab.EFFECTS,
                            EditorToolTab.DETAIL,
                            EditorToolTab.OPTICS,
                            EditorToolTab.PROFILES
                        )

                        items(visibleTabs) { tab ->
                            val isSelected = tab == activeTab
                            val isModified = viewModel.isTabModified(tab)
                            val isBypassed = bypassedTabs.contains(tab)
                            val tabIcon = when (tab) {
                                EditorToolTab.CROP -> Icons.Default.Crop
                                EditorToolTab.LIGHT -> Icons.Default.Brightness6
                                EditorToolTab.COLOR -> Icons.Default.Palette
                                EditorToolTab.BLUR -> Icons.Default.BlurOn
                                EditorToolTab.EFFECTS -> Icons.Default.AutoAwesome
                                EditorToolTab.DETAIL -> Icons.Default.Tune
                                EditorToolTab.OPTICS -> Icons.Default.CameraAlt
                                EditorToolTab.PROFILES -> Icons.Default.ColorLens
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

        if (showShareSheet) {
            ModalBottomSheet(
                onDismissRequest = { showShareSheet = false },
                containerColor = Color(0xFF1E1E1E),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF555555))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    // Header Row: Thumbnail + Info + Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF333333)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Original Photo",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Full resolution • JPEG / RAW",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { showShareSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // Quick Actions: Circular Buttons [Share | Export as...]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Share circular button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    showShareSheet = false
                                    viewModel.shareLastExport(context)
                                }
                                .padding(horizontal = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF2C2C2C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        // Export as... circular button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    showShareSheet = false
                                    showExportConfigSheet = true
                                }
                                .padding(horizontal = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF2C2C2C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Export as...", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Export as...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                    // Options List
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShareSheet = false
                                viewModel.exportImage(com.cleanlr.export.ExportConfig())
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Save copy to device", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                showShareSheet = false
                                showExportConfigSheet = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Export settings", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShareSheet = false }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Get link", color = Color.White, fontSize = 15.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShareSheet = false }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Create edit replay", color = Color.White, fontSize = 15.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShareSheet = false }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Post to Community", color = Color.White, fontSize = 15.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShareSheet = false }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Invite to edit", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }

        if (showViewOptionsMenu) {
            AlertDialog(
                onDismissRequest = { showViewOptionsMenu = false },
                title = {
                    Text("View options", color = SpectrumTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    TextButton(onClick = { showViewOptionsMenu = false }) {
                        Text("Done", color = SpectrumBlue)
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
private fun DedicatedCommitBottomBar(
    title: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Color(0xFF2A2A2A))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onConfirm) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Apply",
                    tint = Color(0xFF1686FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionsPanel(
    onAutoTone: () -> Unit,
    onQuickMask: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Tone & Color",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "AI exposure & dynamic range adjustment",
                            color = Color(0xFF9E9E9E),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onAutoTone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1686FF)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Apply", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "QUICK ACTIONS",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Enhance Details" to Icons.Default.AutoAwesome,
                "Select Subject" to Icons.Default.Person,
                "Select Sky" to Icons.Default.Cloud,
                "Select Background" to Icons.Default.Wallpaper
            ).forEach { (label, icon) ->
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF383838)),
                    modifier = Modifier.clickable { onQuickMask(label) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MobilePrimaryToolbar(
    selectedTool: String?,
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
            .background(Color(0xFF1A1A1A))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Color(0xFF2A2A2A))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { (label, icon) ->
                val active = selectedTool == label
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToolSelected(label) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) Color(0xFF1686FF) else Color(0xFFB0B0B0),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        color = if (active) Color(0xFF1686FF) else Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Curved Straighten Dial Arc matching Reference Screenshot 11
 */
@Composable
private fun CurvedStraightenDial(
    angle: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = dragAmount.x / 14f
                    val newAngle = (angle + delta).coerceIn(-45f, 45f)
                    onAngleChange(newAngle)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val arcY = height * 0.85f

            // Draw curved subtle arc base line
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(width * 0.08f, arcY)
                quadraticBezierTo(centerX, arcY - 14f, width * 0.92f, arcY)
            }
            drawPath(path, color = Color(0xFF444444), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))

            // Draw ticks along the dial
            val tickCount = 41
            for (i in 0 until tickCount) {
                val tickFraction = i / (tickCount - 1).toFloat()
                val tickX = width * 0.08f + tickFraction * (width * 0.84f)
                val curveOffset = 14f * (1f - ((tickX - centerX) / (width * 0.42f)).let { it * it }.coerceIn(0f, 1f))
                val currentY = arcY - curveOffset
                val isMajor = i % 5 == 0
                val tickHeight = if (isMajor) 12f else 6f
                val isCenter = i == tickCount / 2
                val tickColor = if (isCenter) Color(0xFF1686FF) else if (isMajor) Color(0xFFAAAAAA) else Color(0xFF555555)

                drawLine(
                    color = tickColor,
                    start = androidx.compose.ui.geometry.Offset(tickX, currentY - tickHeight),
                    end = androidx.compose.ui.geometry.Offset(tickX, currentY),
                    strokeWidth = if (isCenter) 2.5f else 1.2f
                )
            }
        }

        // Center degree readout pill (e.g. "0.00°")
        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF444444)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .clickable { onAngleChange(0f) }
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.2f°", angle),
                color = if (angle != 0f) Color(0xFF1686FF) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CropAndExpandPanel(
    crop: com.cleanlr.model.CropAdjustments,
    onRotate: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
    onStraighten: (Float) -> Unit,
    aspectRatioGuide: String?,
    onAspectRatioSelected: (String?) -> Unit
) {
    var page by remember { mutableStateOf("Aspect") }
    var geometryMode by remember { mutableStateOf("Auto") }
    var isAspectLocked by remember { mutableStateOf(false) }
    var showOverflowFlip by remember { mutableStateOf(false) }

    val ratios = listOf(
        "Free" to null,
        "Original" to "Original",
        "1:1" to "1:1",
        "4:5" to "4:5",
        "16:9" to "16:9",
        "9:16" to "9:16",
        "3:2" to "3:2",
        "4:3" to "4:3"
    )
    val geometryModes = listOf("Auto", "Level", "Vertical", "Full", "Guided")

    Column(modifier = Modifier.fillMaxWidth().background(SpectrumSurface)) {
        // Curved Straighten Dial Arc (Screenshot 11)
        CurvedStraightenDial(
            angle = crop.straightenAngle,
            onAngleChange = onStraighten
        )

        // Control Row: Fullscreen, Aspect lock, Rotate 90, Overflow (Flip)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen preview", tint = Color.White)
            }
            IconButton(onClick = { isAspectLocked = !isAspectLocked }) {
                Icon(
                    if (isAspectLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Aspect lock",
                    tint = if (isAspectLocked) Color(0xFF1686FF) else Color.White
                )
            }
            IconButton(onClick = { onRotate(true) }) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90 degrees", tint = Color.White)
            }
            Box {
                IconButton(onClick = { showOverflowFlip = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More crop tools", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showOverflowFlip,
                    onDismissRequest = { showOverflowFlip = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Flip Horizontal") },
                        onClick = { onFlip(true); showOverflowFlip = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Flip Vertical") },
                        onClick = { onFlip(false); showOverflowFlip = false }
                    )
                }
            }
        }

        // Tabs: [Aspect, Geometry]
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
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
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(60.dp).height(2.dp).background(if (page == item) Color.White else Color.Transparent))
                }
            }
        }

        if (page == "Aspect") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ratios.forEach { (label, ratio) ->
                    val selected = (ratio == null && aspectRatioGuide == null) || ratio == aspectRatioGuide
                    Surface(
                        color = if (selected) Color(0xFF1686FF) else Color(0xFF2C2C2C),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onAspectRatioSelected(ratio) }
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                geometryModes.forEach { mode ->
                    val selected = geometryMode == mode
                    Surface(
                        color = if (selected) Color(0xFF1686FF) else Color(0xFF2C2C2C),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { geometryMode = mode }
                    ) {
                        Text(mode, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Remove Tool Panel matching Reference Screenshot 35
 */
@Composable
private fun RemoveToolPanel(
    brushSize: Float = 25f,
    onBrushSizeChange: (Float) -> Unit = {}
) {
    var localBrushSize by remember(brushSize) { mutableFloatStateOf(brushSize) }
    var detectObjects by remember { mutableStateOf(false) }
    var generativeAI by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpectrumSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Floating pill: "Remove extra people"
        Surface(
            color = Color(0xFF252525),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF3A3A3A)),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { }
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove extra people", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        RampedSlider(
            label = "Size",
            value = localBrushSize,
            minVal = 1f,
            maxVal = 100f,
            defaultValue = 25f,
            isBipolar = false,
            onValueChangeStart = { },
            onValueChange = {
                localBrushSize = it
                onBrushSizeChange(it)
            },
            onValueChangeEnd = { }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Detect objects", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = detectObjects,
                onCheckedChange = { detectObjects = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1686FF)
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Generative AI", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = generativeAI,
                onCheckedChange = { generativeAI = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1686FF)
                )
            )
        }
    }
}

/**
 * Lens Blur Panel matching Reference Screenshot 26
 */
@Composable
private fun LensBlurPanel(
    blurAmount: Float,
    onBlurAmountChange: (Float) -> Unit,
    selectedBokehShape: String,
    onBokehShapeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Surface(
            color = Color(0xFF242424),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Depth map detected", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        RampedSlider(
            label = "Blur Amount",
            value = blurAmount,
            minVal = 0f,
            maxVal = 100f,
            defaultValue = 0f,
            isBipolar = false,
            onValueChangeStart = { },
            onValueChange = { onBlurAmountChange(it) },
            onValueChangeEnd = { }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "BOKEH",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        val shapes = listOf("Circle", "Bubble", "5-blade", "Ring", "Cat eye")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shapes.forEach { shape ->
                val isSelected = selectedBokehShape == shape
                Surface(
                    color = if (isSelected) Color(0xFF1686FF) else Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onBokehShapeSelected(shape) }
                ) {
                    Text(
                        text = shape,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Optics Panel matching Reference Screenshot 31
 */
@Composable
private fun OpticsPanel(
    removeChromaticAberration: Boolean,
    onRemoveChromaticAberrationChange: (Boolean) -> Unit,
    enableLensCorrections: Boolean,
    onEnableLensCorrectionsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Remove chromatic aberration",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = removeChromaticAberration,
                onCheckedChange = onRemoveChromaticAberrationChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1686FF)
                )
            )
        }

        HorizontalDivider(color = Color(0xFF333333))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable lens corrections",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enableLensCorrections,
                onCheckedChange = onEnableLensCorrectionsChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1686FF)
                )
            )
        }
    }
}

/**
 * Profiles Panel matching Reference Screenshot 32
 */
@Composable
private fun ProfilesPanel(
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    selectedProfile: String,
    onProfileSelected: (String) -> Unit
) {
    var showGroupMenu by remember { mutableStateOf(false) }
    val profiles = listOf("Adobe Color", "Adobe Monochrome", "Adobe Portrait", "Adobe Landscape", "Adobe Neutral", "Adobe Vivid")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { showGroupMenu = true }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedGroup,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
        }

        DropdownMenu(
            expanded = showGroupMenu,
            onDismissRequest = { showGroupMenu = false }
        ) {
            listOf("Adobe Raw", "Camera Matching", "Legacy", "Artistic", "B&W", "Modern", "Vintage").forEach { group ->
                DropdownMenuItem(
                    text = { Text(group) },
                    onClick = {
                        onGroupSelected(group)
                        showGroupMenu = false
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            profiles.forEach { profile ->
                val isSelected = selectedProfile == profile
                Surface(
                    color = Color(0xFF282828),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, if (isSelected) Color(0xFF1686FF) else Color(0xFF404040)),
                    modifier = Modifier
                        .width(96.dp)
                        .clickable { onProfileSelected(profile) }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (profile) {
                                        "Adobe Monochrome" -> Brush.verticalGradient(listOf(Color(0xFF222222), Color(0xFF888888)))
                                        "Adobe Landscape" -> Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF388E3C)))
                                        "Adobe Portrait" -> Brush.verticalGradient(listOf(Color(0xFF8D6E63), Color(0xFFD7CCC8)))
                                        "Adobe Vivid" -> Brush.verticalGradient(listOf(Color(0xFFE65100), Color(0xFFFFB74D)))
                                        else -> Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF42A5F5)))
                                    }
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = profile.removePrefix("Adobe "),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
