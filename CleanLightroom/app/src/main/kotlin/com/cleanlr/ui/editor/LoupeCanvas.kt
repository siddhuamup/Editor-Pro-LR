package com.cleanlr.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cleanlr.engine.HistogramData
import com.cleanlr.engine.HistogramExtractor
import com.cleanlr.engine.RenderPipeline
import com.cleanlr.engine.SplineInterpolator
import com.cleanlr.model.EditRecipe
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LoupeGLView(context: Context) : GLSurfaceView(context) {

    private val renderer = LoupeRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setBitmap(bitmap: Bitmap, onHistogramExtracted: (HistogramData) -> Unit) {
        renderer.onHistogramUpdated = { histo ->
            post { onHistogramExtracted(histo) }
        }
        queueEvent {
            renderer.loadBitmap(bitmap)
            requestRender()
        }
    }

    fun updateRecipe(recipe: EditRecipe, altClippingMode: Int, showOriginal: Boolean) {
        queueEvent {
            renderer.updateRecipe(recipe, altClippingMode, showOriginal)
            requestRender()
        }
    }
}

class LoupeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var pipeline: RenderPipeline? = null
    private var pendingBitmap: Bitmap? = null
    private var currentRecipe = EditRecipe()
    private var altClippingMode = 0
    private var showOriginal = false

    var onHistogramUpdated: ((HistogramData) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pipeline = RenderPipeline(context).apply {
            initGL()
        }
        pendingBitmap?.let {
            pipeline?.loadBitmap(it)
            pendingBitmap = null
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClearColor(0.07f, 0.07f, 0.07f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val lutData = SplineInterpolator.generateLut256(currentRecipe.curves)
        pipeline?.updateToneCurve(lutData)
        pipeline?.render(currentRecipe, altClippingMode, showOriginal)

        // Live GPU 256-bin histogram extraction (~1ms)
        pipeline?.extractHistogram()?.let { histo ->
            onHistogramUpdated?.invoke(histo)
        }
    }

    fun loadBitmap(bitmap: Bitmap) {
        if (pipeline != null) {
            pipeline?.loadBitmap(bitmap)
        } else {
            pendingBitmap = bitmap
        }
    }

    fun updateRecipe(recipe: EditRecipe, clippingMode: Int, original: Boolean) {
        currentRecipe = recipe
        altClippingMode = clippingMode
        showOriginal = original
    }
}

@Composable
fun LoupeCanvas(
    bitmap: Bitmap?,
    recipe: EditRecipe,
    altClippingMode: Int,
    isComparingOriginal: Boolean,
    onComparingOriginalChanged: (Boolean) -> Unit = {},
    onDoubleTapZoom: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onHistogramUpdated: (HistogramData) -> Unit,
    isTatActive: Boolean = false,
    tatTargetName: String? = null,
    onTatSample: (normX: Float, normY: Float) -> Unit = { _, _ -> },
    onTatDrag: (deltaY: Float) -> Unit = {},
    activeTool: ActiveTool? = null,
    brushSize: Float = 50.0f,
    brushFeather: Float = 50.0f,
    modifier: Modifier = Modifier
) {
    var glView by remember { mutableStateOf<LoupeGLView?>(null) }

    // Zoom and pan state
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // TAT touch point
    var tatTouchPos by remember { mutableStateOf<Offset?>(null) }

    // Brush cursor touch point
    var brushTouchPos by remember { mutableStateOf<Offset?>(null) }

    // Update GLView when recipe or comparison changes
    LaunchedEffect(recipe, altClippingMode, isComparingOriginal) {
        glView?.updateRecipe(recipe, altClippingMode, isComparingOriginal)
    }

    // Update GLView when bitmap changes
    LaunchedEffect(bitmap) {
        bitmap?.let {
            glView?.setBitmap(it, onHistogramUpdated)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (activeTool == ActiveTool.BRUSH) {
                    Modifier.pointerInput(activeTool) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                brushTouchPos = startOffset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                brushTouchPos = change.position
                            },
                            onDragEnd = { },
                            onDragCancel = { }
                        )
                    }
                } else if (isTatActive) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                tatTouchPos = startOffset
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    onTatSample(startOffset.x / w, startOffset.y / h)
                                }
                            },
                            onDragEnd = {
                                tatTouchPos = null
                            },
                            onDragCancel = {
                                tatTouchPos = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                tatTouchPos = change.position
                                onTatDrag(dragAmount.y)
                            }
                        )
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        detectCanvasTouchGestures(
                            onLongPressChange = { active ->
                                onComparingOriginalChanged(active)
                            },
                            onDoubleTap = { tapPos ->
                                onDoubleTapZoom()
                                if (scale > 1.0f) {
                                    scale = 1.0f
                                    offset = Offset.Zero
                                } else {
                                    val targetScale = 2.5f
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    offset = Offset((centerX - tapPos.x) * (targetScale - 1f), (centerY - tapPos.y) * (targetScale - 1f))
                                    scale = targetScale
                                }
                            },
                            onTwoFingerTap = {
                                onUndo()
                            },
                            onThreeFingerTap = {
                                onRedo()
                            },
                            onTransform = { _, pan, zoom ->
                                scale = (scale * zoom).coerceIn(1.0f, 6.0f)
                                offset = if (scale == 1.0f) Offset.Zero else offset + pan
                            }
                        )
                    }
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                LoupeGLView(ctx).also {
                    glView = it
                    bitmap?.let { bmp -> it.setBitmap(bmp, onHistogramUpdated) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        // TAT Reticle & Target Channel HUD Overlay
        if (isTatActive) {
            // Top HUD showing targeted channel
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tatTargetName != null) "🎯 TAT: $tatTargetName (Drag ↑/↓)" else "🎯 TAT: Tap on photo to target color",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            // Target Crosshair at touched position
            tatTouchPos?.let { pos ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White,
                        radius = 18.dp.toPx(),
                        center = pos,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 20.dp.toPx(),
                        center = pos,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    // Crosshairs
                    val arm = 26.dp.toPx()
                    drawLine(Color.White, Offset(pos.x - arm, pos.y), Offset(pos.x + arm, pos.y), strokeWidth = 1.5.dp.toPx())
                    drawLine(Color.White, Offset(pos.x, pos.y - arm), Offset(pos.x, pos.y + arm), strokeWidth = 1.5.dp.toPx())
                }
            }
        }

        // Dual-Ring Brush Cursor Overlay (PDF Page 7)
        if (activeTool == ActiveTool.BRUSH) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🖌️ Brush: Radius ${brushSize.toInt()}dp | Feather ${brushFeather.toInt()}% ([ ] size, Shift+[ ] feather)",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            DualRingBrushCursor(
                center = brushTouchPos,
                brushRadiusDp = brushSize,
                featherPercent = brushFeather,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
