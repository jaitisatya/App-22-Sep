package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.data.entity.UserEntity
import com.example.util.DateUtils
import com.example.viewmodel.StudentViewModel
import com.example.viewmodel.TestExamViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTestScreen(
    classId: String,
    className: String,
    existingTestId: String? = null,
    currentUser: UserEntity,
    testExamViewModel: TestExamViewModel,
    studentViewModel: StudentViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Students in this class
    val allClassesStudents by studentViewModel.allStudents.collectAsState()
    val classStudents: List<StudentEntity> = remember(allClassesStudents, classId, className) {
        allClassesStudents.filter {
            (it.classId.equals(classId, ignoreCase = true) || it.classId.equals(className, ignoreCase = true)) && it.active
        }.sortedWith(compareBy<StudentEntity> { it.studentId }.thenBy { it.studentName })
    }

    // Form states
    var testTitle by remember { mutableStateOf("Weekly Test") }
    var selectedSubject by remember { mutableStateOf("Mathematics") }
    var customSubject by remember { mutableStateOf("") }
    var isSubjectDropdownOpen by remember { mutableStateOf(false) }

    // Date state (default today, teacher can change)
    var testDateIso by remember { mutableStateOf(DateUtils.getTodayIso()) }

    // Total Marks state
    var totalMarksText by remember { mutableStateOf("20") }
    val totalMarks = totalMarksText.toDoubleOrNull() ?: 20.0

    // Student marks map: studentId -> Pair<obtainedMarksText, isAbsent>
    val marksEntryMap = remember { mutableStateMapOf<String, MarksEntryState>() }
    var isLoadedFromExisting by remember { mutableStateOf(false) }

    // Pre-fill student marks map from active students
    LaunchedEffect(classStudents) {
        if (!isLoadedFromExisting && existingTestId.isNullOrBlank()) {
            classStudents.forEach { student ->
                if (!marksEntryMap.containsKey(student.studentId)) {
                    marksEntryMap[student.studentId] = MarksEntryState(
                        studentId = student.studentId,
                        studentName = student.studentName,
                        marksText = "",
                        isAbsent = false
                    )
                }
            }
        }
    }

    // If editing existing test, load its details & saved marks
    LaunchedEffect(existingTestId) {
        if (!existingTestId.isNullOrBlank()) {
            val existing = testExamViewModel.getTestByIdDirect(existingTestId)
            if (existing != null) {
                testTitle = existing.testTitle
                selectedSubject = existing.subject
                testDateIso = existing.testDate
                totalMarksText = if (existing.totalMarks % 1.0 == 0.0) {
                    existing.totalMarks.toInt().toString()
                } else {
                    existing.totalMarks.toString()
                }

                val savedMarks = testExamViewModel.getMarksForTestDirect(existingTestId)
                savedMarks.forEach { m ->
                    marksEntryMap[m.studentId] = MarksEntryState(
                        studentId = m.studentId,
                        studentName = m.studentName,
                        marksText = if (m.isAbsent) "" else (m.obtainedMarks?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""),
                        isAbsent = m.isAbsent
                    )
                }
                isLoadedFromExisting = true
            }
        }
    }

    // Ensure all class students exist in entry map even if new student added
    LaunchedEffect(classStudents, isLoadedFromExisting) {
        classStudents.forEach { s ->
            if (!marksEntryMap.containsKey(s.studentId)) {
                marksEntryMap[s.studentId] = MarksEntryState(
                    studentId = s.studentId,
                    studentName = s.studentName,
                    marksText = "",
                    isAbsent = false
                )
            }
        }
    }

    val isSaving by testExamViewModel.isSaving.collectAsState()

    val standardSubjects = listOf(
        "Mathematics",
        "Science",
        "Hindi",
        "English",
        "Social Studies",
        "Sanskrit",
        "Computer",
        "Art / Drawing",
        "General Knowledge",
        "Other / Custom"
    )

    // Formatted date string
    val formattedDisplayDate = remember(testDateIso) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = parser.parse(testDateIso)
            if (date != null) formatter.format(date) else testDateIso
        } catch (_: Exception) {
            testDateIso
        }
    }

    // DatePicker trigger
    fun openDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = parser.parse(testDateIso)
            if (d != null) calendar.time = d
        } catch (_: Exception) {}

        val dpd = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                testDateIso = formatted
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (existingTestId == null) "Add Test Marks" else "Edit Test Marks",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Class $className • ${classStudents.size} Students",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_add_edit_test")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Save Action in Top Bar
                    TextButton(
                        onClick = {
                            saveTestRecord(
                                existingTestId = existingTestId,
                                classId = classId,
                                className = className,
                                testTitle = testTitle,
                                selectedSubject = if (selectedSubject == "Other / Custom") customSubject else selectedSubject,
                                testDateIso = testDateIso,
                                totalMarks = totalMarks,
                                currentUser = currentUser,
                                marksEntryMap = marksEntryMap,
                                testExamViewModel = testExamViewModel,
                                context = context,
                                onSuccess = onBack
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("btn_save_test_top")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("SAVE", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                // Card 1: Test Details (Title, Subject, Date, Total Marks)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Test Information",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 1. Test Title
                        OutlinedTextField(
                            value = testTitle,
                            onValueChange = { testTitle = it },
                            label = { Text("Test Title / Exam Name") },
                            placeholder = { Text("e.g. Weekly Test 1, Unit Test") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_test_title"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Quick Title Suggestions Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Weekly Test", "Monthly Test", "Unit Test", "Revision").forEach { chip ->
                                FilterChip(
                                    selected = testTitle == chip,
                                    onClick = { testTitle = chip },
                                    label = { Text(chip, fontSize = 11.sp) }
                                )
                            }
                        }

                        // 2. Subject Selection Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedSubject,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Subject") },
                                trailingIcon = {
                                    IconButton(onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSubjectDropdownOpen = true }
                                    .testTag("dropdown_subject"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            DropdownMenu(
                                expanded = isSubjectDropdownOpen,
                                onDismissRequest = { isSubjectDropdownOpen = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                standardSubjects.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub, fontSize = 14.sp) },
                                        onClick = {
                                            selectedSubject = sub
                                            isSubjectDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Custom Subject Text field if "Other / Custom" selected
                        if (selectedSubject == "Other / Custom") {
                            OutlinedTextField(
                                value = customSubject,
                                onValueChange = { customSubject = it },
                                label = { Text("Enter Custom Subject Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_custom_subject"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // 3. Test Date & Total Marks in Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Date Picker Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(56.dp)
                                    .clickable { openDatePicker() }
                                    .testTag("box_test_date"),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Test Date", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = formattedDisplayDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = "Pick Date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Total Marks Input
                            OutlinedTextField(
                                value = totalMarksText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        totalMarksText = input
                                    }
                                },
                                label = { Text("Total Marks") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("input_total_marks"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Quick Total Marks chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Presets:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf("10", "20", "25", "50", "100").forEach { preset ->
                                FilterChip(
                                    selected = totalMarksText == preset,
                                    onClick = { totalMarksText = preset },
                                    label = { Text("$preset M", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Section Header: Student Marks Entry
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Student Marks (${classStudents.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fill marks or tap 'AB' if absent (Max: ${totalMarks.toInt()})",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Auto-fill Absent all remaining or quick action
                    TextButton(
                        onClick = {
                            marksEntryMap.forEach { (id, state) ->
                                if (state.marksText.isBlank() && !state.isAbsent) {
                                    marksEntryMap[id] = state.copy(isAbsent = true)
                                }
                            }
                        }
                    ) {
                        Text("Mark Empty AB", fontSize = 11.sp)
                    }
                }
            }

            // Student Rows
            if (classStudents.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.PeopleOutline, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No active students in class $className", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                itemsIndexed(classStudents, key = { _, s -> s.studentId }) { index, student ->
                    val entryState = marksEntryMap[student.studentId] ?: MarksEntryState(
                        studentId = student.studentId,
                        studentName = student.studentName,
                        marksText = "",
                        isAbsent = false
                    )

                    val enteredNum = entryState.marksText.toDoubleOrNull()
                    val isExceeding = enteredNum != null && enteredNum > totalMarks

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (entryState.isAbsent) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isExceeding) Color(0xFFDC2626) else if (entryState.isAbsent) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Roll/Index + Student ID & Name
                            Row(
                                modifier = Modifier.weight(1.3f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column {
                                    Text(
                                        text = student.studentName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = student.studentId,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Right: Absent Toggle + Obtained Marks Input
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Absent Button ("AB")
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (entryState.isAbsent) Color(0xFFDC2626) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clickable {
                                            val newAbsent = !entryState.isAbsent
                                            marksEntryMap[student.studentId] = entryState.copy(
                                                isAbsent = newAbsent,
                                                marksText = if (newAbsent) "" else entryState.marksText
                                            )
                                        }
                                        .testTag("btn_absent_${student.studentId}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "AB",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entryState.isAbsent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Marks Input Field
                                OutlinedTextField(
                                    value = if (entryState.isAbsent) "" else entryState.marksText,
                                    onValueChange = { input ->
                                        if (!entryState.isAbsent) {
                                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                                marksEntryMap[student.studentId] = entryState.copy(marksText = input)
                                            }
                                        }
                                    },
                                    placeholder = { Text("Marks", fontSize = 11.sp) },
                                    enabled = !entryState.isAbsent,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = if (index == classStudents.size - 1) ImeAction.Done else ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(52.dp)
                                        .testTag("input_marks_${student.studentId}"),
                                    shape = RoundedCornerShape(8.dp),
                                    isError = isExceeding
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Spacing & Submit Button
            item {
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        saveTestRecord(
                            existingTestId = existingTestId,
                            classId = classId,
                            className = className,
                            testTitle = testTitle,
                            selectedSubject = if (selectedSubject == "Other / Custom") customSubject else selectedSubject,
                            testDateIso = testDateIso,
                            totalMarks = totalMarks,
                            currentUser = currentUser,
                            marksEntryMap = marksEntryMap,
                            testExamViewModel = testExamViewModel,
                            context = context,
                            onSuccess = onBack
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_save_test_bottom"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Saving Test & Marks...")
                    } else {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (existingTestId == null) "Save Test & Marks" else "Update Test & Marks",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

data class MarksEntryState(
    val studentId: String,
    val studentName: String,
    val marksText: String,
    val isAbsent: Boolean
)

private fun saveTestRecord(
    existingTestId: String?,
    classId: String,
    className: String,
    testTitle: String,
    selectedSubject: String,
    testDateIso: String,
    totalMarks: Double,
    currentUser: UserEntity,
    marksEntryMap: Map<String, MarksEntryState>,
    testExamViewModel: TestExamViewModel,
    context: android.content.Context,
    onSuccess: () -> Unit
) {
    if (testTitle.isBlank()) {
        Toast.makeText(context, "Please enter a test title", Toast.LENGTH_SHORT).show()
        return
    }
    if (selectedSubject.isBlank()) {
        Toast.makeText(context, "Please select or enter a subject", Toast.LENGTH_SHORT).show()
        return
    }
    if (totalMarks <= 0.0) {
        Toast.makeText(context, "Total marks must be greater than 0", Toast.LENGTH_SHORT).show()
        return
    }

    // Check if any marks exceed total marks
    val hasExceeding = marksEntryMap.values.any { entry ->
        !entry.isAbsent && (entry.marksText.toDoubleOrNull() ?: 0.0) > totalMarks
    }
    if (hasExceeding) {
        Toast.makeText(context, "Some obtained marks exceed the total marks of ${totalMarks.toInt()}", Toast.LENGTH_LONG).show()
        return
    }

    val finalTestId = existingTestId ?: "TEST_${classId}_${System.currentTimeMillis()}"

    val testEntity = ClassTestEntity(
        testId = finalTestId,
        classId = classId,
        className = className,
        testTitle = testTitle.trim(),
        subject = selectedSubject.trim(),
        testDate = testDateIso,
        totalMarks = totalMarks,
        passingMarks = totalMarks * 0.33,
        teacherId = currentUser.userId,
        teacherName = currentUser.fullName.ifBlank { currentUser.username },
        createdTimestamp = System.currentTimeMillis(),
        lastModifiedTimestamp = System.currentTimeMillis()
    )

    val marksEntities = marksEntryMap.values.map { entry ->
        val obtained = if (entry.isAbsent) null else entry.marksText.toDoubleOrNull()
        StudentTestMarksEntity(
            markId = "MARK_${finalTestId}_${entry.studentId}",
            testId = finalTestId,
            studentId = entry.studentId,
            studentName = entry.studentName,
            classId = classId,
            obtainedMarks = obtained,
            isAbsent = entry.isAbsent,
            remark = if (entry.isAbsent) "Absent" else "",
            lastModifiedTimestamp = System.currentTimeMillis()
        )
    }

    testExamViewModel.saveTest(
        test = testEntity,
        marks = marksEntities,
        onSuccess = {
            Toast.makeText(context, "Test & Marks saved successfully!", Toast.LENGTH_SHORT).show()
            onSuccess()
        },
        onError = { err ->
            Toast.makeText(context, "Error saving test: $err", Toast.LENGTH_LONG).show()
        }
    )
}
