package com.cleanlr.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.engine.TatChannelWeights
import com.cleanlr.model.HslAdjustments
import com.cleanlr.model.HslChannel
import com.cleanlr.ui.theme.*
import kotlin.math.abs

/**
 * Tip 209: HSL View Modes
 */
enum class HslViewMode(val label: String) {
    BY_COLOR("By Color"),
    ALL_COLORS("All Colors")
}

enum class HslTargetProperty(val label: String) {
    HUE("Hue"),
    SATURATION("Saturation"),
    LUMINANCE("Luminance")
}

/**
 * 8 Adobe Spectrum Colors with Abbr and Hue Transitions (Tips 212–214, 222)
 */
enum class HslColor(
    val title: String,
    val abbr: String,
    val color: Color,
    val hueGradient: List<Color>,
    val darkColor: Color,
    val lightColor: Color
) {
    RED(
        title = "Red",
        abbr = "R",
        color = Color(0xFFE63946),
        hueGradient = listOf(Color(0xFFF72585), Color(0xFFE63946), Color(0xFFF48C06)),
        darkColor = Color(0xFF5D0000),
        lightColor = Color(0xFFFF8A80)
    ),
    ORANGE(
        title = "Orange",
        abbr = "O",
        color = Color(0xFFF48C06),
        hueGradient = listOf(Color(0xFFE63946), Color(0xFFF48C06), Color(0xFFFFBE0B)),
        darkColor = Color(0xFF6B3B00),
        lightColor = Color(0xFFFFD180)
    ),
    YELLOW(
        title = "Yellow",
        abbr = "Y",
        color = Color(0xFFFFBE0B),
        hueGradient = listOf(Color(0xFFF48C06), Color(0xFFFFBE0B), Color(0xFF38B000)),
        darkColor = Color(0xFF706000),
        lightColor = Color(0xFFFFFF8D)
    ),
    GREEN(
        title = "Green",
        abbr = "G",
        color = Color(0xFF38B000),
        hueGradient = listOf(Color(0xFFFFBE0B), Color(0xFF38B000), Color(0xFF00B4D8)),
        darkColor = Color(0xFF004D15),
        lightColor = Color(0xFFB9F6CA)
    ),
    AQUA(
        title = "Aqua",
        abbr = "A",
        color = Color(0xFF00B4D8),
        hueGradient = listOf(Color(0xFF38B000), Color(0xFF00B4D8), Color(0xFF0077B6)),
        darkColor = Color(0xFF004852),
        lightColor = Color(0xFF84FFFF)
    ),
    BLUE(
        title = "Blue",
        abbr = "B",
        color = Color(0xFF0077B6),
        hueGradient = listOf(Color(0xFF00B4D8), Color(0xFF0077B6), Color(0xFF7209B7)),
        darkColor = Color(0xFF002B66),
        lightColor = Color(0xFF82B1FF)
    ),
    PURPLE(
        title = "Purple",
        abbr = "P",
        color = Color(0xFF7209B7),
        hueGradient = listOf(Color(0xFF0077B6), Color(0xFF7209B7), Color(0xFFF72585)),
        darkColor = Color(0xFF420054),
        lightColor = Color(0xFFEA80FC)
    ),
    MAGENTA(
        title = "Magenta",
        abbr = "M",
        color = Color(0xFFF72585),
        hueGradient = listOf(Color(0xFF7209B7), Color(0xFFF72585), Color(0xFFE63946)),
        darkColor = Color(0xFF5E0025),
        lightColor = Color(0xFFFF80AB)
    )
}

/**
 * HslMixerPanel — Pro Lightroom 8-Channel Color Mixer
 * Implements Tips 209 to 250
 */
