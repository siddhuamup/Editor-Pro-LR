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

enum class FlagState { NONE, PICK, REJECT }
enum class ViewMode { LOUPE, GRID }
enum class ActiveTool { BRUSH, LINEAR_GRADIENT, RADIAL_GRADIENT }
enum class MaskOverlayColor(val hexArgb: Long, val label: String) {
    RED(0x80E02B2BL, "Ruby Red"),
    GREEN(0x802BE040L, "Emerald Green"),
    BLUE(0x802B70E0L, "Cobalt Blue"),
    WHITE(0x80FFFFFFL, "White")
}

class EditorViewModel(application: Application) : AndroidViewModel(application), com.cleanlr.ui.shortcuts.EditorShortcutsDispatcher {

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

    // Targeted Adjustment Tool (TAT) State
    private val _isTatActive = MutableStateFlow(false)
    val isTatActive: StateFlow<Boolean> = _isTatActive.asStateFlow()

    private val _tatMode = MutableStateFlow(com.cleanlr.engine.TatTargetMode.SATURATION)
    val tatMode: StateFlow<com.cleanlr.engine.TatTargetMode> = _tatMode.asStateFlow()

    private val _activeTatWeights = MutableStateFlow<com.cleanlr.engine.TatChannelWeights?>(null)
    val activeTatWeights: StateFlow<com.cleanlr.engine.TatChannelWeights?> = _activeTatWeights.asStateFlow()

    private val _activeTatTargetName = MutableStateFlow<String?>(null)
    val activeTatTargetName: StateFlow<String?> = _activeTatTargetName.asStateFlow()

    // View & Navigation States (PDF Part A Section 3)
    private val _isSplitView = MutableStateFlow(false)
    val isSplitView: StateFlow<Boolean> = _isSplitView.asStateFlow()

    private val _isCleanScreenMode = MutableStateFlow(false)
    val isCleanScreenMode: StateFlow<Boolean> = _isCleanScreenMode.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _isZoom100Percent = MutableStateFlow(false)
    val isZoom100Percent: StateFlow<Boolean> = _isZoom100Percent.asStateFlow()

    private val _isInfoOverlayVisible = MutableStateFlow(false)
    val isInfoOverlayVisible: StateFlow<Boolean> = _isInfoOverlayVisible.asStateFlow()

    // Rating & Flags States
    private val _starRating = MutableStateFlow(0)
    val starRating: StateFlow<Int> = _starRating.asStateFlow()

    private val _flagState = MutableStateFlow(FlagState.NONE)
    val flagState: StateFlow<FlagState> = _flagState.asStateFlow()

    // Tools & Modes States
    private val _viewMode = MutableStateFlow(ViewMode.LOUPE)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _isCropActive = MutableStateFlow(false)
    val isCropActive: StateFlow<Boolean> = _isCropActive.asStateFlow()

    private val _activeTool = MutableStateFlow<ActiveTool?>(null)
    val activeTool: StateFlow<ActiveTool?> = _activeTool.asStateFlow()

    // Mask Overlay & Color Cycling States
    private val _isMaskOverlayVisible = MutableStateFlow(true)
    val isMaskOverlayVisible: StateFlow<Boolean> = _isMaskOverlayVisible.asStateFlow()

    private val _maskOverlayColor = MutableStateFlow(MaskOverlayColor.RED)
    val maskOverlayColor: StateFlow<MaskOverlayColor> = _maskOverlayColor.asStateFlow()

