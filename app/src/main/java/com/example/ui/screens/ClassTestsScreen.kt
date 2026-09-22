package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarToday
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
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.data.entity.UserEntity
import com.example.ui.components.TestMarksDetailDialog
import com.example.viewmodel.TestExamViewModel
import com.example.viewmodel.TestStats
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassTestsScreen(
    classId: String,
    className: String,
    currentUser: UserEntity,
    testExamViewModel: TestExamViewModel,
    onBack: () -> Unit,
    onNavigateToAddTest: () -> Unit,
    onNavigateToEditTest: (testId: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(classId) {
        testExamViewModel.setClassId(classId)
    }

    val tests by testExamViewModel.testsForSelectedClass.collectAsState()

    var selectedSubjectFilter by remember { mutableStateOf("All") }
    var selectedTestForDetail by remember { mutableStateOf<ClassTestEntity?>(null) }
    var marksForDetail by remember { mutableStateOf<List<StudentTestMarksEntity>>(emptyList()) }

    // Subject filters
    val availableSubjects = remember(tests) {
        val list = mutableListOf("All")
        val subjects = tests.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        list.addAll(subjects)
        list
    }

    val filteredTests = remember(tests, selectedSubjectFilter) {
        if (selectedSubjectFilter == "All") tests
        else tests.filter { it.subject.equals(selectedSubjectFilter, ignoreCase = true) }
    }

    // Top Stats Overview
    val totalTestsCount = tests.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tests & Exams • $className",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Class Examination Dashboard",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_class_tests")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onNavigateToAddTest,
                        modifier = Modifier.padding(end = 8.dp).testTag("btn_create_new_test_top")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddTest,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text("New Test", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_create_test")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Overview Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Class $className Assessments",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$totalTestsCount test(s) conducted for this batch",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Assignment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Subject Filter Chips
            if (availableSubjects.size > 2) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableSubjects) { subject ->
                            FilterChip(
                                selected = selectedSubjectFilter == subject,
                                onClick = { selectedSubjectFilter = subject },
                                label = { Text(subject, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Tests List
            if (filteredTests.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedSubjectFilter == "All") "No tests recorded yet for $className" else "No $selectedSubjectFilter tests found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap '+ New Test' to conduct and store a class test with students' marks.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToAddTest,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Test")
                            }
                        }
                    }
                }
            } else {
                items(filteredTests, key = { it.testId }) { testItem ->
                    TestCardItem(
                        test = testItem,
                        testExamViewModel = testExamViewModel,
                        onClick = {
                            coroutineScope.launch {
                                val marks = testExamViewModel.getMarksForTestDirect(testItem.testId)
                                marksForDetail = marks
                                selectedTestForDetail = testItem
                            }
                        },
                        onEdit = {
                            onNavigateToEditTest(testItem.testId)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp)) // Floating button padding
            }
        }
    }

    // Detail Dialog (Leaderboard / Marksheet / Share / Delete)
    selectedTestForDetail?.let { activeTest ->
        TestMarksDetailDialog(
            test = activeTest,
            marks = marksForDetail,
            onDismiss = { selectedTestForDetail = null },
            onEdit = {
                val tId = activeTest.testId
                selectedTestForDetail = null
                onNavigateToEditTest(tId)
            },
            onDelete = {
                testExamViewModel.deleteTest(
                    testId = activeTest.testId,
                    onSuccess = {
                        Toast.makeText(context, "Test deleted", Toast.LENGTH_SHORT).show()
                        selectedTestForDetail = null
                    },
                    onError = { err ->
                        Toast.makeText(context, "Error deleting test: $err", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
private fun TestCardItem(
    test: ClassTestEntity,
    testExamViewModel: TestExamViewModel,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val marksFlow = remember(test.testId) { testExamViewModel.getMarksForTest(test.testId) }
    val marks by marksFlow.collectAsState(initial = emptyList())
    val stats = remember(test, marks) { TestExamViewModel.calculateStats(test, marks) }

    val formattedDate = remember(test.testDate) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val d = parser.parse(test.testDate)
            if (d != null) formatter.format(d) else test.testDate
        } catch (_: Exception) {
            test.testDate
        }
    }

    // Subject-specific color palette
    val subjectColor = remember(test.subject) {
        when (test.subject.lowercase().trim()) {
            "mathematics", "maths" -> Color(0xFF2563EB)
            "science" -> Color(0xFF059669)
            "hindi" -> Color(0xFFD97706)
            "english" -> Color(0xFF7C3AED)
            "social studies", "social science" -> Color(0xFFEA580C)
            "computer" -> Color(0xFF0891B2)
            else -> Color(0xFF4B5563)
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("test_card_${test.testId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Subject Pill + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = subjectColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = test.subject,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjectColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test Title & Total Marks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = test.testTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Max: ${test.totalMarks.toInt()} M",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Score & Participation Metrics Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stats.totalStudents} Students (${stats.appearedStudents} P • ${stats.absentStudents} AB)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (stats.appearedStudents > 0) {
                        Text(
                            text = "Avg: %.1f / ${test.totalMarks.toInt()} (%.0f%%)".format(stats.averageMarks, (stats.averageMarks / test.totalMarks) * 100.0),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Teacher Name + Quick Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "By ${test.teacherName.ifBlank { "Teacher" }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Marks", fontSize = 11.5.sp)
                    }

                    TextButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("View Marksheet →", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
