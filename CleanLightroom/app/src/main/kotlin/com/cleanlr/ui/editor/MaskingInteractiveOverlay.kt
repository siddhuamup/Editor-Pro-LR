package com.cleanlr.ui.editor

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.engine.CropGeometryEngine
import com.cleanlr.model.*
import kotlin.math.*

/**
 * Complete Adobe Lightroom Mobile Masking Canvas Host (Reference Screenshots 083730, 083741,
 * 201028, 201040, 201104–201117).
 *
 * Renders:
 *  1. Pixel-aligned real Ruby-Red / Green / B&W mask overlay + interactive draggable overlay grids
 *     (Linear Gradient 3-line guide, Radial Gradient dual-ellipse + 4 handles, Smooth Brush ribbon,
 *     Color Range Eyedropper target, Subject/Sky/Background glowing centroid pin).
 *  2. Left Floating Action Pill (Invert + Delete — Screenshot 201028).
 *  3. Right Floating Mask Layers Dock with live B&W silhouette thumbnails, nested `↳` sub-mask tree,
 *     and authentic `+/-` callout menu ("Add to" / "Subtract from" — Screenshot 201040).
 *  4. Top-Right Overlay Visibility Toggle (`ic_lr_mask_overlay_on` / `ic_lr_mask_overlay_off`) +
 *     Sub-Mask live parameter pill (Brush Size/Feather/Flow, Radial Feather, Range sliders).
 */
