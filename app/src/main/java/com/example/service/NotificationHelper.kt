package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

/**
 * Helper class for creating and displaying high-priority heads-up push notifications
 * with custom sound, vibration, badges, and deep-linking into specific app screens.
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"

    // Thread-safe set of already shown notification IDs to prevent duplicate system push notifications
    private val shownNotificationIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    const val CHANNEL_ID = "school_notifications_channel"
    const val CHANNEL_NAME = "School Notifications & Alerts"
    const val CHANNEL_DESC = "Real-time notices, exam results, and student message alerts"

    // Deep link keys passed to MainActivity
    const val EXTRA_DEEP_LINK_TYPE = "extra_deep_link_type"
    const val EXTRA_DEEP_LINK_ID = "extra_deep_link_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_NOTIFICATION_TITLE = "extra_notification_title"
    const val EXTRA_NOTIFICATION_BODY = "extra_notification_body"

    // Deep link destination types
    const val DEEP_LINK_NOTICE = "NOTICE"
    const val DEEP_LINK_EXAM_RESULT = "EXAM_RESULT"
    const val DEEP_LINK_STUDENT_MESSAGE = "STUDENT_MESSAGE"

    /**
     * Initializes the high-priority notification channel on Android 8.0+ (API 26+).
     * Configured for heads-up alert banner, sound, and vibration pattern.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    /**
     * Displays a heads-up system tray notification with deep link PendingIntent.
     */
    fun showNotification(
        context: Context,
        notificationId: String,
        title: String,
        body: String,
        deepLinkType: String = DEEP_LINK_NOTICE,
        deepLinkId: String = "",
        numericId: Int = 0,
        senderName: String? = null
    ) {
        try {
            // Prevent pushing the exact same notification ID multiple times
            if (notificationId.isNotBlank()) {
                if (!shownNotificationIds.add(notificationId)) {
                    Log.d(TAG, "Notification $notificationId was already displayed as push alert. Skipping duplicate.")
                    return
                }
                // Cap cache size to avoid unbounded memory growth
                if (shownNotificationIds.size > 300) {
                    val it = shownNotificationIds.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }

            // Deterministic numeric ID based on notification ID to prevent duplicate tray entries
            val safeNumericId = if (numericId != 0) {
                numericId
            } else if (notificationId.isNotBlank()) {
                Math.abs(notificationId.hashCode() % 100000)
            } else {
                (System.currentTimeMillis() % 100000).toInt()
            }

            // Ensure channel exists
            createNotificationChannel(context)

            // Intent with deep link payload for MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_DEEP_LINK_TYPE, deepLinkType)
                putExtra(EXTRA_DEEP_LINK_ID, deepLinkId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_NOTIFICATION_TITLE, title)
                putExtra(EXTRA_NOTIFICATION_BODY, body)
                if (!senderName.isNullOrBlank()) {
                    putExtra("EXTRA_SENDER_NAME", senderName)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                safeNumericId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 300, 150, 300))
                .setContentIntent(pendingIntent)

            if (!senderName.isNullOrBlank()) {
                builder.setSubText(senderName)
            }

            val managerCompat = NotificationManagerCompat.from(context)
            // Verify permission check safely before notify
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                managerCompat.notify(safeNumericId, builder.build())
                Log.d(TAG, "Notification displayed successfully: ID=$safeNumericId, Title=$title")
            } else {
                Log.w(TAG, "Cannot show notification: POST_NOTIFICATIONS permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display notification: ${e.message}", e)
        }
    }
}
