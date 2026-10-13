package com.cleanlr.ui.editor

import android.app.Application
import android.graphics.*
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cleanlr.engine.HistogramData
import com.cleanlr.export.ExportImageWorker
import com.cleanlr.history.HistoryManager
import com.cleanlr.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class EditorToolTab(val title: String) {
    LIGHT("Light"),
    COLOR("Color"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    CURVES("Curves")
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val historyManager = HistoryManager(EditRecipe())

    private val _recipe = MutableStateFlow(EditRecipe())
    val recipe: StateFlow<EditRecipe> = _recipe.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _sourceUri = MutableStateFlow<Uri?>(null)
    val sourceUri: StateFlow<Uri?> = _sourceUri.asStateFlow()

    private val _histogramData = MutableStateFlow(HistogramData())
    val histogramData: StateFlow<HistogramData> = _histogramData.asStateFlow()

    private val _altClippingMode = MutableStateFlow(0)
    val altClippingMode: StateFlow<Int> = _altClippingMode.asStateFlow()

    private val _isComparingOriginal = MutableStateFlow(false)
    val isComparingOriginal: StateFlow<Boolean> = _isComparingOriginal.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorToolTab.LIGHT)
    val activeTab: StateFlow<EditorToolTab> = _activeTab.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    val canUndo = historyManager.canUndo
    val canRedo = historyManager.canRedo

    init {
        // Generate high quality landscape sample image so editor works immediately
        loadSampleImage()
    }

    private fun loadSampleImage() {
        viewModelScope.launch(Dispatchers.Default) {
            val width = 1200
            val height = 800
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Sky gradient (Sunset / Golden hour)
            val skyPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height * 0.65f,
                    intArrayOf(
                        Color.rgb(20, 45, 90),     // Deep blue sky
                        Color.rgb(180, 85, 45),    // Orange golden horizon
                        Color.rgb(240, 160, 60)    // Sunset glow
                    ),
                    floatArrayOf(0.0f, 0.7f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height * 0.65f, skyPaint)

            // Sun disk
            val sunPaint = Paint().apply {
                color = Color.rgb(255, 245, 200)
                isAntiAlias = true
            }
            canvas.drawCircle(width * 0.55f, height * 0.52f, 48f, sunPaint)

            // Distant Mountains
            val mountainPaint = Paint().apply {
                color = Color.rgb(45, 30, 45)
                isAntiAlias = true
            }
            val mountainPath = Path().apply {
                moveTo(0f, height * 0.65f)
                lineTo(width * 0.25f, height * 0.42f)
                lineTo(width * 0.5f, height * 0.55f)
                lineTo(width * 0.75f, height * 0.38f)
                lineTo(width.toFloat(), height * 0.65f)
                close()
            }
            canvas.drawPath(mountainPath, mountainPaint)

            // Foreground lake / water reflection
            val waterPaint = Paint().apply {
                shader = LinearGradient(
                    0f, height * 0.65f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.rgb(90, 45, 30),
                        Color.rgb(20, 25, 40)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, height * 0.65f, width.toFloat(), height.toFloat(), waterPaint)

            _bitmap.value = bmp
        }
    }

    fun loadFromUri(uri: Uri) {
        _sourceUri.value = uri
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val decoded = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (decoded != null) {
                    _bitmap.value = decoded
                    resetAll()
                }
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Failed to load image from URI", e)
            }
        }
    }

    fun onSliderTouchStart() {
        historyManager.onTouchDown(_recipe.value)
    }

    fun onSliderTouchEnd(actionName: String) {
        historyManager.onTouchUp(_recipe.value, actionName)
    }

    fun updateRecipe(actionName: String? = null, transform: (EditRecipe) -> EditRecipe) {
        val updated = transform(_recipe.value)
        _recipe.value = updated
        if (actionName != null) {
            historyManager.commitState(updated, actionName)
        }
    }

    fun undo() {
        val prev = historyManager.undo()
        if (prev != null) {
            _recipe.value = prev
        }
    }

    fun redo() {
        val next = historyManager.redo()
        if (next != null) {
            _recipe.value = next
        }
    }

    fun resetAll() {
        val reset = EditRecipe()
        historyManager.commitState(reset, "Reset All")
        _recipe.value = reset
    }

    fun setAltClippingMode(mode: Int) {
        _altClippingMode.value = mode
    }

    fun setComparingOriginal(comparing: Boolean) {
        _isComparingOriginal.value = comparing
    }

    fun setActiveTab(tab: EditorToolTab) {
        _activeTab.value = tab
    }

    fun updateHistogram(data: HistogramData) {
        _histogramData.value = data
    }

    fun exportImage() {
        val bmp = _bitmap.value ?: return
        val current = _recipe.value
        _exportStatus.value = "Exporting..."

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val context = getApplication<Application>()
                val recipeJson = Json.encodeToString(current)

                val uri = _sourceUri.value
                if (uri != null) {
                    // Use WorkManager
                    val workRequest = OneTimeWorkRequestBuilder<ExportImageWorker>()
                        .setInputData(
                            workDataOf(
                                ExportImageWorker.KEY_IMAGE_URI to uri.toString(),
                                ExportImageWorker.KEY_RECIPE_JSON to recipeJson,
                                ExportImageWorker.KEY_QUALITY to 95
                            )
                        )
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                    _exportStatus.value = "Export task scheduled"
                } else {
                    // Export directly via Headless Renderer
                    val renderer = com.cleanlr.export.HeadlessGlRenderer(context)
                    val rendered = renderer.renderFullResolution(bmp, current)
                    saveSampleToMediaStore(rendered)
                    _exportStatus.value = "Exported to Pictures/CleanLightroom"
                }
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Export failed", e)
                _exportStatus.value = "Export failed"
            }
        }
    }

    private fun saveSampleToMediaStore(bitmap: Bitmap) {
        val context = getApplication<Application>()
        val filename = "CleanLR_Export_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/CleanLightroom")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }
}
