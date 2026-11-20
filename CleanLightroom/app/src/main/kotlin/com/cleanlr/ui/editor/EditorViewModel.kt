package com.cleanlr.ui.editor

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.cleanlr.export.BatchExportItem
import com.cleanlr.export.BatchExportManager
import com.cleanlr.export.BatchExportProgress

import com.cleanlr.preset.Preset
import com.cleanlr.preset.PresetManager
import com.cleanlr.storage.DebouncedAutoSaver
import com.cleanlr.storage.LocalDocumentStore
import com.cleanlr.version.NamedVersion
import com.cleanlr.version.VersionManager

enum class EditorToolTab(val title: String) {
    CROP("Crop"),
    LIGHT("Light"),
    COLOR("Color"),
    BLUR("Blur"),
    EFFECTS("Effects"),
    DETAIL("Detail"),
    OPTICS("Optics"),
    PROFILES("Profiles"),
    CURVES("Curves"),
    PRESETS("Presets"),
    VERSIONS("Versions"),
    MASKS("Masks"),
    ACTIONS("Actions"),
    REMOVE("Remove")
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

/**
 * Tip 98: Photo EXIF Information for Info Overlay HUD
 */
data class PhotoExifInfo(
    val fileName: String = "Photo",
    val width: Int = 0,
    val height: Int = 0,
    val megaPixels: String = "",
    val cameraModel: String = "",
    val lensModel: String = "",
    val iso: String = "",
    val focalLength: String = "",
    val aperture: String = "",
    val shutterSpeed: String = "",
    val isP3Gamut: Boolean = false,
    val hasGainMap: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application), com.cleanlr.ui.shortcuts.EditorShortcutsDispatcher {

    val historyManager = HistoryManager(EditRecipe())

    private val versionManager = VersionManager(EditRecipe())
    val versions: StateFlow<List<NamedVersion>> = versionManager.versions

    private val documentStore = LocalDocumentStore(application.applicationContext)
    private val autoSaver = DebouncedAutoSaver(documentStore, viewModelScope, 300L)

    private val _transientToast = MutableStateFlow<String?>(null)
    val transientToast: StateFlow<String?> = _transientToast.asStateFlow()

    fun showTransientToast(msg: String) {
        _transientToast.value = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            if (_transientToast.value == msg) {
                _transientToast.value = null
            }
        }
    }

    fun clearTransientToast() {
        _transientToast.value = null
    }

    val presetGroups = listOf("All") + PresetManager.getGroups() + listOf("User Presets")
    private val _selectedPresetGroup = MutableStateFlow("All")
    val selectedPresetGroup: StateFlow<String> = _selectedPresetGroup.asStateFlow()

    private val _customPresets = MutableStateFlow<List<Preset>>(emptyList())
    val customPresets: StateFlow<List<Preset>> = _customPresets.asStateFlow()

    private val _allPresets = MutableStateFlow<List<Preset>>(PresetManager.ALL_PRESETS)
    val allPresets: StateFlow<List<Preset>> = _allPresets.asStateFlow()

    private val _activePreset = MutableStateFlow<Preset?>(null)
    val activePreset: StateFlow<Preset?> = _activePreset.asStateFlow()

    private val _presetAmount = MutableStateFlow(100.0f)
    val presetAmount: StateFlow<Float> = _presetAmount.asStateFlow()

    private var recipeBeforePreset: EditRecipe? = null

    private val _recipe = MutableStateFlow(EditRecipe())
    val recipe: StateFlow<EditRecipe> = _recipe.asStateFlow()

    init {
        com.cleanlr.preset.MinedPresetLibrary.init(application.applicationContext)
        val saved = documentStore.loadRecipe("current_photo")
        if (saved != null) {
            _recipe.value = saved
        }
    }

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _sourceUri = MutableStateFlow<Uri?>(null)
    val sourceUri: StateFlow<Uri?> = _sourceUri.asStateFlow()

    // Tips 83, 98: Photo EXIF Metadata
    private val _photoExif = MutableStateFlow<PhotoExifInfo?>(null)
    val photoExif: StateFlow<PhotoExifInfo?> = _photoExif.asStateFlow()

    // Tips 92–95: Split View Position & Orientation
    private val _splitPosition = MutableStateFlow(0.5f)
    val splitPosition: StateFlow<Float> = _splitPosition.asStateFlow()

    private val _isSplitHorizontal = MutableStateFlow(false)
    val isSplitHorizontal: StateFlow<Boolean> = _isSplitHorizontal.asStateFlow()

    // Tip 100: Rule of Thirds 3x3 Grid
    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    // Tip 119: Aspect Ratio Guide
    private val _aspectRatioGuide = MutableStateFlow<String?>(null)
    val aspectRatioGuide: StateFlow<String?> = _aspectRatioGuide.asStateFlow()

    // Tip 118: Zoom Lock
    private val _isZoomLocked = MutableStateFlow(false)
    val isZoomLocked: StateFlow<Boolean> = _isZoomLocked.asStateFlow()

    // Tips 201 & 202: 3D Film Emulation LUTs
    private val _activeFilm3dLut = MutableStateFlow(com.cleanlr.engine.stage.Film3dLut.NONE)
    val activeFilm3dLut: StateFlow<com.cleanlr.engine.stage.Film3dLut> = _activeFilm3dLut.asStateFlow()

    private val _film3dLutIntensity = MutableStateFlow(1.0f)
    val film3dLutIntensity: StateFlow<Float> = _film3dLutIntensity.asStateFlow()

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

    private val _lastExportedUri = MutableStateFlow<android.net.Uri?>(null)
    val lastExportedUri: StateFlow<android.net.Uri?> = _lastExportedUri.asStateFlow()

    private val _lastExportedFormat = MutableStateFlow<com.cleanlr.export.ExportFormat>(com.cleanlr.export.ExportFormat.JPEG)
    val lastExportedFormat: StateFlow<com.cleanlr.export.ExportFormat> = _lastExportedFormat.asStateFlow()

    private val _activeBatchProgress = MutableStateFlow<BatchExportProgress?>(null)
    val activeBatchProgress: StateFlow<BatchExportProgress?> = _activeBatchProgress.asStateFlow()

    private val _isBatchExportDialogVisible = MutableStateFlow(false)
    val isBatchExportDialogVisible: StateFlow<Boolean> = _isBatchExportDialogVisible.asStateFlow()

    private var currentBatchId: String? = null

    // Targeted Adjustment Tool (TAT) State
    private val _isTatActive = MutableStateFlow(false)
    val isTatActive: StateFlow<Boolean> = _isTatActive.asStateFlow()

    private val _tatMode = MutableStateFlow(com.cleanlr.engine.TatTargetMode.SATURATION)
    val tatMode: StateFlow<com.cleanlr.engine.TatTargetMode> = _tatMode.asStateFlow()

    private val _activeTatWeights = MutableStateFlow<com.cleanlr.engine.TatChannelWeights?>(null)
    val activeTatWeights: StateFlow<com.cleanlr.engine.TatChannelWeights?> = _activeTatWeights.asStateFlow()

    private val _activeTatTargetName = MutableStateFlow<String?>(null)
    val activeTatTargetName: StateFlow<String?> = _activeTatTargetName.asStateFlow()

    // View & Navigation States (PDF Part A Section 3 & Tips 1-35)
    private val _isSplitView = MutableStateFlow(false)
    val isSplitView: StateFlow<Boolean> = _isSplitView.asStateFlow()

    private val _isCleanScreenMode = MutableStateFlow(false)
    val isCleanScreenMode: StateFlow<Boolean> = _isCleanScreenMode.asStateFlow()

    private val _canvasBackground = MutableStateFlow(com.cleanlr.ui.theme.CanvasBackground.PURE_BLACK)
    val canvasBackground: StateFlow<com.cleanlr.ui.theme.CanvasBackground> = _canvasBackground.asStateFlow()

    private val _isCanvasMattingActive = MutableStateFlow(false)
    val isCanvasMattingActive: StateFlow<Boolean> = _isCanvasMattingActive.asStateFlow()

    private val _canvasMattingWidthDp = MutableStateFlow(16)
    val canvasMattingWidthDp: StateFlow<Int> = _canvasMattingWidthDp.asStateFlow()

    private val _panelSnapState = MutableStateFlow(com.cleanlr.ui.theme.PanelSnapState.COMPACT)
    val panelSnapState: StateFlow<com.cleanlr.ui.theme.PanelSnapState> = _panelSnapState.asStateFlow()

    private val _isSliderDragging = MutableStateFlow(false)
    val isSliderDragging: StateFlow<Boolean> = _isSliderDragging.asStateFlow()

    private val _bypassedTabs = MutableStateFlow<Set<EditorToolTab>>(emptySet())
    val bypassedTabs: StateFlow<Set<EditorToolTab>> = _bypassedTabs.asStateFlow()

    private val _isColorWbExpanded = MutableStateFlow(true)
    val isColorWbExpanded: StateFlow<Boolean> = _isColorWbExpanded.asStateFlow()

    private val _isColorVibranceExpanded = MutableStateFlow(true)
    val isColorVibranceExpanded: StateFlow<Boolean> = _isColorVibranceExpanded.asStateFlow()

    private val _isColorMixerExpanded = MutableStateFlow(false)
    val isColorMixerExpanded: StateFlow<Boolean> = _isColorMixerExpanded.asStateFlow()

    private val _isColorGradingExpanded = MutableStateFlow(false)
    val isColorGradingExpanded: StateFlow<Boolean> = _isColorGradingExpanded.asStateFlow()

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