@Composable
fun EditorMaskingOverlayHost(
    viewModel: EditorViewModel,
    recipe: EditRecipe,
    bitmap: Bitmap?,
    onAddNewMaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val activeMaskIndex by viewModel.activeMaskLayerIndex.collectAsState()
    val activeSubMaskIndex by viewModel.activeSubMaskIndex.collectAsState()
    val showMaskOverlay by viewModel.showMaskOverlay.collectAsState()
    val maskOverlayMode by viewModel.maskOverlayMode.collectAsState()
    val maskPreviewBitmaps by viewModel.maskPreviewBitmaps.collectAsState()
    val maskBrushSize by viewModel.maskBrushSize.collectAsState()
    val maskBrushFeather by viewModel.maskBrushFeather.collectAsState()
    val maskBrushFlow by viewModel.maskBrushFlow.collectAsState()
    val isMaskBrushErasing by viewModel.isMaskBrushErasing.collectAsState()

    var showAddSubtractCallout by remember { mutableStateOf(false) }
    var pendingCombineMode by remember { mutableStateOf<MaskCombineMode?>(null) }

    val masks = recipe.masks
    val activeMask = masks.getOrNull(activeMaskIndex)
    val activeSubMask = activeMask?.subMasks?.getOrNull(
        activeSubMaskIndex.coerceIn(0, ((activeMask.subMasks.lastIndex)).coerceAtLeast(0))
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Pixel-Aligned Interactive Mask Overlay & Draggable Grids
        if (activeMask != null && activeMask.enabled) {
            InteractiveMaskOverlayCanvas(
                crop = recipe.crop,
                imageWidth = bitmap?.width ?: 1200,
                imageHeight = bitmap?.height ?: 800,
                mask = activeMask,
                activeSubMask = activeSubMask,
                previewAlphaBitmap = maskPreviewBitmaps[activeMask.id],
                showOverlay = showMaskOverlay,
                overlayMode = maskOverlayMode,
                brushSize = maskBrushSize,
                brushFeather = maskBrushFeather,
                isBrushErasing = isMaskBrushErasing,
                onLinearGradientDrag = { sx, sy, ex, ey ->
                    viewModel.updateActiveSubMask { sub ->
                        if (sub is SubMask.LinearGradient) sub.copy(startX = sx, startY = sy, endX = ex, endY = ey) else sub
                    }
                },
                onRadialGradientDrag = { cx, cy, rx, ry ->
                    viewModel.updateActiveSubMask { sub ->
                        if (sub is SubMask.RadialGradient) sub.copy(centerX = cx, centerY = cy, radiusX = rx, radiusY = ry) else sub
                    }
                },
                onBrushPoint = { nx, ny, isNew -> viewModel.appendMaskBrushPoint(nx, ny, isNew) },
                onColorSample = { nx, ny -> viewModel.sampleColorRangeAt(nx, ny) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Overlay toggle lives cleanly in Top Bar (matching Lightroom Screenshots 201028, 201040, 201104)

        // 3. Top-Left Active Sub-Mask Quick Parameter Strip (Brush / Radial / ColorRange / LuminanceRange)
        if (activeSubMask != null && (activeSubMask is SubMask.Brush || activeSubMask is SubMask.RadialGradient || activeSubMask is SubMask.ColorRange || activeSubMask is SubMask.LuminanceRange)) {
            ActiveSubMaskMiniControls(
                subMask = activeSubMask,
                brushSize = maskBrushSize,
                brushFeather = maskBrushFeather,
                brushFlow = maskBrushFlow,
                isBrushErasing = isMaskBrushErasing,
                onBrushSizeChange = { viewModel.setMaskBrushSize(it) },
                onBrushFeatherChange = { viewModel.setMaskBrushFeather(it) },
                onBrushFlowChange = { viewModel.setMaskBrushFlow(it) },
                onToggleBrushErase = { viewModel.setMaskBrushErasing(!isMaskBrushErasing) },
                onRadialFeatherChange = { f ->
                    viewModel.updateActiveSubMask { if (it is SubMask.RadialGradient) it.copy(feather = f) else it }
                },
                onColorToleranceChange = { tol ->
                    viewModel.updateActiveSubMask { if (it is SubMask.ColorRange) it.copy(hueTolerance = tol) else it }
                },
                onLumaRangeChange = { mn, mx ->
                    viewModel.updateActiveSubMask { if (it is SubMask.LuminanceRange) it.copy(minLuminance = mn, maxLuminance = mx) else it }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 10.dp, start = 12.dp)
            )
        }

        // 4. Left Floating Action Pill: Invert + Delete (Exact Match to Screenshot 201028!)
        if (activeMask != null) {
            val isInverted = activeMask.subMasks.firstOrNull()?.isInverted == true
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color(0xE6202022),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF3A3A3E)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isInverted) Color(0xFF1473E6) else Color.Transparent)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.invertMaskLayer(activeMaskIndex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_mask_invert_box),
                                contentDescription = "Invert Mask",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.deleteMaskLayer(activeMaskIndex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lr_mask_delete),
                                contentDescription = "Delete Mask",
                                tint = Color(0xFFE0E0E4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                // Subtle white vertical grab handle beside left pill (exact match to Screenshot 201028)
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }
        }

        // 5. Floating "Add to / Subtract from" Callout Card (Exact Match to Screenshot 201040!)
        if (showAddSubtractCallout && activeMask != null) {
            val maskLabel = activeMask.name.ifBlank { "Mask ${activeMaskIndex + 1}" }
            Surface(
                color = Color(0xF5242427),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF424247)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 86.dp)
                    .width(210.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAddSubtractCallout = false
                                pendingCombineMode = MaskCombineMode.ADD
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_mask_add_to),
                            contentDescription = "Add",
                            tint = Color(0xFFD0D0D5),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Add to \"$maskLabel\"",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    HorizontalDivider(color = Color(0xFF343438), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAddSubtractCallout = false
                                pendingCombineMode = MaskCombineMode.SUBTRACT
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_mask_subtract_from),
                            contentDescription = "Subtract",
                            tint = Color(0xFFD0D0D5),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Subtract from \"$maskLabel\"",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 6. Right Floating Mask Layers Dock with B&W Silhouette Thumbnails + `↳` Sub-Mask Tree + `+/-`
        // (Exact Match to Screenshots 083741, 201028, 201040!)
        AuthenticFloatingMaskDock(
            masks = masks,
            activeMaskIndex = activeMaskIndex,
            activeSubMaskIndex = activeSubMaskIndex,
            previewBitmaps = maskPreviewBitmaps,
            onSelectMask = {
                showAddSubtractCallout = false
                viewModel.selectMaskLayer(it)
            },
            onSelectSubMask = { viewModel.selectSubMask(it) },
            onToggleLayer = { idx ->
                val layer = masks.getOrNull(idx)
                if (layer != null) viewModel.updateMaskLayer(layer.copy(enabled = !layer.enabled))
            },
            onInvertMask = { viewModel.invertMaskLayer(it) },
            onDuplicateMask = { viewModel.duplicateMaskLayer(it) },
            onDeleteMask = { viewModel.deleteMaskLayer(it) },
            onAddSubtractClick = { showAddSubtractCallout = !showAddSubtractCallout },
            onAddNewMaskClick = {
                showAddSubtractCallout = false
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onAddNewMaskClick()
            },
            modifier = Modifier
                .align(if (masks.isEmpty()) Alignment.BottomEnd else Alignment.CenterEnd)
                .padding(bottom = if (masks.isEmpty()) 16.dp else 0.dp)
        )

        // Sub-mask Add / Subtract picker bottom sheet
        pendingCombineMode?.let { mode ->
            CombineSubMaskBottomSheet(
                initialCombineMode = mode,
                onDismissRequest = { pendingCombineMode = null },
                onSelectSubMask = { newSub ->
                    viewModel.addSubMaskToActive(newSub)
                    pendingCombineMode = null
                }
            )
        }
    }
}

