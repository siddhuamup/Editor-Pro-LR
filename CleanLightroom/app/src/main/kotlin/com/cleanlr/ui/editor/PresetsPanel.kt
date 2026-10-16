package com.cleanlr.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.preset.Preset
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*

/**
 * Adobe Lightroom Mobile Signature Presets Panel
 *
 * Features:
 * - Group filter pills (All, Creative, B&W, Portrait, Landscape, Vintage)
 * - Amount 0%–200% slider with real-time dynamic scaling
 * - Curated preset cards with active indicators and descriptions
 */
@Composable
fun PresetsPanel(
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    presets: List<Preset>,
    activePreset: Preset?,
    presetAmount: Float,
    onSelectPreset: (Preset) -> Unit,
    onAmountChange: (Float) -> Unit,
    onAmountChangeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val filteredPresets = if (selectedGroup == "All") {
        presets
    } else {
        presets.filter { it.group.equals(selectedGroup, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Group Filter Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                val isSelected = group == selectedGroup
                Surface(
                    color = if (isSelected) SpectrumBlue else SpectrumElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isSelected) SpectrumBlue else SpectrumBorder),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectGroup(group)
                    }
                ) {
                    Text(
                        text = group,
                        color = if (isSelected) Color.White else SpectrumTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Amount Slider (Lightroom Mobile Signature: 0% to 200%)
        if (activePreset != null) {
            Spacer(modifier = Modifier.height(4.dp))
            RampedSlider(
                label = "Amount (${activePreset.name})",
                value = presetAmount,
                minVal = 0.0f,
                maxVal = 200.0f,
                defaultValue = 100.0f,
                isBipolar = false,
                formatPattern = "%.0f%%",
                onValueChange = onAmountChange,
                onValueChangeEnd = onAmountChangeEnd
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Preset Cards Carousel
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredPresets) { preset ->
                val isSelected = preset.id == activePreset?.id
                Surface(
                    color = if (isSelected) SpectrumBlue.copy(alpha = 0.15f) else SpectrumElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) SpectrumBlue else SpectrumBorder
                    ),
                    modifier = Modifier
                        .width(135.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectPreset(preset)
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isSelected) SpectrumBlue else SpectrumTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = SpectrumBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = preset.name,
                            color = SpectrumTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preset.group,
                            color = SpectrumTextSecondary,
                            fontSize = 10.sp
                        )
                        if (preset.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.description,
                                color = SpectrumTextSecondary.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                maxLines = 2,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
