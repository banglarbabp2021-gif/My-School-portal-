package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.StudentEntity
import com.example.util.BarcodeUtil
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

@Composable
fun CameraScannerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onBarcodeScanned: (studentId: String, type: String) -> Unit,
    availableStudents: List<StudentEntity> = emptyList(),
    operatorRole: String = "TEACHER" // "TEACHER" or "ADMIN"
) {
    if (!isOpen) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedType by remember { mutableStateOf("ENTRY") } // "ENTRY" or "EXIT"
    var manualIdInput by remember { mutableStateOf("") }
    var scannedResult by remember { mutableStateOf<String?>(null) }
    var isManualMode by remember { mutableStateOf(false) }
    var lastScanTimestamp by remember { mutableLongStateOf(0L) }
    var lastScannedId by remember { mutableStateOf<String?>(null) }

    // Student Name Verification State
    var pendingVerificationStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var pendingVerificationStudentId by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Scan Student Barcode",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Logged by $operatorRole for Attendance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_scanner")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Entry / Exit Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = selectedType == "ENTRY",
                        onClick = { selectedType = "ENTRY" },
                        label = { Text("School Entry (IN)") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF16A34A),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("chip_entry")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = selectedType == "EXIT",
                        onClick = { selectedType = "EXIT" },
                        label = { Text("School Exit (OUT)") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDC2626),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("chip_exit")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode toggle: Camera View or Quick Student Select / Manual Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!isManualMode) "Camera Viewfinder" else "Quick Student Select",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { isManualMode = !isManualMode },
                        modifier = Modifier.testTag("btn_toggle_scan_mode")
                    ) {
                        Text(if (!isManualMode) "Manual / List Pick" else "Use Camera View")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isManualMode) {
                    if (hasCameraPermission) {
                        // CameraX Preview Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        try {
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                            val multiFormatReader = MultiFormatReader().apply {
                                                val hints = mapOf(
                                                    DecodeHintType.POSSIBLE_FORMATS to listOf(
                                                        BarcodeFormat.QR_CODE,
                                                        BarcodeFormat.CODE_128,
                                                        BarcodeFormat.CODE_39,
                                                        BarcodeFormat.EAN_13
                                                    )
                                                )
                                                setHints(hints)
                                            }

                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                                try {
                                                    val yBuffer = imageProxy.planes[0].buffer
                                                    val data = ByteArray(yBuffer.remaining())
                                                    yBuffer.get(data)
                                                    val source = PlanarYUVLuminanceSource(
                                                        data,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        0,
                                                        0,
                                                        imageProxy.width,
                                                        imageProxy.height,
                                                        false
                                                    )
                                                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                                    val result = multiFormatReader.decodeWithState(binaryBitmap)
                                                    val rawText = result?.text
                                                    if (!rawText.isNullOrBlank()) {
                                                        val studentId = BarcodeUtil.parseStudentIdFromScan(rawText)
                                                        val now = System.currentTimeMillis()
                                                        if (pendingVerificationStudentId == null && (now - lastScanTimestamp > 2000L || studentId != lastScannedId)) {
                                                            lastScanTimestamp = now
                                                            lastScannedId = studentId
                                                            val matched = availableStudents.find { it.id.equals(studentId, ignoreCase = true) }
                                                            pendingVerificationStudent = matched
                                                            pendingVerificationStudentId = studentId
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                    // Frame does not contain valid barcode or not aligned
                                                } finally {
                                                    imageProxy.close()
                                                }
                                            }

                                            cameraProvider.unbindAll()
                                            try {
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    preview,
                                                    imageAnalysis
                                                )
                                            } catch (_: Exception) {
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    preview
                                                )
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay Scanner Reticle
                            Box(
                                modifier = Modifier
                                    .size(240.dp, 160.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            )

                            // Realtime scan detection banner if logged
                            if (scannedResult != null) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                        .testTag("banner_scan_success"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF16A34A)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Logged $selectedType: $scannedResult",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // Quick trigger button below viewfinder to simulate scan of first student or scanned ID
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Position Barcode / QR Code inside frame",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (availableStudents.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            val sample = availableStudents.first()
                                            onBarcodeScanned(sample.id, selectedType)
                                            scannedResult = "${sample.name} (${sample.id})"
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("btn_simulate_camera_scan")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Capture / Scan Test Card")
                                    }
                                }
                            }
                        }
                    } else {
                        // Permission prompt fallback
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Camera Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Please allow camera access to scan physical student barcode cards.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    modifier = Modifier.testTag("btn_request_camera_perm")
                                ) {
                                    Text("Grant Camera Permission")
                                }
                            }
                        }
                    }
                } else {
                    // Manual ID input and Student quick select list
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = manualIdInput,
                            onValueChange = { manualIdInput = it },
                            label = { Text("Enter Barcode / Student ID") },
                            placeholder = { Text("e.g. STU-10-A-01") },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (manualIdInput.isNotBlank()) {
                                            val id = BarcodeUtil.parseStudentIdFromScan(manualIdInput)
                                            onBarcodeScanned(id, selectedType)
                                            onDismiss()
                                        }
                                    },
                                    enabled = manualIdInput.isNotBlank(),
                                    modifier = Modifier.testTag("btn_submit_manual_barcode")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Submit")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_manual_barcode"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap student below to log ${if (selectedType == "ENTRY") "Entry" else "Exit"}:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableStudents) { student ->
                                Card(
                                    onClick = {
                                        onBarcodeScanned(student.id, selectedType)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_scan_item_${student.id}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = student.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "ID: ${student.id}  •  Class ${student.className}-${student.section} (Roll: ${student.rollNumber})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                pendingVerificationStudent = student
                                                pendingVerificationStudentId = student.id
                                            },
                                            modifier = Modifier.testTag("btn_scan_student_${student.id}")
                                        ) {
                                            Text("Verify ($selectedType)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_cancel_scanner")
                ) {
                    Text("Close Scanner")
                }
            }
        }

        // Student Name Verification Dialog (OK or Cancel)
        if (pendingVerificationStudentId != null) {
            val verifiedName = pendingVerificationStudent?.name ?: "Student ($pendingVerificationStudentId)"
            AlertDialog(
                onDismissRequest = {
                    pendingVerificationStudent = null
                    pendingVerificationStudentId = null
                },
                icon = {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text("Verify Student / নাম ভেরিফিকেশন", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = verifiedName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        pendingVerificationStudent?.let { st ->
                            Text("Class: ${st.className}-${st.section}  |  Roll: #${st.rollNumber}", style = MaterialTheme.typography.bodyMedium)
                            Text("School: ${st.schoolName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Student ID: $pendingVerificationStudentId", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedType == "ENTRY") Color(0xFF16A34A) else Color(0xFF2563EB)
                        ) {
                            Text(
                                text = "Action: Log $selectedType Attendance",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val stId = pendingVerificationStudentId!!
                            onBarcodeScanned(stId, selectedType)
                            scannedResult = "Attendance recorded for $verifiedName! Ready for next barcode."
                            pendingVerificationStudent = null
                            pendingVerificationStudentId = null
                        },
                        modifier = Modifier.testTag("btn_confirm_dialog_scan")
                    ) {
                        Text("OK (Confirm & Ready for Next)")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            pendingVerificationStudent = null
                            pendingVerificationStudentId = null
                        },
                        modifier = Modifier.testTag("btn_cancel_dialog_scan")
                    ) {
                        Text("Cancel / বাতিল")
                    }
                }
            )
        }
    }
}
