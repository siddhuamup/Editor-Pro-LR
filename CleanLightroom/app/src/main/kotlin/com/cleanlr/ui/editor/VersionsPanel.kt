package com.cleanlr.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.ui.theme.*
import com.cleanlr.version.NamedVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adobe Lightroom Mobile Signature Named Versions Panel (WFCatVersions.lua compliant)
 */
@Composable
fun VersionsPanel(
    versions: List<NamedVersion>,
    onCreateVersion: (String) -> Unit,
    onApplyVersion: (String) -> Unit,
    onDeleteVersion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newVersionName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Header Row: Count & Create Version Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SpectrumTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VERSIONS (${versions.size})",
                    color = SpectrumTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Create Version Button
            Surface(
                color = SpectrumBlue,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable {
                    newVersionName = "Version ${versions.size}"
                    showCreateDialog = true
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Version",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Create",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Carousel of Versions
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(versions) { version ->
                Surface(
                    color = SpectrumElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SpectrumBorder),
                    modifier = Modifier
                        .width(150.dp)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onApplyVersion(version.id)
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = if (version.isBase) SpectrumSurface else SpectrumBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (version.isBase) "ORIGINAL" else "SAVED",
                                    color = if (version.isBase) SpectrumTextSecondary else SpectrumBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (!version.isBase) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Version",
                                    tint = SpectrumTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onDeleteVersion(version.id)
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = version.name,
                            color = SpectrumTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = dateFormat.format(Date(version.createdAt)),
                            color = SpectrumTextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }

    // Create Version Modal Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "Name This Version",
                    color = SpectrumTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newVersionName,
                    onValueChange = { newVersionName = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Warm Sunset, Moody B&W", color = SpectrumTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpectrumBlue,
                        unfocusedBorderColor = SpectrumBorder,
                        focusedTextColor = SpectrumTextPrimary,
                        unfocusedTextColor = SpectrumTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newVersionName.isNotBlank()) {
                            onCreateVersion(newVersionName.trim())
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpectrumBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SpectrumTextSecondary)
                }
            },
            containerColor = SpectrumSurface
        )
    }
}
