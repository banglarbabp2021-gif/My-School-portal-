package com.example.data.auth

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.StudentEntity
import com.example.data.model.TeacherEntity
import com.example.data.session.UserSessionState
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthResult {
    data class Success(val session: UserSessionState, val firebaseUser: FirebaseUser?) : AuthResult()
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult()
}

/**
 * Firebase Authentication service providing separate authentication flows for:
 *  - Admin (System Administrator)
 *  - Teacher (School-scoped Educator)
 *  - Student (Individual Scholar)
 */
class FirebaseAuthService(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "FirebaseAuthService"

    private fun getFirebaseAuth(): FirebaseAuth? {
        return com.example.data.firebase.FirebaseConfig.getAuth(context)
    }

    private fun getFirestore(): FirebaseFirestore? {
        return com.example.data.firebase.FirebaseConfig.getFirestore(context)
    }

    val currentUser: FirebaseUser?
        get() = getFirebaseAuth()?.currentUser

    // =========================================================================
    // 1. ADMIN AUTHENTICATION
    // =========================================================================
    suspend fun authenticateAdmin(adminId: String, pass: String): AuthResult = withContext(Dispatchers.IO) {
        val trimmedId = adminId.trim()
        val validDefaultAdmin = (trimmedId.equals("banglarbabp2021@gmail.com", ignoreCase = true) && pass == "Sourav#1997Soma@1998")

        val authEmail = if (trimmedId.contains("@")) {
            trimmedId.lowercase()
        } else {
            "${trimmedId.lowercase()}@schoolportal.com"
        }
        val authPassword = if (pass.length >= 6) pass else "${pass}#admin"

        val auth = getFirebaseAuth()
        val firestore = getFirestore()
        var firebaseUser: FirebaseUser? = null

        if (auth != null) {
            try {
                if (validDefaultAdmin) {
                    try {
                        val signInResult = auth.signInWithEmailAndPassword(authEmail, authPassword).await()
                        firebaseUser = signInResult.user
                    } catch (e: Exception) {
                        Log.i(TAG, "Default admin email auth step noted (${e.message}). Ensuring Firebase session via anonymous sign-in.")
                        if (auth.currentUser == null) {
                            try {
                                val anonResult = auth.signInAnonymously().await()
                                firebaseUser = anonResult.user
                            } catch (ae: Exception) {
                                Log.w(TAG, "Anonymous fallback notice: ${ae.message}")
                            }
                        } else {
                            firebaseUser = auth.currentUser
                        }
                    }

                    // Record / verify admin document in Firestore
                    if (firestore != null) {
                        try {
                            val adminDoc = firestore.collection("admins").document(trimmedId)
                            adminDoc.set(
                                hashMapOf(
                                    "adminId" to trimmedId,
                                    "email" to authEmail,
                                    "role" to "ADMIN",
                                    "name" to "Administrator",
                                    "lastLoginAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            ).await()
                        } catch (fe: Exception) {
                            Log.w(TAG, "Firestore admin doc set notice: ${fe.message}")
                        }
                    }
                } else if (firestore != null) {
                    try {
                        val doc = firestore.collection("admins").document(trimmedId).get().await()
                        if (doc.exists() && doc.getString("password") == pass) {
                            if (auth.currentUser == null) {
                                try {
                                    val anonResult = auth.signInAnonymously().await()
                                    firebaseUser = anonResult.user
                                } catch (_: Exception) {}
                            } else {
                                firebaseUser = auth.currentUser
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Admin Auth network step: ${e.message}")
            }
        }

        // Validate admin credentials
        if (validDefaultAdmin || firebaseUser != null) {
            val session = UserSessionState(
                role = "ADMIN",
                userId = "banglarbabp2021@gmail.com",
                userName = "Administrator"
            )
            AuthResult.Success(session, firebaseUser ?: auth?.currentUser)
        } else {
            AuthResult.Error("ভুল ইউজার আইডি বা পাসওয়ার্ড")
        }
    }

    // =========================================================================
    // 2. TEACHER AUTHENTICATION (Restricted to Teacher's School)
    // =========================================================================
    suspend fun authenticateTeacher(
        schoolQuery: String,
        teacherIdOrName: String,
        pass: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val trimmedSchool = schoolQuery.trim()
        val trimmedIdOrName = teacherIdOrName.trim()
        val firestore = getFirestore()
        val auth = getFirebaseAuth()

        var teacher: TeacherEntity? = null

        // 1. Query Firestore first for teacher record
        if (firestore != null) {
            try {
                val teachersRef = firestore.collection("teachers")
                val querySnapshot = teachersRef
                    .whereEqualTo("password", pass)
                    .get()
                    .await()

                for (doc in querySnapshot.documents) {
                    val docId = doc.getString("id") ?: doc.id
                    val docSchoolId = doc.getString("schoolId") ?: ""
                    val docSchoolName = doc.getString("schoolName") ?: ""
                    val docName = doc.getString("name") ?: ""

                    val matchesSchool = docSchoolId.equals(trimmedSchool, ignoreCase = true) ||
                            docSchoolName.equals(trimmedSchool, ignoreCase = true)
                    val matchesTeacher = docId.equals(trimmedIdOrName, ignoreCase = true) ||
                            docName.equals(trimmedIdOrName, ignoreCase = true)

                    if (matchesSchool && matchesTeacher) {
                        teacher = TeacherEntity(
                            id = docId,
                            schoolId = docSchoolId,
                            schoolName = docSchoolName,
                            name = docName,
                            email = doc.getString("email") ?: "",
                            mobile = doc.getString("mobile") ?: "",
                            password = pass,
                            subject = doc.getString("subject") ?: "General",
                            assignedClass = doc.getString("assignedClass") ?: "Class 10"
                        )
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore teacher lookup failed: ${e.message}")
            }
        }

        // 2. Fallback to local Room database if Firestore is offline or didn't return
        if (teacher == null) {
            teacher = database.teacherDao().loginTeacher(trimmedSchool, trimmedIdOrName, pass)
        }

        if (teacher == null) {
            return@withContext AuthResult.Error("Teacher login failed. Check School, Teacher ID, and Password.")
        }

        // 3. Authenticate with Firebase Auth
        var firebaseUser: FirebaseUser? = null
        if (auth != null) {
            val teacherEmail = if (teacher.email.isNotBlank() && teacher.email.contains("@")) {
                teacher.email.lowercase()
            } else {
                "teacher_${teacher.id.lowercase().replace("-", "_")}@schoolportal.com"
            }
            val authPass = if (pass.length >= 6) pass else "${pass}#teacher"

            try {
                try {
                    val signInResult = auth.signInWithEmailAndPassword(teacherEmail, authPass).await()
                    firebaseUser = signInResult.user
                } catch (e: Exception) {
                    if (auth.currentUser == null) {
                        try {
                            val anonResult = auth.signInAnonymously().await()
                            firebaseUser = anonResult.user
                        } catch (ae: Exception) {
                            Log.w(TAG, "Anonymous auth for teacher notice: ${ae.message}")
                        }
                    } else {
                        firebaseUser = auth.currentUser
                    }
                }

                // Update Firestore teacher document with login timestamp
                firestore?.collection("teachers")?.document(teacher.id)?.set(
                    hashMapOf(
                        "id" to teacher.id,
                        "schoolId" to teacher.schoolId,
                        "schoolName" to teacher.schoolName,
                        "name" to teacher.name,
                        "email" to teacherEmail,
                        "subject" to teacher.subject,
                        "assignedClass" to teacher.assignedClass,
                        "lastLoginAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )?.await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Auth for teacher notice: ${e.message}")
            }
        }

        val session = UserSessionState(
            role = "TEACHER",
            userId = teacher.id,
            schoolId = teacher.schoolId,
            schoolName = teacher.schoolName,
            userName = teacher.name,
            userClass = teacher.assignedClass
        )
        AuthResult.Success(session, firebaseUser ?: auth?.currentUser)
    }

    // =========================================================================
    // 3. STUDENT AUTHENTICATION (By Class & Roll or Barcode ID)
    // =========================================================================
    suspend fun authenticateStudentByRoll(
        schoolQuery: String,
        className: String,
        section: String,
        rollNumber: String,
        pass: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val trimmedSchool = schoolQuery.trim()
        val trimmedClass = className.trim()
        val trimmedSection = section.trim()
        val trimmedRoll = rollNumber.trim()
        val firestore = getFirestore()

        var student: StudentEntity? = null

        // 1. Check Firestore first
        if (firestore != null) {
            try {
                val snapshot = firestore.collection("students")
                    .whereEqualTo("className", trimmedClass)
                    .whereEqualTo("section", trimmedSection)
                    .whereEqualTo("rollNumber", trimmedRoll)
                    .whereEqualTo("password", pass)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val docSchoolId = doc.getString("schoolId") ?: ""
                    val docSchoolName = doc.getString("schoolName") ?: ""
                    val matchesSchool = docSchoolId.equals(trimmedSchool, ignoreCase = true) ||
                            docSchoolName.equals(trimmedSchool, ignoreCase = true)

                    if (matchesSchool) {
                        student = parseStudentDoc(doc, pass)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore student by roll query failed: ${e.message}")
            }
        }

        // 2. Fallback to local Room database
        if (student == null) {
            student = database.studentDao().loginStudent(
                schoolQuery = trimmedSchool,
                className = trimmedClass,
                section = trimmedSection,
                rollNumber = trimmedRoll,
                password = pass
            )
        }

        if (student == null) {
            return@withContext AuthResult.Error("Student login failed. Check class, section, roll number, and password.")
        }

        finishStudentAuth(student, pass)
    }

    suspend fun authenticateStudentByBarcode(
        barcodeId: String,
        pass: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val trimmedId = barcodeId.trim()
        val firestore = getFirestore()

        var student: StudentEntity? = null

        // 1. Check Firestore
        if (firestore != null) {
            try {
                val doc = firestore.collection("students").document(trimmedId).get().await()
                if (doc.exists() && doc.getString("password") == pass) {
                    student = parseStudentDoc(doc, pass)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore student by barcode query failed: ${e.message}")
            }
        }

        // 2. Fallback to local Room database
        if (student == null) {
            student = database.studentDao().loginStudentByBarcodeId(trimmedId, pass)
        }

        if (student == null) {
            return@withContext AuthResult.Error("Invalid Barcode ID or Password.")
        }

        finishStudentAuth(student, pass)
    }

    private suspend fun finishStudentAuth(student: StudentEntity, pass: String): AuthResult {
        val auth = getFirebaseAuth()
        val firestore = getFirestore()
        var firebaseUser: FirebaseUser? = null

        if (auth != null) {
            val studentEmail = "student_${student.id.lowercase().replace("-", "_")}@schoolportal.com"
            val authPass = if (pass.length >= 6) pass else "${pass}#student"
            try {
                try {
                    val signInResult = auth.signInWithEmailAndPassword(studentEmail, authPass).await()
                    firebaseUser = signInResult.user
                } catch (e: Exception) {
                    if (auth.currentUser == null) {
                        try {
                            val anonResult = auth.signInAnonymously().await()
                            firebaseUser = anonResult.user
                        } catch (ae: Exception) {
                            Log.w(TAG, "Anonymous auth for student notice: ${ae.message}")
                        }
                    } else {
                        firebaseUser = auth.currentUser
                    }
                }

                firestore?.collection("students")?.document(student.id)?.set(
                    hashMapOf(
                        "lastLoginAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )?.await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Auth student step notice: ${e.message}")
            }
        }

        val session = UserSessionState(
            role = "STUDENT",
            userId = student.id,
            schoolId = student.schoolId,
            schoolName = student.schoolName,
            userName = student.name,
            userClass = student.className,
            userSection = student.section,
            userRoll = student.rollNumber
        )
        return AuthResult.Success(session, firebaseUser ?: auth?.currentUser)
    }

    private fun parseStudentDoc(doc: com.google.firebase.firestore.DocumentSnapshot, pass: String): StudentEntity {
        val id = doc.getString("id") ?: doc.id
        return StudentEntity(
            id = id,
            schoolId = doc.getString("schoolId") ?: "",
            schoolName = doc.getString("schoolName") ?: "",
            name = doc.getString("name") ?: "",
            className = doc.getString("className") ?: "",
            section = doc.getString("section") ?: "",
            rollNumber = doc.getString("rollNumber") ?: "",
            fatherName = doc.getString("fatherName") ?: "",
            motherName = doc.getString("motherName") ?: "",
            parentMobile = doc.getString("parentMobile") ?: "",
            studentMobile = doc.getString("studentMobile") ?: "",
            password = doc.getString("password") ?: pass,
            gender = doc.getString("gender") ?: "General",
            dateOfBirth = doc.getString("dateOfBirth") ?: "",
            casteCategory = doc.getString("casteCategory") ?: "General",
            bloodGroup = doc.getString("bloodGroup") ?: "O+",
            photoUri = doc.getString("photoUri") ?: "",
            barcodeData = doc.getString("barcodeData") ?: id,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    fun signOut() {
        try {
            getFirebaseAuth()?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out from FirebaseAuth: ${e.message}")
        }
    }
}
