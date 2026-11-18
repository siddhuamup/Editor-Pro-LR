package com.cleanlr.ui.editor

import android.content.res.Configuration
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.cleanlr.R
import com.cleanlr.model.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.cleanlr.profile.ProfileManager
import kotlinx.coroutines.launch
import kotlin.math.abs

// Tip 22: 12.dp fading edge on scrollable panels using CompositingStrategy.Offscreen
fun Modifier.fadingEdge(
    scrollState: ScrollState,
    topEdgeHeight: Dp = 12.dp,
    bottomEdgeHeight: Dp = 12.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Auto)
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
    val cropSubPage by viewModel.cropSubPage.collectAsState()
    val genAiConfig by viewModel.genAiConfig.collectAsState()
    val isGenAiProcessing by viewModel.isGenAiProcessing.collectAsState()
    val showGenAiSettingsDialog by viewModel.showGenAiSettingsDialog.collectAsState()
    val removeBrushPoints by viewModel.removeBrushPoints.collectAsState()
    val useGenerativeAiRemove by viewModel.useGenerativeAiRemove.collectAsState()
    val removeToolMode by viewModel.removeToolMode.collectAsState()
    val healFeather by viewModel.healFeather.collectAsState()
    val healOpacity by viewModel.healOpacity.collectAsState()
    val detectObjectsRemove by viewModel.detectObjectsRemove.collectAsState()
    val isRefineEraseMode by viewModel.isRefineEraseMode.collectAsState()
    val healCloneSourceNorm by viewModel.healCloneSourceNorm.collectAsState()
    val modelStates by viewModel.modelStates.collectAsState()
    val showModelManagerSheet by viewModel.showModelManagerSheet.collectAsState()
    val discoveredLanServers by viewModel.discoveredLanServers.collectAsState()
    val isLanScanning by viewModel.isLanScanning.collectAsState()
    val opticalBlurPack by viewModel.opticalBlurPack.collectAsState()
    val opticalStageStatus by viewModel.opticalStageStatus.collectAsState()
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
    var selectedMobileTool by rememberSaveable { mutableStateOf<String?>("Edit") }
    var showCreateMaskSheet by remember { mutableStateOf(false) }
    var selectedMaskCategory by remember { mutableStateOf(LocalAdjustmentCategory.LIGHT) }
    var isLensBlurDepthDetecting by remember { mutableStateOf(true) }

    var isHdrModeActive by remember { mutableStateOf(false) }
    var hdrLimitEv by remember { mutableFloatStateOf(1.0f) }
    var lensBlurAmount by remember { mutableFloatStateOf(21f) }
    var selectedBokehShape by remember { mutableStateOf("Circle") }
    var removeChromaticAberration by remember { mutableStateOf(false) }
    var enableLensCorrections by remember { mutableStateOf(false) }
    var selectedProfileGroup by remember { mutableStateOf("Basic") }
    var selectedProfile by remember { mutableStateOf("Artistic 01") }

    val allPresets by viewModel.allPresets.collectAsState()
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var colorSubTab by remember { mutableStateOf("WB") }
    var selectedCurveChannel by remember { mutableStateOf(com.cleanlr.ui.components.CurveChannel.RGB) }
    val isDedicatedSubMode = (activeTab == EditorToolTab.COLOR && colorSubTab in listOf("MIXER", "GRADING")) ||
        activeTab == EditorToolTab.CURVES ||
        activeTab == EditorToolTab.BLUR ||
        activeTab == EditorToolTab.PROFILES
    var effectsSubTab by remember { mutableStateOf("Effects") }
    var detailSubTab by remember { mutableStateOf("Sharpening") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val xmpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importXmpPreset(it, context) }
    }

    var pendingImportModelId by remember { mutableStateOf<String?>(null) }
    val localModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val mid = pendingImportModelId
        if (uri != null && mid != null) {
            viewModel.importLocalByomModel(mid, uri)
        }
        pendingImportModelId = null
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

    val synchronizeSelectedTool: (String?) -> Unit = { tool ->
        if (selectedMobileTool == tool && tool == "Crop") {
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = canvasBackground.color,
        topBar = {
            if (!isCleanScreenMode) {
                if (selectedMobileTool == "Crop") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.size(36.dp))
                            Surface(
                                color = Color(0xFF262628),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.setComparingOriginal(true)
                                            tryAwaitRelease()
                                            viewModel.setComparingOriginal(false)
                                        }
                                    )
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
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_context_help),
                                    contentDescription = "Help",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (activeTab == EditorToolTab.PROFILES) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_context_help),
                                    contentDescription = "Help",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (activeTab == EditorToolTab.BLUR) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_context_help),
                                    contentDescription = "Help",
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleComparingOriginal() }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_compare_before_after),
                                    contentDescription = "Compare original",
                                    tint = if (isComparingOriginal) SpectrumBlue else Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        modifier = Modifier.height(50.dp),
                        navigationIcon = {
                            IconButton(onClick = { onBack?.invoke() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_back_chevron),
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
                        val isPresetsOrActions = selectedMobileTool in listOf("Presets", "Actions")
                        val isMasking = selectedMobileTool == "Masking"
                        val isCurvesOrSubColor = activeTab == EditorToolTab.CURVES || (activeTab == EditorToolTab.COLOR && colorSubTab in listOf("MIXER", "GRADING"))

                        // HDR Mode Pill Badge (Screenshot 14)
                        if (!isPresetsOrActions && (isHdrModeActive || recipe.isHdrEnabled)) {
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

                        // Undo with dropdown arrow & long-press history (Screenshots 14, 34, 35)
                        if (!isPresetsOrActions) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .pointerInput(canUndo) {
                                            detectTapGestures(
                                                onTap = { if (canUndo) viewModel.undo() },
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
                                        painter = painterResource(R.drawable.ic_lr_undo),
                                        contentDescription = "Undo (Tap) / History (Long-Press)",
                                        tint = if (canUndo) SpectrumTextPrimary else SpectrumIconDisabled,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lr_chevron_down),
                                        contentDescription = null,
                                        tint = if (canUndo) SpectrumTextPrimary else SpectrumIconDisabled,
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

                        // Compare Before/After Icon (Shown in Edit and Remove, hidden in Masking, Curves, Color Mixer, Presets, Actions)
                        if (!isPresetsOrActions && !isMasking && !isCurvesOrSubColor) {
                            IconButton(onClick = { viewModel.toggleComparingOriginal() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_compare_before_after),
                                    contentDescription = "Compare before/after",
                                    tint = if (isComparingOriginal) SpectrumBlue else SpectrumTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Share / Export Icon (Screenshots 4, 6: standard Android share icon)
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_share_export),
                                contentDescription = "Share and Export",
                                tint = SpectrumTextPrimary
                            )
                        }

                        // Mask overlay toggle in Masking mode (Screenshot 34: Placed right after Share and before ⋮)
                        if (isMasking) {
                            IconButton(onClick = { viewModel.toggleMaskOverlay() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_mask_hide_eye),
                                    contentDescription = "Toggle mask overlay",
                                    tint = if (isMaskOverlayVisible) SpectrumBlue else SpectrumIconDisabled
                                )
                            }
                        }

                        // 3-dots Overflow Menu (Screenshots 4, 5: 3 vertical dots)
                        Box {
                            IconButton(onClick = { showEditorOverflowMenu = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_more_vert),
                                    contentDescription = "More options",
                                    tint = SpectrumTextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showEditorOverflowMenu,
                                onDismissRequest = { showEditorOverflowMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E1E1E))
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painter = painterResource(R.drawable.ic_lr_save_to_device), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_slider_exposure), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_create_preset), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_copy_settings), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_paste_settings), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_versions), contentDescription = null, tint = SpectrumTextPrimary, modifier = Modifier.size(18.dp))
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
                                            Icon(painter = painterResource(R.drawable.ic_lr_context_help), contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Help", color = SpectrumTextSecondary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = { showEditorOverflowMenu = false }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painter = painterResource(R.drawable.ic_lr_mask_people), contentDescription = null, tint = SpectrumTextSecondary, modifier = Modifier.size(18.dp))
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
            }
        },
    bottomBar = {
        if (!isCleanScreenMode) {
            if (isDedicatedSubMode) {
                when (activeTab) {
                    EditorToolTab.BLUR -> DedicatedCommitBottomBar(
                        title = "Lens blur",
                        onCancel = {
                            viewModel.updateRecipe { it.copy(blur = it.blur.copy(amount = 0f)) }
                            viewModel.setActiveTab(EditorToolTab.LIGHT)
                        },
                        onConfirm = {
                            viewModel.setActiveTab(EditorToolTab.LIGHT)
                        }
                    )
                    EditorToolTab.PROFILES -> DedicatedCommitBottomBar(
                        title = "Profiles",
                        onCancel = { viewModel.setActiveTab(EditorToolTab.LIGHT) },
                        onConfirm = { viewModel.setActiveTab(EditorToolTab.LIGHT) }
                    )
                    else -> {}
                }
            } else {
                when (selectedMobileTool) {
                    "Crop" -> DedicatedCommitBottomBar(
                        title = "Crop and expand",
                        onCancel = {
                            viewModel.cancelCrop()
                            selectedMobileTool = null
                        },
                        onConfirm = {
                            viewModel.confirmCrop()
                            selectedMobileTool = null
                        }
                    )
                    else -> MobilePrimaryToolbar(
                        selectedTool = selectedMobileTool,
                        onToolSelected = synchronizeSelectedTool
                    )
                }
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
                    isCropActive = (selectedMobileTool == "Crop" || isCropActive),
                    isExpandEnabled = recipe.crop.isExpandEnabled,
                    isGuidedUprightActive = (cropSubPage == "Geometry" && recipe.crop.uprightMode == "Guided"),
                    isRemoveBrushActive = (selectedMobileTool == "Remove"),
                    removeBrushPoints = removeBrushPoints,
                    removeToolMode = removeToolMode,
                    isRefineEraseMode = isRefineEraseMode,
                    healCloneSourceNorm = healCloneSourceNorm,
                    onHealCloneSourceChanged = { nx, ny -> viewModel.setHealCloneSourceNorm(nx, ny) },
                    isGenAiProcessing = isGenAiProcessing,
                    opticalBlurPack = opticalBlurPack,
                    onRemoveBrushPoint = { nx, ny -> viewModel.addRemoveBrushPoint(nx, ny) },
                    onCropRectChanged = { l, t, r, b -> viewModel.setCropRect(l, t, r, b) },
                    onGuidedLineChanged = { id, x1, y1, x2, y2 -> viewModel.updateGuidedLine(id, x1, y1, x2, y2) },
                    onTriggerExpandPreview = { viewModel.triggerGenerativeExpandPreview() },
                    isCurveActive = (activeTab == EditorToolTab.CURVES),
                    curvePoints = when (selectedCurveChannel) {
                        com.cleanlr.ui.components.CurveChannel.RGB -> recipe.curves.composite
                        com.cleanlr.ui.components.CurveChannel.RED -> recipe.curves.red
                        com.cleanlr.ui.components.CurveChannel.GREEN -> recipe.curves.green
                        com.cleanlr.ui.components.CurveChannel.BLUE -> recipe.curves.blue
                    },
                    curveChannelColor = selectedCurveChannel.color,
                    onCurvePointChanged = { updated ->
                        val updatedCurves = when (selectedCurveChannel) {
                            com.cleanlr.ui.components.CurveChannel.RGB -> recipe.curves.copy(composite = updated, pointCurve = updated)
                            com.cleanlr.ui.components.CurveChannel.RED -> recipe.curves.copy(red = updated)
                            com.cleanlr.ui.components.CurveChannel.GREEN -> recipe.curves.copy(green = updated)
                            com.cleanlr.ui.components.CurveChannel.BLUE -> recipe.curves.copy(blue = updated)
                        }
                        viewModel.updateRecipe("Tone Curve") { it.copy(curves = updatedCurves) }
                    },
                    onCurveTouchStart = { viewModel.onSliderTouchStart() },
                    onCurveTouchEnd = { viewModel.onSliderTouchEnd(it) },
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
                                        painter = painterResource(R.drawable.ic_lr_share_export),
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

                // Floating pill: "Remove extra people" (Reference Screenshot 083752 & 201127)
                if (selectedMobileTool == "Remove" && removeToolMode == com.cleanlr.engine.RemoveToolMode.REMOVE && !isCleanScreenMode) {
                    Surface(
                        color = Color(0xE62A2A2C),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color(0xFF4A4A4F)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clickable(enabled = !isGenAiProcessing) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.executeRemoveExtraPeople()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_distracting_people),
                                contentDescription = "Remove extra people",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = "Remove extra people",
                                color = Color(0xFFE8E8EA),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                // Floating blue depth detection banner for Lens Blur (Reference Screenshot 083626)
                if (activeTab == EditorToolTab.BLUR && isLensBlurDepthDetecting && !isCleanScreenMode) {
                    Surface(
                        color = Color(0xFF1473E6),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Detecting depth using AI...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { isLensBlurDepthDetecting = false }
                            )
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
            }

            // Tip 5: 8.dp Subtle Top Shadow between Canvas & Controls Panel
            if (selectedMobileTool != null) {
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
            }

                // Tip 6: 3-Snap Panel Height Animation (Actions mode shows only secondary strip over photo, no upper panel)
                val animatedPanelHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (selectedMobileTool != null && selectedMobileTool != "Actions") {
                        if (isDedicatedSubMode) {
                            if (activeTab == EditorToolTab.CURVES) 130.dp
                            else if (activeTab == EditorToolTab.BLUR) 245.dp
                            else if (activeTab == EditorToolTab.PROFILES) 160.dp
                            else 360.dp
                        } else if (selectedMobileTool == "Masking" && recipe.masks.isEmpty()) {
                            0.dp
                        } else {
                            when (panelSnapState) {
                                PanelSnapState.COLLAPSED -> 0.dp
                                PanelSnapState.COMPACT -> if (selectedMobileTool == "Crop") (if (cropSubPage == "Geometry") 336.dp else 300.dp) else if (selectedMobileTool == "Presets") 300.dp else if (selectedMobileTool == "Remove") 220.dp else 210.dp
                                PanelSnapState.EXPANDED -> 380.dp
                            }
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

                if (!isCleanScreenMode && animatedPanelHeight > 0.dp && selectedMobileTool != null) {
                    if (selectedMobileTool == "Edit" && !isDedicatedSubMode) {
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
                    }

                    // Active Panel Controls (Scrollable) with 12.dp fading edge (Tip 22)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedPanelHeight)
                            .background(SpectrumSurface)
                            .graphicsLayer {
                                alpha = animatedPanelAlpha
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
                            }
                    ) {
                        if (isCropActive || selectedMobileTool == "Crop") {
                            CropAndExpandPanel(
                                crop = recipe.crop,
                                page = cropSubPage,
                                onPageChanged = { viewModel.setCropSubPage(it) },
                                onRotate = { viewModel.rotateCrop(it) },
                                onFlip = { viewModel.flipCrop(it) },
                                onStraighten = { viewModel.setStraightenAngle(it) },
                                onAutoStraighten = { viewModel.autoStraighten() },
                                aspectRatioGuide = aspectRatioGuide,
                                onAspectRatioSelected = { viewModel.applyAspectRatio(it) },
                                onToggleAspectLock = { viewModel.toggleAspectLock() },
                                onSwapAspectOrientation = { viewModel.swapAspectRatioOrientation() },
                                onResetCrop = { viewModel.resetCrop() },
                                onResetGeometry = { viewModel.resetGeometryOnly() },
                                isExpandEnabled = recipe.crop.isExpandEnabled,
                                onExpandToggled = { viewModel.setExpandEnabled(it) },
                                onTriggerExpandPreview = { viewModel.triggerGenerativeExpandPreview() },
                                constrainCrop = recipe.crop.constrainCrop,
                                onConstrainCropToggled = { viewModel.setConstrainCrop(it) },
                                uprightMode = recipe.crop.uprightMode,
                                onUprightModeSelected = { viewModel.setUprightMode(it) },
                                onAddGuidedLine = { viewModel.addGuidedLine() },
                                onClearGuidedLines = { viewModel.clearGuidedLines() },
                                distortion = recipe.crop.distortion,
                                onDistortionChanged = { viewModel.setDistortion(it) },
                                vertical = recipe.crop.verticalPerspective,
                                onVerticalChanged = { viewModel.setVerticalPerspective(it) },
                                horizontal = recipe.crop.horizontalPerspective,
                                onHorizontalChanged = { viewModel.setHorizontalPerspective(it) },
                                rotate = recipe.crop.geometryRotate,
                                onRotateChanged = { viewModel.setGeometryRotate(it) },
                                aspect = recipe.crop.geometryAspect,
                                onAspectChanged = { viewModel.setGeometryAspect(it) },
                                scale = recipe.crop.geometryScale,
                                onScaleChanged = { viewModel.setGeometryScale(it) },
                                xOffset = recipe.crop.geometryXOffset,
                                onXOffsetChanged = { viewModel.setGeometryXOffset(it) },
                                yOffset = recipe.crop.geometryYOffset,
                                onYOffsetChanged = { viewModel.setGeometryYOffset(it) },
                                onTouchStart = { viewModel.onSliderTouchStart() },
                                onTouchEnd = { viewModel.onSliderTouchEnd(it) }
                            )
                        } else if (selectedMobileTool == "Presets" || activeTab == EditorToolTab.PRESETS) {
                            // Mounted directly without outer verticalScroll to eliminate nested scroll conflict
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
                                },
                                previewBitmap = bitmap,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
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
                                    selectedMobileTool == "Remove" -> RemoveToolPanel(
                                        activeMode = removeToolMode,
                                        onModeSelected = { viewModel.setRemoveToolMode(it) },
                                        brushSize = brushSize,
                                        onBrushSizeChange = { viewModel.setBrushSize(it) },
                                        feather = healFeather,
                                        onFeatherChange = { viewModel.setHealFeather(it) },
                                        opacity = healOpacity,
                                        onOpacityChange = { viewModel.setHealOpacity(it) },
                                        detectObjects = detectObjectsRemove,
                                        onDetectObjectsChange = { viewModel.setDetectObjectsRemove(it) },
                                        useGenerativeAi = useGenerativeAiRemove,
                                        onUseGenerativeAiChange = { viewModel.setUseGenerativeAiRemove(it) },
                                        isRefineMode = isRefineEraseMode,
                                        onToggleRefineMode = { viewModel.toggleRefineEraseMode() },
                                        hasBrushStrokes = removeBrushPoints.isNotEmpty(),
                                        isProcessing = isGenAiProcessing,
                                        onApplyRemove = { viewModel.executeRemoveStrokes() },
                                        onClearStrokes = { viewModel.clearRemoveBrushPoints() },
                                        onOpenAiInfo = { viewModel.openModelManagerSheet() }
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
                                            painter = painterResource(R.drawable.ic_lr_curve),
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

                            // Authentic Adobe Lightroom Header (Screenshot 083509)
                            if (colorSubTab == "WB") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isBw = recipe.presence.saturation <= -99f
                                    Surface(
                                        color = if (isBw) SpectrumBlue else SpectrumElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, SpectrumInnerBorder),
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
                                            text = "B & W",
                                            color = if (isBw) Color.White else SpectrumTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Grading Button (Screenshot 083509)
                                        Surface(
                                            color = SpectrumElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, SpectrumInnerBorder),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                colorSubTab = "GRADING"
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_lr_cg_3way_wheels),
                                                    contentDescription = "Color Grading",
                                                    tint = SpectrumTextPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Grading",
                                                    color = SpectrumTextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (isGradingModified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(SpectrumBlue)
                                                    )
                                                }
                                            }
                                        }

                                        // Mix Button (Screenshot 083509)
                                        Surface(
                                            color = SpectrumElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, SpectrumInnerBorder),
                                            modifier = Modifier.clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                colorSubTab = "MIXER"
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Canvas(modifier = Modifier.size(14.dp)) {
                                                    drawCircle(
                                                        brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                                                        style = Stroke(width = 2.dp.toPx())
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Mix",
                                                    color = SpectrumTextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (isMixerModified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(SpectrumBlue)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            }

                            when (colorSubTab) {
                                "WB" -> {
                                    // Authentic White Balance Header Row (Screenshot 083509)
                                    var showWbMenu by remember { mutableStateOf(false) }
                                    val currentWbPreset = remember(recipe.basic.temperature, recipe.basic.tint) {
                                        when {
                                            abs(recipe.basic.temperature - 5500f) < 50f && abs(recipe.basic.tint - 0f) < 2f -> "As Shot"
                                            abs(recipe.basic.temperature - 5500f) < 50f && abs(recipe.basic.tint - 10f) < 2f -> "Daylight"
                                            abs(recipe.basic.temperature - 6500f) < 50f && abs(recipe.basic.tint - 10f) < 2f -> "Cloudy"
                                            abs(recipe.basic.temperature - 7500f) < 50f && abs(recipe.basic.tint - 10f) < 2f -> "Shade"
                                            abs(recipe.basic.temperature - 3200f) < 50f && abs(recipe.basic.tint - 0f) < 2f -> "Tungsten"
                                            abs(recipe.basic.temperature - 4000f) < 50f && abs(recipe.basic.tint - (-10f)) < 2f -> "Fluorescent"
                                            abs(recipe.basic.temperature - 5500f) < 50f && abs(recipe.basic.tint - 2f) < 2f -> "Auto"
                                            else -> "Custom"
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "White balance",
                                            color = SpectrumTextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal
                                        )

                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { showWbMenu = true }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = currentWbPreset,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_lr_chevron_down),
                                                    contentDescription = "Select White Balance Preset",
                                                    tint = SpectrumTextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showWbMenu,
                                                onDismissRequest = { showWbMenu = false }
                                            ) {
                                                listOf(
                                                    Triple("As Shot", 5500f, 0f),
                                                    Triple("Auto", 5500f, 2f),
                                                    Triple("Daylight", 5500f, 10f),
                                                    Triple("Cloudy", 6500f, 10f),
                                                    Triple("Shade", 7500f, 10f),
                                                    Triple("Tungsten", 3200f, 0f),
                                                    Triple("Fluorescent", 4000f, -10f),
                                                    Triple("Custom", recipe.basic.temperature, recipe.basic.tint)
                                                ).forEach { (label, temp, tint) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label, color = if (label == currentWbPreset) SpectrumBlue else Color.White) },
                                                        onClick = {
                                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                            showWbMenu = false
                                                            viewModel.updateRecipe("WB: $label") {
                                                                it.copy(basic = it.basic.copy(temperature = temp, tint = tint)).syncLegacyFields()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe("Auto Neutral WB") {
                                                    it.copy(basic = it.basic.copy(temperature = 5500f, tint = 0f)).syncLegacyFields()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_lr_wb_eyedropper),
                                                contentDescription = "White Balance Eyedropper",
                                                tint = SpectrumIconNormal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    RampedSlider(
                                        label = "Temp",
                                        value = ((recipe.basic.temperature - 5500f) / 45f).coerceIn(-100f, 100f),
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
                                        formatPattern = "%.0f",
                                        trackBrush = tempTrackBrush,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setSliderDragging(true)
                                        },
                                        onValueChange = { rel ->
                                            viewModel.updateRecipe { r ->
                                                r.copy(
                                                    basic = r.basic.copy(temperature = (5500f + rel * 45f).coerceIn(2000f, 10000f))
                                                ).syncLegacyFields()
                                            }
                                        },
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

                                    // Vibrance & Saturation
                                    RampedSlider(
                                        label = "Vibrance",
                                        value = recipe.basic.vibrance,
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
                                                    basic = r.basic.copy(vibrance = it)
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
                                        value = recipe.basic.saturation,
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
                                                    basic = r.basic.copy(saturation = it)
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
                                        onTouchEnd = { viewModel.onSliderTouchEnd(it) },
                                        isTatActive = isTatActive,
                                        onToggleTat = { viewModel.toggleTat() },
                                        onDone = { colorSubTab = "WB" }
                                    )
                                }
                                "GRADING" -> {
                                    ColorGradingWheelWidget(
                                        colorGrading = recipe.colorGrading,
                                        onColorGradingChanged = { updated ->
                                            viewModel.updateRecipe("Color Grading") { it.copy(colorGrading = updated) }
                                        },
                                        onTouchStart = { viewModel.onSliderTouchStart() },
                                        onTouchEnd = { viewModel.onSliderTouchEnd(it) },
                                        onDone = { colorSubTab = "WB" }
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

                            // Authentic Lightroom Sub-Tabs (Effects | Vignette | Grain - Screenshots 083640, 083652, 083657)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "Effects" to "Effects",
                                    "Vignette" to "Vignette",
                                    "Grain" to "Grain"
                                ).forEach { (id, label) ->
                                    val isSelected = effectsSubTab == id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(IntrinsicSize.Max)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                effectsSubTab = id
                                            }
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else SpectrumTextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(if (isSelected) Color.White else Color.Transparent)
                                        )
                                    }
                                }
                            }

                            when (effectsSubTab) {
                                "Effects" -> {
                                    RampedSlider(
                                        label = "Texture",
                                        value = recipe.effects.texture,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
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
                                        label = "Clarity",
                                        value = recipe.effects.clarity,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
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
                                        label = "Dehaze",
                                        value = recipe.effects.dehaze,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
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
                                    RampedSlider(
                                        label = "Amount",
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
                                    RampedSlider(
                                        label = "Roundness",
                                        value = recipe.effects.vignetteRoundness,
                                        minVal = -100f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = true,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(effects = r.effects.copy(vignetteRoundness = it)).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Roundness") }
                                    )
                                    RampedSlider(
                                        label = "Highlights",
                                        value = recipe.effects.vignetteHighlights,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = {
                                            viewModel.updateRecipe { r ->
                                                r.copy(effects = r.effects.copy(vignetteHighlights = it)).syncLegacyFields()
                                            }
                                        },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Highlights") }
                                    )
                                }
                                "Grain" -> {
                                    RampedSlider(
                                        label = "Amount",
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
                                    RampedSlider(
                                        label = "Size",
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
                                        label = "Roughness",
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

                        EditorToolTab.DETAIL -> {
                            val isSharpeningModified = recipe.detail.sharpening != 40f && recipe.detail.sharpening != 0f
                            val isNoiseModified = recipe.detail.luminanceNoise != 0f || recipe.detail.colorNoise != 25f

                            // Authentic Lightroom Sub-Tabs (Sharpening | Noise | Color noise - Screenshot 083710)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "Sharpening" to "Sharpening",
                                    "Noise" to "Noise",
                                    "Color noise" to "Color noise"
                                ).forEach { (id, label) ->
                                    val isSelected = detailSubTab == id
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(IntrinsicSize.Max)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                detailSubTab = id
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else SpectrumTextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(if (isSelected) Color.White else Color.Transparent)
                                        )
                                    }
                                }
                            }

                            when (detailSubTab) {
                                "Sharpening" -> {
                                    RampedSlider(
                                        label = "Amount",
                                        value = recipe.detail.sharpening,
                                        minVal = 0f,
                                        maxVal = 150f,
                                        defaultValue = 40f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpening = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpening Amount") }
                                    )
                                    RampedSlider(
                                        label = "Radius",
                                        value = recipe.detail.sharpenRadius,
                                        minVal = 0.5f,
                                        maxVal = 3.0f,
                                        defaultValue = 1.0f,
                                        isBipolar = false,
                                        formatPattern = "%.2f",
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
                                    RampedSlider(
                                        label = "Masking",
                                        value = recipe.detail.sharpenMasking,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = {
                                            viewModel.onSliderTouchStart()
                                            viewModel.setAltClippingMode(3)
                                        },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenMasking = it)).syncLegacyFields() } },
                                        onValueChangeEnd = {
                                            viewModel.setAltClippingMode(0)
                                            viewModel.onSliderTouchEnd("Masking")
                                        }
                                    )
                                }
                                "Noise" -> {
                                    RampedSlider(
                                        label = "Noise reduction",
                                        value = recipe.detail.luminanceNoise,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(luminanceNoise = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Noise Reduction") }
                                    )
                                    RampedSlider(
                                        label = "Detail",
                                        value = recipe.detail.luminanceDetail,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 50f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(luminanceDetail = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Noise Detail") }
                                    )
                                    RampedSlider(
                                        label = "Contrast",
                                        value = recipe.detail.luminanceContrast,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 0f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(luminanceContrast = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Noise Contrast") }
                                    )
                                }
                                "Color noise" -> {
                                    RampedSlider(
                                        label = "Color noise reduction",
                                        value = recipe.detail.colorNoise,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 25f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(colorNoise = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Color Noise Reduction") }
                                    )
                                    RampedSlider(
                                        label = "Detail",
                                        value = recipe.detail.colorDetail,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 50f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(colorDetail = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Color Noise Detail") }
                                    )
                                    RampedSlider(
                                        label = "Smoothness",
                                        value = recipe.detail.colorSmoothness,
                                        minVal = 0f,
                                        maxVal = 100f,
                                        defaultValue = 50f,
                                        isBipolar = false,
                                        onValueChangeStart = { viewModel.onSliderTouchStart() },
                                        onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(colorSmoothness = it)).syncLegacyFields() } },
                                        onValueChangeEnd = { viewModel.onSliderTouchEnd("Color Noise Smoothness") }
                                    )
                                }
                            }
                        }

                        EditorToolTab.CURVES -> {
                            // Authentic Tone Curve Header (Screenshot 083443)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Curve",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = SpectrumElevated,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, SpectrumInnerBorder),
                                    modifier = Modifier.clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.setActiveTab(EditorToolTab.LIGHT)
                                    }
                                ) {
                                    Text(
                                        text = "DONE",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            // Authentic 5-Channel Selector Buttons Row (RGB, Red, Green, Blue, Parametric)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.cleanlr.ui.components.CurveChannel.entries.forEach { ch ->
                                    val isSelected = selectedCurveChannel == ch
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedCurveChannel = ch
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawCircle(ch.color)
                                                drawCircle(if (ch == com.cleanlr.ui.components.CurveChannel.RGB) Color.Black else Color.White, radius = 3.dp.toPx())
                                            }
                                        } else {
                                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                                drawCircle(
                                                    color = ch.color,
                                                    style = Stroke(width = 1.8.dp.toPx())
                                                )
                                            }
                                        }
                                    }
                                }

                                // 5th Button: Parametric Curve Icon Button
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                        val p = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(size.width * 0.25f, size.height * 0.75f)
                                            cubicTo(
                                                size.width * 0.45f, size.height * 0.75f,
                                                size.width * 0.55f, size.height * 0.25f,
                                                size.width * 0.75f, size.height * 0.25f
                                            )
                                        }
                                        drawPath(p, color = Color(0xFFAAAAAA), style = Stroke(width = 2.dp.toPx()))
                                    }
                                }
                            }
                        }

                        EditorToolTab.BLUR -> {
                            LensBlurPanel(
                                blurAmount = recipe.blur.amount,
                                onBlurAmountChange = { amt ->
                                    viewModel.updateRecipe { it.copy(blur = it.blur.copy(amount = amt)) }
                                },
                                bokehBoost = recipe.blur.boost,
                                onBokehBoostChange = { boost ->
                                    viewModel.updateRecipe { it.copy(blur = it.blur.copy(boost = boost)) }
                                },
                                focalDepth = ((recipe.blur.focalRangeStart + recipe.blur.focalRangeEnd) * 0.5f),
                                onFocalDepthChange = { focal ->
                                    val half = 0.08f
                                    viewModel.updateRecipe {
                                        it.copy(
                                            blur = it.blur.copy(
                                                focalRangeStart = (focal - half).coerceIn(0f, 1f),
                                                focalRangeEnd = (focal + half).coerceIn(0f, 1f)
                                            )
                                        )
                                    }
                                },
                                selectedBokehShape = recipe.blur.bokehShape,
                                onBokehShapeSelected = { shape ->
                                    viewModel.updateRecipe { it.copy(blur = it.blur.copy(bokehShape = shape)) }
                                },
                                hasOpticalPack = opticalBlurPack != null,
                                opticalStatusText = opticalStageStatus,
                                onBuildOpticalCleanPlate = { viewModel.buildOpticalCleanPlateAndDepth() },
                                onOpenByomModels = { viewModel.openModelManagerSheet() }
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
                            MaskingSlidersPanel(
                                layers = recipe.masks,
                                activeLayerIndex = activeMaskLayerIndex,
                                selectedCategory = selectedMaskCategory,
                                onUpdateLayerAdjustments = { layer -> viewModel.updateMaskLayer(layer) },
                                onDeleteActiveLayer = { viewModel.deleteActiveMaskLayer() },
                                onAddSubMaskToActive = { subMask -> viewModel.addSubMaskToActive(subMask) }
                            )
                        }

                        EditorToolTab.ACTIONS, EditorToolTab.REMOVE -> {}
                        EditorToolTab.ACTIONS -> {}
                        EditorToolTab.REMOVE -> {}
                        }
                    }
                    }
                }
            }
        }

            // Category Tool Tabs Bar — Adobe Lightroom Mobile Signature Style (Screenshots 7, 13, 20, 34)
            // Rendered directly beneath the active controls panel, right above the primary bottom navigation
            if (!isCleanScreenMode && !isDedicatedSubMode && selectedMobileTool in listOf("Edit", "Actions", "Masking")) {
                HorizontalDivider(color = Color(0xFF242424), thickness = 0.5.dp)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(spectrum_surface_dark)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedMobileTool == "Actions") {
                        // Action items: Auto, Enhance, Divider, Subject, Background, Sky, Animate (Screenshot 7)
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
                                    painter = painterResource(R.drawable.ic_lr_auto),
                                    contentDescription = "Auto",
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
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.updateRecipe("Enhance") {
                                            it.copy(
                                                basic = it.basic.copy(
                                                    vibrance = (it.basic.vibrance + 15f).coerceAtMost(100f)
                                                ),
                                                effects = it.effects.copy(
                                                    clarity = (it.effects.clarity + 15f).coerceAtMost(100f),
                                                    texture = (it.effects.texture + 10f).coerceAtMost(100f)
                                                )
                                            ).syncLegacyFields()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_quick_actions),
                                    contentDescription = "Enhance",
                                    tint = SpectrumIconNormal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Enhance",
                                    color = SpectrumTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                        item {
                            // Vertical separator line between Enhance and Mask quick-actions
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(Color(0xFF242424))
                            )
                        }
                        val maskActions = listOf(
                            Triple("Subject", R.drawable.ic_lr_mask_subject) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Background", R.drawable.ic_lr_mask_background) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Sky", R.drawable.ic_lr_mask_sky) {
                                viewModel.setActiveTab(EditorToolTab.MASKS)
                                selectedMobileTool = "Masking"
                            },
                            Triple("Animate", R.drawable.ic_lr_edit_replay_video) {
                                android.widget.Toast.makeText(context, "Animation requires motion preview export pipeline", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        items(maskActions) { (label, iconRes, action) ->
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
                                    painter = painterResource(iconRes),
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
                    } else if (selectedMobileTool == "Masking") {
                        // Resting & Active state for Masking tool (Reference Screenshot 083741)
                        // Shows sub-category tabs: Light, Color, Effects, Detail, Optics
                        val maskTabs = listOf(
                            "Light" to (R.drawable.ic_lr_light to LocalAdjustmentCategory.LIGHT),
                            "Color" to (R.drawable.ic_lr_color to LocalAdjustmentCategory.COLOR),
                            "Effects" to (R.drawable.ic_lr_effects to LocalAdjustmentCategory.EFFECTS),
                            "Detail" to (R.drawable.ic_lr_detail to LocalAdjustmentCategory.DETAIL),
                            "Optics" to (R.drawable.ic_lr_optics to null)
                        )
                        val isDimmed = recipe.masks.isEmpty()
                        items(maskTabs) { (title, tabData) ->
                            val (iconRes, category) = tabData
                            val isSelected = !isDimmed && category != null && selectedMaskCategory == category
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(enabled = !isDimmed && category != null) {
                                        category?.let { selectedMaskCategory = it }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .graphicsLayer { alpha = if (isDimmed) 0.35f else 1.0f }
                            ) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = title,
                                    tint = if (isSelected) SpectrumBlue else SpectrumIconNormal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = title,
                                    color = if (isSelected) SpectrumBlue else SpectrumTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_auto),
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
                                Spacer(modifier = Modifier.height(7.dp))
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
                            val isModified = remember(recipe, tab) { viewModel.isTabModified(tab) }
                            val isBypassed = bypassedTabs.contains(tab)
                            val tabIconRes = when (tab) {
                                EditorToolTab.CROP -> R.drawable.ic_lr_crop
                                EditorToolTab.LIGHT -> R.drawable.ic_lr_light
                                EditorToolTab.COLOR -> R.drawable.ic_lr_color
                                EditorToolTab.BLUR -> R.drawable.ic_lr_blur
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
                            Surface(
                                color = if (isSelected) Color(0xFF2C2C2C) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
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
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = tabIconRes),
                                        contentDescription = tab.title,
                                        tint = if (isSelected) Color.White else if (isBypassed) SpectrumIconDisabled else SpectrumIconNormal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tab.title,
                                            color = if (isSelected) Color.White else if (isBypassed) SpectrumIconDisabled else SpectrumTextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (isModified) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2680EB))
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2680EB))
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(7.dp))
                                    }
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_reset_all),
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
                                Spacer(modifier = Modifier.height(7.dp))
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

        if (showCreateMaskSheet) {
            CreateNewMaskBottomSheet(
                onDismissRequest = { showCreateMaskSheet = false },
                onSelectSubMask = { subMask ->
                    showCreateMaskSheet = false
                    viewModel.addMaskLayer(subMask)
                }
            )
        }

        if (showGenAiSettingsDialog) {
            FluxFillServerConfigDialog(
                currentConfig = genAiConfig,
                onDismiss = { viewModel.dismissGenAiSettingsDialog() },
                onSave = { viewModel.saveGenAiConfig(it) }
            )
        }

        if (showModelManagerSheet) {
            ModelManagerBottomSheet(
                modelStates = modelStates,
                storageSummary = remember(modelStates) { viewModel.modelManager.getStorageSummary() },
                discoveredLanServers = discoveredLanServers,
                isLanScanning = isLanScanning,
                onDismiss = { viewModel.dismissModelManagerSheet() },
                onDownloadModel = { viewModel.downloadByomModel(it) },
                onPauseDownload = { viewModel.pauseByomModelDownload(it) },
                onImportLocalModel = { modelId ->
                    pendingImportModelId = modelId
                    localModelPickerLauncher.launch("*/*")
                },
                onDeleteModel = { viewModel.deleteByomModel(it) },
                onStartLanScan = { viewModel.startLanBridgeScan() },
                onConnectLanServer = { viewModel.connectLanServer(it) },
                onOpenCustomEndpointDialog = {
                    viewModel.dismissModelManagerSheet()
                    viewModel.openGenAiSettingsDialog()
                }
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
            val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showShareSheet = false },
                sheetState = shareSheetState,
                containerColor = Color(0xFF1E1E1E),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp)
                ) {
                    // Header Row (Screenshot 083231: [←] Share 1 Photo ... [Select All])
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showShareSheet = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_back_chevron),
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Share 1 Photo",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = Color(0xFF2C2C2E),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        ) {
                            Text(
                                text = "Select All",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                    }

                    // Photo Carousel with blue selection border & checkbox (Screenshot 083231)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(4.dp))
                                .border(2.5.dp, Color(0xFF1473E6), RoundedCornerShape(4.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap!!.asImageBitmap(),
                                    contentDescription = "Selected photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF1473E6),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_select_checkmark),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Quick Actions: Circular Buttons [Share | Export as...] (Screenshot 083231)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    showShareSheet = false
                                    viewModel.shareLastExport(context)
                                }
                                .padding(horizontal = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF262628))
                                    .border(0.5.dp, Color(0xFF242424), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_lr_share_export), contentDescription = "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    showShareSheet = false
                                    showExportConfigSheet = true
                                }
                                .padding(horizontal = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF262628))
                                    .border(0.5.dp, Color(0xFF242424), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_lr_share_export), contentDescription = "Export as...", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Export as...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    // Options List (Screenshot 083231)
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                            Icon(painter = painterResource(R.drawable.ic_lr_save_to_device), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Text("Save copy to device", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    showShareSheet = false
                                    showExportConfigSheet = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_lr_more_options), contentDescription = "Export settings", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showShareSheet = false }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_lr_aspect_ratio), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Get shareable link", color = Color.White, fontSize = 15.sp)
                                Text("Create and share customized albums", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showShareSheet = false }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_lr_edit_replay_video), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Create edit replay", color = Color.White, fontSize = 15.sp)
                                Text("Save and share a video of your edits", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showShareSheet = false }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_lr_community_tab), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Post to Community", color = Color.White, fontSize = 15.sp)
                                Text("Share your edits and get remixes", color = Color(0xFF8E8E93), fontSize = 12.sp)
                            }
                        }
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
            .background(Color(0xFF121212))
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Color(0xFF222222))
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
                    painter = painterResource(R.drawable.ic_lr_close),
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
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = "Apply",
                    tint = Color.White,
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
            border = BorderStroke(0.5.dp, Color(0xFF242424)),
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
                "Enhance Details" to R.drawable.ic_lr_slider_clarity,
                "Select Subject" to R.drawable.ic_lr_mask_subject,
                "Select Sky" to R.drawable.ic_lr_mask_sky,
                "Select Background" to R.drawable.ic_lr_mask_background
            ).forEach { (label, iconRes) ->
                Surface(
                    color = Color(0xFF262626),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF242424)),
                    modifier = Modifier.clickable { onQuickMask(label) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painter = painterResource(iconRes), contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(16.dp))
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
        "Actions" to R.drawable.ic_lr_quick_actions,
        "Presets" to R.drawable.ic_lr_presets,
        "Crop" to R.drawable.ic_lr_crop,
        "Edit" to R.drawable.ic_lr_edit_sliders,
        "Masking" to R.drawable.ic_lr_masking,
        "Remove" to R.drawable.ic_lr_remove_plain
    )

    if (selectedTool == null) {
        // Resting Loupe Floating Pill (Reference Screenshot 4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = spectrum_surface_dark,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(0.5.dp, Color(0xFF242424)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tools.forEach { (label, iconRes) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToolSelected(label) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label,
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = Color(0xFFAAAAAA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Active Editing Docked Toolbar (Reference Screenshots 7, 8, 13, 20, 34, 35)
        // Docked edge-to-edge, icon-only, active tool enclosed in vibrant blue rounded square with indicator dot
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(spectrum_surface_dark)
                .navigationBarsPadding()
        ) {
            HorizontalDivider(color = Color(0xFF242424), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tools.forEach { (label, iconRes) ->
                    val active = selectedTool == label
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1473E6))
                                    .clickable { onToolSelected(label) },
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = { onToolSelected(label) },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = label,
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                if (label == "Edit") {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
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
    onTouchStart: () -> Unit = {},
    onTouchEnd: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val currentAngle by rememberUpdatedState(angle)
    val currentOnAngleChange by rememberUpdatedState(onAngleChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onTouchStart() },
                    onDragEnd = { onTouchEnd("Straighten") },
                    onDragCancel = { onTouchEnd("Straighten") },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.x / 10f
                        var newAngle = (currentAngle + delta).coerceIn(-45f, 45f)
                        // Magnetic snap to 0.00° when crossing zero slowly
                        if (abs(newAngle) < 0.25f && abs(delta) < 0.6f) {
                            if (abs(currentAngle) >= 0.25f) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            newAngle = 0f
                        }
                        currentOnAngleChange(newAngle)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val arcTopY = height * 0.14f
            val arcDip = 25.dp.toPx()
            val arcSpan = width * 0.40f

            // Visible tick window shifts smoothly with angle (-45°..+45°, 1 tick = 1°)
            val halfWindowDeg = 22
            val centerDegInt = angle.toInt()
            for (deg in (centerDegInt - halfWindowDeg)..(centerDegInt + halfWindowDeg)) {
                if (deg < -45 || deg > 45) continue
                val relDeg = (deg - angle) / halfWindowDeg.toFloat() // -1.0 to +1.0
                if (abs(relDeg) > 1.0f) continue

                val tickX = centerX + relDeg * arcSpan
                val curveNorm = relDeg * relDeg
                val currentY = arcTopY + (1f - curveNorm) * arcDip
                val fadeAlpha = (1f - curveNorm * 0.65f).coerceIn(0.15f, 1f)

                val isZeroTick = deg == 0
                val isMajor = deg % 5 == 0
                val isBetweenZeroAndCurrent = (angle > 0f && deg in 0..angle.toInt()) || (angle < 0f && deg in angle.toInt()..0)

                val tickColor = when {
                    isZeroTick -> Color.White.copy(alpha = fadeAlpha)
                    isBetweenZeroAndCurrent -> Color(0xFF38A9FF).copy(alpha = fadeAlpha)
                    isMajor -> Color(0xFFDDDDDD).copy(alpha = fadeAlpha)
                    else -> Color(0xFF888888).copy(alpha = fadeAlpha)
                }

                if (isZeroTick || isMajor) {
                    val tickLen = if (isZeroTick) 9.dp.toPx() else 6.dp.toPx()
                    drawLine(
                        color = tickColor,
                        start = androidx.compose.ui.geometry.Offset(tickX, currentY),
                        end = androidx.compose.ui.geometry.Offset(tickX, currentY + tickLen),
                        strokeWidth = if (isZeroTick) 1.8.dp.toPx() else 1.2.dp.toPx()
                    )
                } else {
                    drawCircle(
                        color = tickColor,
                        radius = 1.25.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(tickX, currentY + 2.dp.toPx())
                    )
                }
            }

            // Fixed center needle indicator pointing up
            val needleTopY = arcTopY + arcDip + 5.dp.toPx()
            drawLine(
                color = if (abs(angle) > 0.01f) Color(0xFF38A9FF) else Color.White,
                start = androidx.compose.ui.geometry.Offset(centerX, needleTopY),
                end = androidx.compose.ui.geometry.Offset(centerX, needleTopY + 11.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Center degree readout pill (tap to reset to 0.00°)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAngleChange(0f)
                }
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.2f°", angle),
                color = if (abs(angle) > 0.01f) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.widthIn(min = 52.dp)
            )
        }
    }
}

