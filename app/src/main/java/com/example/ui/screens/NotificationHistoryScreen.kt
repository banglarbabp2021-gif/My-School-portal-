package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.NotificationEntity
import com.example.service.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    notifications: List<NotificationEntity>,
    onBack: () -> Unit,
    onMarkAsRead: (Long, String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onNavigateDeepLink: (deepLinkType: String, deepLinkId: String, notification: NotificationEntity) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "UNREAD", "NOTICE", "EXAM_RESULT", "STUDENT_MESSAGE"
    var selectedNoticeForDetail by remember { mutableStateOf<NotificationEntity?>(null) }

    val deduplicatedNotifications = remember(notifications) {
        notifications.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
    }

    val filteredNotifications = remember(deduplicatedNotifications, selectedFilter) {
        when (selectedFilter) {
            "UNREAD" -> deduplicatedNotifications.filter { !it.readStatus }
            "NOTICE" -> deduplicatedNotifications.filter { it.deepLinkType == NotificationHelper.DEEP_LINK_NOTICE || it.targetType.contains("SCHOOL") }
            "EXAM_RESULT" -> deduplicatedNotifications.filter { it.deepLinkType == NotificationHelper.DEEP_LINK_EXAM_RESULT }
            "STUDENT_MESSAGE" -> deduplicatedNotifications.filter { it.deepLinkType == NotificationHelper.DEEP_LINK_STUDENT_MESSAGE || it.targetType == "STUDENT" }
            else -> deduplicatedNotifications
        }
    }

    val unreadCount = deduplicatedNotifications.count { !it.readStatus }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notification History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (unreadCount > 0) "$unreadCount unread notifications" else "All caught up",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_notification_history_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllAsRead,
                            modifier = Modifier.testTag("btn_mark_all_read")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark All Read", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${notifications.size})") },
                    modifier = Modifier.testTag("chip_filter_all")
                )
                FilterChip(
                    selected = selectedFilter == "UNREAD",
                    onClick = { selectedFilter = "UNREAD" },
                    label = { Text("Unread ($unreadCount)") },
                    modifier = Modifier.testTag("chip_filter_unread")
                )
                FilterChip(
                    selected = selectedFilter == "NOTICE",
                    onClick = { selectedFilter = "NOTICE" },
                    label = { Text("Notices") },
                    modifier = Modifier.testTag("chip_filter_notices")
                )
                FilterChip(
                    selected = selectedFilter == "EXAM_RESULT",
                    onClick = { selectedFilter = "EXAM_RESULT" },
                    label = { Text("Results") },
                    modifier = Modifier.testTag("chip_filter_results")
                )
            }

            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Notifications Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedFilter == "UNREAD") "You have read all your notifications!" else "When announcements or alerts are pushed, they will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotifications, key = { it.notificationId.ifBlank { "${it.id}_${it.timestamp}" } }) { notif ->
                        NotificationHistoryCard(
                            notification = notif,
                            onClick = {
                                onMarkAsRead(notif.id, notif.notificationId)
                                if (notif.deepLinkType.isNotBlank() && notif.deepLinkType != "NOTICE") {
                                    onNavigateDeepLink(notif.deepLinkType, notif.deepLinkId, notif)
                                } else {
                                    selectedNoticeForDetail = notif
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog for Full Notice view
    selectedNoticeForDetail?.let { notice ->
        AlertDialog(
            onDismissRequest = { selectedNoticeForDetail = null },
            icon = {
                Icon(
                    imageVector = when (notice.deepLinkType) {
                        NotificationHelper.DEEP_LINK_EXAM_RESULT -> Icons.Default.Assignment
                        NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> Icons.Default.Message
                        else -> Icons.Default.Campaign
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(notice.title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "From: ${notice.senderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(notice.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = notice.body.ifBlank { notice.message },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )

                    if (!notice.imageUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AsyncImage(
                            model = notice.imageUrl,
                            contentDescription = "Notice attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    if (notice.isPaymentAlert && notice.amountDue != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Outstanding Fee: $${String.format(Locale.US, "%.2f", notice.amountDue)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val type = notice.deepLinkType
                        val id = notice.deepLinkId
                        selectedNoticeForDetail = null
                        if (type.isNotBlank() && type != "NOTICE") {
                            onNavigateDeepLink(type, id, notice)
                        }
                    }
                ) {
                    Text(
                        when (notice.deepLinkType) {
                            NotificationHelper.DEEP_LINK_EXAM_RESULT -> "View Exam Results"
                            NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> "Open Messages"
                            else -> "Close"
                        }
                    )
                }
            },
            dismissButton = {
                if (notice.deepLinkType != "NOTICE") {
                    TextButton(onClick = { selectedNoticeForDetail = null }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

@Composable
fun NotificationHistoryCard(
    notification: NotificationEntity,
    onClick: () -> Unit
) {
    val isUnread = !notification.readStatus
    val formattedTime = remember(notification.timestamp) {
        val diff = System.currentTimeMillis() - notification.timestamp
        when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notification.timestamp))
        }
    }

    val iconVector = when (notification.deepLinkType) {
        NotificationHelper.DEEP_LINK_EXAM_RESULT -> Icons.Default.Grade
        NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> Icons.Default.Chat
        else -> if (notification.isPaymentAlert) Icons.Default.AttachMoney else Icons.Default.Campaign
    }

    val iconBgColor = when (notification.deepLinkType) {
        NotificationHelper.DEEP_LINK_EXAM_RESULT -> Color(0xFF10B981) // emerald
        NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> Color(0xFF8B5CF6) // purple
        else -> if (notification.isPaymentAlert) Color(0xFFEF4444) else Color(0xFF3B82F6) // red / blue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notification_item_${notification.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = iconBgColor,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Unread badge dot
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("dot_unread_${notification.id}")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.body.ifBlank { notification.message },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Footer tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "From: ${notification.senderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    // Action label
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (notification.deepLinkType) {
                                NotificationHelper.DEEP_LINK_EXAM_RESULT -> "View Results"
                                NotificationHelper.DEEP_LINK_STUDENT_MESSAGE -> "Reply"
                                else -> "Read Notice"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
