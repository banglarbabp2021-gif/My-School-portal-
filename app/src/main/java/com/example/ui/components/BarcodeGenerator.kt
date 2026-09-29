package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.StudentEntity
import com.example.util.BarcodeUtil

@Composable
fun BarcodeGeneratorCard(
    student: StudentEntity,
    modifier: Modifier = Modifier,
    onDownloadClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var qrBitmap by remember(student) {
        mutableStateOf<Bitmap?>(null)
    }
    var barcode1DBitmap by remember(student) {
        mutableStateOf<Bitmap?>(null)
    }
    var showQrView by remember { mutableStateOf(true) }

    LaunchedEffect(student) {
        qrBitmap = BarcodeUtil.generateStudentQrCode(
            schoolId = student.schoolId,
            className = student.className,
            section = student.section,
            rollNumber = student.rollNumber,
            studentId = student.id,
            width = 400,
            height = 400
        )
        barcode1DBitmap = BarcodeUtil.generateStudent1DBarcode(
            studentId = student.id,
            width = 500,
            height = 160
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("barcode_generator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ID Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = student.schoolName.ifBlank { "Academic Academy" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "DIGITAL STUDENT IDENTITY CARD",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "VALID 2026-27",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Student photo and details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (student.photoUri.isNotBlank()) {
                        AsyncImage(
                            model = student.photoUri,
                            contentDescription = "Student Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Avatar",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Class: ${student.className}-${student.section}  |  Roll: ${student.rollNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Student ID: ${student.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Blood Group: ${student.bloodGroup}  |  Gender: ${student.gender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Switch between QR code and 1D Barcode
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = showQrView,
                    onClick = { showQrView = true },
                    label = { Text("QR Code") },
                    leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_qr_code")
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !showQrView,
                    onClick = { showQrView = false },
                    label = { Text("1D Barcode") },
                    leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_1d_barcode")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barcode Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showQrView) {
                    if (qrBitmap != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "Scannable Student QR Code",
                                modifier = Modifier
                                    .size(180.dp)
                                    .testTag("qr_code_image")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scan for Student Entry / Exit",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.DarkGray
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    if (barcode1DBitmap != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = barcode1DBitmap!!.asImageBitmap(),
                                contentDescription = "Scannable Student 1D Barcode",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .testTag("barcode_1d_image")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "* ${student.id} *",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                letterSpacing = 2.sp
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions: Download / Share ID Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val activeBitmap = if (showQrView) qrBitmap else barcode1DBitmap
                        if (activeBitmap != null) {
                            BarcodeUtil.shareBitmap(
                                context,
                                activeBitmap,
                                "Student ID Card - ${student.name} (${student.id})"
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_share_barcode")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }

                Button(
                    onClick = {
                        val activeBitmap = if (showQrView) qrBitmap else barcode1DBitmap
                        if (activeBitmap != null) {
                            BarcodeUtil.shareBitmap(
                                context,
                                activeBitmap,
                                "Downloaded Barcode ID Card for ${student.name}"
                            )
                        }
                        onDownloadClick?.invoke()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_download_barcode")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            }
        }
    }
}
