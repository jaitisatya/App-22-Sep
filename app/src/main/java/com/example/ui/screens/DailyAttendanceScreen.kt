package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role
import com.example.data.repository.EducatorManager
import com.example.ui.components.EducatorFormDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.StudentAttendanceHistoryDialog
import com.example.ui.components.StudentDetailDialog
import com.example.ui.components.StudentFormDialog
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAttendanceScreen(
    classId: String,
    className: String,
    dateIso: String = DateUtils.getTodayIso(),
    currentUser: UserEntity,
    attendanceViewModel: AttendanceViewModel,
    studentViewModel: StudentViewModel? = null,
    classViewModel: ClassViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isEducatorsClass = classId == "CLASS_EDUCATORS"

    LaunchedEffect(Unit) {
        EducatorManager.init(context)
        com.example.data.repository.ClassPhotoManager.startListening(context)
        com.example.data.repository.ClassPhotoManager.syncAllDailyPhotosFromCloud(context)
    }

    val uiState by attendanceViewModel.uiState.collectAsState()
    val isRefreshing by attendanceViewModel.isRefreshing.collectAsState()

    val classes by classViewModel?.allClasses?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val allAttendanceRecords by studentViewModel?.allAttendanceRecords?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    val firstDayOfMonth = remember { DateUtils.getFirstDayOfCurrentMonthIso() }
    val todayIso = remember { DateUtils.getTodayIso() }

    var searchQuery by remember { mutableStateOf("") }
    var fullScreenPhotoStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var selectedStudentForDetail by remember { mutableStateOf<StudentEntity?>(null) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var studentForAttendanceHistory by remember { mutableStateOf<Pair<StudentEntity, List<AttendanceRecordEntity>>?>(null) }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showClassPhotoDialog by remember { mutableStateOf(false) }
    var showAddStudentToClassDialog by remember { mutableStateOf(false) }
    var showFullAddStudentDialog by remember { mutableStateOf(false) }
    var showAddEducatorDialog by remember { mutableStateOf(false) }
    var showNotifyDialog by remember { mutableStateOf(false) }
    var activeRemarkStudentId by remember { mutableStateOf<String?>(null) }
    var remarkText by remember { mutableStateOf("") }

    val isAdmin = currentUser.isMasterAdmin || currentUser.role == Role.ADMIN

    // If editing a student, show full StudentInfoScreen
    editingStudent?.let { studentToEdit ->
        StudentInfoScreen(
            student = studentToEdit,
            classes = classes,
            isAdmin = isAdmin,
            onBack = { editingStudent = null },
            onSave = { updatedStudent ->
                studentViewModel?.updateStudent(updatedStudent)
                editingStudent = null
                attendanceViewModel.refreshAttendanceData(classId = classId, className = className) { _, _ -> }
            },
            onDelete = if (isAdmin) {
                { studentToDelete ->
                    studentViewModel?.deleteStudent(studentToDelete)
                    editingStudent = null
                    attendanceViewModel.refreshAttendanceData(classId = classId, className = className) { _, _ -> }
                }
            } else null
        )
        return
    }

    // Initialize data for selected class and date
    LaunchedEffect(classId, dateIso) {
        attendanceViewModel.selectClassAndDate(classId, className, dateIso)
    }

    val filteredStudents = remember(uiState.students, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.students
        } else {
            uiState.students.filter {
                it.studentName.contains(searchQuery, ignoreCase = true) ||
                it.fatherName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalCount = uiState.students.size
    val presentCount = remember(uiState.students, uiState.attendanceMap) {
        uiState.students.count { student ->
            uiState.attendanceMap[student.studentId] == AttendanceStatus.PRESENT
        }
    }
    val absentCount = remember(uiState.students, uiState.attendanceMap) {
        uiState.students.count { student ->
            uiState.attendanceMap[student.studentId] == AttendanceStatus.ABSENT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = className.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            IconButton(
                                onClick = {
                                    if (isEducatorsClass) {
                                        showAddEducatorDialog = true
                                    } else {
                                        if (studentViewModel != null && classViewModel != null) {
                                            showFullAddStudentDialog = true
                                        } else {
                                            showAddStudentToClassDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("top_add_student_btn")
                            ) {
                                Icon(
                                    imageVector = if (isEducatorsClass) Icons.Default.Add else Icons.Outlined.PersonAdd,
                                    contentDescription = if (isEducatorsClass) "Add Educator" else "Add Student",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        val isToday = dateIso == DateUtils.getTodayIso()
                        val isYesterday = dateIso == DateUtils.getYesterdayIso()
                        val dateLabel = when {
                            isToday -> "Today • ${DateUtils.formatIsoToDisplay(dateIso)}"
                            isYesterday -> "Yesterday • ${DateUtils.formatIsoToDisplay(dateIso)}"
                            else -> DateUtils.formatIsoToDisplay(dateIso)
                        }
                        Text(
                            text = dateLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("attendance_back_btn")) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                com.example.data.repository.ClassPhotoManager.syncAllDailyPhotosFromCloud(context)
                            }
                            attendanceViewModel.refreshAttendanceData(classId = classId, className = className, dateIso = dateIso) { success, msg ->
                                Toast.makeText(context, if (success) "✓ $msg" else "⚠️ $msg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("daily_attendance_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync from Firestore",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Daily Class Photo Upload Button (replaces P All button)
                    IconButton(
                        onClick = { showClassPhotoDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("top_class_photo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Upload Daily Class Photo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Save Button in Top Bar - Bordered Pill like P All
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = if (uiState.isSaving) 0.1f else 0.2f),
                        border = BorderStroke(1.5.dp, Color.White),
                        onClick = {
                            if (!uiState.isSaving) {
                                attendanceViewModel.saveAttendance(
                                    teacherId = currentUser.userId,
                                    teacherName = currentUser.fullName,
                                    onSuccess = {
                                        Toast.makeText(context, "Attendance Saved Successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("top_save_attendance_btn")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Save",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Past Date Banner
            if (dateIso != DateUtils.getTodayIso()) {
                Surface(
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Viewing Past Record: ${DateUtils.formatIsoToDisplay(dateIso)}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (uiState.isAlreadyTaken) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = if (uiState.isAlreadyTaken) "Recorded" else "Not Recorded",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isAlreadyTaken) Color(0xFF15803D) else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Search / Pick Students field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isEducatorsClass) "Search Educators" else "Pick Students", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("pick_students_search")
            )

            // Count Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$totalCount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Total: $totalCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Present: $presentCount  •  Absent: $absentCount",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.lastSyncTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(0.5.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Live",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Student / Educator Attendance Rows List with Dual P/A Buttons and Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    attendanceViewModel.refreshAttendanceData(classId = classId, className = className) { success, msg ->
                        Toast.makeText(context, if (success) "✓ $msg" else "⚠️ $msg", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("daily_attendance_pull_to_refresh")
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredStudents, key = { it.studentId }) { student ->
                        val status = uiState.attendanceMap[student.studentId] ?: AttendanceStatus.PRESENT
                        val remark = uiState.remarkMap[student.studentId] ?: ""

                        StudentInsideClassRow(
                            student = student,
                            status = status,
                            remark = remark,
                            onPhotoClick = {
                                fullScreenPhotoStudent = student
                            },
                            onSelectPresent = {
                                attendanceViewModel.setStudentStatus(student.studentId, AttendanceStatus.PRESENT, currentUser.userId, currentUser.fullName)
                            },
                            onSelectAbsent = {
                                attendanceViewModel.setStudentStatus(student.studentId, AttendanceStatus.ABSENT, currentUser.userId, currentUser.fullName)
                            },
                            onNameClick = {
                                if (!isEducatorsClass) {
                                    selectedStudentForDetail = student
                                } else {
                                    fullScreenPhotoStudent = student
                                }
                            },
                            onEditStudent = {
                                if (!isEducatorsClass) {
                                    editingStudent = student
                                }
                            },
                            onViewHistory = {
                                if (!isEducatorsClass) {
                                    val sRecords = allAttendanceRecords.filter { it.studentId == student.studentId }
                                    studentForAttendanceHistory = Pair(student, sRecords)
                                }
                            },
                            onAddRemark = {
                                activeRemarkStudentId = student.studentId
                                remarkText = remark
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }

    // Fullscreen photo face match zoom
    fullScreenPhotoStudent?.let { s ->
        FullScreenPhotoDialog(
            name = s.studentName,
            photoUri = s.photoUri.ifBlank { null },
            fatherName = s.fatherName.ifBlank { null },
            className = className,
            phoneNumber = s.phoneNumber.ifBlank { null },
            gender = s.gender,
            onDismiss = { fullScreenPhotoStudent = null }
        )
    }

    // Add Educator Dialog (When in Educators Class)
    if (showAddEducatorDialog) {
        EducatorFormDialog(
            initialEducator = null,
            onDismiss = { showAddEducatorDialog = false },
            onSave = { name, email, photoUri, phone, subject ->
                EducatorManager.addEducator(
                    context = context,
                    name = name,
                    email = email,
                    photoUri = photoUri,
                    phone = phone,
                    subject = subject
                )
                showAddEducatorDialog = false
            }
        )
    }

    // Student Detail Profile Dialog (with Cross top-right, Attendance Stats, History & Edit Buttons)
    selectedStudentForDetail?.let { student ->
        val sRecords = allAttendanceRecords.filter { it.studentId == student.studentId }
        val mPresent = sRecords.count { it.date >= firstDayOfMonth && it.date <= todayIso && it.status == AttendanceStatus.PRESENT }
        val totPresent = sRecords.count { it.status == AttendanceStatus.PRESENT }

        StudentDetailDialog(
            student = student,
            className = className,
            monthPresent = mPresent,
            totalPresent = totPresent,
            onPhotoClick = {
                selectedStudentForDetail = null
                fullScreenPhotoStudent = student
            },
            onViewHistory = {
                selectedStudentForDetail = null
                studentForAttendanceHistory = Pair(student, sRecords)
            },
            onEditStudent = {
                selectedStudentForDetail = null
                editingStudent = student
            },
            onDismiss = { selectedStudentForDetail = null }
        )
    }

    // Student Attendance History Dialog
    studentForAttendanceHistory?.let { (student, recs) ->
        StudentAttendanceHistoryDialog(
            student = student,
            className = className,
            allStudentRecords = recs,
            onDismiss = { studentForAttendanceHistory = null }
        )
    }

    // Add remark dialog
    if (activeRemarkStudentId != null) {
        val student = uiState.students.find { it.studentId == activeRemarkStudentId }
        AlertDialog(
            onDismissRequest = { activeRemarkStudentId = null },
            title = { Text("Remark: ${student?.studentName ?: ""}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    placeholder = { Text("e.g. Sick, Family function, Late...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeRemarkStudentId?.let { sId ->
                            attendanceViewModel.setStudentRemark(sId, remarkText, currentUser.userId, currentUser.fullName)
                        }
                        activeRemarkStudentId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { activeRemarkStudentId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Notify SMS / WhatsApp Dialog
    if (showNotifyDialog) {
        AlertDialog(
            onDismissRequest = { showNotifyDialog = false },
            title = { Text("Attendance Notification", fontWeight = FontWeight.Bold) },
            text = {
                Text("Send attendance summary notification to parents via SMS / WhatsApp:\n\nPresent: $presentCount students\nAbsent: $absentCount students")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifyDialog = false
                        Toast.makeText(context, "Notification sent to parents successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Send Notifications")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNotifyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Daily Class Photo Upload Dialog (< 100 KB)
    if (showClassPhotoDialog) {
        com.example.ui.components.ClassPhotoUploadDialog(
            classId = classId,
            className = className,
            selectedDateIso = uiState.selectedDateIso,
            onDismiss = { showClassPhotoDialog = false }
        )
    }

    // Full Complete Student Form Dialog when +person is tapped
    if (showFullAddStudentDialog && studentViewModel != null) {
        val allStudentsList by studentViewModel.allStudents.collectAsState()
        StudentFormDialog(
            classes = classes,
            initialStudent = null,
            isAdmin = currentUser.isMasterAdmin || currentUser.role == Role.ADMIN,
            onDismiss = { showFullAddStudentDialog = false },
            onSave = { name, father, mother, selectedClassId, phone, photo, dob, schoolClass, gender, aadharUri, birthCertUri, consentUri ->
                val targetClassId = if (selectedClassId.isNotBlank()) selectedClassId else classId
                studentViewModel.addStudent(
                    name = name,
                    fatherName = father,
                    motherName = mother,
                    classId = targetClassId,
                    phoneNumber = phone,
                    photoUri = photo,
                    dob = dob,
                    schoolClass = schoolClass,
                    gender = gender,
                    aadharCardUri = aadharUri,
                    birthCertificateUri = birthCertUri,
                    consentFormUri = consentUri
                )
                val newId = com.example.util.StudentIdUtils.getNextStudentId(allStudentsList)
                val newStudentEntity = StudentEntity(
                    studentId = newId,
                    studentName = name.trim(),
                    fatherName = father.trim(),
                    motherName = mother.trim(),
                    classId = targetClassId,
                    phoneNumber = phone.trim(),
                    photoUri = photo,
                    dob = dob,
                    schoolClass = schoolClass,
                    gender = gender,
                    aadharCardUri = aadharUri,
                    birthCertificateUri = birthCertUri,
                    consentFormUri = consentUri,
                    active = true
                )
                attendanceViewModel.addStudentDirectly(newStudentEntity)
                showFullAddStudentDialog = false
                Toast.makeText(context, "$name added successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add student to class dialog
    if (showAddStudentToClassDialog) {
        var newName by remember { mutableStateOf("") }
        var newFather by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddStudentToClassDialog = false },
            title = { Text("Add to $className", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFather,
                        onValueChange = { newFather = it },
                        label = { Text("Father / Guardian / Role") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newStudent = StudentEntity(
                                studentId = "STU_${System.currentTimeMillis() % 10000}",
                                studentName = newName.trim(),
                                age = 7,
                                gender = "Male",
                                fatherName = newFather.trim(),
                                motherName = "",
                                classId = classId,
                                areaName = "Centre",
                                schoolName = "Jaiti Learning Centre",
                                active = true
                            )
                            attendanceViewModel.addStudentDirectly(newStudent)
                            showAddStudentToClassDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddStudentToClassDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Class Attendance Help", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "• Tap 'P' (Green) for Present or 'A' (Red) for Absent.\n" +
                    "• Both buttons start in light blue and turn Green / Red upon selection.\n" +
                    "• Tap student photo for full-screen face verification zoom.\n" +
                    "• Tap student name to view detailed profile, edit information, or view attendance history.\n" +
                    "• Tap 'Save' at top right to record today's attendance."
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)) {
                    Text("Got it")
                }
            }
        )
    }

    if (uiState.saveMessage != null) {
        AlertDialog(
            onDismissRequest = { attendanceViewModel.clearMessages() },
            title = { Text("Saved!", fontWeight = FontWeight.Bold) },
            text = { Text(uiState.saveMessage ?: "Attendance saved successfully") },
            confirmButton = {
                Button(
                    onClick = {
                        attendanceViewModel.clearMessages()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { attendanceViewModel.clearMessages() },
            title = { Text("Notice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(uiState.errorMessage ?: "An unexpected error occurred") },
            confirmButton = {
                Button(
                    onClick = { attendanceViewModel.clearMessages() },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun StudentInsideClassRow(
    student: StudentEntity,
    status: AttendanceStatus,
    remark: String,
    onPhotoClick: () -> Unit,
    onSelectPresent: () -> Unit,
    onSelectAbsent: () -> Unit,
    onNameClick: () -> Unit,
    onEditStudent: () -> Unit = {},
    onViewHistory: () -> Unit = {},
    onAddRemark: () -> Unit = {}
) {
    val isPresent = status == AttendanceStatus.PRESENT
    val isAbsent = status == AttendanceStatus.ABSENT
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("inside_class_row_${student.studentName}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Photo on left (Tappable for full-screen zoom)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onPhotoClick() }
                .testTag("zoom_photo_${student.studentName}"),
            contentAlignment = Alignment.Center
        ) {
            if (student.photoUri.isNotBlank()) {
                AsyncImage(
                    model = ImageUtils.getImageModel(student.photoUri),
                    contentDescription = "Photo of ${student.studentName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initialLetter = student.studentName.firstOrNull()?.uppercase() ?: "S"
                Text(
                    text = initialLetter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Student Info (Tappable for profile)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onNameClick() }
        ) {
            Text(
                text = student.studentName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = student.fatherName.ifBlank { student.areaName },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (remark.isNotBlank()) {
                Text(
                    text = "Remark: $remark",
                    fontSize = 11.sp,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // P and A Dual Buttons: Both light mint initially, turn Green (P) or Red (A) when clicked
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [ P ] Button (Present)
            Surface(
                onClick = onSelectPresent,
                shape = RoundedCornerShape(8.dp),
                color = if (isPresent) Color(0xFF16A34A) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(width = 38.dp, height = 36.dp)
                    .testTag("btn_p_${student.studentName}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "P",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // [ A ] Button (Absent)
            Surface(
                onClick = onSelectAbsent,
                shape = RoundedCornerShape(8.dp),
                color = if (isAbsent) Color(0xFFDC2626) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isAbsent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(width = 38.dp, height = 36.dp)
                    .testTag("btn_a_${student.studentName}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "A",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Options 3-dots Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Student") },
                    onClick = {
                        showMenu = false
                        onEditStudent()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Attendance History") },
                    onClick = {
                        showMenu = false
                        onViewHistory()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.History, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("View Profile") },
                    onClick = {
                        showMenu = false
                        onNameClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add Remark") },
                    onClick = {
                        showMenu = false
                        onAddRemark()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.EditNote, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Zoom Photo") },
                    onClick = {
                        showMenu = false
                        onPhotoClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ZoomIn, contentDescription = null)
                    }
                )
            }
        }
    }
}
