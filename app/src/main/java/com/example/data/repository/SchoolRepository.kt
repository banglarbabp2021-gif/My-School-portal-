package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SchoolRepository(private val database: AppDatabase) {
    // School DAO operations
    val allSchools: Flow<List<SchoolEntity>> = database.schoolDao().getAllSchools()
    val schoolCount: Flow<Int> = database.schoolDao().getSchoolCount()

    suspend fun getSchoolById(id: String): SchoolEntity? = database.schoolDao().getSchoolById(id)
    suspend fun insertSchool(school: SchoolEntity) = database.schoolDao().insertSchool(school)
    suspend fun deleteSchool(school: SchoolEntity) = database.schoolDao().deleteSchool(school)
    suspend fun deleteSchoolById(id: String) = database.schoolDao().deleteSchoolById(id)

    // Teacher DAO operations
    val allTeachers: Flow<List<TeacherEntity>> = database.teacherDao().getAllTeachers()
    val teacherCount: Flow<Int> = database.teacherDao().getTeacherCount()

    fun getTeachersBySchool(schoolId: String): Flow<List<TeacherEntity>> = database.teacherDao().getTeachersBySchool(schoolId)
    suspend fun getTeacherById(id: String): TeacherEntity? = database.teacherDao().getTeacherById(id)
    suspend fun loginTeacher(schoolQuery: String, teacherIdOrName: String, pass: String): TeacherEntity? =
        database.teacherDao().loginTeacher(schoolQuery, teacherIdOrName, pass)
    suspend fun insertTeacher(teacher: TeacherEntity) = database.teacherDao().insertTeacher(teacher)
    suspend fun updateTeacher(teacher: TeacherEntity) = database.teacherDao().updateTeacher(teacher)
    suspend fun updateTeacherPassword(teacherId: String, newPass: String) = database.teacherDao().updateTeacherPassword(teacherId, newPass)
    suspend fun deleteTeacher(teacher: TeacherEntity) = database.teacherDao().deleteTeacher(teacher)

    // Student DAO operations
    val allStudents: Flow<List<StudentEntity>> = database.studentDao().getAllStudents()
    val studentCount: Flow<Int> = database.studentDao().getStudentCount()

    fun getStudentsBySchool(schoolId: String): Flow<List<StudentEntity>> = database.studentDao().getStudentsBySchool(schoolId)
    fun getStudentsByClass(schoolId: String, className: String, section: String): Flow<List<StudentEntity>> =
        database.studentDao().getStudentsByClass(schoolId, className, section)
    suspend fun getStudentById(id: String): StudentEntity? = database.studentDao().getStudentById(id)
    fun getStudentByIdFlow(id: String): Flow<StudentEntity?> = database.studentDao().getStudentByIdFlow(id)
    suspend fun loginStudent(schoolQuery: String, className: String, section: String, rollNumber: String, pass: String): StudentEntity? =
        database.studentDao().loginStudent(schoolQuery, className, section, rollNumber, pass)
    suspend fun loginStudentByBarcodeId(barcodeId: String, pass: String): StudentEntity? =
        database.studentDao().loginStudentByBarcodeId(barcodeId, pass)
    fun searchStudents(schoolId: String, query: String): Flow<List<StudentEntity>> =
        database.studentDao().searchStudents(schoolId, query)
    suspend fun insertStudent(student: StudentEntity) = database.studentDao().insertStudent(student)
    suspend fun updateStudent(student: StudentEntity) = database.studentDao().updateStudent(student)
    suspend fun deleteStudent(student: StudentEntity) = database.studentDao().deleteStudent(student)

    // Attendance operations
    fun getAttendanceForStudent(studentId: String): Flow<List<AttendanceEntity>> = database.attendanceDao().getAttendanceForStudent(studentId)
    fun getAttendanceForSchool(schoolId: String): Flow<List<AttendanceEntity>> = database.attendanceDao().getAttendanceForSchool(schoolId)
    fun getDailyAttendance(schoolId: String, dateString: String): Flow<List<AttendanceEntity>> = database.attendanceDao().getDailyAttendance(schoolId, dateString)

    suspend fun findStudentByIdOrBarcode(query: String): StudentEntity? {
        val trimmed = query.trim()
        val parsed = com.example.util.BarcodeUtil.parseStudentIdFromScan(trimmed)
        return database.studentDao().getStudentById(trimmed)
            ?: database.studentDao().getStudentById(parsed)
            ?: database.studentDao().findStudentByIdOrBarcode(trimmed)
            ?: database.studentDao().findStudentByIdOrBarcode(parsed)
    }

    suspend fun recordAttendance(
        studentIdOrBarcode: String,
        type: String, // "ENTRY" or "EXIT"
        recordedByTeacherId: String
    ): AttendanceEntity? {
        val student = findStudentByIdOrBarcode(studentIdOrBarcode) ?: return null
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val record = AttendanceEntity(
            studentId = student.id,
            studentName = student.name,
            schoolId = student.schoolId,
            className = student.className,
            section = student.section,
            rollNumber = student.rollNumber,
            type = type,
            timestamp = now,
            dateString = dateFormat.format(Date(now)),
            timeString = timeFormat.format(Date(now)),
            recordedByTeacherId = recordedByTeacherId
        )
        database.attendanceDao().insertAttendance(record)
        return record
    }

    // Exam results
    fun getResultsForStudent(studentId: String): Flow<List<ExamResultEntity>> = database.examResultDao().getResultsForStudent(studentId)
    fun getAllResultsForSchool(schoolId: String): Flow<List<ExamResultEntity>> = database.examResultDao().getAllResultsForSchool(schoolId)
    suspend fun insertExamResult(result: ExamResultEntity): Long = database.examResultDao().insertExamResult(result)
    suspend fun updateExamResult(result: ExamResultEntity) = database.examResultDao().updateExamResult(result)
    suspend fun deleteExamResult(id: Long) = database.examResultDao().deleteExamResult(id)

    // Notifications
    fun getAllNotificationsForSchool(schoolId: String): Flow<List<NotificationEntity>> = database.notificationDao().getAllNotificationsForSchool(schoolId)
    fun getNotificationsForStudent(schoolId: String, studentId: String, className: String, section: String): Flow<List<NotificationEntity>> =
        database.notificationDao().getNotificationsForStudent(schoolId, studentId, className, section)
    fun getNotificationsForTeacher(schoolId: String, teacherId: String): Flow<List<NotificationEntity>> =
        database.notificationDao().getNotificationsForTeacher(schoolId, teacherId)
    val allNotificationsFlow: Flow<List<NotificationEntity>> = database.notificationDao().getAllNotificationsFlow()
    val unreadNotificationsCount: Flow<Int> = database.notificationDao().getUnreadCountFlow()
    suspend fun sendNotification(notification: NotificationEntity) = database.notificationDao().insertNotification(notification)
    suspend fun deleteNotification(notification: NotificationEntity) = database.notificationDao().deleteNotification(notification)
    suspend fun markNotificationAsRead(id: Long) = database.notificationDao().markAsRead(id)
    suspend fun markNotificationAsReadByDocId(notifId: String) = database.notificationDao().markAsReadByNotificationId(notifId)
    suspend fun markAllNotificationsAsRead() = database.notificationDao().markAllAsRead()
    suspend fun deleteDuplicateNotifications() = database.notificationDao().deleteDuplicateNotifications()

    // Products / Online Shopping
    val allProducts: Flow<List<ProductEntity>> = database.productDao().getAllProducts()
    val productCount: Flow<Int> = database.productDao().getProductCount()
    fun getProductsForAudience(audience: String): Flow<List<ProductEntity>> = database.productDao().getProductsForAudience(audience)
    suspend fun insertProduct(product: ProductEntity) = database.productDao().insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = database.productDao().updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = database.productDao().deleteProduct(product)

    // App Settings
    val appSettingsFlow: Flow<AppSettingsEntity?> = database.appSettingsDao().getSettingsFlow()
    suspend fun getAppSettings(): AppSettingsEntity? = database.appSettingsDao().getSettings()
    suspend fun saveAppSettings(settings: AppSettingsEntity) = database.appSettingsDao().saveSettings(settings)

    // Search History operations
    val recentSearches: Flow<List<SearchHistoryEntity>> = database.searchHistoryDao().getRecentSearches()
    fun getRecentSearchesByType(searchType: String): Flow<List<SearchHistoryEntity>> =
        database.searchHistoryDao().getRecentSearchesByType(searchType)

    suspend fun saveSearchQuery(query: String, searchType: String = "ALL") {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            database.searchHistoryDao().deleteSearchByQuery(trimmed)
            database.searchHistoryDao().insertSearch(
                SearchHistoryEntity(
                    query = trimmed,
                    searchType = searchType,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteSearchById(id: Long) = database.searchHistoryDao().deleteSearchById(id)
    suspend fun clearSearchHistory() = database.searchHistoryDao().clearAllHistory()

    suspend fun purgeAllDemoData() {
        database.schoolDao().deleteDemoSchools()
        database.teacherDao().deleteDemoTeachers()
        database.studentDao().deleteDemoStudents()
        database.attendanceDao().deleteDemoAttendance()
        database.examResultDao().deleteDemoExamResults()
        database.notificationDao().deleteDemoNotifications()
        database.productDao().deleteDemoProducts()
    }
}
