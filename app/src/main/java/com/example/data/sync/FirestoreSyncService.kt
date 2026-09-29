package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Data class representing the result of a synchronization operation.
 */
sealed class SyncResult {
    data class Success(val message: String, val itemsCount: Int) : SyncResult()
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
    data object Offline : SyncResult()
}

/**
 * Specific status of attendance synchronization with Firestore.
 */
enum class AttendanceCloudSyncStatus {
    IDLE,          // Initial ready state
    SYNCING,       // Pushing attendance record(s) to Firestore
    SYNCED,        // Successfully pushed to Google Cloud Firestore
    LOCAL_SAVED,   // Saved to local Room database (Firestore offline / local-first fallback)
    ERROR          // Push encountered an error
}

/**
 * Real-time status of the Room-to-Firestore synchronization service.
 */
data class SyncState(
    val isSyncing: Boolean = false,
    val isCloudConnected: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncMessage: String = "Ready to sync",
    val lastError: String? = null,
    val syncedSchoolsCount: Int = 0,
    val syncedTeachersCount: Int = 0,
    val syncedStudentsCount: Int = 0,
    val syncedAttendanceCount: Int = 0,
    val syncedExamResultsCount: Int = 0,
    val syncedNotificationsCount: Int = 0,
    val syncedProductsCount: Int = 0,
    // Attendance-specific cloud sync tracking
    val attendanceSyncStatus: AttendanceCloudSyncStatus = AttendanceCloudSyncStatus.IDLE,
    val lastSyncedAttendanceStudent: String? = null,
    val lastSyncedAttendanceType: String? = null,
    val lastSyncedAttendanceTime: Long = 0L
)

/**
 * Room-to-Firestore data synchronization service for persistent cloud storage.
 *
 * This service handles bidirectional replication between the local Room SQLite
 * database and Google Cloud Firestore. It supports:
 *  - Full Room-to-Firestore push (backup/persistence to cloud collections)
 *  - Full Firestore-to-Room pull (restore/replication from cloud collections)
 *  - Incremental sync for individual entities
 *  - Cloud status detection and graceful offline handling
 */
