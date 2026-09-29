package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceEntity
import com.example.data.model.StudentEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for a single day's attendance consistency data point.
 */
data class DailyConsistencyPoint(
    val dateString: String,      // "2026-09-20"
    val displayLabel: String,    // "20 Sep"
    val dayOfMonth: String,      // "20"
    val presentCount: Int,
    val totalStudents: Int,
    val consistencyRate: Float,  // 0f - 100f
    val status: String           // "High Consistency", "Good", "Needs Attention"
)

/**
 * Student Attendance Trends & Consistency Tracking.
 * Strictly scoped to the logged-in teacher's school.
 * Shows daily attendance consistency tracking & trends, plus direct individual student attendance records.
 */
@Composable
fun AttendanceTrendLineChart(
    attendanceRecords: List<AttendanceEntity>,
    students: List<StudentEntity>,
    modifier: Modifier = Modifier,
    schoolId: String = "",
    schoolName: String = "",
    onManualRecord: ((studentId: String, type: String) -> Unit)? = null
) {
    var selectedRangeDays by remember { mutableIntStateOf(14) } // 7, 14, or 30 days
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var activeViewMode by remember { mutableIntStateOf(0) } // 0: Trends Graph, 1: Student-wise Report
    var studentSearchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf("All") }
    var expandedStudentId by remember { mutableStateOf<String?>(null) }

    // Strict school-level student isolation
    val schoolStudents = remember(students, schoolId) {
        if (schoolId.isNotBlank()) {
            students.filter { it.schoolId.equals(schoolId, ignoreCase = true) }
        } else {
            students
        }
    }
    val schoolStudentIds = remember(schoolStudents) {
        schoolStudents.map { it.id }.toSet()
    }

    // Strict school-level attendance record isolation
    val schoolAttendanceRecords = remember(attendanceRecords, schoolId, schoolStudentIds) {
        if (schoolId.isNotBlank()) {
            attendanceRecords.filter {
                it.schoolId.equals(schoolId, ignoreCase = true) || schoolStudentIds.contains(it.studentId)
            }
        } else {
            attendanceRecords
        }
    }

    val totalStudentsCount = schoolStudents.size.coerceAtLeast(1)

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val dayNumberFormat = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val todayDateStr = remember { dateFormat.format(Date()) }

    // Today's stats for this school
    val todayPresentStudentIds = remember(schoolAttendanceRecords, todayDateStr) {
        schoolAttendanceRecords
            .filter { it.dateString == todayDateStr && it.type == "ENTRY" }
            .map { it.studentId }
            .distinct()
    }

    // Compute past days consistency data
    val dailyPoints = remember(schoolAttendanceRecords, schoolStudents, selectedRangeDays) {
        val list = mutableListOf<DailyConsistencyPoint>()

        for (offset in (selectedRangeDays - 1) downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val dateStr = dateFormat.format(cal.time)
            val label = displayFormat.format(cal.time)
            val dayNum = dayNumberFormat.format(cal.time)

            val presentOnDate = schoolAttendanceRecords
                .filter { it.dateString == dateStr && it.type == "ENTRY" }
                .map { it.studentId }
                .distinct()
                .size

            val consistencyRate = if (schoolStudents.isNotEmpty()) {
                ((presentOnDate.toFloat() / schoolStudents.size.toFloat()) * 100f).coerceIn(0f, 100f)
            } else 0f

            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val status = when {
                dayOfWeek == Calendar.SUNDAY -> "Weekend / Sunday"
                presentOnDate > 0 && consistencyRate >= 90f -> "High Consistency"
                presentOnDate > 0 && consistencyRate >= 75f -> "Good Consistency"
                presentOnDate > 0 -> "Moderate Attendance"
                else -> "No Records Logged"
            }

            list.add(
                DailyConsistencyPoint(
                    dateString = dateStr,
                    displayLabel = label,
                    dayOfMonth = dayNum,
                    presentCount = presentOnDate,
                    totalStudents = schoolStudents.size,
                    consistencyRate = consistencyRate,
                    status = status
                )
            )
        }
        list
    }

    // Key trend metrics
    val nonZeroPoints = dailyPoints.filter { it.consistencyRate > 0f }
    val avgConsistency = if (nonZeroPoints.isNotEmpty()) {
        nonZeroPoints.map { it.consistencyRate }.average().toFloat()
    } else 0f

    val peakPoint = dailyPoints.maxByOrNull { it.consistencyRate }
    val highConsistencyDays = dailyPoints.count { it.consistencyRate >= 90f }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_attendance_trend_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // School Scope Identity Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("banner_school_attendance_scope")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = schoolName.ifBlank { "Registered School" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "School ID: ${schoolId.ifBlank { "SCH-N/A" }} • ${schoolStudents.size} Enrolled Students",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Isolated Report",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Student Attendance Trends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Daily attendance consistency tracking & trends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (avgConsistency >= 85f) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (avgConsistency >= 85f) "Steady ↗" else "Tracking",
                        color = if (avgConsistency >= 85f) Color(0xFF15803D) else Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // View Mode Tab Switcher: Trends Graph vs Student-wise Report
            TabRow(
                selectedTabIndex = activeViewMode,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = activeViewMode == 0,
                    onClick = { activeViewMode = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consistency Trends", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_attendance_trends_graph")
                )
                Tab(
                    selected = activeViewMode == 1,
                    onClick = { activeViewMode = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Students Report (${schoolStudents.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_attendance_students_report")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeViewMode == 0) {
                // TRENDS GRAPH VIEW
                // Time range selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        7 to "Last 7 Days",
                        14 to "Last 14 Days",
                        30 to "Last 30 Days"
                    ).forEach { (days, label) ->
                        FilterChip(
                            selected = selectedRangeDays == days,
                            onClick = {
                                selectedRangeDays = days
                                selectedPointIndex = null
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.testTag("chip_range_${days}d")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Metric Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Avg Consistency
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Avg Consistency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format(Locale.getDefault(), "%.1f", avgConsistency)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Peak Consistency
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Peak Consistency",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${peakPoint?.consistencyRate?.toInt() ?: 0}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Today's Attendance
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Today's Present",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${todayPresentStudentIds.size}/${schoolStudents.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Tooltip Card for inspected point
                val activePoint = selectedPointIndex?.let { idx ->
                    dailyPoints.getOrNull(idx)
                } ?: dailyPoints.lastOrNull()

                AnimatedVisibility(
                    visible = activePoint != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (activePoint != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .testTag("tooltip_active_point"),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${activePoint.displayLabel} (${activePoint.dateString})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${activePoint.presentCount} / ${activePoint.totalStudents} Students Present",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.1f", activePoint.consistencyRate)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (activePoint.consistencyRate >= 90f) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = activePoint.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activePoint.consistencyRate >= 90f) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Canvas Line Chart
                val lineColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .pointerInput(dailyPoints) {
                            detectTapGestures { offset ->
                                val pointSpacing = size.width / (dailyPoints.size - 1).coerceAtLeast(1)
                                val index = (offset.x / pointSpacing).toInt().coerceIn(0, dailyPoints.size - 1)
                                selectedPointIndex = index
                            }
                        }
                        .pointerInput(dailyPoints) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val pointSpacing = size.width / (dailyPoints.size - 1).coerceAtLeast(1)
                                val index = (change.position.x / pointSpacing).toInt().coerceIn(0, dailyPoints.size - 1)
                                selectedPointIndex = index
                            }
                        }
                        .testTag("canvas_line_chart")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
                        val width = size.width
                        val height = size.height

                        if (dailyPoints.isEmpty()) return@Canvas

                        // Draw 4 horizontal grid lines: 100%, 75%, 50%, 25%
                        val gridLevels = listOf(1.0f, 0.75f, 0.5f, 0.25f, 0.0f)
                        for (level in gridLevels) {
                            val y = height * (1f - level)
                            drawLine(
                                color = outlineColor.copy(alpha = 0.4f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            )
                        }

                        val pointSpacing = width / (dailyPoints.size - 1).coerceAtLeast(1)
                        val points = dailyPoints.mapIndexed { index, point ->
                            val x = index * pointSpacing
                            val y = height * (1f - (point.consistencyRate / 100f).coerceIn(0f, 1f))
                            Offset(x, y)
                        }

                        // Build smooth cubic Bezier path
                        val strokePath = Path().apply {
                            if (points.isNotEmpty()) {
                                moveTo(points.first().x, points.first().y)
                                for (i in 0 until points.size - 1) {
                                    val current = points[i]
                                    val next = points[i + 1]
                                    val cx = (current.x + next.x) / 2f
                                    cubicTo(cx, current.y, cx, next.y, next.x, next.y)
                                }
                            }
                        }

                        // Build gradient fill path under line
                        val fillPath = Path().apply {
                            addPath(strokePath)
                            if (points.isNotEmpty()) {
                                lineTo(points.last().x, height)
                                lineTo(points.first().x, height)
                                close()
                            }
                        }

                        // Draw gradient fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.35f),
                                    lineColor.copy(alpha = 0.02f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        // Draw stroke line
                        drawPath(
                            path = strokePath,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Draw dots on peaks and key intervals
                        val step = when {
                            dailyPoints.size <= 7 -> 1
                            dailyPoints.size <= 14 -> 2
                            else -> 3
                        }

                        for (i in dailyPoints.indices step step) {
                            val pt = points[i]
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 2.5.dp.toPx(),
                                center = pt
                            )
                        }

                        // Highlight active selected point
                        val activeIdx = selectedPointIndex ?: (dailyPoints.size - 1)
                        if (activeIdx in points.indices) {
                            val activePt = points[activeIdx]

                            drawLine(
                                color = lineColor.copy(alpha = 0.6f),
                                start = Offset(activePt.x, 0f),
                                end = Offset(activePt.x, height),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )

                            drawCircle(
                                color = lineColor.copy(alpha = 0.25f),
                                radius = 10.dp.toPx(),
                                center = activePt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = activePt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 3.5.dp.toPx(),
                                center = activePt
                            )
                        }
                    }
                }

                // X-axis timeline labels
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val labelCount = if (selectedRangeDays <= 7) selectedRangeDays else 5
                    val stride = (dailyPoints.size / labelCount).coerceAtLeast(1)
                    for (i in 0 until dailyPoints.size step stride) {
                        val p = dailyPoints[i]
                        Text(
                            text = p.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    if (dailyPoints.isNotEmpty()) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                // STUDENT-WISE ATTENDANCE REPORT VIEW
                // ("প্রত্যেকটা স্কুলে তার নিজস্ব স্টুডেন্ট অ্যাটেনডেন্স দেখার")
                val availableClasses = remember(schoolStudents) {
                    listOf("All") + schoolStudents.map { "Class ${it.className}" }.distinct().sorted()
                }

                // Search & Filter Bar
                OutlinedTextField(
                    value = studentSearchQuery,
                    onValueChange = { studentSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_student_attendance"),
                    placeholder = { Text("Search by student name or roll...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (studentSearchQuery.isNotBlank()) {
                            IconButton(onClick = { studentSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Class filter chips
                if (availableClasses.size > 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableClasses.forEach { cls ->
                            FilterChip(
                                selected = selectedClassFilter == cls,
                                onClick = { selectedClassFilter = cls },
                                label = { Text(cls, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Filtered Students List
                val displayedStudents = remember(schoolStudents, studentSearchQuery, selectedClassFilter) {
                    schoolStudents.filter { s ->
                        val matchesSearch = studentSearchQuery.isBlank() ||
                                s.name.contains(studentSearchQuery, ignoreCase = true) ||
                                s.rollNumber.contains(studentSearchQuery, ignoreCase = true) ||
                                s.id.contains(studentSearchQuery, ignoreCase = true)
                        val matchesClass = selectedClassFilter == "All" || "Class ${s.className}" == selectedClassFilter
                        matchesSearch && matchesClass
                    }
                }

                if (displayedStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PersonSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (schoolStudents.isEmpty()) "No students registered for this school yet."
                                else "No matching students found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        displayedStudents.forEach { student ->
                            val studentRecords = remember(schoolAttendanceRecords, student.id) {
                                schoolAttendanceRecords.filter { it.studentId == student.id }
                            }
                            val isPresentToday = todayPresentStudentIds.contains(student.id)
                            val todayEntryRecord = remember(studentRecords, todayDateStr) {
                                studentRecords.firstOrNull { it.dateString == todayDateStr && it.type == "ENTRY" }
                            }
                            val todayExitRecord = remember(studentRecords, todayDateStr) {
                                studentRecords.firstOrNull { it.dateString == todayDateStr && it.type == "EXIT" }
                            }

                            // Calculate consistency in selected range
                            val presentDaysInRange = remember(studentRecords, selectedRangeDays) {
                                val cal = Calendar.getInstance()
                                val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -selectedRangeDays) }.timeInMillis
                                studentRecords
                                    .filter { it.type == "ENTRY" && it.timestamp >= cutoff }
                                    .map { it.dateString }
                                    .distinct()
                                    .size
                            }
                            val consistencyPercent = ((presentDaysInRange.toFloat() / selectedRangeDays.toFloat()) * 100f).coerceIn(0f, 100f)
                            val isExpanded = expandedStudentId == student.id

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedStudentId = if (isExpanded) null else student.id
                                    }
                                    .testTag("item_school_student_attendance_${student.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPresentToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isPresentToday) Color(0xFF16A34A) else MaterialTheme.colorScheme.secondary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = student.name.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column {
                                                Text(
                                                    text = student.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Class ${student.className}-${student.section} • Roll: ${student.rollNumber}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Today's Status Pill
                                        if (isPresentToday) {
                                            Surface(
                                                color = Color(0xFFDCFCE7),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF15803D),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Present Today",
                                                        color = Color(0xFF15803D),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                color = Color(0xFFFEE2E2),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Cancel,
                                                        contentDescription = null,
                                                        tint = Color(0xFFB91C1C),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Not Marked",
                                                        color = Color(0xFFB91C1C),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Consistency progress bar for this student
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Attendance: $presentDaysInRange / $selectedRangeDays Days",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${consistencyPercent.toInt()}% Consistency",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (consistencyPercent >= 80f) Color(0xFF16A34A)
                                            else if (consistencyPercent >= 50f) Color(0xFFD97706)
                                            else Color(0xFFDC2626)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    LinearProgressIndicator(
                                        progress = { consistencyPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (consistencyPercent >= 80f) Color(0xFF16A34A)
                                        else if (consistencyPercent >= 50f) Color(0xFFD97706)
                                        else Color(0xFFDC2626),
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )

                                    // Expandable Attendance Logs Section
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp)
                                        ) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                                            Text(
                                                text = "Recent Attendance Logs (${studentRecords.size}):",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (studentRecords.isEmpty()) {
                                                Text(
                                                    text = "No past attendance logged yet.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            } else {
                                                studentRecords.take(5).forEach { rec ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = if (rec.type == "ENTRY") Icons.Default.Login else Icons.Default.Logout,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = if (rec.type == "ENTRY") Color(0xFF16A34A) else Color(0xFFEA580C)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "${rec.type} • ${rec.dateString}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        Text(
                                                            text = rec.timeString,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // Quick Mark Button if not recorded today
                                            if (!isPresentToday && onManualRecord != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FilledTonalButton(
                                                    onClick = { onManualRecord(student.id, "ENTRY") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Mark Entry for Today", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
