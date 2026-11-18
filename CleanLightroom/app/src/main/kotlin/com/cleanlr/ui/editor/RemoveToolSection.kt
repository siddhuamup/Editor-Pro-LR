package com.cleanlr.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.engine.RemoveToolMode
import com.cleanlr.model.EditRecipe
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.SpectrumTextSecondary

/**
 * Self-contained Remove / Heal / Clone section wrapper to keep `EditorScreen` bytecode lean.
 */
@Composable
fun EditorRemoveSection(viewModel: EditorViewModel) {
    val activeMode by viewModel.removeToolMode.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()
    val feather by viewModel.healFeather.collectAsState()
    val opacity by viewModel.healOpacity.collectAsState()
    val detectObjects by viewModel.detectObjectsRemove.collectAsState()
    val useGenerativeAi by viewModel.useGenerativeAiRemove.collectAsState()
    val isRefineMode by viewModel.isRefineEraseMode.collectAsState()
    val brushPoints by viewModel.removeBrushPoints.collectAsState()
    val isProcessing by viewModel.isGenAiProcessing.collectAsState()

    RemoveToolPanel(
        activeMode = activeMode,
        onModeSelected = { viewModel.setRemoveToolMode(it) },
        brushSize = brushSize,
        onBrushSizeChange = { viewModel.setBrushSize(it) },
        feather = feather,
        onFeatherChange = { viewModel.setHealFeather(it) },
        opacity = opacity,
        onOpacityChange = { viewModel.setHealOpacity(it) },
        detectObjects = detectObjects,
        onDetectObjectsChange = { viewModel.setDetectObjectsRemove(it) },
        useGenerativeAi = useGenerativeAi,
        onUseGenerativeAiChange = { viewModel.setUseGenerativeAiRemove(it) },
        isRefineMode = isRefineMode,
        onToggleRefineMode = { viewModel.toggleRefineEraseMode() },
        hasBrushStrokes = brushPoints.isNotEmpty(),
        isProcessing = isProcessing,
        onApplyRemove = { viewModel.executeRemoveStrokes() },
        onClearStrokes = { viewModel.clearRemoveBrushPoints() },
        onOpenAiInfo = { viewModel.openModelManagerSheet() }
    )
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
fun RemoveToolPanel(
    activeMode: RemoveToolMode = RemoveToolMode.REMOVE,
    onModeSelected: (RemoveToolMode) -> Unit = {},
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
        RemoveToolMode.REMOVE ->
            if (useGenerativeAi) R.drawable.ic_lr_magic_eraser else R.drawable.ic_lr_remove_plain
        RemoveToolMode.HEAL -> R.drawable.ic_lr_heal
        RemoveToolMode.CLONE -> R.drawable.ic_lr_clone
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181A))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── ROW 1: Blue Mode Switcher Square (Flyout: Remove / Heal / Clone) + Size Slider ───
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
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(6.dp)
                        ) {
                            val path = Path().apply {
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
                            RemoveToolMode.REMOVE,
                            if (useGenerativeAi) R.drawable.ic_lr_magic_eraser else R.drawable.ic_lr_remove_plain,
                            "Remove"
                        ),
                        Triple(RemoveToolMode.HEAL, R.drawable.ic_lr_heal, "Heal"),
                        Triple(RemoveToolMode.CLONE, R.drawable.ic_lr_clone, "Clone")
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

            Box(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                RampedSlider(
                    label = "Size",
                    value = brushSize,
                    minVal = 4f,
                    maxVal = 100f,
                    defaultValue = if (activeMode == RemoveToolMode.REMOVE) 10f else 25f,
                    isBipolar = false,
                    formatPattern = "%.0f",
                    onValueChange = onBrushSizeChange
                )
            }
        }

        // ─── MODE-SPECIFIC ROWS 2 & 3 ───
        if (activeMode == RemoveToolMode.REMOVE) {
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
            // ─── HEAL & CLONE AUTHENTIC CONTROLS: Feather + Opacity sliders + Clear / Apply ───
            RampedSlider(
                label = "Feather",
                value = feather,
                minVal = 0f,
                maxVal = 100f,
                defaultValue = if (activeMode == RemoveToolMode.HEAL) 50f else 40f,
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

@Composable
fun LightroomCompactToggle(
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
 * Extracted Effects / Vignette / Grain section to keep `EditorScreen` well below the 64KB JVM method bytecode limit.
 */
@Composable
fun EditorEffectsSection(
    recipe: EditRecipe,
    effectsSubTab: String,
    onSelectSubTab: (String) -> Unit,
    viewModel: EditorViewModel
) {
    val view = LocalView.current
    val dehazeTrackBrush = remember {
        Brush.horizontalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF0D47A1)))
    }
    val vignetteTrackBrush = remember {
        Brush.horizontalGradient(listOf(Color(0xFF000000), Color(0xFF404040), Color(0xFFFFFFFF)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Effects", "Vignette", "Grain").forEach { label ->
            val isSelected = effectsSubTab == label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelectSubTab(label)
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
                minVal = -100f, maxVal = 100f, defaultValue = 0f, isBipolar = true,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = {
                    viewModel.updateRecipe { r ->
                        r.copy(effects = r.effects.copy(texture = it), presence = r.presence.copy(texture = it)).syncLegacyFields()
                    }
                },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Texture") }
            )
            RampedSlider(
                label = "Clarity",
                value = recipe.effects.clarity,
                minVal = -100f, maxVal = 100f, defaultValue = 0f, isBipolar = true,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = {
                    viewModel.updateRecipe { r ->
                        r.copy(effects = r.effects.copy(clarity = it), presence = r.presence.copy(clarity = it)).syncLegacyFields()
                    }
                },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Clarity") }
            )
            RampedSlider(
                label = "Dehaze",
                value = recipe.effects.dehaze,
                minVal = -100f, maxVal = 100f, defaultValue = 0f, isBipolar = true,
                trackBrush = dehazeTrackBrush,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = {
                    viewModel.updateRecipe { r ->
                        r.copy(effects = r.effects.copy(dehaze = it), presence = r.presence.copy(dehaze = it)).syncLegacyFields()
                    }
                },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Dehaze") }
            )
        }
        "Vignette" -> {
            RampedSlider(
                label = "Amount",
                value = recipe.effects.vignetteAmount,
                minVal = -100f, maxVal = 100f, defaultValue = 0f, isBipolar = true,
                trackBrush = vignetteTrackBrush,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteAmount = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette") }
            )
            RampedSlider(
                label = "Midpoint",
                value = recipe.effects.vignetteMidpoint,
                minVal = 0f, maxVal = 100f, defaultValue = 50f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteMidpoint = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Midpoint") }
            )
            RampedSlider(
                label = "Feather",
                value = recipe.effects.vignetteFeather,
                minVal = 0f, maxVal = 100f, defaultValue = 50f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteFeather = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Feather") }
            )
            RampedSlider(
                label = "Roundness",
                value = recipe.effects.vignetteRoundness,
                minVal = -100f, maxVal = 100f, defaultValue = 0f, isBipolar = true,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteRoundness = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Roundness") }
            )
            RampedSlider(
                label = "Highlights",
                value = recipe.effects.vignetteHighlights,
                minVal = 0f, maxVal = 100f, defaultValue = 0f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteHighlights = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette Highlights") }
            )
        }
        "Grain" -> {
            RampedSlider(
                label = "Amount",
                value = recipe.effects.grainAmount,
                minVal = 0f, maxVal = 100f, defaultValue = 0f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(grainAmount = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain") }
            )
            RampedSlider(
                label = "Size",
                value = recipe.effects.grainSize,
                minVal = 0f, maxVal = 100f, defaultValue = 25f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(grainSize = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain Size") }
            )
            RampedSlider(
                label = "Roughness",
                value = recipe.effects.grainRoughness,
                minVal = 0f, maxVal = 100f, defaultValue = 50f, isBipolar = false,
                onValueChangeStart = { viewModel.onSliderTouchStart() },
                onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(grainRoughness = it)).syncLegacyFields() } },
                onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain Roughness") }
            )
        }
    }
}
