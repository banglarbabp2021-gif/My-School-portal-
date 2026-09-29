package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.session.UserSessionState
import com.example.data.sync.SyncState
import com.example.ui.components.BarcodeGeneratorCard
import com.example.ui.components.CameraScannerDialog
import com.example.ui.components.AttendanceTrendLineChart
import com.example.ui.components.CloudSyncDialog
import com.example.ui.components.NotificationBellButton
import com.example.ui.components.TeacherNoticesReadView
import com.example.ui.components.ConsecutiveAbsenceBadge
import com.example.ui.components.ConsecutiveAbsenceBanner
import com.example.ui.components.ConsecutiveAbsenceDialog
import com.example.ui.components.ConsecutiveAbsenceInfo
import com.example.ui.components.ConsecutiveAbsenceSectionCard
import com.example.ui.components.calculateConsecutiveAbsences
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeacherDashboardScreen(
    currentSession: UserSessionState,
    students: List<StudentEntity>,
    attendanceRecords: List<AttendanceEntity>,
    notifications: List<NotificationEntity>,
    syncState: SyncState = SyncState(),
    onSyncRoomToFirestore: () -> Unit = {},
    onSyncFirestoreToRoom: () -> Unit = {},
    onSeedFirebaseData: () -> Unit = {},
    onClearDemoAccounts: () -> Unit = {},
    onRecordAttendance: (studentId: String, type: String) -> Unit,
    onSaveStudent: (StudentEntity) -> Unit,
    onUpdateStudentCredentials: (studentId: String, newPhone: String, newPass: String) -> Unit,
    onUpdateStudentPhoto: (studentId: String, photoUri: String) -> Unit = { _, _ -> },
    onEditStudentProfile: (oldId: String, updated: StudentEntity) -> Unit = { _, _ -> },
    onRemoveStudent: (StudentEntity) -> Unit = {},
    onUpdateTeacherProfile: (TeacherEntity) -> Unit = {},
    onDeleteTeacherProfile: () -> Unit = {},
    teacherProfile: TeacherEntity? = null,
    onSaveExamResult: (ExamResultEntity) -> Unit,
    onSendNotification: (
        targetType: String,
        targetStudentId: String?,
        targetClassName: String?,
        targetSection: String?,
        title: String,
        message: String,
        imageUrl: String?,
        isPaymentAlert: Boolean,
        amountDue: Double?,
        senderTeacherName: String?,
        senderTeacherId: String?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onChangeTeacherPassword: (newPass: String) -> Unit,
    onOpenShopping: () -> Unit,
    onOpenScannerScreen: () -> Unit = {},
    onOpenNotificationHistory: () -> Unit = {},
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Students, 1: Attendance, 2: Exam Marks, 3: Notice Board
    var showScannerDialog by remember { mutableStateOf(false) }
    var showMarksScannerDialog by remember { mutableStateOf(false) }
    var marksScannedStudentId by remember { mutableStateOf<String?>(null) }
    var showNoticeScannerDialog by remember { mutableStateOf(false) }
    var noticeScannedStudentId by remember { mutableStateOf<String?>(null) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var selectedStudentForDetails by remember { mutableStateOf<StudentEntity?>(null) }
    var studentToFullEdit by remember { mutableStateOf<StudentEntity?>(null) }
    var studentToRemove by remember { mutableStateOf<StudentEntity?>(null) }
    var studentForPhotoUpload by remember { mutableStateOf<StudentEntity?>(null) }
    var showTeacherProfileDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAbsenceAlertDialog by remember { mutableStateOf(false) }
    var isAbsenceBannerDismissed by rememberSaveable { mutableStateOf(false) }

    // 3-Day Consecutive Absence Detection for Teacher's School
    val consecutiveAbsentAlerts = remember(students, attendanceRecords, currentSession.schoolId) {
        val schoolStudents = if (currentSession.schoolId.isNotBlank()) {
            students.filter { it.schoolId.equals(currentSession.schoolId, ignoreCase = true) }
        } else {
            students
        }
        val schoolRecords = if (currentSession.schoolId.isNotBlank()) {
            attendanceRecords.filter { it.schoolId.equals(currentSession.schoolId, ignoreCase = true) }
        } else {
            attendanceRecords
        }
        calculateConsecutiveAbsences(schoolStudents, schoolRecords)
    }

    // Trigger a small notification / alert banner when 3-day consecutive absentees exist
    val snackbarHostState = remember { SnackbarHostState() }
    var hasNotifiedAbsenceThisSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(consecutiveAbsentAlerts.size) {
        if (consecutiveAbsentAlerts.isNotEmpty() && !hasNotifiedAbsenceThisSession) {
            hasNotifiedAbsenceThisSession = true
            val first = consecutiveAbsentAlerts.first()
            val alertMsg = if (consecutiveAbsentAlerts.size == 1) {
                "⚠️ Attendance Alert: ${first.student.name} (Roll #${first.student.rollNumber}) has been absent for 3 consecutive days."
            } else {
                "⚠️ Attendance Alert: ${consecutiveAbsentAlerts.size} students have been absent for 3 consecutive days."
            }
            val res = snackbarHostState.showSnackbar(
                message = alertMsg,
                actionLabel = "View",
                duration = SnackbarDuration.Long
            )
            if (res == SnackbarResult.ActionPerformed) {
                showAbsenceAlertDialog = true
            }
        }
    }

    // Student Search Query
    var studentSearchQuery by remember { mutableStateOf("") }
    val filteredStudents = remember(students, studentSearchQuery) {
        if (studentSearchQuery.isBlank()) students
        else {
            students.filter {
                it.name.contains(studentSearchQuery, ignoreCase = true) ||
                it.className.contains(studentSearchQuery, ignoreCase = true) ||
                it.rollNumber.contains(studentSearchQuery, ignoreCase = true) ||
                it.id.contains(studentSearchQuery, ignoreCase = true)
            }
        }
    }

    val isKeyboardOpen = WindowInsets.isImeVisible

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentSession.userName.ifBlank { "Teacher Dashboard" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(currentSession.schoolName.ifBlank { "School Portal" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    // 3-Day Consecutive Absence Alert Indicator with Badge
                    if (consecutiveAbsentAlerts.isNotEmpty()) {
                        IconButton(
                            onClick = { showAbsenceAlertDialog = true },
                            modifier = Modifier.testTag("btn_teacher_consecutive_absence_alert")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("${consecutiveAbsentAlerts.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Consecutive Absence Alerts",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Notification Bell with Badge & History
                    NotificationBellButton(
                        notifications = notifications,
                        modifier = Modifier.testTag("teacher_notification_bell"),
                        onOpenHistory = onOpenNotificationHistory
                    )

                    // Cloud Sync action
                    IconButton(
                        onClick = { showCloudSyncDialog = true },
                        modifier = Modifier.testTag("btn_teacher_cloud_sync")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud Storage Sync",
                            tint = if (syncState.isCloudConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Attendance Barcode Scanner Quick Launch
                    IconButton(
                        onClick = { onOpenScannerScreen() },
                        modifier = Modifier.testTag("btn_teacher_open_scanner")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Attendance", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Online Shopping
                    IconButton(
                        onClick = onOpenShopping,
                        modifier = Modifier.testTag("btn_teacher_shopping")
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Online Store")
                    }

                    // Tutender Teacher Profile Management
                    IconButton(
                        onClick = { showTeacherProfileDialog = true },
                        modifier = Modifier.testTag("btn_teacher_profile")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "My Profile", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Change Password
                    IconButton(
                        onClick = { showPasswordChangeDialog = true },
                        modifier = Modifier.testTag("btn_teacher_change_password")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "Change Password")
                    }

                    // Logout
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.testTag("btn_teacher_logout")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isKeyboardOpen) {
                if (selectedTab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddStudentDialog = true },
                        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                        text = { Text("New Student") },
                        modifier = Modifier.testTag("fab_add_student")
                    )
                } else if (selectedTab == 1) {
                    ExtendedFloatingActionButton(
                        onClick = { onOpenScannerScreen() },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        text = { Text("Scan Barcode") },
                        modifier = Modifier.testTag("fab_scan_attendance")
                    )
                } else if (selectedTab == 2) {
                    ExtendedFloatingActionButton(
                        onClick = { showMarksScannerDialog = true },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        text = { Text("Scan Student") },
                        modifier = Modifier.testTag("fab_scan_marks_student")
                    )
                } else if (selectedTab == 3) {
                    ExtendedFloatingActionButton(
                        onClick = { showNoticeScannerDialog = true },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        text = { Text("Scan for Notice") },
                        modifier = Modifier.testTag("fab_scan_notice_student")
                    )
                }
            }
        },
        bottomBar = {
            if (!isKeyboardOpen) {
                NavigationBar(
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            if (consecutiveAbsentAlerts.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text("${consecutiveAbsentAlerts.size}")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null)
                                }
                            } else {
                                Icon(Icons.Default.People, contentDescription = null)
                            }
                        },
                        label = { Text("Students") },
                        modifier = Modifier.testTag("tab_nav_students")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            if (consecutiveAbsentAlerts.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text("${consecutiveAbsentAlerts.size}")
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.HowToReg, contentDescription = null)
                                }
                            } else {
                                Icon(Icons.Default.HowToReg, contentDescription = null)
                            }
                        },
                        label = { Text("Attendance") },
                        modifier = Modifier.testTag("tab_nav_attendance")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null) },
                        label = { Text("Marks") },
                        modifier = Modifier.testTag("tab_nav_marks")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                        label = { Text("Notices") },
                        modifier = Modifier.testTag("tab_nav_notices")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when (selectedTab) {
                0 -> {
                    // Students Directory & Profiles
                    TeacherStudentsView(
                        students = filteredStudents,
                        searchQuery = studentSearchQuery,
                        onSearchChange = { studentSearchQuery = it },
                        onSelectStudent = { selectedStudentForDetails = it },
                        onEditStudent = { studentToFullEdit = it },
                        onDeleteStudent = { studentToRemove = it },
                        onUploadPhoto = { studentForPhotoUpload = it },
                        onScanClick = { onOpenScannerScreen() },
                        consecutiveAbsentAlerts = consecutiveAbsentAlerts,
                        isAbsenceAlertDismissed = isAbsenceBannerDismissed,
                        onDismissAbsenceAlert = { isAbsenceBannerDismissed = true },
                        onViewAllAbsenceAlerts = { showAbsenceAlertDialog = true },
                        onNotifyAbsentStudent = { student ->
                            noticeScannedStudentId = student.id
                            selectedTab = 3
                        }
                    )
                }

                1 -> {
                    // Attendance Records & Scanning Hub
                    TeacherAttendanceView(
                        records = attendanceRecords,
                        students = students,
                        onLaunchScanner = { onOpenScannerScreen() },
                        onManualRecord = onRecordAttendance,
                        schoolId = currentSession.schoolId,
                        schoolName = currentSession.schoolName,
                        consecutiveAbsentAlerts = consecutiveAbsentAlerts,
                        isAbsenceAlertDismissed = isAbsenceBannerDismissed,
                        onDismissAbsenceAlert = { isAbsenceBannerDismissed = true },
                        onNotifyAbsentStudent = { student ->
                            noticeScannedStudentId = student.id
                            selectedTab = 3
                        },
                        onSelectStudent = { selectedStudentForDetails = it }
                    )
                }

                2 -> {
                    // Exam Marks Entry
                    TeacherExamMarksView(
                        students = students,
                        scannedStudentIdToSelect = marksScannedStudentId,
                        onClearScannedStudentId = { marksScannedStudentId = null },
                        onLaunchBarcodeScanner = { showMarksScannerDialog = true },
                        onSaveMarks = onSaveExamResult
                    )
                }

                3 -> {
                    // School Notices & Student Notification Dispatcher (from Teacher ID)
                    TeacherNotificationsView(
                        students = students,
                        sentNotifications = notifications,
                        currentTeacherId = currentSession.userId,
                        currentTeacherName = currentSession.userName,
                        schoolId = currentSession.schoolId,
                        schoolName = currentSession.schoolName,
                        scannedStudentIdToSelect = noticeScannedStudentId,
                        onClearScannedStudentId = { noticeScannedStudentId = null },
                        onLaunchBarcodeScanner = { showNoticeScannerDialog = true },
                        onSendNotice = onSendNotification
                    )
                }
            }
        }
    }

    // Camera Barcode Scanner Dialog for Attendance
    CameraScannerDialog(
        isOpen = showScannerDialog,
        onDismiss = { showScannerDialog = false },
        onBarcodeScanned = { studentId, type ->
            onRecordAttendance(studentId, type)
        },
        availableStudents = students,
        operatorRole = "TEACHER"
    )

    // Camera Barcode Scanner Dialog for Marks Student Selection
    CameraScannerDialog(
        isOpen = showMarksScannerDialog,
        onDismiss = { showMarksScannerDialog = false },
        onBarcodeScanned = { scannedId, _ ->
            marksScannedStudentId = scannedId
            showMarksScannerDialog = false
        },
        availableStudents = students,
        operatorRole = "TEACHER"
    )

    // Camera Barcode Scanner Dialog for Notice Student Selection
    CameraScannerDialog(
        isOpen = showNoticeScannerDialog,
        onDismiss = { showNoticeScannerDialog = false },
        onBarcodeScanned = { scannedId, _ ->
            noticeScannedStudentId = scannedId
            showNoticeScannerDialog = false
        },
        availableStudents = students,
        operatorRole = "TEACHER"
    )

    // Add Student Dialog
    if (showAddStudentDialog) {
        val effectiveSchoolId = currentSession.schoolId.ifBlank {
            students.firstOrNull()?.schoolId ?: teacherProfile?.schoolId ?: ""
        }
        val effectiveSchoolName = currentSession.schoolName.ifBlank {
            students.firstOrNull()?.schoolName ?: teacherProfile?.schoolName ?: "My School"
        }
        AddStudentDialog(
            schoolId = effectiveSchoolId,
            schoolName = effectiveSchoolName,
            onDismiss = { showAddStudentDialog = false },
            onSave = { newStudent ->
                onSaveStudent(newStudent)
                showAddStudentDialog = false
            }
        )
    }

    // Student ID Card & Full Profile Dialog
    if (selectedStudentForDetails != null) {
        Dialog(onDismissRequest = { selectedStudentForDetails = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    BarcodeGeneratorCard(student = selectedStudentForDetails!!)

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            studentForPhotoUpload = selectedStudentForDetails
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_dialog_upload_student_photo"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedStudentForDetails!!.photoUri.isNotBlank()) "Change Student Photo" else "Upload Student Photo")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    StudentProfileDetailsCard(student = selectedStudentForDetails!!)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val s = selectedStudentForDetails
                                selectedStudentForDetails = null
                                studentToFullEdit = s
                            },
                            modifier = Modifier.weight(1f).testTag("btn_dialog_edit_student_id")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit ID/Info")
                        }

                        OutlinedButton(
                            onClick = {
                                val s = selectedStudentForDetails
                                selectedStudentForDetails = null
                                studentToRemove = s
                            },
                            modifier = Modifier.weight(1f).testTag("btn_dialog_remove_student_id"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove ID")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { selectedStudentForDetails = null },
                        modifier = Modifier.fillMaxWidth().testTag("btn_close_student_card_dialog")
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Upload Student Photo Dialog (Teacher Authorized)
    if (studentForPhotoUpload != null) {
        UploadStudentPhotoDialog(
            student = studentForPhotoUpload!!,
            teacherId = currentSession.userId,
            teacherName = currentSession.userName,
            onDismiss = { studentForPhotoUpload = null },
            onSavePhoto = { newPhotoUri ->
                onUpdateStudentPhoto(studentForPhotoUpload!!.id, newPhotoUri)
                if (selectedStudentForDetails?.id == studentForPhotoUpload?.id) {
                    selectedStudentForDetails = selectedStudentForDetails?.copy(photoUri = newPhotoUri)
                }
                studentForPhotoUpload = null
            }
        )
    }

    // Edit Full Student ID & Profile (Authorized by Teacher ID)
    if (studentToFullEdit != null) {
        EditStudentIdAndProfileDialog(
            student = studentToFullEdit!!,
            teacherId = currentSession.userId.ifBlank { teacherProfile?.id ?: "TEACHER" },
            teacherName = currentSession.userName.ifBlank { teacherProfile?.name ?: "Authorized Faculty" },
            onDismiss = { studentToFullEdit = null },
            onSave = { oldId, updatedStudent ->
                onEditStudentProfile(oldId, updatedStudent)
                studentToFullEdit = null
            }
        )
    }

    // Confirm Remove Student ID (Authorized by Teacher ID)
    if (studentToRemove != null) {
        ConfirmRemoveStudentDialog(
            student = studentToRemove!!,
            teacherId = currentSession.userId.ifBlank { teacherProfile?.id ?: "TEACHER" },
            onDismiss = { studentToRemove = null },
            onConfirm = {
                onRemoveStudent(studentToRemove!!)
                studentToRemove = null
            }
        )
    }

    // Teacher Tutender Profile Management Dialog
    if (showTeacherProfileDialog) {
        TeacherProfileDialog(
            teacher = teacherProfile,
            currentSession = currentSession,
            onDismiss = { showTeacherProfileDialog = false },
            onSaveProfile = { updated ->
                onUpdateTeacherProfile(updated)
                showTeacherProfileDialog = false
            },
            onDeleteProfile = {
                showTeacherProfileDialog = false
                onDeleteTeacherProfile()
            }
        )
    }

    // Change Teacher Password Dialog
    if (showPasswordChangeDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordChangeDialog = false },
            onConfirm = { newPass ->
                onChangeTeacherPassword(newPass)
                showPasswordChangeDialog = false
            }
        )
    }

    // Logout Confirmation
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Teacher Logout") },
            text = { Text("Are you sure you want to log out of your teacher account?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("btn_confirm_teacher_logout")
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cloud Persistent Storage Synchronization Dialog
    CloudSyncDialog(
        isOpen = showCloudSyncDialog,
        syncState = syncState,
        onDismiss = { showCloudSyncDialog = false },
        onSyncRoomToFirestore = onSyncRoomToFirestore,
        onSyncFirestoreToRoom = onSyncFirestoreToRoom,
        onSeedFirebaseData = onSeedFirebaseData
    )

    // 3-Day Consecutive Absence Detailed Dialog
    if (showAbsenceAlertDialog) {
        ConsecutiveAbsenceDialog(
            alerts = consecutiveAbsentAlerts,
            onDismiss = { showAbsenceAlertDialog = false },
            onNotifyStudent = { student ->
                showAbsenceAlertDialog = false
                noticeScannedStudentId = student.id
                selectedTab = 3
            },
            onSelectStudent = { student ->
                showAbsenceAlertDialog = false
                selectedStudentForDetails = student
            }
        )
    }
}

@Composable
fun TeacherStudentsView(
    students: List<StudentEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectStudent: (StudentEntity) -> Unit,
    onEditStudent: (StudentEntity) -> Unit = {},
    onDeleteStudent: (StudentEntity) -> Unit = {},
    onUploadPhoto: (StudentEntity) -> Unit,
    onScanClick: () -> Unit,
    consecutiveAbsentAlerts: List<ConsecutiveAbsenceInfo> = emptyList(),
    isAbsenceAlertDismissed: Boolean = false,
    onDismissAbsenceAlert: () -> Unit = {},
    onViewAllAbsenceAlerts: () -> Unit = {},
    onNotifyAbsentStudent: (StudentEntity) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 3-Day Consecutive Absence Warning Banner (with close '×' option)
        if (!isAbsenceAlertDismissed && consecutiveAbsentAlerts.isNotEmpty()) {
            item(key = "consecutive_absence_banner") {
                ConsecutiveAbsenceBanner(
                    alerts = consecutiveAbsentAlerts,
                    onViewAll = onViewAllAbsenceAlerts,
                    onNotifyStudent = onNotifyAbsentStudent,
                    onDismiss = onDismissAbsenceAlert,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Barcode Scanning Banner
        item(key = "quick_barcode_scan_banner") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScanClick)
                    .testTag("banner_quick_barcode_scan"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan Student Barcode / QR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Record school entry or exit instantly with camera",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // Search Bar & Count Header
        item(key = "search_and_count_header") {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search by name, class, roll, or ID...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_student_directory"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Registered Students (${students.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap for ID / Barcode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (students.isEmpty()) {
            item(key = "empty_student_list") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No students match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = students,
                key = { it.id }
            ) { student ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_card_${student.id}")
                            .clickable { onSelectStudent(student) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Student Profile Photo Avatar with click to upload
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable { onUploadPhoto(student) }
                                    .testTag("avatar_student_${student.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (student.photoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = student.photoUri,
                                        contentDescription = "Photo of ${student.name}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = student.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val absenceAlert = consecutiveAbsentAlerts.find { it.student.id == student.id }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (absenceAlert != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ConsecutiveAbsenceBadge(consecutiveDays = absenceAlert.consecutiveDaysCount)
                                    }
                                }
                                Text(
                                    text = "Class ${student.className}-${student.section}  |  Roll: ${student.rollNumber}  |  ID: ${student.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Mobile: ${student.studentMobile.ifBlank { "None" }}  |  Blood: ${student.bloodGroup}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onUploadPhoto(student) },
                                    modifier = Modifier.testTag("btn_upload_photo_${student.id}")
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onEditStudent(student) },
                                    modifier = Modifier.testTag("btn_edit_student_${student.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Student Profile & ID",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteStudent(student) },
                                    modifier = Modifier.testTag("btn_remove_student_${student.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Student ID",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onSelectStudent(student) },
                                    modifier = Modifier.testTag("btn_view_barcode_${student.id}")
                                ) {
                                    Icon(
                                        Icons.Default.QrCode,
                                        contentDescription = "View Barcode",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun TeacherAttendanceView(
    records: List<AttendanceEntity>,
    students: List<StudentEntity>,
    onLaunchScanner: () -> Unit,
    onManualRecord: (studentId: String, type: String) -> Unit,
    schoolId: String = "",
    schoolName: String = "",
    consecutiveAbsentAlerts: List<ConsecutiveAbsenceInfo> = emptyList(),
    isAbsenceAlertDismissed: Boolean = false,
    onDismissAbsenceAlert: () -> Unit = {},
    onNotifyAbsentStudent: (StudentEntity) -> Unit = {},
    onSelectStudent: (StudentEntity) -> Unit = {}
) {
    val schoolFilteredRecords = remember(records, schoolId, students) {
        if (schoolId.isNotBlank()) {
            val studentIds = students.map { it.id }.toSet()
            records.filter { it.schoolId.equals(schoolId, ignoreCase = true) || studentIds.contains(it.studentId) }
        } else {
            records
        }
    }

    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id ?: "") }
    var selectedType by remember { mutableStateOf("ENTRY") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 3-Day Consecutive Absence Risk Section (with close '×' option)
        if (!isAbsenceAlertDismissed && consecutiveAbsentAlerts.isNotEmpty()) {
            item {
                ConsecutiveAbsenceSectionCard(
                    alerts = consecutiveAbsentAlerts,
                    onNotifyStudent = onNotifyAbsentStudent,
                    onSelectStudent = onSelectStudent,
                    onDismiss = onDismissAbsenceAlert,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Attendance Trend Line Chart (School-Scoped Daily Consistency & Student Reports)
        item {
            AttendanceTrendLineChart(
                attendanceRecords = schoolFilteredRecords,
                students = students,
                schoolId = schoolId,
                schoolName = schoolName,
                onManualRecord = onManualRecord,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_attendance_trends")
            )
        }

        // Quick Scan CTA
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_attendance_scanner_cta"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Barcode Attendance Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan student cards using camera to auto-record Entry or Exit timestamp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLaunchScanner,
                        modifier = Modifier.fillMaxWidth().testTag("btn_launch_camera_scanner")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Camera Barcode Scanner")
                    }
                }
            }
        }

        // Manual Attendance Marking Option
        item {
            var manualInputId by remember { mutableStateOf("") }
            var dropdownExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_manual_attendance"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Manual Attendance / উপস্থিতি প্রদান",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Manual Entry",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (students.isNotEmpty()) {
                        Text(
                            text = "Select Student from School List:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                val currentStudent = students.find { it.id == selectedStudentId }
                                Text(
                                    text = if (manualInputId.isNotBlank()) "Manual ID: $manualInputId"
                                           else if (currentStudent != null) "${currentStudent.name} (${currentStudent.id} • Roll: ${currentStudent.rollNumber})"
                                           else "Choose Student (${students.size} registered)",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                students.forEach { stu ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stu.name, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "ID: ${stu.id} • Class ${stu.className}-${stu.section} (Roll: ${stu.rollNumber})",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedStudentId = stu.id
                                            manualInputId = ""
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = "এখনো কোনো শিক্ষার্থী যুক্ত করা হয়নি। নিচে সরাসরি আইডি/বারকোড লিখে উপস্থিতি দিতে পারেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = manualInputId,
                        onValueChange = {
                            manualInputId = it
                            if (it.isNotBlank()) selectedStudentId = it.trim()
                        },
                        label = { Text("Or Type Barcode / Student ID manually") },
                        placeholder = { Text("e.g. STU-001 or Barcode") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedType == "ENTRY",
                            onClick = { selectedType = "ENTRY" },
                            label = { Text("Entry (IN) / প্রবেশ") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == "EXIT",
                            onClick = { selectedType = "EXIT" },
                            label = { Text("Exit (OUT) / প্রস্থান") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val effectiveTargetId = if (manualInputId.isNotBlank()) manualInputId.trim() else selectedStudentId

                    Button(
                        onClick = {
                            if (effectiveTargetId.isNotBlank()) {
                                onManualRecord(effectiveTargetId, selectedType)
                                manualInputId = ""
                            }
                        },
                        enabled = effectiveTargetId.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_quick_log_selected_student")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record $selectedType Attendance")
                    }
                }
            }
        }

        // Recent Logs List
        item {
            Text(
                text = "Recent Attendance Logs (${schoolFilteredRecords.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (schoolFilteredRecords.isEmpty()) {
            item {
                Text(
                    text = "No attendance logs recorded today yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(schoolFilteredRecords) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                            Text(record.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Class ${record.className}-${record.section} • ID: ${record.studentId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (record.type == "ENTRY") Color(0xFF16A34A) else Color(0xFFDC2626)
                            ) {
                                Text(
                                    text = record.type,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "${record.dateString} ${record.timeString}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherExamMarksView(
    students: List<StudentEntity>,
    scannedStudentIdToSelect: String? = null,
    onClearScannedStudentId: () -> Unit = {},
    onLaunchBarcodeScanner: () -> Unit = {},
    onSaveMarks: (ExamResultEntity) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var examType by remember { mutableStateOf("Monthly Exam - Sept 2026") }
    var marksInput by remember { mutableStateOf("85") }
    var maxMarksInput by remember { mutableStateOf("100") }
    var remarksInput by remember { mutableStateOf("Good performance") }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(students) {
        if (selectedStudent == null && students.isNotEmpty()) {
            selectedStudent = students.firstOrNull()
        }
    }

    // Student Search & Barcode Selection State
    var searchQuery by remember { mutableStateOf("") }

    // Seven Subjects with In-place Editing
    val subjectList = remember {
        mutableStateListOf(
            "Mathematics",
            "Science",
            "English",
            "Social Studies",
            "Computer Science",
            "Physical Education",
            "Art & Design"
        )
    }
    var selectedSubjectIndex by remember { mutableStateOf(0) }
    var subjectToEditIndex by remember { mutableStateOf<Int?>(null) }
    var subjectEditTempText by remember { mutableStateOf("") }

    val currentSubject = subjectList.getOrElse(selectedSubjectIndex) { subjectList.firstOrNull() ?: "Mathematics" }

    // Handle incoming scanned barcode from camera scanner
    LaunchedEffect(scannedStudentIdToSelect) {
        if (!scannedStudentIdToSelect.isNullOrBlank()) {
            val parsedId = com.example.util.BarcodeUtil.parseStudentIdFromScan(scannedStudentIdToSelect)
            val matched = students.find {
                it.id.equals(parsedId, ignoreCase = true) ||
                it.id.equals(scannedStudentIdToSelect, ignoreCase = true) ||
                scannedStudentIdToSelect.contains(it.id, ignoreCase = true)
            }
            if (matched != null) {
                selectedStudent = matched
                searchQuery = matched.name
            } else {
                searchQuery = scannedStudentIdToSelect
            }
            onClearScannedStudentId()
        }
    }

    // Filter students by Name or Barcode / Student ID
    val searchFilteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else {
            val q = searchQuery.trim()
            students.filter { student ->
                student.name.contains(q, ignoreCase = true) ||
                student.id.contains(q, ignoreCase = true) ||
                student.rollNumber.contains(q, ignoreCase = true) ||
                student.className.contains(q, ignoreCase = true)
            }
        }
    }

    val examOptions = listOf("Monthly Exam - Sept 2026", "Monthly Exam - Oct 2026", "Annual Exam - 2026")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Record Student Exam Marks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search or scan student barcode, select from 7 subjects, and save marks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onLaunchBarcodeScanner,
                    modifier = Modifier.testTag("btn_marks_open_scanner")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Student Barcode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (saveSuccessMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = saveSuccessMessage ?: "",
                            color = Color(0xFF166534),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { saveSuccessMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // SECTION 1: SEARCH & SELECT STUDENT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Student Selection",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (selectedStudent != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Selected: ${selectedStudent!!.name}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search input by Name or Barcode
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            // If typed query exactly matches a barcode or name, select it automatically
                            val exactMatch = students.find {
                                it.id.equals(query.trim(), ignoreCase = true) ||
                                it.name.equals(query.trim(), ignoreCase = true)
                            }
                            if (exactMatch != null) {
                                selectedStudent = exactMatch
                            }
                        },
                        label = { Text("Search by Name or Barcode / ID") },
                        placeholder = { Text("e.g. Rahul Sharma or STU-10-A-01") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.testTag("btn_clear_marks_search")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                                IconButton(
                                    onClick = onLaunchBarcodeScanner,
                                    modifier = Modifier.testTag("btn_scan_student_barcode_field")
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Barcode",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_marks_student_search")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selected student preview card
                    if (selectedStudent != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_selected_student"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = selectedStudent!!.name.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedStudent!!.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Class ${selectedStudent!!.className}-${selectedStudent!!.section} • Roll ${selectedStudent!!.rollNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.QrCode,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = selectedStudent!!.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filtered students selection chips or list
                    Text(
                        text = if (searchQuery.isBlank()) "Matching Students (Tap to select):" else "Search Results (${searchFilteredStudents.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (searchFilteredStudents.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No student found matching \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("btn_reset_marks_student_filter")
                                ) {
                                    Text("Show All Students")
                                }
                            }
                        }
                    } else {
                        // Scrollable student choice list
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            searchFilteredStudents.forEach { st ->
                                val isSelected = selectedStudent?.id == st.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedStudent = st
                                            searchQuery = st.name
                                        }
                                        .testTag("chip_student_${st.id}"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedStudent = st
                                                searchQuery = st.name
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = st.name,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Barcode / ID: ${st.id} • Class ${st.className}-${st.section}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION 2: EXAM TYPE
                    Text("2. Exam Type", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        examOptions.forEach { opt ->
                            FilterChip(
                                selected = examType == opt,
                                onClick = { examType = opt },
                                label = { Text(if (opt.contains("Annual")) "Annual" else if (opt.contains("Oct")) "Monthly Oct" else "Monthly Sept") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION 3: SEVEN SUBJECTS WITH EDITABLE NAMES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "3. Subject (7 Subjects Available)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Tap subject to select, or tap pencil icon to edit subject name",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Render the 7 subjects as interactive cards / chips with edit action
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        subjectList.forEachIndexed { index, subName ->
                            val isSelected = selectedSubjectIndex == index
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSubjectIndex = index }
                                    .testTag("subject_item_$index"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = subName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    // Pencil Button to edit subject name
                                    IconButton(
                                        onClick = {
                                            subjectToEditIndex = index
                                            subjectEditTempText = subName
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("btn_edit_subject_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit $subName",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION 4: MARKS & REMARKS
                    Text(
                        text = "4. Enter Marks for $currentSubject",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = marksInput,
                            onValueChange = { marksInput = it },
                            label = { Text("Marks Obtained") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_marks_obtained"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = maxMarksInput,
                            onValueChange = { maxMarksInput = it },
                            label = { Text("Max Marks") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_max_marks"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = remarksInput,
                        onValueChange = { remarksInput = it },
                        label = { Text("Teacher Remarks") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_exam_remarks"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (selectedStudent != null && marksInput.isNotBlank()) {
                                val marks = marksInput.toIntOrNull() ?: 0
                                val max = maxMarksInput.toIntOrNull() ?: 100
                                val pct = if (max > 0) (marks.toDouble() / max.toDouble()) * 100.0 else 0.0
                                val grade = when {
                                    pct >= 90 -> "A+"
                                    pct >= 80 -> "A"
                                    pct >= 70 -> "B+"
                                    pct >= 60 -> "B"
                                    pct >= 50 -> "C"
                                    else -> "D"
                                }
                                val entity = ExamResultEntity(
                                    studentId = selectedStudent!!.id,
                                    schoolId = selectedStudent!!.schoolId,
                                    examType = examType,
                                    examMonthYear = "2026-09",
                                    subject = currentSubject,
                                    marksObtained = marks,
                                    maxMarks = max,
                                    grade = grade,
                                    remarks = remarksInput
                                )
                                onSaveMarks(entity)
                                saveSuccessMessage = "Recorded $currentSubject marks ($marks/$max, Grade: $grade) for ${selectedStudent!!.name}. Saved to Cloud Firestore."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_exam_marks"),
                        enabled = selectedStudent != null
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Exam Result to Report Card")
                    }
                }
            }
        }
    }

    // Dialog for Editing Subject Name
    if (subjectToEditIndex != null) {
        val targetIndex = subjectToEditIndex!!
        AlertDialog(
            onDismissRequest = { subjectToEditIndex = null },
            title = {
                Text("Edit Subject ${targetIndex + 1}")
            },
            text = {
                Column {
                    Text(
                        text = "Customize the name of this subject. It will be reflected in the marks selection and saved on the report card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = subjectEditTempText,
                        onValueChange = { subjectEditTempText = it },
                        label = { Text("Subject Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_subject_name")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = subjectEditTempText.trim()
                        if (trimmed.isNotEmpty() && targetIndex in subjectList.indices) {
                            subjectList[targetIndex] = trimmed
                        }
                        subjectToEditIndex = null
                    },
                    enabled = subjectEditTempText.isNotBlank(),
                    modifier = Modifier.testTag("btn_confirm_edit_subject")
                ) {
                    Text("Save Subject Name")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { subjectToEditIndex = null },
                    modifier = Modifier.testTag("btn_cancel_edit_subject")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherNotificationsView(
    students: List<StudentEntity>,
    sentNotifications: List<NotificationEntity>,
    currentTeacherId: String = "",
    currentTeacherName: String = "",
    schoolId: String = "",
    schoolName: String = "",
    scannedStudentIdToSelect: String? = null,
    onClearScannedStudentId: () -> Unit = {},
    onLaunchBarcodeScanner: () -> Unit = {},
    onSendNotice: (
        targetType: String,
        targetStudentId: String?,
        targetClassName: String?,
        targetSection: String?,
        title: String,
        message: String,
        imageUrl: String?,
        isPaymentAlert: Boolean,
        amountDue: Double?,
        senderTeacherName: String?,
        senderTeacherId: String?
    ) -> Unit
) {
    // Sub-tab: 0 = Send Notice, 1 = Notice Board
    var selectedSubTab by remember { mutableStateOf(0) }

    // Target Selection: 0 = All Students, 1 = Specific Class, 2 = Specific Student
    var targetSelectionIndex by remember { mutableStateOf(0) }

    // Class selection
    val availableClasses = remember(students) {
        val set = mutableSetOf("10", "9", "8", "7", "6")
        students.forEach { s ->
            if (s.className.isNotBlank()) {
                set.add(s.className)
            }
        }
        set.toList().sorted()
    }
    var selectedClassName by remember { mutableStateOf(availableClasses.firstOrNull() ?: "10") }
    var selectedSection by remember { mutableStateOf("A") }
    var expandedClassDropdown by remember { mutableStateOf(false) }

    // Student selection
    var selectedStudentId by remember { mutableStateOf(students.firstOrNull()?.id ?: "") }
    var studentSearchQuery by remember { mutableStateOf("") }
    var expandedStudentDropdown by remember { mutableStateOf(false) }

    // Handle incoming barcode scanned student ID
    LaunchedEffect(scannedStudentIdToSelect) {
        if (!scannedStudentIdToSelect.isNullOrBlank()) {
            val matched = students.find { it.id.equals(scannedStudentIdToSelect, ignoreCase = true) || it.barcodeData.contains(scannedStudentIdToSelect) }
            if (matched != null) {
                selectedStudentId = matched.id
                targetSelectionIndex = 2
            }
        }
    }

    val filteredStudents = remember(students, studentSearchQuery) {
        if (studentSearchQuery.isBlank()) students
        else students.filter {
            it.name.contains(studentSearchQuery, ignoreCase = true) ||
            it.id.contains(studentSearchQuery, ignoreCase = true) ||
            it.rollNumber.contains(studentSearchQuery, ignoreCase = true) ||
            it.className.contains(studentSearchQuery, ignoreCase = true)
        }
    }

    val selectedStudent = remember(students, selectedStudentId) {
        students.find { it.id == selectedStudentId }
    }

    // Form fields
    var noticeTitle by remember { mutableStateOf("") }
    var noticeMessage by remember { mutableStateOf("") }
    var noticeImageUrl by remember { mutableStateOf("") }
    var isPaymentAlert by remember { mutableStateOf(false) }
    var amountDueText by remember { mutableStateOf("") }

    // Success dialog
    var sentNoticeInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tabs navigation
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("নোটিফিকেশন পাঠান (Send)", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_teacher_send_notice")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("নোটিশ বোর্ড (${sentNotifications.size})", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_teacher_notice_board")
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedSubTab == 1) {
                TeacherNoticesReadView(
                    notifications = sentNotifications,
                    schoolName = schoolName,
                    schoolId = schoolId
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Sender Teacher Identity Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.RecordVoiceOver,
                                        contentDescription = "Teacher Sender",
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Send Notice to Students",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "TEACHER ID",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "প্রেরক: ${currentTeacherName.ifBlank { "Teacher" }} (ID: ${currentTeacherId.ifBlank { "TEACHER" }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ছাত্রছাত্রীদের মোবাইল অ্যাপ ও প্রোফাইলে নোটিফিকেশন প্রেরিত হবে।",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Step 1: Target Audience Selection
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "১. প্রাপক নির্বাচন করুন (Target Audience)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "সকল শিক্ষার্থী",
                                    1 to "নির্দিষ্ট শ্রেণি",
                                    2 to "নির্দিষ্ট ছাত্র/ছাত্রী"
                                ).forEach { (idx, label) ->
                                    FilterChip(
                                        selected = targetSelectionIndex == idx,
                                        onClick = { targetSelectionIndex = idx },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                        modifier = Modifier.weight(1f).testTag("chip_teacher_target_$idx")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (targetSelectionIndex) {
                                0 -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "বিদ্যালয়ের সকল নিবন্ধিত শিক্ষার্থীর কাছে নোটিফিকেশন পৌঁছাবে (${students.size} জন শিক্ষার্থী)।",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    // Class & Section Picker
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ExposedDropdownMenuBox(
                                                expanded = expandedClassDropdown,
                                                onExpandedChange = { expandedClassDropdown = !expandedClassDropdown },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                OutlinedTextField(
                                                    value = "Class $selectedClassName",
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("শ্রেণি") },
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassDropdown) },
                                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = expandedClassDropdown,
                                                    onDismissRequest = { expandedClassDropdown = false }
                                                ) {
                                                    availableClasses.forEach { cls ->
                                                        DropdownMenuItem(
                                                            text = { Text("Class $cls") },
                                                            onClick = {
                                                                selectedClassName = cls
                                                                expandedClassDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            OutlinedTextField(
                                                value = selectedSection,
                                                onValueChange = { selectedSection = it.uppercase() },
                                                label = { Text(" শাখা (Section)") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Class, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val countInClass = students.count { it.className.equals(selectedClassName, ignoreCase = true) && (selectedSection.isBlank() || it.section.equals(selectedSection, ignoreCase = true)) }
                                                Text(
                                                    text = "Class $selectedClassName-$selectedSection এর শিক্ষার্থীদের ($countInClass জন) কাছে নোটিশ পাঠানো হবে।",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Specific Student Picker with Barcode Scanner option
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = studentSearchQuery,
                                                onValueChange = { studentSearchQuery = it },
                                                label = { Text("শিক্ষার্থী খুঁজুন (নাম, রোল বা আইডি)") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )

                                            FilledTonalIconButton(
                                                onClick = onLaunchBarcodeScanner,
                                                modifier = Modifier.testTag("btn_scan_student_for_notice")
                                            ) {
                                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                                            }
                                        }

                                        ExposedDropdownMenuBox(
                                            expanded = expandedStudentDropdown,
                                            onExpandedChange = { expandedStudentDropdown = !expandedStudentDropdown },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val displayText = if (selectedStudent != null) {
                                                "${selectedStudent.name} (Class ${selectedStudent.className}-${selectedStudent.section} • Roll: ${selectedStudent.rollNumber} • ID: ${selectedStudent.id})"
                                            } else {
                                                "শিক্ষার্থী নির্বাচন করুন"
                                            }

                                            OutlinedTextField(
                                                value = displayText,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("নির্বাচিত শিক্ষার্থী") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStudentDropdown) },
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                                    .testTag("dropdown_teacher_select_student")
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expandedStudentDropdown,
                                                onDismissRequest = { expandedStudentDropdown = false }
                                            ) {
                                                filteredStudents.forEach { s ->
                                                    DropdownMenuItem(
                                                        text = { Text("${s.name} (${s.id} • Class ${s.className}-${s.section} • Roll: ${s.rollNumber})") },
                                                        onClick = {
                                                            selectedStudentId = s.id
                                                            expandedStudentDropdown = false
                                                        }
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

                // Step 2: Quick Notice Presets
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "২. দ্রুত নোটিশ প্রিসেট (Quick Presets)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            noticeTitle = "আজকের বাড়ির কাজ (Homework)"
                                            noticeMessage = "প্রিয় শিক্ষার্থী, আজকের ক্লাসে দেওয়া বাড়ির কাজগুলো আগামীকাল অবশ্যই সম্পন্ন করে ক্লাসে নিয়ে আসবে।"
                                            isPaymentAlert = false
                                        },
                                        label = { Text("বাড়ির কাজ", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            noticeTitle = "অনুপস্থিতির সতর্কতা (Attendance Alert)"
                                            noticeMessage = "সম্মানিত অভিভাবক, আপনার সন্তান আজ বিদ্যালয়ে অনুপস্থিত ছিল। অনুগ্রহ করে নিয়মিত উপস্থিতি নিশ্চিত করুন।"
                                            isPaymentAlert = false
                                        },
                                        label = { Text("অনুপস্থিতি", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            noticeTitle = "ক্লাস টেস্ট ঘোষণা (Class Test)"
                                            noticeMessage = "আগামী সোমবার অধ্যায় ৩ এর ওপর ২০ নম্বরের শ্রেণি পরীক্ষা অনুষ্ঠিত হবে। প্রস্তুতি নিয়ে আসবে।"
                                            isPaymentAlert = false
                                        },
                                        label = { Text("ক্লাস টেস্ট", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    AssistChip(
                                        onClick = {
                                            noticeTitle = "স্কুল ফি / বেতন পরিশোধের নোটিশ (Fee Due)"
                                            noticeMessage = "চলতি মাসের স্কুল ফি আগামী ১০ তারিখের মধ্যে পরিশোধ করার জন্য অনুরোধ করা হচ্ছে।"
                                            isPaymentAlert = true
                                            amountDueText = "1000.0"
                                        },
                                        label = { Text("ফি নোটিশ", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 3: Notice Content
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "৩. নোটিফিকেশনের বিষয় ও বার্তা",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = noticeTitle,
                                onValueChange = { noticeTitle = it },
                                label = { Text("নোটিশের শিরোনাম * (Title)") },
                                placeholder = { Text("যেমন: জরুরি ক্লাস টেস্ট / বাড়ির কাজ") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_teacher_notice_title"),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = noticeMessage,
                                onValueChange = { noticeMessage = it },
                                label = { Text("বিস্তারিত বার্তা * (Message Body)") },
                                placeholder = { Text("নোটিফিকেশনের সম্পূর্ণ বার্তা এখানে লিখুন...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_teacher_notice_message"),
                                minLines = 3,
                                maxLines = 5
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = noticeImageUrl,
                                onValueChange = { noticeImageUrl = it },
                                label = { Text("ছবির লিঙ্ক (Optional Image URL)") },
                                placeholder = { Text("https://example.com/image.jpg") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Fee / Payment alert toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("বকেয়া ফি অ্যালার্ট অন্তর্ভুক্ত করুন", style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = isPaymentAlert,
                                    onCheckedChange = { isPaymentAlert = it },
                                    modifier = Modifier.testTag("switch_teacher_payment_alert")
                                )
                            }

                            if (isPaymentAlert) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = amountDueText,
                                    onValueChange = { amountDueText = it },
                                    label = { Text("বকেয়া টাকার পরিমাণ (₹ / $)") },
                                    placeholder = { Text("e.g. 1000.00") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_teacher_amount_due"),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val canSend = noticeTitle.isNotBlank() && noticeMessage.isNotBlank() && (targetSelectionIndex != 2 || selectedStudentId.isNotBlank())

                            Button(
                                onClick = {
                                    val targetType = when (targetSelectionIndex) {
                                        0 -> "ALL_STUDENTS"
                                        1 -> "CLASS"
                                        2 -> "STUDENT"
                                        else -> "ALL_STUDENTS"
                                    }
                                    val targetStudentId = if (targetSelectionIndex == 2) selectedStudentId else null
                                    val targetClassName = if (targetSelectionIndex == 1) selectedClassName else selectedStudent?.className
                                    val targetSection = if (targetSelectionIndex == 1) selectedSection else selectedStudent?.section
                                    val amount = amountDueText.toDoubleOrNull()

                                    onSendNotice(
                                        targetType,
                                        targetStudentId,
                                        targetClassName,
                                        targetSection,
                                        noticeTitle.trim(),
                                        noticeMessage.trim(),
                                        noticeImageUrl.ifBlank { null },
                                        isPaymentAlert,
                                        amount,
                                        currentTeacherName.ifBlank { null },
                                        currentTeacherId.ifBlank { null }
                                    )

                                    val targetLabel = when (targetSelectionIndex) {
                                        0 -> "সকল শিক্ষার্থী (All Students)"
                                        1 -> "Class $selectedClassName-$selectedSection"
                                        2 -> "শিক্ষার্থী: ${selectedStudent?.name ?: selectedStudentId}"
                                        else -> "শিক্ষার্থীবৃন্দ"
                                    }
                                    sentNoticeInfo = Pair(noticeTitle.trim(), targetLabel)

                                    // Clear inputs
                                    noticeTitle = ""
                                    noticeMessage = ""
                                    noticeImageUrl = ""
                                    amountDueText = ""
                                    isPaymentAlert = false
                                    onClearScannedStudentId()
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_teacher_send_notice"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "টিচার আইডি থেকে নোটিফিকেশন পাঠান",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Confirmation Alert Dialog
    if (sentNoticeInfo != null) {
        val (titleSent, targetSent) = sentNoticeInfo!!
        AlertDialog(
            onDismissRequest = { sentNoticeInfo = null },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("নোটিফিকেশন সফলভাবে প্রেরিত") },
            text = {
                Column {
                    Text("আপনার Teacher ID (${currentTeacherId.ifBlank { "TEACHER" }}) থেকে শিক্ষার্থীদের নোটিফিকেশন পাঠানো হয়েছে।")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• শিরোনাম: $titleSent", fontWeight = FontWeight.Bold)
                    Text("• প্রাপক: $targetSent")
                    Text("• স্ট্যাটাস: শিক্ষার্থীদের নোটিফিকেশন হিস্টোরি ও ডিভাইসে তাৎক্ষণিক যুক্ত হয়েছে।")
                }
            },
            confirmButton = {
                Button(onClick = { sentNoticeInfo = null }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}

@Composable
fun AddStudentDialog(
    schoolId: String,
    schoolName: String,
    onDismiss: () -> Unit,
    onSave: (StudentEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("10") }
    var section by remember { mutableStateOf("A") }
    var rollNumber by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var motherName by remember { mutableStateOf("") }
    var parentMobile by remember { mutableStateOf("") }
    var studentMobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("pass123") }
    var gender by remember { mutableStateOf("Male") }
    var dateOfBirth by remember { mutableStateOf("") }
    var casteCategory by remember { mutableStateOf("General") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var photoUri by remember { mutableStateOf("") }
    val context = LocalContext.current

    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dateOfBirth = formatted
            },
            calendar.get(Calendar.YEAR) - 15,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val addStudentPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                photoUri = uri.toString()
            }
        }
    )

    val genderOptions = listOf("Male", "Female", "Other")
    val bloodOptions = listOf("A+", "B+", "O+", "AB+", "O-")
    val casteOptions = listOf("General", "OBC", "SC", "ST")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create Student Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A unique ZXing Barcode will be auto-generated for this student.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it },
                        label = { Text("Class *") },
                        modifier = Modifier.weight(1f).testTag("input_new_student_class"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section *") },
                        modifier = Modifier.weight(1f).testTag("input_new_student_section"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll No. *") },
                        modifier = Modifier.weight(1f).testTag("input_new_student_roll"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    placeholder = { Text("e.g. 2010-05-15") },
                    leadingIcon = {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier.testTag("btn_pick_student_dob")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date of Birth", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_dob"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = { Text("Father's Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_father"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = motherName,
                    onValueChange = { motherName = it },
                    label = { Text("Mother's Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_mother"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = parentMobile,
                    onValueChange = { parentMobile = it },
                    label = { Text("Parent's Mobile Number") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_parent_phone"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = studentMobile,
                    onValueChange = { studentMobile = it },
                    label = { Text("Student Mobile Number") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_phone"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Student Password *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_student_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Gender:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genderOptions.forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Blood Group:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bloodOptions.forEach { b ->
                        FilterChip(
                            selected = bloodGroup == b,
                            onClick = { bloodGroup = b },
                            label = { Text(b) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Caste Category:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    casteOptions.forEach { c ->
                        FilterChip(
                            selected = casteCategory == c,
                            onClick = { casteCategory = c },
                            label = { Text(c) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Student Profile Photo:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = photoUri,
                        onValueChange = { photoUri = it },
                        label = { Text("Photo URL / Device Pick") },
                        placeholder = { Text("Pick or paste URL") },
                        modifier = Modifier.weight(1f).testTag("input_new_student_photo"),
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = {
                            addStudentPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("btn_add_student_pick_photo")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Pick Photo", modifier = Modifier.size(18.dp))
                    }
                }

                if (photoUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Photo Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Photo Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { photoUri = "" }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).testTag("btn_cancel_add_student")
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && className.isNotBlank() && rollNumber.isNotBlank()) {
                                val generatedStudentId = "STU-$className-$section-$rollNumber"
                                val barcodePayload = "$schoolId:$className:$section:$rollNumber:$generatedStudentId"
                                val student = StudentEntity(
                                    id = generatedStudentId,
                                    schoolId = schoolId,
                                    schoolName = schoolName,
                                    name = name,
                                    className = className,
                                    section = section,
                                    rollNumber = rollNumber,
                                    fatherName = fatherName,
                                    motherName = motherName,
                                    parentMobile = parentMobile,
                                    studentMobile = studentMobile,
                                    password = password,
                                    gender = gender,
                                    dateOfBirth = dateOfBirth,
                                    casteCategory = casteCategory,
                                    bloodGroup = bloodGroup,
                                    photoUri = photoUri,
                                    barcodeData = barcodePayload
                                )
                                onSave(student)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_save_new_student"),
                        enabled = name.isNotBlank() && className.isNotBlank() && rollNumber.isNotBlank()
                    ) {
                        Text("Create Profile")
                    }
                }
            }
        }
    }
}

@Composable
fun EditStudentCredentialsDialog(
    student: StudentEntity,
    onDismiss: () -> Unit,
    onSave: (phone: String, pass: String) -> Unit
) {
    var phone by remember { mutableStateOf(student.studentMobile) }
    var password by remember { mutableStateOf(student.password) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student Credentials") },
        text = {
            Column {
                Text(
                    text = "Update login phone number and password for ${student.name} (${student.id}):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Student Mobile Number") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_phone"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Student Password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_password"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phone, password) },
                modifier = Modifier.testTag("btn_save_student_creds")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (newPass: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Teacher Password") },
        text = {
            Column {
                Text("Enter a new password for your teacher account:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_teacher_pass"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Confirm New Password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_confirm_teacher_pass"),
                    singleLine = true
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.isBlank()) {
                        error = "Password cannot be blank"
                    } else if (newPassword != confirmPassword) {
                        error = "Passwords do not match"
                    } else {
                        onConfirm(newPassword)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_change_password")
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UploadStudentPhotoDialog(
    student: StudentEntity,
    teacherId: String,
    teacherName: String,
    onDismiss: () -> Unit,
    onSavePhoto: (photoUri: String) -> Unit
) {
    var photoUriInput by remember { mutableStateOf(student.photoUri) }
    var previewUri by remember { mutableStateOf(student.photoUri) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val uriStr = uri.toString()
                photoUriInput = uriStr
                previewUri = uriStr
            }
        }
    )

    val sampleAvatars = listOf(
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=400&q=80",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&q=80",
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&q=80",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&q=80",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&q=80",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&q=80"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Upload Student Photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${student.name} • Class ${student.className}-${student.section} (Roll: ${student.rollNumber})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teacher ID authorization badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Teacher ID: ${teacherId.ifBlank { "TCH-001" }} (${teacherName.ifBlank { "Authorized Faculty" }})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live Photo Preview
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                        .testTag("preview_student_photo"),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri.isNotBlank()) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = "Selected Student Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No Photo Set",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (previewUri.isNotBlank()) {
                    TextButton(
                        onClick = {
                            previewUri = ""
                            photoUriInput = ""
                        },
                        modifier = Modifier.testTag("btn_clear_student_photo")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove Photo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Option 1: Gallery Photo Picker
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_pick_photo_gallery"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose from Device Gallery")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option 2: Sample Student Portraits
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Or choose sample student portrait:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sampleAvatars) { avatarUrl ->
                            val isSelected = previewUri == avatarUrl
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        previewUri = avatarUrl
                                        photoUriInput = avatarUrl
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Sample Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option 3: Manual URL entry
                OutlinedTextField(
                    value = photoUriInput,
                    onValueChange = {
                        photoUriInput = it
                        previewUri = it.trim()
                    },
                    label = { Text("Or Paste Photo URL") },
                    placeholder = { Text("https://example.com/photo.jpg") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_student_photo_url"),
                    singleLine = true,
                    trailingIcon = {
                        if (photoUriInput.isNotBlank()) {
                            IconButton(onClick = { photoUriInput = ""; previewUri = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_cancel_upload_photo")
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSavePhoto(previewUri.trim())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_student_photo")
                    ) {
                        Text("Save Photo")
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherProfileDialog(
    teacher: TeacherEntity?,
    currentSession: UserSessionState,
    onDismiss: () -> Unit,
    onSaveProfile: (TeacherEntity) -> Unit,
    onDeleteProfile: () -> Unit
) {
    val effectiveTeacher = teacher ?: TeacherEntity(
        id = currentSession.userId.ifBlank { "TEACHER" },
        schoolId = currentSession.schoolId,
        schoolName = currentSession.schoolName.ifBlank { "School Faculty" },
        name = currentSession.userName.ifBlank { "Teacher" },
        email = "",
        mobile = "",
        assignedClass = currentSession.userClass.ifBlank { "Class 10" },
        subject = "General",
        password = ""
    )

    var name by remember { mutableStateOf(effectiveTeacher.name) }
    var email by remember { mutableStateOf(effectiveTeacher.email) }
    var mobile by remember { mutableStateOf(effectiveTeacher.mobile) }
    var assignedClass by remember { mutableStateOf(effectiveTeacher.assignedClass) }
    var subject by remember { mutableStateOf(effectiveTeacher.subject) }
    var password by remember { mutableStateOf(effectiveTeacher.password) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Teacher Tutender Profile")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Teacher ID: ${effectiveTeacher.id}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "School: ${effectiveTeacher.schoolName} (${effectiveTeacher.schoolId})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Teacher Full Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_teacher_profile_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth().testTag("input_teacher_profile_email"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth().testTag("input_teacher_profile_mobile"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = assignedClass,
                        onValueChange = { assignedClass = it },
                        label = { Text("Class") },
                        modifier = Modifier.weight(1f).testTag("input_teacher_profile_class"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f).testTag("input_teacher_profile_subject"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Account Password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_teacher_profile_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Danger Zone: Delete Profile
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Delete Tutender Profile",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Permanently remove your teacher profile (ID: ${effectiveTeacher.id}) from the system.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("btn_delete_teacher_profile_trigger")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete My Profile (ID: ${effectiveTeacher.id})")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProfile(
                        effectiveTeacher.copy(
                            name = name.ifBlank { effectiveTeacher.name },
                            email = email.ifBlank { effectiveTeacher.email },
                            mobile = mobile.ifBlank { effectiveTeacher.mobile },
                            assignedClass = assignedClass.ifBlank { effectiveTeacher.assignedClass },
                            subject = subject.ifBlank { effectiveTeacher.subject },
                            password = password.ifBlank { effectiveTeacher.password }
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_save_teacher_profile")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm Profile Deletion") },
            text = {
                Text("Are you sure you want to permanently delete your Tutender teacher profile (${effectiveTeacher.name}, ID: ${effectiveTeacher.id})?\n\nThis will permanently delete your teacher credentials and log you out immediately.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_teacher_self")
                ) {
                    Text("Delete Forever & Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EditStudentIdAndProfileDialog(
    student: StudentEntity,
    teacherId: String,
    teacherName: String,
    onDismiss: () -> Unit,
    onSave: (oldId: String, updated: StudentEntity) -> Unit
) {
    var studentId by remember { mutableStateOf(student.id) }
    var name by remember { mutableStateOf(student.name) }
    var className by remember { mutableStateOf(student.className) }
    var section by remember { mutableStateOf(student.section) }
    var rollNumber by remember { mutableStateOf(student.rollNumber) }
    var dateOfBirth by remember { mutableStateOf(student.dateOfBirth) }
    var fatherName by remember { mutableStateOf(student.fatherName) }
    var motherName by remember { mutableStateOf(student.motherName) }
    var studentMobile by remember { mutableStateOf(student.studentMobile) }
    var parentMobile by remember { mutableStateOf(student.parentMobile) }
    var gender by remember { mutableStateOf(student.gender) }
    var casteCategory by remember { mutableStateOf(student.casteCategory) }
    var bloodGroup by remember { mutableStateOf(student.bloodGroup) }
    var password by remember { mutableStateOf(student.password) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val editDobDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dateOfBirth = formatted
            },
            calendar.get(Calendar.YEAR) - 15,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Student ID & Profile")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Authorized under Teacher ID: $teacherId ($teacherName)",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = { Text("Student ID (Unique)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_id"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Full Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it },
                        label = { Text("Class") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_class"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_section"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll #") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_roll"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    placeholder = { Text("e.g. 2010-05-15") },
                    leadingIcon = {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { editDobDialog.show() },
                            modifier = Modifier.testTag("btn_pick_edit_student_dob")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date of Birth", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_dob"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("Father Name") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_father"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = motherName,
                        onValueChange = { motherName = it },
                        label = { Text("Mother Name") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_mother"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = studentMobile,
                        onValueChange = { studentMobile = it },
                        label = { Text("Student Phone") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_phone"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bloodGroup,
                        onValueChange = { bloodGroup = it },
                        label = { Text("Blood Group") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_blood"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = parentMobile,
                        onValueChange = { parentMobile = it },
                        label = { Text("Parent Mobile") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_parent_phone"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender") },
                        modifier = Modifier.weight(1f).testTag("input_edit_student_gender"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = casteCategory,
                    onValueChange = { casteCategory = it },
                    label = { Text("Category (General/OBC/SC/ST)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Student Password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_student_pwd"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanId = studentId.trim().ifBlank { student.id }
                    val updated = student.copy(
                        id = cleanId,
                        name = name.ifBlank { student.name },
                        className = className.ifBlank { student.className },
                        section = section.ifBlank { student.section },
                        rollNumber = rollNumber.ifBlank { student.rollNumber },
                        fatherName = fatherName.ifBlank { student.fatherName },
                        motherName = motherName.ifBlank { student.motherName },
                        parentMobile = parentMobile.ifBlank { student.parentMobile },
                        studentMobile = studentMobile,
                        gender = gender.ifBlank { student.gender },
                        dateOfBirth = dateOfBirth.ifBlank { student.dateOfBirth },
                        casteCategory = casteCategory.ifBlank { student.casteCategory },
                        bloodGroup = bloodGroup.ifBlank { student.bloodGroup },
                        password = password.ifBlank { student.password },
                        barcodeData = "${student.schoolId}:$className:$section:$rollNumber:$cleanId"
                    )
                    onSave(student.id, updated)
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_save_edit_student_id")
            ) {
                Text("Save ID & Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmRemoveStudentDialog(
    student: StudentEntity,
    teacherId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Remove Student ID") },
        text = {
            Column {
                Text("Are you sure you want to remove Student ID '${student.id}' (${student.name}) from Class ${student.className}-${student.section}?")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Authorized by Teacher ID: $teacherId\nThis permanently removes the student's records and profile.",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("btn_confirm_remove_student_${student.id}")
            ) {
                Text("Remove Student ID")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
