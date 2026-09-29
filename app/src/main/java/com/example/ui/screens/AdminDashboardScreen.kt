package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.sync.SyncState
import com.example.ui.components.CameraScannerDialog
import com.example.ui.components.CloudSyncCard
import com.example.ui.components.CloudSyncDialog
import com.example.ui.components.AdminNotificationDispatchView
import com.example.ui.components.NotificationBellButton
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    schools: List<SchoolEntity>,
    teachers: List<TeacherEntity>,
    students: List<StudentEntity>,
    attendanceRecords: List<AttendanceEntity>,
    products: List<ProductEntity>,
    appSettings: AppSettingsEntity?,
    recentSearches: List<SearchHistoryEntity> = emptyList(),
    adminId: String = "ADM-001",
    syncState: SyncState = SyncState(),
    onSyncRoomToFirestore: () -> Unit = {},
    onSyncFirestoreToRoom: () -> Unit = {},
    onSeedFirebaseData: () -> Unit = {},
    onInitializeFirestoreSchema: () -> Unit = {},
    onClearDemoAccounts: () -> Unit = {},
    onDeleteSchool: (SchoolEntity) -> Unit = {},
    onDeleteTeacher: (TeacherEntity) -> Unit = {},
    onUpdateTeacher: (originalTeacherId: String, updatedTeacher: TeacherEntity) -> Unit = { _, _ -> },
    onRegisterTeacherOnly: (
        schoolId: String,
        schoolName: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPass: String,
        assignedClass: String,
        subject: String
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onRegisterSchoolAndTeacher: (
        schoolName: String,
        address: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPass: String,
        assignedClass: String
    ) -> Unit,
    onAddProduct: (
        title: String,
        description: String,
        imageUrl: String,
        purchaseUrl: String,
        price: Double,
        category: String,
        targetAudience: String
    ) -> Unit,
    onUpdateProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onUpdateAppSettings: (appName: String, logoUrl: String) -> Unit,
    onRecordAttendance: (studentId: String, type: String) -> Unit,
    onSaveSearchQuery: (query: String, searchType: String) -> Unit = { _, _ -> },
    onDeleteSearchHistory: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onOpenScannerScreen: () -> Unit = {},
    notifications: List<NotificationEntity> = emptyList(),
    onSendNotification: (
        targetType: String,
        targetId: String,
        recipientUserId: String,
        title: String,
        body: String,
        deepLinkType: String,
        deepLinkId: String,
        isPaymentAlert: Boolean,
        amountDue: Double?
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onOpenNotificationHistory: () -> Unit = {},
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview, 1: Credentials, 2: Attendance, 3: Store Management, 4: Push Alerts
    var showScannerDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showAddSchoolDialog by remember { mutableStateOf(false) }
    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Root Admin Dashboard", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("banglarbabp2021@gmail.com • Master Control", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    // Notification Bell with Badge & History
                    NotificationBellButton(
                        notifications = notifications,
                        modifier = Modifier.testTag("admin_notification_bell"),
                        onOpenHistory = onOpenNotificationHistory
                    )

                    // Cloud Sync action
                    IconButton(
                        onClick = { showCloudSyncDialog = true },
                        modifier = Modifier.testTag("btn_admin_cloud_sync")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud Storage Sync",
                            tint = if (syncState.isCloudConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Barcode Scanner quick launch
                    IconButton(
                        onClick = { onOpenScannerScreen() },
                        modifier = Modifier.testTag("btn_admin_scan")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Attendance", tint = MaterialTheme.colorScheme.primary)
                    }

                    // App Branding Customization
                    IconButton(
                        onClick = { showBrandingDialog = true },
                        modifier = Modifier.testTag("btn_admin_branding")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "App Customization")
                    }

                    // Logout
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.testTag("btn_admin_logout")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSchoolDialog = true },
                    icon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
                    text = { Text("Register School") },
                    modifier = Modifier.testTag("fab_admin_add_school")
                )
            } else if (selectedTab == 3) {
                ExtendedFloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = null) },
                    text = { Text("Upload Product") },
                    modifier = Modifier.testTag("fab_admin_add_product")
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Metrics") },
                    modifier = Modifier.testTag("nav_admin_overview")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    label = { Text("Credentials") },
                    modifier = Modifier.testTag("nav_admin_credentials")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                    label = { Text("Attendance") },
                    modifier = Modifier.testTag("nav_admin_attendance")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                    label = { Text("Store") },
                    modifier = Modifier.testTag("nav_admin_store")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                    label = { Text("Push Alerts") },
                    modifier = Modifier.testTag("nav_admin_push_alerts")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Admin Overview & Metrics
                    AdminOverviewView(
                        schools = schools,
                        teachers = teachers,
                        students = students,
                        attendanceRecords = attendanceRecords,
                        syncState = syncState,
                        onSyncRoomToFirestore = onSyncRoomToFirestore,
                        onSyncFirestoreToRoom = onSyncFirestoreToRoom,
                        onSeedFirebaseData = onSeedFirebaseData,
                        onInitializeFirestoreSchema = onInitializeFirestoreSchema,
                        onDeleteSchool = onDeleteSchool
                    )
                }

                1 -> {
                    // Schools & Teachers Credentials Management
                    AdminCredentialsView(
                        schools = schools,
                        teachers = teachers,
                        students = students,
                        recentSearches = recentSearches,
                        adminId = adminId,
                        onDeleteSchool = onDeleteSchool,
                        onDeleteTeacher = onDeleteTeacher,
                        onUpdateTeacher = onUpdateTeacher,
                        onSaveSearchQuery = onSaveSearchQuery,
                        onDeleteSearchHistory = onDeleteSearchHistory,
                        onClearSearchHistory = onClearSearchHistory,
                        onAddSchool = { showAddSchoolDialog = true },
                        onAddTeacher = { showAddTeacherDialog = true }
                    )
                }

                2 -> {
                    // Attendance Summary & Scanner View
                    AdminAttendanceSummaryView(
                        attendanceRecords = attendanceRecords,
                        students = students,
                        onOpenScanner = { onOpenScannerScreen() }
                    )
                }

                3 -> {
                    // Online Store Product Inventory Management
                    AdminStoreManagementView(
                        products = products,
                        onAddProduct = { showAddProductDialog = true },
                        onToggleStock = { prod ->
                            onUpdateProduct(prod.copy(inStock = !prod.inStock))
                        },
                        onUpdateProduct = onUpdateProduct,
                        onDelete = onDeleteProduct
                    )
                }

                4 -> {
                    // FCM Push Notifications Dispatcher (All Students, Specific Class, Specific Student, All Teachers)
                    AdminNotificationDispatchView(
                        students = students,
                        teachers = teachers,
                        notifications = notifications,
                        onSendNotification = onSendNotification
                    )
                }
            }
        }
    }

    // Barcode Scanner Dialog
    CameraScannerDialog(
        isOpen = showScannerDialog,
        onDismiss = { showScannerDialog = false },
        onBarcodeScanned = { studentId, type ->
            onRecordAttendance(studentId, type)
        },
        availableStudents = students,
        operatorRole = "ADMIN"
    )

    // Register School & Teacher Dialog
    if (showAddSchoolDialog) {
        RegisterSchoolDialog(
            onDismiss = { showAddSchoolDialog = false },
            onRegister = { sName, addr, tName, tEmail, tMob, tPass, cls ->
                onRegisterSchoolAndTeacher(sName, addr, tName, tEmail, tMob, tPass, cls)
                showAddSchoolDialog = false
            }
        )
    }

    // Register Teacher Only Dialog
    if (showAddTeacherDialog) {
        RegisterTeacherDialog(
            schools = schools,
            onDismiss = { showAddTeacherDialog = false },
            onRegister = { sId, sName, tName, tEmail, tMob, tPass, cls, subj ->
                onRegisterTeacherOnly(sId, sName, tName, tEmail, tMob, tPass, cls, subj)
                showAddTeacherDialog = false
            }
        )
    }

    // Add Product Dialog
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onSave = { title, desc, img, url, price, cat, aud ->
                onAddProduct(title, desc, img, url, price, cat, aud)
                showAddProductDialog = false
            }
        )
    }

    // App Branding Customization Dialog
    if (showBrandingDialog) {
        AppBrandingDialog(
            currentSettings = appSettings,
            onDismiss = { showBrandingDialog = false },
            onSave = { newName, newLogo ->
                onUpdateAppSettings(newName, newLogo)
                showBrandingDialog = false
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Administrator Logout") },
            text = { Text("Are you sure you want to end your master admin session?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("btn_confirm_admin_logout")
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

    // Cloud Synchronization Dialog
    CloudSyncDialog(
        isOpen = showCloudSyncDialog,
        syncState = syncState,
        onDismiss = { showCloudSyncDialog = false },
        onSyncRoomToFirestore = onSyncRoomToFirestore,
        onSyncFirestoreToRoom = onSyncFirestoreToRoom,
        onSeedFirebaseData = onSeedFirebaseData,
        onClearDemoAccounts = onClearDemoAccounts
    )
}

@Composable
fun AdminOverviewView(
    schools: List<SchoolEntity>,
    teachers: List<TeacherEntity>,
    students: List<StudentEntity>,
    attendanceRecords: List<AttendanceEntity>,
    syncState: SyncState = SyncState(),
    onSyncRoomToFirestore: () -> Unit = {},
    onSyncFirestoreToRoom: () -> Unit = {},
    onSeedFirebaseData: () -> Unit = {},
    onInitializeFirestoreSchema: () -> Unit = {},
    onDeleteSchool: (SchoolEntity) -> Unit = {}
) {
    val entriesToday = attendanceRecords.count { it.type == "ENTRY" }
    val exitsToday = attendanceRecords.count { it.type == "EXIT" }
    var schoolToDelete by remember { mutableStateOf<SchoolEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Room-to-Firestore Cloud Persistent Synchronization Card
        item {
            CloudSyncCard(
                syncState = syncState,
                onSyncRoomToFirestore = onSyncRoomToFirestore,
                onSyncFirestoreToRoom = onSyncFirestoreToRoom,
                onSeedFirebaseData = onSeedFirebaseData,
                onInitializeFirestoreSchema = onInitializeFirestoreSchema
            )
        }

        item {
            Text(
                text = "System Performance & Metrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Live analytics of schools, active teachers, enrolled students, and attendance logs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 4 KPI Cards Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminMetricCard(
                    title = "Schools",
                    value = "${schools.size}",
                    subtitle = "Registered",
                    icon = Icons.Default.Apartment,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Teachers",
                    value = "${teachers.size}",
                    subtitle = "Authorized",
                    icon = Icons.Default.CoPresent,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdminMetricCard(
                    title = "Students",
                    value = "${students.size}",
                    subtitle = "Enrolled",
                    icon = Icons.Default.School,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Attendance",
                    value = "${attendanceRecords.size}",
                    subtitle = "$entriesToday In / $exitsToday Out",
                    icon = Icons.Default.FactCheck,
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Class Details Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enrolled Classes & Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val classGroups = students.groupBy { "Class ${it.className}-${it.section}" }
                    if (classGroups.isEmpty()) {
                        Text("No classes registered yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        classGroups.forEach { (className, list) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(className, fontWeight = FontWeight.Medium)
                                Text("${list.size} Students", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // School Information List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Registered School Campuses (${schools.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (schools.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো নিবন্ধিত স্কুল নেই / No schools registered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(schools, key = { it.id }) { school ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("card_school_${school.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(school.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                        Text(
                                            text = "Reg: ${school.registrationId}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("School ID: ${school.id}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            IconButton(
                                onClick = { schoolToDelete = school },
                                modifier = Modifier.testTag("btn_delete_school_overview_${school.id}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Remove School",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(school.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (schoolToDelete != null) {
        ConfirmDeleteSchoolDialog(
            school = schoolToDelete!!,
            adminId = "ADM-001",
            onDismiss = { schoolToDelete = null },
            onConfirm = {
                val target = schoolToDelete
                schoolToDelete = null
                if (target != null) {
                    onDeleteSchool(target)
                }
            }
        )
    }
}

@Composable
fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminCredentialsView(
    schools: List<SchoolEntity>,
    teachers: List<TeacherEntity>,
    students: List<StudentEntity>,
    recentSearches: List<SearchHistoryEntity> = emptyList(),
    adminId: String = "ADM-001",
    onDeleteSchool: (SchoolEntity) -> Unit = {},
    onDeleteTeacher: (TeacherEntity) -> Unit = {},
    onUpdateTeacher: (originalTeacherId: String, updatedTeacher: TeacherEntity) -> Unit = { _, _ -> },
    onSaveSearchQuery: (query: String, searchType: String) -> Unit = { _, _ -> },
    onDeleteSearchHistory: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onAddSchool: () -> Unit,
    onAddTeacher: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var credentialTab by remember { mutableStateOf(0) } // 0: Schools, 1: Teachers, 2: Students
    var schoolToDelete by remember { mutableStateOf<SchoolEntity?>(null) }
    var teacherToEdit by remember { mutableStateOf<TeacherEntity?>(null) }
    var teacherToDelete by remember { mutableStateOf<TeacherEntity?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically record search query after user types (debounced)
    LaunchedEffect(searchQuery, credentialTab) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            kotlinx.coroutines.delay(1000)
            val type = if (credentialTab == 0) "SCHOOL" else if (credentialTab == 1) "TEACHER" else "STUDENT"
            onSaveSearchQuery(trimmed, type)
        }
    }

    val submitSearch = {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotBlank()) {
            val type = if (credentialTab == 0) "SCHOOL" else if (credentialTab == 1) "TEACHER" else "STUDENT"
            onSaveSearchQuery(trimmed, type)
        }
        keyboardController?.hide()
    }

    val filteredSchools = remember(schools, searchQuery) {
        if (searchQuery.isBlank()) schools
        else schools.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            it.registrationId.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredTeachers = remember(teachers, searchQuery) {
        if (searchQuery.isBlank()) teachers
        else teachers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            it.schoolName.contains(searchQuery, ignoreCase = true) ||
            it.assignedClass.contains(searchQuery, ignoreCase = true) ||
            it.mobile.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            it.schoolName.contains(searchQuery, ignoreCase = true) ||
            it.className.contains(searchQuery, ignoreCase = true) ||
            it.rollNumber.contains(searchQuery, ignoreCase = true) ||
            it.parentMobile.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Credential Management", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("View IDs, passwords & registration keys", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (credentialTab == 1) {
                    OutlinedButton(onClick = onAddTeacher, modifier = Modifier.testTag("btn_register_teacher_header")) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Teacher")
                    }
                }
                Button(onClick = onAddSchool, modifier = Modifier.testTag("btn_register_school_header")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New School")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input Field with Clear & Keyboard Search Actions
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    if (credentialTab == 0) "Search schools by name, ID, campus..."
                    else if (credentialTab == 1) "Search teachers by name, ID, school..."
                    else "Search students by name, ID, roll, school..."
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("btn_clear_search_query")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search query")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            modifier = Modifier.fillMaxWidth().testTag("input_search_credentials"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Recent Searches Section
        if (recentSearches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_history_section")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${recentSearches.size}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = onClearSearchHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("btn_clear_search_history")
                    ) {
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentSearches, key = { it.id }) { item ->
                        val isCurrentQuery = searchQuery.equals(item.query, ignoreCase = true)
                        InputChip(
                            selected = isCurrentQuery,
                            onClick = {
                                searchQuery = item.query
                                if (item.searchType == "TEACHER") {
                                    credentialTab = 0
                                } else if (item.searchType == "STUDENT") {
                                    credentialTab = 1
                                }
                                onSaveSearchQuery(item.query, item.searchType)
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.query,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (item.searchType in listOf("TEACHER", "STUDENT")) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (item.searchType == "TEACHER") "• Teachers" else "• Students",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = if (isCurrentQuery) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteSearchHistory(item.id) },
                                    modifier = Modifier
                                        .size(18.dp)
                                        .testTag("btn_delete_search_history_${item.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete search history item",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("search_history_chip_${item.id}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = credentialTab == 0,
                onClick = { credentialTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text("Schools (${filteredSchools.size})")
            }
            SegmentedButton(
                selected = credentialTab == 1,
                onClick = { credentialTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text("Teachers (${filteredTeachers.size})")
            }
            SegmentedButton(
                selected = credentialTab == 2,
                onClick = { credentialTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text("Students (${filteredStudents.size})")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (credentialTab == 0) {
                if (filteredSchools.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No schools found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (searchQuery.isNotBlank()) {
                                    Text("No results matching \"$searchQuery\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    }
                }
                items(filteredSchools, key = { it.id }) { school ->
                    val teacherCountForSchool = teachers.count { it.schoolId == school.id || it.schoolName.equals(school.name, ignoreCase = true) }
                    val studentCountForSchool = students.count { it.schoolId == school.id || it.schoolName.equals(school.name, ignoreCase = true) }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("credential_school_${school.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = school.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "School ID: ${school.id}  •  Reg: ${school.registrationId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        text = school.registrationId,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Campus: ${school.address}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "👨‍🏫 Teachers: $teacherCountForSchool",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "🎓 Students: $studentCountForSchool",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { schoolToDelete = school },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_delete_school_${school.id}"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove School / স্কুল মুছুন", style = MaterialTheme.typography.labelMedium)
                            }

                            Text(
                                text = "Admin ID: $adminId Authorized",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else if (credentialTab == 1) {
                if (filteredTeachers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No teachers found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (searchQuery.isNotBlank()) {
                                    Text("No results matching \"$searchQuery\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    }
                }
                items(filteredTeachers) { teacher ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("credential_teacher_${teacher.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(teacher.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        text = "ID: ${teacher.id}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("School: ${teacher.schoolName} (${teacher.schoolId})", style = MaterialTheme.typography.bodySmall)
                            Text("Email: ${teacher.email}  |  Mobile: ${teacher.mobile}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                                Text(
                                    text = "Password: ${teacher.password}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { teacherToEdit = teacher },
                                    modifier = Modifier.weight(1f).testTag("btn_edit_teacher_${teacher.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit ID", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = { teacherToDelete = teacher },
                                    modifier = Modifier.weight(1f).testTag("btn_delete_teacher_${teacher.id}"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove ID", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = "Admin ID: $adminId Authorized",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                if (filteredStudents.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No students found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (searchQuery.isNotBlank()) {
                                    Text("No results matching \"$searchQuery\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    }
                }
                items(filteredStudents) { student ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("credential_student_${student.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        text = "ID: ${student.id}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Class ${student.className}-${student.section} • Roll #${student.rollNumber} • ${student.schoolName}", style = MaterialTheme.typography.bodySmall)
                            if (student.dateOfBirth.isNotBlank()) {
                                Text("DOB: ${student.dateOfBirth}  |  Gender: ${student.gender}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Parent Mobile: ${student.parentMobile}  |  Blood: ${student.bloodGroup}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                                Text(
                                    text = "Password: ${student.password}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (teacherToEdit != null) {
            EditTeacherIdDialog(
                teacher = teacherToEdit!!,
                adminId = adminId,
                onDismiss = { teacherToEdit = null },
                onSave = { updatedTeacher ->
                    onUpdateTeacher(teacherToEdit!!.id, updatedTeacher)
                    teacherToEdit = null
                }
            )
        }

        if (teacherToDelete != null) {
            ConfirmDeleteTeacherDialog(
                teacher = teacherToDelete!!,
                adminId = adminId,
                onDismiss = { teacherToDelete = null },
                onConfirm = {
                    onDeleteTeacher(teacherToDelete!!)
                    teacherToDelete = null
                }
            )
        }

        if (schoolToDelete != null) {
            ConfirmDeleteSchoolDialog(
                school = schoolToDelete!!,
                adminId = adminId,
                onDismiss = { schoolToDelete = null },
                onConfirm = {
                    val target = schoolToDelete
                    schoolToDelete = null
                    if (target != null) {
                        onDeleteSchool(target)
                    }
                }
            )
        }
    }
}

@Composable
fun AdminAttendanceSummaryView(
    attendanceRecords: List<AttendanceEntity>,
    students: List<StudentEntity>,
    onOpenScanner: () -> Unit
) {
    val totalEntries = attendanceRecords.count { it.type == "ENTRY" }
    val totalExits = attendanceRecords.count { it.type == "EXIT" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("admin_attendance_cta"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Master Barcode Attendance Scanner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan student digital ID cards using camera to record school entry and exit.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenScanner,
                        modifier = Modifier.fillMaxWidth().testTag("btn_admin_open_scanner")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Barcode Scanner (Camera)")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$totalEntries", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Text("Total Entries", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$totalExits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        Text("Total Exits", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item {
            Text(
                text = "Live Attendance Activity Log (${attendanceRecords.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(attendanceRecords) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(record.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Class ${record.className}-${record.section} • ID: ${record.studentId}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (record.type == "ENTRY") Color(0xFF16A34A) else Color(0xFFDC2626)
                        ) {
                            Text(
                                text = record.type,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Text("${record.dateString} ${record.timeString}", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStoreManagementView(
    products: List<ProductEntity>,
    onAddProduct: () -> Unit,
    onToggleStock: (ProductEntity) -> Unit,
    onUpdateProduct: (ProductEntity) -> Unit = {},
    onDelete: (ProductEntity) -> Unit
) {
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Online Store Inventory", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Add items, update pricing & stock levels, external store links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAddProduct, modifier = Modifier.testTag("btn_admin_add_product_top")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Item")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (products.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No products in online store. Tap '+ Add Item' to upload products.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(products) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("admin_product_${product.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (product.imageUrl.isNotBlank()) {
                                    AsyncImage(model = product.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "₹${String.format("%.2f", product.price)} • ${product.category}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (product.inStock) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = if (product.inStock) "In Stock" else "Out of Stock",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = if (product.inStock) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text("For: ${product.targetAudience}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { productToEdit = product },
                                        modifier = Modifier.testTag("btn_edit_product_${product.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Stock & Price", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { onDelete(product) },
                                        modifier = Modifier.testTag("btn_delete_product_${product.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Switch(
                                    checked = product.inStock,
                                    onCheckedChange = { onToggleStock(product) },
                                    modifier = Modifier.testTag("switch_stock_${product.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (productToEdit != null) {
            EditStoreItemDialog(
                product = productToEdit!!,
                onDismiss = { productToEdit = null },
                onSave = { updated ->
                    onUpdateProduct(updated)
                    productToEdit = null
                }
            )
        }
    }
}

@Composable
fun RegisterSchoolDialog(
    onDismiss: () -> Unit,
    onRegister: (
        schoolName: String,
        address: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPass: String,
        assignedClass: String
    ) -> Unit
) {
    var schoolName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var teacherEmail by remember { mutableStateOf("") }
    var teacherMobile by remember { mutableStateOf("") }
    var teacherPass by remember { mutableStateOf("teacher123") }
    var assignedClass by remember { mutableStateOf("Class 10") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Register School & Head Teacher", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Auto-generates School ID, Registration ID, and Teacher ID.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("School Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_school_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("School Address / Campus") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_school_address"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Head / Lead Teacher Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Teacher Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherEmail,
                    onValueChange = { teacherEmail = it },
                    label = { Text("Teacher Email") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_email"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherMobile,
                    onValueChange = { teacherMobile = it },
                    label = { Text("Teacher Mobile") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_mobile"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherPass,
                    onValueChange = { teacherPass = it },
                    label = { Text("Teacher Password *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_pass"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = assignedClass,
                    onValueChange = { assignedClass = it },
                    label = { Text("Assigned Class / Grade") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_class"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (schoolName.isNotBlank() && teacherName.isNotBlank() && teacherPass.isNotBlank()) {
                                onRegister(schoolName, address, teacherName, teacherEmail, teacherMobile, teacherPass, assignedClass)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_submit_reg_school"),
                        enabled = schoolName.isNotBlank() && teacherName.isNotBlank() && teacherPass.isNotBlank()
                    ) {
                        Text("Register")
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterTeacherDialog(
    schools: List<SchoolEntity>,
    onDismiss: () -> Unit,
    onRegister: (
        schoolId: String,
        schoolName: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPass: String,
        assignedClass: String,
        subject: String
    ) -> Unit
) {
    var selectedSchoolId by remember { mutableStateOf(schools.firstOrNull()?.id ?: "") }
    var manualSchoolName by remember { mutableStateOf(schools.firstOrNull()?.name ?: "") }
    var teacherName by remember { mutableStateOf("") }
    var teacherEmail by remember { mutableStateOf("") }
    var teacherMobile by remember { mutableStateOf("") }
    var teacherPass by remember { mutableStateOf("teacher123") }
    var assignedClass by remember { mutableStateOf("Class 10") }
    var subject by remember { mutableStateOf("General") }
    var schoolDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Register Teacher / নতুন শিক্ষক", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Add and assign a faculty member to a school.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                if (schools.isNotEmpty()) {
                    Text("Select School / ক্যাম্পাস:", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { schoolDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (manualSchoolName.isNotBlank()) manualSchoolName else "Choose School")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = schoolDropdownExpanded,
                            onDismissRequest = { schoolDropdownExpanded = false }
                        ) {
                            schools.forEach { sch ->
                                DropdownMenuItem(
                                    text = { Text("${sch.name} (${sch.id})") },
                                    onClick = {
                                        selectedSchoolId = sch.id
                                        manualSchoolName = sch.name
                                        schoolDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    OutlinedTextField(
                        value = manualSchoolName,
                        onValueChange = { manualSchoolName = it },
                        label = { Text("School Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Teacher Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_only_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherMobile,
                    onValueChange = { teacherMobile = it },
                    label = { Text("Mobile Number * (Teacher Login ID)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_only_mobile"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherEmail,
                    onValueChange = { teacherEmail = it },
                    label = { Text("Teacher Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacherPass,
                    onValueChange = { teacherPass = it },
                    label = { Text("Teacher Password *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_reg_teacher_only_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = assignedClass,
                        onValueChange = { assignedClass = it },
                        label = { Text("Class") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (teacherName.isBlank() || teacherMobile.isBlank() || teacherPass.isBlank()) {
                                errorMessage = "নাম, মোবাইল এবং পাসওয়ার্ড আবশ্যক"
                                return@Button
                            }
                            val effectiveId = selectedSchoolId.ifBlank { "SCH-${UUID.randomUUID().toString().take(6).uppercase()}" }
                            val effectiveName = manualSchoolName.ifBlank { "School Campus" }
                            onRegister(effectiveId, effectiveName, teacherName.trim(), teacherEmail.trim(), teacherMobile.trim(), teacherPass.trim(), assignedClass.trim(), subject.trim())
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_confirm_register_teacher_only")
                    ) {
                        Text("Register Teacher")
                    }
                }
            }
        }
    }
}

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        imageUrl: String,
        purchaseUrl: String,
        price: Double,
        category: String,
        targetAudience: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var purchaseUrl by remember { mutableStateOf("https://amazon.com") }
    var priceInput by remember { mutableStateOf("19.99") }
    var category by remember { mutableStateOf("Books") }
    var targetAudience by remember { mutableStateOf("ALL") }

    val categories = listOf("Books", "Uniform", "Stationery", "Electronics", "General")
    val audiences = listOf("ALL", "STUDENT", "TEACHER")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Upload Store Product", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("Students & Teachers can view and click link to buy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Product Title *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_product_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().testTag("input_product_desc"),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text("Price (₹) *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_product_price"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = purchaseUrl,
                    onValueChange = { purchaseUrl = it },
                    label = { Text("Purchase / Store Link (URL) *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_product_url"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Product Image URL") },
                    placeholder = { Text("https://images.unsplash.com/...") },
                    modifier = Modifier.fillMaxWidth().testTag("input_product_image_url"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Category:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(3).forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Audience Visibility:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    audiences.forEach { aud ->
                        FilterChip(
                            selected = targetAudience == aud,
                            onClick = { targetAudience = aud },
                            label = { Text(aud) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val price = priceInput.toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && purchaseUrl.isNotBlank()) {
                                onSave(title, description, imageUrl, purchaseUrl, price, category, targetAudience)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_save_product"),
                        enabled = title.isNotBlank() && purchaseUrl.isNotBlank()
                    ) {
                        Text("Upload")
                    }
                }
            }
        }
    }
}

@Composable
fun AppBrandingDialog(
    currentSettings: AppSettingsEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, logo: String) -> Unit
) {
    var appName by remember { mutableStateOf(currentSettings?.appName ?: "School Portal") }
    var logoUrl by remember { mutableStateOf(currentSettings?.logoUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Branding & Customization") },
        text = {
            Column {
                Text("Change the application name and brand logo displayed at the top of the portal.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Title / School Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_branding_app_name"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Custom Logo Image URL") },
                    placeholder = { Text("https://example.com/logo.png") },
                    modifier = Modifier.fillMaxWidth().testTag("input_branding_logo_url"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(appName, logoUrl) },
                modifier = Modifier.testTag("btn_save_branding")
            ) {
                Text("Save Branding")
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
fun EditStoreItemDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (updated: ProductEntity) -> Unit
) {
    var title by remember { mutableStateOf(product.title) }
    var priceText by remember { mutableStateOf(String.format("%.2f", product.price)) }
    var inStock by remember { mutableStateOf(product.inStock) }
    var category by remember { mutableStateOf(product.category) }
    var targetAudience by remember { mutableStateOf(product.targetAudience) }
    var imageUrl by remember { mutableStateOf(product.imageUrl) }
    var purchaseUrl by remember { mutableStateOf(product.purchaseUrl) }
    var description by remember { mutableStateOf(product.description) }

    val categories = listOf("Books", "Uniform", "Stationery", "Electronics", "General")
    val audiences = listOf("ALL", "STUDENT", "TEACHER")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Item Pricing & Stock")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Update item details, inventory stock availability, and retail price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Product Title") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_product_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price (₹)") },
                    prefix = { Text("₹") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_product_price"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stock Level Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Stock Availability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (inStock) "Item is In Stock & Available" else "Item is Out of Stock",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (inStock) Color(0xFF15803D) else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = inStock,
                        onCheckedChange = { inStock = it },
                        modifier = Modifier.testTag("switch_edit_product_stock")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Image URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = purchaseUrl,
                    onValueChange = { purchaseUrl = it },
                    label = { Text("Store / Purchase Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPrice = priceText.toDoubleOrNull() ?: product.price
                    onSave(
                        product.copy(
                            title = title.ifBlank { product.title },
                            price = parsedPrice,
                            inStock = inStock,
                            category = category,
                            targetAudience = targetAudience,
                            imageUrl = imageUrl,
                            purchaseUrl = purchaseUrl,
                            description = description
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_edit_product")
            ) {
                Text("Save Changes")
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
fun EditTeacherIdDialog(
    teacher: TeacherEntity,
    adminId: String,
    onDismiss: () -> Unit,
    onSave: (TeacherEntity) -> Unit
) {
    var teacherId by remember { mutableStateOf(teacher.id) }
    var teacherName by remember { mutableStateOf(teacher.name) }
    var teacherMobile by remember { mutableStateOf(teacher.mobile) }
    var teacherEmail by remember { mutableStateOf(teacher.email) }
    var teacherPassword by remember { mutableStateOf(teacher.password) }
    var assignedClass by remember { mutableStateOf(teacher.assignedClass) }
    var subject by remember { mutableStateOf(teacher.subject) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_edit_teacher_id"),
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Edit Teacher ID",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Edit Teacher ID & Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Authorized under Admin: $adminId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "School: ${teacher.schoolName} (${teacher.schoolId})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Teacher ID field
                OutlinedTextField(
                    value = teacherId,
                    onValueChange = {
                        teacherId = it
                        errorMessage = null
                    },
                    label = { Text("Teacher ID (e.g. TCH-501)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_teacher_id")
                )

                // Teacher Name field
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = {
                        teacherName = it
                        errorMessage = null
                    },
                    label = { Text("Teacher Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_teacher_name")
                )

                // Mobile
                OutlinedTextField(
                    value = teacherMobile,
                    onValueChange = {
                        teacherMobile = it
                        errorMessage = null
                    },
                    label = { Text("Mobile Number (Login ID)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_teacher_mobile")
                )

                // Email
                OutlinedTextField(
                    value = teacherEmail,
                    onValueChange = { teacherEmail = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_teacher_email")
                )

                // Password
                OutlinedTextField(
                    value = teacherPassword,
                    onValueChange = {
                        teacherPassword = it
                        errorMessage = null
                    },
                    label = { Text("Login Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_teacher_password")
                )

                // Assigned Class
                OutlinedTextField(
                    value = assignedClass,
                    onValueChange = { assignedClass = it },
                    label = { Text("Assigned Class") },
                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Subject
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanId = teacherId.trim()
                    val cleanName = teacherName.trim()
                    val cleanMobile = teacherMobile.trim()
                    val cleanPass = teacherPassword.trim()

                    if (cleanId.isBlank()) {
                        errorMessage = "Teacher ID cannot be empty"
                        return@Button
                    }
                    if (cleanName.isBlank()) {
                        errorMessage = "Teacher name cannot be empty"
                        return@Button
                    }
                    if (cleanMobile.isBlank()) {
                        errorMessage = "Mobile number cannot be empty"
                        return@Button
                    }
                    if (cleanPass.isBlank()) {
                        errorMessage = "Password cannot be empty"
                        return@Button
                    }

                    val updated = teacher.copy(
                        id = cleanId,
                        name = cleanName,
                        mobile = cleanMobile,
                        email = teacherEmail.trim(),
                        password = cleanPass,
                        assignedClass = assignedClass.trim().ifBlank { "Class 10-A" },
                        subject = subject.trim().ifBlank { "General" }
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("btn_save_edit_teacher")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_edit_teacher")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmDeleteTeacherDialog(
    teacher: TeacherEntity,
    adminId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Remove Teacher ID") },
        text = {
            Column {
                Text("Are you sure you want to remove Teacher ID '${teacher.id}' (${teacher.name}) from ${teacher.schoolName}?")
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Authorized by Admin ID: $adminId\nAction is permanent and deletes teacher credentials.",
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
                modifier = Modifier.testTag("btn_confirm_remove_teacher_${teacher.id}")
            ) {
                Text("Remove Teacher ID")
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
fun ConfirmDeleteSchoolDialog(
    school: SchoolEntity,
    adminId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "স্কুল মুছে ফেলতে নিশ্চিত করুন? / Remove School",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = "আপনি কি নিশ্চিত যে আপনি '${school.name}' (School ID: ${school.id}) স্কুলটি সম্পূর্ণ মুছে ফেলতে চান?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "⚠ গুরুত্বপূর্ণ সতর্কতা:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "এই স্কুলটি মুছে ফেলা হলে Teacher বা Student লগইনের 'Choose your school from registered list' ড্রপডাউন ও তালিকা থেকে এই স্কুলের নাম স্থায়ীভাবে মুছে যাবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Authorized by Admin ID: $adminId",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("btn_confirm_remove_school_${school.id}")
            ) {
                Text("মুছে ফেলুন (Remove School)")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_remove_school")
            ) {
                Text("বাতিল (Cancel)")
            }
        }
    )
}
