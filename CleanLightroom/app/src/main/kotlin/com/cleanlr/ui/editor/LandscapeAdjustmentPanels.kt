package com.cleanlr.ui.editor

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.model.*
import com.cleanlr.preset.Preset
import com.cleanlr.ui.components.*
import com.cleanlr.ui.theme.*
import java.util.UUID
import kotlin.math.abs

// CURVED STRAIGHTEN DIAL ARC FOR LANDSCAPE
// =============================================================================
@Composable
internal fun CurvedStraightenDialLandscape(
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val arcTopY = height * 0.15f
            val arcDip = 20.dp.toPx()
            val arcSpan = width * 0.35f

            val tickCount = 45
            for (i in 0 until tickCount) {
                val tickFraction = (i - tickCount / 2f) / (tickCount / 2f)
                val tickX = centerX + tickFraction * arcSpan
                val curveNorm = tickFraction * tickFraction
                val currentY = arcTopY + (1f - curveNorm) * arcDip

                val isCenter = i == tickCount / 2
                val isMajor = abs(i - tickCount / 2) % 6 == 0

                if (isCenter) {
                    drawLine(
                        color = Color.White,
                        start = Offset(tickX, currentY + 3.dp.toPx()),
                        end = Offset(tickX, currentY + 12.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else if (isMajor) {
                    drawLine(
                        color = Color(0xFFDDDDDD),
                        start = Offset(tickX, currentY),
                        end = Offset(tickX, currentY + 5.dp.toPx()),
                        strokeWidth = 1.2.dp.toPx()
                    )
                } else {
                    drawCircle(
                        color = Color(0xFF888888),
                        radius = 1.2.dp.toPx(),
                        center = Offset(tickX, currentY + 2.dp.toPx())
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clickable { onAngleChange(0f) }
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.2f°", angle),
                color = if (angle != 0f) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

// =============================================================================
// CROP ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun CropLandscapeAdjustmentPanel(
    crop: CropAdjustments,
    cropPage: String,
    onCropPageSelected: (String) -> Unit,
    selectedAspectCategory: String,
    onAspectCategorySelected: (String) -> Unit,
    aspectRatioGuide: String?,
    onAspectRatioSelected: (String?) -> Unit,
    isExpandEnabled: Boolean,
    onExpandToggled: (Boolean) -> Unit,
    constrainCrop: Boolean,
    onConstrainCropToggled: (Boolean) -> Unit,
    uprightMode: String,
    onUprightModeSelected: (String) -> Unit,
    distortion: Float,
    onDistortionChanged: (Float) -> Unit,
    vertical: Float,
    onVerticalChanged: (Float) -> Unit,
    horizontal: Float,
    onHorizontalChanged: (Float) -> Unit,
    rotate: Float,
    onRotateChanged: (Float) -> Unit,
    aspect: Float,
    onAspectChanged: (Float) -> Unit,
    scale: Float,
    onScaleChanged: (Float) -> Unit,
    xOffset: Float,
    onXOffsetChanged: (Float) -> Unit,
    yOffset: Float,
    onYOffsetChanged: (Float) -> Unit,
    onTouchStart: () -> Unit,
    onTouchEnd: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tab Headers: Aspect vs Geometry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Aspect", "Geometry").forEach { tab ->
                val selected = cropPage == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onCropPageSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (selected) Color.White else SpectrumTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(2.dp)
                                .background(SpectrumBlue)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider)

        if (cropPage == "Aspect") {
            // Expand with AI Toggle Card
            Surface(
                color = Color(0xFF222222),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_quick_actions),
                            contentDescription = null,
                            tint = SpectrumBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Expand with AI", color = Color.White, fontSize = 13.sp)
                    }
                    Switch(
                        checked = isExpandEnabled,
                        onCheckedChange = onExpandToggled,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                    )
                }
            }

            Text(
                text = "ASPECT RATIO",
                color = SpectrumTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            val ratios = listOf(
                "Original" to "Original",
                "1:1" to "1:1",
                "4:5" to "4:5",
                "16:9" to "16:9",
                "9:16" to "9:16",
                "3:2" to "3:2",
                "4:3" to "4:3",
                "Free" to null
            )

            ratios.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { (label, ratioVal) ->
                        val isSelected = aspectRatioGuide == ratioVal
                        Surface(
                            color = if (isSelected) Color(0xFF2C2C2E) else Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) SpectrumBlue else SpectrumInnerBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { onAspectRatioSelected(ratioVal) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else SpectrumTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Geometry Page
            val uprightModes = listOf(
                Triple("Auto", R.drawable.ic_lr_upright_auto, "Auto"),
                Triple("Level", R.drawable.ic_lr_upright_level, "Level"),
                Triple("Vertical", R.drawable.ic_lr_upright_vertical, "Vertical"),
                Triple("Full", R.drawable.ic_lr_upright_full, "Full"),
                Triple("Guided", R.drawable.ic_lr_upright_guided, "Guided")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uprightModes.forEach { (mode, iconRes, label) ->
                    val selected = uprightMode == mode
                    Surface(
                        color = if (selected) Color(0xFF2C2C2E) else Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) Color.White else SpectrumInnerBorder),
                        modifier = Modifier
                            .size(54.dp)
                            .clickable { onUprightModeSelected(mode) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = label,
                                tint = if (selected) Color.White else SpectrumIconNormal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(label, color = if (selected) Color.White else SpectrumTextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Constrain Crop", color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = constrainCrop,
                    onCheckedChange = onConstrainCropToggled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                )
            }

            Text(
                text = "Manual Transforms",
                color = SpectrumTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            RampedSlider("Distortion", distortion, -100f, 100f, onValueChangeStart = onTouchStart, onValueChange = onDistortionChanged, onValueChangeEnd = { onTouchEnd("Distortion") })
            RampedSlider("Vertical", vertical, -100f, 100f, onValueChangeStart = onTouchStart, onValueChange = onVerticalChanged, onValueChangeEnd = { onTouchEnd("Vertical") })
            RampedSlider("Horizontal", horizontal, -100f, 100f, onValueChangeStart = onTouchStart, onValueChange = onHorizontalChanged, onValueChangeEnd = { onTouchEnd("Horizontal") })
            RampedSlider("Rotate", rotate, -45f, 45f, onValueChangeStart = onTouchStart, onValueChange = onRotateChanged, onValueChangeEnd = { onTouchEnd("Rotate") })
            RampedSlider("Aspect", aspect, -100f, 100f, onValueChangeStart = onTouchStart, onValueChange = onAspectChanged, onValueChangeEnd = { onTouchEnd("Aspect") })
            RampedSlider("Scale", scale, 50f, 150f, defaultValue = 100f, isBipolar = false, onValueChangeStart = onTouchStart, onValueChange = onScaleChanged, onValueChangeEnd = { onTouchEnd("Scale") })
        }
    }
}

// =============================================================================
// LIGHT ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun LightLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    isHdrModeActive: Boolean,
    hdrLimitEv: Float,
    onHdrToggle: (Boolean) -> Unit,
    onHdrLimitChange: (Float) -> Unit,
    onOpenCurves: () -> Unit,
    viewModel: EditorViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Curve Navigation Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = SpectrumElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SpectrumInnerBorder),
                modifier = Modifier.clickable { onOpenCurves() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_curve),
                        contentDescription = "Curve",
                        tint = SpectrumBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Curve", color = SpectrumTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        val exposureTrackBrush = remember {
            Brush.horizontalGradient(listOf(Color(0xFF1A1A1A), Color(0xFFE0E0E0)))
        }
        val contrastTrackBrush = remember {
            Brush.horizontalGradient(listOf(Color(0xFF555555), Color(0xFF1A1A1A), Color(0xFF555555), Color(0xFFE0E0E0), Color(0xFF555555)))
        }
        val highlightsTrackBrush = remember {
            Brush.horizontalGradient(listOf(Color(0xFF606060), Color(0xFFFFFFFF)))
        }
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

        Spacer(Modifier.height(8.dp))

        // HDR Mode Card
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
                            Text("Edit in HDR Mode", color = SpectrumTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("High dynamic range editing", color = SpectrumTextSecondary, fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = isHdrModeActive,
                        onCheckedChange = onHdrToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
                    )
                }

                if (isHdrModeActive) {
                    Spacer(Modifier.height(8.dp))
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
                        onValueChange = onHdrLimitChange,
                        onValueChangeEnd = { viewModel.onSliderTouchEnd("HDR Limit") }
                    )
                }
            }
        }
    }
}

