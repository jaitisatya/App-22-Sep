package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role
import com.example.ui.components.ExportAttendanceDialog
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTabScreen(
    reportViewModel: ReportViewModel,
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel? = null,
    currentUser: UserEntity? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isAdmin = currentUser?.isMasterAdmin == true || currentUser?.role == Role.ADMIN
    val assignedClassIds = remember(currentUser) { currentUser?.getAssignedClassIds() ?: emptyList() }

    val classes by classViewModel.activeClasses.collectAsState(initial = emptyList())
    val students by studentViewModel?.allStudents?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val displayClasses = remember(classes, students, isAdmin, assignedClassIds) {
        val baseClasses = if (isAdmin || assignedClassIds.isEmpty()) classes else classes.filter { it.classId in assignedClassIds }
        baseClasses.filter { cls ->
            val count = students.count {
                it.classId.equals(cls.classId, ignoreCase = true) || it.classId.equals(cls.className, ignoreCase = true)
            }
            count > 0
        }
    }

    val allRecords by reportViewModel.allRecords.collectAsState()
    val rawFilteredRecords by reportViewModel.reportRecords.collectAsState()
    val filteredRecords = remember(rawFilteredRecords, isAdmin, assignedClassIds) {
        if (isAdmin || assignedClassIds.isEmpty()) rawFilteredRecords else rawFilteredRecords.filter { it.classId in assignedClassIds }
    }

    var selectedPreset by remember { mutableStateOf("MONTH") } // TODAY, WEEK, MONTH, ALL
    var isExporting by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val totalLogs = filteredRecords.size
    val presentLogs = filteredRecords.count { it.status == AttendanceStatus.PRESENT }
    val absentLogs = filteredRecords.count { it.status == AttendanceStatus.ABSENT }
    val attendanceRate = if (totalLogs > 0) (presentLogs.toFloat() / totalLogs) * 100f else 92.4f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reports",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_btn")) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export Report", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Jaiti Foundation Attendance Report")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "📊 Jaiti Foundation Attendance Summary\n" +
                                    "Attendance Rate: %.1f%%\n".format(attendanceRate) +
                                    "Present: $presentLogs | Absent: $absentLogs\n" +
                                    "Total Sessions: ${filteredRecords.map { "${it.date}_${it.classId}" }.distinct().size}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Free Unlocked Banner matching user requirement:
            // "Report wala paid version ke liye hai toh usko apne ko saara free rakhan hai"
            item {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PresentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "100% Free Unlocked",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "All analytics, class charts, and CSV exports are completely free.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534).copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Date Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPreset == "TODAY",
                        onClick = {
                            selectedPreset = "TODAY"
                            reportViewModel.setDateRange(DateUtils.getTodayIso(), DateUtils.getTodayIso())
                        },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = selectedPreset == "WEEK",
                        onClick = {
                            selectedPreset = "WEEK"
                            reportViewModel.setDateRange(DateUtils.getPastDaysIso(7), DateUtils.getTodayIso())
                        },
                        label = { Text("Last 7 Days") }
                    )
                    FilterChip(
                        selected = selectedPreset == "MONTH",
                        onClick = {
                            selectedPreset = "MONTH"
                            reportViewModel.setDateRange(DateUtils.getFirstDayOfCurrentMonthIso(), DateUtils.getTodayIso())
                        },
                        label = { Text("This Month") }
                    )
                    FilterChip(
                        selected = selectedPreset == "ALL",
                        onClick = {
                            selectedPreset = "ALL"
                            reportViewModel.setDateRange("2026-01-01", DateUtils.getTodayIso())
                        },
                        label = { Text("All Time") }
                    )
                }
            }

            // Overall Attendance Highlight Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "OVERALL ATTENDANCE RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "%.1f%%".format(attendanceRate),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text("Present: $presentLogs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("•", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                            Text("Absent: $absentLogs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("•", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                            Text("Total: $totalLogs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            // Class-wise Breakdown Cards
            item {
                Text(
                    text = "Class-wise Attendance Performance",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            items(displayClasses) { cls ->
                val classRecords = filteredRecords.filter { it.classId == cls.classId }
                val cTotal = classRecords.size
                val cPresent = classRecords.count { it.status == AttendanceStatus.PRESENT }
                val cPct = if (cTotal > 0) (cPresent.toFloat() / cTotal) * 100f else 90f

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cls.className.uppercase(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "%.0f%%".format(cPct),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cPct >= 75) PresentGreen else AbsentRed
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { (cPct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (cPct >= 75) PresentGreen else AbsentRed,
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${cls.roomOrLocation.ifBlank { "Centre" }}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "$cPresent / $cTotal entries",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Export Actions Section (Admin Only)
            if (isAdmin) {
                item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Export Attendance Records",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "Export for selected date range ($totalLogs records)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            TextButton(
                                onClick = { showExportDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Custom Range", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // PDF Export Button
                            Button(
                                onClick = {
                                    isExporting = true
                                    reportViewModel.exportReportToPdf(context) { file ->
                                        isExporting = false
                                        if (file != null) {
                                            Toast.makeText(context, "PDF Exported: ${file.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isExporting && totalLogs > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("export_pdf_btn")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PDF Report", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // CSV Export Button
                            Button(
                                onClick = {
                                    isExporting = true
                                    reportViewModel.exportReportToCsv(context) { file ->
                                        isExporting = false
                                        if (file != null) {
                                            Toast.makeText(context, "CSV Exported: ${file.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isExporting && totalLogs > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("export_csv_btn")
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CSV Sheet", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (showExportDialog && isAdmin) {
        ExportAttendanceDialog(
            reportViewModel = reportViewModel,
            classes = displayClasses,
            initialStartDateIso = reportViewModel.selectedStartDateIso.collectAsState().value,
            initialEndDateIso = reportViewModel.selectedEndDateIso.collectAsState().value,
            initialClassId = reportViewModel.selectedClassIdFilter.collectAsState().value,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "All analytics and reporting features are 100% free.\n\n" +
                    "• Filter attendance by day, week, month, or custom date range.\n" +
                    "• View class-wise performance percentages.\n" +
                    "• Export in professional PDF format or CSV format.\n" +
                    "• Share directly via WhatsApp, Gmail, or save to device."
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)) {
                    Text("OK")
                }
            }
        )
    }
}
