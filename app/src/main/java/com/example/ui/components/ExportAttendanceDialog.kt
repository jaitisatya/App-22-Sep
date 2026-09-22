package com.example.ui.components

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import com.example.data.entity.ClassEntity
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.ReportViewModel
import java.util.Calendar

enum class ExportFormat {
    PDF,
    CSV
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportAttendanceDialog(
    reportViewModel: ReportViewModel,
    classes: List<ClassEntity>,
    initialStartDateIso: String = DateUtils.getFirstDayOfCurrentMonthIso(),
    initialEndDateIso: String = DateUtils.getTodayIso(),
    initialClassId: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var selectedPreset by remember { mutableStateOf("MONTH") } // TODAY, YESTERDAY, WEEK, MONTH, CUSTOM
    var startDateIso by remember { mutableStateOf(initialStartDateIso) }
    var endDateIso by remember { mutableStateOf(initialEndDateIso) }
    var selectedClassId by remember { mutableStateOf(initialClassId) }
    var exportFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var isExporting by remember { mutableStateOf(false) }
    var expandedClassDropdown by remember { mutableStateOf(false) }

    val selectedClassName = classes.find { it.classId == selectedClassId }?.className ?: "All Classes / Centers"

    // DatePicker launchers for Custom Date Selection
    val calendar = Calendar.getInstance()

    fun showStartDatePicker() {
        val parsed = DateUtils.parseIsoToMillis(startDateIso)
        if (parsed != null) calendar.timeInMillis = parsed

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                startDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
                selectedPreset = "CUSTOM"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showEndDatePicker() {
        val parsed = DateUtils.parseIsoToMillis(endDateIso)
        if (parsed != null) calendar.timeInMillis = parsed

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                endDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
                selectedPreset = "CUSTOM"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SkyBluePrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = SkyBluePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Export Attendance",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Export reports in PDF or CSV",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isExporting,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Presets
                Text(
                    text = "SELECT DATE RANGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "Today",
                        selected = selectedPreset == "TODAY",
                        onClick = {
                            selectedPreset = "TODAY"
                            startDateIso = DateUtils.getTodayIso()
                            endDateIso = DateUtils.getTodayIso()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = "Yesterday",
                        selected = selectedPreset == "YESTERDAY",
                        onClick = {
                            selectedPreset = "YESTERDAY"
                            startDateIso = DateUtils.getYesterdayIso()
                            endDateIso = DateUtils.getYesterdayIso()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = "Last 7D",
                        selected = selectedPreset == "WEEK",
                        onClick = {
                            selectedPreset = "WEEK"
                            startDateIso = DateUtils.getPastDaysIso(7)
                            endDateIso = DateUtils.getTodayIso()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = "This Month",
                        selected = selectedPreset == "MONTH",
                        onClick = {
                            selectedPreset = "MONTH"
                            startDateIso = DateUtils.getFirstDayOfCurrentMonthIso()
                            endDateIso = DateUtils.getTodayIso()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Date Range Pickers (Start Date -> End Date)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DateBoxField(
                        label = "From Date",
                        displayDate = DateUtils.formatIsoToDisplay(startDateIso),
                        onClick = { showStartDatePicker() },
                        modifier = Modifier.weight(1f)
                    )

                    DateBoxField(
                        label = "To Date",
                        displayDate = DateUtils.formatIsoToDisplay(endDateIso),
                        onClick = { showEndDatePicker() },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Class Filter Dropdown
                Text(
                    text = "CLASS / CENTER FILTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedClassDropdown,
                    onExpandedChange = { expandedClassDropdown = !expandedClassDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedClassName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedClassDropdown,
                        onDismissRequest = { expandedClassDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Classes / Centers", fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedClassId = null
                                expandedClassDropdown = false
                            }
                        )
                        classes.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.className) },
                                onClick = {
                                    selectedClassId = cls.classId
                                    expandedClassDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Export Format Selection (PDF vs CSV)
                Text(
                    text = "EXPORT FORMAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PDF Option Card
                    FormatCard(
                        title = "PDF Document",
                        subtitle = "Clean layout with stats & table",
                        icon = Icons.Default.PictureAsPdf,
                        iconTint = Color(0xFFE11D48),
                        isSelected = exportFormat == ExportFormat.PDF,
                        onClick = { exportFormat = ExportFormat.PDF },
                        modifier = Modifier.weight(1f)
                    )

                    // CSV Option Card
                    FormatCard(
                        title = "Excel / CSV",
                        subtitle = "Raw data sheet for spreadsheet",
                        icon = Icons.Default.TableChart,
                        iconTint = Color(0xFF16A34A),
                        isSelected = exportFormat == ExportFormat.CSV,
                        onClick = { exportFormat = ExportFormat.CSV },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isExporting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            isExporting = true
                            if (exportFormat == ExportFormat.PDF) {
                                reportViewModel.exportReportToPdf(
                                    context = context,
                                    startDateIso = startDateIso,
                                    endDateIso = endDateIso,
                                    classFilter = selectedClassId,
                                    classFilterName = selectedClassName
                                ) { file ->
                                    isExporting = false
                                    if (file != null) {
                                        Toast.makeText(context, "PDF Exported Successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "No attendance records found for this range", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                reportViewModel.exportReportToCsv(
                                    context = context,
                                    startDateIso = startDateIso,
                                    endDateIso = endDateIso,
                                    classFilter = selectedClassId
                                ) { file ->
                                    isExporting = false
                                    if (file != null) {
                                        Toast.makeText(context, "CSV Exported Successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "No attendance records found for this range", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (exportFormat == ExportFormat.PDF) Color(0xFFE11D48) else Color(0xFF0284C7)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_export_btn")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (exportFormat == ExportFormat.PDF) Icons.Default.PictureAsPdf else Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (exportFormat == ExportFormat.PDF) "Export PDF" else "Export CSV",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) SkyBluePrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DateBoxField(
    label: String,
    displayDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayDate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = SkyBluePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FormatCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) iconTint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) iconTint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = iconTint),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 13.sp
            )
        }
    }
}
