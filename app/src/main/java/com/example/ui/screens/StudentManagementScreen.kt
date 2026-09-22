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
import com.example.data.entity.StudentEntity
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.components.StudentFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(
    studentViewModel: StudentViewModel,
    classViewModel: ClassViewModel,
    isTeacherView: Boolean = false,
    assignedClassIds: List<String> = emptyList(),
    onViewStudentHistory: (studentId: String) -> Unit = {}
) {
    val filterState by studentViewModel.filterState.collectAsState()
    val allClasses by classViewModel.allClasses.collectAsState()

    val studentsList by if (isTeacherView) {
        studentViewModel.getTeacherStudents(assignedClassIds).collectAsState(initial = emptyList())
    } else {
        studentViewModel.filteredStudents.collectAsState()
    }

    var showAddStudentDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var viewingStudentDetail by remember { mutableStateOf<StudentEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddStudentDialog = true },
                containerColor = JaitiPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_student_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Student")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                text = "Student Directory",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Search, filter, and manage enrolled children across slum centers.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            SearchAndFilterBar(
                query = filterState.searchQuery,
                onQueryChange = { studentViewModel.setSearchQuery(it) },
                placeholderText = "Search by student name, ID, father, mother..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (Class & Gender)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Class Dropdown Filter
                var expandedClassMenu by remember { mutableStateOf(false) }
                val selectedClassName = allClasses.find { it.classId == filterState.selectedClassId }?.className ?: "All Classes"

                Box {
                    FilterChip(
                        selected = filterState.selectedClassId != null,
                        onClick = { expandedClassMenu = true },
                        label = { Text(selectedClassName, fontSize = 12.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("filter_class_chip")
                    )

                    DropdownMenu(
                        expanded = expandedClassMenu,
                        onDismissRequest = { expandedClassMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Classes") },
                            onClick = {
                                studentViewModel.setClassFilter(null)
                                expandedClassMenu = false
                            }
                        )
                        allClasses.forEach { cls ->
                            DropdownMenuItem(
                                text = { Text(cls.className) },
                                onClick = {
                                    studentViewModel.setClassFilter(cls.classId)
                                    expandedClassMenu = false
                                }
                            )
                        }
                    }
                }

                // Gender Filter Chips
                FilterChip(
                    selected = filterState.selectedGender == "Female",
                    onClick = {
                        val newGender = if (filterState.selectedGender == "Female") null else "Female"
                        studentViewModel.setGenderFilter(newGender)
                    },
                    label = { Text("Girls", fontSize = 12.sp) }
                )

                FilterChip(
                    selected = filterState.selectedGender == "Male",
                    onClick = {
                        val newGender = if (filterState.selectedGender == "Male") null else "Male"
                        studentViewModel.setGenderFilter(newGender)
                    },
                    label = { Text("Boys", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Count Indicator
            Text(
                text = "Showing ${studentsList.size} students",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Student List
            if (studentsList.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No students match the selected filter criteria.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(studentsList, key = { it.studentId }) { student ->
                        val studentClass = allClasses.find { it.classId == student.classId }
                        StudentCardItem(
                            student = student,
                            className = studentClass?.className ?: "Class ${student.classId}",
                            onCardClick = { viewingStudentDetail = student },
                            onEditClick = { editingStudent = student },
                            onToggleActive = { studentViewModel.toggleStudentActive(student.studentId, student.active) },
                            onViewHistory = { onViewStudentHistory(student.studentId) }
                        )
                    }
                }
            }
        }
    }

    // Add Student Dialog
    if (showAddStudentDialog) {
        StudentFormDialog(
            classes = allClasses,
            initialStudent = null,
            isAdmin = true,
            onSave = { name, father, mother, classId, phone, photo, dob, schoolClass, gender, aadharUri, birthCertUri, consentUri ->
                studentViewModel.addStudent(
                    name = name,
                    fatherName = father,
                    motherName = mother,
                    classId = classId,
                    phoneNumber = phone,
                    photoUri = photo,
                    dob = dob,
                    schoolClass = schoolClass,
                    gender = gender,
                    aadharCardUri = aadharUri,
                    birthCertificateUri = birthCertUri,
                    consentFormUri = consentUri
                )
                showAddStudentDialog = false
            },
            onDismiss = { showAddStudentDialog = false }
        )
    }

    // Edit Student Dialog
    if (editingStudent != null) {
        StudentFormDialog(
            classes = allClasses,
            initialStudent = editingStudent,
            isAdmin = true,
            onSave = { name, father, mother, classId, phone, photo, dob, schoolClass, gender, aadharUri, birthCertUri, consentUri ->
                studentViewModel.updateStudent(
                    editingStudent!!.copy(
                        studentName = name,
                        fatherName = father,
                        motherName = mother,
                        classId = classId,
                        phoneNumber = phone,
                        photoUri = photo,
                        dob = dob,
                        schoolClass = schoolClass,
                        gender = gender,
                        aadharCardUri = aadharUri,
                        birthCertificateUri = birthCertUri,
                        consentFormUri = consentUri
                    )
                )
                editingStudent = null
            },
            onDelete = { studentToDelete ->
                studentViewModel.deleteStudent(studentToDelete)
                editingStudent = null
            },
            onDismiss = { editingStudent = null }
        )
    }

    // View Student Profile Detail Dialog
    if (viewingStudentDetail != null) {
        val s = viewingStudentDetail!!
        val studentClass = allClasses.find { it.classId == s.classId }
        AlertDialog(
            onDismissRequest = { viewingStudentDetail = null },
            title = { Text("Student Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${s.studentName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Student ID: ${s.studentId}", fontSize = 13.sp, color = JaitiPrimary)
                    Divider()
                    Text("Jaiti Batch: ${studentClass?.className ?: s.classId}", fontSize = 14.sp)
                    if (s.schoolClass.isNotBlank()) {
                        Text("School Class: ${s.schoolClass}", fontSize = 14.sp)
                    }
                    if (s.dob.isNotBlank()) {
                        Text("DOB: ${s.dob}", fontSize = 14.sp)
                    }
                    Text("Gender & Age: ${s.gender}, ${s.age} years old", fontSize = 14.sp)
                    if (s.fatherName.isNotBlank()) {
                        Text("Father's Name: ${s.fatherName}", fontSize = 14.sp)
                    }
                    if (s.motherName.isNotBlank()) {
                        Text("Mother's Name: ${s.motherName}", fontSize = 14.sp)
                    }
                    if (s.phoneNumber.isNotBlank()) {
                        Text("Phone: ${s.phoneNumber}", fontSize = 14.sp)
                    }
                    Text("Status: ${if (s.active) "Active Enrolled" else "Inactive / Left"}", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sId = s.studentId
                    viewingStudentDetail = null
                    onViewStudentHistory(sId)
                }) {
                    Text("View Attendance History")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewingStudentDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StudentCardItem(
    student: StudentEntity,
    className: String,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onToggleActive: () -> Unit,
    onViewHistory: () -> Unit
) {
    Card(
        onClick = onCardClick,
        colors = CardDefaults.cardColors(
            containerColor = if (student.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.studentName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${student.studentId} • $className",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = JaitiPrimary
                    )
                }

                Surface(
                    color = if (student.active) JaitiPrimaryContainer else OfflineBadgeBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (student.active) "ACTIVE" else "INACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (student.active) JaitiPrimary else OfflineBadgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Father: ${student.fatherName} | Mother: ${student.motherName}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Area: ${student.areaName} (${student.gender}, ${student.age} yrs)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Student", tint = JaitiPrimary, modifier = Modifier.size(18.dp))
                    }

                    TextButton(
                        onClick = onToggleActive,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (student.active) "Deactivate" else "Activate", fontSize = 11.sp)
                    }
                }

                FilledTonalButton(
                    onClick = onViewHistory,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History", fontSize = 11.sp)
                }
            }
        }
    }
}

