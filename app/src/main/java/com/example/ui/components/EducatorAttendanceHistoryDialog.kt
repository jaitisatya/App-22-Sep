package com.example.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.EducatorProfile
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.util.PdfExporter

@Composable
fun EducatorAttendanceHistoryDialog(
    educator: EducatorProfile,
    allRecords: List<AttendanceRecordEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val firstDayOfMonth = remember { DateUtils.getFirstDayOfCurrentMonthIso() }
    val todayIso = remember { DateUtils.getTodayIso() }
    val monthName = remember { DateUtils.getCurrentMonthShortName() }

    // Filter states: ALL, MONTH, PRESENT, ABSENT
    var selectedFilter by remember { mutableStateOf("ALL") }

    val sortedRecords = remember(allRecords) {
        allRecords.sortedByDescending { it.date }
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
            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAttendanceLog() {
        val message = buildString {
            appendLine("📚 *Jaiti Foundation - Educator Attendance Report*")
            appendLine("----------------------------------")
            appendLine("👤 *Educator:* ${educator.name}")
            if (educator.subject.isNotBlank()) appendLine("📖 *Subject:* ${educator.subject}")
            if (educator.phone.isNotBlank()) appendLine("📞 *Phone:* ${educator.phone}")
            appendLine()
            appendLine("🗓️ *Current Month ($monthName):* $monthPresent Present / $monthRecorded Total (%.1f%%)".format(monthPercentage))
            appendLine("📊 *Overall All-Time:* $totalPresent Present / $totalRecorded Total (%.1f%%)".format(overallPercentage))
            appendLine("----------------------------------")
            appendLine("📅 *Recent Attendance Logs:*")
            displayedRecords.take(15).forEach { rec ->
                val statusEmoji = if (rec.status == AttendanceStatus.PRESENT) "✅ Present" else "❌ Absent"
                appendLine("• ${DateUtils.formatIsoToDisplay(rec.date)}: $statusEmoji")
            }
            if (displayedRecords.size > 15) {
                appendLine("... and ${displayedRecords.size - 15} more records.")
            }
            appendLine("----------------------------------")
            appendLine("Shared via Jaiti Attendance App")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${educator.name} - Attendance Report")
            putExtra(Intent.EXTRA_TEXT, message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Attendance Report via"))
    }

    fun exportPdf() {
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
            classFilterName = "Educator: ${educator.name}",
            fileNamePrefix = "Educator_${educator.name.replace(" ", "_")}"
        )

        if (pdfFile != null) {
            PdfExporter.sharePdfFile(context, pdfFile)
        } else {
            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("educator_attendance_history_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header (Educator Info + Call + Close)
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
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (educator.photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageUtils.getImageModel(educator.photoUri),
                                    contentDescription = educator.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = educator.name.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = educator.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subInfo = when {
                                educator.subject.isNotBlank() -> "Subject: ${educator.subject}"
                                educator.phone.isNotBlank() -> educator.phone
                                else -> "Educator (Staff)"
                            }
                            Text(
                                text = subInfo,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Call icon if phone is present
                    if (educator.phone.isNotBlank()) {
                        IconButton(
                            onClick = { makePhoneCall(educator.phone) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFDCFCE7), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call Educator",
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
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Cards (Month Attendance + Overall Total Attendance)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Month Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$monthPresent / $monthRecorded",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "%.1f%% Present".format(monthPercentage),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Total Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
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
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$totalPresent / $totalRecorded",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "%.1f%% Present".format(overallPercentage),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tabs (All, Month, Present, Absent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                // Records List
                if (displayedRecords.isEmpty()) {
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No attendance records found for this filter",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            EducatorAttendanceRecordRow(rec)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions (Share Text / WhatsApp & Export PDF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { shareAttendanceLog() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Log", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { exportPdf() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EducatorAttendanceRecordRow(record: AttendanceRecordEntity) {
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
                        "Educator Attendance • ${record.remark}"
                    } else if (record.teacherName.isNotBlank()) {
                        "Marked by ${record.teacherName}"
                    } else {
                        "Staff Attendance"
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