    // Brush Parameter States
    private val _brushSize = MutableStateFlow(50.0f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _brushFeather = MutableStateFlow(50.0f)
    val brushFeather: StateFlow<Float> = _brushFeather.asStateFlow()

    // Clipboard for Edit Settings (Ctrl+C / Ctrl+V)
    private val _clipboardRecipe = MutableStateFlow<EditRecipe?>(null)
    val clipboardRecipe: StateFlow<EditRecipe?> = _clipboardRecipe.asStateFlow()

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
                    // XMP / Metadata Round-Trip: restore embedded recipe if photo was exported by CleanLR / Lightroom
                    val restoredRecipe = context.contentResolver.openInputStream(uri)?.use { inStream ->
                        com.cleanlr.export.ExportMetadataWriter.extractRecipe(inStream)
                    }
                    if (restoredRecipe != null) {
                        _recipe.value = restoredRecipe
                        historyManager.commitState(restoredRecipe, "Restore Embedded Recipe")
                    } else {
                        resetAll()
                    }
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

    override fun undo() {
        val prev = historyManager.undo()
        if (prev != null) {
            _recipe.value = prev
        }
    }

    override fun redo() {
        val next = historyManager.redo()
        if (next != null) {
            _recipe.value = next
        }
    }

    override fun resetAll() {
        val reset = EditRecipe()
        historyManager.commitState(reset, "Reset All")
        _recipe.value = reset
    }

    override fun setAltClippingMode(mode: Int) {
        _altClippingMode.value = mode
    }

    override fun setComparingOriginal(comparing: Boolean) {
        _isComparingOriginal.value = comparing
    }

    override fun toggleComparingOriginal() {
        _isComparingOriginal.value = !_isComparingOriginal.value
    }

    override fun toggleSplitView() {
        _isSplitView.value = !_isSplitView.value
    }

    override fun toggleCleanScreenMode() {
        _isCleanScreenMode.value = !_isCleanScreenMode.value
    }

    override fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun toggleZoom100Percent() {
        _isZoom100Percent.value = !_isZoom100Percent.value
    }

    override fun toggleInfoOverlay() {
        _isInfoOverlayVisible.value = !_isInfoOverlayVisible.value
    }

    override fun setStarRating(rating: Int) {
        _starRating.value = rating.coerceIn(0, 5)
    }

    fun setFlagState(state: FlagState) {
        _flagState.value = state
    }

    override fun pickPhoto() = setFlagState(FlagState.PICK)
    override fun unflagPhoto() = setFlagState(FlagState.NONE)
    override fun rejectPhoto() = setFlagState(FlagState.REJECT)

    override fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    override fun toggleCropActive() {
        _isCropActive.value = !_isCropActive.value
    }

    override fun setActiveTool(tool: ActiveTool?) {
        _activeTool.value = tool
    }

    override fun selectBrushTool() = setActiveTool(ActiveTool.BRUSH)
    override fun selectLinearGradientMask() = setActiveTool(ActiveTool.LINEAR_GRADIENT)
    override fun selectRadialGradientMask() = setActiveTool(ActiveTool.RADIAL_GRADIENT)

    override fun toggleMaskOverlay() {
        _isMaskOverlayVisible.value = !_isMaskOverlayVisible.value
    }

    override fun cycleMaskOverlayColor() {
        val values = MaskOverlayColor.values()
        val nextIdx = (_maskOverlayColor.value.ordinal + 1) % values.size
        _maskOverlayColor.value = values[nextIdx]
    }

    override fun adjustBrushSize(delta: Float) {
        _brushSize.value = (_brushSize.value + delta).coerceIn(5.0f, 200.0f)
    }

    override fun adjustBrushFeather(delta: Float) {
        _brushFeather.value = (_brushFeather.value + delta).coerceIn(0.0f, 100.0f)
    }

    override fun copyEditSettings() {
        _clipboardRecipe.value = _recipe.value
    }

    override fun pasteEditSettings() {
        val copied = _clipboardRecipe.value ?: return
        historyManager.commitState(copied, "Paste Settings")
        _recipe.value = copied
    }

    fun setActiveTab(tab: EditorToolTab) {
        _activeTab.value = tab
    }

    fun updateHistogram(data: HistogramData) {
        _histogramData.value = data
    }

    fun toggleTat() {
        val next = !_isTatActive.value
        _isTatActive.value = next
        if (!next) {
            _activeTatWeights.value = null
            _activeTatTargetName.value = null
        }
    }

    fun setTatMode(mode: com.cleanlr.engine.TatTargetMode) {
        _tatMode.value = mode
    }

    fun sampleTatPixel(normX: Float, normY: Float) {
        val bmp = _bitmap.value ?: return
        val px = (normX.coerceIn(0f, 1f) * (bmp.width - 1)).toInt()
        val py = (normY.coerceIn(0f, 1f) * (bmp.height - 1)).toInt()
        val pixel = bmp.getPixel(px, py)

        val r = android.graphics.Color.red(pixel) / 255.0f
        val g = android.graphics.Color.green(pixel) / 255.0f
        val b = android.graphics.Color.blue(pixel) / 255.0f

        val hsl = com.cleanlr.engine.TatEngine.rgbToHsl(r, g, b)
        val weights = com.cleanlr.engine.TatEngine.computeChannelWeights(hsl[0])
        _activeTatWeights.value = weights
        _activeTatTargetName.value = weights.dominantChannelName()
    }

    fun dragTat(deltaY: Float) {
        val weights = _activeTatWeights.value ?: return
        val updatedHsl = com.cleanlr.engine.TatEngine.applyTatDrag(
            currentHsl = _recipe.value.hsl,
            weights = weights,
            delta = -deltaY * 0.4f,
            mode = _tatMode.value
        )
        updateRecipe("TAT ${tatMode.value.label}") { it.copy(hsl = updatedHsl) }
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
                    com.cleanlr.export.ExportManager.enqueueSingle(context, uri, current)
                    _exportStatus.value = "Export task scheduled (expedited)"
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
