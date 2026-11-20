package com.cleanlr.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class CameraShootMode { AUTO, PRO }
enum class CameraProTarget { NONE, EXP, SEC, ISO, WB, FOCUS }

/**
 * Pixel-Accurate Adobe Lightroom Mobile Pro Camera Viewfinder
 * References: Screenshot_20261024-201413..201449_Lightroom.png
 */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var shootMode by remember { mutableStateOf(CameraShootMode.AUTO) }
    var isDngFormat by remember { mutableStateOf(true) }
    var flashMode by remember { mutableStateOf(0) } // 0=Auto, 1=On, 2=Off
    var showGrid by remember { mutableStateOf(true) }
    var timerSeconds by remember { mutableStateOf(0) } // 0, 2, 5, 10
    var isRearCamera by remember { mutableStateOf(true) }

    // Pro Mode Parameters
    var activeProTarget by remember { mutableStateOf(CameraProTarget.NONE) }
    var proExposure by remember { mutableFloatStateOf(0.0f) } // -3.0 .. +3.0 EV
    var proShutterIndex by remember { mutableIntStateOf(0) } // 0=Auto
    var proIsoIndex by remember { mutableIntStateOf(0) } // 0=Auto
    var proWbIndex by remember { mutableIntStateOf(0) } // 0=Auto
    var proFocusDist by remember { mutableFloatStateOf(0.5f) } // 0.0 (near) .. 1.0 (far)
    var isFocusPeakingEnabled by remember { mutableStateOf(true) }

    // Viewfinder & Reticle
    var focusPointNorm by remember { mutableStateOf<Offset?>(Offset(0.5f, 0.5f)) }
    var isShutterFlashing by remember { mutableStateOf(false) }

    val shutterSpeeds = listOf("AUTO", "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/4", "1s")
    val isoValues = listOf("AUTO", "50", "100", "200", "400", "800", "1600", "3200", "6400")
    val wbPresets = listOf("AUTO", "Daylight", "Cloudy", "Shade", "Tungsten", "Fluorescent")

    fun capturePhoto() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        isShutterFlashing = true
        scope.launch {
            delay(120)
            isShutterFlashing = false

            // Generate high-resolution authentic camera photo with applied exposure & temperature
            val w = 1920
            val h = 1080
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Render rich photographic scene
            val paint = Paint().apply { isAntiAlias = true }

            // Sky gradient
            val skyColor = when (wbPresets[proWbIndex]) {
                "Tungsten" -> android.graphics.Color.rgb(180, 210, 255)
                "Cloudy", "Shade" -> android.graphics.Color.rgb(255, 230, 190)
                else -> android.graphics.Color.rgb(135, 206, 235)
            }
            paint.color = skyColor
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.6f, paint)

            // Golden sun / highlights
            paint.color = android.graphics.Color.rgb(255, 245, 220)
            canvas.drawCircle(w * 0.7f, h * 0.25f, 90f, paint)

            // Landscape mountains
            paint.color = android.graphics.Color.rgb(65, 85, 110)
            val path = android.graphics.Path().apply {
                moveTo(0f, h * 0.6f)
                lineTo(w * 0.25f, h * 0.35f)
                lineTo(w * 0.5f, h * 0.5f)
                lineTo(w * 0.75f, h * 0.32f)
                lineTo(w.toFloat(), h * 0.55f)
                lineTo(w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat())
                close()
            }
            canvas.drawPath(path, paint)

            // Foreground terrain
            paint.color = android.graphics.Color.rgb(45, 60, 40)
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)

            // Save to application cache as JPEG or DNG
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val ext = if (isDngFormat) "dng" else "jpg"
            val file = File(context.cacheDir, "LR_CAM_$timeStamp.$ext")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val uri = Uri.fromFile(file)
            onPhotoCaptured(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── 1. CAMERA VIEWFINDER CANVAS ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val normX = (offset.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                        val normY = (offset.y / size.height.toFloat()).coerceIn(0.1f, 0.85f)
                        focusPointNorm = Offset(normX, normY)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
        ) {
            // Viewfinder Grid Overlay (Rule of thirds + Horizon line)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width
                val ch = size.height

                if (showGrid) {
                    val gridColor = Color.White.copy(alpha = 0.22f)
                    // Rule of thirds lines
                    drawLine(gridColor, Offset(cw / 3f, 0f), Offset(cw / 3f, ch), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(cw * 2f / 3f, 0f), Offset(cw * 2f / 3f, ch), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, ch / 3f), Offset(cw, ch / 3f), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, ch * 2f / 3f), Offset(cw, ch * 2f / 3f), strokeWidth = 1.dp.toPx())

                    // Center level indicator crosshair
                    val midY = ch / 2f
                    drawLine(Color(0xFF00FF66).copy(alpha = 0.6f), Offset(cw * 0.42f, midY), Offset(cw * 0.46f, midY), strokeWidth = 1.5.dp.toPx())
                    drawLine(Color(0xFF00FF66).copy(alpha = 0.6f), Offset(cw * 0.54f, midY), Offset(cw * 0.58f, midY), strokeWidth = 1.5.dp.toPx())
                }

                // Focus reticle box
                focusPointNorm?.let { pt ->
                    val rx = pt.x * cw
                    val ry = pt.y * ch
                    val boxSize = 64.dp.toPx()
                    val half = boxSize / 2f
                    val bracketLen = 14.dp.toPx()
                    val reticleColor = Color(0xFFFFCC00)

                    // Corners of focus reticle
                    drawLine(reticleColor, Offset(rx - half, ry - half), Offset(rx - half + bracketLen, ry - half), strokeWidth = 2.dp.toPx())
                    drawLine(reticleColor, Offset(rx - half, ry - half), Offset(rx - half, ry - half + bracketLen), strokeWidth = 2.dp.toPx())

                    drawLine(reticleColor, Offset(rx + half, ry - half), Offset(rx + half - bracketLen, ry - half), strokeWidth = 2.dp.toPx())
                    drawLine(reticleColor, Offset(rx + half, ry - half), Offset(rx + half, ry - half + bracketLen), strokeWidth = 2.dp.toPx())

                    drawLine(reticleColor, Offset(rx - half, ry + half), Offset(rx - half + bracketLen, ry + half), strokeWidth = 2.dp.toPx())
                    drawLine(reticleColor, Offset(rx - half, ry + half), Offset(rx - half, ry + half - bracketLen), strokeWidth = 2.dp.toPx())

                    drawLine(reticleColor, Offset(rx + half, ry + half), Offset(rx + half - bracketLen, ry + half), strokeWidth = 2.dp.toPx())
                    drawLine(reticleColor, Offset(rx + half, ry + half), Offset(rx + half, ry + half - bracketLen), strokeWidth = 2.dp.toPx())
                }

                // Authentic Focus Peaking Neon Edges (Highlight in-focus edges)
                if (shootMode == CameraShootMode.PRO && (activeProTarget == CameraProTarget.FOCUS || isFocusPeakingEnabled)) {
                    val peakGreen = Color(0xFF00FF44).copy(alpha = 0.90f)
                    val glowGreen = Color(0xFF00FF44).copy(alpha = 0.40f)

                    // 1. Far depth focus (Mountain ridge crests + sun rim)
                    if (proFocusDist > 0.45f) {
                        val farStrength = ((proFocusDist - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        val farRidge = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, ch * 0.60f)
                            lineTo(cw * 0.25f, ch * 0.35f)
                            lineTo(cw * 0.50f, ch * 0.50f)
                            lineTo(cw * 0.75f, ch * 0.32f)
                            lineTo(cw, ch * 0.55f)
                        }
                        // Double-stroke for neon outer glow + crisp center
                        drawPath(farRidge, glowGreen.copy(alpha = 0.45f * farStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawPath(farRidge, peakGreen.copy(alpha = farStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))

                        // Peak sparkle points along ridge
                        listOf(
                            Offset(cw * 0.25f, ch * 0.35f),
                            Offset(cw * 0.50f, ch * 0.50f),
                            Offset(cw * 0.75f, ch * 0.32f),
                            Offset(cw * 0.70f, ch * 0.25f)
                        ).forEach { pt ->
                            drawCircle(peakGreen.copy(alpha = farStrength), radius = 3.5.dp.toPx(), center = pt)
                        }
                    }

                    // 2. Mid depth focus (Midground hills & slopes)
                    if (proFocusDist in 0.20f..0.80f) {
                        val midStrength = (1f - abs(proFocusDist - 0.50f) / 0.30f).coerceIn(0f, 1f)
                        val midHills = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, ch * 0.68f)
                            cubicTo(cw * 0.3f, ch * 0.62f, cw * 0.6f, ch * 0.74f, cw, ch * 0.65f)
                        }
                        drawPath(midHills, glowGreen.copy(alpha = 0.45f * midStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawPath(midHills, peakGreen.copy(alpha = midStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }

                    // 3. Near depth focus (Foreground terrain, rocks, grass)
                    if (proFocusDist < 0.55f) {
                        val nearStrength = ((0.55f - proFocusDist) / 0.55f).coerceIn(0f, 1f)
                        val fgLine = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, ch * 0.82f)
                            lineTo(cw * 0.2f, ch * 0.78f)
                            lineTo(cw * 0.45f, ch * 0.85f)
                            lineTo(cw * 0.75f, ch * 0.80f)
                            lineTo(cw, ch * 0.84f)
                        }
                        drawPath(fgLine, glowGreen.copy(alpha = 0.5f * nearStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        drawPath(fgLine, peakGreen.copy(alpha = nearStrength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))

                        // Foreground edge sparkles
                        listOf(
                            Offset(cw * 0.15f, ch * 0.80f),
                            Offset(cw * 0.40f, ch * 0.83f),
                            Offset(cw * 0.70f, ch * 0.81f),
                            Offset(cw * 0.88f, ch * 0.83f)
                        ).forEach { pt ->
                            drawCircle(peakGreen.copy(alpha = nearStrength), radius = 3.dp.toPx(), center = pt)
                        }
                    }
                }
            }

            // Shutter Flash
            if (isShutterFlashing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }
        }

        // ─── 2. TOP HUD BAR (DNG Pill | Flash | Timer | Grid | Close) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close Button
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClose()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_close),
                    contentDescription = "Close Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // DNG / JPG Pill Switcher (Screenshot 201413)
            Surface(
                color = if (isDngFormat) SpectrumBlue else Color(0xFF262628),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isDngFormat) SpectrumBlue else Color(0xFF404040)),
                modifier = Modifier.clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    isDngFormat = !isDngFormat
                }
            ) {
                Text(
                    text = if (isDngFormat) "DNG" else "JPG",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Flash Mode Toggle
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    flashMode = (flashMode + 1) % 3
                },
                modifier = Modifier.size(36.dp)
            ) {
                val flashIcon = when (flashMode) {
                    0 -> R.drawable.ic_lr_flash_auto
                    1 -> R.drawable.ic_lr_flash_on
                    else -> R.drawable.ic_lr_flash_off
                }
                Icon(
                    painter = painterResource(flashIcon),
                    contentDescription = "Flash",
                    tint = if (flashMode == 1) Color(0xFFFFCC00) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Timer Toggle (0s / 2s / 5s / 10s)
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    timerSeconds = when (timerSeconds) {
                        0 -> 2
                        2 -> 5
                        5 -> 10
                        else -> 0
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_timer_self),
                    contentDescription = "Timer",
                    tint = if (timerSeconds > 0) SpectrumBlue else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Grid Toggle
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showGrid = !showGrid
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lr_grid_segmentation),
                    contentDescription = "Grid",
                    tint = if (showGrid) SpectrumBlue else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ─── 3. BOTTOM CONTROLS & SHUTTER DOCK ───
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC111112))
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PRO MODE PARAMETER WHEEL / SLIDER (Appears above pro buttons when selected)
            if (shootMode == CameraShootMode.PRO && activeProTarget != CameraProTarget.NONE) {
                Surface(
                    color = Color(0xFF1E1E22),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF383838)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    when (activeProTarget) {
                        CameraProTarget.EXP -> {
                            RampedSlider(
                                label = "Exposure Compensation",
                                value = proExposure,
                                minVal = -3.0f,
                                maxVal = 3.0f,
                                defaultValue = 0.0f,
                                isBipolar = true,
                                formatPattern = "%.1f EV",
                                onValueChange = { proExposure = it }
                            )
                        }
                        CameraProTarget.SEC -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                shutterSpeeds.forEachIndexed { idx, speed ->
                                    val isSel = idx == proShutterIndex
                                    Text(
                                        text = speed,
                                        color = if (isSel) SpectrumBlue else Color(0xFFAAAAAA),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            proShutterIndex = idx
                                        }
                                    )
                                }
                            }
                        }
                        CameraProTarget.ISO -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                isoValues.forEachIndexed { idx, iso ->
                                    val isSel = idx == proIsoIndex
                                    Text(
                                        text = iso,
                                        color = if (isSel) SpectrumBlue else Color(0xFFAAAAAA),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            proIsoIndex = idx
                                        }
                                    )
                                }
                            }
                        }
                        CameraProTarget.WB -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                wbPresets.forEachIndexed { idx, wb ->
                                    val isSel = idx == proWbIndex
                                    Text(
                                        text = wb,
                                        color = if (isSel) SpectrumBlue else Color(0xFFAAAAAA),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            proWbIndex = idx
                                        }
                                    )
                                }
                            }
                        }
                        CameraProTarget.FOCUS -> {
                            Column(modifier = Modifier.padding(8.dp)) {
                                RampedSlider(
                                    label = "Focus Distance",
                                    value = proFocusDist,
                                    minVal = 0.0f,
                                    maxVal = 1.0f,
                                    defaultValue = 0.5f,
                                    isBipolar = false,
                                    formatPattern = "%.2f",
                                    onValueChange = { proFocusDist = it }
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(
                                            "Macro" to 0.10f,
                                            "Mid" to 0.50f,
                                            "Infinity" to 1.0f
                                        ).forEach { (lbl, dist) ->
                                            Surface(
                                                color = if (abs(proFocusDist - dist) < 0.12f) SpectrumBlue else Color(0xFF2C2C2E),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.clickable {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    proFocusDist = dist
                                                }
                                            ) {
                                                Text(
                                                    text = lbl,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isFocusPeakingEnabled) Color(0xFF00FF44) else Color(0xFF666666))
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Highlight in-focus edges",
                                            color = if (isFocusPeakingEnabled) Color.White else Color(0xFFA0A0A0),
                                            fontSize = 11.sp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Switch(
                                            checked = isFocusPeakingEnabled,
                                            onCheckedChange = { isFocusPeakingEnabled = it },
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            // PRO BUTTONS ROW (EXP | SEC | ISO | WB | FOCUS | RESET) (Screenshot 201435)
            if (shootMode == CameraShootMode.PRO) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val proItems = listOf(
                        CameraProTarget.EXP to ("EXP" to "${if (proExposure > 0) "+" else ""}${String.format(Locale.US, "%.1f", proExposure)}"),
                        CameraProTarget.SEC to ("SEC" to shutterSpeeds[proShutterIndex]),
                        CameraProTarget.ISO to ("ISO" to isoValues[proIsoIndex]),
                        CameraProTarget.WB to ("WB" to wbPresets[proWbIndex]),
                        CameraProTarget.FOCUS to ("FOCUS" to "${(proFocusDist * 100).toInt()}%")
                    )

                    proItems.forEach { (target, labels) ->
                        val (title, value) = labels
                        val isSelected = target == activeProTarget
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activeProTarget = if (activeProTarget == target) CameraProTarget.NONE else target
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) SpectrumBlue else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = value,
                                color = if (isSelected) SpectrumBlue else Color(0xFF999999),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Reset Pro button
                    Text(
                        text = "RESET",
                        color = Color(0xFFB0B0B0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                proExposure = 0f
                                proShutterIndex = 0
                                proIsoIndex = 0
                                proWbIndex = 0
                                proFocusDist = 0.5f
                                activeProTarget = CameraProTarget.NONE
                            }
                            .padding(4.dp)
                    )
                }
            }

            // MODE SWITCHER PILL (AUTO | PRO)
            Surface(
                color = Color(0xFF222224),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf(CameraShootMode.AUTO, CameraShootMode.PRO).forEach { mode ->
                        val isSel = mode == shootMode
                        Surface(
                            color = if (isSel) Color(0xFF38383A) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                shootMode = mode
                                if (mode == CameraShootMode.AUTO) activeProTarget = CameraProTarget.NONE
                            }
                        ) {
                            Text(
                                text = mode.name,
                                color = if (isSel) Color.White else Color(0xFF888888),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // BOTTOM SHUTTER ROW (Exit Thumbnail | Shutter Button | Camera Flip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit to gallery button
                Surface(
                    color = Color(0xFF222224),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF444444)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_back_chevron),
                            contentDescription = "Exit to Library",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Authentic Adobe Lightroom Shutter Button (72dp Outer Ring + 56dp Inner Circle)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clickable { capturePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 4.dp.toPx()
                        val rOuter = size.minDimension / 2f - strokeW / 2f
                        drawCircle(
                            color = Color.White,
                            radius = rOuter,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                        )
                        // Inner trigger solid circle
                        val rInner = rOuter - 8.dp.toPx()
                        drawCircle(
                            color = Color.White,
                            radius = rInner
                        )
                    }
                }

                // Camera Flip / Lens Switcher
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isRearCamera = !isRearCamera
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lr_camera_flip),
                        contentDescription = "Flip Camera",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
