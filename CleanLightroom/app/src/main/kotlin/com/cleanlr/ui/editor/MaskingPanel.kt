package com.cleanlr.ui.editor

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.model.*
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 5 Local Adjustment Categories for Masking
 * (Light, Color, Effects, Detail, Optics) matching Adobe Lightroom Mobile exactly.
 * Reference: Screenshots 201104, 201109, 201112, 201114, 201117.
 */
enum class LocalAdjustmentCategory(val title: String) {
    LIGHT("Light"),
    COLOR("Color"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    OPTICS("Optics")
}

typealias MaskAdjustmentCategory = LocalAdjustmentCategory

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

// -----------------------------------------------------------------------------
// Task 2: Canvas Overlay & Badge (Ruby Red Overlay & Centroid Active Mask Pin)
// -----------------------------------------------------------------------------

/**
 * Ruby Red Mask Overlay rendering helper (authentic Lightroom Mobile #E02B2B / #FF0033 at 50% alpha).
 * Renders over the active canvas to visualize masked regions.
 * Reference: Screenshot 201104_Lightroom.png.
 */
@Composable
fun MaskRubyRedOverlay(
    mask: MaskLayer?,
    isVisible: Boolean = true,
    overlayColor: Color = Color(0x80FF0033), // Ruby Red 50% alpha
    maskBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    if (mask == null || !mask.enabled || !isVisible || mask.subMasks.isEmpty()) return

    val effectiveAlpha = (overlayColor.alpha * mask.opacity).coerceIn(0f, 1f)
    val tint = overlayColor.copy(alpha = effectiveAlpha)

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (maskBitmap != null) {
            drawImage(
                image = maskBitmap.asImageBitmap(),
                dstSize = androidx.compose.ui.unit.IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint, androidx.compose.ui.graphics.BlendMode.SrcIn)
            )
            return@Canvas
        }

