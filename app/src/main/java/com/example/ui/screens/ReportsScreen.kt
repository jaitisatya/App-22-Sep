package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.model.AttendanceStatus
import com.example.ui.components.ExportAttendanceDialog
import com.example.ui.components.QuickStatCard
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    reportViewModel: ReportViewModel,
    classViewModel: ClassViewModel
) {
    val context = LocalContext.current
    val classes by classViewModel.allClasses.collectAsState()
    val records by reportViewModel.reportRecords.collectAsState()

    val startDateIso by reportViewModel.selectedStartDateIso.collectAsState()
    val endDateIso by reportViewModel.selectedEndDateIso.collectAsState()
    val selectedClassId by reportViewModel.selectedClassIdFilter.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var expandedClassMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val totalRecords = records.size
    val presentCount = records.count { it.status == AttendanceStatus.PRESENT }
    val absentCount = records.count { it.status == AttendanceStatus.ABSENT }
    val attendancePercentage = if (totalRecords > 0) (presentCount.toFloat() / totalRecords) * 100f else 0f

    val selectedClassName = classes.find { it.classId == selectedClassId }?.className ?: "All Classes"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Attendance Reports & Analytics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Generate and export attendance reports for government audits and foundation donors.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Date Range & Class Filters
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report Parameters", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Date Range Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = startDateIso == DateUtils.getTodayIso() && endDateIso == DateUtils.getTodayIso(),
                            onClick = { reportViewModel.setDateRange(DateUtils.getTodayIso(), DateUtils.getTodayIso()) },
                            label = { Text("Today", fontSize = 12.sp) }
                        )

                        FilterChip(
                            selected = startDateIso == DateUtils.getPastDaysIso(7) && endDateIso == DateUtils.getTodayIso(),
                            onClick = { reportViewModel.setDateRange(DateUtils.getPastDaysIso(7), DateUtils.getTodayIso()) },
                            label = { Text("Last 7 Days", fontSize = 12.sp) }
                        )

                        FilterChip(
                            selected = startDateIso == DateUtils.getFirstDayOfCurrentMonthIso(),
                            onClick = { reportViewModel.setDateRange(DateUtils.getFirstDayOfCurrentMonthIso(), DateUtils.getTodayIso()) },
                            label = { Text("This Month", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Range Info
                    Text(
                        text = "Selected Period: ${DateUtils.formatIsoToDisplay(startDateIso)} to ${DateUtils.formatIsoToDisplay(endDateIso)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = JaitiPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Class Dropdown Selector
                    ExposedDropdownMenuBox(
                        expanded = expandedClassMenu,
                        onExpandedChange = { expandedClassMenu = !expandedClassMenu }
                    ) {
                        OutlinedTextField(
                            value = selectedClassName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filter by Class / Center") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassMenu) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedClassMenu,
                            onDismissRequest = { expandedClassMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Classes") },
                                onClick = {
                                    reportViewModel.setClassFilter(null)
                                    expandedClassMenu = false
                                }
                            )
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.className) },
                                    onClick = {
                                        reportViewModel.setClassFilter(cls.classId)
                                        expandedClassMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    title = "Present Logs",
                    value = "$presentCount",
                    subtitle = "Attendance entries",
                    icon = Icons.Default.CheckCircle,
                    contentColor = PresentGreen,
                    modifier = Modifier.weight(1f)
                )

                QuickStatCard(
                    title = "Absent Logs",
                    value = "$absentCount",
                    subtitle = "Absence entries",
                    icon = Icons.Default.Cancel,
                    contentColor = AbsentRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JaitiPrimaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Period Attendance Rate", fontSize = 13.sp, color = JaitiOnPrimaryContainer)
                        Text(
                            text = "%.1f%%".format(attendancePercentage),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = JaitiPrimary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = JaitiPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Export Actions Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Attendance Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "Export attendance records in PDF or CSV. Supports offline sharing via WhatsApp or Email.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Options", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // PDF Export
                        Button(
                            onClick = {
                                isExporting = true
                                reportViewModel.exportReportToPdf(
                                    context = context,
                                    startDateIso = startDateIso,
                                    endDateIso = endDateIso,
                                    classFilter = selectedClassId,
                                    classFilterName = selectedClassName
                                ) { file ->
                                    isExporting = false
                                    if (file != null) {
                                        Toast.makeText(context, "PDF Exported: ${file.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = totalRecords > 0 && !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_report_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // CSV Export
                        Button(
                            onClick = {
                                isExporting = true
                                reportViewModel.exportReportToCsv(
                                    context = context,
                                    startDateIso = startDateIso,
                                    endDateIso = endDateIso,
                                    classFilter = selectedClassId
                                ) { file ->
                                    isExporting = false
                                    if (file != null) {
                                        Toast.makeText(context, "CSV Export created successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = totalRecords > 0 && !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_csv_report_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT CSV", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Sample Report Preview List
        item {
            Text("Filtered Attendance Records ($totalRecords)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (records.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No records found for the selected date range and class filter.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(records.take(20)) { record ->
                ReportRecordRow(record = record)
            }
        }
    }

    if (showExportDialog) {
        ExportAttendanceDialog(
            reportViewModel = reportViewModel,
            classes = classes,
            initialStartDateIso = startDateIso,
            initialEndDateIso = endDateIso,
            initialClassId = selectedClassId,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun ReportRecordRow(record: AttendanceRecordEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${record.className} • ${record.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = record.status.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (record.status == AttendanceStatus.PRESENT) PresentGreen else AbsentRed
            )
        }
    }
}
