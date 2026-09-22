package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.viewmodel.TestExamViewModel

@Composable
fun TestMarksDetailDialog(
    test: ClassTestEntity,
    marks: List<StudentTestMarksEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val stats = remember(test, marks) {
        TestExamViewModel.calculateStats(test, marks)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val sortedMarks = remember(marks) {
        marks.sortedWith(
            compareByDescending<StudentTestMarksEntity> { !it.isAbsent }
                .thenByDescending { it.obtainedMarks ?: -1.0 }
                .thenBy { it.studentName }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = test.subject,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "• ${test.className}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = test.testTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Date: ${test.testDate} • Max: ${test.totalMarks.toInt()} Marks",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Performance Summary Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(
                            label = "Appeared",
                            value = "${stats.appearedStudents}/${stats.totalStudents}",
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatItem(
                            label = "Absent",
                            value = "${stats.absentStudents}",
                            color = if (stats.absentStudents > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatItem(
                            label = "Avg Score",
                            value = "%.1f".format(stats.averageMarks),
                            color = Color(0xFF0369A1)
                        )
                        StatItem(
                            label = "Highest",
                            value = "${stats.highestMarks.toInt()}/${test.totalMarks.toInt()}",
                            color = Color(0xFF16A34A)
                        )
                        StatItem(
                            label = "Pass %",
                            value = "%.0f%%".format(stats.passPercentage),
                            color = if (stats.passPercentage >= 60) Color(0xFF16A34A) else Color(0xFFEA580C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Marksheet List Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Student Scores (${marks.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ranked by Marks",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Student Marksheet Rows
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(sortedMarks, key = { _, it -> it.markId }) { index, item ->
                        val isPass = !item.isAbsent && (item.obtainedMarks ?: 0.0) >= (test.passingMarks.takeIf { it > 0 } ?: (test.totalMarks * 0.33))
                        val pct = if (!item.isAbsent && item.obtainedMarks != null && test.totalMarks > 0) {
                            (item.obtainedMarks / test.totalMarks) * 100.0
                        } else null

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.isAbsent) Color(0xFFFEF2F2) else Color.White,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (item.isAbsent) Color(0xFFFEE2E2) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Rank circle
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (index) {
                                                    0 -> Color(0xFFFEF08A)
                                                    1 -> Color(0xFFE2E8F0)
                                                    2 -> Color(0xFFFED7AA)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.studentName,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = item.studentId,
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Marks & Status
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.isAbsent) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFEE2E2),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                                        ) {
                                            Text(
                                                text = "ABSENT",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        val marksStr = if ((item.obtainedMarks ?: 0.0) % 1.0 == 0.0) {
                                            "${item.obtainedMarks?.toInt() ?: 0}"
                                        } else {
                                            "%.1f".format(item.obtainedMarks ?: 0.0)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$marksStr / ${test.totalMarks.toInt()}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPass) Color(0xFF16A34A) else Color(0xFFDC2626)
                                            )
                                            if (pct != null) {
                                                Text(
                                                    text = "%.0f%%".format(pct),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons: Edit, Share, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Button
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.testTag("btn_delete_test")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }

                    // Share Report Button
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("📋 *${test.testTitle}* - ${test.className}")
                                appendLine("Subject: ${test.subject}")
                                appendLine("Date: ${test.testDate} | Max Marks: ${test.totalMarks.toInt()}")
                                appendLine("Avg Score: %.1f | Highest: ${stats.highestMarks.toInt()}".format(stats.averageMarks))
                                appendLine("-----------------------------")
                                sortedMarks.forEachIndexed { i, m ->
                                    val scoreStr = if (m.isAbsent) "AB" else "${m.obtainedMarks?.toInt() ?: 0}/${test.totalMarks.toInt()}"
                                    appendLine("${i + 1}. ${m.studentName} (${m.studentId}): $scoreStr")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Test Marksheet"))
                        },
                        modifier = Modifier.weight(1f).testTag("btn_share_test")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp)
                    }

                    // Edit Marks Button
                    Button(
                        onClick = {
                            onDismiss()
                            onEdit()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).testTag("btn_edit_test_marks")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Marks", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Test?") },
            text = { Text("Are you sure you want to permanently delete \"${test.testTitle}\" and all recorded student marks?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