/**
 * Pixel-Aligned Interactive Mask Overlay Canvas with Authentic Adobe Lightroom Grids & Draggable Handles:
 *  - Real Ruby-Red / Green / White-on-Black evaluated silhouette overlay
 *  - Linear Gradient 3-line parallel guide + draggable start/mid/end handles
 *  - Radial Gradient outer solid ellipse + inner dashed feather ellipse + 4 resize handles + center pin
 *  - Smooth interpolated Brush ribbon (zero polka-dot beads!) + live brush cursor
 *  - Color Range draggable Eyedropper loupe target
 *  - Subject / Sky / Background glowing blue-and-white Centroid Pin
 */
@Composable
private fun InteractiveMaskOverlayCanvas(
    crop: CropAdjustments,
    imageWidth: Int,
    imageHeight: Int,
    mask: MaskLayer,
    activeSubMask: SubMask?,
    previewAlphaBitmap: Bitmap?,
    showOverlay: Boolean,
    overlayMode: String,
    brushSize: Float,
    brushFeather: Float,
    isBrushErasing: Boolean,
    onLinearGradientDrag: (Float, Float, Float, Float) -> Unit,
    onRadialGradientDrag: (Float, Float, Float, Float) -> Unit,
    onBrushPoint: (Float, Float, Boolean) -> Unit,
    onColorSample: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val curCrop by rememberUpdatedState(crop)
    val curSub by rememberUpdatedState(activeSubMask)
    val curOnLinear by rememberUpdatedState(onLinearGradientDrag)
    val curOnRadial by rememberUpdatedState(onRadialGradientDrag)
    val curOnBrush by rememberUpdatedState(onBrushPoint)
    val curOnColor by rememberUpdatedState(onColorSample)

    var activeBrushCursor by remember { mutableStateOf<Offset?>(null) }
    var colorPickerNorm by remember(mask.id) { mutableStateOf(Offset(0.5f, 0.5f)) }
    // Drag target for interactive gradient handles: 0=none, 1=center, 2=start/top, 3=end/bottom, 4=left, 5=right
    var dragTarget by remember { mutableIntStateOf(0) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }

    val tintColor = when (overlayMode) {
        "Green" -> Color(0x8800E676)
        "WhiteOnBlack" -> Color.White
        else -> Color(0x8CFF0033) // Authentic Lightroom Ruby Red (#FF0033 at ~55% alpha)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(mask.id, activeSubMask?.id, imageWidth, imageHeight) {
                detectTapGestures { pos ->
                    val disp = CropGeometryEngine.computeImageDisplayRect(
                        size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight, curCrop, false
                    )
                    if (disp.width <= 1f || disp.height <= 1f) return@detectTapGestures
                    val nx = ((pos.x - disp.left) / disp.width).coerceIn(0f, 1f)
                    val ny = ((pos.y - disp.top) / disp.height).coerceIn(0f, 1f)
                    when (curSub) {
                        is SubMask.Brush -> curOnBrush(nx, ny, true)
                        is SubMask.ColorRange -> {
                            colorPickerNorm = Offset(nx, ny)
                            curOnColor(nx, ny)
                        }
                        else -> Unit
                    }
                }
            }
            .pointerInput(mask.id, activeSubMask?.id, imageWidth, imageHeight) {
                detectDragGestures(
                    onDragStart = { start ->
                        val disp = CropGeometryEngine.computeImageDisplayRect(
                            size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight, curCrop, false
                        )
                        if (disp.width <= 1f || disp.height <= 1f) return@detectDragGestures
                        val nx = ((start.x - disp.left) / disp.width).coerceIn(0f, 1f)
                        val ny = ((start.y - disp.top) / disp.height).coerceIn(0f, 1f)
                        dragStartOffset = Offset(nx, ny)

                        when (val sub = curSub) {
                            is SubMask.Brush -> {
                                activeBrushCursor = start
                                curOnBrush(nx, ny, true)
                            }
                            is SubMask.LinearGradient -> {
                                val pStart = Offset(disp.left + sub.startX * disp.width, disp.top + sub.startY * disp.height)
                                val pEnd = Offset(disp.left + sub.endX * disp.width, disp.top + sub.endY * disp.height)
                                val pMid = Offset((pStart.x + pEnd.x) * 0.5f, (pStart.y + pEnd.y) * 0.5f)
                                val dStart = hypot(start.x - pStart.x, start.y - pStart.y)
                                val dEnd = hypot(start.x - pEnd.x, start.y - pEnd.y)
                                val dMid = hypot(start.x - pMid.x, start.y - pMid.y)
                                dragTarget = when {
                                    dMid < 64f && dMid <= dStart && dMid <= dEnd -> 1
                                    dStart < 72f && dStart <= dEnd -> 2
                                    dEnd < 72f -> 3
                                    else -> 1
                                }
                            }
                            is SubMask.RadialGradient -> {
                                val cx = disp.left + sub.centerX * disp.width
                                val cy = disp.top + sub.centerY * disp.height
                                val rxPx = sub.radiusX * disp.width
                                val ryPx = sub.radiusY * disp.height
                                val dCenter = hypot(start.x - cx, start.y - cy)
                                val dTop = hypot(start.x - cx, start.y - (cy - ryPx))
                                val dBot = hypot(start.x - cx, start.y - (cy + ryPx))
                                val dLeft = hypot(start.x - (cx - rxPx), start.y - cy)
                                val dRight = hypot(start.x - (cx + rxPx), start.y - cy)
                                dragTarget = when {
                                    dCenter < 56f -> 1
                                    dTop < 56f || dBot < 56f -> 2
                                    dLeft < 56f || dRight < 56f -> 4
                                    else -> 1
                                }
                            }
                            is SubMask.ColorRange -> {
                                colorPickerNorm = Offset(nx, ny)
                                curOnColor(nx, ny)
                            }
                            else -> Unit
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        val disp = CropGeometryEngine.computeImageDisplayRect(
                            size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight, curCrop, false
                        )
                        if (disp.width <= 1f || disp.height <= 1f) return@detectDragGestures
                        val nx = ((pos.x - disp.left) / disp.width).coerceIn(0.01f, 0.99f)
                        val ny = ((pos.y - disp.top) / disp.height).coerceIn(0.01f, 0.99f)

                        when (val sub = curSub) {
                            is SubMask.Brush -> {
                                activeBrushCursor = pos
                                curOnBrush(nx, ny, false)
                            }
                            is SubMask.LinearGradient -> {
                                when (dragTarget) {
                                    2 -> curOnLinear(nx, ny, sub.endX, sub.endY)
                                    3 -> curOnLinear(sub.startX, sub.startY, nx, ny)
                                    else -> {
                                        val dx = nx - dragStartOffset.x
                                        val dy = ny - dragStartOffset.y
                                        dragStartOffset = Offset(nx, ny)
                                        curOnLinear(
                                            (sub.startX + dx).coerceIn(0f, 1f),
                                            (sub.startY + dy).coerceIn(0f, 1f),
                                            (sub.endX + dx).coerceIn(0f, 1f),
                                            (sub.endY + dy).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                            is SubMask.RadialGradient -> {
                                when (dragTarget) {
                                    2 -> {
                                        val newRy = abs(ny - sub.centerY).coerceIn(0.04f, 0.75f)
                                        curOnRadial(sub.centerX, sub.centerY, sub.radiusX, newRy)
                                    }
                                    4 -> {
                                        val newRx = abs(nx - sub.centerX).coerceIn(0.04f, 0.75f)
                                        curOnRadial(sub.centerX, sub.centerY, newRx, sub.radiusY)
                                    }
                                    else -> curOnRadial(nx, ny, sub.radiusX, sub.radiusY)
                                }
                            }
                            is SubMask.ColorRange -> {
                                colorPickerNorm = Offset(nx, ny)
                                curOnColor(nx, ny)
                            }
                            else -> Unit
                        }
                    },
                    onDragEnd = {
                        activeBrushCursor = null
                        dragTarget = 0
                    },
                    onDragCancel = {
                        activeBrushCursor = null
                        dragTarget = 0
                    }
                )
            }
    ) {
        // LAYER A: Real Evaluated Ruby-Red / Green / White-on-Black Mask Alpha Overlay (Pixel-Aligned!)
        if (showOverlay) {
            val imgBitmap = remember(previewAlphaBitmap) { previewAlphaBitmap?.asImageBitmap() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val disp = CropGeometryEngine.computeImageDisplayRect(
                    size.width, size.height, imageWidth, imageHeight, crop, false
                )
                if (disp.width <= 1f || disp.height <= 1f) return@Canvas

                if (overlayMode == "WhiteOnBlack") {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.85f),
                        topLeft = Offset(disp.left, disp.top),
                        size = Size(disp.width, disp.height)
                    )
                }

                if (imgBitmap != null) {
                    drawImage(
                        image = imgBitmap,
                        dstOffset = IntOffset(disp.left.roundToInt(), disp.top.roundToInt()),
                        dstSize = IntSize(disp.width.roundToInt().coerceAtLeast(1), disp.height.roundToInt().coerceAtLeast(1)),
                        colorFilter = ColorFilter.tint(tintColor, BlendMode.SrcIn)
                    )
                } else {
                    // Instant responsive preview before async BiRefNet/evaluator finishes
                    when (val sub = activeSubMask) {
                        is SubMask.LinearGradient -> {
                            val s = Offset(disp.left + sub.startX * disp.width, disp.top + sub.startY * disp.height)
                            val e = Offset(disp.left + sub.endX * disp.width, disp.top + sub.endY * disp.height)
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = if (sub.isInverted) listOf(Color.Transparent, tintColor) else listOf(tintColor, Color.Transparent),
                                    start = s,
                                    end = e
                                ),
                                topLeft = Offset(disp.left, disp.top),
                                size = Size(disp.width, disp.height)
                            )
                        }
                        is SubMask.RadialGradient -> {
                            val c = Offset(disp.left + sub.centerX * disp.width, disp.top + sub.centerY * disp.height)
                            val r = max(sub.radiusX * disp.width, sub.radiusY * disp.height).coerceAtLeast(24f)
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = if (sub.isInverted) listOf(Color.Transparent, tintColor) else listOf(tintColor, Color.Transparent),
                                    center = c,
                                    radius = r
                                ),
                                topLeft = Offset(disp.left, disp.top),
                                size = Size(disp.width, disp.height)
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        // LAYER B: Authentic Interactive Mask Overlay Grids, Dashed Guides & Draggable Handles!
        val activePinIconRes = activeSubMask?.let { getSubMaskIconRes(it) } ?: R.drawable.ic_lr_mask_subject
        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val disp = CropGeometryEngine.computeImageDisplayRect(
                size.width, size.height, imageWidth, imageHeight, crop, false
            )
            if (disp.width <= 1f || disp.height <= 1f) return@Canvas
            val minDisp = min(disp.width, disp.height).coerceAtLeast(1f)

            when (val sub = activeSubMask) {
                is SubMask.LinearGradient -> {
                    val pStart = Offset(disp.left + sub.startX * disp.width, disp.top + sub.startY * disp.height)
                    val pEnd = Offset(disp.left + sub.endX * disp.width, disp.top + sub.endY * disp.height)
                    val pMid = Offset((pStart.x + pEnd.x) * 0.5f, (pStart.y + pEnd.y) * 0.5f)
                    val vx = pEnd.x - pStart.x
                    val vy = pEnd.y - pStart.y
                    val len = hypot(vx, vy).coerceAtLeast(1f)
                    // Perpendicular unit vector spanning across the photo
                    val px = -vy / len
                    val py = vx / len
                    val halfSpan = max(disp.width, disp.height) * 0.65f

                    // Helper to draw shadowed crisp white guide line
                    fun drawGuideLine(center: Offset, dashed: Boolean) {
                        val a = Offset(center.x - px * halfSpan, center.y - py * halfSpan)
                        val b = Offset(center.x + px * halfSpan, center.y + py * halfSpan)
                        drawLine(Color.Black.copy(alpha = 0.45f), a, b, strokeWidth = 3.dp.toPx(), pathEffect = if (dashed) dashEffect else null)
                        drawLine(Color.White.copy(alpha = 0.92f), a, b, strokeWidth = 1.4.dp.toPx(), pathEffect = if (dashed) dashEffect else null)
                    }

                    // 3 Authentic Parallel Linear Gradient Guide Lines (Top 100%, Middle Dashed 50%, Bottom 0%)
                    drawGuideLine(pStart, dashed = false)
                    drawGuideLine(pMid, dashed = true)
                    drawGuideLine(pEnd, dashed = false)

                    // Orthogonal axis connector from start -> end
                    drawLine(Color.White.copy(alpha = 0.7f), pStart, pEnd, strokeWidth = 1.2.dp.toPx(), pathEffect = dashEffect)

                    // Start & End Drag Knobs
                    for (knob in listOf(pStart, pEnd)) {
                        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 7.dp.toPx(), center = knob)
                        drawCircle(Color.White, radius = 5.dp.toPx(), center = knob)
                    }
                    // Glowing Blue/White Center Pin
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = pMid)
                    drawCircle(Color(0xFF1473E6), radius = 8.5.dp.toPx(), center = pMid)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = pMid)
                }

                is SubMask.RadialGradient -> {
                    val cx = disp.left + sub.centerX * disp.width
                    val cy = disp.top + sub.centerY * disp.height
                    val rx = (sub.radiusX * disp.width).coerceAtLeast(18f)
                    val ry = (sub.radiusY * disp.height).coerceAtLeast(18f)
                    val featherRatio = (1f - (sub.feather / 100f).coerceIn(0f, 0.95f)).coerceIn(0.08f, 0.96f)
                    val irx = rx * featherRatio
                    val iry = ry * featherRatio

                    // Subtle center crosshair axes
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - rx, cy), Offset(cx + rx, cy), 1.dp.toPx(), pathEffect = dashEffect)
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx, cy - ry), Offset(cx, cy + ry), 1.dp.toPx(), pathEffect = dashEffect)

                    // Outer solid ellipse (0% / boundary)
                    drawOval(
                        color = Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawOval(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Inner dashed feather ellipse
                    drawOval(
                        color = Color.White.copy(alpha = 0.78f),
                        topLeft = Offset(cx - irx, cy - iry),
                        size = Size(irx * 2f, iry * 2f),
                        style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashEffect)
                    )

                    // 4 Cardinal Resize Handles (N, S, E, W)
                    val handles = listOf(
                        Offset(cx, cy - ry),
                        Offset(cx, cy + ry),
                        Offset(cx - rx, cy),
                        Offset(cx + rx, cy)
                    )
                    handles.forEach { h ->
                        drawCircle(Color.Black.copy(alpha = 0.55f), radius = 7.dp.toPx(), center = h)
                        drawCircle(Color.White, radius = 5.dp.toPx(), center = h)
                    }

                    // Center Blue/White Pin
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color(0xFF1473E6), radius = 8.5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(cx, cy))
                }

                is SubMask.Brush -> {
                    activeBrushCursor?.let { cur ->
                        val outerR = ((brushSize.coerceIn(4f, 100f) / 400f) * minDisp).coerceAtLeast(10f)
                        val innerR = outerR * (1f - (brushFeather / 100f) * 0.8f).coerceIn(0.15f, 0.95f)
                        val ringColor = if (isBrushErasing) Color(0xFF38A9FF) else Color.White
                        drawCircle(Color.Black.copy(alpha = 0.4f), radius = outerR + 1.dp.toPx(), center = cur, style = Stroke(3.dp.toPx()))
                        drawCircle(ringColor, radius = outerR, center = cur, style = Stroke(1.6.dp.toPx()))
                        drawCircle(ringColor.copy(alpha = 0.65f), radius = innerR, center = cur, style = Stroke(1.dp.toPx(), pathEffect = dashEffect))
                    }
                }

                is SubMask.ColorRange -> {
                    val c = Offset(disp.left + colorPickerNorm.x * disp.width, disp.top + colorPickerNorm.y * disp.height)
                    val sampledHueColor = Color.hsv(sub.targetHue.coerceIn(0f, 359f), 0.85f, 0.95f)
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = 22.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
                    drawCircle(sampledHueColor, radius = 20.dp.toPx(), center = c, style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, radius = 17.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                    // Center crosshair
                    drawLine(Color.White, Offset(c.x - 6.dp.toPx(), c.y), Offset(c.x + 6.dp.toPx(), c.y), 1.4.dp.toPx())
                    drawLine(Color.White, Offset(c.x, c.y - 6.dp.toPx()), Offset(c.x, c.y + 6.dp.toPx()), 1.4.dp.toPx())
                }

                else -> Unit
            }
        }

        // LAYER C: Glowing Blue/White Centroid Pin for AI Subject / Sky / Background / Luminance (Screenshots 201028 & 201104)
        if (activeSubMask is SubMask.AISubject || activeSubMask is SubMask.AISky || activeSubMask is SubMask.AIBackground || activeSubMask is SubMask.LuminanceRange) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(8.dp, CircleShape, ambientColor = Color(0xFF1473E6), spotColor = Color(0xFF1473E6))
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1473E6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(activePinIconRes),
                    contentDescription = "Active Mask Pin",
                    tint = Color(0xFF1473E6),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Compact Floating Quick-Parameter Pill for Active Sub-Mask (Brush Size/Feather/Flow, Radial Feather, Range sliders).
 */
