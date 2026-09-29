package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SchoolEntity::class,
        TeacherEntity::class,
        StudentEntity::class,
        AttendanceEntity::class,
        TeacherAttendanceEntity::class,
        ExamResultEntity::class,
        NotificationEntity::class,
        ProductEntity::class,
        AppSettingsEntity::class,
        SearchHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao
    abstract fun teacherDao(): TeacherDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun teacherAttendanceDao(): TeacherAttendanceDao
    abstract fun examResultDao(): ExamResultDao
    abstract fun notificationDao(): NotificationDao
    abstract fun productDao(): ProductDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "school_portal_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Clean install: No demo accounts or dummy schools are pre-populated.
                            // All data is manually registered and maintained by users.
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun clearAllDemoAccounts(db: AppDatabase) {
            try {
                db.schoolDao().deleteDemoSchools()
                db.teacherDao().deleteDemoTeachers()
                db.studentDao().deleteDemoStudents()
                db.attendanceDao().deleteDemoAttendance()
                db.examResultDao().deleteDemoExamResults()
                db.notificationDao().deleteDemoNotifications()
                db.productDao().deleteDemoProducts()
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "Error clearing demo accounts: ${e.message}")
            }
        }
    }
}