    // Brush Parameter States (Default 10.0f matching Screenshot 083752)
    private val _brushSize = MutableStateFlow(10.0f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _brushFeather = MutableStateFlow(50.0f)
    val brushFeather: StateFlow<Float> = _brushFeather.asStateFlow()

    // SOTA FLUX.1 Fill + Poisson Blending Generative Remove & Expand States (Zero APK Bloat)
    private val _genAiConfig = MutableStateFlow(
        com.cleanlr.engine.GenerativeAiConfigStore.load(application.applicationContext)
    )
    val genAiConfig: StateFlow<com.cleanlr.engine.GenerativeAiConfig> = _genAiConfig.asStateFlow()

    private val _isGenAiProcessing = MutableStateFlow(false)
    val isGenAiProcessing: StateFlow<Boolean> = _isGenAiProcessing.asStateFlow()

    private val _isLensBlurDetecting = MutableStateFlow(false)
    val isLensBlurDetecting: StateFlow<Boolean> = _isLensBlurDetecting.asStateFlow()
    private var lensBlurDepthJob: kotlinx.coroutines.Job? = null

    private val _showGenAiSettingsDialog = MutableStateFlow(false)
    val showGenAiSettingsDialog: StateFlow<Boolean> = _showGenAiSettingsDialog.asStateFlow()

    private val _removeBrushPoints = MutableStateFlow<List<BrushStrokePoint>>(emptyList())
    val removeBrushPoints: StateFlow<List<BrushStrokePoint>> = _removeBrushPoints.asStateFlow()

    private val _removeStrokeBreakIndices = MutableStateFlow<Set<Int>>(emptySet())
    val removeStrokeBreakIndices: StateFlow<Set<Int>> = _removeStrokeBreakIndices.asStateFlow()

    private val _useGenerativeAiRemove = MutableStateFlow(true)
    val useGenerativeAiRemove: StateFlow<Boolean> = _useGenerativeAiRemove.asStateFlow()

    // Authentic Lightroom Remove Tool Modes (Remove, Heal, Clone) + Feather / Opacity / Refine / Source Pin
    private val _removeToolMode = MutableStateFlow(com.cleanlr.engine.RemoveToolMode.REMOVE)
    val removeToolMode: StateFlow<com.cleanlr.engine.RemoveToolMode> = _removeToolMode.asStateFlow()

    private val _healFeather = MutableStateFlow(50f)
    val healFeather: StateFlow<Float> = _healFeather.asStateFlow()

    private val _healOpacity = MutableStateFlow(100f)
    val healOpacity: StateFlow<Float> = _healOpacity.asStateFlow()

    private val _detectObjectsRemove = MutableStateFlow(false)
    val detectObjectsRemove: StateFlow<Boolean> = _detectObjectsRemove.asStateFlow()

    private val _isRefineEraseMode = MutableStateFlow(false)
    val isRefineEraseMode: StateFlow<Boolean> = _isRefineEraseMode.asStateFlow()

    private val _healCloneSourceNorm = MutableStateFlow<Pair<Float, Float>?>(null)
    val healCloneSourceNorm: StateFlow<Pair<Float, Float>?> = _healCloneSourceNorm.asStateFlow()

    // Phase 1-4: Modular BYOM ModelManager, LAN Wi-Fi Bridge & 5-Stage Optical Blur Pack
    val modelManager = com.cleanlr.ai.ModelManager(application.applicationContext)
    val modelStates: StateFlow<Map<String, com.cleanlr.ai.ModelRuntimeState>> = modelManager.states

    private val lanBridge = com.cleanlr.ai.LanBridgeDiscovery(application.applicationContext)
    val discoveredLanServers: StateFlow<List<com.cleanlr.ai.DiscoveredLanServer>> = lanBridge.discoveredServers
    val isLanScanning: StateFlow<Boolean> = lanBridge.isScanning

    private val _showModelManagerSheet = MutableStateFlow(false)
    val showModelManagerSheet: StateFlow<Boolean> = _showModelManagerSheet.asStateFlow()

    private val _opticalBlurPack = MutableStateFlow<com.cleanlr.ai.AiVisionPipeline.OpticalBlurPack?>(null)
    val opticalBlurPack: StateFlow<com.cleanlr.ai.AiVisionPipeline.OpticalBlurPack?> = _opticalBlurPack.asStateFlow()

    private val _opticalStageStatus = MutableStateFlow<String?>(null)
    val opticalStageStatus: StateFlow<String?> = _opticalStageStatus.asStateFlow()

    private val bitmapUndoStack = ArrayDeque<Bitmap>()

    // Clipboard for Edit Settings (Ctrl+C / Ctrl+V)
    private val _clipboardRecipe = MutableStateFlow<EditRecipe?>(null)
    val clipboardRecipe: StateFlow<EditRecipe?> = _clipboardRecipe.asStateFlow()

    val canUndo = historyManager.canUndo
    val canRedo = historyManager.canRedo

    init {
        // Register FLUX.1 Fill provider if already configured in SharedPreferences
        val savedCfg = _genAiConfig.value
        if (savedCfg.isReady) {
            com.cleanlr.engine.GenerativeExpandEngine.activeProvider =
                com.cleanlr.engine.FluxFillExpandProvider(savedCfg)
        }
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
        _isCleanScreenMode.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                // Tip 84: Smart Preview Downsampling (inJustDecodeBounds)
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    BitmapFactory.decodeStream(inStream, null, boundsOptions)
                }

                val origW = boundsOptions.outWidth
                val origH = boundsOptions.outHeight
                val maxDim = maxOf(origW, origH)
                var sampleSize = 1
                val targetMaxEdge = 2560
                while (maxDim / (sampleSize * 2) >= targetMaxEdge) {
                    sampleSize *= 2
                }

                // Decode preview bitmap with sampleSize
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val rawDecoded = context.contentResolver.openInputStream(uri)?.use { inStream ->
                    BitmapFactory.decodeStream(inStream, null, decodeOptions)
                }

                if (rawDecoded != null) {
                    // Tip 83: EXIF Auto-Rotate & Tip 98: Metadata Extraction
                    var rotationDegrees = 0f
                    var flipHorizontal = false
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Photo"
                    var exifInfo = PhotoExifInfo(
                        fileName = fileName,
                        width = origW,
                        height = origH,
                        megaPixels = if (origW > 0 && origH > 0) String.format(java.util.Locale.US, "%.1f", (origW * origH) / 1_000_000f) else ""
                    )

                    try {
                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                            val exif = androidx.exifinterface.media.ExifInterface(inStream)
                            val orientation = exif.getAttributeInt(
                                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                            )
                            when (orientation) {
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270f
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal = true
                                else -> {}
                            }

                            val make = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE) ?: ""
                            val model = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL) ?: ""
                            val cameraModel = if (model.startsWith(make)) model else "$make $model".trim()
                            val lens = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL) ?: ""
                            val iso = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: ""
                            val focal = exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                            val fNumber = exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, 0.0)
                            val expTime = exif.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                            val shutterStr = if (expTime > 0) {
                                if (expTime < 1.0) "1/${(1.0 / expTime).toInt()}s" else "${String.format(java.util.Locale.US, "%.1f", expTime)}s"
                            } else ""

                            exifInfo = exifInfo.copy(
                                cameraModel = cameraModel,
                                lensModel = lens,
                                iso = if (iso.isNotEmpty()) "ISO $iso" else "",
                                focalLength = if (focal > 0) "${focal.toInt()}mm" else "",
                                aperture = if (fNumber > 0) "f/${String.format(java.util.Locale.US, "%.1f", fNumber)}" else "",
                                shutterSpeed = shutterStr
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("EditorViewModel", "Failed to extract EXIF", e)
                    }

                    _photoExif.value = exifInfo

                    // Apply rotation matrix
                    val matrix = Matrix()
                    if (rotationDegrees != 0f) matrix.postRotate(rotationDegrees)
                    if (flipHorizontal) matrix.postScale(-1f, 1f)

                    val finalBitmap = if (!matrix.isIdentity) {
                        Bitmap.createBitmap(rawDecoded, 0, 0, rawDecoded.width, rawDecoded.height, matrix, true)
                    } else rawDecoded

                    _bitmap.value = finalBitmap

                    // XMP / Metadata Round-Trip
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

    fun setSplitPosition(pos: Float) {
        _splitPosition.value = pos.coerceIn(0.05f, 0.95f)
    }

    fun toggleSplitOrientation() {
        _isSplitHorizontal.value = !_isSplitHorizontal.value
    }

    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
    }

    fun setAspectRatioGuide(guide: String?) {
        _aspectRatioGuide.value = guide
    }

    fun toggleZoomLock() {
        _isZoomLocked.value = !_isZoomLocked.value
    }

    fun getHistorySteps(): List<String> {
        return historyManager.getHistoryDescriptions()
    }

    fun restoreToHistoryStep(index: Int) {
        val restored = historyManager.restoreToStep(index)
        if (restored != null) {
            _recipe.value = restored
            autoSaver.scheduleSave("current_photo", restored)
        }
    }

    fun cycleAspectRatioGuide() {
        _aspectRatioGuide.value = when (_aspectRatioGuide.value) {
            null -> "4:5"
            "4:5" -> "9:16"
            "9:16" -> "1:1"
            else -> null
        }
    }

    fun onSliderTouchStart() {
        _isSliderDragging.value = true
        historyManager.onTouchDown(_recipe.value)
    }

    fun onSliderTouchEnd(actionName: String) {
        _isSliderDragging.value = false
        historyManager.onTouchUp(_recipe.value, actionName)
    }

    fun updateRecipe(actionName: String? = null, transform: (EditRecipe) -> EditRecipe) {
        val updated = transform(_recipe.value)
        _recipe.value = updated
        autoSaver.scheduleSave("current_photo", updated)
        if (actionName != null) {
            historyManager.commitState(updated, actionName)
        }
    }

    fun selectPresetGroup(group: String) {
        _selectedPresetGroup.value = group
    }

    fun applyPreset(preset: Preset) {
        recipeBeforePreset = _recipe.value
        _activePreset.value = preset
        _presetAmount.value = 100.0f
        val updated = PresetManager.applyPresetWithAmount(_recipe.value, preset, 100.0f)
        _recipe.value = updated
        historyManager.commitState(updated, "Preset: ${preset.name}")
        autoSaver.scheduleSave("current_photo", updated)
    }

    fun adjustPresetAmount(amount: Float) {
        val base = recipeBeforePreset ?: return
        val preset = _activePreset.value ?: return
        _presetAmount.value = amount
        val updated = PresetManager.applyPresetWithAmount(base, preset, amount)
        _recipe.value = updated
        historyManager.updateLive(updated)
    }

    fun commitPresetAmount() {
        val preset = _activePreset.value ?: return
        historyManager.commitState(_recipe.value, "Preset: ${preset.name} (${_presetAmount.value.toInt()}%)")
        autoSaver.scheduleSave("current_photo", _recipe.value)
    }

    // Tip 400: Revert to original before preset application
    fun resetPreset() {
        val base = recipeBeforePreset
        if (base != null) {
            _recipe.value = base
            historyManager.commitState(base, "Reset Preset")
            autoSaver.scheduleSave("current_photo", base)
        }
        _activePreset.value = null
        _presetAmount.value = 100.0f
    }

    // Tip 383: Save current edit as custom user preset
    fun saveCustomPreset(name: String, group: String = "User Presets") {
        val newPreset = Preset(
            id = "user_preset_${System.currentTimeMillis()}",
            name = name,
            group = group,
            recipe = _recipe.value,
            description = "Custom preset saved from current edit"
        )
        val updated = _customPresets.value + newPreset
        _customPresets.value = updated
        _allPresets.value = PresetManager.ALL_PRESETS + updated
        _activePreset.value = newPreset
        _presetAmount.value = 100f
        _exportStatus.value = "Preset \"$name\" saved!"
    }

    // Tip 384: Import Adobe Lightroom .xmp preset file
    fun importXmpPreset(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    val importedRecipe = com.cleanlr.storage.AcrXmpConverter.fromXmp(content)
                    val fileName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".xmp") ?: "Imported Preset"
                    val newPreset = Preset(
                        id = "xmp_${System.currentTimeMillis()}",
                        name = fileName,
                        group = "User Presets",
                        recipe = importedRecipe,
                        description = "Imported from .xmp file"
                    )
                    val updated = _customPresets.value + newPreset
                    _customPresets.value = updated
                    _allPresets.value = PresetManager.ALL_PRESETS + updated
                    withContext(Dispatchers.Main) {
                        applyPreset(newPreset)
                        _exportStatus.value = "Imported XMP \"$fileName\""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _exportStatus.value = "Failed to import XMP: ${e.message}"
                }
            }
        }
    }

    fun createVersion(name: String) {
        versionManager.createVersion(name, _recipe.value)
    }

    fun applyVersion(id: String) {
        val versionRecipe = versionManager.applyVersion(id)
        if (versionRecipe != null) {
            _recipe.value = versionRecipe
            historyManager.commitState(versionRecipe, "Apply Version")
            autoSaver.scheduleSave("current_photo", versionRecipe)
        }
    }

    fun deleteVersion(id: String) {
        versionManager.deleteVersion(id)
    }

    fun autoTone() {
        val auto = _recipe.value.copy(
            basic = _recipe.value.basic.copy(
                exposure = 0.20f,
                contrast = 18.0f,
                highlights = -25.0f,
                shadows = 20.0f,
                whites = 12.0f,
                blacks = -8.0f,
                vibrance = 15.0f
            )
        )
        _recipe.value = auto
        historyManager.commitState(auto, "Auto Tone")
        autoSaver.scheduleSave("current_photo", auto)
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

    // Tip 512: Reset sliders only (preserves masks and crop adjustments)
    fun resetSlidersOnly() {
        val current = _recipe.value
        val reset = EditRecipe(
            crop = current.crop,
            masks = current.masks
        ).syncLegacyFields()
        historyManager.commitState(reset, "Reset Sliders Only")
        _recipe.value = reset
        autoSaver.scheduleSave("current_photo", reset)
    }

    // Tip 525: Built-in 4 Demo Scenes
    enum class DemoScene(val title: String) {
        SUNSET_LANDSCAPE("Sunset Landscape"),
        STUDIO_PORTRAIT("Studio Portrait"),
        NEON_NIGHT("Neon Night City"),
        ARCHITECTURE("High-Dynamic Architecture")
    }

    fun loadDemoScene(scene: DemoScene) {
        viewModelScope.launch(Dispatchers.Default) {
            val width = 1200
            val height = 800
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            when (scene) {
                DemoScene.SUNSET_LANDSCAPE -> {
                    val skyPaint = Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, height * 0.65f,
                            intArrayOf(Color.rgb(20, 45, 90), Color.rgb(180, 85, 45), Color.rgb(240, 160, 60)),
                            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height * 0.65f, skyPaint)
                    val sunPaint = Paint().apply { color = Color.rgb(255, 245, 200); isAntiAlias = true }
                    canvas.drawCircle(width * 0.55f, height * 0.52f, 48f, sunPaint)
                    val mountainPaint = Paint().apply { color = Color.rgb(45, 30, 45); isAntiAlias = true }
                    val path = Path().apply {
                        moveTo(0f, height * 0.65f)
                        lineTo(width * 0.25f, height * 0.42f)
                        lineTo(width * 0.5f, height * 0.55f)
                        lineTo(width * 0.75f, height * 0.38f)
                        lineTo(width.toFloat(), height * 0.65f)
                        close()
                    }
                    canvas.drawPath(path, mountainPaint)
                    val waterPaint = Paint().apply {
                        shader = LinearGradient(0f, height * 0.65f, 0f, height.toFloat(),
                            intArrayOf(Color.rgb(90, 45, 30), Color.rgb(20, 25, 40)), null, Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(0f, height * 0.65f, width.toFloat(), height.toFloat(), waterPaint)
                }
                DemoScene.STUDIO_PORTRAIT -> {
                    val bgPaint = Paint().apply {
                        shader = RadialGradient(width * 0.5f, height * 0.4f, width * 0.6f,
                            intArrayOf(Color.rgb(110, 105, 100), Color.rgb(35, 30, 30)), null, Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                    val hairPaint = Paint().apply { color = Color.rgb(40, 25, 15); isAntiAlias = true }
                    canvas.drawCircle(width * 0.5f, height * 0.35f, 160f, hairPaint)
                    val skinPaint = Paint().apply { color = Color.rgb(228, 175, 140); isAntiAlias = true }
                    canvas.drawOval(RectF(width * 0.38f, height * 0.22f, width * 0.62f, height * 0.58f), skinPaint)
                }
                DemoScene.NEON_NIGHT -> {
                    val nightPaint = Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                            intArrayOf(Color.rgb(8, 10, 24), Color.rgb(22, 12, 35)), null, Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), nightPaint)
                    val neonCyan = Paint().apply { color = Color.rgb(0, 230, 255); strokeWidth = 14f; isAntiAlias = true }
                    val neonMagenta = Paint().apply { color = Color.rgb(255, 30, 150); strokeWidth = 14f; isAntiAlias = true }
                    canvas.drawLine(width * 0.2f, height * 0.8f, width * 0.2f, height * 0.2f, neonCyan)
                    canvas.drawLine(width * 0.8f, height * 0.8f, width * 0.8f, height * 0.2f, neonMagenta)
                }
                DemoScene.ARCHITECTURE -> {
                    val skyPaint = Paint().apply { color = Color.rgb(140, 180, 220) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
                    val bldgPaint = Paint().apply {
                        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                            intArrayOf(Color.rgb(240, 240, 245), Color.rgb(60, 65, 75)), null, Shader.TileMode.CLAMP)
                    }
                    val path = Path().apply {
                        moveTo(width * 0.15f, height.toFloat())
                        lineTo(width * 0.35f, height * 0.15f)
                        lineTo(width * 0.75f, height * 0.18f)
                        lineTo(width * 0.9f, height.toFloat())
                        close()
                    }
                    canvas.drawPath(path, bldgPaint)
                }
            }
            withContext(Dispatchers.Main) {
                _bitmap.value = bmp
                val metadata = PhotoExifInfo(
                    fileName = "Demo: ${scene.title}",
                    cameraModel = "CleanLR Virtual Cam",
                    lensModel = "50mm f/1.4",
                    shutterSpeed = "1/250s",
                    aperture = "f/2.8",
                    iso = "100",
                    focalLength = "50mm"
                )
                _photoExif.value = metadata
                _exportStatus.value = "Loaded: ${scene.title}"
            }
        }
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

    private var cropSnapshotBeforeEdit: CropAdjustments = CropAdjustments()

    private val _cropSubPage = MutableStateFlow("Aspect")
    val cropSubPage: StateFlow<String> = _cropSubPage.asStateFlow()

    fun setCropSubPage(page: String) {
        _cropSubPage.value = page
    }

    override fun toggleCropActive() {
        val willBeActive = !_isCropActive.value
        if (willBeActive) {
            cropSnapshotBeforeEdit = _recipe.value.crop
        }
        _isCropActive.value = willBeActive
    }

    fun cancelCrop() {
        _aspectRatioGuide.value = null
        updateRecipe("Cancel Crop") {
            it.copy(crop = cropSnapshotBeforeEdit)
        }
        _isCropActive.value = false
    }

    fun confirmCrop() {
        _isCropActive.value = false
        historyManager.commitState(_recipe.value, "Crop and Expand")
    }

    private fun currentEffectiveImageAspect(): Float {
        val bmp = _bitmap.value
        val w = bmp?.width ?: 1200
        val h = bmp?.height ?: 800
        val (effW, effH) = com.cleanlr.engine.CropGeometryEngine.effectiveImageDimensions(
            w, h, _recipe.value.crop.rotationDegrees
        )
        return (effW / effH).coerceAtLeast(0.05f)
    }

    fun setCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        updateRecipe { current ->
            val minB = if (current.crop.isExpandEnabled) com.cleanlr.engine.CropGeometryEngine.EXPAND_MIN_BOUND else 0f
            val maxB = if (current.crop.isExpandEnabled) com.cleanlr.engine.CropGeometryEngine.EXPAND_MAX_BOUND else 1f
            val minSpan = com.cleanlr.engine.CropGeometryEngine.MIN_CROP_SPAN
            val cl = left.coerceIn(minB, maxB - minSpan)
            val ct = top.coerceIn(minB, maxB - minSpan)
            val cr = right.coerceIn(cl + minSpan, maxB)
            val cb = bottom.coerceIn(ct + minSpan, maxB)
            current.copy(crop = current.crop.copy(left = cl, top = ct, right = cr, bottom = cb))
        }
    }

    fun autoStraighten() {
        val bmp = _bitmap.value
        val currentAngle = _recipe.value.crop.straightenAngle
        if (kotlin.math.abs(currentAngle) >= 0.1f) {
            updateRecipe("Reset Straighten") {
                it.copy(crop = it.crop.copy(straightenAngle = 0.0f))
            }
            return
        }
        if (bmp == null) return
        viewModelScope.launch(Dispatchers.Default) {
            val detected = com.cleanlr.engine.CropGeometryEngine.analyzeStraightenAngle(bmp)
            withContext(Dispatchers.Main) {
                if (kotlin.math.abs(detected) < 0.05f) {
                    _exportStatus.value = "Horizon already level (0.00°)"
                } else {
                    updateRecipe("Auto Straighten") {
                        it.copy(crop = it.crop.copy(straightenAngle = detected))
                    }
                    _exportStatus.value = String.format(java.util.Locale.US, "Auto Straightened: %+.2f°", detected)
                }
            }
        }
    }

    fun toggleAspectLock() {
        updateRecipe("Toggle Aspect Lock") { current ->
            current.copy(crop = current.crop.copy(aspectLocked = !current.crop.aspectLocked))
        }
    }

    fun swapAspectRatioOrientation() {
        val crop = _recipe.value.crop
        val effAspect = currentEffectiveImageAspect()
        val currentRatioStr = crop.aspectRatio
        val currentNumeric = com.cleanlr.engine.CropGeometryEngine.parseAspectRatio(currentRatioStr, effAspect)
        if (currentNumeric != null && kotlin.math.abs(currentNumeric - 1f) > 0.01f) {
            val swappedLabel = if (currentRatioStr.equals("Original", ignoreCase = true)) {
                "Swapped"
            } else {
                com.cleanlr.engine.CropGeometryEngine.swapAspectLabel(currentRatioStr)
            }
            val swappedNumeric = 1f / currentNumeric
            val rect = com.cleanlr.engine.CropGeometryEngine.computeAspectCropRect(
                targetAspect = swappedNumeric,
                effectiveImageAspect = effAspect,
                isExpandEnabled = crop.isExpandEnabled
            )
            _aspectRatioGuide.value = swappedLabel
            updateRecipe("Swap Aspect Orientation") { current ->
                current.copy(
                    crop = current.crop.copy(
                        aspectRatio = swappedLabel,
                        aspectLocked = true,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom
                    )
                )
            }
        } else {
            // Swap current free box's pixel aspect ratio
            val curW = (crop.right - crop.left).coerceAtLeast(0.08f)
            val curH = (crop.bottom - crop.top).coerceAtLeast(0.08f)
            val curPixAspect = (curW / curH) * effAspect
            val rect = com.cleanlr.engine.CropGeometryEngine.computeAspectCropRect(
                targetAspect = 1f / curPixAspect,
                effectiveImageAspect = effAspect,
                isExpandEnabled = crop.isExpandEnabled
            )
            updateRecipe("Swap Crop Orientation") { current ->
                current.copy(crop = current.crop.copy(left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom))
            }
        }
    }

    fun applyAspectRatio(ratioStr: String?) {
        val effAspect = currentEffectiveImageAspect()
        _aspectRatioGuide.value = ratioStr
        updateRecipe("Aspect Ratio: " + (ratioStr ?: "Free")) { current ->
            val targetAspect = com.cleanlr.engine.CropGeometryEngine.parseAspectRatio(ratioStr, effAspect)
            val newCrop = if (ratioStr == null || ratioStr.equals("Free", ignoreCase = true)) {
                current.crop.copy(aspectRatio = "Free", aspectLocked = false)
            } else if (ratioStr.equals("Original", ignoreCase = true)) {
                current.crop.copy(
                    aspectRatio = "Original",
                    aspectLocked = true,
                    left = 0f,
                    top = 0f,
                    right = 1f,
                    bottom = 1f
                )
            } else if (targetAspect != null) {
                val rect = com.cleanlr.engine.CropGeometryEngine.computeAspectCropRect(
                    targetAspect = targetAspect,
                    effectiveImageAspect = effAspect,
                    isExpandEnabled = current.crop.isExpandEnabled
                )
                current.crop.copy(
                    aspectRatio = ratioStr,
                    aspectLocked = true,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom
                )
            } else {
                current.crop.copy(aspectRatio = ratioStr)
            }
            current.copy(crop = newCrop)
        }
    }

    fun rotateCrop(clockwise: Boolean) {
        val current = _recipe.value.crop.rotationDegrees
        val delta = if (clockwise) 90f else -90f
        val next = (current + delta + 360f) % 360f
        updateRecipe("Rotate ${if (clockwise) "CW" else "CCW"}") {
            it.copy(crop = it.crop.copy(rotationDegrees = next))
        }
    }

    fun flipCrop(horizontal: Boolean) {
        updateRecipe(if (horizontal) "Flip Horizontal" else "Flip Vertical") {
            if (horizontal) {
                it.copy(crop = it.crop.copy(flipHorizontal = !it.crop.flipHorizontal))
            } else {
                it.copy(crop = it.crop.copy(flipVertical = !it.crop.flipVertical))
            }
        }
    }

    fun setStraightenAngle(angle: Float) {
        updateRecipe {
            it.copy(crop = it.crop.copy(straightenAngle = angle.coerceIn(-45f, 45f)))
        }
    }

    fun setCropAspectRatio(aspectRatio: String) {
        applyAspectRatio(aspectRatio)
    }

    fun resetCrop() {
        _aspectRatioGuide.value = null
        updateRecipe("Reset Crop") {
            it.copy(crop = CropAdjustments())
        }
    }

    fun resetGeometryOnly() {
        updateRecipe("Reset Geometry") {
            it.copy(
                crop = it.crop.copy(
                    uprightMode = "Off",
                    constrainCrop = false,
                    distortion = 0f,
                    verticalPerspective = 0f,
                    horizontalPerspective = 0f,
                    geometryRotate = 0f,
                    geometryAspect = 0f,
                    geometryScale = 100f,
                    geometryXOffset = 0f,
                    geometryYOffset = 0f,
                    guidedLines = emptyList()
                )
            )
        }
    }

    fun setExpandEnabled(enabled: Boolean) {
        updateRecipe(if (enabled) "Enable Expand" else "Disable Expand") { current ->
            val c = current.crop
            val updatedCrop = if (!enabled) {
                // Clamp crop boundaries cleanly back inside [0..1] when disabling Expand
                val l = c.left.coerceIn(0f, 0.92f)
                val t = c.top.coerceIn(0f, 0.92f)
                val r = c.right.coerceIn(l + 0.08f, 1f)
                val b = c.bottom.coerceIn(t + 0.08f, 1f)
                c.copy(isExpandEnabled = false, left = l, top = t, right = r, bottom = b)
            } else {
                c.copy(isExpandEnabled = true)
            }
            current.copy(crop = updatedCrop)
        }
    }

    fun triggerGenerativeExpandPreview() {
        val bmp = _bitmap.value ?: return
        val region = com.cleanlr.engine.GenerativeExpandEngine.computeExpandRegion(
            _recipe.value.crop, bmp.width, bmp.height
        )
        if (!region.hasExpansion) {
            _exportStatus.value = "Drag crop handles outward onto checkerboard to expand"
            return
        }
        val cfg = _genAiConfig.value
        val hasConnectedLanBridge = discoveredLanServers.value.isNotEmpty() &&
            cfg.backend == com.cleanlr.engine.GenerativeAiBackend.LOCAL_LAN_BRIDGE &&
            cfg.isReady
        val hasCloudKey = cfg.backend != com.cleanlr.engine.GenerativeAiBackend.LOCAL_LAN_BRIDGE && cfg.isReady
        val useRemoteServer = hasConnectedLanBridge || hasCloudKey

        _isGenAiProcessing.value = true
        _exportStatus.value = if (useRemoteServer) {
            "Desktop LAN / FLUX generating expansion (${region.outputWidth}×${region.outputHeight})..."
        } else {
            "Offline SD-Turbo INT8 outpainting (${region.outputWidth}×${region.outputHeight})..."
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                var expandedResultBmp: Bitmap? = null
                var statusMsg = ""

                if (useRemoteServer) {
                    val provider = com.cleanlr.engine.FluxFillExpandProvider(cfg)
                    com.cleanlr.engine.GenerativeExpandEngine.activeProvider = provider
                    val (padded, mask) = com.cleanlr.engine.GenerativeExpandEngine.buildOutpaintInputs(bmp, region)
                    val remoteRes = provider.generateExpansion(padded, mask, region)
                    if (remoteRes.isSuccess) {
                        expandedResultBmp = remoteRes.getOrNull()
                        statusMsg = if (cfg.usePoissonBlending) {
                            "LAN / FLUX Expand applied with Poisson Blending!"
                        } else {
                            "LAN / FLUX Expand applied!"
                        }
                    }
                }

                // Default Offline Mobile AI Expand (sd_turbo_inpaint_int8.onnx) whenever LAN Bridge is not connected/reachable
                if (expandedResultBmp == null) {
                    val offlineOut = com.cleanlr.ai.AiVisionPipeline.runOfflineMobileExpand(
                        originalBitmap = bmp,
                        region = region,
                        modelManager = modelManager,
                        onProgress = { msg -> _exportStatus.value = msg }
                    )
                    expandedResultBmp = offlineOut.bitmap
                    val modeTag = if (offlineOut.usedNativeOnnx) "SD-Turbo INT8 (${offlineOut.delegateName})" else "Offline SD-Turbo Tiled"
                    statusMsg = "$modeTag Expand applied (${offlineOut.tilesProcessed} tiles, 24px cosine feather + grain match)!"
                }

                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    pushBitmapUndoSnapshot(bmp)
                    _bitmap.value = expandedResultBmp
                    updateRecipe("Apply Generative Expand") {
                        it.copy(crop = it.crop.copy(left = 0f, top = 0f, right = 1f, bottom = 1f, isExpandEnabled = false))
                    }
                    _exportStatus.value = statusMsg
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    _exportStatus.value = "Expand failed: ${e.message}"
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SOTA FLUX.1 FILL + POISSON BLENDING (GENERATIVE REMOVE & SERVER CONFIG)
    // ═════════════════════════════════════════════════════════════════════════

    fun openGenAiSettingsDialog() {
        _showGenAiSettingsDialog.value = true
    }

    fun dismissGenAiSettingsDialog() {
        _showGenAiSettingsDialog.value = false
    }

    fun openModelManagerSheet() {
        modelManager.refreshAllStates()
        _showModelManagerSheet.value = true
    }

    fun dismissModelManagerSheet() {
        lanBridge.stopDiscovery()
        _showModelManagerSheet.value = false
    }

    fun downloadByomModel(modelId: String) {
        viewModelScope.launch {
            val res = modelManager.downloadModel(modelId)
            res.onSuccess {
                _exportStatus.value = "Downloaded & verified ${modelId} (SHA-256 OK)"
            }.onFailure { err ->
                if (err.message != "Download paused") {
                    _exportStatus.value = "Download error: ${err.message}"
                }
            }
        }
    }

    fun pauseByomModelDownload(modelId: String) {
        modelManager.pauseDownload(modelId)
    }

    fun importLocalByomModel(modelId: String, uri: Uri) {
        viewModelScope.launch {
            val res = modelManager.importLocalModel(modelId, uri)
            res.onSuccess {
                _exportStatus.value = "Imported local model: ${it.name}"
            }.onFailure { err ->
                _exportStatus.value = "Import failed: ${err.message}"
            }
        }
    }

    fun deleteByomModel(modelId: String) {
        modelManager.deleteModel(modelId)
        _exportStatus.value = "Removed model $modelId from storage"
    }

    fun startLanBridgeScan() {
        lanBridge.startDiscovery()
        _exportStatus.value = "Scanning Wi-Fi LAN for CleanLR Desktop (mDNS)..."
    }

    fun connectLanServer(server: com.cleanlr.ai.DiscoveredLanServer) {
        val cfg = _genAiConfig.value.copy(
            backend = com.cleanlr.engine.GenerativeAiBackend.LOCAL_LAN_BRIDGE,
            endpointUrl = server.endpointUrl,
            modelId = server.modelName,
            apiKey = ""
        )
        saveGenAiConfig(cfg)
        _exportStatus.value = "Connected to LAN Desktop: ${server.serviceName}"
    }

    fun cancelLensBlurDepthDetection() {
        lensBlurDepthJob?.cancel()
        lensBlurDepthJob = null
        _isLensBlurDetecting.value = false
    }

    /**
     * Phase 3: Builds the complete 5-Stage Optical Lens Blur Pack sequentially:
     * BiRefNet AlphaMatte -> DepthAnythingV2 16-bit Depth -> LaMa Clean-Plate -> Noise Sigma.
     * Runs non-blocking on its own cancellable job (never blocks UI touches with GenAI modal).
     */
    fun buildOpticalCleanPlateAndDepth() {
        val bmp = _bitmap.value ?: return
        if (_isLensBlurDetecting.value || _opticalBlurPack.value != null) return
        _isLensBlurDetecting.value = true
        lensBlurDepthJob?.cancel()
        lensBlurDepthJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val pack = com.cleanlr.ai.AiVisionPipeline.buildOpticalBlurPack(
                    bitmap = bmp,
                    modelManager = modelManager,
                    onProgress = { msg -> _opticalStageStatus.value = msg }
                )
                withContext(Dispatchers.Main) {
                    _opticalBlurPack.value = pack
                    _opticalStageStatus.value = "Depth ready"
                    _isLensBlurDetecting.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    _isLensBlurDetecting.value = false
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLensBlurDetecting.value = false
                    _opticalStageStatus.value = "Depth unavailable: ${e.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        lanBridge.stopDiscovery()
    }

    fun saveGenAiConfig(config: com.cleanlr.engine.GenerativeAiConfig) {
        _genAiConfig.value = config
        com.cleanlr.engine.GenerativeAiConfigStore.save(getApplication<Application>(), config)
        com.cleanlr.engine.GenerativeExpandEngine.activeProvider =
            if (config.isReady) com.cleanlr.engine.FluxFillExpandProvider(config)
            else com.cleanlr.engine.GenerativeExpandEngine.UnconfiguredExpandProvider()
        _showGenAiSettingsDialog.value = false
        _exportStatus.value = "Saved AI Engine: ${config.backend.displayName}"
    }

    fun setUseGenerativeAiRemove(enabled: Boolean) {
        _useGenerativeAiRemove.value = enabled
    }

    fun setRemoveToolMode(mode: com.cleanlr.engine.RemoveToolMode) {
        _removeToolMode.value = mode
        _isRefineEraseMode.value = false
        if (mode == com.cleanlr.engine.RemoveToolMode.REMOVE) {
            _healCloneSourceNorm.value = null
        } else if (_removeBrushPoints.value.isNotEmpty() && _healCloneSourceNorm.value == null) {
            val avgX = _removeBrushPoints.value.map { it.x }.average().toFloat()
            val avgY = _removeBrushPoints.value.map { it.y }.average().toFloat()
            val sx = if (avgX < 0.72f) (avgX + 0.16f).coerceIn(0.05f, 0.95f) else (avgX - 0.16f).coerceIn(0.05f, 0.95f)
            _healCloneSourceNorm.value = Pair(sx, avgY.coerceIn(0.05f, 0.95f))
        }
    }

    fun setHealFeather(value: Float) {
        _healFeather.value = value.coerceIn(0f, 100f)
    }

    fun setHealOpacity(value: Float) {
        _healOpacity.value = value.coerceIn(0f, 100f)
    }

    fun setDetectObjectsRemove(enabled: Boolean) {
        _detectObjectsRemove.value = enabled
    }

    fun toggleRefineEraseMode() {
        _isRefineEraseMode.value = !_isRefineEraseMode.value
    }

    fun setHealCloneSourceNorm(nx: Float, ny: Float) {
        _healCloneSourceNorm.value = Pair(nx.coerceIn(0.02f, 0.98f), ny.coerceIn(0.02f, 0.98f))
    }

    fun addRemoveBrushPoint(normX: Float, normY: Float, isNewStroke: Boolean = false) {
        val cx = normX.coerceIn(0f, 1f)
        val cy = normY.coerceIn(0f, 1f)
        val curRadius = _brushSize.value.coerceIn(4f, 100f)

        if (_isRefineEraseMode.value) {
            val eraseRadiusNorm = (curRadius / 360f).coerceIn(0.015f, 0.25f)
            _removeBrushPoints.value = _removeBrushPoints.value.filter { pt ->
                kotlin.math.hypot(pt.x - cx, pt.y - cy) > eraseRadiusNorm
            }
            if (_removeBrushPoints.value.isEmpty()) {
                _isRefineEraseMode.value = false
                _healCloneSourceNorm.value = null
                _removeStrokeBreakIndices.value = emptySet()
            }
            return
        }

        val existing = _removeBrushPoints.value
        val added = ArrayList<BrushStrokePoint>()
        if (isNewStroke || existing.isEmpty()) {
            _removeStrokeBreakIndices.value = _removeStrokeBreakIndices.value + existing.size
            added.add(BrushStrokePoint(x = cx, y = cy, radius = curRadius))
        } else {
            val prev = existing.last()
            val dx = cx - prev.x
            val dy = cy - prev.y
            val dist = kotlin.math.hypot(dx, dy)
            // Dense sub-radius interpolation prevents dot-dot gaps during fast finger swipes
            val stepNorm = ((curRadius / 400f) * 0.22f).coerceIn(0.0035f, 0.018f)
            val steps = kotlin.math.ceil(dist / stepNorm).toInt().coerceIn(1, 36)
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                added.add(
                    BrushStrokePoint(
                        x = (prev.x + dx * t).coerceIn(0f, 1f),
                        y = (prev.y + dy * t).coerceIn(0f, 1f),
                        radius = curRadius
                    )
                )
            }
        }
        _removeBrushPoints.value = existing + added

        // Auto-place draggable Lightroom source pin for Heal & Clone on first painted stroke
        if (_removeToolMode.value != com.cleanlr.engine.RemoveToolMode.REMOVE && _healCloneSourceNorm.value == null) {
            val sx = if (cx < 0.72f) (cx + 0.16f).coerceIn(0.05f, 0.95f) else (cx - 0.16f).coerceIn(0.05f, 0.95f)
            _healCloneSourceNorm.value = Pair(sx, cy.coerceIn(0.05f, 0.95f))
        }
    }

    fun clearRemoveBrushPoints() {
        _removeBrushPoints.value = emptyList()
        _removeStrokeBreakIndices.value = emptySet()
        _isRefineEraseMode.value = false
        _healCloneSourceNorm.value = null
    }

    /**
     * Floating "Remove extra people" pill action:
     * Uses bundled BiRefNet-Lite + LaMa FP16 to clean peripheral background clutter/bystanders
     * while preserving the primary centered portrait subject.
     */
    fun executeRemoveExtraPeople() {
        val bmp = _bitmap.value ?: return
        if (_isGenAiProcessing.value) return
        _isGenAiProcessing.value = true
        _exportStatus.value = "Detecting & removing background bystanders (Offline AI)..."

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Sample edge peripheral bystanders while protecting center subject
                val pack = com.cleanlr.ai.AiVisionPipeline.buildOpticalBlurPack(
                    bitmap = bmp,
                    modelManager = modelManager,
                    onProgress = { msg -> _exportStatus.value = "Remove extra people: $msg" }
                )
                // Synthesize peripheral distraction mask along outer left/right margins (keeping main subject untouched)
                val w = bmp.width
                val h = bmp.height
                val alphaScaled = Bitmap.createScaledBitmap(pack.alphaMatte, w, h, true)
                val cleanScaled = Bitmap.createScaledBitmap(pack.cleanBackgroundPlate, w, h, true)
                val origPx = IntArray(w * h)
                val alphaPx = IntArray(w * h)
                val cleanPx = IntArray(w * h)
                bmp.getPixels(origPx, 0, w, 0, 0, w, h)
                alphaScaled.getPixels(alphaPx, 0, w, 0, 0, w, h)
                cleanScaled.getPixels(cleanPx, 0, w, 0, 0, w, h)

                val outPx = origPx.copyOf()
                var modified = 0
                for (y in 0 until h) {
                    val ny = y.toFloat() / h
                    val row = y * w
                    for (x in 0 until w) {
                        val nx = x.toFloat() / w
                        val inPeriphery = (nx < 0.22f || nx > 0.78f) && ny in 0.25f..0.92f
                        if (!inPeriphery) continue
                        val a = android.graphics.Color.red(alphaPx[row + x]) / 255f
                        if (a > 0.45f) {
                            // Secondary foreground figure detected in outer margin -> replace cleanly with LaMa plate
                            outPx[row + x] = cleanPx[row + x]
                            modified++
                        }
                    }
                }
                alphaScaled.recycle()
                cleanScaled.recycle()
                pack.decontaminatedForeground.recycle()
                pack.cleanBackgroundPlate.recycle()
                pack.alphaMatte.recycle()
                pack.depthMap16Bit.recycle()

                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    if (modified > 32) {
                        val updated = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                            setPixels(outPx, 0, w, 0, 0, w, h)
                        }
                        pushBitmapUndoSnapshot(bmp)
                        _bitmap.value = updated
                        _exportStatus.value = "Removed peripheral extra people (Offline LaMa FP16)!"
                    } else {
                        _exportStatus.value = "No extra background people detected — brush over any object to remove"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    _exportStatus.value = "Remove extra people failed: ${e.message}"
                }
            }
        }
    }

    private fun pushBitmapUndoSnapshot(bmp: Bitmap) {
        if (bitmapUndoStack.size >= 5) bitmapUndoStack.removeFirst()
        bitmapUndoStack.addLast(bmp)
    }

    fun undoLastGenerativeEdit() {
        if (bitmapUndoStack.isEmpty()) return
        val prev = bitmapUndoStack.removeLast()
        _bitmap.value = prev
        _exportStatus.value = "Restored photo before Generative edit"
    }

    fun canUndoGenerativeEdit(): Boolean = bitmapUndoStack.isNotEmpty()

    fun executeRemoveStrokes(customPrompt: String? = null) {
        val bmp = _bitmap.value ?: return
        val strokes = _removeBrushPoints.value
        if (strokes.isEmpty()) {
            _exportStatus.value = "Paint over an area on the photo first"
            return
        }

        val activeMode = _removeToolMode.value
        val featherVal = _healFeather.value
        val opacityVal = _healOpacity.value
        val sourcePin = _healCloneSourceNorm.value
        val detectObj = _detectObjectsRemove.value

        // ── MODE: HEAL or CLONE (Instant On-Device Spot Heal / Clone Stamp) ──
        if (activeMode == com.cleanlr.engine.RemoveToolMode.HEAL || activeMode == com.cleanlr.engine.RemoveToolMode.CLONE) {
            _isGenAiProcessing.value = true
            _exportStatus.value = if (activeMode == com.cleanlr.engine.RemoveToolMode.HEAL) {
                "Applying Content-Aware Heal (Feather ${featherVal.toInt()}%, Opacity ${opacityVal.toInt()}%)..."
            } else {
                "Applying Clone Stamp (Feather ${featherVal.toInt()}%, Opacity ${opacityVal.toInt()}%)..."
            }
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val updated = if (activeMode == com.cleanlr.engine.RemoveToolMode.HEAL) {
                        com.cleanlr.engine.HealCloneEngine.applyHeal(
                            originalBitmap = bmp,
                            strokes = strokes,
                            feather = featherVal,
                            opacity = opacityVal,
                            sourceNorm = sourcePin
                        )
                    } else {
                        com.cleanlr.engine.HealCloneEngine.applyClone(
                            originalBitmap = bmp,
                            strokes = strokes,
                            feather = featherVal,
                            opacity = opacityVal,
                            sourceNorm = sourcePin
                        )
                    }
                    withContext(Dispatchers.Main) {
                        pushBitmapUndoSnapshot(bmp)
                        _bitmap.value = updated
                        _removeBrushPoints.value = emptyList()
                        _healCloneSourceNorm.value = null
                        _isRefineEraseMode.value = false
                        _isGenAiProcessing.value = false
                        _exportStatus.value = "${activeMode.label} applied!"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _isGenAiProcessing.value = false
                        _exportStatus.value = "${activeMode.label} failed: ${e.message}"
                    }
                }
            }
            return
        }

        // ── MODE: REMOVE (Generative AI LaMa FP16 / Optional LAN FLUX / Local Poisson + Optional MobileSAM Detect Objects) ──
        val cfg = _genAiConfig.value
        val hasConnectedLanBridge = discoveredLanServers.value.isNotEmpty() &&
            cfg.backend == com.cleanlr.engine.GenerativeAiBackend.LOCAL_LAN_BRIDGE &&
            cfg.isReady
        val hasCloudKey = cfg.backend != com.cleanlr.engine.GenerativeAiBackend.LOCAL_LAN_BRIDGE && cfg.isReady
        val useGenAi = _useGenerativeAiRemove.value
        val useRemoteFlux = useGenAi && (hasConnectedLanBridge || hasCloudKey)

        _isGenAiProcessing.value = true
        _exportStatus.value = when {
            useRemoteFlux -> "Extracting Crop+Mask ROI & calling Desktop LAN / FLUX..."
            useGenAi && detectObj -> "Detecting object (MobileSAM INT8) & removing via LaMa FP16..."
            useGenAi -> "Running Bundled Offline LaMa FP16 + Poisson Blending..."
            else -> "Running instant Content-Aware Poisson Remove..."
        }

        viewModelScope.launch(Dispatchers.Default) {
            val extracted = com.cleanlr.engine.RoiPatchExtractor.extractRemoveRoi(bmp, strokes)
            if (extracted == null) {
                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    _exportStatus.value = "Invalid mask region"
                }
                return@launch
            }

            try {
                // If Detect Objects is ON, snap mask to object silhouette via MobileSAM INT8
                val effectiveMask: Bitmap = if (detectObj) {
                    val avgX = strokes.map { it.x }.average().toFloat()
                    val avgY = strokes.map { it.y }.average().toFloat()
                    val samMask = com.cleanlr.ai.AiVisionPipeline.segmentInteractive(
                        bitmap = bmp,
                        modelManager = modelManager,
                        tapNormX = avgX,
                        tapNormY = avgY
                    )
                    val croppedSam = Bitmap.createScaledBitmap(samMask, bmp.width, bmp.height, true)
                    samMask.recycle()
                    val roiSam = Bitmap.createBitmap(
                        croppedSam,
                        extracted.roiBounds.left,
                        extracted.roiBounds.top,
                        extracted.roiBounds.width,
                        extracted.roiBounds.height
                    )
                    croppedSam.recycle()
                    val rw = extracted.binaryMask.width
                    val rh = extracted.binaryMask.height
                    val merged = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                    val bPx = IntArray(rw * rh)
                    val sPx = IntArray(rw * rh)
                    extracted.binaryMask.getPixels(bPx, 0, rw, 0, 0, rw, rh)
                    val scaledRoiSam = Bitmap.createScaledBitmap(roiSam, rw, rh, true)
                    roiSam.recycle()
                    scaledRoiSam.getPixels(sPx, 0, rw, 0, 0, rw, rh)
                    scaledRoiSam.recycle()
                    for (i in bPx.indices) {
                        if (android.graphics.Color.red(sPx[i]) > 140) bPx[i] = android.graphics.Color.WHITE
                    }
                    merged.setPixels(bPx, 0, rw, 0, 0, rw, rh)
                    merged
                } else {
                    extracted.binaryMask
                }

                var usedRemote = false
                val updatedBitmap: Bitmap = when {
                    useRemoteFlux -> {
                        val client = com.cleanlr.engine.FluxFillClient(cfg)
                        val patchResult = client.generatePatch(
                            croppedImage = extracted.croppedImage,
                            binaryMask = effectiveMask,
                            prompt = customPrompt?.takeIf { it.isNotBlank() }
                                ?: com.cleanlr.engine.FluxFillClient.DEFAULT_REMOVE_PROMPT,
                            mode = "remove",
                            uploadWidth = extracted.uploadWidth,
                            uploadHeight = extracted.uploadHeight
                        )
                        if (patchResult.isSuccess) {
                            usedRemote = true
                            val patch = patchResult.getOrThrow()
                            val blended = com.cleanlr.engine.PoissonBlender.blendPatchIntoOriginal(
                                originalBitmap = bmp,
                                sourcePatch = patch,
                                maskBitmap = effectiveMask,
                                offsetX = extracted.roiBounds.left,
                                offsetY = extracted.roiBounds.top,
                                iterations = if (cfg.usePoissonBlending) 120 else 1,
                                mixedGradients = false
                            )
                            patch.recycle()
                            blended
                        } else {
                            com.cleanlr.ai.AiVisionPipeline.runOfflineLamaRemove(
                                originalBitmap = bmp,
                                strokes = strokes,
                                modelManager = modelManager
                            )
                        }
                    }
                    useGenAi -> {
                        com.cleanlr.ai.AiVisionPipeline.runOfflineLamaRemove(
                            originalBitmap = bmp,
                            strokes = strokes,
                            modelManager = modelManager
                        )
                    }
                    else -> {
                        val localPatch = com.cleanlr.engine.PoissonBlender.localContentAwareRemove(
                            originalBitmap = extracted.croppedImage,
                            maskBitmap = effectiveMask,
                            offsetX = 0,
                            offsetY = 0,
                            iterations = 180
                        )
                        com.cleanlr.engine.PoissonBlender.blendPatchIntoOriginal(
                            originalBitmap = bmp,
                            sourcePatch = localPatch,
                            maskBitmap = effectiveMask,
                            offsetX = extracted.roiBounds.left,
                            offsetY = extracted.roiBounds.top,
                            iterations = 100
                        ).also { localPatch.recycle() }
                    }
                }

                if (effectiveMask !== extracted.binaryMask) effectiveMask.recycle()
                extracted.croppedImage.recycle()
                extracted.binaryMask.recycle()

                withContext(Dispatchers.Main) {
                    pushBitmapUndoSnapshot(bmp)
                    _bitmap.value = updatedBitmap
                    _removeBrushPoints.value = emptyList()
                    _isRefineEraseMode.value = false
                    _isGenAiProcessing.value = false
                    _exportStatus.value = when {
                        usedRemote -> "FLUX LAN + Poisson Blend complete!"
                        useGenAi -> "Generative AI Remove (Offline LaMa FP16) applied!"
                        else -> "Content-Aware Remove applied!"
                    }
                }
            } catch (e: Exception) {
                extracted.croppedImage.recycle()
                extracted.binaryMask.recycle()
                withContext(Dispatchers.Main) {
                    _isGenAiProcessing.value = false
                    _exportStatus.value = "Remove failed: ${e.message}"
                }
            }
        }
    }

    fun setConstrainCrop(constrain: Boolean) {
        updateRecipe("Constrain Crop: $constrain") {
            it.copy(crop = it.crop.copy(constrainCrop = constrain))
        }
    }

    fun setUprightMode(mode: String) {
        val currentMode = _recipe.value.crop.uprightMode
        // Tapping active mode turns Upright Off
        if (currentMode.equals(mode, ignoreCase = true) && !mode.equals("Off", ignoreCase = true)) {
            updateRecipe("Upright: Off") {
                it.copy(
                    crop = it.crop.copy(
                        uprightMode = "Off",
                        verticalPerspective = 0f,
                        horizontalPerspective = 0f,
                        geometryRotate = 0f,
                        geometryAspect = 0f
                    )
                )
            }
            return
        }

        if (mode.equals("Guided", ignoreCase = true)) {
            val existing = _recipe.value.crop.guidedLines
            val guides = if (existing.isEmpty()) {
                listOf(
                    GuidedLine("g1", 0.25f, 0.18f, 0.22f, 0.82f),
                    GuidedLine("g2", 0.75f, 0.18f, 0.78f, 0.82f)
                )
            } else existing
            val solved = com.cleanlr.engine.CropGeometryEngine.solveGuidedUpright(guides, currentEffectiveImageAspect())
            updateRecipe("Upright: Guided") {
                it.copy(
                    crop = it.crop.copy(
                        uprightMode = "Guided",
                        guidedLines = guides,
                        verticalPerspective = solved.verticalPerspective,
                        horizontalPerspective = solved.horizontalPerspective,
                        geometryRotate = solved.geometryRotate
                    )
                )
            }
            return
        }

        if (mode.equals("Off", ignoreCase = true)) {
            updateRecipe("Upright: Off") {
                it.copy(
                    crop = it.crop.copy(
                        uprightMode = "Off",
                        verticalPerspective = 0f,
                        horizontalPerspective = 0f,
                        geometryRotate = 0f,
                        geometryAspect = 0f
                    )
                )
            }
            return
        }

        val bmp = _bitmap.value
        if (bmp == null) {
            updateRecipe("Upright: $mode") { it.copy(crop = it.crop.copy(uprightMode = mode)) }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val result = com.cleanlr.engine.CropGeometryEngine.analyzeUpright(bmp, mode)
            withContext(Dispatchers.Main) {
                updateRecipe("Upright: $mode") {
                    it.copy(
                        crop = it.crop.copy(
                            uprightMode = mode,
                            verticalPerspective = result.verticalPerspective,
                            horizontalPerspective = result.horizontalPerspective,
                            geometryRotate = result.geometryRotate,
                            geometryAspect = result.geometryAspect
                        )
                    )
                }
                _exportStatus.value = "Upright ($mode): V=${result.verticalPerspective.toInt()}, H=${result.horizontalPerspective.toInt()}, R=${String.format(java.util.Locale.US, "%.1f°", result.geometryRotate)}"
            }
        }
    }

    fun addGuidedLine() {
        val current = _recipe.value.crop.guidedLines
        if (current.size >= 4) {
            _exportStatus.value = "Maximum 4 Guided Upright lines reached"
            return
        }
        val newLine = when (current.size) {
            0 -> GuidedLine("g1", 0.25f, 0.18f, 0.25f, 0.82f)
            1 -> GuidedLine("g2", 0.75f, 0.18f, 0.75f, 0.82f)
            2 -> GuidedLine("g3", 0.18f, 0.30f, 0.82f, 0.30f)
            else -> GuidedLine("g4", 0.18f, 0.70f, 0.82f, 0.70f)
        }
        val updated = current + newLine
        val solved = com.cleanlr.engine.CropGeometryEngine.solveGuidedUpright(updated, currentEffectiveImageAspect())
        updateRecipe("Add Guided Line") {
            it.copy(
                crop = it.crop.copy(
                    uprightMode = "Guided",
                    guidedLines = updated,
                    verticalPerspective = solved.verticalPerspective,
                    horizontalPerspective = solved.horizontalPerspective,
                    geometryRotate = solved.geometryRotate
                )
            )
        }
    }

    fun updateGuidedLine(id: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        val updated = _recipe.value.crop.guidedLines.map { line ->
            if (line.id == id) {
                line.copy(
                    x1 = x1.coerceIn(0.02f, 0.98f),
                    y1 = y1.coerceIn(0.02f, 0.98f),
                    x2 = x2.coerceIn(0.02f, 0.98f),
                    y2 = y2.coerceIn(0.02f, 0.98f)
                )
            } else line
        }
        val solved = com.cleanlr.engine.CropGeometryEngine.solveGuidedUpright(updated, currentEffectiveImageAspect())
        updateRecipe {
            it.copy(
                crop = it.crop.copy(
                    guidedLines = updated,
                    verticalPerspective = solved.verticalPerspective,
                    horizontalPerspective = solved.horizontalPerspective,
                    geometryRotate = solved.geometryRotate
                )
            )
        }
    }

    fun clearGuidedLines() {
        updateRecipe("Clear Guided Lines") {
            it.copy(
                crop = it.crop.copy(
                    guidedLines = emptyList(),
                    verticalPerspective = 0f,
                    horizontalPerspective = 0f,
                    geometryRotate = 0f
                )
            )
        }
    }

    fun setDistortion(distortion: Float) {
        updateRecipe {
            it.copy(crop = it.crop.copy(distortion = distortion.coerceIn(-100f, 100f)))
        }
    }

    fun setVerticalPerspective(vert: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(verticalPerspective = vert.coerceIn(-100f, 100f))) }
    }

    fun setHorizontalPerspective(horiz: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(horizontalPerspective = horiz.coerceIn(-100f, 100f))) }
    }

    fun setGeometryRotate(rot: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(geometryRotate = rot.coerceIn(-45f, 45f))) }
    }

    fun setGeometryAspect(aspect: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(geometryAspect = aspect.coerceIn(-100f, 100f))) }
    }

    fun setGeometryScale(scale: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(geometryScale = scale.coerceIn(50f, 200f))) }
    }

    fun setGeometryXOffset(xOff: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(geometryXOffset = xOff.coerceIn(-100f, 100f))) }
    }

    fun setGeometryYOffset(yOff: Float) {
        updateRecipe { it.copy(crop = it.crop.copy(geometryYOffset = yOff.coerceIn(-100f, 100f))) }
    }

    override fun setActiveTool(tool: ActiveTool?) {
        _activeTool.value = tool
    }

    override fun selectBrushTool() = setActiveTool(ActiveTool.BRUSH)
    override fun selectLinearGradientMask() = setActiveTool(ActiveTool.LINEAR_GRADIENT)
    override fun selectRadialGradientMask() = setActiveTool(ActiveTool.RADIAL_GRADIENT)

    override fun toggleMaskOverlay() {
        val next = !_showMaskOverlay.value
        _showMaskOverlay.value = next
        _isMaskOverlayVisible.value = next
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

    private val _clipboardSelection = MutableStateFlow(CopySettingsSelection())
    val clipboardSelection: StateFlow<CopySettingsSelection> = _clipboardSelection.asStateFlow()

    override fun copyEditSettings() {
        _clipboardRecipe.value = _recipe.value
        showTransientToast("Settings copied")
    }

    fun copyEditSettingsSelective(selection: CopySettingsSelection) {
        _clipboardRecipe.value = _recipe.value
        _clipboardSelection.value = selection
        showTransientToast("Settings copied")
    }

    override fun pasteEditSettings() {
        pasteEditSettingsSelective()
    }

    fun pasteEditSettingsSelective() {
        val copied = _clipboardRecipe.value ?: return
        val sel = _clipboardSelection.value
        val cur = _recipe.value
        var next = cur
        if (sel.tools) {
            next = next.copy(crop = copied.crop)
        }
        if (sel.profile) {
            next = next.copy(profile = copied.profile)
        }
        if (sel.light) {
            next = next.copy(
                basic = next.basic.copy(
                    exposure = copied.basic.exposure,
                    contrast = copied.basic.contrast,
                    highlights = copied.basic.highlights,
                    shadows = copied.basic.shadows,
                    whites = copied.basic.whites,
                    blacks = copied.basic.blacks
                ),
                curves = copied.curves
            )
        }
        if (sel.color) {
            next = next.copy(
                basic = next.basic.copy(
                    temperature = copied.basic.temperature,
                    tint = copied.basic.tint,
                    vibrance = copied.basic.vibrance,
                    saturation = copied.basic.saturation
                ),
                hsl = copied.hsl,
                colorGrading = copied.colorGrading
            )
        }
        if (sel.effects) {
            next = next.copy(effects = copied.effects)
        }
        if (sel.detail) {
            next = next.copy(detail = copied.detail)
        }
        if (sel.optics) {
            next = next.copy(optics = copied.optics)
        }
        if (sel.geometry) {
            next = next.copy(
                crop = next.crop.copy(
                    distortion = copied.crop.distortion,
                    verticalPerspective = copied.crop.verticalPerspective,
                    horizontalPerspective = copied.crop.horizontalPerspective,
                    uprightMode = copied.crop.uprightMode,
                    geometryRotate = copied.crop.geometryRotate,
                    geometryAspect = copied.crop.geometryAspect,
                    geometryScale = copied.crop.geometryScale,
                    geometryXOffset = copied.crop.geometryXOffset,
                    geometryYOffset = copied.crop.geometryYOffset,
                    guidedLines = copied.crop.guidedLines
                )
            )
        }
        if (sel.masks) {
            next = next.copy(masks = copied.masks)
        }
        next = next.syncLegacyFields()
        historyManager.commitState(next, "Paste Settings")
        _recipe.value = next
        autoSaver.scheduleSave("current_photo", next)
        showTransientToast("Settings applied")
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

    fun exportImage(config: com.cleanlr.export.ExportConfig = com.cleanlr.export.ExportConfig()) {
        val bmp = _bitmap.value ?: return
        val current = _recipe.value
        _exportStatus.value = "Exporting..."

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val context = getApplication<Application>()
                val uri = _sourceUri.value
                if (uri != null) {
                    val workId = com.cleanlr.export.ExportManager.enqueueSingle(context, uri, current, config)
                    _exportStatus.value = "Export scheduled (0%)..."
                    WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).collect { workInfo ->
                        if (workInfo != null) {
                            when (workInfo.state) {
                                androidx.work.WorkInfo.State.RUNNING -> {
                                    val prog = workInfo.progress.getInt("progress", 0)
                                    _exportStatus.value = if (prog > 0) "Exporting ($prog%)..." else "Exporting..."
                                }
                                androidx.work.WorkInfo.State.SUCCEEDED -> {
                                    val outUriStr = workInfo.outputData.getString(com.cleanlr.export.ExportImageWorker.KEY_OUTPUT_URI)
                                    if (outUriStr != null) {
                                        _lastExportedUri.value = android.net.Uri.parse(outUriStr)
                                    }
                                    _lastExportedFormat.value = config.format
                                    _exportStatus.value = "Exported to Pictures/CleanLightroom"
                                    if (config.autoShare && _lastExportedUri.value != null) {
                                        shareLastExport(context)
                                    }
                                }
                                androidx.work.WorkInfo.State.FAILED -> {
                                    _exportStatus.value = "Export failed"
                                }
                                else -> {}
                            }
                        }
                    }
                } else {
                    // Export directly via Headless Renderer
                    val renderer = com.cleanlr.export.HeadlessGlRenderer(context)
                    val rendered = renderer.renderFullResolution(bmp, current)
                    val outputUri = com.cleanlr.export.MediaStoreWriter.saveImage(
                        context = context,
                        bitmap = rendered,
                        config = config,
                        recipe = current
                    )
                    _lastExportedUri.value = outputUri
                    _lastExportedFormat.value = config.format
                    _exportStatus.value = "Exported to Pictures/CleanLightroom"

                    if (config.autoShare) {
                        shareLastExport(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Export failed", e)
                _exportStatus.value = "Export failed"
            }
        }
    }

    fun shareLastExport(context: Context) {
        val uri = _lastExportedUri.value ?: return
        val mimeType = com.cleanlr.export.MediaStoreWriter.getMimeType(_lastExportedFormat.value)
        com.cleanlr.export.MediaStoreWriter.launchShare(context, uri, mimeType)
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    fun startBatchExport(items: List<BatchExportItem>) {
        if (items.isEmpty()) return
        val manager = BatchExportManager.instance
        val batchId = java.util.UUID.randomUUID().toString()
        currentBatchId = batchId
        _isBatchExportDialogVisible.value = true

        val context = getApplication<Application>()
        val processor = com.cleanlr.export.DefaultBatchItemProcessor(context)

        viewModelScope.launch(Dispatchers.Default) {
            manager.executeBatch(
                batchId = batchId,
                items = items,
                processor = processor,
                onProgressUpdate = { progress ->
                    _activeBatchProgress.value = progress
                }
            )
        }
    }

    fun cancelActiveBatch() {
        val id = currentBatchId ?: return
        BatchExportManager.instance.cancelBatch(id)
    }

    fun dismissBatchExportDialog() {
        _isBatchExportDialogVisible.value = false
        _activeBatchProgress.value = null
        currentBatchId = null
    }

    private val _activeMaskLayerIndex = MutableStateFlow(0)
    val activeMaskLayerIndex: StateFlow<Int> = _activeMaskLayerIndex.asStateFlow()

    private val _activeSubMaskIndex = MutableStateFlow(0)
    val activeSubMaskIndex: StateFlow<Int> = _activeSubMaskIndex.asStateFlow()

    private val _showMaskOverlay = MutableStateFlow(true)
    val showMaskOverlay: StateFlow<Boolean> = _showMaskOverlay.asStateFlow()

    private val _maskOverlayMode = MutableStateFlow("Ruby") // "Ruby", "Green", "WhiteOnBlack"
    val maskOverlayMode: StateFlow<String> = _maskOverlayMode.asStateFlow()

    private val _maskBrushSize = MutableStateFlow(28f)
    val maskBrushSize: StateFlow<Float> = _maskBrushSize.asStateFlow()

    private val _maskBrushFeather = MutableStateFlow(50f)
    val maskBrushFeather: StateFlow<Float> = _maskBrushFeather.asStateFlow()

    private val _maskBrushFlow = MutableStateFlow(100f)
    val maskBrushFlow: StateFlow<Float> = _maskBrushFlow.asStateFlow()

    private val _isMaskBrushErasing = MutableStateFlow(false)
    val isMaskBrushErasing: StateFlow<Boolean> = _isMaskBrushErasing.asStateFlow()

    // Live evaluated B&W silhouette bitmaps per MaskLayer.id (powers real Ruby Red canvas overlay + B&W dock thumbnails)
    private val _maskPreviewBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val maskPreviewBitmaps: StateFlow<Map<String, Bitmap>> = _maskPreviewBitmaps.asStateFlow()

    val activeMaskBitmap: StateFlow<Bitmap?> = kotlinx.coroutines.flow.combine(_maskPreviewBitmaps, _activeMaskLayerIndex, _recipe) { map, idx, r ->
        val layer = r.masks.getOrNull(idx) ?: return@combine null
        map[layer.id]
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    fun cycleMaskOverlayMode() {
        _maskOverlayMode.value = when (_maskOverlayMode.value) {
            "Ruby" -> "Green"
            "Green" -> "WhiteOnBlack"
            else -> "Ruby"
        }
    }

    fun setMaskBrushSize(v: Float) { _maskBrushSize.value = v.coerceIn(4f, 100f) }
    fun setMaskBrushFeather(v: Float) { _maskBrushFeather.value = v.coerceIn(0f, 100f) }
    fun setMaskBrushFlow(v: Float) { _maskBrushFlow.value = v.coerceIn(5f, 100f) }
    fun setMaskBrushErasing(erasing: Boolean) { _isMaskBrushErasing.value = erasing }

    fun selectMaskLayer(index: Int) {
        _activeMaskLayerIndex.value = index
        _activeSubMaskIndex.value = 0
    }

    fun selectSubMask(index: Int) {
        _activeSubMaskIndex.value = index.coerceAtLeast(0)
    }

    fun addMaskLayer(subMask: SubMask) {
        val currentLayers = _recipe.value.masks
        val baseTitle = when (subMask) {
            is SubMask.AISubject -> "Subject"
            is SubMask.AISky -> "Sky"
            is SubMask.AIBackground -> "Background"
            is SubMask.Brush -> "Brush"
            is SubMask.LinearGradient -> "Linear"
            is SubMask.RadialGradient -> "Radial"
            is SubMask.ColorRange -> "Color"
            is SubMask.LuminanceRange -> "Luminance"
        }
        val initializedSub = when (subMask) {
            is SubMask.LinearGradient -> if (subMask.startX == 0f && subMask.startY == 0f && subMask.endX == 0f && subMask.endY == 0f) {
                subMask.copy(startX = 0.5f, startY = 0.18f, endX = 0.5f, endY = 0.62f)
            } else subMask
            else -> subMask
        }
        val newLayer = MaskLayer(
            id = java.util.UUID.randomUUID().toString(),
            name = "$baseTitle ${currentLayers.size + 1}",
            subMasks = listOf(initializedSub)
        )
        val updated = currentLayers + newLayer
        updateRecipe("Add Mask: $baseTitle") { it.copy(masks = updated) }
        _activeMaskLayerIndex.value = updated.size - 1
        _activeSubMaskIndex.value = 0
        _showMaskOverlay.value = true
        evaluateMaskPreviewAsync(newLayer)
    }

    fun addSubMaskToActive(subMask: SubMask) {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            val layer = currentLayers[idx]
            val initSub = when (subMask) {
                is SubMask.LinearGradient -> if (subMask.startX == 0f && subMask.startY == 0f && subMask.endX == 0f && subMask.endY == 0f) {
                    subMask.copy(startX = 0.5f, startY = 0.18f, endX = 0.5f, endY = 0.62f)
                } else subMask
                else -> subMask
            }
            val updatedLayer = layer.copy(subMasks = layer.subMasks + initSub)
            currentLayers[idx] = updatedLayer
            updateRecipe("Add Component to Mask") { it.copy(masks = currentLayers) }
            _activeSubMaskIndex.value = updatedLayer.subMasks.lastIndex
            evaluateMaskPreviewAsync(updatedLayer)
        }
    }

    fun updateActiveSubMask(transform: (SubMask) -> SubMask) {
        val currentLayers = _recipe.value.masks.toMutableList()
        val lIdx = _activeMaskLayerIndex.value
        if (lIdx !in currentLayers.indices) return
        val layer = currentLayers[lIdx]
        val sIdx = _activeSubMaskIndex.value.coerceIn(0, (layer.subMasks.lastIndex).coerceAtLeast(0))
        if (sIdx !in layer.subMasks.indices) return
        val updatedSubs = layer.subMasks.toMutableList()
        updatedSubs[sIdx] = transform(updatedSubs[sIdx])
        val updatedLayer = layer.copy(subMasks = updatedSubs)
        currentLayers[lIdx] = updatedLayer
        updateRecipe { it.copy(masks = currentLayers) }
        evaluateMaskPreviewAsync(updatedLayer)
    }

    fun appendMaskBrushPoint(normX: Float, normY: Float, isNewStroke: Boolean) {
        val cx = normX.coerceIn(0f, 1f)
        val cy = normY.coerceIn(0f, 1f)
        val rad = _maskBrushSize.value.coerceIn(4f, 100f)
        val feather = _maskBrushFeather.value
        val flow = (_maskBrushFlow.value / 100f).coerceIn(0.05f, 1f)
        val erasing = _isMaskBrushErasing.value

        updateActiveSubMask { sub ->
            if (sub !is SubMask.Brush) return@updateActiveSubMask sub
            if (erasing) {
                val eraseR = (rad / 360f).coerceIn(0.015f, 0.25f)
                sub.copy(points = sub.points.filter { kotlin.math.hypot(it.x - cx, it.y - cy) > eraseR })
            } else {
                val pts = sub.points
                val added = ArrayList<BrushStrokePoint>()
                if (isNewStroke || pts.isEmpty()) {
                    added.add(BrushStrokePoint(cx, cy, rad, feather, flow))
                } else {
                    val prev = pts.last()
                    val dx = cx - prev.x
                    val dy = cy - prev.y
                    val dist = kotlin.math.hypot(dx, dy)
                    val step = ((rad / 400f) * 0.25f).coerceIn(0.004f, 0.02f)
                    val n = kotlin.math.ceil(dist / step).toInt().coerceIn(1, 28)
                    for (i in 1..n) {
                        val t = i.toFloat() / n
                        added.add(BrushStrokePoint((prev.x + dx * t).coerceIn(0f, 1f), (prev.y + dy * t).coerceIn(0f, 1f), rad, feather, flow))
                    }
                }
                sub.copy(points = pts + added)
            }
        }
    }

    fun sampleColorRangeAt(normX: Float, normY: Float) {
        val bmp = _bitmap.value ?: return
        val px = (normX.coerceIn(0f, 1f) * (bmp.width - 1)).toInt()
        val py = (normY.coerceIn(0f, 1f) * (bmp.height - 1)).toInt()
        val color = bmp.getPixel(px, py)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        updateActiveSubMask { sub ->
            if (sub is SubMask.ColorRange) sub.copy(targetHue = hsv[0]) else sub
        }
    }

    fun updateMaskLayer(layer: MaskLayer) {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            currentLayers[idx] = layer
            updateRecipe("Update Mask Adjustments") { it.copy(masks = currentLayers) }
            evaluateMaskPreviewAsync(layer)
        }
    }

    /**
     * Evaluates a real continuous B&W alpha silhouette bitmap for [layer] using bundled
     * BiRefNet-Lite FP16 (Subject/Background), Depth/Sky matte (Sky), or MaskEvaluator (Gradients/Ranges/Brush).
     */
    private fun evaluateMaskPreviewAsync(layer: MaskLayer) {
        val src = _bitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val maxEdge = 256
                val scale = kotlin.math.min(1f, maxEdge.toFloat() / kotlin.math.max(src.width, src.height).coerceAtLeast(1))
                val w = (src.width * scale).toInt().coerceAtLeast(48)
                val h = (src.height * scale).toInt().coerceAtLeast(48)

                val needsBiRefNet = layer.subMasks.any { it is SubMask.AISubject || it is SubMask.AIBackground }
                val cachedAiAlpha: FloatArray? = if (needsBiRefNet) {
                    // Use existing opticalBlurPack alpha if already computed, else run bundled BiRefNet-Lite
                    val existingPack = _opticalBlurPack.value
                    if (existingPack != null) {
                        val scaledA = Bitmap.createScaledBitmap(existingPack.alphaMatte, w, h, true)
                        val px = IntArray(w * h)
                        scaledA.getPixels(px, 0, w, 0, 0, w, h)
                        if (scaledA !== existingPack.alphaMatte) scaledA.recycle()
                        FloatArray(w * h) { i -> android.graphics.Color.red(px[i]) / 255f }
                    } else {
                        val pack = com.cleanlr.ai.AiVisionPipeline.buildOpticalBlurPack(
                            bitmap = src,
                            modelManager = modelManager
                        )
                        withContext(Dispatchers.Main) { _opticalBlurPack.value = pack }
                        val scaledA = Bitmap.createScaledBitmap(pack.alphaMatte, w, h, true)
                        val px = IntArray(w * h)
                        scaledA.getPixels(px, 0, w, 0, 0, w, h)
                        if (scaledA !== pack.alphaMatte) scaledA.recycle()
                        FloatArray(w * h) { i -> android.graphics.Color.red(px[i]) / 255f }
                    }
                } else null

                val acc = FloatArray(w * h)
                var isFirst = true
                for (sub in layer.subMasks) {
                    val subAlpha = when {
                        sub is SubMask.AISubject && cachedAiAlpha != null -> {
                            if (sub.isInverted) FloatArray(w * h) { 1f - cachedAiAlpha[it] } else cachedAiAlpha.copyOf()
                        }
                        sub is SubMask.AIBackground && cachedAiAlpha != null -> {
                            val bg = FloatArray(w * h) { 1f - cachedAiAlpha[it] }
                            if (sub.isInverted) FloatArray(w * h) { 1f - bg[it] } else bg
                        }
                        else -> com.cleanlr.mask.MaskEvaluator.evaluateSubMask(sub, w, h, src)
                    }
                    if (isFirst) {
                        System.arraycopy(subAlpha, 0, acc, 0, acc.size)
                        isFirst = false
                    } else {
                        when (sub.combineMode) {
                            MaskCombineMode.ADD -> for (i in acc.indices) acc[i] = (acc[i] + subAlpha[i]).coerceAtMost(1f)
                            MaskCombineMode.SUBTRACT -> for (i in acc.indices) acc[i] = (acc[i] - subAlpha[i]).coerceAtLeast(0f)
                            MaskCombineMode.INTERSECT -> for (i in acc.indices) acc[i] = (acc[i] * subAlpha[i]).coerceIn(0f, 1f)
                        }
                    }
                }
                val bwPixels = IntArray(w * h) { i ->
                    val v = (acc[i].coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                    android.graphics.Color.argb(v, 255, 255, 255)
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                    setPixels(bwPixels, 0, w, 0, 0, w, h)
                }
                withContext(Dispatchers.Main) {
                    val map = _maskPreviewBitmaps.value.toMutableMap()
                    map[layer.id] = bmp
                    _maskPreviewBitmaps.value = map
                }
            } catch (_: Exception) {
                // Keep geometric overlay fallback if background evaluation is interrupted
            }
        }
    }

    fun deleteActiveMaskLayer() {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            currentLayers.removeAt(idx)
            updateRecipe("Delete Mask") { it.copy(masks = currentLayers) }
            _activeMaskLayerIndex.value = (idx - 1).coerceAtLeast(0)
        }
    }

    fun deleteMaskLayer(idx: Int) {
        val currentLayers = _recipe.value.masks.toMutableList()
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            currentLayers.removeAt(idx)
            updateRecipe("Delete Mask") { it.copy(masks = currentLayers) }
            _activeMaskLayerIndex.value = (idx - 1).coerceAtLeast(0)
        }
    }

    fun invertMaskLayer(idx: Int) {
        val currentLayers = _recipe.value.masks.toMutableList()
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            val layer = currentLayers[idx]
            val invertedSubMasks = layer.subMasks.map { sub ->
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
            currentLayers[idx] = layer.copy(subMasks = invertedSubMasks)
            updateRecipe("Invert Mask") { it.copy(masks = currentLayers) }
            evaluateMaskPreviewAsync(currentLayers[idx])
        }
    }

    fun duplicateMaskLayer(idx: Int) {
        val currentLayers = _recipe.value.masks.toMutableList()
        if (currentLayers.isEmpty()) return
        if (idx in currentLayers.indices) {
            val layer = currentLayers[idx]
            val duplicated = layer.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${layer.name.ifEmpty { "Mask" }} Copy"
            )
            currentLayers.add(idx + 1, duplicated)
            _activeMaskLayerIndex.value = idx + 1
            updateRecipe("Duplicate Mask") { it.copy(masks = currentLayers) }
            evaluateMaskPreviewAsync(duplicated)
        }
    }

    // =========================================================================
    // Tips 1–35 Darkroom Ergonomics & Surface Controls
    // =========================================================================

    // Tip 1: Canvas Background Control
    fun setCanvasBackground(bg: com.cleanlr.ui.theme.CanvasBackground) {
        _canvasBackground.value = bg
    }

    fun cycleCanvasBackground() {
        val values = com.cleanlr.ui.theme.CanvasBackground.values()
        val nextIdx = (_canvasBackground.value.ordinal + 1) % values.size
        _canvasBackground.value = values[nextIdx]
    }

    // Tip 2: Canvas Matting
    fun toggleCanvasMatting() {
        _isCanvasMattingActive.value = !_isCanvasMattingActive.value
    }

    fun setCanvasMatting(active: Boolean) {
        _isCanvasMattingActive.value = active
    }

    // Tip 6: 3-Snap Bottom Sheet
    fun setPanelSnapState(state: com.cleanlr.ui.theme.PanelSnapState) {
        _panelSnapState.value = state
    }

    fun cyclePanelSnapState() {
        _panelSnapState.value = when (_panelSnapState.value) {
            com.cleanlr.ui.theme.PanelSnapState.COLLAPSED -> com.cleanlr.ui.theme.PanelSnapState.COMPACT
            com.cleanlr.ui.theme.PanelSnapState.COMPACT -> com.cleanlr.ui.theme.PanelSnapState.EXPANDED
            com.cleanlr.ui.theme.PanelSnapState.EXPANDED -> com.cleanlr.ui.theme.PanelSnapState.COMPACT
        }
    }

    // Tips 8 & 9: Slider Dragging Dimming
    fun setSliderDragging(dragging: Boolean) {
        _isSliderDragging.value = dragging
    }

    fun setCleanScreenMode(active: Boolean) {
        _isCleanScreenMode.value = active
    }

    fun setBrushSize(size: Float) {
        _brushSize.value = size
    }

    // Tips 23 & 24: Active Modification Indicator Check
    fun isTabModified(tab: EditorToolTab): Boolean {
        val r = _recipe.value
        return when (tab) {
            EditorToolTab.CROP -> {
                r.crop.rotationDegrees != 0f || r.crop.straightenAngle != 0f ||
                r.crop.flipHorizontal || r.crop.flipVertical ||
                r.crop.left != 0f || r.crop.top != 0f || r.crop.right != 1f || r.crop.bottom != 1f ||
                r.crop.distortion != 0f || r.crop.verticalPerspective != 0f || r.crop.horizontalPerspective != 0f ||
                r.crop.geometryRotate != 0f || r.crop.geometryAspect != 0f || r.crop.geometryScale != 100f ||
                r.crop.geometryXOffset != 0f || r.crop.geometryYOffset != 0f || r.crop.isExpandEnabled
            }
            EditorToolTab.LIGHT -> {
                r.basic.exposure != 0f || r.basic.contrast != 0f ||
                r.basic.highlights != 0f || r.basic.shadows != 0f ||
                r.basic.whites != 0f || r.basic.blacks != 0f
            }
            EditorToolTab.COLOR -> {
                r.basic.temperature != 5500f || r.basic.tint != 0f ||
                r.basic.vibrance != 0f || r.basic.saturation != 0f ||
                r.hsl != com.cleanlr.model.HslAdjustments() ||
                r.colorGrading != com.cleanlr.model.ColorGrading()
            }
            EditorToolTab.EFFECTS -> {
                r.effects.texture != 0f || r.effects.clarity != 0f || r.effects.dehaze != 0f ||
                r.effects.vignetteAmount != 0f || r.effects.grainAmount != 0f
            }
            EditorToolTab.BLUR -> {
                r.blur.amount != 0f || r.blur.boost != 0f
            }
            EditorToolTab.DETAIL -> {
                r.detail.sharpening != 40f && r.detail.sharpening != 0f ||
                r.detail.luminanceNoise != 0f || r.detail.colorNoise != 25f
            }
            EditorToolTab.OPTICS -> {
                r.optics.removeChromaticAberration || r.optics.enableLensCorrections || r.optics.distortion != 0f || r.optics.vignetteAmount != 0f
            }
            EditorToolTab.PROFILES -> {
                r.profile != "Adobe Color"
            }
            EditorToolTab.CURVES -> {
                r.curves != com.cleanlr.model.ToneCurves()
            }
            EditorToolTab.PRESETS -> {
                _activePreset.value != null
            }
            EditorToolTab.VERSIONS -> {
                versions.value.size > 1
            }
            EditorToolTab.MASKS -> {
                r.masks.isNotEmpty()
            }
            EditorToolTab.ACTIONS -> false
            EditorToolTab.REMOVE -> false



        }
    }

    // Tip 25: Reset Single Tab on Long-Press
    fun resetTab(tab: EditorToolTab) {
        when (tab) {
            EditorToolTab.CROP -> {
                resetCrop()
            }
            EditorToolTab.LIGHT -> {
                updateRecipe("Reset Light") {
                    it.copy(basic = it.basic.copy(
                        exposure = 0f, contrast = 0f, highlights = 0f,
                        shadows = 0f, whites = 0f, blacks = 0f
                    ))
                }
            }
            EditorToolTab.COLOR -> {
                updateRecipe("Reset Color") {
                    it.copy(
                        basic = it.basic.copy(temperature = 5500f, tint = 0f, vibrance = 0f, saturation = 0f),
                        hsl = com.cleanlr.model.HslAdjustments(),
                        colorGrading = com.cleanlr.model.ColorGrading()
                    )
                }
            }
            EditorToolTab.BLUR -> {
                updateRecipe("Reset Blur") {
                    it.copy(blur = com.cleanlr.model.BlurAdjustments())
                }
            }
            EditorToolTab.EFFECTS -> {
                updateRecipe("Reset Effects") {
                    it.copy(effects = com.cleanlr.model.EffectsAdjustments())
                }
            }
            EditorToolTab.DETAIL -> {
                updateRecipe("Reset Detail") {
                    it.copy(detail = com.cleanlr.model.DetailAdjustments())
                }
            }
            EditorToolTab.OPTICS -> {
                updateRecipe("Reset Optics") {
                    it.copy(optics = com.cleanlr.model.OpticsAdjustments())
                }
            }
            EditorToolTab.PROFILES -> {
                updateRecipe("Reset Profile") {
                    it.copy(profile = "Adobe Color")
                }
            }
            EditorToolTab.CURVES -> {
                updateRecipe("Reset Curves") {
                    it.copy(curves = com.cleanlr.model.ToneCurves())
                }
            }
            EditorToolTab.PRESETS -> {
                _activePreset.value = null
            }
            EditorToolTab.VERSIONS -> {}
            EditorToolTab.MASKS -> {
                updateRecipe("Reset Masks") { it.copy(masks = emptyList()) }
            }
            EditorToolTab.ACTIONS -> {}
            EditorToolTab.REMOVE -> {}


        }
    }

    // Tip 26: Temporary Bypass on Double-Tap
    fun toggleBypassTab(tab: EditorToolTab) {
        val current = _bypassedTabs.value
        _bypassedTabs.value = if (current.contains(tab)) {
            current - tab
        } else {
            current + tab
        }
    }

    // Tips 33 & 34: Color Accordion Section Toggles
    fun toggleColorSection(section: String) {
        when (section) {
            "WB" -> _isColorWbExpanded.value = !_isColorWbExpanded.value
            "VIBRANCE" -> _isColorVibranceExpanded.value = !_isColorVibranceExpanded.value
            "MIXER" -> _isColorMixerExpanded.value = !_isColorMixerExpanded.value
            "GRADING" -> _isColorGradingExpanded.value = !_isColorGradingExpanded.value
        }
    }

    // Tips 201 & 202: 3D Film Emulation LUTs
    fun setFilm3dLut(lut: com.cleanlr.engine.stage.Film3dLut) {
        _activeFilm3dLut.value = lut
        historyManager.commitState(_recipe.value, "Film LUT: ${lut.label}")
    }

    fun setFilm3dLutIntensity(intensity: Float) {
        _film3dLutIntensity.value = intensity.coerceIn(0f, 1f)
    }
}
