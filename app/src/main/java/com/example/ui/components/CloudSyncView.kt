package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.sync.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncCard(
    syncState: SyncState,
    onSyncRoomToFirestore: () -> Unit,
    onSyncFirestoreToRoom: () -> Unit,
    onSeedFirebaseData: () -> Unit = {},
    onInitializeFirestoreSchema: () -> Unit = {},
    onClearDemoAccounts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_cloud_sync"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Cloud Storage Sync",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Room-to-Firestore Cloud Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Google Cloud Firestore persistent storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connection badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (syncState.isCloudConnected) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (syncState.isCloudConnected) Color(0xFF16A34A) else Color(0xFFEAB308))
                        )
                        Text(
                            text = if (syncState.isCloudConnected) "Online Cloud" else "Local Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (syncState.isCloudConnected) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status message
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (syncState.lastError != null) Icons.Default.ErrorOutline else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (syncState.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = syncState.lastSyncMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncState.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Sync stats if synced
            if (syncState.lastSyncTimestamp > 0L) {
                val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(syncState.lastSyncTimestamp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last synced: $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Synced Breakdown Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SyncCountChip(label = "Schools", count = syncState.syncedSchoolsCount)
                    SyncCountChip(label = "Teachers", count = syncState.syncedTeachersCount)
                    SyncCountChip(label = "Students", count = syncState.syncedStudentsCount)
                    SyncCountChip(label = "Attendance", count = syncState.syncedAttendanceCount)
                }
            }

            // Loading indicator
            AnimatedVisibility(visible = syncState.isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_cloud_sync")
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncRoomToFirestore,
                    enabled = !syncState.isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_sync_room_to_firestore"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (syncState.isSyncing) "Syncing..." else "Backup to Cloud")
                }

                OutlinedButton(
                    onClick = onSyncFirestoreToRoom,
                    enabled = !syncState.isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_sync_firestore_to_room"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Cloud")
                }
            }

            // Clear All Demo Accounts & Legacy Test Records
            OutlinedButton(
                onClick = onClearDemoAccounts,
                enabled = !syncState.isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_clear_demo_accounts"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear All Demo Accounts (ডেমো একাউন্ট মুছুন)",
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Primary Firebase Data Sync Action
            FilledTonalButton(
                onClick = onSeedFirebaseData,
                enabled = !syncState.isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_seed_firebase_data"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Backup & Sync All School Data to Firebase",
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Dedicated Action: Initialize Firestore Schema for Student Profiles, Attendance Logs & Report Cards
            OutlinedButton(
                onClick = onInitializeFirestoreSchema,
                enabled = !syncState.isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_initialize_firestore_schema"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Initialize Firestore Schema (Profiles, Attendance & Report Cards)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun SyncCountChip(label: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "$label: $count",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CloudSyncDialog(
    isOpen: Boolean,
    syncState: SyncState,
    onDismiss: () -> Unit,
    onSyncRoomToFirestore: () -> Unit,
    onSyncFirestoreToRoom: () -> Unit,
    onSeedFirebaseData: () -> Unit = {},
    onClearDemoAccounts: () -> Unit = {}
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("dialog_cloud_sync"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Cloud Synchronization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                CloudSyncCard(
                    syncState = syncState,
                    onSyncRoomToFirestore = onSyncRoomToFirestore,
                    onSyncFirestoreToRoom = onSyncFirestoreToRoom,
                    onSeedFirebaseData = onSeedFirebaseData,
                    onClearDemoAccounts = onClearDemoAccounts
                )

                Text(
                    text = "Room-to-Firestore synchronization keeps local SQLite entities securely replicated to Google Cloud Firestore, providing permanent cloud backup and offline-first availability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