// =============================================================================
// COLOR ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun ColorLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    colorSubTab: String,
    onColorSubTabSelected: (String) -> Unit,
    viewModel: EditorViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Sub-tabs row (WB, MIXER, GRADING)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("WB" to "White Balance", "MIXER" to "Color Mixer", "GRADING" to "Color Grading").forEach { (key, title) ->
                val selected = colorSubTab == key
                Surface(
                    color = if (selected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (selected) SpectrumBlue else Color.Transparent),
                    modifier = Modifier.clickable { onColorSubTabSelected(key) }
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else SpectrumTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider)

        when (colorSubTab) {
            "WB" -> {
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

                val isBw = recipe.presence.saturation <= -99f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isBw) SpectrumBlue else SpectrumElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SpectrumInnerBorder),
                        modifier = Modifier.clickable {
                            val nextSat = if (isBw) 0f else -100f
                            viewModel.updateRecipe(if (isBw) "Exit B&W" else "Enter B&W") {
                                it.copy(
                                    basic = it.basic.copy(saturation = nextSat),
                                    presence = it.presence.copy(saturation = nextSat)
                                ).syncLegacyFields()
                            }
                        }
                    ) {
                        Text(
                            text = if (isBw) "B&W Active" else "B&W",
                            color = if (isBw) Color.White else SpectrumTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                RampedSlider(
                    label = "Temp",
                    value = recipe.basic.temperature,
                    minVal = 2000f,
                    maxVal = 50000f,
                    defaultValue = 5500f,
                    isBipolar = false,
                    useMiredScale = true,
                    unitSuffix = "K",
                    trackBrush = tempTrackBrush,
                    onValueChangeStart = { viewModel.onSliderTouchStart() },
                    onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(temperature = it)).syncLegacyFields() } },
                    onValueChangeEnd = { viewModel.onSliderTouchEnd("Temp") }
                )

                RampedSlider(
                    label = "Tint",
                    value = recipe.basic.tint,
                    minVal = -150f,
                    maxVal = 150f,
                    trackBrush = tintTrackBrush,
                    onValueChangeStart = { viewModel.onSliderTouchStart() },
                    onValueChange = { viewModel.updateRecipe { r -> r.copy(basic = r.basic.copy(tint = it)).syncLegacyFields() } },
                    onValueChangeEnd = { viewModel.onSliderTouchEnd("Tint") }
                )

                RampedSlider(
                    label = "Vibrance",
                    value = recipe.presence.vibrance,
                    minVal = -100f,
                    maxVal = 100f,
                    trackBrush = vibranceSatTrackBrush,
                    onValueChangeStart = { viewModel.onSliderTouchStart() },
                    onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(vibrance = it)).syncLegacyFields() } },
                    onValueChangeEnd = { viewModel.onSliderTouchEnd("Vibrance") }
                )

                RampedSlider(
                    label = "Saturation",
                    value = recipe.presence.saturation,
                    minVal = -100f,
                    maxVal = 100f,
                    trackBrush = vibranceSatTrackBrush,
                    onValueChangeStart = { viewModel.onSliderTouchStart() },
                    onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(saturation = it)).syncLegacyFields() } },
                    onValueChangeEnd = { viewModel.onSliderTouchEnd("Saturation") }
                )
            }

            "MIXER" -> {
                HslMixerPanel(
                    hsl = recipe.hsl,
                    onHslChanged = { updated ->
                        viewModel.updateRecipe("Color Mixer") { it.copy(hsl = updated) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            "GRADING" -> {
                ColorGradingWheelWidget(
                    colorGrading = recipe.colorGrading,
                    onColorGradingChanged = { updated ->
                        viewModel.updateRecipe("Color Grading") { it.copy(colorGrading = updated) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =============================================================================
// CURVES ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun CurvesLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    viewModel: EditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        ToneCurveWidget(
            curves = recipe.curves,
            onCurveChanged = { updated ->
                viewModel.updateRecipe("Tone Curve") { it.copy(curves = updated) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        )
    }
}

// =============================================================================
// BLUR ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun BlurLandscapeAdjustmentPanel(
    blurAmount: Float,
    selectedBokehShape: String,
    onBlurAmountChange: (Float) -> Unit,
    onBokehShapeChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Lens Blur",
            color = SpectrumTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(14.dp)
        )

        RampedSlider(
            label = "Blur Amount",
            value = blurAmount,
            minVal = 0f,
            maxVal = 100f,
            isBipolar = false,
            onValueChange = onBlurAmountChange
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Bokeh Shape",
            color = SpectrumTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        val shapes = listOf("Circle", "Bubble", "5-Blade", "Ring", "Cat Eye")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shapes.forEach { shape ->
                val isSelected = selectedBokehShape == shape
                Surface(
                    color = if (isSelected) Color(0xFF2C2C2E) else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) SpectrumBlue else SpectrumInnerBorder),
                    modifier = Modifier.clickable { onBokehShapeChange(shape) }
                ) {
                    Text(
                        text = shape,
                        color = if (isSelected) Color.White else SpectrumTextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// EFFECTS ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun EffectsLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    subTab: String,
    onSubTabChange: (String) -> Unit,
    viewModel: EditorViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Effects", "Vignette", "Grain").forEach { tab ->
                val selected = subTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSubTabChange(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (selected) Color.White else SpectrumTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selected) {
                        Box(modifier = Modifier.width(32.dp).height(2.dp).background(SpectrumBlue))
                    }
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider)

        when (subTab) {
            "Effects" -> {
                RampedSlider("Texture", recipe.presence.texture, -100f, 100f, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(texture = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Texture") })
                RampedSlider("Clarity", recipe.presence.clarity, -100f, 100f, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(clarity = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Clarity") })
                RampedSlider("Dehaze", recipe.presence.dehaze, -100f, 100f, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(presence = r.presence.copy(dehaze = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Dehaze") })
            }
            "Vignette" -> {
                RampedSlider("Amount", recipe.effects.vignetteAmount, -100f, 100f, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteAmount = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Vignette") })
                RampedSlider("Midpoint", recipe.effects.vignetteMidpoint, 0f, 100f, defaultValue = 50f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteMidpoint = it)) } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Midpoint") })
                RampedSlider("Feather", recipe.effects.vignetteFeather, 0f, 100f, defaultValue = 50f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(vignetteFeather = it)) } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Feather") })
            }
            "Grain" -> {
                RampedSlider("Amount", recipe.effects.grainAmount, 0f, 100f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(grainAmount = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain") })
                RampedSlider("Size", recipe.effects.grainSize, 0f, 100f, defaultValue = 25f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(effects = r.effects.copy(grainSize = it)) } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Grain Size") })
            }
        }
    }
}

// =============================================================================
// DETAIL ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun DetailLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    subTab: String,
    onSubTabChange: (String) -> Unit,
    viewModel: EditorViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("Sharpening", "Noise", "Color Noise").forEach { tab ->
                val selected = subTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSubTabChange(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (selected) Color.White else SpectrumTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selected) {
                        Box(modifier = Modifier.width(36.dp).height(2.dp).background(SpectrumBlue))
                    }
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider)

        when (subTab) {
            "Sharpening" -> {
                RampedSlider("Sharpening", recipe.detail.sharpening, 0f, 150f, defaultValue = 0f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpening = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Sharpening") })
                RampedSlider("Radius", recipe.detail.sharpenRadius, 0.5f, 3.0f, defaultValue = 1.0f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenRadius = it)) } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Radius") })
                RampedSlider("Detail", recipe.detail.sharpenDetail, 0f, 100f, defaultValue = 25f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(sharpenDetail = it)) } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Detail") })
            }
            "Noise" -> {
                RampedSlider("Noise Reduction", recipe.detail.luminanceNoise, 0f, 100f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(luminanceNoise = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Noise Reduction") })
            }
            "Color Noise" -> {
                RampedSlider("Color Noise Reduction", recipe.detail.colorNoise, 0f, 100f, isBipolar = false, onValueChangeStart = { viewModel.onSliderTouchStart() }, onValueChange = { viewModel.updateRecipe { r -> r.copy(detail = r.detail.copy(colorNoise = it)).syncLegacyFields() } }, onValueChangeEnd = { viewModel.onSliderTouchEnd("Color Noise") })
            }
        }
    }
}

// =============================================================================
// OPTICS ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun OpticsLandscapeAdjustmentPanel(
    recipe: EditRecipe,
    removeCa: Boolean,
    enableLensCorrections: Boolean,
    onRemoveCaChange: (Boolean) -> Unit,
    onEnableLensCorrectionsChange: (Boolean) -> Unit,
    viewModel: EditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remove Chromatic Aberration", color = Color.White, fontSize = 13.sp)
            Switch(
                checked = removeCa,
                onCheckedChange = onRemoveCaChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable Lens Profile Corrections", color = Color.White, fontSize = 13.sp)
            Switch(
                checked = enableLensCorrections,
                onCheckedChange = onEnableLensCorrectionsChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
            )
        }
    }
}

// =============================================================================
// PROFILES ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun ProfilesLandscapeAdjustmentPanel(
    selectedGroup: String,
    selectedProfile: String,
    onGroupSelected: (String) -> Unit,
    onProfileSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val groups = listOf("Basic", "Artistic", "B&W", "Modern", "Vintage")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { group ->
                val selected = selectedGroup == group
                Surface(
                    color = if (selected) Color(0xFF2C2C2E) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) SpectrumBlue else SpectrumInnerBorder),
                    modifier = Modifier.clickable { onGroupSelected(group) }
                ) {
                    Text(
                        text = group,
                        color = if (selected) Color.White else SpectrumTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = SpectrumGroupDivider)

        val profiles = (1..6).map { "$selectedGroup 0$it" }
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { prof ->
                val selected = selectedProfile == prof
                Surface(
                    color = if (selected) Color(0xFF2680EB).copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) SpectrumBlue else SpectrumInnerBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProfileSelected(prof) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prof,
                            color = if (selected) SpectrumBlue else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// PRESETS ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun PresetsLandscapeAdjustmentPanel(
    allPresets: List<Preset>,
    activePreset: Preset?,
    presetAmount: Float,
    onSelectPreset: (Preset) -> Unit,
    onPresetAmountChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        if (activePreset != null) {
            Text(
                text = "Preset: ${activePreset.name}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            RampedSlider(
                label = "Amount",
                value = presetAmount,
                minVal = 0f,
                maxVal = 200f,
                defaultValue = 100f,
                isBipolar = false,
                onValueChange = onPresetAmountChange
            )
            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = "AVAILABLE PRESETS",
            color = SpectrumTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (allPresets.isEmpty()) {
            Text("No presets found", color = SpectrumTextSecondary, fontSize = 12.sp)
        } else {
            for (preset in allPresets) {
                val isSelected = activePreset?.id == preset.id
                Surface(
                    color = if (isSelected) SpectrumBlue.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) SpectrumBlue else SpectrumInnerBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isSelected) SpectrumBlue else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// REMOVE ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun RemoveLandscapeAdjustmentPanel(
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1473E6)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lr_generative_remove),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
        }

        RampedSlider(
            label = "Size",
            value = brushSize,
            minVal = 1f,
            maxVal = 100f,
            isBipolar = false,
            onValueChange = onBrushSizeChange
        )

        var detectObjects by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Detect objects", color = Color.White, fontSize = 13.sp)
            Switch(
                checked = detectObjects,
                onCheckedChange = { detectObjects = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
            )
        }

        var generativeAi by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Generative AI", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_lr_context_help),
                    contentDescription = null,
                    tint = SpectrumTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Switch(
                checked = generativeAi,
                onCheckedChange = { generativeAi = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpectrumBlue)
            )
        }
    }
}

// =============================================================================
// ACTIONS ADJUSTMENT PANEL (Column 2A)
// =============================================================================
@Composable
internal fun ActionsLandscapeAdjustmentPanel(
    onAutoTone: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Surface(
            color = Color(0xFF222222),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, Color(0xFF242424)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Auto Tone & Color", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("AI exposure & dynamic range adjustment", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAutoTone,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1686FF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "QUICK ACTIONS",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        val quickActions = listOf(
            "Enhance Details" to R.drawable.ic_lr_slider_clarity,
            "Select Subject" to R.drawable.ic_lr_mask_subject,
            "Select Sky" to R.drawable.ic_lr_mask_sky,
            "Select Background" to R.drawable.ic_lr_mask_background
        )

        quickActions.forEach { (label, iconRes) ->
            Surface(
                color = Color(0xFF262626),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color(0xFF242424)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onQuickAction(label) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painter = painterResource(iconRes), contentDescription = null, tint = Color(0xFF1686FF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

