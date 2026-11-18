package com.cleanlr.ui.editor

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.R
import com.cleanlr.preset.Preset
import com.cleanlr.preset.PresetPreviewEngine
import com.cleanlr.ui.components.RampedSlider
import com.cleanlr.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adobe Lightroom Mobile Signature Presets Panel
 * 1:1 Parity with Reference Screenshots 083251, 083253, 083256, 200546, 200549, 200557, 200600:
 * - Sticky Sub-Tabs: "Recommended" | "Premium" | "Yours" with subtle dark active highlight + white bar
 * - "Recommended": Photo-analyzed live color-science recommendations with All | Subtle | Strong | B&W filters
 * - "Premium": All 30 authentic collections with original `.webp` thumbnails mined from Lightroom APK
 * - "Yours": User Presets (+ Save / Import XMP) + all classic Adobe preset collections with exact live counts
 * - Opened Collection: Live photo-preview 2-column grid with white active border, slider overlay toggle,
 *   and full 0%-200% Color Science Amount slider
 */
@Composable
fun PresetsPanel(
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    presets: List<Preset>,
    activePreset: Preset?,
    presetAmount: Float,
    onSelectPreset: (Preset) -> Unit,
    onAmountChange: (Float) -> Unit,
    onAmountChangeEnd: () -> Unit,
    onResetToOriginal: () -> Unit = {},
    onSaveCurrentAsPreset: () -> Unit = {},
    onImportXmpPreset: () -> Unit = {},
    previewBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    var activeSubTab by remember { mutableStateOf("Premium") }
    var openedCollection by remember { mutableStateOf<String?>(null) }
    var recommendedFilter by remember { mutableStateOf("All") }
    var showAmountSlider by remember { mutableStateOf(false) }

    // Reusable 112x112 base thumbnail of the active photo for ultra-fast color-science preset cards
    var baseThumb by remember { mutableStateOf<Bitmap?>(null) }
    val bitmapKey = remember(previewBitmap) { System.identityHashCode(previewBitmap) }
    LaunchedEffect(previewBitmap) {
        baseThumb = if (previewBitmap != null) {
            withContext(Dispatchers.Default) {
                PresetPreviewEngine.createPreviewBase(previewBitmap, 112)
            }
        } else null
    }

    // Photo-aware Recommended presets computed asynchronously on Dispatchers.Default
    var recommendedPresets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var isAnalyzingPhoto by remember { mutableStateOf(true) }
    LaunchedEffect(previewBitmap, presets, recommendedFilter) {
        isAnalyzingPhoto = recommendedPresets.isEmpty()
        recommendedPresets = withContext(Dispatchers.Default) {
            PresetPreviewEngine.recommendPresets(previewBitmap, presets, recommendedFilter)
        }
        isAnalyzingPhoto = false
    }

    // All 30 authentic Premium collections paired with original mined `.webp` thumbnail assets
    val premiumCollections = remember(presets) {
        PresetPreviewEngine.PREMIUM_COLLECTION_THUMBS.map { (title, thumbAsset) ->
            val count = presets.count { it.group.equals(title, ignoreCase = true) }
            Triple(title, count, thumbAsset)
        }.filter { it.second > 0 }
    }

    val premiumGroupSet = remember {
        PresetPreviewEngine.PREMIUM_COLLECTION_THUMBS.map { it.first.lowercase() }.toSet()
    }

    // Yours collections: User Presets first, followed by all authentic non-premium Adobe collections
    val yoursCollections = remember(presets) {
        val preferredOrder = listOf(
            "Color", "Creative", "B&W", "Portraits", "Portrait", "Landscape", "Vintage",
            "Curve", "Grain", "Sharpening", "Vignetting", "Optics", "Defaults",
            "Artistic", "Modern", "Scene Balance", "Samsung",
            "Legacy: Color", "Legacy: Creative", "Legacy: B&W", "Legacy: Detail", "Adaptive"
        )
        val nonPremiumGroups = presets
            .map { it.group }
            .distinct()
            .filter { g ->
                val low = g.lowercase()
                low !in premiumGroupSet && low != "user" && low != "user presets" && low != "profiles"
            }
        val ordered = preferredOrder.filter { p -> nonPremiumGroups.any { it.equals(p, ignoreCase = true) } } +
            nonPremiumGroups.filter { g -> preferredOrder.none { it.equals(g, ignoreCase = true) } }.sorted()

        val userCount = presets.count {
            it.isCustom || it.group.equals("User", ignoreCase = true) || it.group.equals("User Presets", ignoreCase = true)
        }
        listOf("User Presets" to userCount) + ordered.map { g ->
            g to presets.count { it.group.equals(g, ignoreCase = true) }
        }.filter { it.second > 0 }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF19191B))
    ) {
        // ═════════════════════════════════════════════════════════════════
        // 1. HEADER BAR: Sub-Tabs OR Opened Collection Back Header
        // ═════════════════════════════════════════════════════════════════
        if (openedCollection == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E20))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Recommended", "Premium", "Yours").forEach { tabName ->
                    val isSelected = activeSubTab == tabName
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) Color(0xFF28282B) else Color.Transparent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                activeSubTab = tabName
                            }
                            .padding(horizontal = 16.dp)
                            .height(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) Color.White else Color(0xFFB0B0B6),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(if (tabName == "Recommended") 92.dp else if (tabName == "Premium") 58.dp else 40.dp)
                                    .height(2.dp)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (activeSubTab == "Yours") {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSaveCurrentAsPreset()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Preset",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onImportXmpPreset()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import XMP",
                            tint = Color(0xFFB0B0B6),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF2E2E32), thickness = 1.dp)
        } else {
            // Opened Collection Navigation Header (Matching Screenshot 200546)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E20))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        openedCollection = null
                        showAmountSlider = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = openedCollection ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (activePreset != null) {
                    Surface(
                        color = if (showAmountSlider) SpectrumBlue else Color(0xFF2A2A2E),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAmountSlider = !showAmountSlider
                            }
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Preset Amount",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${presetAmount.toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = "Original",
                        color = Color(0xFF9A9AA2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAmountSlider = false
                                onResetToOriginal()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF2E2E32), thickness = 1.dp)
        }

        // ═════════════════════════════════════════════════════════════════
        // 2. COLLAPSIBLE AMOUNT SLIDER (0%–200%) WHEN ACTIVE PRESET SELECTED
        // ═════════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = activePreset != null && showAmountSlider,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (activePreset != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222225))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    RampedSlider(
                        label = "Amount (${activePreset.name})",
                        value = presetAmount,
                        minVal = 0.0f,
                        maxVal = 200.0f,
                        defaultValue = 100.0f,
                        isBipolar = false,
                        formatPattern = "%.0f",
                        unitSuffix = "%",
                        onValueChange = onAmountChange,
                        onValueChangeEnd = onAmountChangeEnd
                    )
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════
        // 3. PANEL BODY (Full remaining height, zero nested scroll conflict!)
        // ═════════════════════════════════════════════════════════════════
        if (openedCollection != null) {
            val collectionPresets = remember(openedCollection, presets) {
                val grp = openedCollection ?: ""
                if (grp.equals("User Presets", ignoreCase = true)) {
                    presets.filter {
                        it.isCustom || it.group.equals("User", ignoreCase = true) || it.group.equals("User Presets", ignoreCase = true)
                    }
                } else {
                    presets.filter { it.group.equals(grp, ignoreCase = true) }
                }
            }
            val subtitle = PresetPreviewEngine.COLLECTION_SUBTITLES[openedCollection]

            if (collectionPresets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No user presets saved yet", color = Color(0xFF9A9AA2), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSaveCurrentAsPreset,
                                colors = ButtonDefaults.buttonColors(containerColor = SpectrumBlue)
                            ) {
                                Text("Save Current Edit", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = onImportXmpPreset) {
                                Text("Import .XMP", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = Color(0xFFB8B8BE),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // 2-Column Live Color-Science Photo Preview Grid (Matching Screenshot 200546)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(collectionPresets, key = { it.id }) { preset ->
                            val isSelected = preset.id == activePreset?.id
                            LivePresetPreviewCard(
                                preset = preset,
                                baseThumb = baseThumb,
                                bitmapKey = bitmapKey,
                                isSelected = isSelected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (isSelected) {
                                        showAmountSlider = !showAmountSlider
                                    } else {
                                        onSelectPreset(preset)
                                        showAmountSlider = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            when (activeSubTab) {
                // ─────────────────────────────────────────────────────────
                // RECOMMENDED TAB (Matching Screenshot 200557)
                // ─────────────────────────────────────────────────────────
                "Recommended" -> {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        // Filter chips: All | Subtle | Strong | B&W
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("All", "Subtle", "Strong", "B&W").forEach { chip ->
                                val selected = recommendedFilter == chip
                                Surface(
                                    color = if (selected) Color(0xFF3A3A3F) else Color(0xFF222225),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) Color(0xFF6E6E74) else Color(0xFF343438)
                                    ),
                                    modifier = Modifier.clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        recommendedFilter = chip
                                    }
                                ) {
                                    Text(
                                        text = chip,
                                        color = if (selected) Color.White else Color(0xFFB8B8BE),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (activePreset != null) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    color = Color(0xFF26262A),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        showAmountSlider = false
                                        onResetToOriginal()
                                    }
                                ) {
                                    Text(
                                        text = "Reset",
                                        color = Color(0xFF38A9FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (isAnalyzingPhoto) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF8E8E93),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("Analyzing your photo...", color = Color(0xFF8E8E93), fontSize = 13.sp)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(recommendedPresets, key = { it.id }) { preset ->
                                    val isSelected = preset.id == activePreset?.id
                                    LivePresetPreviewCard(
                                        preset = preset,
                                        baseThumb = baseThumb,
                                        bitmapKey = bitmapKey,
                                        isSelected = isSelected,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            if (isSelected) {
                                                showAmountSlider = !showAmountSlider
                                            } else {
                                                onSelectPreset(preset)
                                                showAmountSlider = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────
                // PREMIUM TAB: Authentic Mined Adobe `.webp` Thumbnails (Screenshots 083253 & 200549)
                // ─────────────────────────────────────────────────────────
                "Premium" -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(premiumCollections, key = { it.first }) { (title, count, thumbAsset) ->
                            val thumbBitmap = remember(thumbAsset) {
                                PresetPreviewEngine.loadPremiumThumb(context, thumbAsset)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        openedCollection = title
                                        onSelectGroup(title)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Authentic Original Adobe Lightroom Photograph Thumbnail (48dp rounded square)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF28282C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumbBitmap != null) {
                                        Image(
                                            bitmap = thumbBitmap.asImageBitmap(),
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lr_presets),
                                            contentDescription = null,
                                            tint = Color(0xFF88888E),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Text(
                                    text = title,
                                    color = Color(0xFFDCDCE0),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Cloud pack",
                                    tint = Color(0xFF9A9AA0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "$count",
                                    color = Color(0xFF9A9AA0),
                                    fontSize = 13.sp,
                                    modifier = Modifier.widthIn(min = 20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF9A9AA0),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────
                // YOURS TAB (Matching Screenshots 083256 & 200600)
                // ─────────────────────────────────────────────────────────
                "Yours" -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(yoursCollections, key = { _, item -> item.first }) { index, (title, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        openedCollection = title
                                        onSelectGroup(title)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    color = Color(0xFFE5E5EA),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count",
                                    color = Color(0xFF9A9AA0),
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF9A9AA0),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (index == 0) {
                                HorizontalDivider(color = Color(0xFF2C2C30), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live Photo-Preview Preset Card (Matching Reference Screenshots 200546 & 200557).
 * Renders the user's actual photo graded with the preset's color science, bottom scrim label,
 * white 2dp active border, and centered slider toggle icon when selected.
 */
@Composable
private fun LivePresetPreviewCard(
    preset: Preset,
    baseThumb: Bitmap?,
    bitmapKey: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var gradedBitmap by remember(preset.id, bitmapKey) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(preset.id, baseThumb, bitmapKey) {
        if (baseThumb != null) {
            gradedBitmap = withContext(Dispatchers.Default) {
                PresetPreviewEngine.renderPresetPreview(baseThumb, bitmapKey, preset)
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF26262A))
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) Color.White else Color(0xFF343438),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    ) {
        val bmp = gradedBitmap ?: baseThumb
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom readability scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(32.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
        )

        // Centered slider icon on active card (Exact Lightroom Screenshot 200546 signature!)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Adjust amount",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = preset.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