        for (sub in mask.subMasks) {
            when (sub) {
                is SubMask.AISubject -> {
                    // Subject silhouette: high-contrast center-weighted coverage
                    val path = Path().apply {
                        val centerX = canvasWidth * 0.5f
                        val topY = canvasHeight * 0.15f
                        val bottomY = canvasHeight * 0.90f
                        val radiusX = canvasWidth * 0.32f
                        moveTo(centerX, topY)
                        cubicTo(
                            centerX + radiusX * 0.8f, topY + canvasHeight * 0.1f,
                            centerX + radiusX * 1.1f, topY + canvasHeight * 0.35f,
                            centerX + radiusX, topY + canvasHeight * 0.5f
                        )
                        cubicTo(
                            centerX + radiusX * 0.9f, bottomY - canvasHeight * 0.2f,
                            centerX + radiusX * 0.7f, bottomY,
                            centerX, bottomY
                        )
                        cubicTo(
                            centerX - radiusX * 0.7f, bottomY,
                            centerX - radiusX * 0.9f, bottomY - canvasHeight * 0.2f,
                            centerX - radiusX, topY + canvasHeight * 0.5f
                        )
                        cubicTo(
                            centerX - radiusX * 1.1f, topY + canvasHeight * 0.35f,
                            centerX - radiusX * 0.8f, topY + canvasHeight * 0.1f,
                            centerX, topY
                        )
                        close()
                    }
                    if (sub.isInverted) {
                        drawRect(tint)
                        // Inverted: clear subject region
                        drawPath(path, Color.Transparent, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    } else {
                        drawPath(path, tint)
                    }
                }

                is SubMask.AISky -> {
                    // Sky region: top 40% with horizon feathering
                    val skyBrush = Brush.verticalGradient(
                        colors = listOf(tint, tint.copy(alpha = effectiveAlpha * 0.8f), Color.Transparent),
                        startY = 0f,
                        endY = canvasHeight * 0.45f
                    )
                    drawRect(brush = skyBrush, size = Size(canvasWidth, canvasHeight * 0.45f))
                }

                is SubMask.AIBackground -> {
                    // Background region: full screen minus center subject ellipse
                    drawRect(tint)
                    val cutoutRadiusX = canvasWidth * 0.28f
                    val cutoutRadiusY = canvasHeight * 0.35f
                    drawOval(
                        color = Color.Transparent,
                        topLeft = Offset(canvasWidth * 0.5f - cutoutRadiusX, canvasHeight * 0.45f - cutoutRadiusY),
                        size = Size(cutoutRadiusX * 2f, cutoutRadiusY * 2f),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                }

                is SubMask.Brush -> {
                    if (sub.points.isNotEmpty()) {
                        val strokePath = Path()
                        sub.points.forEachIndexed { i, pt ->
                            val px = pt.x * canvasWidth
                            val py = pt.y * canvasHeight
                            if (i == 0) strokePath.moveTo(px, py) else strokePath.lineTo(px, py)
                            // Draw individual stamp
                            val r = pt.radius.coerceAtLeast(12f)
                            drawCircle(
                                color = tint.copy(alpha = (effectiveAlpha * pt.flow).coerceIn(0f, 1f)),
                                radius = r,
                                center = Offset(px, py)
                            )
                        }
                        val firstRadius = sub.points.first().radius.coerceAtLeast(12f)
                        drawPath(
                            path = strokePath,
                            color = tint,
                            style = Stroke(
                                width = firstRadius * 2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                is SubMask.LinearGradient -> {
                    val start = Offset(sub.startX * canvasWidth, sub.startY * canvasHeight)
                    val end = Offset(sub.endX * canvasWidth, sub.endY * canvasHeight)
                    val gradBrush = Brush.linearGradient(
                        colors = listOf(tint, tint.copy(alpha = effectiveAlpha * 0.3f), Color.Transparent),
                        start = start,
                        end = end
                    )
                    drawRect(brush = gradBrush)
                }

                is SubMask.RadialGradient -> {
                    val center = Offset(sub.centerX * canvasWidth, sub.centerY * canvasHeight)
                    val radius = (sub.radiusX * canvasWidth).coerceAtLeast(40f)
                    val radBrush = Brush.radialGradient(
                        colors = listOf(tint, tint.copy(alpha = effectiveAlpha * 0.4f), Color.Transparent),
                        center = center,
                        radius = radius
                    )
                    drawRect(brush = radBrush)
                }

                is SubMask.ColorRange, is SubMask.LuminanceRange -> {
                    // Range masks: diffuse targeted tint wash
                    drawRect(tint.copy(alpha = effectiveAlpha * 0.7f))
                }
            }
        }
    }
}

/**
 * Centroid Active Mask Badge / Pin (Reference Screenshot 201104).
 * Displays a glowing circular/pill badge pinned to the centroid of the active mask on canvas,
 * showing the mask tool icon with blue tint and white circular halo/glow.
 */
@Composable
fun ActiveMaskCentroidPin(
    subMask: SubMask?,
    centroidFraction: Offset = Offset(0.5f, 0.48f),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (subMask == null) return
    val iconRes = getSubMaskIconRes(subMask)

    Box(
        modifier = modifier
            .offset(
                x = (centroidFraction.x * 200 - 100).dp,
                y = (centroidFraction.y * 200 - 100).dp
            )
            .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = Color(0xFF1473E6), spotColor = Color(0xFF1473E6))
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color(0xFF1473E6), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = "Active Mask Centroid",
            tint = Color(0xFF1473E6),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * High-level Mask Overlay Canvas composable combining Ruby Red overlay and centroid pin.
 */
@Composable
fun MaskOverlayCanvas(
    masks: List<MaskLayer>,
    activeMaskIndex: Int,
    isOverlayVisible: Boolean = true,
    maskBitmap: Bitmap? = null,
    onPinClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeMask = masks.getOrNull(activeMaskIndex)
    Box(modifier = modifier.fillMaxSize()) {
        MaskRubyRedOverlay(
            mask = activeMask,
            isVisible = isOverlayVisible,
            maskBitmap = maskBitmap,
            modifier = Modifier.fillMaxSize()
        )
        if (activeMask != null && activeMask.enabled && isOverlayVisible) {
            val primarySub = activeMask.subMasks.firstOrNull()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ActiveMaskCentroidPin(
                    subMask = primarySub,
                    onClick = onPinClick
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Task 3: Floating Dock & Layer Stack (FloatingMaskDock)
// -----------------------------------------------------------------------------

/**
 * Floating Mask Dock on Loupe Canvas (Right Edge)
 * Exactly matches Reference Screenshots 083741 & 201104:
 * - Resting state (no masks): Compact pill at bottom-right (#222222) with white drag handle (3x28dp) + blue (+) circular FAB (46dp).
 * - Active state (masks present):
 *   - Collapsed state: Compact pill with thumbnail silhouette + grab handle + (+) button.
 *   - Expanded state: Vertical scrollable stack of mask layer cards docked to right edge.
 *     Each card shows thumbnail silhouette, layer name (e.g. "Mask 1"), eye visibility toggle, and 3-dot context menu (Duplicate, Invert, Delete).
 *     Vertical scrolling ensures large layer counts never overflow.
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
    var isExpanded by remember { mutableStateOf(true) }

    if (masks.isEmpty()) {
        // Resting State: Compact pill docked at bottom-right (Screenshot 083741)
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
                // White vertical grab handle bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Circular Blue (+) Button (46dp)
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
    } else if (!isExpanded) {
        // Collapsed resting state with masks present (compact thumbnail silhouette pill, Screenshot 201104)
        val activeLayer = masks.getOrNull(activeMaskIndex) ?: masks.first()
        val primarySubMask = activeLayer.subMasks.firstOrNull()
        val iconRes = primarySubMask?.let { getSubMaskIconRes(it) } ?: R.drawable.ic_lr_mask_brush

        Surface(
            color = Color(0xFF1A1A1A).copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            shadowElevation = 8.dp,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grab handle: tapping toggles expanded state
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable { isExpanded = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Thumbnail card showing mask silhouette
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222222))
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable { isExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = "Expand Masks",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Small (+) button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1473E6))
                        .clickable { onAddNewMaskClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_plus),
                        contentDescription = "Add Mask",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    } else {
        // Expanded State: Vertical scrollable floating stack docked to right edge (Screenshot 083741)
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
                // White vertical grab handle on left edge (tap to collapse)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable { isExpanded = false }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Vertical scrollable container: ensures large layer counts never overflow
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                                    .width(76.dp)
                                    .clickable { onSelectMask(idx) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Top row of card: Eye toggle & 3-dot overflow menu
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

                                        // 3-dot overflow menu (Duplicate, Invert, Delete)
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
                                                DropdownMenuItem(
                                                    text = { Text("Duplicate and Invert", color = Color.White, fontSize = 13.sp) },
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
                                                        onDuplicateMask(idx)
                                                        onInvertMask(idx)
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
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2E2E2E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Ruby red wash
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x40E02B2B))
                                        )
                                        // Mask type icon
                                        Icon(
                                            painter = painterResource(iconRes),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        // Invert badge indicator if inverted
                                        if (primarySubMask?.isInverted == true) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(2.dp)
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1473E6))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Mask Label (e.g. Mask 1)
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

// -----------------------------------------------------------------------------
// Task 4 & Task 5: Layer Operations & 5 Local Adjustment Categories
// -----------------------------------------------------------------------------

/**
 * 7-parameter overload called by EditorScreen.kt (backward compatible)
 */
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
) {
    MaskingSlidersPanel(
        layers = layers,
        activeLayerIndex = activeLayerIndex,
        selectedCategory = selectedCategory,
        onCategorySelected = {},
        onUpdateLayerAdjustments = onUpdateLayerAdjustments,
        onDeleteActiveLayer = onDeleteActiveLayer,
        onAddSubMaskToActive = onAddSubMaskToActive,
        onSubtractSubMaskFromActive = onAddSubMaskToActive,
        onIntersectSubMaskWithActive = onAddSubMaskToActive,
        showCategoryTabs = showCategoryTabs,
        modifier = modifier
    )
}

/**
 * Complete 10-parameter MaskingSlidersPanel adhering strictly to PROJECT.md § Interface Contracts.
 * Supports all 5 categories: Light, Color, Effects, Detail, Optics.
 * Reference Screenshots: 201104 (Light), 201109 (Color), 201112 (Effects), 201114 (Detail), 201117 (Optics).
 */
@Composable
fun MaskingSlidersPanel(
    layers: List<MaskLayer>,
    activeLayerIndex: Int,
    selectedCategory: MaskAdjustmentCategory,
    onCategorySelected: (MaskAdjustmentCategory) -> Unit = {},
    onUpdateLayerAdjustments: (MaskLayer) -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onAddSubMaskToActive: (SubMask) -> Unit,
    onSubtractSubMaskFromActive: (SubMask) -> Unit = onAddSubMaskToActive,
    onIntersectSubMaskWithActive: (SubMask) -> Unit = onAddSubMaskToActive,
    showCategoryTabs: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeLayer = layers.getOrNull(activeLayerIndex) ?: return
    var showCombineSheet by remember { mutableStateOf(false) }

    // Internal category state allows direct category switching in panel header
    var currentCategory by remember(selectedCategory) { mutableStateOf(selectedCategory) }

    // Color fine adjustment switch & Hue state (Screenshot 201109)
    var useFineAdjustment by remember { mutableStateOf(false) }
    var currentHue by remember(activeLayer.id) { mutableStateOf(0f) }

    // Local Optics state: Moiré and Defringe (Screenshot 201117)
    var moireValue by remember(activeLayer.id) { mutableStateOf(activeLayer.detailAdjustments.colorSmoothness) }
    var defringeValue by remember(activeLayer.id) { mutableStateOf(activeLayer.detailAdjustments.luminanceDetail) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Authentic Lightroom Masking Sliders Header: clean uppercase category label + divider (Screenshots 201104 & 201109)
        val primarySub = activeLayer.subMasks.firstOrNull()
        if (primarySub != null) {
            when (primarySub) {
                is SubMask.LuminanceRange -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LUMINANCE RANGE",
                                color = Color(0xFF38A9FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Invert", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = primarySub.isInverted,
                                    onCheckedChange = { inv ->
                                        val updatedSub = primarySub.copy(isInverted = inv)
                                        val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                        onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                        RampedSlider(
                            label = "Min Luminance",
                            value = primarySub.minLuminance,
                            minVal = 0f,
                            maxVal = 100f,
                            defaultValue = 60f,
                            isBipolar = false,
                            onValueChange = { minL ->
                                val updatedSub = primarySub.copy(minLuminance = minL)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        RampedSlider(
                            label = "Max Luminance",
                            value = primarySub.maxLuminance,
                            minVal = 0f,
                            maxVal = 100f,
                            defaultValue = 100f,
                            isBipolar = false,
                            onValueChange = { maxL ->
                                val updatedSub = primarySub.copy(maxLuminance = maxL)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        RampedSlider(
                            label = "Feather / Smoothness",
                            value = primarySub.feather,
                            minVal = 0f,
                            maxVal = 100f,
                            defaultValue = 25f,
                            isBipolar = false,
                            onValueChange = { f ->
                                val updatedSub = primarySub.copy(feather = f)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        HorizontalDivider(color = Color(0xFF2E2E32), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                is SubMask.ColorRange -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COLOR RANGE",
                                color = Color(0xFF38A9FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Invert", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = primarySub.isInverted,
                                    onCheckedChange = { inv ->
                                        val updatedSub = primarySub.copy(isInverted = inv)
                                        val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                        onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                        RampedSlider(
                            label = "Target Hue",
                            value = primarySub.targetHue,
                            minVal = 0f,
                            maxVal = 360f,
                            defaultValue = 210f,
                            isBipolar = false,
                            onValueChange = { hue ->
                                val updatedSub = primarySub.copy(targetHue = hue)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        RampedSlider(
                            label = "Refine / Tolerance",
                            value = primarySub.hueTolerance,
                            minVal = 5f,
                            maxVal = 90f,
                            defaultValue = 30f,
                            isBipolar = false,
                            onValueChange = { tol ->
                                val updatedSub = primarySub.copy(hueTolerance = tol)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        RampedSlider(
                            label = "Feather",
                            value = primarySub.feather,
                            minVal = 0f,
                            maxVal = 60f,
                            defaultValue = 25f,
                            isBipolar = false,
                            onValueChange = { f ->
                                val updatedSub = primarySub.copy(feather = f)
                                val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                            }
                        )
                        HorizontalDivider(color = Color(0xFF2E2E32), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                is SubMask.LinearGradient, is SubMask.RadialGradient, is SubMask.AISubject, is SubMask.AISky, is SubMask.AIBackground -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getSubMaskDisplayName(primarySub).uppercase(),
                            color = Color(0xFFC0C0C4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Invert", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = primarySub.isInverted,
                                onCheckedChange = { inv ->
                                    val updatedSub = when (primarySub) {
                                        is SubMask.LinearGradient -> primarySub.copy(isInverted = inv)
                                        is SubMask.RadialGradient -> primarySub.copy(isInverted = inv)
                                        is SubMask.AISubject -> primarySub.copy(isInverted = inv)
                                        is SubMask.AISky -> primarySub.copy(isInverted = inv)
                                        is SubMask.AIBackground -> primarySub.copy(isInverted = inv)
                                        else -> primarySub
                                    }
                                    val updatedSubs = activeLayer.subMasks.toMutableList().apply { set(0, updatedSub) }
                                    onUpdateLayerAdjustments(activeLayer.copy(subMasks = updatedSubs))
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
                else -> {}
            }
        }

        Text(
            text = currentCategory.title.uppercase(),
            color = Color(0xFFB8B8BE),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        HorizontalDivider(color = Color(0xFF323236), thickness = 0.8.dp)

        if (showCategoryTabs) {
        // 3. Category Header & Tab Selector Strip (Light | Color | Effects | Detail | Optics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalAdjustmentCategory.values().forEach { cat ->
                val isCatSelected = cat == currentCategory
                Text(
                    text = cat.title.uppercase(),
                    color = if (isCatSelected) Color(0xFF38A9FF) else Color(0xFF8E8E8E),
                    fontSize = 11.sp,
                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            currentCategory = cat
                            onCategorySelected(cat)
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }

        // 4. 5 Local Adjustment Categories with Sliders
        when (currentCategory) {
            LocalAdjustmentCategory.LIGHT -> {
                // Exposure (-3 to +3 EV)
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
                // Contrast (-100 to +100)
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
                // Highlights (-100 to +100)
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
                // Shadows (-100 to +100)
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
                // Whites (-100 to +100)
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
                // Blacks (-100 to +100)
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
                // Temp (blue-amber gradient track, Screenshot 201109)
                val tempOffset = if (activeLayer.localAdjustments.temperature in -100f..100f) {
                    activeLayer.localAdjustments.temperature
                } else {
                    ((activeLayer.localAdjustments.temperature - 5500f) / 45f).coerceIn(-100f, 100f)
                }
                RampedSlider(
                    label = "Temp",
                    value = tempOffset,
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = true,
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color(0xFF3B72FF), Color(0xFF666666), Color(0xFFFFAA3B))
                    ),
                    onValueChange = { v ->
                        val newKelvin = 5500f + v * 45f
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(temperature = newKelvin))
                        )
                    }
                )

                // Tint (green-magenta gradient track, Screenshot 201109)
                RampedSlider(
                    label = "Tint",
                    value = activeLayer.localAdjustments.tint.coerceIn(-100f, 100f),
                    minVal = -100f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = true,
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color(0xFF2ECC71), Color(0xFF666666), Color(0xFFE02B8A))
                    ),
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(localAdjustments = activeLayer.localAdjustments.copy(tint = v))
                        )
                    }
                )

                // Saturation (-100 to +100)
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

                // Hue (rainbow gradient track, Screenshot 201109)
                RampedSlider(
                    label = "Hue",
                    value = currentHue,
                    minVal = 0.0f,
                    maxVal = 360.0f,
                    defaultValue = 0.0f,
                    isBipolar = false,
                    formatPattern = "%.1f",
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                            Color.Blue, Color.Magenta, Color.Red
                        )
                    ),
                    onValueChange = { v ->
                        val step = if (useFineAdjustment) 0.1f else 1.0f
                        currentHue = (v / step).roundToInt() * step
                    }
                )

                // Use Fine Adjustment switch (Screenshot 201109)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Use Fine Adjustment",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Switch(
                        checked = useFineAdjustment,
                        onCheckedChange = { useFineAdjustment = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1473E6),
                            uncheckedThumbColor = Color(0xFF8E8E93),
                            uncheckedTrackColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.scale(0.85f)
                    )
                }

                // Color Swatch indicator: Color H:- S:0 (Screenshot 201109)
                val currentSat = activeLayer.localAdjustments.saturation.coerceAtLeast(0f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hText = if (currentSat > 0f) "${currentHue.roundToInt()}" else "-"
                    val sText = "${currentSat.roundToInt()}"
                    Text(
                        text = "Color   H:$hText   S:$sText",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentSat > 0f) {
                                    val hsv = floatArrayOf(currentHue, (currentSat / 100f).coerceIn(0f, 1f), 1.0f)
                                    Color(android.graphics.Color.HSVToColor(hsv))
                                } else {
                                    Color.White
                                }
                            )
                            .border(1.dp, Color(0xFF555555), CircleShape)
                    )
                }
            }

            LocalAdjustmentCategory.EFFECTS -> {
                // Texture (-100 to +100)
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
                // Clarity (-100 to +100)
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
                // Dehaze (-100 to +100)
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
                // Noise (0 to 100, unipolar, Screenshot 201114)
                RampedSlider(
                    label = "Noise",
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
                // Sharpness (0 to 100, unipolar, Screenshot 201114)
                RampedSlider(
                    label = "Sharpness",
                    value = activeLayer.detailAdjustments.sharpening,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = false,
                    onValueChange = { v ->
                        onUpdateLayerAdjustments(
                            activeLayer.copy(detailAdjustments = activeLayer.detailAdjustments.copy(sharpening = v))
                        )
                    }
                )
            }

            LocalAdjustmentCategory.OPTICS -> {
                // Moiré (0 to 100, unipolar, Screenshot 201117)
                RampedSlider(
                    label = "Moiré",
                    value = moireValue,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = false,
                    onValueChange = { v ->
                        moireValue = v
                        onUpdateLayerAdjustments(
                            activeLayer.copy(
                                detailAdjustments = activeLayer.detailAdjustments.copy(colorSmoothness = v)
                            )
                        )
                    }
                )
                // Defringe (0 to 100, unipolar, Screenshot 201117)
                RampedSlider(
                    label = "Defringe",
                    value = defringeValue,
                    minVal = 0f,
                    maxVal = 100f,
                    defaultValue = 0f,
                    isBipolar = false,
                    onValueChange = { v ->
                        defringeValue = v
                        onUpdateLayerAdjustments(
                            activeLayer.copy(
                                detailAdjustments = activeLayer.detailAdjustments.copy(luminanceDetail = v)
                            )
                        )
                    }
                )
            }
        }
    }

    // Combine with Mask Sheet (Add, Subtract, Intersect)
    if (showCombineSheet) {
        CombineSubMaskBottomSheet(
            onDismissRequest = { showCombineSheet = false },
            onSelectSubMask = { subMask ->
                when (subMask.combineMode) {
                    MaskCombineMode.ADD -> onAddSubMaskToActive(subMask)
                    MaskCombineMode.SUBTRACT -> onSubtractSubMaskFromActive(subMask)
                    MaskCombineMode.INTERSECT -> onIntersectSubMaskWithActive(subMask)
                }
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
    onSelectSubMask: (SubMask) -> Unit,
    initialCombineMode: MaskCombineMode = MaskCombineMode.ADD
) {
    var combineMode by remember(initialCombineMode) { mutableStateOf(initialCombineMode) }
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
            // Header: Title + Close Button
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
                            textAlign = TextAlign.Center,
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
                // Group 1: AI Tools (Cloud)
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

                // Divider
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

                // Group 2: Manual Tools
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
                        onSelectSubMask(
                            SubMask.LinearGradient(
                                id = UUID.randomUUID().toString(),
                                startX = 0.5f,
                                startY = 0.18f,
                                endX = 0.5f,
                                endY = 0.62f,
                                combineMode = combineMode
                            )
                        )
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

                // Divider
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

                // Group 3: Range Tools
                MaskSheetItem(
                    title = "Color Range",
                    iconRes = R.drawable.ic_lr_mask_color_range,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.ColorRange(id = UUID.randomUUID().toString(), targetHue = 210f, hueTolerance = 30f, feather = 25f, combineMode = combineMode))
                    }
                )
                MaskSheetItem(
                    title = "Luminance Range",
                    iconRes = R.drawable.ic_lr_mask_luminance_range,
                    showCloud = false,
                    onClick = {
                        onSelectSubMask(SubMask.LuminanceRange(id = UUID.randomUUID().toString(), minLuminance = 60f, maxLuminance = 100f, feather = 25f, combineMode = combineMode))
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Task 1: "Create new Mask" Bottom Sheet (CreateNewMaskBottomSheet)
// -----------------------------------------------------------------------------

/**
 * Create New Mask Bottom Sheet exactly matching Reference Screenshot 083730:
 * - Top header with title and help icon (?)
 * - 3 distinct tool categories with rounded dark buttons (#222222):
 *   - Group 1 (AI Cloud): Select Subject, Select Sky, Select Background (displaying Cloud AI badge on right).
 *   - Group 2 (Manual): Brush, Linear Gradient, Radial Gradient.
 *   - Group 3 (Range): Color Range, Luminance Range.
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
        containerColor = Color(0xFF1E1E20),
        contentColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: "Create new Mask" + crisp white Help Icon (Screenshots 083730 & 201045)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create new Mask",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    painter = painterResource(R.drawable.ic_lr_context_help),
                    contentDescription = "Help",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E32), thickness = 0.8.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group 1: AI (Select Subject, Select Sky, Select Background) with cloud icon badge
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

                // Centered subtle divider line (Screenshot 083730)
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
                                startX = 0.5f,
                                startY = 0.18f,
                                endX = 0.5f,
                                endY = 0.62f
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

                // Centered subtle divider line (Screenshot 083730)
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
                    onClick = { onSelectSubMask(SubMask.ColorRange(id = UUID.randomUUID().toString(), targetHue = 210f, hueTolerance = 30f, feather = 25f)) }
                )
                MaskSheetItem(
                    title = "Luminance Range",
                    iconRes = R.drawable.ic_lr_mask_luminance_range,
                    showCloud = false,
                    onClick = { onSelectSubMask(SubMask.LuminanceRange(id = UUID.randomUUID().toString(), minLuminance = 60f, maxLuminance = 100f, feather = 25f)) }
                )
            }
        }
    }
}

/**
 * Individual recessed dark button item for mask creation (Exact Screenshots 083730 & 201045: #111113 card inside #1E1E20 sheet!)
 */
@Composable
private fun MaskSheetItem(
    title: String,
    iconRes: Int,
    showCloud: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF111113),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                tint = Color(0xFFD0D0D4),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = Color(0xFFEAEAEF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (showCloud) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_cloud_filled),
                    contentDescription = "AI Mask",
                    tint = Color(0xFFA0A0A4),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
