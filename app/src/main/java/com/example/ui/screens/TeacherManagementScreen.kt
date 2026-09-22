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
fun TeacherManagementScreen(
    classViewModel: ClassViewModel
) {
    val teachers by classViewModel.allTeachers.collectAsState()
    val classes by classViewModel.allClasses.collectAsState()

    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<UserEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTeacherDialog = true },
                containerColor = JaitiPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_teacher_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Teacher")
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
                text = "Teacher Staff Directory",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Manage foundation teachers and their assigned educational centers.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (teachers.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No teachers registered yet. Tap '+' to add a teacher.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(teachers, key = { it.userId }) { teacher ->
                        val assignedClassNames = classes
                            .filter { teacher.getAssignedClassIds().contains(it.classId) || it.primaryTeacherId == teacher.userId }
                            .map { it.className }

                        TeacherCardItem(
                            teacher = teacher,
                            assignedClasses = assignedClassNames,
                            onEditClick = { editingTeacher = teacher }
                        )
                    }
                }
            }
        }
    }

    if (showAddTeacherDialog) {
        TeacherFormDialog(
            title = "Add New Teacher",
            classes = classes,
            initialTeacher = null,
            onConfirm = { username, password, fullName, phone, assignedIds ->
                classViewModel.addTeacher(username, password, fullName, phone, assignedIds)
                showAddTeacherDialog = false
            },
            onDismiss = { showAddTeacherDialog = false }
        )
    }

    if (editingTeacher != null) {
        TeacherFormDialog(
            title = "Edit Teacher - ${editingTeacher!!.fullName}",
            classes = classes,
            initialTeacher = editingTeacher,
            onConfirm = { username, password, fullName, phone, assignedIds ->
                classViewModel.updateTeacher(
                    editingTeacher!!.copy(
                        username = username,
                        passwordHash = if (password.isBlank()) editingTeacher!!.passwordHash else password,
                        fullName = fullName,
                        phone = phone,
                        assignedClassIdsCsv = assignedIds.joinToString(",")
                    )
                )
                editingTeacher = null
            },
            onDismiss = { editingTeacher = null }
        )
    }
}

@Composable
fun TeacherCardItem(
    teacher: UserEntity,
    assignedClasses: List<String>,
    onEditClick: () -> Unit
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = teacher.fullName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Username: ${teacher.username} • Phone: ${teacher.phone.ifBlank { "N/A" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (teacher.active) JaitiPrimaryContainer else OfflineBadgeBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (teacher.active) "ACTIVE" else "INACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (teacher.active) JaitiPrimary else OfflineBadgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Class,
                        contentDescription = null,
                        tint = JaitiSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (assignedClasses.isNotEmpty()) {
                            "Assigned: ${assignedClasses.joinToString()}"
                        } else "No class assigned",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Info & Classes", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherFormDialog(
    title: String,
    classes: List<ClassEntity>,
    initialTeacher: UserEntity?,
    onConfirm: (username: String, password: String, fullName: String, phone: String, assignedIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(initialTeacher?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf(initialTeacher?.fullName ?: "") }
    var phone by remember { mutableStateOf(initialTeacher?.phone ?: "") }

    val currentAssigned = remember(initialTeacher) {
        initialTeacher?.getAssignedClassIds()?.toMutableStateList() ?: mutableStateListOf<String>()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Teacher Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (initialTeacher == null) "Password *" else "New Password (Leave blank to keep)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Assign Classes to Teacher:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                items(classes) { cls ->
                    val isChecked = currentAssigned.contains(cls.classId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) currentAssigned.add(cls.classId)
                                else currentAssigned.remove(cls.classId)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(cls.className, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(username, password, fullName, phone, currentAssigned.toList()) },
                enabled = fullName.isNotBlank() && username.isNotBlank() && (initialTeacher != null || password.isNotBlank())
            ) {
                Text("Save Teacher")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
