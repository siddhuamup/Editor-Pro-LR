package com.cleanlr.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
        queueEvent {
            renderer.loadBitmap(bitmap)
            requestRender()
            // Extract histogram on background thread
            val histo = HistogramExtractor.extract(bitmap)
            post { onHistogramExtracted(histo) }
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
    onHistogramUpdated: (HistogramData) -> Unit,
    modifier: Modifier = Modifier
) {
    var glView by remember { mutableStateOf<LoupeGLView?>(null) }

    // Zoom and pan state
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1.0f, 6.0f)
                    offset = if (scale == 1.0f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.0f) {
                            scale = 1.0f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
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
    }
}
