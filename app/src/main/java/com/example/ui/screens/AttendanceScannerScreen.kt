package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.AttendanceEntity
import com.example.data.model.StudentEntity
import com.example.ui.viewmodel.AttendanceScanResult
import com.example.ui.viewmodel.SchoolViewModel
import com.example.util.BarcodeUtil
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScannedItemRecord(
    val student: StudentEntity?,
    val record: AttendanceEntity,
    val isFirestoreSynced: Boolean,
    val scannedCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScannerScreen(
    viewModel: SchoolViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val session by viewModel.session.collectAsState()
    val allStudents by viewModel.allStudents.collectAsState()

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

    // Camera settings
    var isFrontCamera by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    // Attendance configuration: ENTRY or EXIT
    var attendanceType by remember { mutableStateOf("ENTRY") } // "ENTRY" or "EXIT"

    // Scanner state
    var isScanningActive by remember { mutableStateOf(true) }
    var manualBarcodeQuery by remember { mutableStateOf("") }
    var showManualInputModal by remember { mutableStateOf(false) }

    // Student Name Verification state (OK / Cancel)
    var pendingVerificationStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var pendingVerificationScannedCode by remember { mutableStateOf("") }
    var crossSchoolErrorAlert by remember { mutableStateOf<String?>(null) }

    // Realtime feedback banner
    var latestScanResult by remember { mutableStateOf<AttendanceScanResult?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastScannedCode by remember { mutableStateOf("") }
    var lastScanTimestamp by remember { mutableLongStateOf(0L) }

    // Session scan history list
    val sessionScans = remember { mutableStateListOf<ScannedItemRecord>() }
    var showRecentScansSheet by remember { mutableStateOf(false) }

    // Confirm attendance after user verifies the student name
    fun confirmAttendanceForVerifiedStudent() {
        val student = pendingVerificationStudent ?: return
        coroutineScope.launch {
            val result = viewModel.logBarcodeAttendance(student.id, attendanceType)
            if (result.success && result.record != null) {
                latestScanResult = result
                scanErrorMessage = null
                sessionScans.add(
                    0,
                    ScannedItemRecord(
                        student = result.student,
                        record = result.record,
                        isFirestoreSynced = result.isFirestoreSynced,
                        scannedCode = pendingVerificationScannedCode.ifBlank { student.id }
                    )
                )
            } else {
                scanErrorMessage = result.message
                latestScanResult = null
            }
            pendingVerificationStudent = null
            pendingVerificationScannedCode = ""

            // Ready for next student barcode scan
            delay(500L)
            isScanningActive = true

            // Auto-hide latest scan banner after 3.5 seconds
            delay(3500L)
            if (latestScanResult == result) {
                latestScanResult = null
            }
        }
    }

    // Cancel attendance verification and resume scanning immediately
    fun cancelVerification() {
        pendingVerificationStudent = null
        pendingVerificationScannedCode = ""
        isScanningActive = true
    }

    // Process a scanned or entered barcode: verifies student first
    fun processBarcode(rawCode: String) {
        val trimmed = rawCode.trim()
        if (trimmed.isBlank()) return
        if (pendingVerificationStudent != null) return // Already verifying

        val now = System.currentTimeMillis()
        // Prevent duplicate scanning of the same barcode within 2 seconds
        if (trimmed == lastScannedCode && (now - lastScanTimestamp) < 2000L) {
            return
        }

        coroutineScope.launch {
            val student = viewModel.lookupStudentForScan(trimmed)
            if (student == null) {
                scanErrorMessage = "Student not found for barcode: $trimmed"
                latestScanResult = null
                return@launch
            }

            // Cross-School check for multi-tenant isolation
            val currentSession = viewModel.session.value
            if (currentSession.role == "TEACHER" && currentSession.schoolId.isNotBlank()) {
                if (!student.schoolId.equals(currentSession.schoolId, ignoreCase = true)) {
                    crossSchoolErrorAlert = "Cross-School Restriction:\nStudent '${student.name}' belongs to '${student.schoolName}' (${student.schoolId}).\n\nYou are authorized for '${currentSession.schoolName}' only."
                    return@launch
                }
            }

            // Name Verification Step: pause scanning and show confirmation dialog
            lastScannedCode = trimmed
            lastScanTimestamp = now
            pendingVerificationStudent = student
            pendingVerificationScannedCode = trimmed
            isScanningActive = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Barcode Attendance Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CameraX + ZXing -> Google Cloud Firestore",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_scanner_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Flashlight / Torch toggle button
                    IconButton(
                        onClick = {
                            activeCamera?.cameraControl?.let { control ->
                                val target = !isTorchEnabled
                                control.enableTorch(target)
                                isTorchEnabled = target
                            }
                        },
                        modifier = Modifier.testTag("btn_scanner_torch")
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch",
                            tint = if (isTorchEnabled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Flip camera button
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier.testTag("btn_scanner_flip_camera")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera"
                        )
                    }

                    // Manual input dialog button
                    IconButton(
                        onClick = { showManualInputModal = true },
                        modifier = Modifier.testTag("btn_scanner_manual_entry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Manual Code Entry"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Bottom control strip: Attendance Type selector & Scans Counter
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Entry vs Exit mode selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // School Entry (IN)
                        FilledTonalButton(
                            onClick = { attendanceType = "ENTRY" },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_mode_entry"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (attendanceType == "ENTRY") Color(0xFF16A34A) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (attendanceType == "ENTRY") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Entry (IN)", fontWeight = FontWeight.Bold)
                        }

                        // School Exit (OUT)
                        FilledTonalButton(
                            onClick = { attendanceType = "EXIT" },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_mode_exit"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (attendanceType == "EXIT") Color(0xFF2563EB) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (attendanceType == "EXIT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exit (OUT)", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Recent scans counter and quick drawer toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRecentScansSheet = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Session Logs: ${sessionScans.size} Scanned",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = { showRecentScansSheet = true },
                            modifier = Modifier.testTag("btn_view_recent_scans")
                        ) {
                            Text("View List", fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // CameraX Preview and ZXing ImageAnalysis View
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val cameraSelector = if (isFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                // ZXing multi-format reader with 1D barcodes and QR Code support
                                val multiFormatReader = MultiFormatReader().apply {
                                    val hints = mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(
                                            BarcodeFormat.CODE_128,
                                            BarcodeFormat.CODE_39,
                                            BarcodeFormat.EAN_13,
                                            BarcodeFormat.EAN_8,
                                            BarcodeFormat.UPC_A,
                                            BarcodeFormat.UPC_E,
                                            BarcodeFormat.QR_CODE,
                                            BarcodeFormat.ITF
                                        ),
                                        DecodeHintType.TRY_HARDER to true
                                    )
                                    setHints(hints)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    try {
                                        if (!isScanningActive) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }

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
                                        val decodeResult = multiFormatReader.decodeWithState(binaryBitmap)
                                        val rawText = decodeResult?.text
                                        if (!rawText.isNullOrBlank()) {
                                            processBarcode(rawText)
                                        }
                                    } catch (_: Exception) {
                                        // Barcode not in viewfinder frame or unaligned
                                    } finally {
                                        imageProxy.close()
                                    }
                                }

                                cameraProvider.unbindAll()
                                activeCamera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Reticle Viewfinder with animated laser line
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
                    val laserOffsetY by infiniteTransition.animateFloat(
                        initialValue = -80f,
                        targetValue = 80f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laser_offset"
                    )

                    // Reticle box
                    Box(
                        modifier = Modifier
                            .size(width = 280.dp, height = 200.dp)
                            .border(
                                width = 2.dp,
                                color = if (attendanceType == "ENTRY") Color(0xFF22C55E) else Color(0xFF3B82F6),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("reticle_viewfinder")
                    ) {
                        // Scanner sweep laser line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.Center)
                                .offset(y = laserOffsetY.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            if (attendanceType == "ENTRY") Color(0xFF4ADE80) else Color(0xFF60A5FA),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // Instruction badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 130.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "Point camera at student ID barcode or QR code",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                // Camera Permission Denied / Rationale View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Access Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To scan student ID barcodes with CameraX, please grant camera permission or use manual barcode entry below.",
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("btn_grant_camera_permission")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showManualInputModal = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("btn_switch_to_manual_entry")
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Barcode Manually")
                    }
                }
            }

            // Realtime Success Banner with Student Info and Firestore Badge
            AnimatedVisibility(
                visible = latestScanResult != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                latestScanResult?.let { res ->
                    val student = res.student
                    val record = res.record
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("banner_scan_success"),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Student photo or fallback avatar
                            if (student != null && student.photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = student.photoUri,
                                    contentDescription = student.name,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (res.isFirestoreSynced) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    color = if (res.isFirestoreSynced) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = student?.name?.take(1) ?: "S",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.isFirestoreSynced) Color(0xFF166534) else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = student?.name ?: "Student",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Attendance Type Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (record?.type == "ENTRY") Color(0xFF16A34A) else Color(0xFF2563EB)
                                    ) {
                                        Text(
                                            text = record?.type ?: attendanceType,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Class ${student?.className ?: ""}-${student?.section ?: ""} | Roll #${student?.rollNumber ?: ""} | ID: ${student?.id ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Google Firebase Firestore sync badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (res.isFirestoreSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (res.isFirestoreSynced) Color(0xFF16A34A) else Color(0xFFEAB308)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (res.isFirestoreSynced) "Saved & Synced to Cloud Firestore" else "Saved locally (Cloud sync queued)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (res.isFirestoreSynced) Color(0xFF16A34A) else Color(0xFFB45309)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = record?.timeString ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error notice banner
            AnimatedVisibility(
                visible = scanErrorMessage != null && latestScanResult == null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = scanErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { scanErrorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Session Scanned Student Logs
    if (showRecentScansSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecentScansSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Attendance Logs (${sessionScans.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showRecentScansSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (sessionScans.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No student barcodes scanned yet in this session.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sessionScans) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (item.record.type == "ENTRY") Color(0xFF16A34A) else Color(0xFF2563EB)
                                    ) {
                                        Text(
                                            text = item.record.type,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.student?.name ?: item.record.studentName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Class ${item.record.className}-${item.record.section} (Roll: ${item.record.rollNumber})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = item.record.timeString,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (item.isFirestoreSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = if (item.isFirestoreSynced) Color(0xFF16A34A) else Color(0xFFEAB308)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (item.isFirestoreSynced) "Firestore" else "Local",
                                                fontSize = 10.sp,
                                                color = if (item.isFirestoreSynced) Color(0xFF16A34A) else Color(0xFFB45309)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Barcode / Student ID Entry Dialog (for testing or barcode card damaged)
    if (showManualInputModal) {
        AlertDialog(
            onDismissRequest = { showManualInputModal = false },
            title = {
                Text(
                    text = "Manual Barcode / ID Entry",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a student ID or barcode data string to log attendance directly into Google Firebase Cloud Firestore:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = manualBarcodeQuery,
                        onValueChange = { manualBarcodeQuery = it },
                        placeholder = { Text("e.g. STU-10-A-01") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.QrCode, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manual_scanner_code")
                    )
                    if (allStudents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Registered Student Barcodes (${allStudents.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allStudents.take(10)) { student ->
                                SuggestionChip(
                                    onClick = { manualBarcodeQuery = student.id },
                                    label = { Text("${student.name} (${student.id})", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualBarcodeQuery.isNotBlank()) {
                            processBarcode(manualBarcodeQuery)
                            manualBarcodeQuery = ""
                            showManualInputModal = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_manual_scan")
                ) {
                    Text("Log Attendance")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cross-School Isolation Alert Dialog
    if (crossSchoolErrorAlert != null) {
        AlertDialog(
            onDismissRequest = {
                crossSchoolErrorAlert = null
                isScanningActive = true
            },
            icon = {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "School Access Restriction",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = crossSchoolErrorAlert ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        crossSchoolErrorAlert = null
                        isScanningActive = true
                    }
                ) {
                    Text("Understood")
                }
            }
        )
    }

    // Student Name Verification Dialog (নাম ভেরিফিকেশন: OK বা Cancel)
    pendingVerificationStudent?.let { student ->
        AlertDialog(
            onDismissRequest = { cancelVerification() },
            modifier = Modifier.testTag("dialog_attendance_name_verification"),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Name Verification",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Verify Student / নাম ভেরিফিকেশন",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (attendanceType == "ENTRY") Color(0xFF16A34A) else Color(0xFF2563EB)
                    ) {
                        Text(
                            text = if (attendanceType == "ENTRY") "ACTION: ENTRY LOG (প্রবেশ)" else "ACTION: EXIT LOG (প্রস্থান)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Student Photo or Avatar
                        if (student.photoUri.isNotBlank()) {
                            AsyncImage(
                                model = student.photoUri,
                                contentDescription = student.name,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = student.name.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Verified Student Name
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Class: ${student.className} • Section: ${student.section} • Roll: #${student.rollNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Student ID: ${student.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Text(
                            text = "School: ${student.schoolName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmAttendanceForVerifiedStudent() },
                    modifier = Modifier.testTag("btn_confirm_verified_attendance"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (attendanceType == "ENTRY") Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OK (Confirm & Next)")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { cancelVerification() },
                    modifier = Modifier.testTag("btn_cancel_verified_attendance")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel / বাতিল")
                }
            }
        )
    }
}
