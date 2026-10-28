package com.cleanlr.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.model.*
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*
import java.util.UUID

/**
 * Task T7 & Section 9: Adobe Lightroom Mobile Masking Panel (Tips 336–385, 503)
 *
 * Full layer management for localized edits:
 * - Empty state with 5 prominent cards + 3 One-Tap AI Action chips (Tips 369, 370, 377, 378, 379)
 * - Multi-layer list (Mask 1, Mask 2...) with Eye toggle, Invert (◐), and Opacity slider (Tips 343, 346, 347)
 * - SubMask stack with Combine Mode (ADD, SUBTRACT, INTERSECT) (Tips 344, 345)
 * - Category Tabs for Local Adjustments: Light, Color, Effects (Tips 339, 340, 503)
 * - Support for ColorRange & LuminanceRange masks (Tips 349, 350)
 */
enum class LocalAdjustmentCategory(val title: String) {
    LIGHT("Light"),
    COLOR("Color"),
    EFFECTS("Effects")
}

@Composable
fun MaskingPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: (SubMask) -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    isOverlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMenu by remember { mutableStateOf(false) }
    var showSubMaskMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(LocalAdjustmentCategory.LIGHT) }

    val activeLayer = layers.getOrNull(activeLayerIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpectrumSurface)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_masking),
                    contentDescription = null,
                    tint = SpectrumBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "MASKS (${layers.size})",
                    color = SpectrumTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Overlay Toggle Button (Ruby Red Overlay)
                IconButton(
                    onClick = onToggleOverlay,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_mask_hide_eye),
                        contentDescription = "Toggle Mask Overlay",
                        tint = if (isOverlayVisible) Color(0xFFE02B2B) else SpectrumTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Add New Mask Layer Button
                Button(
                    onClick = { showAddMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpectrumBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_import_add),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Mask", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Empty State with Prominent Cards & One-Tap AI Action Chips (Tips 369, 370, 377, 378, 379)
        if (layers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select a tool to create your first mask:",
                    color = SpectrumTextSecondary,
                    fontSize = 12.sp
                )

                // 5 Primary Mask Cards Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        QuickMaskCard("Subject (AI)", R.drawable.ic_lr_mask_subject, Color(0xFF2680EB)) {
                            onAddLayer(SubMask.AISubject(id = UUID.randomUUID().toString()))
                        }
                    }
                    item {
                        QuickMaskCard("Sky (AI)", R.drawable.ic_lr_mask_sky, Color(0xFF2680EB)) {
                            onAddLayer(SubMask.AISky(id = UUID.randomUUID().toString()))
                        }
                    }
                    item {
                        QuickMaskCard("Brush", R.drawable.ic_lr_mask_brush, Color(0xFFE02B2B)) {
                            onAddLayer(SubMask.Brush(id = UUID.randomUUID().toString()))
                        }
                    }
                    item {
                        QuickMaskCard("Linear", R.drawable.ic_lr_mask_linear_gradient, Color(0xFFFF9800)) {
                            onAddLayer(SubMask.LinearGradient(id = UUID.randomUUID().toString(), startX = 0f, startY = 0f, endX = 0f, endY = 1f))
                        }
                    }
                    item {
                        QuickMaskCard("Radial", R.drawable.ic_lr_mask_radial_gradient, Color(0xFF9C27B0)) {
                            onAddLayer(SubMask.RadialGradient(id = UUID.randomUUID().toString()))
                        }
                    }
                }

                // 3 One-Tap AI Actions Chips Row (Tips 377, 378, 379)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OneTapAiChip("✦ Enhance Sky", Color(0xFF2680EB)) {
                        val mask = MaskLayer(
                            id = UUID.randomUUID().toString(),
                            name = "Sky Pop",
                            subMasks = listOf(SubMask.AISky(id = UUID.randomUUID().toString())),
                            localAdjustments = BasicAdjustments(exposure = -0.35f, contrast = 20f, saturation = 20f)
                        )
                        onAddLayer(mask.subMasks.first())
                    }
                    OneTapAiChip("✦ Pop Subject", Color(0xFF00C853)) {
                        val mask = MaskLayer(
                            id = UUID.randomUUID().toString(),
                            name = "Subject Pop",
                            subMasks = listOf(SubMask.AISubject(id = UUID.randomUUID().toString())),
                            localAdjustments = BasicAdjustments(exposure = 0.25f, contrast = 15f)
                        )
                        onAddLayer(mask.subMasks.first())
                    }
                    OneTapAiChip("✦ Dim BG", Color(0xFFFF9100)) {
                        val mask = MaskLayer(
                            id = UUID.randomUUID().toString(),
                            name = "Dim BG",
                            subMasks = listOf(SubMask.AISubject(id = UUID.randomUUID().toString(), isInverted = true)),
                            localAdjustments = BasicAdjustments(exposure = -0.45f, contrast = 10f)
                        )
                        onAddLayer(mask.subMasks.first())
                    }
                }
            }
        } else {
            // 3. Horizontal Mask Layers Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(layers) { idx, layer ->
                    val isSelected = idx == activeLayerIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SpectrumElevated else SpectrumSurface)
                            .border(1.dp, if (isSelected) SpectrumBlue else SpectrumBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectLayer(idx) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Eye icon to toggle layer enabled/disabled (Tip 346)
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_mask_hide_eye),
                            contentDescription = "Toggle Layer",
                            tint = if (layer.enabled) Color(0xFFE02B2B) else SpectrumTextDisabled,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    onUpdateLayerAdjustments(layer.copy(enabled = !layer.enabled))
                                }
                        )
                        Text(
                            text = layer.name.ifEmpty { "Mask ${idx + 1}" },
                            color = if (isSelected) SpectrumTextPrimary else SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Active Layer Controls & Sub-Mask Stack
            if (activeLayer != null) {
                HorizontalDivider(color = SpectrumBorder, thickness = 0.5.dp)

                // SubMask summary, combine mode, invert, and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${activeLayer.name} (${activeLayer.subMasks.size} items)",
                            color = SpectrumTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Invert toggle button (◐) (Tip 343)
                        val isFirstInverted = activeLayer.subMasks.firstOrNull()?.isInverted == true
                        Surface(
                            color = if (isFirstInverted) SpectrumBlue else SpectrumElevated,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clickable {
                                    val updatedSubMasks = activeLayer.subMasks.map { sub ->
                                        when (sub) {
                                            is SubMask.Brush -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.LinearGradient -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.RadialGradient -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.AISubject -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.AISky -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.AIBackground -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.ColorRange -> sub.copy(isInverted = !sub.isInverted)
                                            is SubMask.LuminanceRange -> sub.copy(isInverted = !sub.isInverted)
                                        }
                                    }
                                    onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubMasks))
                                }
                        ) {
                            Text(
                                text = if (isFirstInverted) "◐ Inverted" else "○ Invert",
                                color = if (isFirstInverted) Color.White else SpectrumTextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Reset Adjustments Button (Tip 358)
                        IconButton(
                            onClick = {
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = BasicAdjustments()))
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_reset_all),
                                contentDescription = "Reset Adjustments",
                                tint = SpectrumIconNormal,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Add component to active mask
                        OutlinedButton(
                            onClick = { showSubMaskMenu = true },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBorder)
                        ) {
                            Text("+ Combine", fontSize = 10.sp, color = SpectrumTextPrimary)
                        }

                        // Delete mask
                        IconButton(
                            onClick = onDeleteActiveLayer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Mask",
                                tint = Color(0xFFE02B2B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Opacity Slider (Tip 347)
                RampedSlider(
                    label = "Mask Opacity",
                    value = activeLayer.opacity * 100f,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 100f,
                    isBipolar = false,
                    unitSuffix = "%",
                    onValueChange = { newOp ->
                        onUpdateLayerAdjustments(activeLayer.copy(opacity = newOp / 100f))
                    }
                )

                HorizontalDivider(color = SpectrumBorder, thickness = 0.5.dp)

                // 5. Category Tabs for Local Adjustments (Tips 339, 340, 503)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LocalAdjustmentCategory.values().forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        Surface(
                            color = if (isCatSelected) SpectrumBlue else SpectrumElevated,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat.title,
                                color = if (isCatSelected) Color.White else SpectrumTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }

                // 6. Category Slider Content
                when (selectedCategory) {
                    LocalAdjustmentCategory.LIGHT -> {
                        RampedSlider(
                            label = "Exposure",
                            value = activeLayer.localAdjustments.exposure,
                            minVal = -3.0f,
                            maxVal = 3.0f,
                            defaultValue = 0f,
                            unitSuffix = "EV",
                            formatPattern = "%.2f",
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(exposure = v)))
                            }
                        )
                        RampedSlider(
                            label = "Contrast",
                            value = activeLayer.localAdjustments.contrast,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(contrast = v)))
                            }
                        )
                        RampedSlider(
                            label = "Highlights",
                            value = activeLayer.localAdjustments.highlights,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(highlights = v)))
                            }
                        )
                        RampedSlider(
                            label = "Shadows",
                            value = activeLayer.localAdjustments.shadows,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(shadows = v)))
                            }
                        )
                    }

                    LocalAdjustmentCategory.COLOR -> {
                        RampedSlider(
                            label = "Temp",
                            value = activeLayer.localAdjustments.temperature,
                            minVal = 2000f,
                            maxVal = 10000f,
                            defaultValue = 5500f,
                            isBipolar = false,
                            unitSuffix = "K",
                            formatPattern = "%.0f",
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(temperature = v)))
                            }
                        )
                        RampedSlider(
                            label = "Tint",
                            value = activeLayer.localAdjustments.tint,
                            minVal = -150f,
                            maxVal = 150f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(tint = v)))
                            }
                        )
                        RampedSlider(
                            label = "Saturation",
                            value = activeLayer.localAdjustments.saturation,
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(saturation = v)))
                            }
                        )
                    }

                    LocalAdjustmentCategory.EFFECTS -> {
                        RampedSlider(
                            label = "Clarity",
                            value = activeLayer.localAdjustments.whites, // stored in local whites / clarity channel
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(whites = v)))
                            }
                        )
                        RampedSlider(
                            label = "Texture",
                            value = activeLayer.localAdjustments.blacks, // stored in local blacks / texture channel
                            minVal = -100f,
                            maxVal = 100f,
                            defaultValue = 0f,
                            onValueChange = { v ->
                                onUpdateLayerAdjustments(activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(blacks = v)))
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog for adding/combining SubMask types (Tips 344, 349, 350)
    if (showAddMenu || showSubMaskMenu) {
        val isSubMaskAction = showSubMaskMenu
        var combineMode by remember { mutableStateOf(MaskCombineMode.ADD) }

        AlertDialog(
            onDismissRequest = {
                showAddMenu = false
                showSubMaskMenu = false
            },
            title = {
                Text(
                    text = if (isSubMaskAction) "Combine Component" else "New Mask Layer",
                    color = SpectrumTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSubMaskAction) {
                        Text("Combine Mode:", fontSize = 11.sp, color = SpectrumTextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MaskCombineMode.values().forEach { mode ->
                                val isSelected = combineMode == mode
                                Surface(
                                    color = if (isSelected) SpectrumBlue else SpectrumSurface,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { combineMode = mode }
                                ) {
                                    Text(
                                        text = mode.name,
                                        color = if (isSelected) Color.White else SpectrumTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    MaskTypeOption("Select Subject (AI)", R.drawable.ic_lr_mask_subject) {
                        val sub = SubMask.AISubject(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Select Sky (AI)", R.drawable.ic_lr_mask_sky) {
                        val sub = SubMask.AISky(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Brush", R.drawable.ic_lr_mask_brush) {
                        val sub = SubMask.Brush(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Linear Gradient", R.drawable.ic_lr_mask_linear_gradient) {
                        val sub = SubMask.LinearGradient(id = UUID.randomUUID().toString(), startX = 0f, startY = 0f, endX = 0f, endY = 1f, combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Radial Gradient", R.drawable.ic_lr_mask_radial_gradient) {
                        val sub = SubMask.RadialGradient(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Color Range", R.drawable.ic_lr_mask_color_range) {
                        val sub = SubMask.ColorRange(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Luminance Range", R.drawable.ic_lr_mask_luminance_range) {
                        val sub = SubMask.LuminanceRange(id = UUID.randomUUID().toString(), combineMode = combineMode)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMenu = false; showSubMaskMenu = false }) {
                    Text("Cancel", color = SpectrumTextSecondary)
                }
            },
            containerColor = SpectrumElevated
        )
    }
}

@Composable
private fun QuickMaskCard(
    title: String,
    iconRes: Int,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = SpectrumElevated,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBorder),
        modifier = Modifier
            .clickable { onClick() }
            .width(82.dp)
            .height(68.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = title, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, color = SpectrumTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun OneTapAiChip(
    title: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = SpectrumElevated,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun MaskTypeOption(
    title: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = SpectrumBlue, modifier = Modifier.size(18.dp))
        Text(text = title, color = SpectrumTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Create New Mask Bottom Sheet matching Reference Screenshot 083730
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewMaskBottomSheet(
    onDismissRequest: () -> Unit,
    onSelectSubMask: (SubMask) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        contentColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Row: "Create new Mask" + Help Icon (Screenshot 083730)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create new Mask",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    painter = painterResource(R.drawable.ic_lr_context_help),
                    contentDescription = "Help",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group 1: AI (Select Subject, Select Sky, Select Background) with cloud icon
                MaskSheetItem(
                    title = "Select Subject",
                    iconRes = R.drawable.ic_lr_mask_subject,
                    showCloud = true,
                    onClick = { onSelectSubMask(SubMask.AISubject(id = UUID.randomUUID().toString())) }
                )
                MaskSheetItem(
                    title = "Select Sky",
                    iconRes = R.drawable.ic_lr_mask_sky,
                    showCloud = true,
                    onClick = { onSelectSubMask(SubMask.AISky(id = UUID.randomUUID().toString())) }
                )
                MaskSheetItem(
                    title = "Select Background",
                    iconRes = R.drawable.ic_lr_mask_background,
                    showCloud = true,
                    onClick = { onSelectSubMask(SubMask.AIBackground(id = UUID.randomUUID().toString())) }
                )

                // Centered subtle divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(1.dp)
                            .background(Color(0xFF333333))
                    )
                }

                // Group 2: Manual (Brush, Linear Gradient, Radial Gradient)
                MaskSheetItem(
                    title = "Brush",
                    iconRes = R.drawable.ic_lr_mask_brush,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.Brush(id = UUID.randomUUID().toString())) }
                )
                MaskSheetItem(
                    title = "Linear Gradient",
                    iconRes = R.drawable.ic_lr_mask_linear_gradient,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.LinearGradient(id = UUID.randomUUID().toString(), startX = 0f, startY = 0f, endX = 0f, endY = 1f)) }
                )
                MaskSheetItem(
                    title = "Radial Gradient",
                    iconRes = R.drawable.ic_lr_mask_radial_gradient,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.RadialGradient(id = UUID.randomUUID().toString())) }
                )

                // Centered subtle divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(1.dp)
                            .background(Color(0xFF333333))
                    )
                }

                // Group 3: Range (Color Range, Luminance Range)
                MaskSheetItem(
                    title = "Color Range",
                    iconRes = R.drawable.ic_lr_mask_color_range,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.ColorRange(id = UUID.randomUUID().toString())) }
                )
                MaskSheetItem(
                    title = "Luminance Range",
                    iconRes = R.drawable.ic_lr_mask_luminance_range,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.LuminanceRange(id = UUID.randomUUID().toString())) }
                )
            }
        }
    }
}

@Composable
private fun MaskSheetItem(
    title: String,
    iconRes: Int,
    showCloud: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF222222),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = Color(0xFFD4D4D4),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (showCloud) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_cloud_filled),
                    contentDescription = "Cloud AI",
                    tint = Color(0xFFA0A0A0),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
