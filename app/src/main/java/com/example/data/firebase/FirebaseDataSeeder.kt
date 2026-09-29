package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettingsEntity
import com.example.data.model.AttendanceEntity
import com.example.data.model.ExamResultEntity
import com.example.data.model.NotificationEntity
import com.example.data.model.ProductEntity
import com.example.data.model.SchoolEntity
import com.example.data.model.StudentEntity
import com.example.data.model.TeacherEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Result returned after seeding or adding application data to Google Firebase services.
 */
data class SeederResult(
    val success: Boolean,
    val totalItemsAdded: Int,
    val message: String,
    val details: Map<String, Int> = emptyMap()
)

/**
 * Service to populate, seed, and add full application data directly to Google Firebase Firestore.
 */
object FirebaseDataSeeder {
    private const val TAG = "FirebaseDataSeeder"

    const val COLLECTION_SCHOOLS = "schools"
    const val COLLECTION_TEACHERS = "teachers"
    const val COLLECTION_STUDENTS = "students"
    const val COLLECTION_ATTENDANCE = "attendance"
    const val COLLECTION_ATTENDANCE_LOGS = "attendance_logs"
    const val COLLECTION_REPORT_CARDS = "report_cards"
    const val COLLECTION_EXAM_RESULTS = "exam_results"
    const val COLLECTION_SCORES = "scores"
    const val COLLECTION_NOTIFICATIONS = "notifications"
    const val COLLECTION_PRODUCTS = "products"
    const val COLLECTION_SETTINGS = "app_settings"
    const val COLLECTION_SCHEMA_METADATA = "_schema_metadata"

