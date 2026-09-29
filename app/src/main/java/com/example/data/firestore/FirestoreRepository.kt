package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.sync.FirestoreSyncService
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Direct Firestore Repository providing real-time streams and operations
 * with strict role-based data isolation:
 *  - Teacher: Restricted to data belonging to their school
 *  - Student: Restricted to their own profile, attendance, exam results, and notices
 *  - Admin: Full management of schools, teachers, students, attendance, results, notices, products
 */
class FirestoreRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "FirestoreRepository"
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun getFirestore(): FirebaseFirestore? {
        return com.example.data.firebase.FirebaseConfig.getFirestore(context)
    }

    // =========================================================================
    // REAL-TIME FIRESTORE DATA STREAMS (WITH LOCAL ROOM CACHE MIRRORING)
    // =========================================================================

    /**
     * Admin: Stream all schools from Firestore in real-time.
     */
    fun getAllSchoolsRealtime(): Flow<List<SchoolEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.schoolDao().getAllSchools().collect { localList ->
                trySend(localList)
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_SCHOOLS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Schools listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            val id = doc.getString("id") ?: doc.id
                            val name = doc.getString("name") ?: return@mapNotNull null
                            val regId = doc.getString("registrationId") ?: "REG-$id"
                            val address = doc.getString("address") ?: "Main Campus"
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            SchoolEntity(id, name, regId, address, createdAt)
                        }
                        trySend(list)
                        ioScope.launch {
                            val localSchools = database.schoolDao().getAllSchoolsList()
                            val remoteIds = list.map { it.id }.toSet()
                            localSchools.filter { it.id !in remoteIds }.forEach {
                                database.schoolDao().deleteSchool(it)
                            }
                            if (list.isNotEmpty()) {
                                database.schoolDao().insertSchools(list)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach schools listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Teacher: Stream students strictly belonging to the teacher's school from Firestore.
     */
    fun getTeacherStudentsRealtime(schoolId: String): Flow<List<StudentEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.studentDao().getStudentsBySchool(schoolId).collect { localList ->
                trySend(localList)
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_STUDENTS)
                .whereEqualTo("schoolId", schoolId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Teacher students listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseStudentDoc(doc) }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.studentDao().insertStudents(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach teacher students listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Teacher: Stream attendance strictly belonging to the teacher's school from Firestore.
     */
    fun getTeacherAttendanceRealtime(schoolId: String): Flow<List<AttendanceEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.attendanceDao().getAttendanceForSchool(schoolId).collect { localList ->
                trySend(localList)
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_ATTENDANCE)
                .whereEqualTo("schoolId", schoolId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Teacher attendance listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseAttendanceDoc(doc) }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.attendanceDao().insertAttendanceList(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach teacher attendance listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Teacher: Stream notifications strictly belonging to the teacher's school.
     */
    fun getTeacherNotificationsRealtime(schoolId: String): Flow<List<NotificationEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.notificationDao().getAllNotificationsForSchool(schoolId).collect { localList ->
                if (localList.isNotEmpty()) {
                    trySend(localList)
                }
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("schoolId", schoolId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Teacher notices listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseNotificationDoc(doc) }
                            .distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.notificationDao().insertNotifications(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach teacher notices listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Teacher: Stream exam results strictly belonging to the teacher's school.
     */
    fun getTeacherExamResultsRealtime(schoolId: String): Flow<List<ExamResultEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.examResultDao().getAllResultsForSchool(schoolId).collect { localList ->
                if (localList.isNotEmpty()) {
                    trySend(localList)
                }
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_EXAM_RESULTS)
                .whereEqualTo("schoolId", schoolId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Teacher exam results listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseExamResultDoc(doc) }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.examResultDao().insertExamResults(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach teacher exam results listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    // =========================================================================
    // STUDENT ACCESS: STRICTLY SCOPED TO STUDENT'S OWN DATA
    // =========================================================================

    /**
     * Student: Stream own profile document from Firestore.
     */
    fun getStudentProfileRealtime(studentId: String): Flow<StudentEntity?> = callbackFlow {
        val localSub = ioScope.launch {
            database.studentDao().getStudentByIdFlow(studentId).collect { localStudent ->
                if (localStudent != null) {
                    trySend(localStudent)
                }
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_STUDENTS)
                .document(studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Student profile listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val student = parseStudentDoc(snapshot)
                        if (student != null) {
                            trySend(student)
                            ioScope.launch { database.studentDao().insertStudent(student) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach student profile listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Student: Stream own attendance records from Firestore.
     */
    fun getStudentAttendanceRealtime(studentId: String): Flow<List<AttendanceEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.attendanceDao().getAttendanceForStudent(studentId).collect { localList ->
                if (localList.isNotEmpty()) {
                    trySend(localList)
                }
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_ATTENDANCE)
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Student attendance listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseAttendanceDoc(doc) }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.attendanceDao().insertAttendanceList(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach student attendance listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Student: Stream own exam results from Firestore.
     */
    fun getStudentExamResultsRealtime(studentId: String): Flow<List<ExamResultEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.examResultDao().getResultsForStudent(studentId).collect { localList ->
                if (localList.isNotEmpty()) {
                    trySend(localList)
                }
            }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_EXAM_RESULTS)
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Student exam results listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc -> parseExamResultDoc(doc) }
                        if (list.isNotEmpty()) {
                            trySend(list)
                            ioScope.launch { database.examResultDao().insertExamResults(list) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach student exam results listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    /**
     * Student: Stream notices targeted to student, class, or all-school.
     */
    fun getStudentNoticesRealtime(
        schoolId: String,
        studentId: String,
        className: String,
        section: String
    ): Flow<List<NotificationEntity>> = callbackFlow {
        val localSub = ioScope.launch {
            database.notificationDao().getNotificationsForStudent(schoolId, studentId, className, section)
                .collect { localList ->
                    if (localList.isNotEmpty()) {
                        trySend(localList)
                    }
                }
        }

        val firestore = getFirestore()
        if (firestore == null) {
            awaitClose { localSub.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(FirestoreSyncService.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("schoolId", schoolId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Student notices listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val allSchoolNotices = snapshot.documents.mapNotNull { doc -> parseNotificationDoc(doc) }
                        val studentNotices = allSchoolNotices.filter { notice ->
                            notice.targetType == "ALL_SCHOOL" ||
                                    notice.targetType == "ALL_STUDENTS" ||
                                    (notice.targetType == "STUDENT" && (notice.targetStudentId == studentId || notice.recipientUserId == studentId || notice.targetId == studentId)) ||
                                    (notice.targetType == "CLASS" && (notice.targetClassName == className || notice.targetId == className) &&
                                            (notice.targetSection.isNullOrBlank() || notice.targetSection == section))
                        }.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
                        trySend(studentNotices)
                        if (studentNotices.isNotEmpty()) {
                            ioScope.launch { database.notificationDao().insertNotifications(studentNotices) }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach student notices listener: ${e.message}")
        }

        awaitClose {
            localSub.cancel()
            registration?.remove()
        }
    }

    // =========================================================================
    // DIRECT FIRESTORE MUTATIONS (PERSISTENT CLOUD WRITES)
    // =========================================================================

    private suspend fun safeAwaitTask(task: Task<*>, timeoutMs: Long = 5000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                task.await()
            }
            true
        } catch (e: TimeoutCancellationException) {
            Log.i(TAG, "Write queued in Firestore local cache; cloud synchronization in progress.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firestore write error: ${e.message}")
            throw e
        }
    }

    suspend fun saveSchoolToFirestore(school: SchoolEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val map = hashMapOf(
                "id" to school.id,
                "name" to school.name,
                "registrationId" to school.registrationId,
                "address" to school.address,
                "createdAt" to school.createdAt,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_SCHOOLS)
                .document(school.id)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save school to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteSchoolFromFirestore(schoolId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val task = firestore.collection(FirestoreSyncService.COLLECTION_SCHOOLS).document(schoolId).delete()
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete school from Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveTeacherToFirestore(teacher: TeacherEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
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
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_TEACHERS)
                .document(teacher.id)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save teacher to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteTeacherFromFirestore(teacherId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val task = firestore.collection(FirestoreSyncService.COLLECTION_TEACHERS).document(teacherId).delete()
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete teacher from Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveStudentToFirestore(student: StudentEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
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
                "dateOfBirth" to student.dateOfBirth,
                "password" to student.password,
                "gender" to student.gender,
                "casteCategory" to student.casteCategory,
                "bloodGroup" to student.bloodGroup,
                "photoUri" to student.photoUri,
                "barcodeData" to student.barcodeData,
                "createdAt" to student.createdAt,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_STUDENTS)
                .document(student.id)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)
            Log.i(TAG, "Student ${student.id} successfully persisted in Firestore.")
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save student to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteStudentFromFirestore(studentId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val task = firestore.collection(FirestoreSyncService.COLLECTION_STUDENTS).document(studentId).delete()
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete student from Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun recordAttendanceToFirestore(record: AttendanceEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val docId = "${record.schoolId}_${record.studentId}_${record.timestamp}"
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
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_ATTENDANCE)
                .document(docId)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)

            try {
                val logTask = firestore.collection(FirestoreSyncService.COLLECTION_ATTENDANCE_LOGS)
                    .document(docId)
                    .set(map, SetOptions.merge())
                safeAwaitTask(logTask)
            } catch (e: Exception) {
                Log.w(TAG, "attendance_logs write notice: ${e.message}")
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record attendance in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveExamResultToFirestore(result: ExamResultEntity): Result<String> = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        if (firestore == null) {
            val msg = "Cloud Firestore instance not available"
            Log.e(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val schoolId = if (result.schoolId.isNotBlank()) result.schoolId else "SCHOOL"
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
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task1 = firestore.collection(FirestoreSyncService.COLLECTION_EXAM_RESULTS)
                .document(docId)
                .set(map, SetOptions.merge())
            safeAwaitTask(task1)

            try {
                val task2 = firestore.collection("scores")
                    .document(docId)
                    .set(map, SetOptions.merge())
                safeAwaitTask(task2)
            } catch (e: Exception) {
                Log.w(TAG, "scores collection write fallback: ${e.message}")
            }

            Log.i(TAG, "Exam result successfully written to Firestore doc: $docId")
            Result.success(docId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save exam result to Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendNotificationToFirestore(notice: NotificationEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val notifId = notice.notificationId.ifBlank {
                "NOTIF_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
            }
            val bodyText = notice.body.ifBlank { notice.message }
            val map = hashMapOf(
                "id" to notice.id,
                "notificationId" to notifId,
                "schoolId" to notice.schoolId,
                "senderTeacherId" to notice.senderTeacherId,
                "senderName" to notice.senderName,
                "targetType" to notice.targetType,
                "targetId" to (notice.targetId.ifBlank { notice.targetStudentId ?: notice.targetClassName ?: "ALL" }),
                "targetStudentId" to (notice.targetStudentId ?: ""),
                "targetClassName" to (notice.targetClassName ?: ""),
                "targetSection" to (notice.targetSection ?: ""),
                "recipientUserId" to (notice.recipientUserId.ifBlank { notice.targetStudentId ?: "" }),
                "title" to notice.title,
                "body" to bodyText,
                "message" to bodyText,
                "imageUrl" to (notice.imageUrl ?: ""),
                "isPaymentAlert" to notice.isPaymentAlert,
                "amountDue" to (notice.amountDue ?: 0.0),
                "timestamp" to notice.timestamp,
                "createdAt" to notice.createdAt,
                "readStatus" to notice.readStatus,
                "deepLinkType" to notice.deepLinkType,
                "deepLinkId" to (notice.deepLinkId.ifBlank { notice.targetId }),
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_NOTIFICATIONS)
                .document(notifId)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)

            // Replicate to fcm_outbox for FCM Cloud Function dispatching
            val outboxMap = hashMapOf(
                "notificationId" to notifId,
                "schoolId" to notice.schoolId,
                "senderRole" to "ADMIN",
                "senderId" to notice.senderTeacherId,
                "targetType" to notice.targetType,
                "targetId" to (notice.targetId.ifBlank { notice.targetStudentId ?: notice.targetClassName ?: "ALL" }),
                "recipientUserId" to (notice.recipientUserId.ifBlank { notice.targetStudentId ?: "" }),
                "title" to notice.title,
                "body" to bodyText,
                "isPaymentAlert" to notice.isPaymentAlert,
                "amountDue" to (notice.amountDue ?: 0.0),
                "deepLinkType" to notice.deepLinkType,
                "deepLinkId" to (notice.deepLinkId.ifBlank { notice.targetId }),
                "fcmTopic" to when (notice.targetType) {
                    "ALL_STUDENTS" -> "school_${notice.schoolId.lowercase()}_students"
                    "ALL_TEACHERS" -> "school_${notice.schoolId.lowercase()}_teachers"
                    "CLASS" -> "class_${(notice.targetClassName ?: "").replace(" ", "_").lowercase()}"
                    "ALL_SCHOOL" -> "school_${notice.schoolId.lowercase()}_all"
                    else -> null
                },
                "status" to "SENT_VIA_FCM",
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("fcm_outbox").document(notifId).set(outboxMap, SetOptions.merge())

            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send notification to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Updates notification read status in Firestore.
     */
    suspend fun markNotificationReadInFirestore(notificationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val task = firestore.collection(FirestoreSyncService.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("readStatus", true)
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark notification as read in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Securely stores the device's FCM push token in Firestore without exposing any server secrets.
     */
    suspend fun saveFcmToken(
        userId: String,
        role: String,
        schoolId: String,
        className: String,
        token: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val timestamp = System.currentTimeMillis()
            val tokenData = hashMapOf(
                "userId" to userId,
                "role" to role,
                "schoolId" to schoolId,
                "className" to className,
                "fcmToken" to token,
                "platform" to "android",
                "updatedAt" to timestamp
            )
            // 1. Central token lookup index for Cloud Functions
            val tokenTask = firestore.collection("fcm_tokens").document(userId).set(tokenData, SetOptions.merge())
            safeAwaitTask(tokenTask)

            // 2. Direct user collection update
            if (role.equals("STUDENT", ignoreCase = true)) {
                val studentTask = firestore.collection(FirestoreSyncService.COLLECTION_STUDENTS).document(userId)
                    .set(mapOf("fcmToken" to token, "tokenUpdatedAt" to timestamp), SetOptions.merge())
                safeAwaitTask(studentTask)
            } else if (role.equals("TEACHER", ignoreCase = true)) {
                val teacherTask = firestore.collection(FirestoreSyncService.COLLECTION_TEACHERS).document(userId)
                    .set(mapOf("fcmToken" to token, "tokenUpdatedAt" to timestamp), SetOptions.merge())
                safeAwaitTask(teacherTask)
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save FCM token in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveProductToFirestore(product: ProductEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val docId = product.id.toString()
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
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_PRODUCTS)
                .document(docId)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save product to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteProductFromFirestore(productId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val task = firestore.collection(FirestoreSyncService.COLLECTION_PRODUCTS)
                .document(productId.toString())
                .delete()
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete product from Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveAppSettingsToFirestore(settings: AppSettingsEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(Exception("Cloud Firestore unavailable"))
        try {
            com.example.data.firebase.FirebaseConfig.ensureAuthenticated(context)
            val map = hashMapOf(
                "configKey" to settings.configKey,
                "appName" to settings.appName,
                "logoUrl" to settings.logoUrl,
                "lastModifiedAt" to System.currentTimeMillis()
            )
            val task = firestore.collection(FirestoreSyncService.COLLECTION_SETTINGS)
                .document(settings.configKey)
                .set(map, SetOptions.merge())
            safeAwaitTask(task)
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save app settings to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // PARSERS
    // =========================================================================

    private fun parseStudentDoc(doc: com.google.firebase.firestore.DocumentSnapshot): StudentEntity? {
        val id = doc.getString("id") ?: doc.id
        val schoolId = doc.getString("schoolId") ?: return null
        val schoolName = doc.getString("schoolName") ?: "School"
        val name = doc.getString("name") ?: return null
        val className = doc.getString("className") ?: "10"
        val section = doc.getString("section") ?: "A"
        val rollNumber = doc.getString("rollNumber") ?: "01"
        val fatherName = doc.getString("fatherName") ?: ""
        val motherName = doc.getString("motherName") ?: ""
        val parentMobile = doc.getString("parentMobile") ?: ""
        val studentMobile = doc.getString("studentMobile") ?: ""
        val dateOfBirth = doc.getString("dateOfBirth") ?: ""
        val password = doc.getString("password") ?: "student123"
        val gender = doc.getString("gender") ?: "General"
        val casteCategory = doc.getString("casteCategory") ?: "General"
        val bloodGroup = doc.getString("bloodGroup") ?: "O+"
        val photoUri = doc.getString("photoUri") ?: ""
        val barcodeData = doc.getString("barcodeData") ?: id
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

        return StudentEntity(
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

    private fun parseAttendanceDoc(doc: com.google.firebase.firestore.DocumentSnapshot): AttendanceEntity? {
        val id = doc.getLong("id") ?: 0L
        val studentId = doc.getString("studentId") ?: return null
        val studentName = doc.getString("studentName") ?: "Student"
        val schoolId = doc.getString("schoolId") ?: return null
        val className = doc.getString("className") ?: "10"
        val section = doc.getString("section") ?: "A"
        val rollNumber = doc.getString("rollNumber") ?: "01"
        val type = doc.getString("type") ?: "ENTRY"
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val dateString = doc.getString("dateString") ?: ""
        val timeString = doc.getString("timeString") ?: ""
        val recordedByTeacherId = doc.getString("recordedByTeacherId") ?: "TEACHER"

        return AttendanceEntity(
            id = id,
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
            recordedByTeacherId = recordedByTeacherId
        )
    }

    private fun parseExamResultDoc(doc: com.google.firebase.firestore.DocumentSnapshot): ExamResultEntity? {
        val id = doc.getLong("id") ?: 0L
        val studentId = doc.getString("studentId") ?: return null
        val schoolId = doc.getString("schoolId") ?: ""
        val examType = doc.getString("examType") ?: "Exam"
        val examMonthYear = doc.getString("examMonthYear") ?: "2026-09"
        val subject = doc.getString("subject") ?: return null
        val marksObtained = when (val m = doc.get("marksObtained")) {
            is Number -> m.toInt()
            is String -> m.toIntOrNull() ?: 0
            else -> 0
        }
        val maxMarks = when (val m = doc.get("maxMarks")) {
            is Number -> m.toInt()
            is String -> m.toIntOrNull() ?: 100
            else -> 100
        }
        val grade = doc.getString("grade") ?: "A"
        val remarks = doc.getString("remarks") ?: ""

        return ExamResultEntity(
            id = id,
            studentId = studentId,
            schoolId = schoolId,
            examType = examType,
            examMonthYear = examMonthYear,
            subject = subject,
            marksObtained = marksObtained,
            maxMarks = maxMarks,
            grade = grade,
            remarks = remarks
        )
    }

    private fun parseNotificationDoc(doc: com.google.firebase.firestore.DocumentSnapshot): NotificationEntity? {
        val id = doc.getLong("id") ?: 0L
        val schoolId = doc.getString("schoolId") ?: ""
        val senderTeacherId = doc.getString("senderTeacherId") ?: ""
        val senderName = doc.getString("senderName") ?: "Teacher"
        val targetType = doc.getString("targetType") ?: "ALL_SCHOOL"
        val targetStudentId = doc.getString("targetStudentId")?.ifBlank { null }
        val targetClassName = doc.getString("targetClassName")?.ifBlank { null }
        val targetSection = doc.getString("targetSection")?.ifBlank { null }
        val title = doc.getString("title") ?: return null
        val message = doc.getString("message") ?: doc.getString("body") ?: ""
        val imageUrl = doc.getString("imageUrl")?.ifBlank { null }

        // Ignore demo notifications as per user request
        if (title.contains("Annual Sports Day", ignoreCase = true) ||
            title.contains("Mathematics Formula", ignoreCase = true) ||
            title.contains("Lab Fee Due", ignoreCase = true) ||
            title.contains("Inter-School Science Fair", ignoreCase = true) ||
            senderTeacherId in listOf("TCH-501", "TCH-502", "TCH-503", "TCH-504") ||
            schoolId in listOf("SCH-1001", "SCH-1002")
        ) {
            return null
        }
        val isPaymentAlert = doc.getBoolean("isPaymentAlert") ?: false
        val amountDue = doc.getDouble("amountDue")
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

        // FCM push notification fields
        val notificationId = doc.getString("notificationId") ?: doc.id
        val body = doc.getString("body") ?: message
        val recipientUserId = doc.getString("recipientUserId") ?: ""
        val targetId = doc.getString("targetId") ?: ""
        val createdAt = doc.getLong("createdAt") ?: timestamp
        val readStatus = doc.getBoolean("readStatus") ?: false
        val deepLinkType = doc.getString("deepLinkType") ?: "NOTICE"
        val deepLinkId = doc.getString("deepLinkId") ?: ""

        return NotificationEntity(
            id = id,
            schoolId = schoolId,
            senderTeacherId = senderTeacherId,
            senderName = senderName,
            targetType = targetType,
            targetStudentId = targetStudentId,
            targetClassName = targetClassName,
            targetSection = targetSection,
            title = title,
            message = message,
            imageUrl = imageUrl,
            isPaymentAlert = isPaymentAlert,
            amountDue = amountDue,
            timestamp = timestamp,
            notificationId = notificationId,
            body = body,
            recipientUserId = recipientUserId,
            targetId = targetId,
            createdAt = createdAt,
            readStatus = readStatus,
            deepLinkType = deepLinkType,
            deepLinkId = deepLinkId
        )
    }

    /**
     * Initializes the Firestore schema for student profiles, attendance logs,
     * and report card data structure.
     */
    suspend fun initializeFirestoreSchema(): SchemaInitResult = withContext(Dispatchers.IO) {
        val initializer = FirestoreSchemaInitializer(context, database)
        initializer.initializeSchema()
    }
}
