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
    EFFECTS("Effects"),
    DETAIL("Detail"),
    CURVES("Curves"),
    PRESETS("Presets"),
    VERSIONS("Versions"),
    MASKS("Masks")
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

    val presetGroups = listOf("All") + PresetManager.getGroups() + listOf("User Presets")
    private val _selectedPresetGroup = MutableStateFlow("All")
    val selectedPresetGroup: StateFlow<String> = _selectedPresetGroup.asStateFlow()

    private val _customPresets = MutableStateFlow<List<Preset>>(emptyList())
    val customPresets: StateFlow<List<Preset>> = _customPresets.asStateFlow()

    private val _allPresets = MutableStateFlow<List<Preset>>(PresetManager.STARTER_PACK)
    val allPresets: StateFlow<List<Preset>> = _allPresets.asStateFlow()

    private val _activePreset = MutableStateFlow<Preset?>(null)
    val activePreset: StateFlow<Preset?> = _activePreset.asStateFlow()

    private val _presetAmount = MutableStateFlow(100.0f)
    val presetAmount: StateFlow<Float> = _presetAmount.asStateFlow()

    private var recipeBeforePreset: EditRecipe? = null

    private val _recipe = MutableStateFlow(EditRecipe())
    val recipe: StateFlow<EditRecipe> = _recipe.asStateFlow()

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

    private val _canvasBackground = MutableStateFlow(com.cleanlr.ui.theme.CanvasBackground.DARK_GRAY)
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
        _allPresets.value = PresetManager.STARTER_PACK + updated
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
                    _allPresets.value = PresetManager.STARTER_PACK + updated
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

    override fun toggleCropActive() {
        _isCropActive.value = !_isCropActive.value
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
        updateRecipe("Aspect Ratio $aspectRatio") {
            it.copy(crop = it.crop.copy(aspectRatio = aspectRatio))
        }
    }

    fun resetCrop() {
        updateRecipe("Reset Crop") {
            it.copy(crop = CropAdjustments())
        }
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

    fun selectMaskLayer(index: Int) {
        _activeMaskLayerIndex.value = index
    }

    fun addMaskLayer(subMask: SubMask) {
        val currentLayers = _recipe.value.masks
        val newLayer = MaskLayer(
            id = java.util.UUID.randomUUID().toString(),
            name = "Mask ${currentLayers.size + 1}",
            subMasks = listOf(subMask)
        )
        val updated = currentLayers + newLayer
        updateRecipe("Add Mask") { it.copy(masks = updated) }
        _activeMaskLayerIndex.value = updated.size - 1
    }

    fun addSubMaskToActive(subMask: SubMask) {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (idx in currentLayers.indices) {
            val layer = currentLayers[idx]
            currentLayers[idx] = layer.copy(subMasks = layer.subMasks + subMask)
            updateRecipe("Add Component to Mask") { it.copy(masks = currentLayers) }
        }
    }

    fun updateMaskLayer(layer: MaskLayer) {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (idx in currentLayers.indices) {
            currentLayers[idx] = layer
            updateRecipe("Update Mask Adjustments") { it.copy(masks = currentLayers) }
        }
    }

    fun deleteActiveMaskLayer() {
        val currentLayers = _recipe.value.masks.toMutableList()
        val idx = _activeMaskLayerIndex.value
        if (idx in currentLayers.indices) {
            currentLayers.removeAt(idx)
            updateRecipe("Delete Mask") { it.copy(masks = currentLayers) }
            _activeMaskLayerIndex.value = (idx - 1).coerceAtLeast(0)
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

    // Tips 23 & 24: Active Modification Indicator Check
    fun isTabModified(tab: EditorToolTab): Boolean {
        val r = _recipe.value
        return when (tab) {
            EditorToolTab.CROP -> {
                r.crop.rotationDegrees != 0f || r.crop.straightenAngle != 0f ||
                r.crop.flipHorizontal || r.crop.flipVertical ||
                r.crop.left != 0f || r.crop.top != 0f || r.crop.right != 1f || r.crop.bottom != 1f
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
            EditorToolTab.DETAIL -> {
                r.detail.sharpening != 40f && r.detail.sharpening != 0f ||
                r.detail.luminanceNoise != 0f || r.detail.colorNoise != 25f
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
