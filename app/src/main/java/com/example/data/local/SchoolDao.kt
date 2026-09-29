package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    @Query("SELECT * FROM schools ORDER BY name ASC")
    fun getAllSchools(): Flow<List<SchoolEntity>>

    @Query("SELECT * FROM schools ORDER BY name ASC")
    suspend fun getAllSchoolsList(): List<SchoolEntity>

    @Query("SELECT * FROM schools WHERE id = :schoolId LIMIT 1")
    suspend fun getSchoolById(schoolId: String): SchoolEntity?

    @Query("SELECT * FROM schools WHERE name = :name LIMIT 1")
    suspend fun getSchoolByName(name: String): SchoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchool(school: SchoolEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchools(schools: List<SchoolEntity>)

    @Delete
    suspend fun deleteSchool(school: SchoolEntity)

    @Query("DELETE FROM schools WHERE id = :schoolId")
    suspend fun deleteSchoolById(schoolId: String)

    @Query("DELETE FROM schools WHERE id IN ('SCH-1001', 'SCH-1002') OR name LIKE '%Delhi Public%' OR name LIKE '%St. Xavier%'")
    suspend fun deleteDemoSchools()

    @Query("SELECT COUNT(*) FROM schools")
    fun getSchoolCount(): Flow<Int>
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun getAllTeachers(): Flow<List<TeacherEntity>>

    @Query("DELETE FROM teachers WHERE id IN ('TCH-501', 'TCH-502', 'TCH-503', 'TCH-504') OR schoolId IN ('SCH-1001', 'SCH-1002') OR email LIKE '%sarah.jenkins%' OR email LIKE '%robert.vance%'")
    suspend fun deleteDemoTeachers()

    @Query("SELECT * FROM teachers ORDER BY name ASC")
    suspend fun getAllTeachersList(): List<TeacherEntity>

    @Query("SELECT * FROM teachers WHERE schoolId = :schoolId ORDER BY name ASC")
    fun getTeachersBySchool(schoolId: String): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getTeacherById(id: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE (id = :idOrEmail OR email = :idOrEmail) AND password = :password LIMIT 1")
    suspend fun authenticateTeacher(idOrEmail: String, password: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE (schoolName LIKE '%' || :schoolQuery || '%' OR schoolId = :schoolQuery) AND (id = :teacherIdOrName OR name LIKE '%' || :teacherIdOrName || '%') AND password = :password LIMIT 1")
    suspend fun loginTeacher(schoolQuery: String, teacherIdOrName: String, password: String): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: TeacherEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeachers(teachers: List<TeacherEntity>)

    @Update
    suspend fun updateTeacher(teacher: TeacherEntity)

    @Query("UPDATE teachers SET password = :newPassword WHERE id = :teacherId")
    suspend fun updateTeacherPassword(teacherId: String, newPassword: String)

    @Query("SELECT * FROM teachers WHERE id = :query OR barcodeData = :query OR mobile = :query OR email = :query LIMIT 1")
    suspend fun findTeacherByIdOrBarcode(query: String): TeacherEntity?

    @Delete
    suspend fun deleteTeacher(teacher: TeacherEntity)

    @Query("SELECT COUNT(*) FROM teachers")
    fun getTeacherCount(): Flow<Int>
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY className, section, rollNumber ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY className, section, rollNumber ASC")
    suspend fun getAllStudentsList(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId ORDER BY className, section, rollNumber ASC")
    fun getStudentsBySchool(schoolId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND className = :className AND section = :section ORDER BY rollNumber ASC")
    fun getStudentsByClass(schoolId: String, className: String, section: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :query OR barcodeData = :query OR rollNumber = :query LIMIT 1")
    suspend fun findStudentByIdOrBarcode(query: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :id")
    fun getStudentByIdFlow(id: String): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE (schoolName LIKE '%' || :schoolQuery || '%' OR schoolId = :schoolQuery) AND className = :className AND section = :section AND rollNumber = :rollNumber AND password = :password LIMIT 1")
    suspend fun loginStudent(schoolQuery: String, className: String, section: String, rollNumber: String, password: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :barcodeId AND password = :password LIMIT 1")
    suspend fun loginStudentByBarcodeId(barcodeId: String, password: String): StudentEntity?

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND (name LIKE '%' || :query || '%' OR rollNumber LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%')")
    fun searchStudents(schoolId: String, query: String): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("SELECT COUNT(*) FROM students")
    fun getStudentCount(): Flow<Int>

    @Query("DELETE FROM students WHERE id LIKE 'STU-10-%' OR schoolId IN ('SCH-1001', 'SCH-1002')")
    suspend fun deleteDemoStudents()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getAttendanceForStudent(studentId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance ORDER BY timestamp DESC")
    suspend fun getAllAttendanceList(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE schoolId = :schoolId ORDER BY timestamp DESC LIMIT 200")
    fun getAttendanceForSchool(schoolId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE schoolId = :schoolId AND dateString = :dateString ORDER BY timestamp DESC")
    fun getDailyAttendance(schoolId: String, dateString: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(records: List<AttendanceEntity>)

    @Query("SELECT COUNT(*) FROM attendance WHERE schoolId = :schoolId AND type = 'ENTRY' AND dateString = :dateString")
    fun getTodayEntryCount(schoolId: String, dateString: String): Flow<Int>

    @Query("DELETE FROM attendance WHERE schoolId IN ('SCH-1001', 'SCH-1002') OR studentId LIKE 'STU-10-%'")
    suspend fun deleteDemoAttendance()
}

@Dao
interface ExamResultDao {
    @Query("SELECT * FROM exam_results WHERE studentId = :studentId ORDER BY examMonthYear DESC, subject ASC")
    fun getResultsForStudent(studentId: String): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results ORDER BY examMonthYear DESC, subject ASC")
    suspend fun getAllExamResultsList(): List<ExamResultEntity>

    @Query("SELECT * FROM exam_results WHERE schoolId = :schoolId")
    fun getAllResultsForSchool(schoolId: String): Flow<List<ExamResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamResult(result: ExamResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamResults(results: List<ExamResultEntity>)

    @Update
    suspend fun updateExamResult(result: ExamResultEntity)

    @Query("DELETE FROM exam_results WHERE id = :id")
    suspend fun deleteExamResult(id: Long)

    @Query("DELETE FROM exam_results WHERE schoolId IN ('SCH-1001', 'SCH-1002') OR studentId LIKE 'STU-10-%'")
    suspend fun deleteDemoExamResults()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE schoolId = :schoolId GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END ORDER BY timestamp DESC")
    fun getAllNotificationsForSchool(schoolId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END ORDER BY timestamp DESC")
    suspend fun getAllNotificationsList(): List<NotificationEntity>

    @Query("""
        SELECT * FROM notifications 
        WHERE (:schoolId = '' OR schoolId = :schoolId) 
          AND (
            targetType = 'ALL_SCHOOL' 
            OR targetType = 'ALL_STUDENTS'
            OR (targetType = 'CLASS' AND (targetClassName = :className OR targetId = :className) AND (targetSection = :section OR targetSection IS NULL OR targetSection = '')) 
            OR (targetType = 'STUDENT' AND (targetStudentId = :studentId OR recipientUserId = :studentId OR targetId = :studentId))
          ) 
        GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END
        ORDER BY timestamp DESC
    """)
    fun getNotificationsForStudent(schoolId: String, studentId: String, className: String, section: String): Flow<List<NotificationEntity>>

    @Query("""
        SELECT * FROM notifications 
        WHERE (:schoolId = '' OR schoolId = :schoolId)
          AND (
            targetType = 'ALL_SCHOOL' 
            OR targetType = 'ALL_TEACHERS'
            OR recipientUserId = :teacherId
            OR targetId = :teacherId
            OR senderTeacherId = :teacherId
          )
        GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END
        ORDER BY timestamp DESC
    """)
    fun getNotificationsForTeacher(schoolId: String, teacherId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE notificationId = :notifId LIMIT 1")
    suspend fun getNotificationByNotificationId(notifId: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE title = :title AND message = :message AND timestamp = :timestamp LIMIT 1")
    suspend fun findDuplicateNotice(title: String, message: String, timestamp: Long): NotificationEntity?

    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT MIN(id) FROM notifications GROUP BY CASE WHEN notificationId IS NOT NULL AND notificationId != '' THEN notificationId ELSE id END)")
    suspend fun deleteDuplicateNotifications()

    @Query("UPDATE notifications SET readStatus = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET readStatus = 1 WHERE notificationId = :notifId")
    suspend fun markAsReadByNotificationId(notifId: String)

    @Query("UPDATE notifications SET readStatus = 1")
    suspend fun markAllAsRead()

    @Query("SELECT COUNT(*) FROM notifications WHERE readStatus = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawNotification(notification: NotificationEntity): Long

    @Transaction
    suspend fun insertNotification(notification: NotificationEntity): Long {
        val targetNotifId = if (notification.notificationId.isNotBlank()) {
            notification.notificationId
        } else {
            "NOTIF_${notification.timestamp}_${(notification.title + notification.message).hashCode()}"
        }
        val existing = getNotificationByNotificationId(targetNotifId)
            ?: findDuplicateNotice(notification.title, notification.message, notification.timestamp)
        return if (existing != null) {
            val updated = notification.copy(
                id = existing.id,
                notificationId = targetNotifId,
                readStatus = existing.readStatus || notification.readStatus
            )
            insertRawNotification(updated)
            existing.id
        } else {
            insertRawNotification(notification.copy(notificationId = targetNotifId))
        }
    }

    @Transaction
    suspend fun insertNotifications(notifications: List<NotificationEntity>) {
        val seenInBatch = HashSet<String>()
        for (item in notifications) {
            val key = if (item.notificationId.isNotBlank()) item.notificationId else "${item.title}_${item.timestamp}"
            if (seenInBatch.add(key)) {
                insertNotification(item)
            }
        }
    }

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE schoolId IN ('SCH-1001', 'SCH-1002') OR senderTeacherId IN ('TCH-501', 'TCH-502', 'TCH-503', 'TCH-504') OR title LIKE '%Annual Sports Day%' OR title LIKE '%Mathematics Formula%' OR title LIKE '%Lab Fee%' OR title LIKE '%Science Fair%' OR title LIKE '%Academic Year%' OR title LIKE '%Term 1%' OR title LIKE '%Term 2%'")
    suspend fun deleteDemoNotifications()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE targetAudience = 'ALL' OR targetAudience = :audience ORDER BY createdAt DESC")
    fun getProductsForAudience(audience: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("SELECT COUNT(*) FROM products")
    fun getProductCount(): Flow<Int>

    @Query("DELETE FROM products WHERE id IN (1, 2, 3, 4) AND (title LIKE '%Uniform%' OR title LIKE '%Mathematics Grade 10%')")
    suspend fun deleteDemoProducts()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE configKey = :key LIMIT 1")
    suspend fun getSettings(key: String = "GLOBAL_CONFIG"): AppSettingsEntity?

    @Query("SELECT * FROM app_settings WHERE configKey = :key LIMIT 1")
    fun getSettingsFlow(key: String = "GLOBAL_CONFIG"): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE searchType = :searchType OR searchType = 'ALL' ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearchesByType(searchType: String): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(searchHistory: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE LOWER(query) = LOWER(:query)")
    suspend fun deleteSearchByQuery(query: String)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()
}

@Dao
interface TeacherAttendanceDao {
    @Query("SELECT * FROM teacher_attendance ORDER BY timestamp DESC")
    fun getAllTeacherAttendance(): Flow<List<TeacherAttendanceEntity>>

    @Query("SELECT * FROM teacher_attendance WHERE schoolId = :schoolId ORDER BY timestamp DESC")
    fun getTeacherAttendanceBySchool(schoolId: String): Flow<List<TeacherAttendanceEntity>>

    @Query("SELECT * FROM teacher_attendance WHERE teacherId = :teacherId ORDER BY timestamp DESC")
    fun getAttendanceForTeacher(teacherId: String): Flow<List<TeacherAttendanceEntity>>

    @Query("SELECT * FROM teacher_attendance ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTeacherAttendance(limit: Int = 50): List<TeacherAttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacherAttendance(record: TeacherAttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacherAttendanceList(records: List<TeacherAttendanceEntity>)

    @Query("DELETE FROM teacher_attendance WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM teacher_attendance WHERE dateString = :dateString AND type = 'ENTRY'")
    fun getTodayTeacherEntryCount(dateString: String): Flow<Int>
}