@Composable
private fun ActiveSubMaskMiniControls(
    subMask: SubMask,
    brushSize: Float,
    brushFeather: Float,
    brushFlow: Float,
    isBrushErasing: Boolean,
    onBrushSizeChange: (Float) -> Unit,
    onBrushFeatherChange: (Float) -> Unit,
    onBrushFlowChange: (Float) -> Unit,
    onToggleBrushErase: () -> Unit,
    onRadialFeatherChange: (Float) -> Unit,
    onColorToleranceChange: (Float) -> Unit,
    onLumaRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(subMask.id) { mutableStateOf(false) }

    if (!isExpanded) {
        // Authentic compact Lightroom pill (unobtrusive over the photo; tap to adjust Size/Feather/Flow)
        val summaryLabel = when (subMask) {
            is SubMask.Brush -> "${if (isBrushErasing) "Eraser" else "Brush"} • ${brushSize.roundToInt()}"
            is SubMask.RadialGradient -> "Feather ${subMask.feather.roundToInt()}"
            is SubMask.ColorRange -> "Refine ${subMask.hueTolerance.roundToInt()}"
            is SubMask.LuminanceRange -> "Range ${subMask.minLuminance.roundToInt()}–${subMask.maxLuminance.roundToInt()}"
            else -> return
        }
        Surface(
            color = Color(0xD91E1E20),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.8.dp, Color(0xFF3E3E42)),
            modifier = modifier.clickable { isExpanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(getSubMaskIconRes(subMask)),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = summaryLabel,
                    color = Color(0xFFE3E3E3),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        return
    }

    Surface(
        color = Color(0xEE1A1A1D),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.8.dp, Color(0xFF3A3A3F)),
        modifier = modifier.width(190.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (subMask) {
                is SubMask.Brush -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Brush",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { isExpanded = false }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (isBrushErasing) Color(0xFF1473E6) else Color(0xFF2B2B30),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { onToggleBrushErase() }
                            ) {
                                Text(
                                    text = if (isBrushErasing) "Eraser" else "Paint",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Done",
                                color = Color(0xFF38A9FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { isExpanded = false }
                            )
                        }
                    }
                    MiniInlineSlider("Size", brushSize, 4f..100f, onBrushSizeChange)
                    MiniInlineSlider("Feather", brushFeather, 0f..100f, onBrushFeatherChange)
                    MiniInlineSlider("Flow", brushFlow, 5f..100f, onBrushFlowChange)
                }
                is SubMask.RadialGradient -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Radial Gradient", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text("Done", color = Color(0xFF38A9FF), fontSize = 11.sp, modifier = Modifier.clickable { isExpanded = false })
                    }
                    MiniInlineSlider("Feather", subMask.feather, 0f..100f, onRadialFeatherChange)
                }
                is SubMask.ColorRange -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Color Range", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text("Done", color = Color(0xFF38A9FF), fontSize = 11.sp, modifier = Modifier.clickable { isExpanded = false })
                    }
                    MiniInlineSlider("Refine", subMask.hueTolerance, 5f..90f, onColorToleranceChange)
                }
                is SubMask.LuminanceRange -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Luminance Range", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text("Done", color = Color(0xFF38A9FF), fontSize = 11.sp, modifier = Modifier.clickable { isExpanded = false })
                    }
                    MiniInlineSlider("Min", subMask.minLuminance, 0f..95f) { mn ->
                        onLumaRangeChange(mn, max(mn + 5f, subMask.maxLuminance))
                    }
                    MiniInlineSlider("Max", subMask.maxLuminance, 5f..100f) { mx ->
                        onLumaRangeChange(min(subMask.minLuminance, mx - 5f), mx)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MiniInlineSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFD0D0D4), fontSize = 11.sp)
            Text("${value.roundToInt()}", color = Color.White, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start).coerceAtLeast(1f)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(range) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val nx = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onChange(range.start + nx * (range.endInclusive - range.start))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                val cy = size.height / 2f
                val tx = fraction * size.width
                val r = 7.dp.toPx()
                drawLine(Color(0xFF5A5A5E), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx())
                drawLine(Color.White, Offset(0f, cy), Offset((tx - r - 2.dp.toPx()).coerceAtLeast(0f), cy), strokeWidth = 2.dp.toPx())
                drawCircle(Color(0xFF1A1A1D), radius = r, center = Offset(tx, cy))
                drawCircle(Color.White, radius = r, center = Offset(tx, cy), style = Stroke(width = 1.8.dp.toPx()))
            }
        }
    }
}

