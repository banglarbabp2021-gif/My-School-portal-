package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AttendanceEntity
import com.example.data.model.StudentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Data model representing a student who has been absent for 3 or more consecutive school days.
 */
data class ConsecutiveAbsenceInfo(
    val student: StudentEntity,
    val consecutiveDaysCount: Int,
    val absentDates: List<String>,
    val isCurrentStreak: Boolean,
    val lastPresentDate: String? = null
)

/**
 * Calculates all students in a given school who have been absent for 3 or more consecutive days.
 */
fun calculateConsecutiveAbsences(
    students: List<StudentEntity>,
    records: List<AttendanceEntity>
): List<ConsecutiveAbsenceInfo> {
    if (students.isEmpty()) return emptyList()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 1. Collect all ENTRY records (attendance presence)
    val entryRecords = records.filter { it.type == "ENTRY" }

    // Map each student to the set of dates they attended
    val studentAttendedDates = mutableMapOf<String, MutableSet<String>>()
    val allActiveSchoolDates = mutableSetOf<String>()

    for (rec in entryRecords) {
        studentAttendedDates.getOrPut(rec.studentId) { mutableSetOf() }.add(rec.dateString)
        allActiveSchoolDates.add(rec.dateString)
    }

    // Active dates where at least one student attended
    val sortedActiveDatesDesc = allActiveSchoolDates.toList().sortedDescending()

    // Real consecutive absences ONLY: strictly evaluate against real school dates where attendance was recorded.
    // Do NOT generate synthetic calendar weekdays or demo alerts when real school attendance hasn't occurred.
    if (sortedActiveDatesDesc.size < 3) {
        return emptyList()
    }

    val evaluationDates = sortedActiveDatesDesc

    val alerts = mutableListOf<ConsecutiveAbsenceInfo>()

    for (student in students) {
        val attendedDates = studentAttendedDates[student.id] ?: emptySet()

        // 1. Check current ongoing absence streak starting from the latest date
        var currentStreak = 0
        val currentAbsentDates = mutableListOf<String>()

        for (date in evaluationDates) {
            if (!attendedDates.contains(date)) {
                currentStreak++
                currentAbsentDates.add(date)
            } else {
                break
            }
        }

        if (currentStreak >= 3) {
            val lastPresentDate = evaluationDates.firstOrNull { attendedDates.contains(it) }
            alerts.add(
                ConsecutiveAbsenceInfo(
                    student = student,
                    consecutiveDaysCount = currentStreak,
                    absentDates = currentAbsentDates.take(currentStreak),
                    isCurrentStreak = true,
                    lastPresentDate = lastPresentDate
                )
            )
            continue
        }

        // 2. Also check if student missed 3 consecutive school days in recent history
        val chronoDates = evaluationDates.take(14).sorted()
        var historicalStreak = 0
        var maxHistoricalStreak = 0
        val tempDates = mutableListOf<String>()
        var bestStreakDates = listOf<String>()

        for (date in chronoDates) {
            if (!attendedDates.contains(date)) {
                historicalStreak++
                tempDates.add(date)
                if (historicalStreak > maxHistoricalStreak) {
                    maxHistoricalStreak = historicalStreak
                    bestStreakDates = ArrayList(tempDates)
                }
            } else {
                historicalStreak = 0
                tempDates.clear()
            }
        }

        if (maxHistoricalStreak >= 3) {
            val lastPresentDate = chronoDates.lastOrNull { attendedDates.contains(it) }
            alerts.add(
                ConsecutiveAbsenceInfo(
                    student = student,
                    consecutiveDaysCount = maxHistoricalStreak,
                    absentDates = bestStreakDates,
                    isCurrentStreak = false,
                    lastPresentDate = lastPresentDate
                )
            )
        }
    }

    return alerts.sortedByDescending { it.consecutiveDaysCount }
}

/**
 * Visual badge displayed on student cards/profiles to highlight 3+ consecutive days absence.
 */