@Composable
private fun CropAndExpandPanel(
    crop: com.cleanlr.model.CropAdjustments,
    page: String = "Aspect",
    onPageChanged: (String) -> Unit = {},
    onRotate: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
    onStraighten: (Float) -> Unit,
    onAutoStraighten: () -> Unit = {},
    aspectRatioGuide: String?,
    onAspectRatioSelected: (String?) -> Unit,
    onToggleAspectLock: () -> Unit = {},
    onSwapAspectOrientation: () -> Unit = {},
    onResetCrop: () -> Unit = {},
    onResetGeometry: () -> Unit = {},
    isExpandEnabled: Boolean = false,
    onExpandToggled: (Boolean) -> Unit = {},
    onTriggerExpandPreview: () -> Unit = {},
    constrainCrop: Boolean = false,
    onConstrainCropToggled: (Boolean) -> Unit = {},
    uprightMode: String = "Off",
    onUprightModeSelected: (String) -> Unit = {},
    onAddGuidedLine: () -> Unit = {},
    onClearGuidedLines: () -> Unit = {},
    distortion: Float = 0f,
    onDistortionChanged: (Float) -> Unit = {},
    vertical: Float = 0f,
    onVerticalChanged: (Float) -> Unit = {},
    horizontal: Float = 0f,
    onHorizontalChanged: (Float) -> Unit = {},
    rotate: Float = 0f,
    onRotateChanged: (Float) -> Unit = {},
    aspect: Float = 0f,
    onAspectChanged: (Float) -> Unit = {},
    scale: Float = 100f,
    onScaleChanged: (Float) -> Unit = {},
    xOffset: Float = 0f,
    onXOffsetChanged: (Float) -> Unit = {},
    yOffset: Float = 0f,
    onYOffsetChanged: (Float) -> Unit = {},
    onTouchStart: () -> Unit = {},
    onTouchEnd: (String) -> Unit = {}
) {
    val view = LocalView.current
    var selectedAspectCategory by remember { mutableStateOf(if (aspectRatioGuide == null || aspectRatioGuide == "Original") "Original" else "Ratios") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showManualTransforms by remember { mutableStateOf(false) }

    // Authentic Ratios matching Screenshot_20261024-200720_Lightroom.png
    val standardRatios = listOf(
        "Free" to "Free",
        "1 × 1" to "1:1",
        "1 × 2" to "1:2",
        "2 × 3" to "2:3",
        "3 × 4" to "3:4",
        "4 × 5" to "4:5",
        "5 × 7" to "5:7",
        "8.5 × 11" to "8.5:11",
        "9 × 16" to "9:16",
        "10 × 16" to "10:16",
        "16 × 9" to "16:9"
    )

    // Authentic Instagram Ratios matching Screenshot_20261024-200728_Lightroom.png
    val instagramRatios = listOf(
        "Square" to "1:1",
        "Story" to "9:16",
        "Portrait" to "4:5",
        "Landscape" to "1.91:1",
        "Profile Photo" to "1:1"
    )

    val tiktokRatios = listOf(
        "Video (9:16)" to "9:16",
        "Profile Photo" to "1:1"
    )

    val uprightModes = listOf(
        Triple("Auto", R.drawable.ic_lr_upright_auto, "Auto"),
        Triple("Level", R.drawable.ic_lr_upright_level, "Level"),
        Triple("Vertical", R.drawable.ic_lr_upright_vertical, "Vertical"),
        Triple("Full", R.drawable.ic_lr_upright_full, "Full"),
        Triple("Guided", R.drawable.ic_lr_upright_guided, "Guided")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpectrumSurface)
    ) {
        if (page == "Aspect") {
            // ═════════════════════════════════════════════════════════════════
            // CURVED SMILE DIAL ARC (Screenshot 083306)
            // ═════════════════════════════════════════════════════════════════
            CurvedStraightenDial(
                angle = crop.straightenAngle,
                onAngleChange = onStraighten,
                onTouchStart = onTouchStart,
                onTouchEnd = onTouchEnd
            )

            // ═════════════════════════════════════════════════════════════════
            // 4 FLOATING CIRCULAR ACTION BUTTONS (Screenshot 083306)
            // ═════════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left 2 buttons: Swap Orientation / Aspect Ratios, Aspect Lock
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF262628),
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSwapAspectOrientation()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_aspect_ratio),
                                contentDescription = "Swap aspect orientation",
                                tint = if (crop.aspectRatio != "Free" && crop.aspectRatio != "Original") SpectrumBlue else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF262628),
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onToggleAspectLock()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(if (crop.aspectLocked) R.drawable.ic_lr_crop_lock_closed else R.drawable.ic_lr_crop_lock_open),
                                contentDescription = "Aspect lock",
                                tint = if (crop.aspectLocked) SpectrumBlue else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Right 2 buttons: Rotate 90 deg clockwise, Overflow Menu
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF262628),
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onRotate(true)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_crop_rotate_circle),
                                contentDescription = "Rotate 90 degrees",
                                tint = if (crop.rotationDegrees != 0f) SpectrumBlue else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF262628),
                            modifier = Modifier
                                .size(42.dp)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showOverflowMenu = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_crop_overflow),
                                    contentDescription = "More crop tools",
                                    tint = if (crop.flipHorizontal || crop.flipVertical) SpectrumBlue else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(SpectrumElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto Straighten", color = Color.White) },
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onAutoStraighten()
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rotate 90° Left", color = Color.White) },
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onRotate(false)
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (crop.flipHorizontal) "Flip Horizontal ✓" else "Flip Horizontal",
                                        color = if (crop.flipHorizontal) Color(0xFF38A9FF) else Color.White
                                    )
                                },
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFlip(true)
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (crop.flipVertical) "Flip Vertical ✓" else "Flip Vertical",
                                        color = if (crop.flipVertical) Color(0xFF38A9FF) else Color.White
                                    )
                                },
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFlip(false)
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Crop", color = Color(0xFFFF6B6B)) },
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onResetCrop()
                                    showOverflowMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════
        // SUB-TABS: Aspect | Geometry (Screenshot 083306 & 083314)
        // ═════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("Aspect", "Geometry").forEach { tabName ->
                val isSelected = page == tabName
                Column(
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPageChanged(tabName)
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tabName,
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(2.dp)
                            .background(if (isSelected) Color.White else Color.Transparent)
                    )
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════
        // ASPECT TAB CONTENT: Expand switch, Original, Ratios, Instagram, Tiktok
        // ═════════════════════════════════════════════════════════════════
        if (page == "Aspect") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Expand Card with Switch toggle
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Switch(
                            checked = isExpandEnabled,
                            onCheckedChange = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onExpandToggled(it)
                            },
                            modifier = Modifier.scale(0.72f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1686FF),
                                uncheckedThumbColor = Color(0xFFAAAAAA),
                                uncheckedTrackColor = Color(0xFF333333)
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Expand",
                            color = if (isExpandEnabled) Color(0xFF38A9FF) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Divider line between Expand and Aspect cards
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0xFF383838))
                )

                // 2. Original Card (Authentic svg_original_crop.svg)
                val isOriginalSelected = selectedAspectCategory == "Original"
                Surface(
                    color = if (isOriginalSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedAspectCategory = "Original"
                            onAspectRatioSelected("Original")
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_crop_original),
                            contentDescription = "Original",
                            tint = if (isOriginalSelected) Color.White else Color(0xFFA0A0A0),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Original",
                            color = if (isOriginalSelected) Color.White else Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // 3. Ratios Card (Authentic svg_ratios_crop.svg)
                val isRatiosSelected = selectedAspectCategory == "Ratios"
                Surface(
                    color = if (isRatiosSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedAspectCategory = "Ratios"
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_crop_ratios),
                            contentDescription = "Ratios",
                            tint = if (isRatiosSelected) Color.White else Color(0xFFA0A0A0),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Ratios",
                            color = if (isRatiosSelected) Color.White else Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // 4. Instagram Card (Authentic svg_instagram_crop.svg)
                val isInstagramSelected = selectedAspectCategory == "Instagram"
                Surface(
                    color = if (isInstagramSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedAspectCategory = "Instagram"
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_crop_instagram),
                            contentDescription = "Instagram",
                            tint = if (isInstagramSelected) Color.White else Color(0xFFA0A0A0),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Instagram",
                            color = if (isInstagramSelected) Color.White else Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // 5. Tiktok Card (Authentic svg_tiktok_crop.svg)
                val isTiktokSelected = selectedAspectCategory == "Tiktok"
                Surface(
                    color = if (isTiktokSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .height(72.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedAspectCategory = "Tiktok"
                            onAspectRatioSelected("9:16")
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_crop_tiktok),
                            contentDescription = "Tiktok",
                            tint = if (isTiktokSelected) Color.White else Color(0xFFA0A0A0),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Tiktok",
                            color = if (isTiktokSelected) Color.White else Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Sub-ratios horizontal strip when Ratios, Instagram, or Tiktok is selected
            val activeStrip = when (selectedAspectCategory) {
                "Ratios" -> standardRatios
                "Instagram" -> instagramRatios
                "Tiktok" -> tiktokRatios
                else -> emptyList()
            }
            if (activeStrip.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeStrip.forEach { (label, ratioVal) ->
                        val isSelected = crop.aspectRatio.equals(ratioVal, ignoreCase = true)
                        Surface(
                            color = if (isSelected) SpectrumBlue else Color(0xFF262628),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onAspectRatioSelected(ratioVal)
                            }
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════
        // GEOMETRY TAB CONTENT (Screenshots 083314, 200437, 200504, 200743)
        // ═════════════════════════════════════════════════════════════════
        if (page == "Geometry") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 5 Upright Mode Cards: Auto, Level, Vertical, Full, Guided
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    uprightModes.forEach { (title, iconRes, modeKey) ->
                        val isSelected = uprightMode.equals(modeKey, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onUprightModeSelected(modeKey)
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF2C2C2E) else Color(0xFF1E1E1E),
                                border = if (isSelected) BorderStroke(1.5.dp, SpectrumBlue) else BorderStroke(0.5.dp, Color(0xFF333333)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = title,
                                        tint = if (isSelected) Color.White else Color(0xFF999999),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color(0xFF8E8E93),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Guided Upright (+) and Clear controls (Matching Screenshot 200743)
                if (uprightMode.equals("Guided", ignoreCase = true)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onAddGuidedLine()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(24.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2A2A2D),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onClearGuidedLines()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lr_reset_all),
                                    contentDescription = "Clear guided lines",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Constrain Crop Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Constrain Crop",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = constrainCrop,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConstrainCropToggled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1686FF),
                            uncheckedThumbColor = Color(0xFFAAAAAA),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }

                // Manual Transforms Accordion Header + Reset Geometry pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualTransforms = !showManualTransforms }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Manual Transforms",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val hasGeometryEdits = distortion != 0f || vertical != 0f || horizontal != 0f ||
                            rotate != 0f || aspect != 0f || scale != 100f || xOffset != 0f || yOffset != 0f
                        if (hasGeometryEdits) {
                            Text(
                                text = "Reset",
                                color = Color(0xFF38A9FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onResetGeometry()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_chevron_down),
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp).rotate(if (showManualTransforms) 180f else 0f)
                    )
                }

                if (showManualTransforms) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RampedSlider(
                            label = "Distortion",
                            value = distortion,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onDistortionChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Distortion") }
                        )
                        RampedSlider(
                            label = "Vertical",
                            value = vertical,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onVerticalChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Vertical") }
                        )
                        RampedSlider(
                            label = "Horizontal",
                            value = horizontal,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onHorizontalChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Horizontal") }
                        )
                        RampedSlider(
                            label = "Rotate",
                            value = rotate,
                            minVal = -45f,
                            maxVal = 45f,
                            defaultValue = 0f,
                            isBipolar = true,
                            formatPattern = "%.1f",
                            unitSuffix = "°",
                            onValueChangeStart = onTouchStart,
                            onValueChange = onRotateChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Rotate") }
                        )
                        RampedSlider(
                            label = "Aspect",
                            value = aspect,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onAspectChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Aspect") }
                        )
                        RampedSlider(
                            label = "Scale",
                            value = scale,
                            minVal = 50f,
                            maxVal = 200f,
                            defaultValue = 100f,
                            isBipolar = false,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onScaleChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Scale") }
                        )
                        RampedSlider(
                            label = "X Offset",
                            value = xOffset,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onXOffsetChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry X Offset") }
                        )
                        RampedSlider(
                            label = "Y Offset",
                            value = yOffset,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            isBipolar = true,
                            onValueChangeStart = onTouchStart,
                            onValueChange = onYOffsetChanged,
                            onValueChangeEnd = { onTouchEnd("Geometry Y Offset") }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Pixel-Accurate Adobe Lightroom Mobile Remove Panel (Reference Screenshots 083752, 201127, 201133).
 *
 * Includes all 3 authentic tools via the blue mode-switcher button + flyout menu:
 *  - Remove (Magic Eraser): Size slider, Detect objects toggle, Generative AI toggle + (i) info, Refine button
 *  - Heal (Band-Aid): Size, Feather, Opacity sliders + draggable source pin
 *  - Clone (Rubber Stamp): Size, Feather, Opacity sliders + draggable source pin
 */
@Composable
private fun RemoveToolPanel(
    activeMode: com.cleanlr.engine.RemoveToolMode = com.cleanlr.engine.RemoveToolMode.REMOVE,
    onModeSelected: (com.cleanlr.engine.RemoveToolMode) -> Unit = {},
    brushSize: Float = 10f,
    onBrushSizeChange: (Float) -> Unit = {},
    feather: Float = 50f,
    onFeatherChange: (Float) -> Unit = {},
    opacity: Float = 100f,
    onOpacityChange: (Float) -> Unit = {},
    detectObjects: Boolean = false,
    onDetectObjectsChange: (Boolean) -> Unit = {},
    useGenerativeAi: Boolean = true,
    onUseGenerativeAiChange: (Boolean) -> Unit = {},
    isRefineMode: Boolean = false,
    onToggleRefineMode: () -> Unit = {},
    hasBrushStrokes: Boolean = false,
    isProcessing: Boolean = false,
    onApplyRemove: () -> Unit = {},
    onClearStrokes: () -> Unit = {},
    onOpenAiInfo: () -> Unit = {}
) {
    val view = LocalView.current
    var showModeFlyout by remember { mutableStateOf(false) }

    val activeIconRes = when (activeMode) {
        com.cleanlr.engine.RemoveToolMode.REMOVE ->
            if (useGenerativeAi) R.drawable.ic_lr_magic_eraser else R.drawable.ic_lr_remove_plain
        com.cleanlr.engine.RemoveToolMode.HEAL -> R.drawable.ic_lr_heal
        com.cleanlr.engine.RemoveToolMode.CLONE -> R.drawable.ic_lr_clone
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181A))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── ROW 1: Blue Mode Switcher Square (with Flyout Menu: Remove / Heal / Clone) + Size Slider ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(46.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showModeFlyout = true
                        }
                ) {
                    // Authentic #1473E6 blue rounded square with tiny bottom-right flyout triangle notch
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1473E6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(activeIconRes),
                            contentDescription = activeMode.label,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        // Bottom-right white triangle notch (exact match to Screenshot 083752 & 201127)
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(6.dp)
                        ) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = activeMode.label,
                        color = Color(0xFFE6E6E8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }

                // Authentic Lightroom Remove / Heal / Clone Flyout Menu (Reference Screenshot 201133)
                DropdownMenu(
                    expanded = showModeFlyout,
                    onDismissRequest = { showModeFlyout = false },
                    modifier = Modifier
                        .width(156.dp)
                        .background(Color(0xFF242426), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color(0xFF3E3E42), RoundedCornerShape(8.dp))
                ) {
                    val modeItems = listOf(
                        Triple(
                            com.cleanlr.engine.RemoveToolMode.REMOVE,
                            if (useGenerativeAi) R.drawable.ic_lr_magic_eraser else R.drawable.ic_lr_remove_plain,
                            "Remove"
                        ),
                        Triple(com.cleanlr.engine.RemoveToolMode.HEAL, R.drawable.ic_lr_heal, "Heal"),
                        Triple(com.cleanlr.engine.RemoveToolMode.CLONE, R.drawable.ic_lr_clone, "Clone")
                    )
                    modeItems.forEach { (mode, iconRes, label) ->
                        val isSelected = (mode == activeMode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color(0xFF1473E6) else Color.Transparent)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onModeSelected(mode)
                                    showModeFlyout = false
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Color(0xFFC4C4C8),
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFFDCDCE0),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Size Slider (Matches Screenshot 083752 hollow circle thumb & clean label/value header)
            Box(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                RampedSlider(
                    label = "Size",
                    value = brushSize,
                    minVal = 4f,
                    maxVal = 100f,
                    defaultValue = if (activeMode == com.cleanlr.engine.RemoveToolMode.REMOVE) 10f else 25f,
                    isBipolar = false,
                    formatPattern = "%.0f",
                    onValueChange = onBrushSizeChange
                )
            }
        }

        // ─── MODE-SPECIFIC ROWS 2 & 3 ───
        if (activeMode == com.cleanlr.engine.RemoveToolMode.REMOVE) {
            // Row 2: Detect objects pill switch (+ inline Clear / Remove buttons when strokes are painted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDetectObjectsChange(!detectObjects)
                    }
                ) {
                    LightroomCompactToggle(
                        checked = detectObjects,
                        onCheckedChange = onDetectObjectsChange
                    )
                    Text(
                        text = "Detect objects",
                        color = Color(0xFFEBEBED),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                if (hasBrushStrokes) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = Color(0xFF28282B),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF3E3E42)),
                            modifier = Modifier.clickable(enabled = !isProcessing) { onClearStrokes() }
                        ) {
                            Text(
                                text = "Clear",
                                color = Color(0xFFD0D0D4),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            color = Color(0xFF1473E6),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.clickable(enabled = !isProcessing) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onApplyRemove()
                            }
                        ) {
                            Text(
                                text = "Remove",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Row 3: Generative AI toggle + (i) info button + right-aligned Refine button (Screenshot 083752)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LightroomCompactToggle(
                        checked = useGenerativeAi,
                        onCheckedChange = onUseGenerativeAiChange
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Generative AI",
                            color = Color(0xFFEBEBED),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.clickable { onUseGenerativeAiChange(!useGenerativeAi) }
                        )
                        // Authentic circular (i) icon — opens Offline AI Diagnostics & Optional LAN Bridge
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.2.dp, Color(0xFFC8C8CC), CircleShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onOpenAiInfo()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "i",
                                color = Color(0xFFD8D8DC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Authentic right-aligned outlined "Refine" button (Screenshot 083752 & 201127)
                val refineEnabled = hasBrushStrokes && !isProcessing
                Surface(
                    color = if (isRefineMode && refineEnabled) Color(0xFF1E3A5F) else Color(0xFF1F1F22),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isRefineMode && refineEnabled -> Color(0xFF38A9FF)
                            refineEnabled -> Color(0xFF56565C)
                            else -> Color(0xFF2D2D31)
                        }
                    ),
                    modifier = Modifier.clickable(enabled = refineEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleRefineMode()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_mask_brush),
                            contentDescription = "Refine",
                            tint = when {
                                isRefineMode && refineEnabled -> Color(0xFF38A9FF)
                                refineEnabled -> Color(0xFFE6E6E8)
                                else -> Color(0xFF5C5C62)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isRefineMode && refineEnabled) "Erasing" else "Refine",
                            color = when {
                                isRefineMode && refineEnabled -> Color(0xFF38A9FF)
                                refineEnabled -> Color(0xFFE6E6E8)
                                else -> Color(0xFF5C5C62)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // ─── HEAL & CLONE AUTHENTIC CONTROLS: Feather + Opacity sliders + Source / Apply bar ───
            RampedSlider(
                label = "Feather",
                value = feather,
                minVal = 0f,
                maxVal = 100f,
                defaultValue = if (activeMode == com.cleanlr.engine.RemoveToolMode.HEAL) 50f else 40f,
                isBipolar = false,
                formatPattern = "%.0f",
                onValueChange = onFeatherChange
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    RampedSlider(
                        label = "Opacity",
                        value = opacity,
                        minVal = 1f,
                        maxVal = 100f,
                        defaultValue = 100f,
                        isBipolar = false,
                        formatPattern = "%.0f",
                        onValueChange = onOpacityChange
                    )
                }

                if (hasBrushStrokes) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = Color(0xFF28282B),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF3E3E42)),
                            modifier = Modifier.clickable(enabled = !isProcessing) { onClearStrokes() }
                        ) {
                            Text(
                                text = "Clear",
                                color = Color(0xFFD0D0D4),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            color = Color(0xFF1473E6),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.clickable(enabled = !isProcessing) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onApplyRemove()
                            }
                        ) {
                            Text(
                                text = "Apply ${activeMode.label}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Authentic Adobe Lightroom Mobile Compact Pill Toggle Switch (Reference Screenshot 083752).
 */
@Composable
private fun LightroomCompactToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    val trackColor = if (checked) Color(0xFF1473E6) else Color(0xFFB5B5BA)
    val thumbBg = if (checked) Color.White else Color(0xFF3E3E42)
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(21.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(trackColor)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(thumbBg)
        )
    }
}

/**
 * SOTA FLUX.1 Fill Cloud / Self-Hosted GPU Server Configuration Dialog (Zero APK Bloat)
 */
@Composable
private fun FluxFillServerConfigDialog(
    currentConfig: com.cleanlr.engine.GenerativeAiConfig,
    onDismiss: () -> Unit,
    onSave: (com.cleanlr.engine.GenerativeAiConfig) -> Unit
) {
    var selectedBackend by remember(currentConfig) { mutableStateOf(currentConfig.backend) }
    var apiKey by remember(currentConfig) { mutableStateOf(currentConfig.apiKey) }
    var endpointUrl by remember(currentConfig) { mutableStateOf(currentConfig.endpointUrl) }
    var modelId by remember(currentConfig) { mutableStateOf(currentConfig.modelId) }
    var usePoisson by remember(currentConfig) { mutableStateOf(currentConfig.usePoissonBlending) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1E),
        title = {
            Column {
                Text(
                    text = "FLUX.1 Fill • AI Server Setup",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero APK Bloat: sends only Crop+Mask ROI → Poisson Blend on-device",
                    color = Color(0xFF9A9AA2),
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select GPU Provider", color = Color(0xFFB8B8C0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.cleanlr.engine.GenerativeAiBackend.entries.forEach { backend ->
                        val isSelected = selectedBackend == backend
                        Surface(
                            color = if (isSelected) Color(0xFF1F3654) else Color(0xFF252529),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) SpectrumBlue else Color(0xFF38383E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBackend = backend
                                    endpointUrl = backend.defaultEndpoint
                                    modelId = backend.defaultModel
                                }
                        ) {
                            Text(
                                text = backend.displayName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("Endpoint URL", color = Color(0xFF9A9AA2), fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpectrumBlue,
                        unfocusedBorderColor = Color(0xFF44444A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model (FLUX.1-Fill-dev / BrushNet)", color = Color(0xFF9A9AA2), fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpectrumBlue,
                        unfocusedBorderColor = Color(0xFF44444A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (selectedBackend == com.cleanlr.engine.GenerativeAiBackend.CUSTOM_GPU_SERVER) "Bearer Token (Optional)" else "API Key",
                            color = Color(0xFF9A9AA2),
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpectrumBlue,
                        unfocusedBorderColor = Color(0xFF44444A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seamless Poisson Blending", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Preserves 100% original pixels outside mask", color = Color(0xFF9A9AA2), fontSize = 10.sp)
                    }
                    Switch(
                        checked = usePoisson,
                        onCheckedChange = { usePoisson = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentConfig.copy(
                            backend = selectedBackend,
                            apiKey = apiKey,
                            endpointUrl = endpointUrl,
                            modelId = modelId,
                            usePoissonBlending = usePoisson
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpectrumBlue)
            ) {
                Text("Save & Connect", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9A9AA2))
            }
        }
    )
}

/**
 * 5-Stage Optical Lens Blur Panel (Clean-Plate + 16-bit Depth CoC + Linear HDR Bokeh +
 * 7 Aperture Blade Shapes + Partial Occlusion + Sensor Grain Matching)
 */
@Composable
private fun LensBlurPanel(
    blurAmount: Float,
    onBlurAmountChange: (Float) -> Unit,
    bokehBoost: Float = 50f,
    onBokehBoostChange: (Float) -> Unit = {},
    focalDepth: Float = 0.42f,
    onFocalDepthChange: (Float) -> Unit = {},
    selectedBokehShape: String,
    onBokehShapeSelected: (String) -> Unit,
    hasOpticalPack: Boolean = false,
    opticalStatusText: String? = null,
    onBuildOpticalCleanPlate: () -> Unit = {},
    onOpenByomModels: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(spectrum_surface_dark)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 5-Stage Optical Clean-Plate & 16-bit Depth Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (hasOpticalPack) Color(0xFF1F3B2C) else Color(0xFF1E2D45),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (hasOpticalPack) Color(0xFF4CD964) else SpectrumBlue),
                modifier = Modifier.clickable { onBuildOpticalCleanPlate() }
            ) {
                Text(
                    text = if (hasOpticalPack) "5-Stage Clean-Plate Ready ✓" else "Build Clean-Plate & 16-bit Depth",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Surface(
                color = Color(0xFF28282C),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable { onOpenByomModels() }
            ) {
                Text(
                    text = "AI Engine ✓",
                    color = Color(0xFF4CD964),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        if (!opticalStatusText.isNullOrBlank()) {
            Text(
                text = opticalStatusText,
                color = Color(0xFF9A9AA2),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Blur amount slider
        RampedSlider(
            label = "Blur amount (Aperture K)",
            value = blurAmount,
            minVal = 0f,
            maxVal = 100f,
            defaultValue = 0f,
            isBipolar = false,
            formatPattern = "%.0f",
            onValueChange = onBlurAmountChange
        )

        // HDR Specular Bokeh Boost + Focal Plane Depth S
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                RampedSlider(
                    label = "HDR Bokeh Boost",
                    value = bokehBoost,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 50f,
                    isBipolar = false,
                    formatPattern = "%.0f",
                    onValueChange = onBokehBoostChange
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                RampedSlider(
                    label = "Focal Plane (S)",
                    value = focalDepth * 100f,
                    minVal = 10f,
                    maxVal = 95f,
                    defaultValue = 42f,
                    isBipolar = false,
                    formatPattern = "%.0f",
                    onValueChange = { onFocalDepthChange(it / 100f) }
                )
            }
        }

        // All 7 Aperture Blade Shapes (Circle, Bubble, 5/7/9-blade, Anamorphic Oval, Cat eye)
        val bokehShapes = listOf(
            "Circle" to R.drawable.ic_lr_lens_blur,
            "Bubble" to R.drawable.ic_lr_lens_blur,
            "5-blade" to R.drawable.ic_lr_lens_blur,
            "7-blade" to R.drawable.ic_lr_lens_blur,
            "9-blade" to R.drawable.ic_lr_lens_blur,
            "Oval" to R.drawable.ic_lr_lens_blur,
            "Cat eye" to R.drawable.ic_lr_lens_blur
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bokehShapes.forEach { (name, iconRes) ->
                val isSelected = selectedBokehShape.equals(name, ignoreCase = true)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onBokehShapeSelected(name) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF2E2E2E) else Color(0xFF1E1E1E))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF1473E6) else Color(0xFF333333),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = name,
                            tint = if (isSelected) Color(0xFF1473E6) else Color(0xFF888888),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = name,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontSize = 10.sp
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
 * Profiles Panel matching Reference Screenshot 083723
 */
@Composable
private fun ProfilesPanel(
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    selectedProfile: String,
    onProfileSelected: (String) -> Unit
) {
    var showGroupMenu by remember { mutableStateOf(false) }
    val profiles = remember(selectedGroup) {
        ProfileManager.getProfilesByGroup(selectedGroup)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(spectrum_surface_dark)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Header Row: "<selectedGroup> ⌄" + Three-dots overflow
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    painter = painterResource(R.drawable.ic_lr_chevron_down),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_more_vert),
                    contentDescription = "Options",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showGroupMenu,
            onDismissRequest = { showGroupMenu = false }
        ) {
            ProfileManager.GROUPS.forEach { group ->
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

        // Profiles Thumbnails Horizontal Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            profiles.forEachIndexed { index, profileItem ->
                val isSelected = selectedProfile == profileItem.name
                val isMonochrome = profileItem.isMonochrome

                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, if (isSelected) Color(0xFF2680EB) else Color(0xFF242424)),
                    modifier = Modifier
                        .width(108.dp)
                        .height(96.dp)
                        .clickable { onProfileSelected(profileItem.name) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Landscape mountain / sunset gradient thumbnail simulating the photo
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isMonochrome) {
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF222222),
                                                Color(0xFF444444),
                                                Color(0xFF666666),
                                                Color(0xFF222222)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF1B2338),
                                                Color(0xFF753852),
                                                Color(0xFFD26E45),
                                                Color(0xFF241C24)
                                            )
                                        )
                                    }
                                )
                        )

                        // Star icon on top right for favorite (e.g. on Monochrome in Screenshot 083723)
                        if (isMonochrome) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_star_filled),
                                contentDescription = "Favorite",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(12.dp)
                            )
                        }

                        // Profile name label at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color(0xB3111111))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = profileItem.name,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Vertical separator after first item if monochrome
                if (index == 0 && isMonochrome) {
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .width(1.dp)
                            .background(Color(0xFF383838))
                    )
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
                        Icon(painter = painterResource(R.drawable.ic_lr_rotate_left), contentDescription = "Rotate 90° CCW", tint = SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onRotate(true)
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_lr_rotate_right), contentDescription = "Rotate 90° CW", tint = SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onFlip(true)
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_lr_flip_horizontal), contentDescription = "Flip Horizontal", tint = if (crop.flipHorizontal) SpectrumBlue else SpectrumIconNormal)
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onFlip(false)
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_lr_flip_vertical), contentDescription = "Flip Vertical", tint = if (crop.flipVertical) SpectrumBlue else SpectrumIconNormal)
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
                                color = if (crop.straightenAngle != 0f) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFeatureSettings = "tnum"),
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
                    Icon(painter = painterResource(R.drawable.ic_lr_reset_all), contentDescription = null, tint = SpectrumIconNormal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Crop & Geometry", color = SpectrumTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
