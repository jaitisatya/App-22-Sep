package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.StudentEntity
import com.example.data.model.AttendanceStatus
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.util.PdfExporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StudentAttendanceHistoryDialog(
    student: StudentEntity,
    className: String,
    allStudentRecords: List<AttendanceRecordEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val firstDayOfMonth = remember { DateUtils.getFirstDayOfCurrentMonthIso() }
    val todayIso = remember { DateUtils.getTodayIso() }
    val monthName = remember { DateUtils.getCurrentMonthShortName() }

    // Filter states: ALL, MONTH, PRESENT, ABSENT
    var selectedFilter by remember { mutableStateOf("ALL") }

    val sortedRecords = remember(allStudentRecords) {
        allStudentRecords.sortedByDescending { it.date }
    }

    val initialCal = remember { Calendar.getInstance(Locale.US) }
    var calendarYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var calendarMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedCalendarDateIso by remember { mutableStateOf(todayIso) }

    val monthDays = remember(calendarYear, calendarMonth) {
        DateUtils.getMonthCalendarDays(calendarYear, calendarMonth)
    }
    val recordsByDate = remember(allStudentRecords) {
        allStudentRecords.associateBy { it.date }
    }
    val dialogMonthTitle = remember(calendarYear, calendarMonth) {
        val cal = Calendar.getInstance(Locale.US)
        cal.set(Calendar.YEAR, calendarYear)
        cal.set(Calendar.MONTH, calendarMonth)
        SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    // Stats
    val totalRecorded = sortedRecords.size
    val totalPresent = sortedRecords.count { it.status == AttendanceStatus.PRESENT }
    val totalAbsent = sortedRecords.count { it.status == AttendanceStatus.ABSENT }
    val overallPercentage = if (totalRecorded > 0) (totalPresent.toFloat() / totalRecorded) * 100f else 0f

    val monthRecords = remember(sortedRecords, firstDayOfMonth, todayIso) {
        sortedRecords.filter { it.date >= firstDayOfMonth && it.date <= todayIso }
    }
    val monthRecorded = monthRecords.size
    val monthPresent = monthRecords.count { it.status == AttendanceStatus.PRESENT }
    val monthAbsent = monthRecords.count { it.status == AttendanceStatus.ABSENT }
    val monthPercentage = if (monthRecorded > 0) (monthPresent.toFloat() / monthRecorded) * 100f else 0f

    val displayedRecords = remember(sortedRecords, selectedFilter, firstDayOfMonth, todayIso) {
        when (selectedFilter) {
            "MONTH" -> monthRecords
            "PRESENT" -> sortedRecords.filter { it.status == AttendanceStatus.PRESENT }
            "ABSENT" -> sortedRecords.filter { it.status == AttendanceStatus.ABSENT }
            else -> sortedRecords
        }
    }

    fun makePhoneCall(phone: String) {
        val sanitized = phone.filter { it.isDigit() || it == '+' }
        if (sanitized.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$sanitized")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No phone number available for this student", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAttendanceToParents() {
        val message = buildString {
            appendLine("📚 *Jaiti Foundation Attendance Report*")
            appendLine("----------------------------------")
            appendLine("👤 *Student:* ${student.studentName}")
            if (student.fatherName.isNotBlank()) appendLine("👨 *Father:* ${student.fatherName}")
            if (className.isNotBlank()) appendLine("🏫 *Class:* $className")
            appendLine()
            appendLine("🗓️ *Current Month ($monthName):* $monthPresent Present / $monthRecorded Total (%.1f%%)".format(monthPercentage))
            appendLine("📊 *Overall All-Time:* $totalPresent Present / $totalRecorded Total (%.1f%%)".format(overallPercentage))
            appendLine("----------------------------------")
            appendLine("📅 *Recent Attendance Logs:*")
            displayedRecords.take(10).forEach { rec ->
                val statusEmoji = if (rec.status == AttendanceStatus.PRESENT) "✅ Present" else "❌ Absent"
                appendLine("• ${DateUtils.formatIsoToDisplay(rec.date)}: $statusEmoji")
            }
            if (displayedRecords.size > 10) {
                appendLine("... and ${displayedRecords.size - 10} more records.")
            }
            appendLine("----------------------------------")
            appendLine("Shared via Jaiti Attendance App")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${student.studentName} - Attendance Report")
            putExtra(Intent.EXTRA_TEXT, message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Attendance Report via"))
    }

    fun exportStudentPdf() {
        if (sortedRecords.isEmpty()) {
            Toast.makeText(context, "No attendance records to export", Toast.LENGTH_SHORT).show()
            return
        }

        val startIso = sortedRecords.lastOrNull()?.date ?: firstDayOfMonth
        val endIso = sortedRecords.firstOrNull()?.date ?: todayIso

        val pdfFile = PdfExporter.generateAttendancePdf(
            context = context,
            records = sortedRecords,
            startDateIso = startIso,
            endDateIso = endIso,
            classFilterName = if (className.isNotBlank()) "$className (${student.studentName})" else student.studentName,
            fileNamePrefix = "Attendance_${student.studentName.replace(" ", "_")}"
        )

        if (pdfFile != null) {
            PdfExporter.sharePdfFile(context, pdfFile)
        } else {
            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .testTag("student_attendance_history_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header (Student Info + Call + Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Squircle Photo / Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (student.photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageUtils.getImageModel(student.photoUri),
                                    contentDescription = student.studentName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = student.studentName.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SkyBlueDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = student.studentName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            val subInfo = when {
                                student.fatherName.isNotBlank() && className.isNotBlank() -> "${student.fatherName} • $className"
                                student.fatherName.isNotBlank() -> student.fatherName
                                className.isNotBlank() -> className
                                else -> "Student"
                            }
                            Text(
                                text = subInfo,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Call icon if phone is present
                    if (student.phoneNumber.isNotBlank()) {
                        IconButton(
                            onClick = { makePhoneCall(student.phoneNumber) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFDCFCE7), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call Parent",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Cards (Month Attendance & Overall Attendance)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current Month Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0FDF4), // Light Green
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$monthName Attendance",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$monthPresent / $monthRecorded",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                            Text(
                                text = "%.1f%% Present".format(monthPercentage),
                                fontSize = 10.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }

                    // Overall All-Time Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F9FF), // Light Sky Blue
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Overall Total",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$totalPresent / $totalRecorded",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                            Text(
                                text = "%.1f%% Present".format(overallPercentage),
                                fontSize = 10.sp,
                                color = Color(0xFF0369A1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tabs (All, Month, Calendar, Present, Absent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HistoryFilterChip(
                        label = "All ($totalRecorded)",
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryFilterChip(
                        label = "$monthName ($monthRecorded)",
                        selected = selectedFilter == "MONTH",
                        onClick = { selectedFilter = "MONTH" },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryFilterChip(
                        label = "Calendar",
                        selected = selectedFilter == "CALENDAR",
                        onClick = { selectedFilter = "CALENDAR" },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryFilterChip(
                        label = "Present ($totalPresent)",
                        selected = selectedFilter == "PRESENT",
                        onClick = { selectedFilter = "PRESENT" },
                        modifier = Modifier.weight(1f)
                    )
                    HistoryFilterChip(
                        label = "Absent ($totalAbsent)",
                        selected = selectedFilter == "ABSENT",
                        onClick = { selectedFilter = "ABSENT" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Records View (Calendar or List)
                if (selectedFilter == "CALENDAR") {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Month switcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (calendarMonth == 0) {
                                        calendarMonth = 11
                                        calendarYear -= 1
                                    } else {
                                        calendarMonth -= 1
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month", tint = MaterialTheme.colorScheme.primary)
                            }

                            Text(
                                text = dialogMonthTitle,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    if (calendarMonth == 11) {
                                        calendarMonth = 0
                                        calendarYear += 1
                                    } else {
                                        calendarMonth += 1
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Day headers
                        val daysHeader = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            daysHeader.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dayName == "Sun") Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Grid
                        val chunkedWeeks = monthDays.chunked(7)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            chunkedWeeks.forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    for (i in 0 until 7) {
                                        val day = week.getOrNull(i)
                                        if (day == null || day.dayNumber == 0) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        } else {
                                            val record = recordsByDate[day.isoDate]
                                            val isSelected = day.isoDate == selectedCalendarDateIso
                                            val isToday = day.isToday
                                            val isSunday = day.isSunday

                                            val (statusColor, statusBg, statusBorder) = when {
                                                record != null && record.status == AttendanceStatus.PRESENT ->
                                                    Triple(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF86EFAC))
                                                record != null && record.status == AttendanceStatus.ABSENT ->
                                                    Triple(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFFFCA5A5))
                                                isSunday ->
                                                    Triple(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFFFDE68A))
                                                else ->
                                                    Triple(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), Color.Transparent)
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else statusBg,
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.5.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primary else statusBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(1.dp)
                                                    .clickable { selectedCalendarDateIso = day.isoDate }
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = "${day.dayNumber}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (isSunday) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                                                        style = TextStyle(
                                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    )

                                                    if (record != null) {
                                                        val letter = if (record.status == AttendanceStatus.PRESENT) "P" else "A"
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(statusColor),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = letter,
                                                                fontSize = 7.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                style = TextStyle(
                                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            )
                                                        }
                                                    } else if (isSunday) {
                                                        Text(
                                                            text = "Sun",
                                                            fontSize = 6.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFB45309),
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Selected Day Detail Card
                        val selectedRecord = recordsByDate[selectedCalendarDateIso]
                        val isSelectedSunday = DateUtils.isSunday(selectedCalendarDateIso)
                        val formattedSelectedDate = DateUtils.getDateMonthYearDisplay(selectedCalendarDateIso)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formattedSelectedDate,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    when {
                                        selectedRecord != null && selectedRecord.status == AttendanceStatus.PRESENT -> {
                                            Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFFDCFCE7), border = BorderStroke(0.6.dp, Color(0xFF86EFAC))) {
                                                Text("✓ PRESENT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        selectedRecord != null && selectedRecord.status == AttendanceStatus.ABSENT -> {
                                            Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFFFEE2E2), border = BorderStroke(0.6.dp, Color(0xFFFCA5A5))) {
                                                Text("✗ ABSENT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        isSelectedSunday -> {
                                            Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFFFEF3C7), border = BorderStroke(0.6.dp, Color(0xFFFDE68A))) {
                                                Text("SUNDAY (OFF)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        else -> {
                                            Text("NOT RECORDED", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                if (selectedRecord != null) {
                                    val timeStr = try {
                                        val ts = if (selectedRecord.createdTimestamp > 0) selectedRecord.createdTimestamp else selectedRecord.lastModifiedTimestamp
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
                                    } catch (e: Exception) { "" }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        if (timeStr.isNotBlank()) {
                                            Text("Time: $timeStr", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (selectedRecord.teacherName.isNotBlank()) {
                                            Text("By: ${selectedRecord.teacherName}", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (displayedRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No attendance records found for this filter",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedRecords, key = { it.attendanceId }) { rec ->
                            AttendanceRecordRow(rec)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions (Share Text / WhatsApp & Export PDF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share Text to WhatsApp / Parents
                    OutlinedButton(
                        onClick = { shareAttendanceToParents() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Log", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export PDF Report
                    Button(
                        onClick = { exportStudentPdf() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) SkyBluePrimary else Color(0xFFF1F5F9),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF475569),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AttendanceRecordRow(record: AttendanceRecordEntity) {
    val isPresent = record.status == AttendanceStatus.PRESENT
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isPresent) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, if (isPresent) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status Icon Indicator
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (isPresent) Color(0xFF16A34A) else Color(0xFFDC2626),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = DateUtils.formatIsoToDisplay(record.date),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    val detail = if (record.remark.isNotBlank()) {
                        "${record.className} • ${record.remark}"
                    } else if (record.teacherName.isNotBlank()) {
                        "${record.className} • By ${record.teacherName}"
                    } else {
                        record.className
                    }
                    Text(
                        text = detail,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Status Badge Text
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isPresent) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
            ) {
                Text(
                    text = if (isPresent) "PRESENT" else "ABSENT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPresent) Color(0xFF15803D) else Color(0xFFB91C1C),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