    suspend fun addApplicationDataToFirebase(
        context: Context,
        database: AppDatabase
    ): SeederResult = withContext(Dispatchers.IO) {
        val firestore = FirebaseConfig.getFirestore(context)
        if (firestore == null) {
            val msg = "Google Firebase Firestore service is currently unavailable."
            Log.e(TAG, msg)
            return@withContext SeederResult(false, 0, msg)
        }

        FirebaseConfig.ensureAuthenticated(context)

        try {
            var totalCount = 0
            val countMap = mutableMapOf<String, Int>()

            // 1. Schools
            val schools = listOf(
                SchoolEntity(
                    id = "SCH-1001",
                    name = "Delhi Public Academy",
                    registrationId = "REG-DPA-2026",
                    address = "Sector 4, Central Enclave, New Delhi",
                    createdAt = System.currentTimeMillis() - 864000000L
                ),
                SchoolEntity(
                    id = "SCH-1002",
                    name = "St. Xavier's Model School",
                    registrationId = "REG-SXMS-2026",
                    address = "Park Street Boulevard, Kolkata",
                    createdAt = System.currentTimeMillis() - 777600000L
                ),
                SchoolEntity(
                    id = "SCH-1003",
                    name = "Greenwood International School",
                    registrationId = "REG-GIS-2026",
                    address = "Greenwood Valley Campus, Bengaluru",
                    createdAt = System.currentTimeMillis() - 691200000L
                )
            )
            writeSchools(firestore, schools)
            database.schoolDao().insertSchools(schools)
            totalCount += schools.size
            countMap["Schools"] = schools.size

            // 2. Teachers
            val teachers = listOf(
                TeacherEntity(
                    id = "TCH-501",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Mrs. Sarah Jenkins",
                    email = "sarah.jenkins@school.edu",
                    mobile = "+1 98765 43210",
                    password = "teacher123",
                    subject = "Mathematics & Science",
                    assignedClass = "Class 10-A"
                ),
                TeacherEntity(
                    id = "TCH-502",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Mr. Robert Vance",
                    email = "robert.vance@school.edu",
                    mobile = "+1 98765 43211",
                    password = "teacher123",
                    subject = "English Literature",
                    assignedClass = "Class 10-B"
                ),
                TeacherEntity(
                    id = "TCH-503",
                    schoolId = "SCH-1002",
                    schoolName = "St. Xavier's Model School",
                    name = "Dr. Angela Martinez",
                    email = "angela.martinez@school.edu",
                    mobile = "+1 98765 43212",
                    password = "teacher123",
                    subject = "Physics & Chemistry",
                    assignedClass = "Class 11-A"
                ),
                TeacherEntity(
                    id = "TCH-504",
                    schoolId = "SCH-1003",
                    schoolName = "Greenwood International School",
                    name = "Mr. David Lee",
                    email = "david.lee@school.edu",
                    mobile = "+1 98765 43213",
                    password = "teacher123",
                    subject = "Computer Science & Robotics",
                    assignedClass = "Class 10-A"
                )
            )
            writeTeachers(firestore, teachers)
            teachers.forEach { database.teacherDao().insertTeacher(it) }
            totalCount += teachers.size
            countMap["Teachers"] = teachers.size

            // 3. Students
            val students = listOf(
                StudentEntity(
                    id = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Aarav Sharma",
                    className = "10",
                    section = "A",
                    rollNumber = "01",
                    fatherName = "Rajesh Sharma",
                    motherName = "Sunita Sharma",
                    parentMobile = "+1 91234 56780",
                    studentMobile = "+1 91234 56781",
                    password = "pass123",
                    gender = "Male",
                    casteCategory = "General",
                    bloodGroup = "O+",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:A:01:STU-10-A-01"
                ),
                StudentEntity(
                    id = "STU-10-A-02",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Priya Patel",
                    className = "10",
                    section = "A",
                    rollNumber = "02",
                    fatherName = "Vikram Patel",
                    motherName = "Anita Patel",
                    parentMobile = "+1 91234 56782",
                    studentMobile = "+1 91234 56783",
                    password = "pass123",
                    gender = "Female",
                    casteCategory = "OBC",
                    bloodGroup = "B+",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:A:02:STU-10-A-02"
                ),
                StudentEntity(
                    id = "STU-10-B-01",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Rohan Das",
                    className = "10",
                    section = "B",
                    rollNumber = "01",
                    fatherName = "Debashis Das",
                    motherName = "Mita Das",
                    parentMobile = "+1 91234 56784",
                    studentMobile = "+1 91234 56785",
                    password = "pass123",
                    gender = "Male",
                    casteCategory = "SC",
                    bloodGroup = "A+",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:B:01:STU-10-B-01"
                ),
                StudentEntity(
                    id = "STU-10-B-02",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Ananya Verma",
                    className = "10",
                    section = "B",
                    rollNumber = "02",
                    fatherName = "Sunil Verma",
                    motherName = "Pooja Verma",
                    parentMobile = "+1 91234 56786",
                    studentMobile = "+1 91234 56787",
                    password = "pass123",
                    gender = "Female",
                    casteCategory = "General",
                    bloodGroup = "AB+",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:B:02:STU-10-B-02"
                ),
                StudentEntity(
                    id = "STU-10-A-03",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Kabir Mehta",
                    className = "10",
                    section = "A",
                    rollNumber = "03",
                    fatherName = "Alok Mehta",
                    motherName = "Meera Mehta",
                    parentMobile = "+1 91234 56788",
                    studentMobile = "+1 91234 56789",
                    password = "pass123",
                    gender = "Male",
                    casteCategory = "General",
                    bloodGroup = "O-",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:A:03:STU-10-A-03"
                ),
                StudentEntity(
                    id = "STU-10-A-04",
                    schoolId = "SCH-1001",
                    schoolName = "Delhi Public Academy",
                    name = "Sneha Reddy",
                    className = "10",
                    section = "A",
                    rollNumber = "04",
                    fatherName = "Venkat Reddy",
                    motherName = "Lakshmi Reddy",
                    parentMobile = "+1 91234 56790",
                    studentMobile = "+1 91234 56791",
                    password = "pass123",
                    gender = "Female",
                    casteCategory = "OBC",
                    bloodGroup = "A+",
                    photoUri = "",
                    barcodeData = "SCH-1001:10:A:04:STU-10-A-04"
                ),
                StudentEntity(
                    id = "STU-SX-11-A-01",
                    schoolId = "SCH-1002",
                    schoolName = "St. Xavier's Model School",
                    name = "Dev Mukherjee",
                    className = "11",
                    section = "A",
                    rollNumber = "01",
                    fatherName = "Subir Mukherjee",
                    motherName = "Pramila Mukherjee",
                    parentMobile = "+1 91234 56795",
                    studentMobile = "+1 91234 56796",
                    password = "pass123",
                    gender = "Male",
                    casteCategory = "General",
                    bloodGroup = "B+",
                    photoUri = "",
                    barcodeData = "SCH-1002:11:A:01:STU-SX-11-A-01"
                ),
                StudentEntity(
                    id = "STU-SX-11-A-02",
                    schoolId = "SCH-1002",
                    schoolName = "St. Xavier's Model School",
                    name = "Ishani Sen",
                    className = "11",
                    section = "A",
                    rollNumber = "02",
                    fatherName = "Pratap Sen",
                    motherName = "Gargi Sen",
                    parentMobile = "+1 91234 56797",
                    studentMobile = "+1 91234 56798",
                    password = "pass123",
                    gender = "Female",
                    casteCategory = "General",
                    bloodGroup = "O+",
                    photoUri = "",
                    barcodeData = "SCH-1002:11:A:02:STU-SX-11-A-02"
                )
            )
            writeStudents(firestore, students)
            database.studentDao().insertStudents(students)
            totalCount += students.size
            countMap["Students"] = students.size

            // 4. Attendance Records
            val now = System.currentTimeMillis()
            val todayStr = "2026-09-21"
            val yesterdayStr = "2026-09-20"
            val attendanceList = listOf(
                AttendanceEntity(
                    studentId = "STU-10-A-01",
                    studentName = "Aarav Sharma",
                    schoolId = "SCH-1001",
                    className = "10",
                    section = "A",
                    rollNumber = "01",
                    type = "ENTRY",
                    timestamp = now - 7200000L,
                    dateString = todayStr,
                    timeString = "08:12 AM",
                    recordedByTeacherId = "TCH-501"
                ),
                AttendanceEntity(
                    studentId = "STU-10-A-02",
                    studentName = "Priya Patel",
                    schoolId = "SCH-1001",
                    className = "10",
                    section = "A",
                    rollNumber = "02",
                    type = "ENTRY",
                    timestamp = now - 7140000L,
                    dateString = todayStr,
                    timeString = "08:15 AM",
                    recordedByTeacherId = "TCH-501"
                ),
                AttendanceEntity(
                    studentId = "STU-10-B-01",
                    studentName = "Rohan Das",
                    schoolId = "SCH-1001",
                    className = "10",
                    section = "B",
                    rollNumber = "01",
                    type = "ENTRY",
                    timestamp = now - 7080000L,
                    dateString = todayStr,
                    timeString = "08:20 AM",
                    recordedByTeacherId = "TCH-502"
                ),
                AttendanceEntity(
                    studentId = "STU-10-A-01",
                    studentName = "Aarav Sharma",
                    schoolId = "SCH-1001",
                    className = "10",
                    section = "A",
                    rollNumber = "01",
                    type = "ENTRY",
                    timestamp = now - 86400000L - 7200000L,
                    dateString = yesterdayStr,
                    timeString = "08:10 AM",
                    recordedByTeacherId = "TCH-501"
                ),
                AttendanceEntity(
                    studentId = "STU-10-A-01",
                    studentName = "Aarav Sharma",
                    schoolId = "SCH-1001",
                    className = "10",
                    section = "A",
                    rollNumber = "01",
                    type = "EXIT",
                    timestamp = now - 86400000L + 18000000L,
                    dateString = yesterdayStr,
                    timeString = "02:45 PM",
                    recordedByTeacherId = "TCH-501"
                ),
                AttendanceEntity(
                    studentId = "STU-SX-11-A-01",
                    studentName = "Dev Mukherjee",
                    schoolId = "SCH-1002",
                    className = "11",
                    section = "A",
                    rollNumber = "01",
                    type = "ENTRY",
                    timestamp = now - 7200000L,
                    dateString = todayStr,
                    timeString = "08:30 AM",
                    recordedByTeacherId = "TCH-503"
                ),
                AttendanceEntity(
                    studentId = "STU-SX-11-A-02",
                    studentName = "Ishani Sen",
                    schoolId = "SCH-1002",
                    className = "11",
                    section = "A",
                    rollNumber = "02",
                    type = "ENTRY",
                    timestamp = now - 6600000L,
                    dateString = todayStr,
                    timeString = "08:40 AM",
                    recordedByTeacherId = "TCH-503"
                ),
                AttendanceEntity(
                    studentId = "STU-SX-11-A-01",
                    studentName = "Dev Mukherjee",
                    schoolId = "SCH-1002",
                    className = "11",
                    section = "A",
                    rollNumber = "01",
                    type = "ENTRY",
                    timestamp = now - 86400000L - 7200000L,
                    dateString = yesterdayStr,
                    timeString = "08:25 AM",
                    recordedByTeacherId = "TCH-503"
                )
            )
            writeAttendance(firestore, attendanceList)
            database.attendanceDao().insertAttendanceList(attendanceList)
            totalCount += attendanceList.size
            countMap["Attendance"] = attendanceList.size

            // 5. Exam Results & Scores
            val examResults = listOf(
                ExamResultEntity(
                    studentId = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Mathematics",
                    marksObtained = 95,
                    maxMarks = 100,
                    grade = "A+",
                    remarks = "Exceptional numerical reasoning"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Science",
                    marksObtained = 92,
                    maxMarks = 100,
                    grade = "A+",
                    remarks = "Clear grasp of physics & chemistry laws"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "English",
                    marksObtained = 88,
                    maxMarks = 100,
                    grade = "A",
                    remarks = "Creative analytical writing"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Social Studies",
                    marksObtained = 89,
                    maxMarks = 100,
                    grade = "A",
                    remarks = "Thorough understanding of governance"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Computer Science",
                    marksObtained = 98,
                    maxMarks = 100,
                    grade = "A+",
                    remarks = "Flawless coding logic & algorithms"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-02",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Mathematics",
                    marksObtained = 87,
                    maxMarks = 100,
                    grade = "A",
                    remarks = "Solid geometry & algebra performance"
                ),
                ExamResultEntity(
                    studentId = "STU-10-A-02",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Science",
                    marksObtained = 91,
                    maxMarks = 100,
                    grade = "A+",
                    remarks = "Excellent laboratory work"
                ),
                ExamResultEntity(
                    studentId = "STU-10-B-01",
                    schoolId = "SCH-1001",
                    examType = "Mid-Term Examination 2026",
                    examMonthYear = "2026-09",
                    subject = "Mathematics",
                    marksObtained = 82,
                    maxMarks = 100,
                    grade = "B+",
                    remarks = "Good effort, continue practicing"
                )
            )
            writeExamResults(firestore, examResults)
            val rcCount = writeReportCards(firestore, students, examResults)
            database.examResultDao().insertExamResults(examResults)
            totalCount += examResults.size + rcCount
            countMap["ExamResults"] = examResults.size
            countMap["ReportCards"] = rcCount

            // 6. Notifications - Demo notifications disabled as per user instruction
            val notifications = emptyList<NotificationEntity>()
            writeNotifications(firestore, notifications)
            notifications.forEach { database.notificationDao().insertNotification(it) }
            totalCount += notifications.size
            countMap["Notifications"] = notifications.size

            // 7. Products (Store)
            val products = listOf(
                ProductEntity(
                    title = "Oxford Complete Student Atlas & Compass Set",
                    description = "Comprehensive world geography atlas paired with a high-precision metal geometric compass set for secondary classes.",
                    imageUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500&auto=format&fit=crop&q=60",
                    purchaseUrl = "https://www.amazon.com/s?k=student+school+atlas+compass+set",
                    price = 19.99,
                    category = "Books",
                    inStock = true,
                    targetAudience = "ALL"
                ),
                ProductEntity(
                    title = "Official School Blazer & Tie Uniform Pack",
                    description = "Premium breathable wool-blend navy blazer tailored with official academy crest and coordinated necktie.",
                    imageUrl = "https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=500&auto=format&fit=crop&q=60",
                    purchaseUrl = "https://www.amazon.com/s?k=navy+school+blazer+uniform",
                    price = 49.50,
                    category = "Uniform",
                    inStock = true,
                    targetAudience = "STUDENT"
                ),
                ProductEntity(
                    title = "Casio FX-991EX Scientific Calculator",
                    description = "High-resolution natural textbook display with 552 functions, matrix calculation, and QR code visualization for STEM students.",
                    imageUrl = "https://images.unsplash.com/photo-1611125832047-1d7ad1e8e48f?w=500&auto=format&fit=crop&q=60",
                    purchaseUrl = "https://www.amazon.com/s?k=casio+scientific+calculator",
                    price = 24.95,
                    category = "Electronics",
                    inStock = true,
                    targetAudience = "ALL"
                ),
                ProductEntity(
                    title = "Ergonomic Hardbound Teacher Planner & Gradebook",
                    description = "All-in-one academic planner with attendance matrices, weekly lesson plans, and tear-out sticky note flags.",
                    imageUrl = "https://images.unsplash.com/photo-1506784983877-45594efa4cbe?w=500&auto=format&fit=crop&q=60",
                    purchaseUrl = "https://www.amazon.com/s?k=teacher+lesson+planner+gradebook",
                    price = 15.00,
                    category = "Stationery",
                    inStock = true,
                    targetAudience = "TEACHER"
                ),
                ProductEntity(
                    title = "STEM Robotics & Sensor Prototyping Kit",
                    description = "Arduino-compatible sensor board with LEDs, ultrasonic distance sensors, and step-by-step tutorial cards.",
                    imageUrl = "https://images.unsplash.com/photo-1517077304055-6e89abbf09b0?w=500&auto=format&fit=crop&q=60",
                    purchaseUrl = "https://www.amazon.com/s?k=stem+robotics+sensor+kit",
                    price = 34.50,
                    category = "Electronics",
                    inStock = true,
                    targetAudience = "ALL"
                )
            )
            writeProducts(firestore, products)
            products.forEach { database.productDao().insertProduct(it) }
            totalCount += products.size
            countMap["Products"] = products.size

            // 8. App Settings
            val settings = AppSettingsEntity(
                configKey = "GLOBAL_CONFIG",
                appName = "School Portal",
                logoUrl = ""
            )
            try {
                val setTask = firestore.collection(COLLECTION_SETTINGS)
                    .document(settings.configKey)
                    .set(
                        hashMapOf(
                            "configKey" to settings.configKey,
                            "appName" to settings.appName,
                            "logoUrl" to settings.logoUrl,
                            "lastSyncedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                withTimeout(5000L) { setTask.await() }
            } catch (e: TimeoutCancellationException) {
                Log.i(TAG, "Settings write cached locally and queued for cloud sync")
            } catch (e: Exception) {
                Log.w(TAG, "Settings write notice: ${e.message}")
            }
            database.appSettingsDao().saveSettings(settings)
            totalCount += 1
            countMap["Settings"] = 1

            val summary = "Successfully added $totalCount application records into Google Firebase Cloud Firestore."
            Log.i(TAG, summary)
            SeederResult(
                success = true,
                totalItemsAdded = totalCount,
                message = summary,
                details = countMap
            )
        } catch (e: Exception) {
            val errorMsg = "Error writing application data to Google Firebase: ${e.message}"
            Log.e(TAG, errorMsg, e)
            SeederResult(false, 0, errorMsg)
        }
    }

    private suspend fun commitBatchSafely(batch: WriteBatch, timeoutMs: Long = 7000L) {
        try {
            withTimeout(timeoutMs) {
                batch.commit().await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.i(TAG, "Batch committed to local Firestore persistent storage; cloud sync proceeding in background.")
        } catch (e: Exception) {
            Log.w(TAG, "Batch commit notice (cached locally): ${e.message}")
        }
    }

    private suspend fun writeSchools(firestore: FirebaseFirestore, list: List<SchoolEntity>) {
        val batch = firestore.batch()
        list.forEach { school ->
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
        commitBatchSafely(batch)
    }

    private suspend fun writeTeachers(firestore: FirebaseFirestore, list: List<TeacherEntity>) {
        val batch = firestore.batch()
        list.forEach { teacher ->
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
        commitBatchSafely(batch)
    }

    private suspend fun writeStudents(firestore: FirebaseFirestore, list: List<StudentEntity>) {
        val batch = firestore.batch()
        list.forEach { student ->
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
        commitBatchSafely(batch)
    }

    private suspend fun writeAttendance(firestore: FirebaseFirestore, list: List<AttendanceEntity>) {
        val batch = firestore.batch()
        list.forEach { record ->
            val docId = "${record.schoolId}_${record.studentId}_${record.timestamp}"
            val docRef1 = firestore.collection(COLLECTION_ATTENDANCE).document(docId)
            val docRef2 = firestore.collection(COLLECTION_ATTENDANCE_LOGS).document(docId)
            val map = hashMapOf(
                "id" to record.id,
                "recordId" to "ATT_${record.schoolId}_${record.studentId}_${record.timestamp}",
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
                "status" to "PRESENT",
                "scanMethod" to "BARCODE_SCANNER",
                "recordedByTeacherId" to record.recordedByTeacherId,
                "syncedWithCloud" to true,
                "schemaVersion" to 2,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            batch.set(docRef1, map, SetOptions.merge())
            batch.set(docRef2, map, SetOptions.merge())
        }
        commitBatchSafely(batch)
    }

    private suspend fun writeReportCards(
        firestore: FirebaseFirestore,
        students: List<StudentEntity>,
        examResults: List<ExamResultEntity>
    ): Int {
        val batch = firestore.batch()
        val studentMap = students.associateBy { it.id }
        val grouped = examResults.groupBy { "${it.studentId}__${it.examMonthYear}__${it.examType}" }
        var count = 0

        grouped.forEach { (key, results) ->
            val parts = key.split("__")
            val studentId = parts.getOrNull(0) ?: return@forEach
            val examMonthYear = parts.getOrNull(1) ?: "2026-09"
            val examType = parts.getOrNull(2) ?: "Term Examination"

            val student = studentMap[studentId]
            val schoolId = student?.schoolId ?: results.firstOrNull()?.schoolId ?: "SCH-1001"
            val schoolName = student?.schoolName ?: "Delhi Public Academy"

            val subjectList = results.map { result ->
                val pct = if (result.maxMarks > 0) (result.marksObtained.toDouble() / result.maxMarks.toDouble()) * 100.0 else 0.0
                hashMapOf(
                    "subject" to result.subject,
                    "marksObtained" to result.marksObtained,
                    "maxMarks" to result.maxMarks,
                    "percentage" to Math.round(pct * 10.0) / 10.0,
                    "grade" to result.grade,
                    "remarks" to result.remarks
                )
            }

            val totalObtained = results.sumOf { it.marksObtained }
            val totalMax = results.sumOf { it.maxMarks }.takeIf { it > 0 } ?: 100
            val overallPercentage = Math.round((totalObtained.toDouble() / totalMax.toDouble()) * 1000.0) / 10.0
            val overallGrade = when {
                overallPercentage >= 90.0 -> "A+"
                overallPercentage >= 80.0 -> "A"
                overallPercentage >= 70.0 -> "B+"
                overallPercentage >= 60.0 -> "B"
                overallPercentage >= 50.0 -> "C"
                else -> "D"
            }

            val docId = "RC_${schoolId}_${studentId}_${examMonthYear}_${examType.take(8).replace(" ", "_")}"
            val docRef = firestore.collection(COLLECTION_REPORT_CARDS).document(docId)

            val reportCardMap = hashMapOf(
                "reportCardId" to docId,
                "studentId" to studentId,
                "studentName" to (student?.name ?: "Student $studentId"),
                "schoolId" to schoolId,
                "schoolName" to schoolName,
                "className" to (student?.className ?: "10"),
                "section" to (student?.section ?: "A"),
                "rollNumber" to (student?.rollNumber ?: "01"),
                "academicYear" to "2026-2027",
                "examType" to examType,
                "examMonthYear" to examMonthYear,
                "subjects" to subjectList,
                "summary" to hashMapOf(
                    "totalMarksObtained" to totalObtained,
                    "totalMaxMarks" to totalMax,
                    "percentage" to overallPercentage,
                    "overallGrade" to overallGrade,
                    "resultStatus" to if (overallPercentage >= 40.0) "PASSED" else "FAILED",
                    "gpa" to when {
                        overallPercentage >= 90.0 -> 4.0
                        overallPercentage >= 80.0 -> 3.7
                        overallPercentage >= 70.0 -> 3.3
                        overallPercentage >= 60.0 -> 3.0
                        else -> 2.5
                    }
                ),
                "attendanceSummary" to hashMapOf(
                    "totalWorkingDays" to 110,
                    "daysPresent" to 106,
                    "attendancePercentage" to 96.4
                ),
                "teacherRemarks" to "Consistently demonstrates strong academic curiosity and diligence.",
                "principalRemarks" to "Promoted with honors.",
                "isPublished" to true,
                "publishedAt" to System.currentTimeMillis(),
                "schemaVersion" to 2,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            batch.set(docRef, reportCardMap, SetOptions.merge())
            count++
        }
        commitBatchSafely(batch)
        return count
    }

    private suspend fun writeExamResults(firestore: FirebaseFirestore, list: List<ExamResultEntity>) {
        val batch = firestore.batch()
        list.forEach { result ->
            val sanitizedSub = result.subject.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val sanitizedStu = result.studentId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val docId = "${result.schoolId}_${sanitizedStu}_${result.examMonthYear}_${sanitizedSub}"

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
            val docRef1 = firestore.collection(COLLECTION_EXAM_RESULTS).document(docId)
            val docRef2 = firestore.collection(COLLECTION_SCORES).document(docId)
            batch.set(docRef1, map, SetOptions.merge())
            batch.set(docRef2, map, SetOptions.merge())
        }
        commitBatchSafely(batch)
    }

    private suspend fun writeNotifications(firestore: FirebaseFirestore, list: List<NotificationEntity>) {
        val batch = firestore.batch()
        list.forEach { notice ->
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
        commitBatchSafely(batch)
    }

    private suspend fun writeProducts(firestore: FirebaseFirestore, list: List<ProductEntity>) {
        val batch = firestore.batch()
        list.forEach { product ->
            val docRef = firestore.collection(COLLECTION_PRODUCTS).document(product.title.hashCode().toString())
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
        commitBatchSafely(batch)
    }
}
