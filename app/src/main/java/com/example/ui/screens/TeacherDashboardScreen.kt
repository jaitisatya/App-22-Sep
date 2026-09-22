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
import com.example.data.entity.ClassEntity
import com.example.data.entity.UserEntity
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel

@Composable
fun TeacherDashboardScreen(
    currentUser: UserEntity,
    classViewModel: ClassViewModel,
    reportViewModel: ReportViewModel,
    onNavigateToTakeAttendance: (classId: String, className: String) -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    val assignedClassIds = remember(currentUser) { currentUser.getAssignedClassIds() }
    val assignedClasses by classViewModel.getAssignedClasses(assignedClassIds).collectAsState(initial = emptyList())
    val dashboardSummary by reportViewModel.dashboardSummary.collectAsState()
    val todayFormatted = remember { DateUtils.getTodayFormatted() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Teacher Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JaitiPrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Welcome,",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = currentUser.fullName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = todayFormatted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (assignedClasses.isNotEmpty()) {
                        Text(
                            text = "Assigned Class: ${assignedClasses.joinToString { it.className }}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = JaitiPrimaryContainer
                        )
                    }
                }
            }
        }

        // Primary Take Attendance CTA Button (Large)
        if (assignedClasses.isNotEmpty()) {
            item {
                val primaryClass = assignedClasses.first()
                Button(
                    onClick = { onNavigateToTakeAttendance(primaryClass.classId, primaryClass.className) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JaitiSecondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("teacher_take_attendance_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToReg,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TAKE TODAY'S ATTENDANCE",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Assigned Classes List Cards
        item {
            Text(
                text = "Your Assigned Classes",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (assignedClasses.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No classes assigned yet. Please contact Admin to assign a class.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(assignedClasses) { cls ->
                val clsSummary = dashboardSummary.classSummaries.find { it.classEntity.classId == cls.classId }
                TeacherClassCard(
                    classEntity = cls,
                    summary = clsSummary,
                    onTakeAttendance = { onNavigateToTakeAttendance(cls.classId, cls.className) }
                )
            }
        }

        // Additional Quick Options
        item {
            Text(
                text = "Quick Actions",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionRow(
                    title = "Attendance History",
                    subtitle = "View chronological daily attendance logs",
                    icon = Icons.Default.History,
                    onClick = onNavigateToHistory,
                    tag = "teacher_goto_history_btn"
                )

                QuickActionRow(
                    title = "Class Student List",
                    subtitle = "Search and view students in assigned class",
                    icon = Icons.Default.FormatListBulleted,
                    onClick = onNavigateToStudents,
                    tag = "teacher_goto_students_btn"
                )

                QuickActionRow(
                    title = "Attendance Reports",
                    subtitle = "View and export attendance statistics",
                    icon = Icons.Default.Summarize,
                    onClick = onNavigateToReports,
                    tag = "teacher_goto_reports_btn"
                )
            }
        }
    }
}

@Composable
fun TeacherClassCard(
    classEntity: ClassEntity,
    summary: com.example.viewmodel.ClassAttendanceSummary?,
    onTakeAttendance: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
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
                        text = classEntity.className,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (classEntity.roomOrLocation.isNotBlank()) {
                        Text(
                            text = classEntity.roomOrLocation,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val percentage = summary?.attendancePercentage ?: 0f
                val recorded = (summary?.presentCount ?: 0) + (summary?.absentCount ?: 0) > 0

                Surface(
                    color = if (recorded) PresentGreenLight else OfflineBadgeBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (recorded) "%.0f%% Present".format(percentage) else "Attendance Pending",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (recorded) PresentGreen else OfflineBadgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Students: ${summary?.totalStudents ?: 0}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Present: ${summary?.presentCount ?: 0}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PresentGreen
                )
                Text(
                    text = "Absent: ${summary?.absentCount ?: 0}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AbsentRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTakeAttendance,
                colors = ButtonDefaults.buttonColors(containerColor = JaitiPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("MARK ATTENDANCE NOW")
            }
        }
    }
}

@Composable
fun QuickActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(JaitiPrimaryContainer, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = JaitiPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
