package com.cleanlr.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cleanlr.R
import com.cleanlr.ui.theme.*

data class CopySettingsSelection(
    val tools: Boolean = false,
    val profile: Boolean = true,
    val light: Boolean = true,
    val color: Boolean = true,
    val effects: Boolean = true,
    val detail: Boolean = true,
    val optics: Boolean = true,
    val geometry: Boolean = false,
    val masks: Boolean = false
) {
    fun allSelected(): Boolean =
        tools && profile && light && color && effects && detail && optics && geometry && masks

    fun setAll(selected: Boolean): CopySettingsSelection =
        CopySettingsSelection(
            tools = selected,
            profile = selected,
            light = selected,
            color = selected,
            effects = selected,
            detail = selected,
            optics = selected,
            geometry = selected,
            masks = selected
        )
}

/**
 * Pixel-accurate Adobe Lightroom Mobile Copy Settings dialog
 * References: Lightroom Mobile v11.8.15 copy settings checklist UI
 */
@Composable
fun CopySettingsDialog(
    initialSelection: CopySettingsSelection = CopySettingsSelection(),
    onDismissRequest: () -> Unit,
    onConfirmCopy: (CopySettingsSelection) -> Unit
) {
    val view = LocalView.current
    var selection by remember { mutableStateOf(initialSelection) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414)),
            color = Color(0xFF141414)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Action Bar: [✕] Title ... [✓]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_close),
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Copy Settings",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfirmCopy(selection)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lr_select_checkmark),
                            contentDescription = "Confirm",
                            tint = SpectrumBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF282828), thickness = 0.8.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    // Select All Row
                    val allChecked = selection.allSelected()
                    CopyCheckItem(
                        label = "Select All",
                        checked = allChecked,
                        isBold = true,
                        onCheckedChange = { check ->
                            selection = selection.setAll(check)
                        }
                    )

                    HorizontalDivider(
                        color = Color(0xFF242426),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                    )

                    // Individual Categorized Items
                    CopyCheckItem(
                        label = "Tools",
                        sublabel = "Crop",
                        checked = selection.tools,
                        onCheckedChange = { selection = selection.copy(tools = it) }
                    )

                    CopyCheckItem(
                        label = "Profile",
                        sublabel = "Camera & Creative Profiles",
                        checked = selection.profile,
                        onCheckedChange = { selection = selection.copy(profile = it) }
                    )

                    CopyCheckItem(
                        label = "Light",
                        sublabel = "Exposure, Contrast, Highlights, Shadows, Whites, Blacks, Curves",
                        checked = selection.light,
                        onCheckedChange = { selection = selection.copy(light = it) }
                    )

                    CopyCheckItem(
                        label = "Color",
                        sublabel = "White Balance, Vibrance, Saturation, Color Mix, Color Grading",
                        checked = selection.color,
                        onCheckedChange = { selection = selection.copy(color = it) }
                    )

                    CopyCheckItem(
                        label = "Effects",
                        sublabel = "Texture, Clarity, Dehaze, Vignette, Grain",
                        checked = selection.effects,
                        onCheckedChange = { selection = selection.copy(effects = it) }
                    )

                    CopyCheckItem(
                        label = "Detail",
                        sublabel = "Sharpening, Noise Reduction, Color Noise Reduction",
                        checked = selection.detail,
                        onCheckedChange = { selection = selection.copy(detail = it) }
                    )

                    CopyCheckItem(
                        label = "Optics",
                        sublabel = "Remove Chromatic Aberration, Lens Profile Corrections",
                        checked = selection.optics,
                        onCheckedChange = { selection = selection.copy(optics = it) }
                    )

                    CopyCheckItem(
                        label = "Geometry",
                        sublabel = "Distortion, Upright, Perspective adjustments",
                        checked = selection.geometry,
                        onCheckedChange = { selection = selection.copy(geometry = it) }
                    )

                    CopyCheckItem(
                        label = "Masking",
                        sublabel = "AI & Manual Mask adjustments",
                        checked = selection.masks,
                        onCheckedChange = { selection = selection.copy(masks = it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyCheckItem(
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    isBold: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (checked) Color.White else Color(0xFFC0C0C4),
                fontSize = 15.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    color = Color(0xFF7E7E84),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Checkbox(
            checked = checked,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCheckedChange(it)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = SpectrumBlue,
                uncheckedColor = Color(0xFF6E6E72),
                checkmarkColor = Color.White
            )
        )
    }
}