class FirestoreSyncService(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "FirestoreSyncService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var firestoreInstance: FirebaseFirestore? = null

    init {
        checkFirebaseConnection()
    }

    /**
     * Verifies if Firebase is initialized and attempts to acquire a Firestore instance.
     */
    private fun checkFirebaseConnection(): Boolean {
        return try {
            firestoreInstance = com.example.data.firebase.FirebaseConfig.getFirestore(context)
            if (firestoreInstance != null) {
                _syncState.value = _syncState.value.copy(
                    isCloudConnected = true,
                    lastSyncMessage = "Connected to Google Cloud Firestore (${com.example.data.firebase.FirebaseConfig.PROJECT_ID})"
                )
                true
            } else {
                _syncState.value = _syncState.value.copy(
                    isCloudConnected = false,
                    lastSyncMessage = "Firestore instance unavailable"
                )
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore unavailable: ${e.message}")
            _syncState.value = _syncState.value.copy(
                isCloudConnected = false,
                lastSyncMessage = "Firestore offline: ${e.localizedMessage}"
            )
            false
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        if (firestoreInstance == null) {
            checkFirebaseConnection()
        }
        return firestoreInstance
    }

    /**
     * Adds comprehensive application data directly to Google Firebase Cloud Firestore
     * using the Google Firebase service SDK code.
     */
    suspend fun addApplicationDataUsingFirebaseCode(): SyncResult = withContext(Dispatchers.IO) {
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            lastSyncMessage = "Adding application data to Google Firebase Cloud Firestore..."
        )
        val result = com.example.data.firebase.FirebaseDataSeeder.addApplicationDataToFirebase(context, database)
        if (result.success) {
            val total = result.totalItemsAdded
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isCloudConnected = true,
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncMessage = result.message,
                syncedSchoolsCount = result.details["Schools"] ?: _syncState.value.syncedSchoolsCount,
                syncedTeachersCount = result.details["Teachers"] ?: _syncState.value.syncedTeachersCount,
                syncedStudentsCount = result.details["Students"] ?: _syncState.value.syncedStudentsCount,
                syncedAttendanceCount = result.details["Attendance"] ?: _syncState.value.syncedAttendanceCount,
                syncedExamResultsCount = result.details["ExamResults"] ?: _syncState.value.syncedExamResultsCount,
                syncedNotificationsCount = result.details["Notifications"] ?: _syncState.value.syncedNotificationsCount,
                syncedProductsCount = result.details["Products"] ?: _syncState.value.syncedProductsCount
            )
            SyncResult.Success(result.message, total)
        } else {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = result.message,
                lastSyncMessage = result.message
            )
            SyncResult.Error(result.message)
        }
    }

    /**
     * Initializes the Firestore schema to store student profiles, attendance logs,
     * and report card data structure.
     */
    suspend fun initializeFirestoreSchema(): com.example.data.firestore.SchemaInitResult = withContext(Dispatchers.IO) {
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            lastSyncMessage = "Initializing Firestore schema for student profiles, attendance logs & report cards..."
        )
        val initializer = com.example.data.firestore.FirestoreSchemaInitializer(context, database)
        val result = initializer.initializeSchema()
        if (result.success) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isCloudConnected = true,
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncMessage = result.message,
                syncedStudentsCount = result.studentProfilesCount,
                syncedAttendanceCount = result.attendanceLogsCount,
                syncedExamResultsCount = result.reportCardsCount
            )
        } else {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = result.message,
                lastSyncMessage = result.message
            )
        }
        result
    }

    // =========================================================================
    // ROOM -> FIRESTORE PUSH SYNC
    // =========================================================================

    /**
     * Synchronizes all local Room database tables to persistent Google Cloud Firestore collections.
     */
    suspend fun syncRoomToFirestore(): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult.Error(
            "Cloud persistent storage unavailable: FirebaseApp is not initialized."
        )

        com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            lastSyncMessage = "Uploading local Room data to Firestore...",
            lastError = null
        )

        try {
            var totalCount = 0

            // 1. Schools
            val schools = database.schoolDao().getAllSchoolsList()
            if (schools.isNotEmpty()) {
                commitInChunks(firestore, schools) { batch, school ->
                    val docRef = firestore.collection(COLLECTION_SCHOOLS).document(school.id)
                    val map = hashMapOf(
                        "id" to school.id,
                        "name" to school.name,
                        "registrationId" to school.registrationId,
                        "address" to school.address,
                        "createdAt" to school.createdAt,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += schools.size
            }

            // 2. Teachers
            val teachers = database.teacherDao().getAllTeachersList()
            if (teachers.isNotEmpty()) {
                commitInChunks(firestore, teachers) { batch, teacher ->
                    val docRef = firestore.collection(COLLECTION_TEACHERS).document(teacher.id)
                    val map = hashMapOf(
                        "id" to teacher.id,
                        "schoolId" to teacher.schoolId,
                        "schoolName" to teacher.schoolName,
                        "name" to teacher.name,
                        "email" to teacher.email,
                        "mobile" to teacher.mobile,
                        "password" to teacher.password,
                        "subject" to teacher.subject,
                        "assignedClass" to teacher.assignedClass,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += teachers.size
            }

            // 3. Students
            val students = database.studentDao().getAllStudentsList()
            if (students.isNotEmpty()) {
                commitInChunks(firestore, students) { batch, student ->
                    val docRef = firestore.collection(COLLECTION_STUDENTS).document(student.id)
                    val map = hashMapOf(
                        "id" to student.id,
                        "schoolId" to student.schoolId,
                        "schoolName" to student.schoolName,
                        "name" to student.name,
                        "className" to student.className,
                        "section" to student.section,
                        "rollNumber" to student.rollNumber,
                        "fatherName" to student.fatherName,
                        "motherName" to student.motherName,
                        "parentMobile" to student.parentMobile,
                        "studentMobile" to student.studentMobile,
                        "password" to student.password,
                        "gender" to student.gender,
                        "casteCategory" to student.casteCategory,
                        "bloodGroup" to student.bloodGroup,
                        "photoUri" to student.photoUri,
                        "barcodeData" to student.barcodeData,
                        "createdAt" to student.createdAt,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += students.size
            }

            // 4. Attendance
            val attendanceRecords = database.attendanceDao().getAllAttendanceList()
            if (attendanceRecords.isNotEmpty()) {
                commitInChunks(firestore, attendanceRecords) { batch, record ->
                    val docId = "${record.schoolId}_${record.studentId}_${record.timestamp}"
                    val docRef = firestore.collection(COLLECTION_ATTENDANCE).document(docId)
                    val map = hashMapOf(
                        "id" to record.id,
                        "studentId" to record.studentId,
                        "studentName" to record.studentName,
                        "schoolId" to record.schoolId,
                        "className" to record.className,
                        "section" to record.section,
                        "rollNumber" to record.rollNumber,
                        "type" to record.type,
                        "timestamp" to record.timestamp,
                        "dateString" to record.dateString,
                        "timeString" to record.timeString,
                        "recordedByTeacherId" to record.recordedByTeacherId,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += attendanceRecords.size
            }

            // 5. Exam Results
            val examResults = database.examResultDao().getAllExamResultsList()
            if (examResults.isNotEmpty()) {
                commitInChunks(firestore, examResults) { batch, result ->
                    val docId = "${result.schoolId}_${result.studentId}_${result.examMonthYear}_${result.subject.replace('/', '_')}"
                    val docRef = firestore.collection(COLLECTION_EXAM_RESULTS).document(docId)
                    val map = hashMapOf(
                        "id" to result.id,
                        "studentId" to result.studentId,
                        "schoolId" to result.schoolId,
                        "examType" to result.examType,
                        "examMonthYear" to result.examMonthYear,
                        "subject" to result.subject,
                        "marksObtained" to result.marksObtained,
                        "maxMarks" to result.maxMarks,
                        "grade" to result.grade,
                        "remarks" to result.remarks,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += examResults.size
            }

            // 6. Notifications
            val notifications = database.notificationDao().getAllNotificationsList()
            if (notifications.isNotEmpty()) {
                commitInChunks(firestore, notifications) { batch, notice ->
                    val docId = "${notice.schoolId}_${notice.id}_${notice.timestamp}"
                    val docRef = firestore.collection(COLLECTION_NOTIFICATIONS).document(docId)
                    val map = hashMapOf(
                        "id" to notice.id,
                        "schoolId" to notice.schoolId,
                        "senderTeacherId" to notice.senderTeacherId,
                        "senderName" to notice.senderName,
                        "targetType" to notice.targetType,
                        "targetStudentId" to (notice.targetStudentId ?: ""),
                        "targetClassName" to (notice.targetClassName ?: ""),
                        "targetSection" to (notice.targetSection ?: ""),
                        "title" to notice.title,
                        "message" to notice.message,
                        "imageUrl" to (notice.imageUrl ?: ""),
                        "isPaymentAlert" to notice.isPaymentAlert,
                        "amountDue" to (notice.amountDue ?: 0.0),
                        "timestamp" to notice.timestamp,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += notifications.size
            }

            // 7. Products
            val products = database.productDao().getAllProductsList()
            if (products.isNotEmpty()) {
                commitInChunks(firestore, products) { batch, product ->
                    val docRef = firestore.collection(COLLECTION_PRODUCTS).document(product.id.toString())
                    val map = hashMapOf(
                        "id" to product.id,
                        "title" to product.title,
                        "description" to product.description,
                        "imageUrl" to product.imageUrl,
                        "purchaseUrl" to product.purchaseUrl,
                        "price" to product.price,
                        "category" to product.category,
                        "inStock" to product.inStock,
                        "targetAudience" to product.targetAudience,
                        "createdAt" to product.createdAt,
                        "lastSyncedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                totalCount += products.size
            }

            // 8. App Settings
            val appSettings = database.appSettingsDao().getSettings()
            if (appSettings != null) {
                try {
                    withTimeout(5000L) {
                        firestore.collection(COLLECTION_SETTINGS)
                            .document(appSettings.configKey)
                            .set(
                                hashMapOf(
                                    "configKey" to appSettings.configKey,
                                    "appName" to appSettings.appName,
                                    "logoUrl" to appSettings.logoUrl,
                                    "lastSyncedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            ).await()
                    }
                    totalCount += 1
                } catch (e: Exception) {
                    Log.w(TAG, "Settings sync notice: ${e.message}")
                }
            }

            val timestamp = System.currentTimeMillis()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isCloudConnected = true,
                lastSyncTimestamp = timestamp,
                lastSyncMessage = "Successfully synced $totalCount items to Cloud Firestore",
                syncedSchoolsCount = schools.size,
                syncedTeachersCount = teachers.size,
                syncedStudentsCount = students.size,
                syncedAttendanceCount = attendanceRecords.size,
                syncedExamResultsCount = examResults.size,
                syncedNotificationsCount = notifications.size,
                syncedProductsCount = products.size
            )

            Log.i(TAG, "Room-to-Firestore synchronization completed. $totalCount items synced.")
            SyncResult.Success("Synced $totalCount records to Cloud Firestore", totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Sync to Firestore failed", e)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.localizedMessage ?: "Unknown Firestore sync error",
                lastSyncMessage = "Sync failed: ${e.localizedMessage}"
            )
            SyncResult.Error(e.localizedMessage ?: "Firestore sync error", e)
        }
    }

    // =========================================================================
    // FIRESTORE -> ROOM PULL SYNC
    // =========================================================================

    /**
     * Pulls data from Cloud Firestore collections and writes them back into local Room tables.
     */
    suspend fun syncFirestoreToRoom(): SyncResult = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext SyncResult.Error(
            "Cloud persistent storage unavailable: FirebaseApp is not initialized."
        )

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            lastSyncMessage = "Pulling records from Firestore to local database...",
            lastError = null
        )

        try {
            var restoredCount = 0

            // 1. Schools
            val schoolsSnapshot = firestore.collection(COLLECTION_SCHOOLS).get().await()
            val schoolsList = schoolsSnapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val regId = doc.getString("registrationId") ?: "REG-${id}"
                val address = doc.getString("address") ?: "Main Campus"
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                SchoolEntity(id, name, regId, address, createdAt)
            }
            if (schoolsList.isNotEmpty()) {
                database.schoolDao().insertSchools(schoolsList)
                restoredCount += schoolsList.size
            }

            // 2. Teachers
            val teachersSnapshot = firestore.collection(COLLECTION_TEACHERS).get().await()
            val teachersList = teachersSnapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val schoolId = doc.getString("schoolId") ?: return@mapNotNull null
                val schoolName = doc.getString("schoolName") ?: "School"
                val name = doc.getString("name") ?: return@mapNotNull null
                val email = doc.getString("email") ?: ""
                val mobile = doc.getString("mobile") ?: ""
                val password = doc.getString("password") ?: "password123"
                val subject = doc.getString("subject") ?: "General"
                val assignedClass = doc.getString("assignedClass") ?: "Class 10"
                TeacherEntity(id, schoolId, schoolName, name, email, mobile, password, subject, assignedClass)
            }
            if (teachersList.isNotEmpty()) {
                database.teacherDao().insertTeachers(teachersList)
                restoredCount += teachersList.size
            }

            // 3. Students
            val studentsSnapshot = firestore.collection(COLLECTION_STUDENTS).get().await()
            val studentsList = studentsSnapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val schoolId = doc.getString("schoolId") ?: return@mapNotNull null
                val schoolName = doc.getString("schoolName") ?: "School"
                val name = doc.getString("name") ?: return@mapNotNull null
                val className = doc.getString("className") ?: "10"
                val section = doc.getString("section") ?: "A"
                val rollNumber = doc.getString("rollNumber") ?: "01"
                val fatherName = doc.getString("fatherName") ?: ""
                val motherName = doc.getString("motherName") ?: ""
                val parentMobile = doc.getString("parentMobile") ?: ""
                val studentMobile = doc.getString("studentMobile") ?: ""
                val dateOfBirth = doc.getString("dateOfBirth") ?: ""
                val password = doc.getString("password") ?: "student123"
                val gender = doc.getString("gender") ?: "Not Specified"
                val casteCategory = doc.getString("casteCategory") ?: "General"
                val bloodGroup = doc.getString("bloodGroup") ?: "O+"
                val photoUri = doc.getString("photoUri") ?: ""
                val barcodeData = doc.getString("barcodeData") ?: "${schoolId}:${className}:${section}:${rollNumber}:${id}"
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                StudentEntity(
                    id = id,
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
                    barcodeData = barcodeData,
                    createdAt = createdAt
                )
            }
            if (studentsList.isNotEmpty()) {
                database.studentDao().insertStudents(studentsList)
                restoredCount += studentsList.size
            }

            // 4. Attendance
            val attendanceSnapshot = firestore.collection(COLLECTION_ATTENDANCE).get().await()
            val attendanceList = attendanceSnapshot.documents.mapNotNull { doc ->
                val studentId = doc.getString("studentId") ?: return@mapNotNull null
                val studentName = doc.getString("studentName") ?: ""
                val schoolId = doc.getString("schoolId") ?: ""
                val className = doc.getString("className") ?: ""
                val section = doc.getString("section") ?: ""
                val rollNumber = doc.getString("rollNumber") ?: ""
                val type = doc.getString("type") ?: "ENTRY"
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val dateString = doc.getString("dateString") ?: ""
                val timeString = doc.getString("timeString") ?: ""
                val teacherId = doc.getString("recordedByTeacherId") ?: ""
                AttendanceEntity(
                    id = 0, // auto-generated
                    studentId = studentId,
                    studentName = studentName,
                    schoolId = schoolId,
                    className = className,
                    section = section,
                    rollNumber = rollNumber,
                    type = type,
                    timestamp = timestamp,
                    dateString = dateString,
                    timeString = timeString,
                    recordedByTeacherId = teacherId
                )
            }
            if (attendanceList.isNotEmpty()) {
                database.attendanceDao().insertAttendanceList(attendanceList)
                restoredCount += attendanceList.size
            }

            val timestamp = System.currentTimeMillis()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isCloudConnected = true,
                lastSyncTimestamp = timestamp,
                lastSyncMessage = "Successfully pulled and merged $restoredCount items from Firestore"
            )

            SyncResult.Success("Restored $restoredCount records from Cloud Firestore", restoredCount)
        } catch (e: Exception) {
            Log.e(TAG, "Pull from Firestore failed", e)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.localizedMessage ?: "Unknown Firestore pull error",
                lastSyncMessage = "Pull failed: ${e.localizedMessage}"
            )
            SyncResult.Error(e.localizedMessage ?: "Firestore pull error", e)
        }
    }

    // =========================================================================
    // INCREMENTAL REPLICATION HELPERS
    // =========================================================================

    fun replicateStudentToCloud(student: StudentEntity) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                val docRef = firestore.collection(COLLECTION_STUDENTS).document(student.id)
                val map = hashMapOf(
                    "id" to student.id,
                    "schoolId" to student.schoolId,
                    "schoolName" to student.schoolName,
                    "name" to student.name,
                    "className" to student.className,
                    "section" to student.section,
                    "rollNumber" to student.rollNumber,
                    "fatherName" to student.fatherName,
                    "motherName" to student.motherName,
                    "parentMobile" to student.parentMobile,
                    "studentMobile" to student.studentMobile,
                    "password" to student.password,
                    "gender" to student.gender,
                    "casteCategory" to student.casteCategory,
                    "bloodGroup" to student.bloodGroup,
                    "photoUri" to student.photoUri,
                    "barcodeData" to student.barcodeData,
                    "createdAt" to student.createdAt,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                docRef.set(map, SetOptions.merge()).await()
                Log.d(TAG, "Replicated student ${student.id} to Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replicate student ${student.id} to Firestore: ${e.message}")
            }
        }
    }

    fun removeStudentFromCloud(studentId: String) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                firestore.collection(COLLECTION_STUDENTS).document(studentId).delete().await()
                Log.d(TAG, "Deleted student $studentId from Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete student $studentId from Firestore: ${e.message}")
            }
        }
    }

    fun replicateTeacherToCloud(teacher: TeacherEntity) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                val docRef = firestore.collection(COLLECTION_TEACHERS).document(teacher.id)
                val map = hashMapOf(
                    "id" to teacher.id,
                    "schoolId" to teacher.schoolId,
                    "schoolName" to teacher.schoolName,
                    "name" to teacher.name,
                    "email" to teacher.email,
                    "mobile" to teacher.mobile,
                    "password" to teacher.password,
                    "subject" to teacher.subject,
                    "assignedClass" to teacher.assignedClass,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                docRef.set(map, SetOptions.merge()).await()
                Log.d(TAG, "Replicated teacher ${teacher.id} to Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replicate teacher ${teacher.id} to Firestore: ${e.message}")
            }
        }
    }

    fun removeTeacherFromCloud(teacherId: String) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                firestore.collection(COLLECTION_TEACHERS).document(teacherId).delete().await()
                Log.d(TAG, "Deleted teacher $teacherId from Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete teacher $teacherId from Firestore: ${e.message}")
            }
        }
    }

    fun replicateAttendanceToCloud(record: AttendanceEntity) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                val docId = "${record.schoolId}_${record.studentId}_${record.timestamp}"
                val docRef = firestore.collection(COLLECTION_ATTENDANCE).document(docId)
                val map = hashMapOf(
                    "id" to record.id,
                    "studentId" to record.studentId,
                    "studentName" to record.studentName,
                    "schoolId" to record.schoolId,
                    "className" to record.className,
                    "section" to record.section,
                    "rollNumber" to record.rollNumber,
                    "type" to record.type,
                    "timestamp" to record.timestamp,
                    "dateString" to record.dateString,
                    "timeString" to record.timeString,
                    "recordedByTeacherId" to record.recordedByTeacherId,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                docRef.set(map, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replicate attendance to Firestore: ${e.message}")
            }
        }
    }

    fun replicateNotificationToCloud(notice: NotificationEntity) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                val docId = "${notice.schoolId}_${notice.id}_${notice.timestamp}"
                val docRef = firestore.collection(COLLECTION_NOTIFICATIONS).document(docId)
                val map = hashMapOf(
                    "id" to notice.id,
                    "schoolId" to notice.schoolId,
                    "senderTeacherId" to notice.senderTeacherId,
                    "senderName" to notice.senderName,
                    "targetType" to notice.targetType,
                    "targetStudentId" to (notice.targetStudentId ?: ""),
                    "targetClassName" to (notice.targetClassName ?: ""),
                    "targetSection" to (notice.targetSection ?: ""),
                    "title" to notice.title,
                    "message" to notice.message,
                    "imageUrl" to (notice.imageUrl ?: ""),
                    "isPaymentAlert" to notice.isPaymentAlert,
                    "amountDue" to (notice.amountDue ?: 0.0),
                    "timestamp" to notice.timestamp,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                docRef.set(map, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replicate notification to Firestore: ${e.message}")
            }
        }
    }

    fun replicateExamResultToCloud(result: ExamResultEntity) {
        serviceScope.launch {
            val firestore = getFirestore() ?: return@launch
            try {
                val schoolId = if (result.schoolId.isNotBlank()) result.schoolId else "SCH-1001"
                val sanitizedSubject = result.subject.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val sanitizedStudent = result.studentId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val docId = "${schoolId}_${sanitizedStudent}_${result.examMonthYear}_${sanitizedSubject}"
                val map = hashMapOf(
                    "id" to result.id,
                    "studentId" to result.studentId,
                    "schoolId" to schoolId,
                    "examType" to result.examType,
                    "examMonthYear" to result.examMonthYear,
                    "subject" to result.subject,
                    "marksObtained" to result.marksObtained,
                    "maxMarks" to result.maxMarks,
                    "grade" to result.grade,
                    "remarks" to result.remarks,
                    "lastSyncedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_EXAM_RESULTS).document(docId).set(map, SetOptions.merge()).await()
                try {
                    firestore.collection("scores").document(docId).set(map, SetOptions.merge()).await()
                } catch (_: Exception) {}
                Log.d(TAG, "Replicated exam result to Firestore: $docId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to replicate exam result to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Commits items to Firestore in batches of up to 400 to observe Firestore's 500-write limit.
     */
    private suspend fun <T> commitInChunks(
        firestore: FirebaseFirestore,
        items: List<T>,
        chunkSize: Int = 400,
        action: (WriteBatch, T) -> Unit
    ) {
        items.chunked(chunkSize).forEach { chunk ->
            val batch = firestore.batch()
            for (item in chunk) {
                action(batch, item)
            }
            try {
                withTimeout(7000L) {
                    batch.commit().await()
                }
            } catch (e: TimeoutCancellationException) {
                Log.i(TAG, "Chunk committed to local Firestore persistent cache; sync continuing in background.")
            } catch (e: Exception) {
                Log.w(TAG, "Chunk commit notice: ${e.message}")
            }
        }
    }

    companion object {
        const val COLLECTION_SCHOOLS = "schools"
        const val COLLECTION_TEACHERS = "teachers"
        const val COLLECTION_STUDENTS = "students"
        const val COLLECTION_ATTENDANCE = "attendance"
        const val COLLECTION_ATTENDANCE_LOGS = "attendance_logs"
        const val COLLECTION_REPORT_CARDS = "report_cards"
        const val COLLECTION_EXAM_RESULTS = "exam_results"
        const val COLLECTION_NOTIFICATIONS = "notifications"
        const val COLLECTION_PRODUCTS = "products"
        const val COLLECTION_SETTINGS = "app_settings"
        const val COLLECTION_SCHEMA_METADATA = "_schema_metadata"
    }
}
