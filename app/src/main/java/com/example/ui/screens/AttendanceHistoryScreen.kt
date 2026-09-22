package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.model.AttendanceStatus
import com.example.ui.components.ExportAttendanceDialog
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel

@Composable
fun AttendanceHistoryScreen(
    reportViewModel: ReportViewModel,
    classViewModel: ClassViewModel,
    initialStudentIdFilter: String? = null,
    onBack: (() -> Unit)? = null
) {
    val classes by classViewModel.allClasses.collectAsState()
    val records by reportViewModel.reportRecords.collectAsState()

    val startDateIso by reportViewModel.selectedStartDateIso.collectAsState()
    val endDateIso by reportViewModel.selectedEndDateIso.collectAsState()
    val classFilter by reportViewModel.selectedClassIdFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<AttendanceStatus?>(null) }
    var activeStudentIdFilter by remember { mutableStateOf(initialStudentIdFilter) }
    var showExportDialog by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, searchQuery, selectedStatusFilter, activeStudentIdFilter) {
        records.filter { rec ->
            val matchesQuery = searchQuery.isBlank() ||
                    rec.studentName.contains(searchQuery, ignoreCase = true) ||
                    rec.studentId.contains(searchQuery, ignoreCase = true)
            val matchesStatus = selectedStatusFilter == null || rec.status == selectedStatusFilter
            val matchesStudent = activeStudentIdFilter == null || rec.studentId == activeStudentIdFilter
            matchesQuery && matchesStatus && matchesStudent
        }.sortedByDescending { it.date }
    }

    val totalRecords = filteredRecords.size
    val presentCount = filteredRecords.count { it.status == AttendanceStatus.PRESENT }
    val absentCount = filteredRecords.count { it.status == AttendanceStatus.ABSENT }
    val attendancePercentage = if (totalRecords > 0) (presentCount.toFloat() / totalRecords) * 100f else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("attendance_history_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = "Attendance History Logs",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View and verify past daily attendance entries.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showExportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Summary Card for Filtered View
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalRecords", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$presentCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PresentGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Absent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$absentCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AbsentRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Attendance Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.0f%%".format(attendancePercentage), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = JaitiPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        SearchAndFilterBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search by student name or ID..."
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedStatusFilter == null,
                onClick = { selectedStatusFilter = null },
                label = { Text("All Status", fontSize = 11.sp) }
            )

            FilterChip(
                selected = selectedStatusFilter == AttendanceStatus.PRESENT,
                onClick = { selectedStatusFilter = if (selectedStatusFilter == AttendanceStatus.PRESENT) null else AttendanceStatus.PRESENT },
                label = { Text("Present Only", fontSize = 11.sp) }
            )

            FilterChip(
                selected = selectedStatusFilter == AttendanceStatus.ABSENT,
                onClick = { selectedStatusFilter = if (selectedStatusFilter == AttendanceStatus.ABSENT) null else AttendanceStatus.ABSENT },
                label = { Text("Absent Only", fontSize = 11.sp) }
            )

            if (activeStudentIdFilter != null) {
                AssistChip(
                    onClick = { activeStudentIdFilter = null },
                    label = { Text("Clear Student Filter", fontSize = 11.sp) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // History Log List
        if (filteredRecords.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No attendance records found for the selected criteria.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredRecords) { record ->
                    HistoryRecordRow(record = record)
                }
            }
        }
    }

    if (showExportDialog) {
        ExportAttendanceDialog(
            reportViewModel = reportViewModel,
            classes = classes,
            initialStartDateIso = startDateIso,
            initialEndDateIso = endDateIso,
            initialClassId = classFilter,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun HistoryRecordRow(record: AttendanceRecordEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.studentName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${record.className} • ${DateUtils.formatIsoToDisplay(record.date)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.remark.isNotBlank()) {
                    Text(
                        text = "Remark: ${record.remark}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Surface(
                color = if (record.status == AttendanceStatus.PRESENT) PresentGreenLight else AbsentRedLight,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = record.status.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (record.status == AttendanceStatus.PRESENT) PresentGreen else AbsentRed,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
