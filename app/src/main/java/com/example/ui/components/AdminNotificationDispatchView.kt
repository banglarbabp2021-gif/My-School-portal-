package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationEntity
import com.example.data.model.StudentEntity
import com.example.data.model.TeacherEntity
import com.example.service.FCMManager
import com.example.service.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationDispatchView(
    students: List<StudentEntity>,
    teachers: List<TeacherEntity>,
    notifications: List<NotificationEntity>,
    onSendNotification: (
        targetType: String,
        targetId: String,
        recipientUserId: String,
        title: String,
        body: String,
        deepLinkType: String,
        deepLinkId: String,
        isPaymentAlert: Boolean,
        amountDue: Double?
    ) -> Unit
) {
    val context = LocalContext.current
    val fcmToken by FCMManager.currentToken.collectAsState()
    val isTokenSynced by FCMManager.isTokenSynced.collectAsState()

    // 0 = All Teachers, 1 = Specific Teacher
    var targetSelectionIndex by remember { mutableStateOf(0) }
    val targetTypes = listOf(
        "সকল শিক্ষক (All Teachers)",
        "নির্দিষ্ট শিক্ষক (Specific Teacher)"
    )

    // Teacher selection & search
    var selectedTeacherId by remember { mutableStateOf(teachers.firstOrNull()?.id ?: "") }
    var teacherSearchQuery by remember { mutableStateOf("") }
    val filteredTeachers = remember(teachers, teacherSearchQuery) {
        if (teacherSearchQuery.isBlank()) teachers
        else teachers.filter {
            it.name.contains(teacherSearchQuery, ignoreCase = true) ||
            it.id.contains(teacherSearchQuery, ignoreCase = true) ||
            it.subject.contains(teacherSearchQuery, ignoreCase = true)
        }
    }

    // Form inputs
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var deepLinkDestination by remember { mutableStateOf(NotificationHelper.DEEP_LINK_NOTICE) }
    var isPaymentAlert by remember { mutableStateOf(false) }
    var amountDueText by remember { mutableStateOf("") }
    var isHighPriority by remember { mutableStateOf(true) }

    var expandedTeacherDropdown by remember { mutableStateOf(false) }

    // History filter (Deduplicated)
    var historyFilter by remember { mutableStateOf("ALL") } // ALL, TEACHERS
    val filteredNotices = remember(notifications, historyFilter) {
        val list = when (historyFilter) {
            "TEACHERS" -> notifications.filter { it.targetType.contains("TEACHER", ignoreCase = true) }
            else -> notifications.filter { it.targetType.contains("TEACHER", ignoreCase = true) }
        }
        list.distinctBy { it.notificationId.ifBlank { "${it.id}_${it.timestamp}_${it.title}" } }
    }

    // Confirmation dialog
    var dispatchedNoticeInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Strict Admin Privilege Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Privilege",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FCM Push Notification Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ADMIN ONLY",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Firebase Cloud Messaging পুশ নোটিফিকেশন নিয়ন্ত্রণ কেন্দ্র",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "গুরুত্বপূর্ণ নিয়ম: শুধুমাত্র অ্যাডমিন ব্যবহারকারী পুশ নোটিফিকেশন পাঠাতে পারেন এবং পোস্ট নোটিফিকেশন শুধুমাত্র শিক্ষকদের (Teachers) কাছেই পাঠানো যাবে।",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Live FCM Device & Cloud Diagnostics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FCM Cloud Messaging Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ready to Dispatch",
                                    fontSize = 11.sp,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Active Token: ${fcmToken?.take(28) ?: "Generating token..."}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val token = fcmToken ?: "No token available"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("FCM Token", token)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "FCM Device Token copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Token", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                NotificationHelper.showNotification(
                                    context = context,
                                    notificationId = "TEST_${System.currentTimeMillis()}",
                                    title = "FCM Test Push Alert",
                                    body = "School Management Push Notification System is operational! 🔔",
                                    deepLinkType = NotificationHelper.DEEP_LINK_NOTICE,
                                    deepLinkId = "TEST"
                                )
                                Toast.makeText(context, "Heads-up alert displayed with sound & vibration!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Push Sound", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 1. Target Audience Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Target Recipient Audience (প্রাপক নির্বাচন - শুধুমাত্র শিক্ষকবৃন্দ)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2 Target Options for Teachers Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "সকল শিক্ষক (All Teachers)", 1 to "নির্দিষ্ট শিক্ষক (Specific Teacher)").forEach { (idx, label) ->
                            FilterChip(
                                selected = targetSelectionIndex == idx,
                                onClick = { targetSelectionIndex = idx },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        if (idx == 0) Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("chip_target_$idx")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (targetSelectionIndex) {
                        0 -> {
                            // All Teachers
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "বিদ্যালয়ের সকল সক্রিয় শিক্ষকদের (${teachers.size} জন শিক্ষক) কাছে FCM টপিকের মাধ্যমে পুশ নোটিফিকেশন প্রেরিত হবে।",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        1 -> {
                            // Specific Teacher
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = teacherSearchQuery,
                                    onValueChange = { teacherSearchQuery = it },
                                    label = { Text("শিক্ষক খুঁজুন (নাম, আইডি বা বিষয়)") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                ExposedDropdownMenuBox(
                                    expanded = expandedTeacherDropdown,
                                    onExpandedChange = { expandedTeacherDropdown = !expandedTeacherDropdown },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val currentTeacher = teachers.find { it.id == selectedTeacherId }
                                    val displayText = if (currentTeacher != null) {
                                        "${currentTeacher.name} (${currentTeacher.subject} • ID: ${currentTeacher.id})"
                                    } else {
                                        "শিক্ষক নির্বাচন করুন"
                                    }

                                    OutlinedTextField(
                                        value = displayText,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("নির্বাচিত শিক্ষক") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTeacherDropdown) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                            .testTag("dropdown_select_teacher")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedTeacherDropdown,
                                        onDismissRequest = { expandedTeacherDropdown = false }
                                    ) {
                                        filteredTeachers.forEach { t ->
                                            DropdownMenuItem(
                                                text = { Text("${t.name} (${t.subject} • ID: ${t.id})") },
                                                onClick = {
                                                    selectedTeacherId = t.id
                                                    expandedTeacherDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Notification Presets & Content
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Notification Content & Templates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Bengali / English template chips
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    title = "জরুরি শিক্ষক সভা (Staff Meeting)"
                                    body = "আগামীকাল বেলা ১২:০০ টায় কনফারেন্স রুমে সকল শিক্ষকদের জরুরি সভা অনুষ্ঠিত হবে। সবার উপস্থিতি বাধ্যতামূলক।"
                                    deepLinkDestination = NotificationHelper.DEEP_LINK_NOTICE
                                    isPaymentAlert = false
                                },
                                label = { Text("Staff Meeting", fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    title = "পরীক্ষার প্রশ্নপত্র জমা (Question Papers)"
                                    body = "আসন্ন সাময়িক পরীক্ষার প্রশ্নপত্র ও নম্বর বণ্টন আগামী ৫ দিনের মধ্যে প্রধান শিক্ষক বরাবর জমা দিন।"
                                    deepLinkDestination = NotificationHelper.DEEP_LINK_NOTICE
                                    isPaymentAlert = false
                                },
                                label = { Text("Question Papers", fontSize = 11.sp) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    title = "সিলেবাস ও লেসন প্ল্যান হালনাগাদ (Lesson Plan)"
                                    body = "চলতি মাসের পাঠ পরিকল্পনা ও সিলেবাস অগ্রগতির রিপোর্ট প্রস্তুত রাখুন।"
                                    deepLinkDestination = NotificationHelper.DEEP_LINK_NOTICE
                                    isPaymentAlert = false
                                },
                                label = { Text("Lesson Plan", fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    title = "জরুরি প্রশাসনিক নির্দেশনা (Academic Directive)"
                                    body = "বিদ্যালয়ের সার্বিক শৃঙ্খলা ও সময়ানুবর্তিতা নিশ্চিত করতে সকল শিক্ষককে যথাসময়ে উপস্থিত থাকার অনুরোধ করা হচ্ছে।"
                                    deepLinkDestination = NotificationHelper.DEEP_LINK_NOTICE
                                    isPaymentAlert = false
                                },
                                label = { Text("Directive", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title *") },
                        placeholder = { Text("e.g. জরুরি নোটিশ / Annual Sports Meet") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notification_title"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message Body *") },
                        placeholder = { Text("নোটিফিকেশনের বিস্তারিত বার্তা লিখুন...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_notification_body"),
                        minLines = 3,
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Deep link selector
                    Text(
                        text = "Notification Tap Action:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = deepLinkDestination == NotificationHelper.DEEP_LINK_NOTICE,
                            onClick = { deepLinkDestination = NotificationHelper.DEEP_LINK_NOTICE },
                            label = { Text("Notice Details") },
                            leadingIcon = { Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("chip_link_notice")
                        )
                        FilterChip(
                            selected = deepLinkDestination == NotificationHelper.DEEP_LINK_EXAM_RESULT,
                            onClick = { deepLinkDestination = NotificationHelper.DEEP_LINK_EXAM_RESULT },
                            label = { Text("Exam Result") },
                            leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("chip_link_result")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional fee alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Include Fee / Due Amount Alert", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isPaymentAlert,
                            onCheckedChange = { isPaymentAlert = it },
                            modifier = Modifier.testTag("switch_payment_alert")
                        )
                    }

                    if (isPaymentAlert) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amountDueText,
                            onValueChange = { amountDueText = it },
                            label = { Text("Amount Due (₹ / $)") },
                            placeholder = { Text("e.g. 1500.00") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_amount_due"),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // 3. Live Heads-up Preview & Send
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Device Heads-up Banner Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "HIGH PRIORITY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Preview notification card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = title.ifBlank { "School Management Portal" },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "now",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = body.ifBlank { "High-priority push alert will be delivered to active device screens with sound and vibration." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                if (isPaymentAlert && amountDueText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Amount Due: ₹$amountDueText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val canSend = title.isNotBlank() && body.isNotBlank() && (targetSelectionIndex != 1 || selectedTeacherId.isNotBlank())

                    Button(
                        onClick = {
                            val targetType = when (targetSelectionIndex) {
                                0 -> "ALL_TEACHERS"
                                1 -> "TEACHER"
                                else -> "ALL_TEACHERS"
                            }
                            val targetId = when (targetSelectionIndex) {
                                1 -> selectedTeacherId
                                else -> ""
                            }
                            val recipientUserId = when (targetSelectionIndex) {
                                1 -> selectedTeacherId
                                else -> ""
                            }
                            val amount = amountDueText.toDoubleOrNull()

                            onSendNotification(
                                targetType,
                                targetId,
                                recipientUserId,
                                title.trim(),
                                body.trim(),
                                deepLinkDestination,
                                targetId,
                                isPaymentAlert,
                                amount
                            )

                            val targetDisplayName = if (targetSelectionIndex == 0) "সকল শিক্ষক (All Teachers)" else "শিক্ষক ID: $selectedTeacherId"
                            dispatchedNoticeInfo = Pair(title.trim(), targetDisplayName)

                            // Clear inputs
                            title = ""
                            body = ""
                            amountDueText = ""
                            isPaymentAlert = false
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_admin_dispatch_fcm"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SendToMobile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dispatch Push Notification via FCM",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Sent Notifications List & Filter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dispatched History (${notifications.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL", "STUDENTS", "TEACHERS").forEach { f ->
                        FilterChip(
                            selected = historyFilter == f,
                            onClick = { historyFilter = f },
                            label = { Text(f, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        if (filteredNotices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No push notifications dispatched yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredNotices) { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = notice.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = notice.targetType,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = notice.message.ifBlank { notice.body },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notice.timestamp))
                            Text(
                                text = "Sent: $timeStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Dispatched via FCM",
                                    fontSize = 11.sp,
                                    color = Color(0xFF047857),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Alert Dialog
    if (dispatchedNoticeInfo != null) {
        val (dispatchedTitle, dispatchedTarget) = dispatchedNoticeInfo!!
        AlertDialog(
            onDismissRequest = { dispatchedNoticeInfo = null },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("FCM পুশ নোটিফিকেশন সফলভাবে প্রেরিত") },
            text = {
                Column {
                    Text("আপনার নোটিফিকেশন Firebase Cloud Messaging সার্ভারে পাঠানো হয়েছে।")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• শিরোনাম: $dispatchedTitle", fontWeight = FontWeight.Bold)
                    Text("• প্রাপক: $dispatchedTarget")
                    Text("• স্ট্যাটাস: ডিভাইসগুলোতে তাৎক্ষণিক সাউন্ড ও ভাইব্রেশনের সাথে নোটিফিকেশন প্রদর্শিত হচ্ছে।")
                }
            },
            confirmButton = {
                Button(onClick = { dispatchedNoticeInfo = null }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}
