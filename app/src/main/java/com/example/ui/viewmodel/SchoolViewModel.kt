package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthResult
import com.example.data.auth.FirebaseAuthService
import com.example.data.firestore.FirestoreRepository
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.SchoolRepository
import com.example.data.session.SessionManager
import com.example.data.session.UserSessionState
import com.example.data.sync.FirestoreSyncService
import com.example.data.sync.SyncResult
import com.example.data.sync.SyncState
import com.example.service.FCMManager
import com.example.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AttendanceScanResult(
    val success: Boolean,
    val student: StudentEntity? = null,
    val record: AttendanceEntity? = null,
    val isFirestoreSynced: Boolean = false,
    val message: String
)

class SchoolViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = SchoolRepository(database)
    val sessionManager = SessionManager(application)

    // Firebase Authentication Service (Admin, Teacher, Student)
    val firebaseAuthService = FirebaseAuthService(application, database)

    // Direct Real-time Firestore Cloud Repository
    val firestoreRepository = FirestoreRepository(application, database)

    // Room-to-Firestore Data Synchronization Service
    val firestoreSyncService = FirestoreSyncService(application, database)
    val syncState: StateFlow<SyncState> = firestoreSyncService.syncState

    val session: StateFlow<UserSessionState> = sessionManager.sessionFlow

    val appSettings: StateFlow<AppSettingsEntity?> = repository.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Admin & General: Real-time schools from Firestore (with Room fallback)
    val allSchools: StateFlow<List<SchoolEntity>> = firestoreRepository.getAllSchoolsRealtime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTeachers: StateFlow<List<TeacherEntity>> = repository.allTeachers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<SearchHistoryEntity>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schoolCount: StateFlow<Int> = repository.schoolCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val teacherCount: StateFlow<Int> = repository.teacherCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val studentCount: StateFlow<Int> = repository.studentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Current Student Profile Flow - Strictly scoped to student's own ID
    val currentStudent: StateFlow<StudentEntity?> = session.flatMapLatest { sess ->
        if (sess.role == "STUDENT" && sess.userId.isNotBlank()) {
            firestoreRepository.getStudentProfileRealtime(sess.userId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current Student Attendance History - Strictly scoped to student's own ID
    val studentAttendance: StateFlow<List<AttendanceEntity>> = session.flatMapLatest { sess ->
        if (sess.role == "STUDENT" && sess.userId.isNotBlank()) {
            firestoreRepository.getStudentAttendanceRealtime(sess.userId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Student Exam Results - Strictly scoped to student's own ID
    val studentExamResults: StateFlow<List<ExamResultEntity>> = session.flatMapLatest { sess ->
        if (sess.role == "STUDENT" && sess.userId.isNotBlank()) {
            firestoreRepository.getStudentExamResultsRealtime(sess.userId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Student Notifications - Strictly scoped to student's school and targeted to student/class (Deduplicated)
    val studentNotifications: StateFlow<List<NotificationEntity>> = session.flatMapLatest { sess ->
        if (sess.role == "STUDENT" && sess.userId.isNotBlank()) {
            firestoreRepository.getStudentNoticesRealtime(
                schoolId = sess.schoolId,
                studentId = sess.userId,
                className = sess.userClass,
                section = sess.userSection
            )
        } else {
            flowOf(emptyList())
        }
    }.map { list ->
        list.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Teacher's School Students - Strictly scoped to Teacher's School
    val teacherSchoolStudents: StateFlow<List<StudentEntity>> = session.flatMapLatest { sess ->
        if (sess.role == "TEACHER" && sess.schoolId.isNotBlank()) {
            firestoreRepository.getTeacherStudentsRealtime(sess.schoolId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Teacher's School Attendance Records - Strictly scoped to Teacher's School
    val teacherSchoolAttendance: StateFlow<List<AttendanceEntity>> = session.flatMapLatest { sess ->
        if (sess.schoolId.isNotBlank()) {
            firestoreRepository.getTeacherAttendanceRealtime(sess.schoolId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Teacher's School Notifications - Strictly scoped to Teacher's School (Deduplicated)
    val teacherSchoolNotifications: StateFlow<List<NotificationEntity>> = session.flatMapLatest { sess ->
        if (sess.schoolId.isNotBlank()) {
            firestoreRepository.getTeacherNotificationsRealtime(sess.schoolId)
        } else {
            flowOf(emptyList())
        }
    }.map { list ->
        list.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Teacher's School Exam Results - Strictly scoped to Teacher's School
    val teacherSchoolExamResults: StateFlow<List<ExamResultEntity>> = session.flatMapLatest { sess ->
        if (sess.schoolId.isNotBlank()) {
            firestoreRepository.getTeacherExamResultsRealtime(sess.schoolId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Complete Notification History (for Notification History screen, Deduplicated)
    val allNotificationsHistory: StateFlow<List<NotificationEntity>> = repository.allNotificationsFlow
        .map { list ->
            list.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unread Notifications Count (for Bell Badges)
    val unreadNotificationsCount: StateFlow<Int> = repository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Feedback message for snackbars/toasts
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        // Automatically purge any legacy demo accounts on launch so only manual real data remains
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.purgeAllDemoData()
                repository.deleteDuplicateNotifications()
            } catch (e: Exception) {
                Log.w("SchoolViewModel", "Auto purge demo error: ${e.message}")
            }

            // If session is already logged in, sync FCM device token
            val sess = session.value
            if (sess.role != "NONE" && sess.userId.isNotBlank()) {
                syncUserFcmToken(
                    userId = sess.userId,
                    role = sess.role,
                    schoolId = sess.schoolId,
                    className = sess.userClass
                )
            }
        }
    }

    fun purgeAllDemoAccounts(onComplete: (SyncResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.purgeAllDemoData()

                // Also delete known demo doc IDs from Firestore if reachable
                try {
                    val firestore = com.example.data.firebase.FirebaseConfig.getFirestore(getApplication())
                    if (firestore != null) {
                        listOf("SCH-1001", "SCH-1002").forEach { id ->
                            firestore.collection("schools").document(id).delete()
                        }
                        listOf("TCH-501", "TCH-502", "TCH-503", "TCH-504").forEach { id ->
                            firestore.collection("teachers").document(id).delete()
                        }
                        listOf("STU-10-A-01", "STU-10-A-02", "STU-10-B-01", "STU-10-B-02", "STU-10-A-03", "STU-10-A-04").forEach { id ->
                            firestore.collection("students").document(id).delete()
                        }
                    }
                } catch (fe: Exception) {
                    Log.w("SchoolViewModel", "Cloud demo cleanup note: ${fe.message}")
                }

                _userMessage.value = "সমস্ত ডেমো অ্যাকাউন্ট সফলভাবে মুছে ফেলা হয়েছে! এখন সম্পূর্ণ ম্যানুয়ালি নতুন ডেটা এন্ট্রি করতে পারবেন।"
                onComplete(SyncResult.Success("Demo accounts removed", 0))
            } catch (e: Exception) {
                _userMessage.value = "ডেমো অ্যাকাউন্ট রিমুভ সম্পন্ন হয়েছে।"
                onComplete(SyncResult.Success("Demo accounts removed", 0))
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // --- Authentication Actions using Firebase Auth ---

    suspend fun loginAdmin(adminId: String, pass: String): Boolean {
        return when (val result = firebaseAuthService.authenticateAdmin(adminId, pass)) {
            is AuthResult.Success -> {
                sessionManager.saveSession(result.session)
                _userMessage.value = "Welcome Admin ${result.session.userName}!"
                // Ensure Firestore has latest data
                viewModelScope.launch { firestoreSyncService.syncRoomToFirestore() }
                syncUserFcmToken(
                    userId = result.session.userId,
                    role = "ADMIN",
                    schoolId = result.session.schoolId
                )
                true
            }
            is AuthResult.Error -> {
                _userMessage.value = result.message
                false
            }
        }
    }

    suspend fun loginTeacher(schoolNameOrId: String, teacherIdOrName: String, pass: String): Boolean {
        return when (val result = firebaseAuthService.authenticateTeacher(schoolNameOrId, teacherIdOrName, pass)) {
            is AuthResult.Success -> {
                sessionManager.saveSession(result.session)
                _userMessage.value = "Welcome, ${result.session.userName}!"
                syncUserFcmToken(
                    userId = result.session.userId,
                    role = "TEACHER",
                    schoolId = result.session.schoolId
                )
                true
            }
            is AuthResult.Error -> {
                _userMessage.value = result.message
                false
            }
        }
    }

    suspend fun loginStudent(
        schoolQuery: String,
        className: String,
        section: String,
        rollNumber: String,
        pass: String
    ): Boolean {
        return when (val result = firebaseAuthService.authenticateStudentByRoll(schoolQuery, className, section, rollNumber, pass)) {
            is AuthResult.Success -> {
                sessionManager.saveSession(result.session)
                _userMessage.value = "Welcome back, ${result.session.userName}!"
                syncUserFcmToken(
                    userId = result.session.userId,
                    role = "STUDENT",
                    schoolId = result.session.schoolId,
                    className = result.session.userClass
                )
                true
            }
            is AuthResult.Error -> {
                _userMessage.value = result.message
                false
            }
        }
    }

    suspend fun loginStudentByBarcode(barcodeId: String, pass: String): Boolean {
        return when (val result = firebaseAuthService.authenticateStudentByBarcode(barcodeId, pass)) {
            is AuthResult.Success -> {
                sessionManager.saveSession(result.session)
                _userMessage.value = "Welcome back, ${result.session.userName}!"
                syncUserFcmToken(
                    userId = result.session.userId,
                    role = "STUDENT",
                    schoolId = result.session.schoolId,
                    className = result.session.userClass
                )
                true
            }
            is AuthResult.Error -> {
                _userMessage.value = result.message
                false
            }
        }
    }

    fun logout() {
        firebaseAuthService.signOut()
        sessionManager.clearSession()
        _userMessage.value = "Logged out successfully."
    }

    fun updateTeacherProfile(updatedTeacher: TeacherEntity) {
        viewModelScope.launch {
            repository.updateTeacher(updatedTeacher)
            firestoreSyncService.replicateTeacherToCloud(updatedTeacher)
            if (session.value.userId == updatedTeacher.id) {
                sessionManager.saveSession(
                    session.value.copy(
                        userName = updatedTeacher.name
                    )
                )
            }
            _userMessage.value = "Teacher profile for ID ${updatedTeacher.id} updated successfully."
        }
    }

    fun deleteTeacherSelf(teacherId: String, onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            val teacher = repository.getTeacherById(teacherId)
            if (teacher != null) {
                repository.deleteTeacher(teacher)
                firestoreSyncService.removeTeacherFromCloud(teacher.id)
                sessionManager.clearSession()
                _userMessage.value = "Tutender profile for Teacher ID $teacherId was deleted."
                onLoggedOut()
            }
        }
    }

    // --- Cloud Synchronization Operations ---

    fun seedFirebaseData(onComplete: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = firestoreSyncService.syncRoomToFirestore()
            _userMessage.value = "সমস্ত স্কুলের বাস্তব ডেটা ক্লাউড ফায়ারবেসে সফলভাবে ব্যাকআপ করা হয়েছে।"
            onComplete(result)
        }
    }

    fun initializeFirestoreSchema(onComplete: (com.example.data.firestore.SchemaInitResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = firestoreSyncService.initializeFirestoreSchema()
            if (result.success) {
                _userMessage.value = "Firestore Schema Initialized: ${result.studentProfilesCount} Student Profiles, ${result.attendanceLogsCount} Attendance Logs, ${result.reportCardsCount} Report Cards."
            } else {
                _userMessage.value = "Firestore Schema: ${result.message}"
            }
            onComplete(result)
        }
    }

    fun syncRoomToFirestore(onComplete: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = firestoreSyncService.syncRoomToFirestore()) {
                is SyncResult.Success -> {
                    _userMessage.value = "Cloud Backup: ${result.itemsCount} records saved to Firestore."
                    onComplete(result)
                }
                is SyncResult.Error -> {
                    _userMessage.value = "Cloud Sync Notice: ${result.message}"
                    onComplete(result)
                }
                SyncResult.Offline -> {
                    _userMessage.value = "Cloud Sync: Offline local mode active."
                    onComplete(result)
                }
            }
        }
    }

    fun syncFirestoreToRoom(onComplete: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            when (val result = firestoreSyncService.syncFirestoreToRoom()) {
                is SyncResult.Success -> {
                    _userMessage.value = "Cloud Restore: ${result.itemsCount} records restored to Room."
                    onComplete(result)
                }
                is SyncResult.Error -> {
                    _userMessage.value = "Cloud Restore Notice: ${result.message}"
                    onComplete(result)
                }
                SyncResult.Offline -> {
                    _userMessage.value = "Cloud Restore: Offline local mode active."
                    onComplete(result)
                }
            }
        }
    }

    suspend fun lookupStudentForScan(scannedCode: String): StudentEntity? {
        val trimmed = scannedCode.trim()
        val parsed = com.example.util.BarcodeUtil.parseStudentIdFromScan(trimmed)
        return repository.findStudentByIdOrBarcode(trimmed)
            ?: repository.findStudentByIdOrBarcode(parsed)
    }

    // --- Attendance Operations ---

    suspend fun logBarcodeAttendance(scannedCode: String, type: String): AttendanceScanResult {
        val currentSchoolId = session.value.schoolId
        val operatorId = session.value.userId.ifBlank { "TEACHER" }
        val student = lookupStudentForScan(scannedCode)
        if (student == null) {
            val msg = "Student not found for barcode: $scannedCode"
            _userMessage.value = msg
            return AttendanceScanResult(
                success = false,
                message = msg
            )
        }

        // Strict School Isolation (Multi-tenant School Architecture)
        if (session.value.role == "TEACHER" && currentSchoolId.isNotBlank()) {
            if (!student.schoolId.equals(currentSchoolId, ignoreCase = true)) {
                val msg = "Cross-school restriction: Student '${student.name}' belongs to '${student.schoolName}' (${student.schoolId}), not your school (${session.value.schoolName})."
                _userMessage.value = msg
                return AttendanceScanResult(
                    success = false,
                    student = student,
                    message = msg
                )
            }
        }

        val record = repository.recordAttendance(student.id, type, operatorId)
        if (record == null) {
            val msg = "Could not record attendance for ${student.name}"
            _userMessage.value = msg
            return AttendanceScanResult(
                success = false,
                student = student,
                message = msg
            )
        }

        val fsResult = firestoreRepository.recordAttendanceToFirestore(record)
        firestoreSyncService.replicateAttendanceToCloud(record)

        val firestoreSuccess = fsResult.isSuccess
        val statusMsg = if (firestoreSuccess) {
            "Logged $type for ${student.name} at ${record.timeString} — Synced to Google Firebase"
        } else {
            val err = fsResult.exceptionOrNull()?.localizedMessage ?: "Sync queued"
            "Logged $type for ${student.name} at ${record.timeString} (Saved locally, Firestore: $err)"
        }
        _userMessage.value = statusMsg
        return AttendanceScanResult(
            success = true,
            student = student,
            record = record,
            isFirestoreSynced = firestoreSuccess,
            message = statusMsg
        )
    }

    fun recordAttendance(studentId: String, type: String) {
        viewModelScope.launch {
            val operatorId = session.value.userId.ifBlank { "TEACHER" }
            val record = repository.recordAttendance(studentId, type, operatorId)
            if (record != null) {
                val fsResult = firestoreRepository.recordAttendanceToFirestore(record)
                firestoreSyncService.replicateAttendanceToCloud(record)
                if (fsResult.isSuccess) {
                    _userMessage.value = "Logged $type for ${record.studentName} at ${record.timeString} (Saved to Google Firebase)"
                } else {
                    val err = fsResult.exceptionOrNull()?.localizedMessage ?: "Sync queued"
                    _userMessage.value = "Logged $type for ${record.studentName} (Local saved, Firebase: $err)"
                }
            } else {
                _userMessage.value = "Student with ID $studentId not found."
            }
        }
    }

    // --- Teacher Student Operations ---

    fun saveStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.insertStudent(student)
            val fsResult = firestoreRepository.saveStudentToFirestore(student)
            firestoreSyncService.replicateStudentToCloud(student)
            if (fsResult.isSuccess) {
                _userMessage.value = "Student ${student.name} saved to Google Firebase!"
            } else {
                val err = fsResult.exceptionOrNull()?.localizedMessage ?: "Sync queued"
                _userMessage.value = "Student ${student.name} saved locally. Firebase: $err"
            }
        }
    }

    fun updateStudentPasswordAndPhone(studentId: String, newPhone: String, newPass: String) {
        viewModelScope.launch {
            val existing = repository.getStudentById(studentId)
            if (existing != null) {
                val updated = existing.copy(
                    studentMobile = newPhone,
                    password = newPass
                )
                repository.updateStudent(updated)
                val fsResult = firestoreRepository.saveStudentToFirestore(updated)
                firestoreSyncService.replicateStudentToCloud(updated)
                if (fsResult.isSuccess) {
                    _userMessage.value = "Updated mobile and password for ${existing.name} in Google Firebase."
                } else {
                    _userMessage.value = "Updated credentials locally for ${existing.name}."
                }
            }
        }
    }

    fun updateStudentPhoto(studentId: String, photoUri: String, teacherId: String? = null) {
        viewModelScope.launch {
            val existing = repository.getStudentById(studentId)
            if (existing != null) {
                val updated = existing.copy(photoUri = photoUri)
                repository.updateStudent(updated)
                val fsResult = firestoreRepository.saveStudentToFirestore(updated)
                firestoreSyncService.replicateStudentToCloud(updated)
                val teacherTag = if (!teacherId.isNullOrBlank()) " (Teacher: $teacherId)" else ""
                if (fsResult.isSuccess) {
                    _userMessage.value = "Photo updated for ${existing.name} in Google Firebase$teacherTag."
                } else {
                    _userMessage.value = "Photo updated locally for ${existing.name}$teacherTag."
                }
            }
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            val fsResult = firestoreRepository.deleteStudentFromFirestore(student.id)
            firestoreSyncService.removeStudentFromCloud(student.id)
            if (fsResult.isSuccess) {
                _userMessage.value = "Student ${student.name} removed from Google Firebase."
            } else {
                _userMessage.value = "Student ${student.name} removed locally."
            }
        }
    }

    fun deleteStudentByTeacher(student: StudentEntity, teacherId: String? = null) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            val fsResult = firestoreRepository.deleteStudentFromFirestore(student.id)
            firestoreSyncService.removeStudentFromCloud(student.id)
            val teacherTag = if (!teacherId.isNullOrBlank()) " by Teacher ID ($teacherId)" else ""
            if (fsResult.isSuccess) {
                _userMessage.value = "Student ID ${student.id} (${student.name}) removed from Google Firebase$teacherTag."
            } else {
                _userMessage.value = "Student ID ${student.id} (${student.name}) removed locally$teacherTag."
            }
        }
    }

    fun editStudentProfile(oldId: String, updatedStudent: StudentEntity, teacherId: String? = null) {
        viewModelScope.launch {
            if (oldId != updatedStudent.id) {
                val old = repository.getStudentById(oldId)
                if (old != null) {
                    repository.deleteStudent(old)
                }
                firestoreRepository.deleteStudentFromFirestore(oldId)
                firestoreSyncService.removeStudentFromCloud(oldId)
                repository.insertStudent(updatedStudent)
            } else {
                repository.updateStudent(updatedStudent)
            }
            val fsResult = firestoreRepository.saveStudentToFirestore(updatedStudent)
            firestoreSyncService.replicateStudentToCloud(updatedStudent)
            val teacherTag = if (!teacherId.isNullOrBlank()) " by Teacher ID ($teacherId)" else ""
            if (fsResult.isSuccess) {
                _userMessage.value = "Student ID ${updatedStudent.id} (${updatedStudent.name}) updated in Google Firebase$teacherTag."
            } else {
                val err = fsResult.exceptionOrNull()?.localizedMessage ?: "Sync queued"
                _userMessage.value = "Student updated locally. Firebase: $err"
            }
        }
    }

    fun deleteTeacherByAdmin(teacher: TeacherEntity, adminId: String? = null) {
        viewModelScope.launch {
            repository.deleteTeacher(teacher)
            val fsResult = firestoreRepository.deleteTeacherFromFirestore(teacher.id)
            firestoreSyncService.removeTeacherFromCloud(teacher.id)
            val adminTag = if (!adminId.isNullOrBlank()) " by Admin ID ($adminId)" else ""
            if (fsResult.isSuccess) {
                _userMessage.value = "Teacher ID ${teacher.id} (${teacher.name}) removed from Google Firebase$adminTag."
            } else {
                _userMessage.value = "Teacher ID ${teacher.id} (${teacher.name}) removed locally$adminTag."
            }
        }
    }

    fun updateTeacherByAdmin(originalTeacherId: String, updatedTeacher: TeacherEntity, adminId: String? = null) {
        viewModelScope.launch {
            if (originalTeacherId != updatedTeacher.id) {
                val oldTeacher = repository.getTeacherById(originalTeacherId)
                if (oldTeacher != null) {
                    repository.deleteTeacher(oldTeacher)
                    firestoreRepository.deleteTeacherFromFirestore(originalTeacherId)
                    firestoreSyncService.removeTeacherFromCloud(originalTeacherId)
                }
                repository.insertTeacher(updatedTeacher)
            } else {
                repository.updateTeacher(updatedTeacher)
            }
            val fsResult = firestoreRepository.saveTeacherToFirestore(updatedTeacher)
            firestoreSyncService.replicateTeacherToCloud(updatedTeacher)
            val adminTag = if (!adminId.isNullOrBlank()) " by Admin ($adminId)" else ""
            if (fsResult.isSuccess) {
                _userMessage.value = "Teacher ID ${updatedTeacher.id} (${updatedTeacher.name}) updated successfully$adminTag."
            } else {
                _userMessage.value = "Teacher ID ${updatedTeacher.id} updated locally. Firebase sync queued."
            }
        }
    }

    // --- Exam Results Operations ---

    fun saveExamResult(result: ExamResultEntity) {
        viewModelScope.launch {
            val insertedId = repository.insertExamResult(result)
            val toSave = if (result.id == 0L) result.copy(id = insertedId) else result
            val firestoreResult = firestoreRepository.saveExamResultToFirestore(toSave)
            firestoreSyncService.replicateExamResultToCloud(toSave)
            if (firestoreResult.isSuccess) {
                _userMessage.value = "Exam marks saved to Cloud Firestore for ${result.subject} (Score: ${result.marksObtained}/${result.maxMarks}, Grade: ${result.grade})."
            } else {
                val err = firestoreResult.exceptionOrNull()?.localizedMessage ?: "Network/Permission error"
                _userMessage.value = "Saved locally. Cloud Firestore sync status: $err"
            }
        }
    }

    // --- Notification Operations ---

    fun syncUserFcmToken(userId: String, role: String, schoolId: String, className: String = "") {
        FCMManager.syncTokenToFirestore(
            context = getApplication(),
            userId = userId,
            role = role,
            schoolId = schoolId,
            className = className
        )
    }

    fun sendNotification(
        targetType: String, // "STUDENT", "CLASS", "ALL_SCHOOL", "ALL_STUDENTS"
        targetStudentId: String? = null,
        targetClassName: String? = null,
        targetSection: String? = null,
        title: String,
        message: String,
        imageUrl: String? = null,
        isPaymentAlert: Boolean = false,
        amountDue: Double? = null,
        senderTeacherName: String? = null,
        senderTeacherId: String? = null
    ) {
        val targetId = when (targetType) {
            "STUDENT" -> targetStudentId ?: ""
            "CLASS" -> {
                val sec = if (!targetSection.isNullOrBlank()) "-$targetSection" else ""
                "${targetClassName ?: ""}$sec"
            }
            else -> "ALL_STUDENTS"
        }
        val actualTargetType = if (targetType == "ALL_SCHOOL") "ALL_STUDENTS" else targetType
        sendPushNotification(
            targetType = actualTargetType,
            targetId = targetId,
            recipientUserId = targetStudentId ?: "",
            title = title,
            body = message,
            deepLinkType = NotificationHelper.DEEP_LINK_NOTICE,
            deepLinkId = targetId,
            imageUrl = imageUrl,
            isPaymentAlert = isPaymentAlert,
            amountDue = amountDue,
            senderTeacherName = senderTeacherName,
            senderTeacherId = senderTeacherId
        )
    }

    fun sendPushNotification(
        targetType: String, // "ALL_STUDENTS", "CLASS", "STUDENT", "ALL_TEACHERS", "TEACHER"
        targetId: String = "",
        recipientUserId: String = "",
        title: String,
        body: String,
        deepLinkType: String = NotificationHelper.DEEP_LINK_NOTICE,
        deepLinkId: String = "",
        imageUrl: String? = null,
        isPaymentAlert: Boolean = false,
        amountDue: Double? = null,
        senderTeacherName: String? = null,
        senderTeacherId: String? = null
    ) {
        viewModelScope.launch {
            val userRole = session.value.role.uppercase()

            // Role-based validation:
            // 1. Students cannot send notifications
            if (userRole == "STUDENT") {
                _userMessage.value = "অননুমোদিত: শিক্ষার্থীদের নোটিফিকেশন পাঠানোর অনুমতি নেই।"
                return@launch
            }

            // 2. Admin can ONLY send notifications to Teachers
            if (userRole == "ADMIN") {
                val isTeacherTarget = targetType.contains("TEACHER", ignoreCase = true)
                if (!isTeacherTarget) {
                    _userMessage.value = "অ্যাডমিন শুধুমাত্র শিক্ষকদের (Teachers) পুশ নোটিফিকেশন পাঠাতে পারবেন।"
                    return@launch
                }
            } else if (userRole == "TEACHER") {
                // 3. Teachers can send notifications to Students
                val isTeacherTarget = targetType.contains("TEACHER", ignoreCase = true)
                if (isTeacherTarget) {
                    _userMessage.value = "শিক্ষকগণ শুধুমাত্র ছাত্রছাত্রীদের (Students) নোটিফিকেশন পাঠাতে পারবেন।"
                    return@launch
                }
            }

            val currentSchoolId = session.value.schoolId.ifBlank {
                if (userRole == "TEACHER") {
                    repository.getTeacherById(session.value.userId)?.schoolId ?: ""
                } else {
                    ""
                }
            }
            val currentSenderId = (senderTeacherId?.ifBlank { null } ?: session.value.userId).ifBlank {
                if (userRole == "ADMIN") "ADMIN-01" else "TEACHER-01"
            }

            // Precisely resolve sender teacher's name
            var resolvedSenderName = senderTeacherName?.trim()?.ifBlank { null } ?: session.value.userName.trim()
            if (userRole == "TEACHER" && (resolvedSenderName.isBlank() || resolvedSenderName.equals("Teacher", ignoreCase = true))) {
                val teacherInDb = repository.getTeacherById(currentSenderId)
                if (teacherInDb != null && teacherInDb.name.isNotBlank()) {
                    resolvedSenderName = teacherInDb.name
                }
            }
            if (resolvedSenderName.isBlank()) {
                resolvedSenderName = if (userRole == "ADMIN") "School Administration" else "Teacher $currentSenderId"
            }

            val notifId = "NOTIF_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val linkId = deepLinkId.ifBlank { targetId }

            val notification = NotificationEntity(
                schoolId = currentSchoolId,
                senderTeacherId = currentSenderId,
                senderName = resolvedSenderName,
                targetType = targetType,
                targetStudentId = if (targetType == "STUDENT") (recipientUserId.ifBlank { targetId }) else null,
                targetClassName = if (targetType == "CLASS") targetId else null,
                title = title,
                message = body,
                imageUrl = imageUrl,
                isPaymentAlert = isPaymentAlert,
                amountDue = amountDue,
                timestamp = System.currentTimeMillis(),
                notificationId = notifId,
                body = body,
                recipientUserId = if (targetType == "STUDENT" || targetType == "TEACHER") (recipientUserId.ifBlank { targetId }) else recipientUserId,
                targetId = targetId,
                createdAt = System.currentTimeMillis(),
                readStatus = false,
                deepLinkType = deepLinkType,
                deepLinkId = linkId
            )

            // 1. Local Room DB
            repository.sendNotification(notification)

            // 2. Google Cloud Firestore & FCM outbox queue (triggers server-side Cloud Function securely)
            val fsResult = firestoreRepository.sendNotificationToFirestore(notification)
            firestoreSyncService.replicateNotificationToCloud(notification)

            // 3. Selective notification delivery:
            // - Teacher to Student: In-App notification ONLY under the Bell icon (no system push popup)
            // - Admin to Teacher: System Push Notification displayed on Teacher ID
            val isFromTeacher = userRole == "TEACHER" || (senderTeacherId?.isNotBlank() == true && !senderTeacherId.startsWith("ADMIN", ignoreCase = true))
            val isTargetStudent = targetType == "STUDENT" || targetType == "CLASS" || targetType == "ALL_STUDENTS" || targetType == "ALL_SCHOOL"
            val isTeacherToStudent = isFromTeacher && isTargetStudent

            val isAdminSender = userRole == "ADMIN" || currentSenderId.startsWith("ADMIN", ignoreCase = true)
            val isTargetTeacher = targetType.contains("TEACHER", ignoreCase = true) || recipientUserId.startsWith("TCH", ignoreCase = true)
            val isAdminToTeacher = isAdminSender && isTargetTeacher

            if (isAdminToTeacher) {
                // Admin to Teacher: Trigger system Push Notification
                NotificationHelper.showNotification(
                    context = getApplication(),
                    notificationId = notifId,
                    title = title,
                    body = body,
                    deepLinkType = deepLinkType,
                    deepLinkId = linkId,
                    senderName = resolvedSenderName
                )
                _userMessage.value = "শিক্ষকের কাছে পুশ নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!"
            } else if (isTeacherToStudent) {
                // Teacher to Student: In-App Notification ONLY under Bell icon
                Log.d("SchoolViewModel", "Notice $notifId from teacher to student saved for In-App Bell icon only")
                _userMessage.value = "শিক্ষার্থীদের ইন-অ্যাপ নোটিফিকেশন সফলভাবে পাঠানো হয়েছে (বেল আইকনে প্রদর্শিত হবে)!"
            } else if (isAdminSender) {
                // Admin general broadcast
                NotificationHelper.showNotification(
                    context = getApplication(),
                    notificationId = notifId,
                    title = title,
                    body = body,
                    deepLinkType = deepLinkType,
                    deepLinkId = linkId,
                    senderName = resolvedSenderName
                )
                _userMessage.value = "পুশ নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!"
            } else {
                _userMessage.value = "নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!"
            }
        }
    }

    fun markNotificationAsRead(id: Long, notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
            if (notificationId.isNotBlank()) {
                repository.markNotificationAsReadByDocId(notificationId)
                firestoreRepository.markNotificationReadInFirestore(notificationId)
            }
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
            _userMessage.value = "All notifications marked as read."
        }
    }

    // --- Teacher Password Change ---

    fun changeTeacherPassword(newPass: String) {
        viewModelScope.launch {
            val teacherId = session.value.userId
            if (teacherId.isNotBlank()) {
                repository.updateTeacherPassword(teacherId, newPass)
                val existing = repository.getTeacherById(teacherId)
                if (existing != null) {
                    val fsResult = firestoreRepository.saveTeacherToFirestore(existing.copy(password = newPass))
                    if (fsResult.isSuccess) {
                        _userMessage.value = "Password updated in Google Firebase!"
                    } else {
                        _userMessage.value = "Password updated locally."
                    }
                } else {
                    _userMessage.value = "Password updated locally."
                }
            }
        }
    }

    // --- Admin Operations ---

    fun createSchoolAndTeacher(
        schoolName: String,
        schoolAddress: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPassword: String,
        assignedClass: String
    ) {
        viewModelScope.launch {
            val newSchoolId = "SCH-" + (1000 + (allSchools.value.size + 1))
            val newRegId = "REG-" + (10000 + (allSchools.value.size + 1))
            val newTeacherId = "TCH-" + (500 + (allTeachers.value.size + 1))

            val school = SchoolEntity(
                id = newSchoolId,
                name = schoolName,
                registrationId = newRegId,
                address = schoolAddress
            )
            val teacher = TeacherEntity(
                id = newTeacherId,
                schoolId = newSchoolId,
                schoolName = schoolName,
                name = teacherName,
                email = teacherEmail,
                mobile = teacherMobile,
                password = teacherPassword,
                assignedClass = assignedClass
            )
            repository.insertSchool(school)
            repository.insertTeacher(teacher)
            val schoolRes = firestoreRepository.saveSchoolToFirestore(school)
            val teacherRes = firestoreRepository.saveTeacherToFirestore(teacher)
            firestoreSyncService.replicateTeacherToCloud(teacher)
            if (schoolRes.isSuccess && teacherRes.isSuccess) {
                _userMessage.value = "Registered School & Teacher in Google Firebase: $schoolName (ID: $newSchoolId, Teacher: $newTeacherId)"
            } else {
                _userMessage.value = "Registered School & Teacher locally: $schoolName (ID: $newSchoolId, Teacher: $newTeacherId)"
            }
        }
    }

    fun createTeacherForSchool(
        schoolId: String,
        schoolName: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPassword: String,
        assignedClass: String,
        subject: String = "General"
    ) {
        viewModelScope.launch {
            val newTeacherId = "TCH-" + (500 + (allTeachers.value.size + 1))
            val teacher = TeacherEntity(
                id = newTeacherId,
                schoolId = schoolId,
                schoolName = schoolName,
                name = teacherName,
                email = teacherEmail,
                mobile = teacherMobile,
                password = teacherPassword,
                assignedClass = assignedClass,
                subject = subject
            )
            repository.insertTeacher(teacher)
            val teacherRes = firestoreRepository.saveTeacherToFirestore(teacher)
            firestoreSyncService.replicateTeacherToCloud(teacher)
            if (teacherRes.isSuccess) {
                _userMessage.value = "Registered Teacher in Google Firebase: $teacherName (ID: $newTeacherId, School: $schoolName)"
            } else {
                _userMessage.value = "Registered Teacher locally: $teacherName (ID: $newTeacherId, School: $schoolName)"
            }
        }
    }

    fun registerTeacherOnly(
        schoolId: String,
        schoolName: String,
        teacherName: String,
        teacherEmail: String,
        teacherMobile: String,
        teacherPassword: String,
        assignedClass: String,
        subject: String = "General"
    ) {
        createTeacherForSchool(schoolId, schoolName, teacherName, teacherEmail, teacherMobile, teacherPassword, assignedClass, subject)
    }

    fun deleteSchoolByAdmin(school: SchoolEntity, adminId: String = "ADM-001") {
        viewModelScope.launch {
            try {
                // 1. Delete from local Room database
                repository.deleteSchool(school)

                // 2. Delete from Cloud Firestore
                val fsRes = firestoreRepository.deleteSchoolFromFirestore(school.id)

                // 3. Remove any teachers associated with this school
                val teachersToDelete = repository.allTeachers.firstOrNull()?.filter {
                    it.schoolId == school.id || it.schoolName.equals(school.name, ignoreCase = true)
                } ?: emptyList()
                for (teacher in teachersToDelete) {
                    repository.deleteTeacher(teacher)
                    firestoreRepository.deleteTeacherFromFirestore(teacher.id)
                }

                // 4. Remove any students associated with this school
                val studentsToDelete = repository.allStudents.firstOrNull()?.filter {
                    it.schoolId == school.id || it.schoolName.equals(school.name, ignoreCase = true)
                } ?: emptyList()
                for (student in studentsToDelete) {
                    repository.deleteStudent(student)
                    firestoreRepository.deleteStudentFromFirestore(student.id)
                }

                val adminTag = if (adminId.isNotBlank()) " (Admin ID: $adminId)" else ""
                if (fsRes.isSuccess) {
                    _userMessage.value = "স্কুল '${school.name}' ফায়ারবেস ও অ্যাপ থেকে সফলভাবে মুছে ফেলা হয়েছে$adminTag।"
                } else {
                    _userMessage.value = "স্কুল '${school.name}' সফলভাবে মুছে ফেলা হয়েছে$adminTag।"
                }
            } catch (e: Exception) {
                _userMessage.value = "স্কুল মুছতে সমস্যা হয়েছে: ${e.message}"
            }
        }
    }

    fun addProduct(
        title: String,
        description: String,
        imageUrl: String,
        purchaseUrl: String,
        price: Double,
        category: String,
        targetAudience: String
    ) {
        viewModelScope.launch {
            val product = ProductEntity(
                title = title,
                description = description,
                imageUrl = imageUrl,
                purchaseUrl = purchaseUrl,
                price = price,
                category = category,
                targetAudience = targetAudience,
                inStock = true
            )
            repository.insertProduct(product)
            val fsResult = firestoreRepository.saveProductToFirestore(product)
            if (fsResult.isSuccess) {
                _userMessage.value = "Product added to Online Store & Google Firebase!"
            } else {
                _userMessage.value = "Product added to Online Store locally."
            }
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
            val fsResult = firestoreRepository.saveProductToFirestore(product)
            if (fsResult.isSuccess) {
                _userMessage.value = "Product updated in Google Firebase."
            } else {
                _userMessage.value = "Product updated locally."
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            val fsResult = firestoreRepository.deleteProductFromFirestore(product.id)
            if (fsResult.isSuccess) {
                _userMessage.value = "Product removed from Google Firebase."
            } else {
                _userMessage.value = "Product removed locally."
            }
        }
    }

    fun updateAppSettings(appName: String, logoUrl: String) {
        viewModelScope.launch {
            val entity = AppSettingsEntity(
                configKey = "GLOBAL_CONFIG",
                appName = appName,
                logoUrl = logoUrl
            )
            repository.saveAppSettings(entity)
            firestoreRepository.saveAppSettingsToFirestore(entity)
            _userMessage.value = "App name & branding updated in Google Firebase!"
        }
    }

    fun saveSearchQuery(query: String, searchType: String = "ALL") {
        viewModelScope.launch {
            repository.saveSearchQuery(query, searchType)
        }
    }

    fun deleteSearchHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteSearchById(id)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _userMessage.value = "Search history cleared."
        }
    }
}
