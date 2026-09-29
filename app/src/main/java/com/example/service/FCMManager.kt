package com.example.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.firebase.FirebaseConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Manages Firebase Cloud Messaging device tokens, subscriptions, and permission status.
 * Safely guards against hard FCM registration failures when Google Play Services is
 * unavailable or restricted (e.g., in emulator/headless test environments).
 */
object FCMManager {
    private const val TAG = "FCMManager"
    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_CACHED_TOKEN = "cached_fcm_token"
    private const val KEY_LAST_SYNCED_USER = "last_synced_user"
    private const val KEY_IS_REAL_FCM = "is_real_fcm_token"

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentToken = MutableStateFlow<String?>(null)
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    private val _isTokenSynced = MutableStateFlow(false)
    val isTokenSynced: StateFlow<Boolean> = _isTokenSynced.asStateFlow()

    /**
     * Checks whether Google Play Services is available and functional on this device.
     */
    fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Throwable) {
            Log.w(TAG, "Google Play Services availability check error: ${e.message}")
            false
        }
    }

    /**
     * Obtains or generates a stable local fallback token for device registration
     * when FCM registration cannot be fulfilled on the hardware/runtime.
     */
    fun getOrCreateFallbackToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CACHED_TOKEN, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = "DEV_DEVICE_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        prefs.edit()
            .putString(KEY_CACHED_TOKEN, generated)
            .putBoolean(KEY_IS_REAL_FCM, false)
            .apply()
        return generated
    }

    /**
     * Initializes FCM, creates notification channels, and safely attempts device token retrieval.
     */
    fun initialize(context: Context) {
        NotificationHelper.createNotificationChannel(context)

        // Read cached token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_TOKEN, null)
        if (!cached.isNullOrBlank()) {
            _currentToken.value = cached
        }

        val hasPlayServices = isPlayServicesAvailable(context)
        if (!hasPlayServices) {
            Log.i(TAG, "Google Play Services is not available on this runtime. Using local device token.")
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
            } catch (e: Throwable) {
                Log.d(TAG, "Could not set isAutoInitEnabled: ${e.message}")
            }
            if (_currentToken.value.isNullOrBlank()) {
                val fallback = getOrCreateFallbackToken(context)
                _currentToken.value = fallback
            }
            return
        }

        // Google Play Services is available: safely attempt FCM token retrieval
        try {
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Fetching FCM registration token unsuccessful: ${task.exception?.message}")
                        try {
                            // Turn off auto-init if registration fails with hard exception
                            FirebaseMessaging.getInstance().isAutoInitEnabled = false
                        } catch (_: Throwable) {}
                        if (_currentToken.value.isNullOrBlank()) {
                            val fallback = getOrCreateFallbackToken(context)
                            _currentToken.value = fallback
                        }
                        return@addOnCompleteListener
                    }

                    val token = task.result
                    if (!token.isNullOrBlank()) {
                        Log.d(TAG, "FCM Device Token retrieved successfully: $token")
                        _currentToken.value = token
                        prefs.edit()
                            .putString(KEY_CACHED_TOKEN, token)
                            .putBoolean(KEY_IS_REAL_FCM, true)
                            .apply()
                    }
                }
        } catch (e: Throwable) {
            Log.w(TAG, "FCM initialization handled exception: ${e.message}")
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
            } catch (_: Throwable) {}
            if (_currentToken.value.isNullOrBlank()) {
                val fallback = getOrCreateFallbackToken(context)
                _currentToken.value = fallback
            }
        }
    }

    /**
     * Called by SchoolFirebaseMessagingService when a new token is generated.
     */
    fun onTokenRefreshed(context: Context, newToken: String) {
        if (newToken.isBlank()) return
        _currentToken.value = newToken
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_TOKEN, newToken)
            .putBoolean(KEY_IS_REAL_FCM, true)
            .apply()

        val lastUser = prefs.getString(KEY_LAST_SYNCED_USER, null)
        if (!lastUser.isNullOrBlank()) {
            syncTokenToFirestore(
                context = context,
                userId = lastUser,
                role = "UNKNOWN",
                schoolId = ""
            )
        }
    }

    /**
     * Synchronizes the user's FCM / device token to Google Cloud Firestore collections:
     * 1. `fcm_tokens/{userId}` (Central index for server-side Cloud Function dispatching)
     * 2. `students/{userId}` or `teachers/{userId}` document fields
     */
    fun syncTokenToFirestore(
        context: Context,
        userId: String,
        role: String,
        schoolId: String,
        className: String = ""
    ) {
        if (userId.isBlank()) return

        managerScope.launch {
            try {
                // Ensure token is available (either real FCM token or stable fallback device identifier)
                var token = _currentToken.value

                if (token.isNullOrBlank() && isPlayServicesAvailable(context)) {
                    token = try {
                        FirebaseMessaging.getInstance().token.await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not await FCM token: ${e.message}")
                        null
                    }
                }

                if (token.isNullOrBlank()) {
                    token = getOrCreateFallbackToken(context)
                }

                _currentToken.value = token
                val isRealFcm = !token.startsWith("DEV_DEVICE_")

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_CACHED_TOKEN, token)
                    .putString(KEY_LAST_SYNCED_USER, userId)
                    .putBoolean(KEY_IS_REAL_FCM, isRealFcm)
                    .apply()

                val firestore = FirebaseConfig.getFirestore(context)
                if (firestore == null) {
                    Log.w(TAG, "Firestore unavailable, cannot sync token")
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val tokenPayload = hashMapOf(
                    "userId" to userId,
                    "role" to role,
                    "schoolId" to schoolId,
                    "className" to className,
                    "fcmToken" to token,
                    "isRealFcmToken" to isRealFcm,
                    "platform" to "android",
                    "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "osVersion" to Build.VERSION.SDK_INT,
                    "updatedAt" to timestamp
                )

                // 1. Write to central fcm_tokens collection
                firestore.collection("fcm_tokens")
                    .document(userId)
                    .set(tokenPayload, SetOptions.merge())
                    .await()

                // 2. Also update student / teacher document directly for fast single-document lookup
                if (role.equals("STUDENT", ignoreCase = true)) {
                    firestore.collection("students")
                        .document(userId)
                        .set(
                            mapOf("fcmToken" to token, "tokenUpdatedAt" to timestamp),
                            SetOptions.merge()
                        )
                        .await()

                    // Only subscribe to FCM topics if it is a real FCM token and Play Services is active
                    if (isRealFcm && isPlayServicesAvailable(context)) {
                        try {
                            if (className.isNotBlank()) {
                                val sanitizedClass = className.replace(" ", "_").lowercase()
                                FirebaseMessaging.getInstance().subscribeToTopic("class_$sanitizedClass")
                            }
                            FirebaseMessaging.getInstance().subscribeToTopic("all_students")
                            if (schoolId.isNotBlank()) {
                                FirebaseMessaging.getInstance().subscribeToTopic("school_${schoolId.lowercase()}_students")
                            }
                        } catch (topicEx: Throwable) {
                            Log.w(TAG, "Topic subscription skipped: ${topicEx.message}")
                        }
                    }
                } else if (role.equals("TEACHER", ignoreCase = true)) {
                    firestore.collection("teachers")
                        .document(userId)
                        .set(
                            mapOf("fcmToken" to token, "tokenUpdatedAt" to timestamp),
                            SetOptions.merge()
                        )
                        .await()

                    if (isRealFcm && isPlayServicesAvailable(context)) {
                        try {
                            FirebaseMessaging.getInstance().subscribeToTopic("all_teachers")
                            if (schoolId.isNotBlank()) {
                                FirebaseMessaging.getInstance().subscribeToTopic("school_${schoolId.lowercase()}_teachers")
                            }
                        } catch (topicEx: Throwable) {
                            Log.w(TAG, "Topic subscription skipped: ${topicEx.message}")
                        }
                    }
                }

                _isTokenSynced.value = true
                Log.d(TAG, "Successfully registered device token for $userId ($role) in Firestore (isRealFcm=$isRealFcm)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync device token to Firestore: ${e.message}")
                _isTokenSynced.value = false
            }
        }
    }
}
