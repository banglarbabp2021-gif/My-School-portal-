package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Centralized Firebase configuration and instance provider.
 * Connects directly to the Google Firebase project: school-portal-3a479
 * using credentials matching google-services.json.
 */
object FirebaseConfig {
    private const val TAG = "FirebaseConfig"

    const val PROJECT_ID = "school-portal-3a479"
    const val APPLICATION_ID = "1:1046356217509:android:5d2bb13712428610e2c74c"
    const val API_KEY = "AIzaSyDNim2hgi3b_2X_ME-EgmwrQDiU3FVYm00"
    const val STORAGE_BUCKET = "school-portal-3a479.firebasestorage.app"
    const val DATABASE_URL = "https://school-portal-3a479-default-rtdb.firebaseio.com"
    const val GCM_SENDER_ID = "1046356217509"

    @Volatile
    private var firestoreConfigured = false

    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(APPLICATION_ID)
                    .setApiKey(API_KEY)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .setDatabaseUrl(DATABASE_URL)
                    .setGcmSenderId(GCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.i(TAG, "FirebaseApp successfully initialized with FirebaseOptions for $PROJECT_ID")
            } else {
                val app = FirebaseApp.getInstance()
                Log.i(TAG, "Active FirebaseApp: ${app.name}, projectId: ${app.options.projectId}")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Primary FirebaseApp initialization error: ${e.message}")
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context.applicationContext)
                }
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback FirebaseApp init error: ${e2.message}")
                false
            }
        }
    }

    fun getFirestore(context: Context): FirebaseFirestore? {
        ensureInitialized(context)
        return try {
            val firestore = FirebaseFirestore.getInstance()
            if (!firestoreConfigured) {
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestore.firestoreSettings = settings
                    firestoreConfigured = true
                    Log.i(TAG, "FirebaseFirestore persistent cache settings successfully applied.")
                } catch (e: Exception) {
                    // Settings might have already been initialized
                    firestoreConfigured = true
                    Log.d(TAG, "Firestore settings already active: ${e.message}")
                }
            }
            firestore
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire FirebaseFirestore: ${e.message}")
            null
        }
    }

    fun getAuth(context: Context): FirebaseAuth? {
        ensureInitialized(context)
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire FirebaseAuth: ${e.message}")
            null
        }
    }

    /**
     * Ensures that Firebase Auth has an active session (e.g. anonymous or user).
     * This ensures that Firestore security rules requiring `request.auth != null`
     * are satisfied even when testing or before explicit login.
     */
    suspend fun ensureAuthenticated(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val auth = getAuth(context) ?: return@withContext false
            if (auth.currentUser != null) {
                return@withContext true
            }

            // Attempt lightweight anonymous session if allowed
            try {
                auth.signInAnonymously().await()
                Log.i(TAG, "Firebase Auth anonymous session active (UID: ${auth.currentUser?.uid})")
                return@withContext true
            } catch (anonErr: Exception) {
                Log.d(TAG, "Anonymous auth not enabled or unavailable: ${anonErr.message}")
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "Authentication check notice: ${e.message}")
            false
        }
    }
}