/**
 * Pixel-Accurate Adobe Lightroom Mobile Right-Edge Floating Mask Layers Dock
 * (Reference Screenshots 083741, 201028, 201040, 201104).
 *
 * Features:
 *  - Live Black-and-White Silhouette Mask Thumbnails (`previewBitmaps[mask.id]`)
 *  - Nested `↳` Sub-Mask tree row with blue active outline + bottom-right corner triangle
 *  - Authentic `+/-` (`ic_lr_mask_add_subtract`) button opening the Add/Subtract callout
 */
@Composable
fun AuthenticFloatingMaskDock(
    masks: List<MaskLayer>,
    activeMaskIndex: Int,
    activeSubMaskIndex: Int,
    previewBitmaps: Map<String, Bitmap>,
    onSelectMask: (Int) -> Unit,
    onSelectSubMask: (Int) -> Unit,
    onToggleLayer: (Int) -> Unit,
    onInvertMask: (Int) -> Unit,
    onDuplicateMask: (Int) -> Unit,
    onDeleteMask: (Int) -> Unit,
    onAddSubtractClick: () -> Unit,
    onAddNewMaskClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    if (masks.isEmpty()) {
        // Resting State (Screenshot 083741)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White)
            )
            Surface(
                onClick = onAddNewMaskClick,
                color = Color(0xFF202022),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                border = BorderStroke(0.8.dp, Color(0xFF343438)),
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
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
        }
    } else if (!isExpanded) {
        // Collapsed Single-Thumbnail Pill (Screenshot 201104)
        val activeLayer = masks.getOrNull(activeMaskIndex) ?: masks.first()
        val thumbBmp = remember(previewBitmaps[activeLayer.id]) { previewBitmaps[activeLayer.id]?.asImageBitmap() }
        val iconRes = activeLayer.subMasks.firstOrNull()?.let { getSubMaskIconRes(it) } ?: R.drawable.ic_lr_mask_subject

        Surface(
            color = Color(0xF01C1C1E),
            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
            border = BorderStroke(0.8.dp, Color(0xFF36363A)),
            shadowElevation = 8.dp,
            modifier = modifier.clickable { isExpanded = true }
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                MaskBwThumbnailBox(
                    thumbBitmap = thumbBmp,
                    fallbackIconRes = iconRes,
                    isSelected = true,
                    sizeDp = 44
                )
            }
        }
    } else {
        // Expanded Mask Stack + Nested `↳` Sub-Mask Tree + `+/-` (Screenshots 201028 & 201040!)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .width(2.5.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable { isExpanded = false }
            )

            Surface(
                color = Color(0xF21B1B1D),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                border = BorderStroke(0.8.dp, Color(0xFF343438)),
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Blue (+) Circle Button (matches Screenshot 201028!)
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
                            contentDescription = "Create new Mask",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Scrollable Mask Layers + Active Sub-Mask Tree
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        masks.forEachIndexed { idx, mask ->
                            val isSelected = (idx == activeMaskIndex)
                            var showMenu by remember { mutableStateOf(false) }
                            val thumbBmp = remember(previewBitmaps[mask.id]) { previewBitmaps[mask.id]?.asImageBitmap() }
                            val primaryIcon = mask.subMasks.firstOrNull()?.let { getSubMaskIconRes(it) } ?: R.drawable.ic_lr_mask_subject

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF0E0E10) else Color.Transparent)
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Main B&W Silhouette Thumbnail (tap selects; tap when selected opens context menu)
                                Box {
                                    MaskBwThumbnailBox(
                                        thumbBitmap = thumbBmp,
                                        fallbackIconRes = primaryIcon,
                                        isSelected = isSelected,
                                        sizeDp = 46,
                                        modifier = Modifier.clickable {
                                            if (isSelected) showMenu = true else onSelectMask(idx)
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier.background(Color(0xFF252528))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (mask.enabled) "Hide Mask" else "Show Mask", color = Color.White, fontSize = 13.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    painterResource(if (mask.enabled) R.drawable.ic_lr_mask_eye_off else R.drawable.ic_lr_mask_eye_on),
                                                    null, tint = Color.White, modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            onClick = { showMenu = false; onToggleLayer(idx) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Invert", color = Color.White, fontSize = 13.sp) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_lr_mask_invert_box), null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                                            onClick = { showMenu = false; onInvertMask(idx) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Duplicate", color = Color.White, fontSize = 13.sp) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_lr_mask_duplicate), null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                                            onClick = { showMenu = false; onDuplicateMask(idx) }
                                        )
                                        HorizontalDivider(color = Color(0xFF3A3A3E), thickness = 0.5.dp)
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = Color(0xFFFF5252), fontSize = 13.sp) },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_lr_mask_delete), null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp)) },
                                            onClick = { showMenu = false; onDeleteMask(idx) }
                                        )
                                    }
                                }

                                // Nested Sub-Mask Tree (`↳` + Sub-Mask Tiles + `+/-` button — exact match to Screenshot 201028!)
                                if (isSelected) {
                                    mask.subMasks.forEachIndexed { subIdx, sub ->
                                        val isSubSelected = (subIdx == activeSubMaskIndex.coerceIn(0, mask.subMasks.lastIndex))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text("↳", color = Color(0xFF8C8C92), fontSize = 11.sp)
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF28282C))
                                                    .border(
                                                        width = if (isSubSelected) 1.6.dp else 0.8.dp,
                                                        color = if (isSubSelected) Color(0xFF38A9FF) else Color(0xFF424248),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable { onSelectSubMask(subIdx) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(getSubMaskIconRes(sub)),
                                                    contentDescription = getSubMaskDisplayName(sub),
                                                    tint = if (sub.combineMode == MaskCombineMode.SUBTRACT) Color(0xFFFF6B6B) else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                // Tiny bottom-right corner notch matching Screenshot 201028
                                                Canvas(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(2.dp)
                                                        .size(5.dp)
                                                ) {
                                                    val p = Path().apply {
                                                        moveTo(size.width, 0f)
                                                        lineTo(size.width, size.height)
                                                        lineTo(0f, size.height)
                                                        close()
                                                    }
                                                    drawPath(p, Color.White)
                                                }
                                            }
                                        }
                                    }

                                    // Authentic `+/-` Add/Subtract button (Screenshot 201028 & 201040!)
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable { onAddSubtractClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_mask_add_subtract),
                                            contentDescription = "Add or Subtract Sub-Mask",
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders an authentic Black-and-White Mask Silhouette Thumbnail (matching Screenshot 201028 & 201104).
 */
@Composable
private fun MaskBwThumbnailBox(
    thumbBitmap: ImageBitmap?,
    fallbackIconRes: Int,
    isSelected: Boolean,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black)
            .border(
                width = if (isSelected) 1.6.dp else 0.8.dp,
                color = if (isSelected) Color.White else Color(0xFF4A4A50),
                shape = RoundedCornerShape(6.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap,
                contentDescription = "Mask Silhouette Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(fallbackIconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        }
    }
}
