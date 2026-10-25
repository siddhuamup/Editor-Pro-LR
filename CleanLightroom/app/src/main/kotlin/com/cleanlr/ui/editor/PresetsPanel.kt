package com.cleanlr.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * Adobe Lightroom Mobile Signature Presets Panel (Tips 381–400, 498, 499)
 *
 * Features:
 * - Group filter pills (All, Creative, B&W, Portrait, Landscape, Vintage)
 * - None (Original) reset chip (Tip 400)
 * - Amount 0%–200% slider with smooth animated reveal (Tip 499)
 * - Selected checkmark badge in blue circle & intensity badge (Tips 395, 498)
 * - Save Preset & Import .xmp actions (Tips 383, 384)
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
    onResetToOriginal: () -> Unit = {},
    onSaveCurrentAsPreset: () -> Unit = {},
    onImportXmpPreset: () -> Unit = {},
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
        // 1. Group Filter Pills & Action Buttons Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(groups) { group ->
                    val isSelected = group == selectedGroup
                    Surface(
                        color = if (isSelected) SpectrumBlue else SpectrumElevated,
                        shape = RoundedCornerShape(14.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Buttons: + Save & Import XMP (Tips 383, 384)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSaveCurrentAsPreset()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_create_preset),
                        contentDescription = "Save Preset",
                        tint = SpectrumIconNormal,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onImportXmpPreset()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_import_add),
                        contentDescription = "Import XMP",
                        tint = SpectrumIconNormal,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // 2. Amount Slider with Smooth Animated Reveal (Tip 499)
        AnimatedVisibility(
            visible = activePreset != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (activePreset != null) {
                Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
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
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Preset Cards Carousel
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 0: None (Original) Reset Button (Tip 400)
            item {
                Surface(
                    color = if (activePreset == null) SpectrumBlue.copy(alpha = 0.15f) else SpectrumElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        width = if (activePreset == null) 1.5.dp else 1.dp,
                        color = if (activePreset == null) SpectrumBlue else SpectrumBorder
                    ),
                    modifier = Modifier
                        .width(110.dp)
                        .height(84.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onResetToOriginal()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_reset_all),
                            contentDescription = "Original",
                            tint = if (activePreset == null) SpectrumBlue else SpectrumTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "None (Original)",
                            color = SpectrumTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

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
                                painter = painterResource(R.drawable.ic_lr_presets),
                                contentDescription = null,
                                tint = if (isSelected) SpectrumBlue else SpectrumTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )

                            // Selected checkmark badge in blue circle (Tip 498) & Amount badge (Tip 395)
                            if (isSelected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        color = SpectrumBlue,
                                        shape = RoundedCornerShape(3.dp)
                                    ) {
                                        Text(
                                            text = "${presetAmount.toInt()}%",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(SpectrumBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_select_checkmark),
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
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