@Composable
fun HslMixerPanel(
    hsl: HslAdjustments,
    onHslChanged: (HslAdjustments) -> Unit,
    activeTatWeights: TatChannelWeights? = null,
    activeTatTargetName: String? = null,
    isMonochrome: Boolean = false,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (actionName: String) -> Unit = {},
    isTatActive: Boolean = false,
    onToggleTat: () -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var viewMode by remember { mutableStateOf(HslViewMode.BY_COLOR) }
    var selectedColor by remember { mutableStateOf(HslColor.RED) }
    var allColorsTargetProperty by remember { mutableStateOf(HslTargetProperty.SATURATION) }

    // Tip 215: Auto-select swatch when TAT samples a color
    LaunchedEffect(activeTatTargetName) {
        activeTatTargetName?.let { name ->
            HslColor.values().firstOrNull { it.title.equals(name, ignoreCase = true) }?.let {
                selectedColor = it
            }
        }
    }

    fun getChannel(color: HslColor): HslChannel = when (color) {
        HslColor.RED -> hsl.red
        HslColor.ORANGE -> hsl.orange
        HslColor.YELLOW -> hsl.yellow
        HslColor.GREEN -> hsl.green
        HslColor.AQUA -> hsl.aqua
        HslColor.BLUE -> hsl.blue
        HslColor.PURPLE -> hsl.purple
        HslColor.MAGENTA -> hsl.magenta
    }

    fun updateChannel(color: HslColor, channel: HslChannel) {
        val updated = when (color) {
            HslColor.RED -> hsl.copy(red = channel)
            HslColor.ORANGE -> hsl.copy(orange = channel)
            HslColor.YELLOW -> hsl.copy(yellow = channel)
            HslColor.GREEN -> hsl.copy(green = channel)
            HslColor.AQUA -> hsl.copy(aqua = channel)
            HslColor.BLUE -> hsl.copy(blue = channel)
            HslColor.PURPLE -> hsl.copy(purple = channel)
            HslColor.MAGENTA -> hsl.copy(magenta = channel)
        }
        onHslChanged(updated)
    }

    fun isChannelModified(color: HslColor): Boolean {
        val ch = getChannel(color)
        return abs(ch.hue) > 0.01f || abs(ch.saturation) > 0.01f || abs(ch.luminance) > 0.01f
    }

    fun resetChannel(color: HslColor) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        updateChannel(color, HslChannel(0f, 0f, 0f))
        onTouchEnd("Reset ${color.title}")
    }

    fun resetAllChannels() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onHslChanged(HslAdjustments.default())
        onTouchEnd("Reset All HSL")
    }

    val activeChannel = getChannel(selectedColor)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Authentic Top Header Bar: "Color Mix" + TAT Icon + "DONE" Button (Screenshot 083551 & 083601)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Color Mix",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TAT Target Adjustment Tool Toggle Button
                Surface(
                    color = if (isTatActive) SpectrumBlue else SpectrumElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleTat()
                    }
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_color_target_tool),
                            contentDescription = "Targeted Adjustment Tool",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Authentic Pro "DONE" Button
                Surface(
                    color = SpectrumElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDone()
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (viewMode == HslViewMode.BY_COLOR) {
            // Tip 232: Compact 8-Color Swatches Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HslColor.values().forEach { col ->
                    val isSelected = col == selectedColor
                    val isModified = isChannelModified(col)
                    val sizeAnim by animateDpAsState(
                        targetValue = if (isSelected) 34.dp else 28.dp,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                        label = "swatchSize"
                    )

                    // Tip 216: Dual-Channel Weight Ring if active in TAT
                    val isTatTarget = activeTatTargetName?.equals(col.title, ignoreCase = true) == true

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.pointerInput(col) {
                            detectTapGestures(
                                onTap = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    selectedColor = col
                                },
                                onDoubleTap = {
                                    resetChannel(col)
                                }
                            )
                        }
                    ) {
                        if (isSelected) {
                            // Active: Filled color circle with white center dot (Screenshot 083551)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col.color)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        } else {
                            // Inactive: Hollow color ring (Screenshot 083551)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, col.color, CircleShape)
                                    .background(Color.Transparent)
                            ) {
                                if (isModified) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(col.color)
                                    )
                                }
                            }
                        }

                        // Modification indicator dot
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isModified && !isSelected) col.color else Color.Transparent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tip 212: Dynamic Hue Track Gradient
            val hueBrush = remember(selectedColor) {
                Brush.horizontalGradient(selectedColor.hueGradient)
            }
            // Tip 213: Dynamic Saturation Track Gradient
            val satBrush = remember(selectedColor) {
                Brush.horizontalGradient(listOf(Color(0xFF707070), selectedColor.color))
            }
            // Tip 214: Dynamic Luminance Track Gradient
            val lumBrush = remember(selectedColor) {
                Brush.horizontalGradient(listOf(selectedColor.darkColor, selectedColor.lightColor))
            }

            if (!isMonochrome) {
                RampedSlider(
                    label = "Hue",
                    value = activeChannel.hue,
                    minVal = -100f,
                    maxVal = 100f,
                    trackBrush = hueBrush,
                    formatPattern = "%+.0f°",
                    onValueChangeStart = onTouchStart,
                    onValueChange = { updateChannel(selectedColor, activeChannel.copy(hue = it)) },
                    onValueChangeEnd = { onTouchEnd("Hue ${selectedColor.title}") }
                )
                RampedSlider(
                    label = "Saturation",
                    value = activeChannel.saturation,
                    minVal = -100f,
                    maxVal = 100f,
                    trackBrush = satBrush,
                    formatPattern = "%+.0f",
                    onValueChangeStart = onTouchStart,
                    onValueChange = { updateChannel(selectedColor, activeChannel.copy(saturation = it)) },
                    onValueChangeEnd = { onTouchEnd("Saturation ${selectedColor.title}") }
                )
            }

            RampedSlider(
                label = if (isMonochrome) "Luminance (B&W)" else "Luminance",
                value = activeChannel.luminance,
                minVal = -100f,
                maxVal = 100f,
                trackBrush = lumBrush,
                formatPattern = "%+.0f",
                onValueChangeStart = onTouchStart,
                onValueChange = { updateChannel(selectedColor, activeChannel.copy(luminance = it)) },
                onValueChangeEnd = { onTouchEnd("Luminance ${selectedColor.title}") }
            )

        } else {
            // ALL_COLORS mode: Sub-tabs for H / S / L (Tip 209)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HslTargetProperty.values().forEach { prop ->
                    val isSelected = allColorsTargetProperty == prop
                    Surface(
                        color = if (isSelected) SpectrumBlue else SpectrumElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            allColorsTargetProperty = prop
                        }
                    ) {
                        Text(
                            text = prop.label,
                            color = if (isSelected) Color.White else SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 8 Sliders in a row for the selected property
            HslColor.values().forEach { col ->
                val ch = getChannel(col)
                val (value, brush, label) = when (allColorsTargetProperty) {
                    HslTargetProperty.HUE -> Triple(ch.hue, Brush.horizontalGradient(col.hueGradient), "${col.title} Hue")
                    HslTargetProperty.SATURATION -> Triple(ch.saturation, Brush.horizontalGradient(listOf(Color(0xFF707070), col.color)), "${col.title} Saturation")
                    HslTargetProperty.LUMINANCE -> Triple(ch.luminance, Brush.horizontalGradient(listOf(col.darkColor, col.lightColor)), "${col.title} Luminance")
                }

                RampedSlider(
                    label = col.title,
                    value = value,
                    minVal = -100f,
                    maxVal = 100f,
                    trackBrush = brush,
                    formatPattern = if (allColorsTargetProperty == HslTargetProperty.HUE) "%+.0f°" else "%+.0f",
                    onValueChangeStart = onTouchStart,
                    onValueChange = { newVal ->
                        val updated = when (allColorsTargetProperty) {
                            HslTargetProperty.HUE -> ch.copy(hue = newVal)
                            HslTargetProperty.SATURATION -> ch.copy(saturation = newVal)
                            HslTargetProperty.LUMINANCE -> ch.copy(luminance = newVal)
                        }
                        updateChannel(col, updated)
                    },
                    onValueChangeEnd = { onTouchEnd(label) }
                )
            }
        }
    }
}
