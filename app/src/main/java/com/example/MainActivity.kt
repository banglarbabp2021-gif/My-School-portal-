package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.service.FCMManager
import com.example.service.NotificationHelper
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SchoolViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SchoolViewModel by viewModels()
    private var pendingDeepLinkType by mutableStateOf<String?>(null)
    private var pendingDeepLinkId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.example.data.firebase.FirebaseConfig.ensureInitialized(this)
            NotificationHelper.createNotificationChannel(this)
            FCMManager.initialize(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Init error: ${e.message}")
        }

        extractDeepLink(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        android.util.Log.d("MainActivity", "Notification permission granted")
                    } else {
                        android.util.Log.w("MainActivity", "Notification permission denied")
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { _ ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppNavigation(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            initialDeepLinkType = pendingDeepLinkType,
                            initialDeepLinkId = pendingDeepLinkId,
                            onClearDeepLink = {
                                pendingDeepLinkType = null
                                pendingDeepLinkId = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        if (intent == null) return
        val linkType = intent.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK_TYPE)
        val linkId = intent.getStringExtra(NotificationHelper.EXTRA_DEEP_LINK_ID)
        val notifId = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_ID)

        if (!linkType.isNullOrBlank()) {
            pendingDeepLinkType = linkType
            pendingDeepLinkId = linkId ?: ""
            // Mark notification as read when opened via push notification tap
            if (!notifId.isNullOrBlank()) {
                viewModel.markNotificationAsRead(0, notifId)
            }
        }
    }
}

