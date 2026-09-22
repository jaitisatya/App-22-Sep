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
import com.example.viewmodel.ClassViewModel

@Composable
fun ClassManagementScreen(
    classViewModel: ClassViewModel,
    onNavigateToTakeAttendance: (classId: String, className: String) -> Unit
) {
    val allClasses by classViewModel.allClasses.collectAsState()
    val teachers by classViewModel.allTeachers.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<ClassEntity?>(null) }
    var deletingClass by remember { mutableStateOf<ClassEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = JaitiPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_class_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Class")
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
                text = "Class & Center Management",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Manage educational centers, assigned teachers, and active classes.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (allClasses.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No classes found. Tap the '+' button to add a new class.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allClasses, key = { it.classId }) { classEntity ->
                        ClassCardItem(
                            classEntity = classEntity,
                            onEditClick = { editingClass = classEntity },
                            onDeleteClick = { deletingClass = classEntity },
                            onToggleActive = { classViewModel.toggleClassActive(classEntity) },
                            onTakeAttendance = { onNavigateToTakeAttendance(classEntity.classId, classEntity.className) }
                        )
                    }
                }
            }
        }
    }

    if (deletingClass != null) {
        val cls = deletingClass!!
        AlertDialog(
            onDismissRequest = { deletingClass = null },
            title = { Text("Delete Class?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${cls.className}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        classViewModel.deleteClass(cls)
                        deletingClass = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingClass = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        ClassFormDialog(
            title = "Add New Class",
            initialName = "",
            initialRoom = "",
            initialTeacherId = "",
            teachers = teachers,
            onConfirm = { name, room, teacherId, teacherName ->
                classViewModel.addClass(name, room, teacherId, teacherName)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editingClass != null) {
        val cls = editingClass!!
        ClassFormDialog(
            title = "Edit Class - ${cls.className}",
            initialName = cls.className,
            initialRoom = cls.roomOrLocation,
            initialTeacherId = cls.primaryTeacherId,
            teachers = teachers,
            onConfirm = { name, room, teacherId, teacherName ->
                classViewModel.updateClass(cls.copy(className = name, roomOrLocation = room), teacherId, teacherName)
                editingClass = null
            },
            onDismiss = { editingClass = null }
        )
    }
}

@Composable
fun ClassCardItem(
    classEntity: ClassEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleActive: () -> Unit,
    onTakeAttendance: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (classEntity.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = classEntity.className,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${classEntity.classId} • Location: ${classEntity.roomOrLocation.ifBlank { "Not specified" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (classEntity.active) JaitiPrimaryContainer else OfflineBadgeBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (classEntity.active) "ACTIVE" else "INACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (classEntity.active) JaitiPrimary else OfflineBadgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = JaitiSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Teacher: ${classEntity.primaryTeacherName.ifBlank { "Unassigned" }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onEditClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = onTakeAttendance,
                    colors = ButtonDefaults.buttonColors(containerColor = JaitiPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Take Attendance", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassFormDialog(
    title: String,
    initialName: String,
    initialRoom: String,
    initialTeacherId: String,
    teachers: List<UserEntity>,
    onConfirm: (name: String, room: String, teacherId: String, teacherName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var room by remember { mutableStateOf(initialRoom) }
    var selectedTeacherId by remember { mutableStateOf(initialTeacherId) }
    var expandedTeacherDropdown by remember { mutableStateOf(false) }

    val selectedTeacher = teachers.find { it.userId == selectedTeacherId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class / Center Name") },
                    placeholder = { Text("e.g. J1 - Primary") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("class_name_input")
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / Location") },
                    placeholder = { Text("e.g. Sanjay Camp Room A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("class_room_input")
                )

                // Teacher Dropdown Selection
                ExposedDropdownMenuBox(
                    expanded = expandedTeacherDropdown,
                    onExpandedChange = { expandedTeacherDropdown = !expandedTeacherDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedTeacher?.fullName ?: "Select Primary Teacher",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Primary Teacher") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTeacherDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedTeacherDropdown,
                        onDismissRequest = { expandedTeacherDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None / Unassigned") },
                            onClick = {
                                selectedTeacherId = ""
                                expandedTeacherDropdown = false
                            }
                        )
                        teachers.forEach { teacher ->
                            DropdownMenuItem(
                                text = { Text(teacher.fullName) },
                                onClick = {
                                    selectedTeacherId = teacher.userId
                                    expandedTeacherDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tName = teachers.find { it.userId == selectedTeacherId }?.fullName ?: ""
                    onConfirm(name, room, selectedTeacherId, tName)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_class_dialog_btn")
            ) {
                Text("Save Class")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
