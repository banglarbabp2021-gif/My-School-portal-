package com.example.service

import android.util.Log
import com.example.data.firebase.FirebaseConfig
import com.example.data.local.AppDatabase
import com.example.data.model.NotificationEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Service that handles Firebase Cloud Messaging (FCM) push notification events:
 *  - Token generation & refresh (onNewToken)
 *  - Payload delivery in foreground, background, and closed app states (onMessageReceived)
 */
class SchoolFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Device Token: $token")
        // Broadcast token update or persist in preferences
        FCMManager.onTokenRefreshed(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // 1. Extract data payload (preferred for custom deep-linking and state)
        val data = remoteMessage.data

        val notificationTitle = remoteMessage.notification?.title
            ?: data["title"]
            ?: "School Notice"

        val notificationBody = remoteMessage.notification?.body
            ?: data["body"]
            ?: data["message"]
            ?: "You have received a new update from the school."

        val notificationId = data["notificationId"]
            ?: data["id"]
            ?: UUID.randomUUID().toString()

        val recipientUserId = data["recipientUserId"] ?: ""
        val targetType = data["targetType"] ?: "ALL_STUDENTS"
        val targetId = data["targetId"] ?: ""
        val schoolId = data["schoolId"] ?: ""
        val senderName = data["senderName"] ?: "School Administration"
        val senderTeacherId = data["senderTeacherId"] ?: ""
        val deepLinkType = data["deepLinkType"] ?: NotificationHelper.DEEP_LINK_NOTICE
        val deepLinkId = data["deepLinkId"] ?: targetId
        val timestamp = data["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis()

        Log.d(TAG, "Extracted notification: id=$notificationId, title=$notificationTitle, linkType=$deepLinkType")

        // 2. Persist notification into local Room database for offline history & read status
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entity = NotificationEntity(
                    schoolId = schoolId,
                    senderTeacherId = senderTeacherId,
                    senderName = senderName,
                    targetType = targetType,
                    targetStudentId = if (targetType == "STUDENT") targetId else null,
                    targetClassName = if (targetType == "CLASS") targetId else null,
                    title = notificationTitle,
                    message = notificationBody,
                    notificationId = notificationId,
                    body = notificationBody,
                    recipientUserId = recipientUserId,
                    targetId = targetId,
                    createdAt = timestamp,
                    timestamp = timestamp,
                    readStatus = false,
                    deepLinkType = deepLinkType,
                    deepLinkId = deepLinkId
                )
                db.notificationDao().insertNotification(entity)
                Log.d(TAG, "Saved notification $notificationId into local Room database")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache notification locally: ${e.message}")
            }
        }

        // 3. Selective heads-up notification display:
        // - Teacher to Student: Persisted into Room DB for In-App Bell icon only (no system push popup)
        // - Admin to Teacher: System tray Push Notification with sound, vibration, and deep-link
        val isTeacherSender = senderTeacherId.isNotBlank() && !senderTeacherId.startsWith("ADMIN", ignoreCase = true)
        val isStudentTarget = targetType == "STUDENT" || targetType == "CLASS" || targetType == "ALL_STUDENTS" || targetType == "ALL_SCHOOL"
        val isTeacherToStudent = isTeacherSender && isStudentTarget

        val isAdminToTeacher = (senderTeacherId.startsWith("ADMIN", ignoreCase = true) || senderName.contains("Admin", ignoreCase = true)) &&
                (targetType.contains("TEACHER", ignoreCase = true) || recipientUserId.startsWith("TCH", ignoreCase = true))

        if (isAdminToTeacher) {
            NotificationHelper.showNotification(
                context = applicationContext,
                notificationId = notificationId,
                title = notificationTitle,
                body = notificationBody,
                deepLinkType = deepLinkType,
                deepLinkId = deepLinkId,
                senderName = senderName
            )
        } else if (isTeacherToStudent) {
            Log.d(TAG, "Notice $notificationId is Teacher-to-Student: Stored for In-App Bell icon only, skipping push popup")
        } else if (senderTeacherId.startsWith("ADMIN", ignoreCase = true) || senderName.contains("Admin", ignoreCase = true)) {
            NotificationHelper.showNotification(
                context = applicationContext,
                notificationId = notificationId,
                title = notificationTitle,
                body = notificationBody,
                deepLinkType = deepLinkType,
                deepLinkId = deepLinkId,
                senderName = senderName
            )
        }
    }

    companion object {
        private const val TAG = "SchoolFCMService"
    }
}
