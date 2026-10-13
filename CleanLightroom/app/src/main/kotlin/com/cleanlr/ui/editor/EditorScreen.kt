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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.ui.components.*
import com.cleanlr.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val recipe by viewModel.recipe.collectAsState()
    val bitmap by viewModel.bitmap.collectAsState()
    val histogramData by viewModel.histogramData.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val altClippingMode by viewModel.altClippingMode.collectAsState()
    val isComparingOriginal by viewModel.isComparingOriginal.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SpectrumCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lightroom Clean",
                        color = SpectrumTextPrimary,
                        fontSize = 16.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpectrumSurface
                ),
                actions = {
                    // Undo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) SpectrumTextPrimary else SpectrumTextSecondary.copy(alpha = 0.4f)
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
                            tint = if (canRedo) SpectrumTextPrimary else SpectrumTextSecondary.copy(alpha = 0.4f)
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

                    // Pick image
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Open Photo",
                            tint = SpectrumTextPrimary
                        )
                    }

                    // Export
                    IconButton(onClick = { viewModel.exportImage() }) {
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
            // Loupe Canvas (Preview Area)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
            ) {
                LoupeCanvas(
                    bitmap = bitmap,
                    recipe = recipe,
                    altClippingMode = altClippingMode,
                    isComparingOriginal = isComparingOriginal,
                    onHistogramUpdated = { viewModel.updateHistogram(it) },
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

                // Export Toast
                exportStatus?.let { status ->
                    Surface(
                        color = SpectrumElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clickable { viewModel.clearExportStatus() }
                    ) {
                        Text(
                            text = status,
                            color = SpectrumTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

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
                isShadowClippingActive = altClippingMode == 2,
                isHighlightClippingActive = altClippingMode == 1
            )

            // Category Tool Tabs Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpectrumSurface)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items(EditorToolTab.values()) { tab ->
                    val isSelected = tab == activeTab
                    Box(
                        modifier = Modifier
                            .clickable { viewModel.setActiveTab(tab) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) SpectrumBlue else SpectrumTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = SpectrumBorder, thickness = 1.dp)

            // Active Panel Controls (Scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(SpectrumSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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
                            RampedSlider(
                                label = "Temperature",
                                value = recipe.basic.temperature,
                                minVal = 2000f,
                                maxVal = 10000f,
                                defaultValue = 5500f,
                                isBipolar = false,
                                formatPattern = "%.0f K",
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(temperature = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("White Balance Temp") }
                            )
                            RampedSlider(
                                label = "Tint",
                                value = recipe.basic.tint,
                                minVal = -150f,
                                maxVal = 150f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(tint = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Tint") }
                            )
                            RampedSlider(
                                label = "Vibrance",
                                value = recipe.presence.vibrance,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(vibrance = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vibrance") }
                            )
                            RampedSlider(
                                label = "Saturation",
                                value = recipe.presence.saturation,
                                minVal = -100f,
                                maxVal = 100f,
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(saturation = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Saturation") }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "COLOR MIXER (8-CHANNEL)",
                                color = SpectrumTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HslMixerPanel(
                                hsl = recipe.hsl,
                                onHslChanged = { updated ->
                                    viewModel.updateRecipe("Color Mixer") { it.copy(hsl = updated) }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "COLOR GRADING (3-WAY WHEELS)",
                                color = SpectrumTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            ColorGradingWheelWidget(
                                colorGrading = recipe.colorGrading,
                                onColorGradingChanged = { updated ->
                                    viewModel.updateRecipe("Color Grading") { it.copy(colorGrading = updated) }
                                }
                            )
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
                                onValueChangeStart = { viewModel.onSliderTouchStart() },
                                onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(masking = it)) } },
                                onValueChangeEnd = { viewModel.onSliderTouchEnd("Detail Masking") }
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
                    }
                }
            }
        }
    }
}
