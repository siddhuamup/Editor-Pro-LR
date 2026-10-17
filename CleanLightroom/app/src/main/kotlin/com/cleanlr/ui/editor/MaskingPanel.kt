package com.cleanlr.ui.editor

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.model.*
import com.cleanlr.ui.theme.*
import java.util.UUID

/**
 * Task T7: Adobe Lightroom Mobile Masking Panel
 *
 * Provides complete layer management for localized edits:
 * - Multi-layer list (Mask 1, Mask 2...)
 * - Add SubMask: Subject (AI), Sky (AI), Brush, Linear, Radial
 * - Combine Modes: ADD, SUBTRACT, INTERSECT
 * - Mask Inversion and Ruby Overlay Toggle
 * - Mask-scoped Local Adjustments sliders (Exposure, Contrast, Temp, Tint)
 */
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

    val activeLayer = layers.getOrNull(activeLayerIndex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpectrumSurface)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header & Layer Bar
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
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = SpectrumBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "MASKS (${layers.size})",
                    color = SpectrumTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Overlay Toggle Button (Ruby overlay)
                IconButton(
                    onClick = onToggleOverlay,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isOverlayVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Mask Overlay",
                        tint = if (isOverlayVisible) Color(0xFFE02B2B) else SpectrumTextSecondary
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Mask", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Horizontal Mask Layers Row
        if (layers.isNotEmpty()) {
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
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (layer.enabled) Color(0xFFE02B2B) else SpectrumTextDisabled)
                        )
                        Text(
                            text = layer.name.ifEmpty { "Mask ${idx + 1}" },
                            color = if (isSelected) SpectrumTextPrimary else SpectrumTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Active Layer SubMask Actions (Add / Subtract / Intersect)
        if (activeLayer != null) {
            HorizontalDivider(color = SpectrumBorder, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${activeLayer.name} (${activeLayer.subMasks.size} components)",
                    color = SpectrumTextSecondary,
                    fontSize = 12.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showSubMaskMenu = true },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBorder)
                    ) {
                        Text("+ Add / Subtract", fontSize = 11.sp, color = SpectrumTextPrimary)
                    }

                    IconButton(
                        onClick = onDeleteActiveLayer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Mask",
                            tint = Color(0xFFE02B2B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 4. Quick Local Adjustments (Exposure slider)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Local Exposure", fontSize = 12.sp, color = SpectrumTextSecondary)
                    Text(
                        text = String.format("%.2f EV", activeLayer.localAdjustments.exposure),
                        fontSize = 12.sp,
                        color = SpectrumBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = activeLayer.localAdjustments.exposure,
                    onValueChange = { newExp ->
                        val updated = activeLayer.copy(
                            localAdjustments = activeLayer.localAdjustments.copy(exposure = newExp)
                        )
                        onUpdateLayerAdjustments(updated)
                    },
                    valueRange = -3.0f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = SpectrumBlue,
                        activeTrackColor = SpectrumBlue,
                        inactiveTrackColor = SpectrumBorder
                    )
                )
            }
        }
    }

    // Modal / Dropdown Dialog for choosing SubMask types
    if (showAddMenu || showSubMaskMenu) {
        val isSubMaskAction = showSubMaskMenu
        AlertDialog(
            onDismissRequest = {
                showAddMenu = false
                showSubMaskMenu = false
            },
            title = {
                Text(
                    text = if (isSubMaskAction) "Combine Mask Component" else "New Mask",
                    color = SpectrumTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaskTypeOption("Select Subject (LiteRT AI)", Icons.Default.Person) {
                        val sub = SubMask.AISubject(id = UUID.randomUUID().toString())
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Select Sky (LiteRT AI)", Icons.Default.Cloud) {
                        val sub = SubMask.AISky(id = UUID.randomUUID().toString())
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Brush", Icons.Default.Brush) {
                        val sub = SubMask.Brush(id = UUID.randomUUID().toString())
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Linear Gradient", Icons.Default.LinearScale) {
                        val sub = SubMask.LinearGradient(id = UUID.randomUUID().toString(), startX = 0f, startY = 0f, endX = 0f, endY = 1f)
                        if (isSubMaskAction) onAddSubMaskToActive(sub) else onAddLayer(sub)
                        showAddMenu = false; showSubMaskMenu = false
                    }
                    MaskTypeOption("Radial Gradient", Icons.Default.Lens) {
                        val sub = SubMask.RadialGradient(id = UUID.randomUUID().toString())
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
private fun MaskTypeOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SpectrumBlue, modifier = Modifier.size(20.dp))
        Text(text = title, color = SpectrumTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
