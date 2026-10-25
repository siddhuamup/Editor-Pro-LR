package com.cleanlr.ui.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.cleanlr.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cleanlr.export.BatchExportProgress
import com.cleanlr.export.BatchItemProgress
import com.cleanlr.export.BatchItemStatus
import com.cleanlr.ui.theme.*

/**
 * Task T6: Authentic Adobe Lightroom Mobile Batch Export Progress Dialog
 *
 * Displays real-time progress for batch operations (10-50 photos):
 * - Overall progress percentage & status ("Exporting 3 of 10...")
 * - Smooth animated progress bar
 * - Scrollable per-item checklist with COMPLETED / PROCESSING / QUEUED / FAILED states
 * - Graceful cancellation support via mid-batch cancel button
 */
@Composable
fun BatchExportProgressDialog(
    progress: BatchExportProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.overallProgressPercent / 100f,
        label = "overall_batch_progress"
    )

    Dialog(
        onDismissRequest = {
            if (progress.isFinished || progress.isCancelled) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = progress.isFinished || progress.isCancelled,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, SpectrumBorder, RoundedCornerShape(16.dp)),
            color = SpectrumElevated,
            contentColor = SpectrumTextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (progress.isFinished) "Export Finished" else "Exporting Photos",
                            color = SpectrumTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = progress.statusText,
                            color = if (progress.isCancelled) Color(0xFFE24A4A) else SpectrumTextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    if (progress.isFinished || progress.isCancelled) {
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
                }

                // Overall Linear Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Overall Progress",
                            color = SpectrumTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${progress.overallProgressPercent}%",
                            color = SpectrumBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            progress.isCancelled -> Color(0xFFE24A4A)
                            progress.isFinished && progress.failedCount > 0 -> Color(0xFFE68A00)
                            else -> SpectrumBlue
                        },
                        trackColor = SpectrumSurface
                    )
                }

                HorizontalDivider(color = SpectrumBorder, thickness = 0.5.dp)

                // Item Checklist Preview
                Text(
                    text = "QUEUE (${progress.completedCount + progress.failedCount + progress.cancelledCount}/${progress.totalCount})",
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(progress.itemStates) { index, item ->
                        BatchItemRow(index = index + 1, item = item)
                    }
                }

                HorizontalDivider(color = SpectrumBorder, thickness = 0.5.dp)

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!progress.isFinished && !progress.isCancelled) {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE24A4A)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66E24A4A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel Export", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpectrumBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchItemRow(
    index: Int,
    item: BatchItemProgress,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpectrumSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Icon / Number
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when (item.status) {
                        BatchItemStatus.COMPLETED -> Color(0xFF2E7D32)
                        BatchItemStatus.PROCESSING -> SpectrumBlue
                        BatchItemStatus.FAILED -> Color(0xFFC62828)
                        BatchItemStatus.CANCELLED -> Color(0xFF616161)
                        BatchItemStatus.QUEUED -> SpectrumElevated
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (item.status) {
                BatchItemStatus.COMPLETED -> Icon(
                    painter = painterResource(R.drawable.ic_lr_select_checkmark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                BatchItemStatus.PROCESSING -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                BatchItemStatus.FAILED -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                BatchItemStatus.CANCELLED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                BatchItemStatus.QUEUED -> Text(
                    text = "$index",
                    fontSize = 11.sp,
                    color = SpectrumTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Info Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Photo $index",
                color = SpectrumTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (item.status) {
                    BatchItemStatus.QUEUED -> "Queued"
                    BatchItemStatus.PROCESSING -> "Rendering (${item.progressPercent}%)"
                    BatchItemStatus.COMPLETED -> "Saved successfully"
                    BatchItemStatus.FAILED -> item.error ?: "Export failed"
                    BatchItemStatus.CANCELLED -> "Cancelled"
                },
                color = when (item.status) {
                    BatchItemStatus.COMPLETED -> Color(0xFF81C784)
                    BatchItemStatus.PROCESSING -> SpectrumBlue
                    BatchItemStatus.FAILED -> Color(0xFFE57373)
                    else -> SpectrumTextSecondary
                },
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Per-item status tag
        if (item.status == BatchItemStatus.PROCESSING) {
            Text(
                text = "${item.progressPercent}%",
                color = SpectrumBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