@Composable
fun ConsecutiveAbsenceBadge(
    consecutiveDays: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = modifier.testTag("badge_consecutive_absence")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$consecutiveDays Days Absent",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Attention-grabbing banner on the teacher dashboard alerting about students with 3 consecutive days of absence.
 */
@Composable
fun ConsecutiveAbsenceBanner(
    alerts: List<ConsecutiveAbsenceInfo>,
    onViewAll: () -> Unit,
    onNotifyStudent: (StudentEntity) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    if (alerts.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("banner_consecutive_absence_alert"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Absence Warning",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "3-Day Consecutive Absence Alert",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "${alerts.size} student${if (alerts.size > 1) "s" else ""} absent for 3 consecutive days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_dismiss_absence_banner")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close / Dismiss Alert (×)",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Student badges row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(alerts) { alert ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clickable { onNotifyStudent(alert.student) }
                            .testTag("chip_absent_student_${alert.student.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${alert.student.name} (Roll ${alert.student.rollNumber})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text("${alert.consecutiveDaysCount}d")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewAll,
                    modifier = Modifier.testTag("btn_view_absence_alerts"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Details", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val firstStudent = alerts.firstOrNull()?.student
                        if (firstStudent != null) onNotifyStudent(firstStudent)
                    },
                    modifier = Modifier.testTag("btn_quick_notify_absentee"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Notice", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Dedicated Absence Risk Card for the Attendance tab.
 */
@Composable
fun ConsecutiveAbsenceSectionCard(
    alerts: List<ConsecutiveAbsenceInfo>,
    onNotifyStudent: (StudentEntity) -> Unit,
    onSelectStudent: (StudentEntity) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    if (alerts.isEmpty()) return

    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_consecutive_absence_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "3-Day Consecutive Absence Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Students requiring immediate check-in or notification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text("${alerts.size} Alerts")
                    }
                    if (onDismiss != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_dismiss_absence_section")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close / Dismiss Alert (×)",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                alerts.take(5).forEach { alert ->
                    val student = alert.student
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStudent(student) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (student.photoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = student.photoUri,
                                        contentDescription = student.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = student.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    ConsecutiveAbsenceBadge(consecutiveDays = alert.consecutiveDaysCount)
                                }
                                Text(
                                    text = "Class ${student.className}-${student.section} • Roll #${student.rollNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (alert.absentDates.isNotEmpty()) {
                                    Text(
                                        text = "Dates: ${alert.absentDates.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Action buttons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (student.studentMobile.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.studentMobile}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = "Call Parent",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onNotifyStudent(student) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Campaign,
                                        contentDescription = "Send Notice",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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

/**
 * Detailed dialog displaying full information on students with 3+ consecutive days absence.
 */
@Composable
fun ConsecutiveAbsenceDialog(
    alerts: List<ConsecutiveAbsenceInfo>,
    onDismiss: () -> Unit,
    onNotifyStudent: (StudentEntity) -> Unit,
    onSelectStudent: (StudentEntity) -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("dialog_consecutive_absence_details"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Consecutive Absence Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${alerts.size} student${if (alerts.size > 1) "s" else ""} absent for 3+ consecutive days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(alerts) { alert ->
                        val student = alert.student
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Class ${student.className}-${student.section} • Roll #${student.rollNumber} • ID: ${student.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    ConsecutiveAbsenceBadge(consecutiveDays = alert.consecutiveDaysCount)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Missed Dates (${alert.consecutiveDaysCount} days):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = alert.absentDates.joinToString("  •  "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 11.sp
                                        )
                                        if (!alert.lastPresentDate.isNullOrBlank()) {
                                            Text(
                                                text = "Last Present: ${alert.lastPresentDate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (student.studentMobile.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.studentMobile}"))
                                                context.startActivity(dialIntent)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Call Parent", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onNotifyStudent(student)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Send In-App Notice", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
