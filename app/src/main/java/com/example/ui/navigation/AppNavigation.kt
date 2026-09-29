package com.example.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.service.NotificationHelper
import com.example.ui.screens.*
import com.example.ui.viewmodel.SchoolViewModel

object AppDestinations {
    const val HOME = "home"
    const val TEACHER_LOGIN = "teacher_login"
    const val STUDENT_LOGIN = "student_login"
    const val ADMIN_LOGIN = "admin_login"
    const val TEACHER_DASHBOARD = "teacher_dashboard"
    const val STUDENT_DASHBOARD = "student_dashboard"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val SHOPPING = "shopping"
    const val ATTENDANCE_SCANNER = "attendance_scanner"
    const val NOTIFICATION_HISTORY = "notification_history"
}

@Composable
fun AppNavigation(
    viewModel: SchoolViewModel,
    snackbarHostState: SnackbarHostState,
    navController: NavHostController = rememberNavController(),
    initialDeepLinkType: String? = null,
    initialDeepLinkId: String? = null,
    onClearDeepLink: () -> Unit = {}
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val allSchools by viewModel.allSchools.collectAsStateWithLifecycle()
    val allTeachers by viewModel.allTeachers.collectAsStateWithLifecycle()
    val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val allNotifications by viewModel.allNotificationsHistory.collectAsStateWithLifecycle()

    // Student specific states
    val currentStudent by viewModel.currentStudent.collectAsStateWithLifecycle()
    val studentAttendance by viewModel.studentAttendance.collectAsStateWithLifecycle()
    val studentExamResults by viewModel.studentExamResults.collectAsStateWithLifecycle()
    val studentNotifications by viewModel.studentNotifications.collectAsStateWithLifecycle()

    // Teacher specific states
    val teacherStudents by viewModel.teacherSchoolStudents.collectAsStateWithLifecycle()
    val teacherAttendance by viewModel.teacherSchoolAttendance.collectAsStateWithLifecycle()
    val teacherNotifications by viewModel.teacherSchoolNotifications.collectAsStateWithLifecycle()

    // Cloud Persistent Storage synchronization state
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    // Listen to user feedback messages and display in snackbar
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Handle FCM Deep Linking from Notification clicks
    LaunchedEffect(initialDeepLinkType, initialDeepLinkId) {
        if (!initialDeepLinkType.isNullOrBlank()) {
            when (initialDeepLinkType) {
                NotificationHelper.DEEP_LINK_EXAM_RESULT -> {
                    if (session.role != "NONE" && session.userId.isNotBlank()) {
                        if (session.role == "STUDENT") {
                            navController.navigate(AppDestinations.STUDENT_DASHBOARD)
                        } else if (session.role == "TEACHER") {
                            navController.navigate(AppDestinations.TEACHER_DASHBOARD)
                        }
                    } else {
                        navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                    }
                }
                NotificationHelper.DEEP_LINK_NOTICE,
                NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> {
                    navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                }
                else -> {
                    navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                }
            }
            onClearDeepLink()
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME
    ) {
        // Home Screen
        composable(AppDestinations.HOME) {
            HomeScreen(
                appSettings = appSettings,
                currentSession = session,
                onNavigateToTeacherLogin = {
                    navController.navigate(AppDestinations.TEACHER_LOGIN)
                },
                onNavigateToStudentLogin = {
                    navController.navigate(AppDestinations.STUDENT_LOGIN)
                },
                onNavigateToAdminLogin = {
                    navController.navigate(AppDestinations.ADMIN_LOGIN)
                },
                onNavigateToDashboard = { role ->
                    when (role) {
                        "TEACHER" -> navController.navigate(AppDestinations.TEACHER_DASHBOARD)
                        "STUDENT" -> navController.navigate(AppDestinations.STUDENT_DASHBOARD)
                        "ADMIN" -> navController.navigate(AppDestinations.ADMIN_DASHBOARD)
                    }
                },
                onLogout = {
                    viewModel.logout()
                },
                onNavigateToScanner = {
                    navController.navigate(AppDestinations.ATTENDANCE_SCANNER)
                }
            )
        }

        // Teacher Login Screen
        composable(AppDestinations.TEACHER_LOGIN) {
            TeacherLoginScreen(
                availableSchools = allSchools,
                availableTeachers = allTeachers,
                onLoginSuccess = {
                    navController.navigate(AppDestinations.TEACHER_DASHBOARD) {
                        popUpTo(AppDestinations.HOME)
                    }
                },
                onBackClick = { navController.popBackStack() },
                onLoginAction = { school, teacherId, pass ->
                    viewModel.loginTeacher(school, teacherId, pass)
                }
            )
        }

        // Student Login Screen
        composable(AppDestinations.STUDENT_LOGIN) {
            StudentLoginScreen(
                availableSchools = allSchools,
                onLoginSuccess = {
                    navController.navigate(AppDestinations.STUDENT_DASHBOARD) {
                        popUpTo(AppDestinations.HOME)
                    }
                },
                onBackClick = { navController.popBackStack() },
                onLoginByClassRoll = { school, cls, sec, roll, pass ->
                    viewModel.loginStudent(school, cls, sec, roll, pass)
                },
                onLoginByBarcodeId = { barcodeId, pass ->
                    viewModel.loginStudentByBarcode(barcodeId, pass)
                }
            )
        }

        // Admin Login Screen
        composable(AppDestinations.ADMIN_LOGIN) {
            AdminLoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestinations.ADMIN_DASHBOARD) {
                        popUpTo(AppDestinations.HOME)
                    }
                },
                onBackClick = { navController.popBackStack() },
                onAdminLoginAction = { id, pass ->
                    viewModel.loginAdmin(id, pass)
                }
            )
        }

        // Teacher Dashboard Screen
        composable(AppDestinations.TEACHER_DASHBOARD) {
            val effectiveTeacherStudents = if (session.schoolId.isNotBlank()) {
                val filtered = allStudents.filter { it.schoolId == session.schoolId }
                if (teacherStudents.isNotEmpty()) teacherStudents else filtered
            } else {
                teacherStudents.ifEmpty { allStudents }
            }

            val effectiveTeacherAttendance = if (session.schoolId.isNotBlank()) {
                teacherAttendance.filter { it.schoolId == session.schoolId }
            } else {
                teacherAttendance
            }

            TeacherDashboardScreen(
                currentSession = session,
                students = effectiveTeacherStudents,
                attendanceRecords = effectiveTeacherAttendance,
                notifications = teacherNotifications,
                syncState = syncState,
                onSyncRoomToFirestore = { viewModel.syncRoomToFirestore() },
                onSyncFirestoreToRoom = { viewModel.syncFirestoreToRoom() },
                onSeedFirebaseData = { viewModel.seedFirebaseData() },
                onClearDemoAccounts = { viewModel.purgeAllDemoAccounts() },
                onRecordAttendance = { studentId, type ->
                    viewModel.recordAttendance(studentId, type)
                },
                onSaveStudent = { student ->
                    viewModel.saveStudent(student)
                },
                onUpdateStudentCredentials = { studentId, newPhone, newPass ->
                    viewModel.updateStudentPasswordAndPhone(studentId, newPhone, newPass)
                },
                onUpdateStudentPhoto = { studentId, photoUri ->
                    viewModel.updateStudentPhoto(studentId, photoUri, session.userId)
                },
                onSaveExamResult = { result ->
                    viewModel.saveExamResult(result)
                },
                onSendNotification = { type, stId, cls, sec, title, msg, img, isPay, amt, sName, sId ->
                    viewModel.sendNotification(type, stId, cls, sec, title, msg, img, isPay, amt, sName, sId)
                },
                onChangeTeacherPassword = { newPass ->
                    viewModel.changeTeacherPassword(newPass)
                },
                onEditStudentProfile = { oldId, updatedStudent ->
                    viewModel.editStudentProfile(oldId, updatedStudent, session.userId)
                },
                onRemoveStudent = { student ->
                    viewModel.deleteStudentByTeacher(student, session.userId)
                },
                onUpdateTeacherProfile = { updatedTeacher ->
                    viewModel.updateTeacherProfile(updatedTeacher)
                },
                onDeleteTeacherProfile = {
                    viewModel.deleteTeacherSelf(session.userId) {
                        navController.navigate(AppDestinations.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                teacherProfile = allTeachers.find { it.id == session.userId },
                onOpenShopping = {
                    navController.navigate(AppDestinations.SHOPPING)
                },
                onOpenScannerScreen = {
                    navController.navigate(AppDestinations.ATTENDANCE_SCANNER)
                },
                onOpenNotificationHistory = {
                    navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Student Dashboard Screen
        composable(AppDestinations.STUDENT_DASHBOARD) {
            StudentDashboardScreen(
                student = currentStudent,
                attendanceList = studentAttendance,
                examResults = studentExamResults,
                notifications = studentNotifications,
                onOpenShopping = {
                    navController.navigate(AppDestinations.SHOPPING)
                },
                onOpenNotificationHistory = {
                    navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                },
                onMarkNotificationRead = { id, docId ->
                    viewModel.markNotificationAsRead(id, docId)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Admin Dashboard Screen
        composable(AppDestinations.ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                schools = allSchools,
                teachers = allTeachers,
                students = allStudents,
                attendanceRecords = teacherAttendance.ifEmpty { allSchools.flatMap { listOf() } },
                products = allProducts,
                appSettings = appSettings,
                recentSearches = recentSearches,
                adminId = if (session.userId.isNotBlank()) session.userId else "ADM-001",
                syncState = syncState,
                onSyncRoomToFirestore = { viewModel.syncRoomToFirestore() },
                onSyncFirestoreToRoom = { viewModel.syncFirestoreToRoom() },
                onSeedFirebaseData = { viewModel.seedFirebaseData() },
                onInitializeFirestoreSchema = { viewModel.initializeFirestoreSchema() },
                onClearDemoAccounts = { viewModel.purgeAllDemoAccounts() },
                onDeleteTeacher = { teacher ->
                    viewModel.deleteTeacherByAdmin(teacher, if (session.userId.isNotBlank()) session.userId else "ADM-001")
                },
                onDeleteSchool = { school ->
                    viewModel.deleteSchoolByAdmin(school, if (session.userId.isNotBlank()) session.userId else "ADM-001")
                },
                onUpdateTeacher = { origId, updatedTeacher ->
                    viewModel.updateTeacherByAdmin(origId, updatedTeacher, if (session.userId.isNotBlank()) session.userId else "ADM-001")
                },
                onRegisterTeacherOnly = { sId, sName, tName, tEmail, tMob, tPass, cls, subj ->
                    viewModel.registerTeacherOnly(sId, sName, tName, tEmail, tMob, tPass, cls, subj)
                },
                onRegisterSchoolAndTeacher = { sName, addr, tName, tEmail, tMob, tPass, cls ->
                    viewModel.createSchoolAndTeacher(sName, addr, tName, tEmail, tMob, tPass, cls)
                },
                onAddProduct = { title, desc, img, url, price, cat, aud ->
                    viewModel.addProduct(title, desc, img, url, price, cat, aud)
                },
                onUpdateProduct = { prod ->
                    viewModel.updateProduct(prod)
                },
                onDeleteProduct = { prod ->
                    viewModel.deleteProduct(prod)
                },
                onUpdateAppSettings = { name, logo ->
                    viewModel.updateAppSettings(name, logo)
                },
                onRecordAttendance = { studentId, type ->
                    viewModel.recordAttendance(studentId, type)
                },
                onSaveSearchQuery = { query, type ->
                    viewModel.saveSearchQuery(query, type)
                },
                onDeleteSearchHistory = { id ->
                    viewModel.deleteSearchHistoryItem(id)
                },
                onClearSearchHistory = {
                    viewModel.clearAllSearchHistory()
                },
                onOpenScannerScreen = {
                    navController.navigate(AppDestinations.ATTENDANCE_SCANNER)
                },
                notifications = allNotifications,
                onSendNotification = { targetType, targetId, recipientUserId, title, body, deepLinkType, deepLinkId, isPaymentAlert, amountDue ->
                    viewModel.sendPushNotification(
                        targetType = targetType,
                        targetId = targetId,
                        recipientUserId = recipientUserId,
                        title = title,
                        body = body,
                        deepLinkType = deepLinkType,
                        deepLinkId = deepLinkId,
                        isPaymentAlert = isPaymentAlert,
                        amountDue = amountDue
                    )
                },
                onOpenNotificationHistory = {
                    navController.navigate(AppDestinations.NOTIFICATION_HISTORY)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(AppDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Online Shopping Catalog Screen
        composable(AppDestinations.SHOPPING) {
            ShoppingScreen(
                userRole = if (session.role.isNotBlank() && session.role != "NONE") session.role else "STUDENT",
                products = allProducts,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Dedicated CameraX & ZXing Attendance Barcode Scanner Screen
        composable(AppDestinations.ATTENDANCE_SCANNER) {
            AttendanceScannerScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Notification History Screen
        composable(AppDestinations.NOTIFICATION_HISTORY) {
            val displayNotifications = when (session.role) {
                "STUDENT" -> studentNotifications
                "TEACHER" -> teacherNotifications
                else -> allNotifications
            }

            NotificationHistoryScreen(
                notifications = displayNotifications,
                onBack = { navController.popBackStack() },
                onMarkAsRead = { id, notifId ->
                    viewModel.markNotificationAsRead(id, notifId)
                },
                onMarkAllAsRead = {
                    viewModel.markAllNotificationsAsRead()
                },
                onNavigateDeepLink = { deepLinkType, deepLinkId, notif ->
                    viewModel.markNotificationAsRead(notif.id, notif.notificationId)
                    when (deepLinkType) {
                        NotificationHelper.DEEP_LINK_EXAM_RESULT -> {
                            if (session.role == "STUDENT") {
                                navController.navigate(AppDestinations.STUDENT_DASHBOARD)
                            } else if (session.role == "TEACHER") {
                                navController.navigate(AppDestinations.TEACHER_DASHBOARD)
                            }
                        }
                        NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> {
                            if (session.role == "STUDENT") {
                                navController.navigate(AppDestinations.STUDENT_DASHBOARD)
                            }
                        }
                    }
                }
            )
        }
    }
}
