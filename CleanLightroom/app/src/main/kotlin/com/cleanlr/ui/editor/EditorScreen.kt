package com.cleanlr.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Brush
import com.cleanlr.ui.components.*
import com.cleanlr.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
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
    val panelSnapState by viewModel.panelSnapState.collectAsState()
    val isSliderDragging by viewModel.isSliderDragging.collectAsState()
    val bypassedTabs by viewModel.bypassedTabs.collectAsState()
    val isColorWbExpanded by viewModel.isColorWbExpanded.collectAsState()
    val isColorVibranceExpanded by viewModel.isColorVibranceExpanded.collectAsState()
    val isColorMixerExpanded by viewModel.isColorMixerExpanded.collectAsState()
    val isColorGradingExpanded by viewModel.isColorGradingExpanded.collectAsState()

    var showExportConfigSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

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
                    title = {
                        Text(
                            text = "Lightroom Clean",
                            color = SpectrumTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SpectrumSurface
                    ),
                    actions = {
                        // Tip 1: Canvas Background Switcher (Pure Black, Dark Gray, Middle Gray, White)
                        IconButton(onClick = { viewModel.cycleCanvasBackground() }) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = "Canvas Background (${canvasBackground.label})",
                                tint = SpectrumIconNormal
                            )
                        }

                        // Undo
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) SpectrumIconNormal else SpectrumIconDisabled
                            )
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

                    // Press & hold Before/After compare
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
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
                            contentDescription = "Compare Before/After",
                            tint = if (isComparingOriginal) SpectrumBlue else SpectrumTextPrimary
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

                    // Pick image
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Open Photo",
                            tint = SpectrumTextPrimary
                        )
                    }

                    // Export
                    IconButton(onClick = { showExportConfigSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Image",
                            tint = SpectrumBlue
                        )
                    }
                }
            )
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
                    activeTool = activeTool,
                    brushSize = brushSize,
                    brushFeather = brushFeather,
                    onSingleTap = { viewModel.toggleCleanScreenMode() },
                    onTwoFingerLongPress = { viewModel.toggleCanvasMatting() },
                    modifier = Modifier.fillMaxSize()
                )

                // Before banner overlay
                if (isComparingOriginal) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "BEFORE (ORIGINAL)",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

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
                // Interactive Live Histogram with 5 drag zones
                InteractiveHistogram(
                    histogramData = histogramData,
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
                    onToggleShadowClipping = {
                        viewModel.setAltClippingMode(if (altClippingMode == 2) 0 else 2)
                    },
                    onToggleHighlightClipping = {
                        viewModel.setAltClippingMode(if (altClippingMode == 1) 0 else 1)
                    },
                    onJumpToBlacks = {
                        viewModel.setActiveTab(EditorToolTab.LIGHT)
                        coroutineScope.launch {
                            toolScrollState.animateScrollTo(toolScrollState.maxValue)
                        }
                    },
                    onJumpToWhites = {
                        viewModel.setActiveTab(EditorToolTab.LIGHT)
                        coroutineScope.launch {
                            toolScrollState.animateScrollTo((toolScrollState.maxValue * 0.75f).toInt())
                        }
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
                    when (activeTab) {
                        EditorToolTab.LIGHT -> {
                            RampedSlider(
                                label = "Exposure",
                                value = recipe.basic.exposure,
                                minVal = -5.0f,
                                maxVal = 5.0f,
                                formatPattern = "%.2f EV",
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(exposure = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Exposure") }
                            )
                            RampedSlider(
                                label = "Contrast",
                                value = recipe.basic.contrast,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(contrast = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Contrast") }
                            )
                            RampedSlider(
                                label = "Highlights",
                                value = recipe.basic.highlights,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(highlights = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Highlights") }
                            )
                            RampedSlider(
                                label = "Shadows",
                                value = recipe.basic.shadows,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(shadows = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Shadows") }
                            )
                            RampedSlider(
                                label = "Whites",
                                value = recipe.basic.whites,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = {
                                    viewModel.onSliderTouchStart()
                                    viewModel.setAltClippingMode(1) // Alt clipping preview while dragging whites
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
                                onValueChangeStart = {
                                    viewModel.onSliderTouchStart()
                                    viewModel.setAltClippingMode(2) // Alt clipping preview while dragging blacks
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
                                Brush.horizontalGradient(listOf(Color(0xFF4A90E2), Color(0xFFFFA726)))
                            }
                            val tintTrackBrush = remember {
                                Brush.horizontalGradient(listOf(Color(0xFF66BB6A), Color(0xFFEC407A)))
                            }

                            // Tip 33 & 34: Accordion 1 — WHITE BALANCE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleColorSection("WB") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isColorWbExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand WB",
                                        tint = SpectrumTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "WHITE BALANCE",
                                        color = SpectrumGroupHeader,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Bypass WB",
                                        tint = SpectrumIconNormal,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset WB",
                                        tint = SpectrumIconNormal,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(temp = 0f, tint = 0f)) }
                                            }
                                    )
                                }
                            }

                            if (isColorWbExpanded) {
                                RampedSlider(
                                    label = "Temperature",
                                    value = recipe.basic.temperature,
                                    minVal = 2000f,
                                    maxVal = 10000f,
                                    defaultValue = 5500f,
                                    isBipolar = false,
                                    formatPattern = "%.0f K",
                                    trackBrush = tempTrackBrush,
                                    onValueChangeStart = {
                                        viewModel.onSliderTouchStart()
                                        viewModel.setSliderDragging(true)
                                    },
                                    onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(temperature = it)) } },
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
                                    onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(tint = it)) } },
                                    onValueChangeEnd = {
                                        viewModel.setSliderDragging(false)
                                        viewModel.onSliderTouchEnd("Tint")
                                    }
                                )
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                            // Tip 33 & 34: Accordion 2 — VIBRANCE & SATURATION
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleColorSection("VIBRANCE") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isColorVibranceExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Vibrance",
                                        tint = SpectrumTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "VIBRANCE & SATURATION",
                                        color = SpectrumGroupHeader,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Bypass Vibrance",
                                        tint = SpectrumIconNormal,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset Vibrance",
                                        tint = SpectrumIconNormal,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(vibrance = 0f, saturation = 0f)) }
                                            }
                                    )
                                }
                            }

                            if (isColorVibranceExpanded) {
                                RampedSlider(
                                    label = "Vibrance",
                                    value = recipe.presence.vibrance,
                                    minVal = -100f,
                                    maxVal = 100f,
                                    onValueChangeStart = {
                                        viewModel.onSliderTouchStart()
                                        viewModel.setSliderDragging(true)
                                    },
                                    onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(vibrance = it)) } },
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
                                    onValueChangeStart = {
                                        viewModel.onSliderTouchStart()
                                        viewModel.setSliderDragging(true)
                                    },
                                    onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(saturation = it)) } },
                                    onValueChangeEnd = {
                                        viewModel.setSliderDragging(false)
                                        viewModel.onSliderTouchEnd("Saturation")
                                    }
                                )
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                            // Tip 33 & 34: Accordion 3 — COLOR MIXER (8-CHANNEL)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleColorSection("MIXER") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isColorMixerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Mixer",
                                        tint = SpectrumTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "COLOR MIXER (8-CHANNEL)",
                                        color = SpectrumGroupHeader,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // TAT toggle chip
                                    Surface(
                                        color = if (isTatActive) SpectrumBlue else SpectrumElevated,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .clickable { viewModel.toggleTat() }
                                            .padding(end = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Adjust,
                                                contentDescription = "TAT",
                                                tint = if (isTatActive) Color.White else SpectrumTextSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "TAT",
                                                color = if (isTatActive) Color.White else SpectrumTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Mode selector pills
                                    if (isTatActive) {
                                        com.cleanlr.engine.TatTargetMode.values().forEach { mode ->
                                            val isSelected = tatMode == mode
                                            Surface(
                                                color = if (isSelected) SpectrumBlue else Color.Transparent,
                                                shape = RoundedCornerShape(3.dp),
                                                modifier = Modifier
                                                    .clickable { viewModel.setTatMode(mode) }
                                                    .padding(horizontal = 2.dp)
                                            ) {
                                                Text(
                                                    text = mode.label.take(3),
                                                    color = if (isSelected) Color.White else SpectrumTextSecondary,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset Mixer",
                                        tint = SpectrumIconNormal,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.updateRecipe { it.copy(hsl = com.cleanlr.model.HslAdjustments()) }
                                            }
                                    )
                                }
                            }

                            if (isColorMixerExpanded) {
                                HslMixerPanel(
                                    hsl = recipe.hsl,
                                    onHslChanged = { updated ->
                                        viewModel.updateRecipe("Color Mixer") { it.copy(hsl = updated) }
                                    }
                                )
                            }

                            HorizontalDivider(color = SpectrumGroupDivider, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                            // Tip 33 & 34: Accordion 4 — COLOR GRADING (3-WAY WHEELS)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleColorSection("GRADING") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isColorGradingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Grading",
                                        tint = SpectrumTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "COLOR GRADING (3-WAY WHEELS)",
                                        color = SpectrumGroupHeader,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Grading",
                                    tint = SpectrumIconNormal,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.updateRecipe { it.copy(colorGrading = com.cleanlr.model.ColorGrading()) }
                                        }
                                )
                            }

                            if (isColorGradingExpanded) {
                                ColorGradingWheelWidget(
                                    colorGrading = recipe.colorGrading,
                                    onColorGradingChanged = { updated ->
                                        viewModel.updateRecipe("Color Grading") { it.copy(colorGrading = updated) }
                                    }
                                )
                            }
                        }

                        EditorToolTab.EFFECTS -> {
                            RampedSlider(
                                label = "Clarity",
                                value = recipe.presence.clarity,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(clarity = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Clarity") }
                            )
                            RampedSlider(
                                label = "Texture",
                                value = recipe.presence.texture,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(texture = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Texture") }
                            )
                            RampedSlider(
                                label = "Dehaze",
                                value = recipe.presence.dehaze,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(dehaze = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Dehaze") }
                            )
                        }

                        EditorToolTab.DETAIL -> {
                            RampedSlider(
                                label = "Sharpening",
                                value = recipe.detail.sharpening,
                                minVal = 0f,
                                maxVal = 150f,
                                defaultValue = 40f,
                                isBipolar = false,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpening = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpening") }
                            )
                            RampedSlider(
                                label = "Masking",
                                value = recipe.detail.masking,
                                minVal = 0f,
                                maxVal = 100f,
                                defaultValue = 0f,
                                isBipolar = false,
                                onValueChangeStart = {
                                    viewModel.onSliderTouchStart()
                                    viewModel.setAltClippingMode(3) // Section 4.1 Alt + Sharpening Masking
                                },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(masking = it)) } },
                                onValueChangeEnd = {
                                    viewModel.setAltClippingMode(0)
                                    viewModel.onSliderTouchEnd("Detail Masking")
                                }
                            )
                            RampedSlider(
                                label = "Noise Reduction",
                                value = recipe.detail.noiseReduction,
                                minVal = 0f,
                                maxVal = 100f,
                                defaultValue = 0f,
                                isBipolar = false,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(noiseReduction = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Noise Reduction") }
                            )
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
                                presets = com.cleanlr.preset.PresetManager.STARTER_PACK,
                                activePreset = activePreset,
                                presetAmount = presetAmount,
                                onSelectPreset = { viewModel.applyPreset(it) },
                                onAmountChange = { viewModel.adjustPresetAmount(it) },
                                onAmountChangeEnd = { viewModel.commitPresetAmount() }
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
                        text = "Reset All Adjustments?",
                        color = SpectrumTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will revert all light, color, effects, and detail settings back to original.",
                        color = SpectrumTextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetAll()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE02B2B))
                    ) {
                        Text("Reset", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
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
