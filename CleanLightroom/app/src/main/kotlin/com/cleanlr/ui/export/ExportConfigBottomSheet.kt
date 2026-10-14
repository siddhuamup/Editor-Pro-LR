package com.cleanlr.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.export.ExportConfig
import com.cleanlr.export.ExportFormat
import com.cleanlr.ui.theme.*

/**
 * Task T8: Export Config Bottom Sheet Dialog
 *
 * Provides granular control over output image format (JPEG, PNG, WebP),
 * compression quality slider (1-100), long-edge dimension constraints
 * (Full Resolution vs Web 2048px), and camera Exif / recipe preservation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportConfigBottomSheet(
    onDismiss: () -> Unit,
    onExportConfirmed: (ExportConfig) -> Unit,
    initialConfig: ExportConfig = ExportConfig()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var format by remember { mutableStateOf(initialConfig.format) }
    var quality by remember { mutableStateOf(initialConfig.quality.toFloat()) }
    var isLongEdge2048 by remember { mutableStateOf(initialConfig.maxDimension == 2048) }
    var includeExif by remember { mutableStateOf(initialConfig.includeExif) }
    var autoShare by remember { mutableStateOf(initialConfig.autoShare) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpectrumElevated,
        contentColor = SpectrumTextPrimary,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                color = SpectrumBorder,
                shape = RoundedCornerShape(2.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = SpectrumBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Export Settings",
                        color = SpectrumTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SpectrumTextSecondary
                    )
                }
            }

            HorizontalDivider(color = SpectrumBorder, thickness = 1.dp)

            // 1. Format Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "FILE FORMAT",
                    color = SpectrumTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpectrumSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExportFormat.values().forEach { fmt ->
                        val isSelected = format == fmt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) SpectrumBlue else Color.Transparent)
                                .clickable { format = fmt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (fmt) {
                                    ExportFormat.JPEG -> "JPEG"
                                    ExportFormat.PNG -> "PNG"
                                    ExportFormat.WEBP -> "WebP"
                                },
                                color = if (isSelected) Color.White else SpectrumTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = when (format) {
                        ExportFormat.JPEG -> "Universal standard, optimized for photos and sharing."
                        ExportFormat.PNG -> "Lossless compression without visual degradation (larger file size)."
                        ExportFormat.WEBP -> "Modern efficient web compression format."
                    },
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp
                )
            }

            // 2. Dimensions Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DIMENSIONS",
                    color = SpectrumTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpectrumSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Full Resolution
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isLongEdge2048) SpectrumBlue else Color.Transparent)
                            .clickable { isLongEdge2048 = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Original (Full)",
                            color = if (!isLongEdge2048) Color.White else SpectrumTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (!isLongEdge2048) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    // Web 2048px
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLongEdge2048) SpectrumBlue else Color.Transparent)
                            .clickable { isLongEdge2048 = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Web (2048px)",
                            color = if (isLongEdge2048) Color.White else SpectrumTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isLongEdge2048) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = if (isLongEdge2048) {
                        "Long edge constrained to 2048px (ideal for Instagram and web portfolio)."
                    } else {
                        "Full resolution master render via 2048px tile processor."
                    },
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp
                )
            }

            // 3. Quality Slider (Applicable to JPEG / WebP)
            if (format != ExportFormat.PNG) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUALITY",
                            color = SpectrumTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${quality.toInt()}%",
                            color = SpectrumBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = SpectrumBlue,
                            activeTrackColor = SpectrumBlue,
                            inactiveTrackColor = SpectrumBorder
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpectrumSurface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compression Quality",
                        color = SpectrumTextPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Lossless (100%)",
                        color = SpectrumTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. Metadata & Exif Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpectrumSurface)
                    .clickable(enabled = format == ExportFormat.JPEG) {
                        includeExif = !includeExif
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Preserve Camera Exif & Edits",
                        color = if (format == ExportFormat.JPEG) SpectrumTextPrimary else SpectrumTextDisabled,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (format == ExportFormat.JPEG) {
                            "Embeds camera tags, ISO, lens, and non-destructive recipe"
                        } else {
                            "Exif preservation is supported on JPEG export"
                        },
                        color = SpectrumTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = includeExif && format == ExportFormat.JPEG,
                    onCheckedChange = { includeExif = it },
                    enabled = format == ExportFormat.JPEG,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SpectrumBlue,
                        uncheckedThumbColor = SpectrumTextSecondary,
                        uncheckedTrackColor = SpectrumBorder
                    )
                )
            }

            // 5. Native Share Sheet Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpectrumSurface)
                    .clickable { autoShare = !autoShare }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Photo After Export",
                        color = SpectrumTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically opens the system Share Sheet upon completion",
                        color = SpectrumTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = autoShare,
                    onCheckedChange = { autoShare = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SpectrumBlue,
                        uncheckedThumbColor = SpectrumTextSecondary,
                        uncheckedTrackColor = SpectrumBorder
                    )
                )
            }

            // 6. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SpectrumTextSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpectrumBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        val finalConfig = ExportConfig(
                            format = format,
                            quality = quality.toInt().coerceIn(1, 100),
                            maxDimension = if (isLongEdge2048) 2048 else null,
                            includeExif = includeExif && format == ExportFormat.JPEG,
                            autoShare = autoShare
                        )
                        onExportConfirmed(finalConfig)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpectrumBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
