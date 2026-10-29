masking_code = '''package com.cleanlr.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.model.*
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*
import java.util.UUID

/**
 * Local Adjustment Categories for Masking
 * (Light, Color, Effects, Detail) matching Adobe Lightroom Mobile
 */
enum class LocalAdjustmentCategory(val title: String) {
    LIGHT("Light"),
    COLOR("Color"),
    EFFECTS("Effects"),
    DETAIL("Detail")
}

/**
 * Helper to get the authentic drawable icon for any SubMask type
 */
fun getSubMaskIconRes(subMask: SubMask): Int = when (subMask) {
    is SubMask.AISubject -> R.drawable.ic_lr_mask_subject
    is SubMask.AISky -> R.drawable.ic_lr_mask_sky
    is SubMask.AIBackground -> R.drawable.ic_lr_mask_background
    is SubMask.Brush -> R.drawable.ic_lr_mask_brush
    is SubMask.LinearGradient -> R.drawable.ic_lr_mask_linear_gradient
    is SubMask.RadialGradient -> R.drawable.ic_lr_mask_radial_gradient
    is SubMask.ColorRange -> R.drawable.ic_lr_mask_color_range
    is SubMask.LuminanceRange -> R.drawable.ic_lr_mask_luminance_range
}

/**
 * Helper to get the authentic label for any SubMask type
 */
fun getSubMaskDisplayName(subMask: SubMask): String = when (subMask) {
    is SubMask.AISubject -> "Subject"
    is SubMask.AISky -> "Sky"
    is SubMask.AIBackground -> "Background"
    is SubMask.Brush -> "Brush"
    is SubMask.LinearGradient -> "Linear Gradient"
    is SubMask.RadialGradient -> "Radial Gradient"
    is SubMask.ColorRange -> "Color Range"
    is SubMask.LuminanceRange -> "Luminance Range"
}

/**
 * Floating Mask Dock on Loupe Canvas (Right Edge)
 * Exactly matches Reference Screenshot 083741:
 * - Resting state (no masks): Floating pill at bottom-right (#222222) with white drag handle (3x28dp) + blue (+) FAB (46dp).
 * - Active state (masks present): Vertical floating layers stack docked to right edge with layer cards + (+) FAB pill.
 */
@Composable
fun FloatingMaskDock(
    masks: List<MaskLayer>,
    activeMaskIndex: Int,
    onSelectMask: (Int) -> Unit,
    onToggleLayer: (Int) -> Unit,
    onInvertMask: (Int) -> Unit,
    onDuplicateMask: (Int) -> Unit,
    onDeleteMask: (Int) -> Unit,
    onAddNewMaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (masks.isEmpty()) {
        // Resting State: Pill docked at bottom-right (Screenshot 083741)
        Surface(
            onClick = onAddNewMaskClick,
            color = Color(0xFF222222),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            shadowElevation = 8.dp,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // White vertical handle
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Circular Blue (+) Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1473E6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_plus),
                        contentDescription = "Create new Mask",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        // Active State: Vertical floating stack docked on right edge (Screenshot 083741)
        Surface(
            color = Color(0xFF1A1A1A).copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            shadowElevation = 10.dp,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // White vertical handle on left edge of the dock
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // List of Mask Layer Cards
                    masks.forEachIndexed { idx, mask ->
                        val isSelected = idx == activeMaskIndex
                        var showCardMenu by remember { mutableStateOf(false) }
                        val primarySubMask = mask.subMasks.firstOrNull()
                        val iconRes = primarySubMask?.let { getSubMaskIconRes(it) } ?: R.drawable.ic_lr_mask_brush

                        Surface(
                            color = if (isSelected) Color(0xFF282828) else Color(0xFF202020),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) Color(0xFF1473E6) else Color(0xFF383838)
                            ),
                            modifier = Modifier
                                .width(74.dp)
                                .clickable { onSelectMask(idx) }
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top row of card: Eye toggle & 3-dot menu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Eye toggle
                                    IconButton(
                                        onClick = { onToggleLayer(idx) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_mask_hide_eye),
                                            contentDescription = "Toggle Mask Visibility",
                                            tint = if (mask.enabled) Color(0xFFE02B2B) else Color(0xFF777777),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // 3-dot overflow menu
                                    Box {
                                        IconButton(
                                            onClick = { showCardMenu = true },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_lr_more_vert),
                                                contentDescription = "Mask Options",
                                                tint = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showCardMenu,
                                            onDismissRequest = { showCardMenu = false },
                                            modifier = Modifier.background(Color(0xFF252525))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Invert", color = Color.White, fontSize = 13.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_lr_mask_invert),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showCardMenu = false
                                                    onInvertMask(idx)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate", color = Color.White, fontSize = 13.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_lr_mask_duplicate),
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showCardMenu = false
                                                    onDuplicateMask(idx)
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF383838), thickness = 0.5.dp)
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = Color(0xFFFF5252), fontSize = 13.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_lr_close),
                                                        contentDescription = null,
                                                        tint = Color(0xFFFF5252),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showCardMenu = false
                                                    onDeleteMask(idx)
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Miniature thumbnail box with ruby-red tint representing the mask
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E2E2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Ruby red wash
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x50E02B2B))
                                    )
                                    // Mask type icon
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Mask Label (e.g., Mask 1)
                                Text(
                                    text = mask.name.ifEmpty { "Mask ${idx + 1}" },
                                    color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Floating (+) Blue FAB to add new mask
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1473E6))
                            .clickable { onAddNewMaskClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_plus),
                            contentDescription = "Add Mask",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Local Adjustment Sliders Panel (Bottom Panel above secondary tabs)
 * Active when recipe.masks.isNotEmpty() in Masking mode.
 */
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: LocalAdjustmentCategory,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLayer = layers.getOrNull(activeLayerIndex) ?: return
    var showCombineSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1D1D1D))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header Sub-Row: [Mask 1] [◐ Invert] ... [Reset] [+ Combine] [Delete]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = activeLayer.name.ifEmpty { "Mask ${activeLayerIndex + 1}" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Invert Toggle Pill Button (◐)
                val isFirstInverted = activeLayer.subMasks.firstOrNull()?.isInverted == true
                Surface(
                    color = if (isFirstInverted) Color(0xFF1473E6) else Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isFirstInverted) Color(0xFF1473E6) else Color(0xFF444444)),
                    modifier = Modifier.clickable {
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
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_mask_invert),
                            contentDescription = "Invert",
                            tint = if (isFirstInverted) Color.White else Color(0xFFCCCCCC),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isFirstInverted) "Inverted" else "Invert",
                            color = if (isFirstInverted) Color.White else Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Reset Adjustments
                IconButton(
                    onClick = {
                        onUpdateLayerAdjustments(
                            activeLayer.copy(
                                localAdjustments = BasicAdjustments(),
                                effectsAdjustments = EffectsAdjustments(),
                                detailAdjustments = DetailAdjustments()
                            )
                        )
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_reset_all),
                        contentDescription = "Reset Adjustments",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // + Combine Pill Button
                Surface(
                    color = Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    modifier = Modifier.clickable { showCombineSheet = true }
                ) {
                    Text(
                        text = "+ Combine",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Delete Mask
                IconButton(
                    onClick = onDeleteActiveLayer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_close),
                        contentDescription = "Delete Mask",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Opacity Slider
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

        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 0.5.dp)

        // 3. Category Sliders
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
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(exposure = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Contrast",
                    value = activeLayer.localAdjustments.contrast,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(contrast = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Highlights",
                    value = activeLayer.localAdjustments.highlights,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(highlights = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Shadows",
                    value = activeLayer.localAdjustments.shadows,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(shadows = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Whites",
                    value = activeLayer.localAdjustments.whites,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(whites = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Blacks",
                    value = activeLayer.localAdjustments.blacks,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(blacks = v))
                        )
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
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(temperature = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Tint",
                    value = activeLayer.localAdjustments.tint,
                    minVal = -150f,
                    maxVal = 150f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(tint = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Saturation",
                    value = activeLayer.localAdjustments.saturation,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(saturation = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Vibrance",
                    value = activeLayer.localAdjustments.vibrance,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(vibrance = v))
                        )
                    }
                )
            }

            LocalAdjustmentCategory.EFFECTS -> {
                RampedSlider(
                    label = "Texture",
                    value = activeLayer.effectsAdjustments.texture,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(effectsAdjustments = activeLayer.effectsAdjustments.copy(texture = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Clarity",
                    value = activeLayer.effectsAdjustments.clarity,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(effectsAdjustments = activeLayer.effectsAdjustments.copy(clarity = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Dehaze",
                    value = activeLayer.effectsAdjustments.dehaze,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(effectsAdjustments = activeLayer.effectsAdjustments.copy(dehaze = v))
                        )
                    }
                )
            }

            LocalAdjustmentCategory.DETAIL -> {
                RampedSlider(
                    label = "Sharpening",
                    value = activeLayer.detailAdjustments.sharpening,
                    minVal = 0f,
                    maxVal = 150f,
                    defaultValue = 0f,
                    isBipolar = false,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(detailAdjustments = activeLayer.detailAdjustments.copy(sharpening = v))
                        )
                    }
                )
                RampedSlider(
                    label = "Noise Reduction",
                    value = activeLayer.detailAdjustments.luminanceNoise,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = false,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(detailAdjustments = activeLayer.detailAdjustments.copy(luminanceNoise = v))
                        )
                    }
                )
            }
        }
    }

    // Combine with Mask Sheet
    if (showCombineSheet) {
        CombineSubMaskBottomSheet(
            onDismissRequest = { showCombineSheet = false },
            onSelectSubMask = { subMask ->
                onAddSubMaskToActive(subMask)
                showCombineSheet = false
            }
        )
    }
}

/**
 * Combine Sub-Mask Bottom Sheet (Add, Subtract, Intersect)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombineSubMaskBottomSheet(
    onDismissRequest: () -> Unit,
    onSelectSubMask: (SubMask) -> Unit
) {
    var combineMode by remember { mutableStateOf(MaskCombineMode.ADD) }
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
                .padding(vertical = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Combine with Mask",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_close),
                        contentDescription = "Close",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF282828), thickness = 1.dp)

            // Operation selector tabs: Add | Subtract | Intersect
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MaskCombineMode.ADD to "+ Add",
                    MaskCombineMode.SUBTRACT to "- Subtract",
                    MaskCombineMode.INTERSECT to "∩ Intersect"
                ).forEach { (mode, label) ->
                    val isSelected = combineMode == mode
                    Surface(
                        color = if (isSelected) Color(0xFF1473E6) else Color(0xFF222222),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF1473E6) else Color(0xFF333333)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { combineMode = mode }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Options List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaskSheetItem(
                    title = "Select Subject",
                    iconRes = R.drawable.ic_lr_mask_subject,
                    showCloud = true,
                    onClick = {
                        onSelectSubMask(SubMask.AISubject(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Select Sky",
                    iconRes = R.drawable.ic_lr_mask_sky,
                    showCloud = true,
                    onClick = {
                        onSelectSubMask(SubMask.AISky(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Select Background",
                    iconRes = R.drawable.ic_lr_mask_background,
                    showCloud = true,
                    onClick = {
                        onSelectSubMask(SubMask.AIBackground(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Brush",
                    iconRes = R.drawable.ic_lr_mask_brush,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.Brush(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Linear Gradient",
                    iconRes = R.drawable.ic_lr_mask_linear_gradient,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.LinearGradient(id = UUID.randomUUID().toString(), startX = 0f, startY = 0f, endX = 0f, endY = 1f, combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Radial Gradient",
                    iconRes = R.drawable.ic_lr_mask_radial_gradient,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.RadialGradient(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Color Range",
                    iconRes = R.drawable.ic_lr_mask_color_range,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.ColorRange(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Luminance Range",
                    iconRes = R.drawable.ic_lr_mask_luminance_range,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.LuminanceRange(id = UUID.randomUUID().toString(), combineMode = combineMode))
                    }
                )
            }
        }
    }
}

/**
 * Create New Mask Bottom Sheet exactly matching Reference Screenshot 083730
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
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
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
                    onClick = {
                        onSelectSubMask(
                            SubMask.LinearGradient(
                                id = UUID.randomUUID().toString(),
                                startX = 0f,
                                startY = 0f,
                                endX = 0f,
                                endY = 1f
                            )
                        )
                    }
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
'''

path = r'CleanLightroom\app\src\main\kotlin\com\cleanlr\ui\editor\MaskingPanel.kt'
with open(path, 'w', encoding='utf-8') as f:
    f.write(masking_code)

print('SUCCESS: MaskingPanel.kt written!')
