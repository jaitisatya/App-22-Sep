package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassEntity
import com.example.data.entity.UserEntity
import com.example.data.model.Role
import com.example.ui.theme.*
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassesTabScreen(
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel,
    currentUser: UserEntity? = null,
    onNavigateToClassDetail: (classId: String, className: String) -> Unit,
    onNavigateToTakeAttendance: (classId: String, className: String) -> Unit = onNavigateToClassDetail
) {
    val isAdmin = currentUser?.isMasterAdmin == true || currentUser?.role == Role.ADMIN
    val canManageClasses = isAdmin || (currentUser?.canManageClasses == true)

    val classes by classViewModel.allClasses.collectAsState()
    val students by studentViewModel.allStudents.collectAsState()

    var showAddClassDialog by remember { mutableStateOf(false) }
    var newClassName by remember { mutableStateOf("") }
    var newClassTeacher by remember { mutableStateOf("") }

    // Selected class for long-press actions
    var selectedClassForActions by remember { mutableStateOf<ClassEntity?>(null) }
    var classToEdit by remember { mutableStateOf<ClassEntity?>(null) }
    var classToDelete by remember { mutableStateOf<ClassEntity?>(null) }

    var editClassName by remember { mutableStateOf("") }
    var editClassTeacher by remember { mutableStateOf("") }

    var showHelpDialog by remember { mutableStateOf(false) }

    // Map student count per class across all classes
    val studentCountMap = remember(classes, students) {
        classes.associate { cls ->
            cls.classId to students.count {
                it.classId.equals(cls.classId, ignoreCase = true) || it.classId.equals(cls.className, ignoreCase = true)
            }
        }
    }

    // Display classes/batches (J Prep, J1 to J5) having at least 1 student
    val displayClasses = remember(classes, studentCountMap) {
        classes.filter { cls ->
            val name = cls.className.trim().uppercase()
            val id = cls.classId.trim().uppercase()
            // Keep valid batches: J Prep, J1, J2, J3, J4, J5 with at least 1 student
            val isValidBatch = name == "J PREP" || name == "PREP" || id == "CLASS_J_PREP" ||
                    name in listOf("J1", "J2", "J3", "J4", "J5") ||
                    id in listOf("CLASS_J1", "CLASS_J2", "CLASS_J3", "CLASS_J4", "CLASS_J5")
            val count = studentCountMap[cls.classId] ?: 0
            isValidBatch && count > 0
        }.sortedWith(Comparator { c1, c2 ->
            com.example.util.BatchConstants.getBatchSortOrder(c1.className)
                .compareTo(com.example.util.BatchConstants.getBatchSortOrder(c2.className))
        })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Classes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (displayClasses.isEmpty()) "No active batches" else "${displayClasses.size} active batches",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (canManageClasses) {
                        IconButton(
                            onClick = { showAddClassDialog = true },
                            modifier = Modifier.testTag("add_class_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Class",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            if (canManageClasses) {
                FloatingActionButton(
                    onClick = { showAddClassDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_class_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Class")
                }
            }
        }
    ) { paddingValues ->
        if (displayClasses.isEmpty()) {
            // Clean Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Class,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Active Batches Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Batches will automatically appear here once students are enrolled in them. You can also add custom batches directly.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showAddClassDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Custom Batch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(displayClasses, key = { it.classId }) { cls ->
                        val count = studentCountMap[cls.classId] ?: 0

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onNavigateToClassDetail(cls.classId, cls.className)
                                    },
                                    onLongClick = if (isAdmin) {
                                        { selectedClassForActions = cls }
                                    } else null
                                )
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                                .testTag("class_row_${cls.className}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cls.className.uppercase(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    val rawTeacher = cls.primaryTeacherName.ifBlank { cls.roomOrLocation }
                                    val teacherDisplay = rawTeacher.trim()
                                    if (teacherDisplay.isNotBlank()) {
                                        Text(
                                            text = teacherDisplay,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$count students",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { selectedClassForActions = cls },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Clean light divider
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
        }
    }

    // Long Press Action Modal Sheet / Dialog
    if (selectedClassForActions != null) {
        val targetClass = selectedClassForActions!!
        AlertDialog(
            onDismissRequest = { selectedClassForActions = null },
            title = {
                Column {
                    Text(
                        text = targetClass.className.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B)
                    )
                    if (targetClass.roomOrLocation.isNotBlank()) {
                        Text(
                            text = targetClass.roomOrLocation,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose an action for this class:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isAdmin) {
                        // Modify Option
                        Surface(
                            onClick = {
                                classToEdit = targetClass
                                editClassName = targetClass.className
                                val rawTeacher = targetClass.primaryTeacherName.ifBlank { targetClass.roomOrLocation }
                                editClassTeacher = rawTeacher.trim()
                                selectedClassForActions = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Modify / Edit Class",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF15803D)
                                    )
                                    Text(
                                        text = "Rename class or update class teacher",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }

                        // Delete Option
                        Surface(
                            onClick = {
                                classToDelete = targetClass
                                selectedClassForActions = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Delete Class",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFFB91C1C)
                                    )
                                    Text(
                                        text = "Permanently remove from list",
                                        fontSize = 12.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedClassForActions = null }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    // Modify Class Dialog
    if (classToEdit != null) {
        val targetClass = classToEdit!!
        AlertDialog(
            onDismissRequest = { classToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Modify Class", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editClassName,
                        onValueChange = { editClassName = it },
                        label = { Text("Class Name *") },
                        placeholder = { Text("e.g. CLASS 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editClassTeacher,
                        onValueChange = { editClassTeacher = it },
                        label = { Text("Class Teacher") },
                        placeholder = { Text("e.g. Teacher Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editClassName.isNotBlank()) {
                            classViewModel.updateClassDetails(
                                classEntity = targetClass,
                                newName = editClassName.trim(),
                                newTeacherName = editClassTeacher.trim()
                            )
                            classToEdit = null
                        }
                    },
                    enabled = editClassName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { classToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Class Confirmation Dialog
    if (classToDelete != null) {
        val targetClass = classToDelete!!
        AlertDialog(
            onDismissRequest = { classToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Class?", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${targetClass.className}\"? This will remove the class from your active list and cloud database.",
                    fontSize = 14.sp,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        classViewModel.deleteClass(targetClass)
                        classToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { classToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Class Dialog
    if (showAddClassDialog) {
        AlertDialog(
            onDismissRequest = { showAddClassDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Class", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newClassName,
                        onValueChange = { newClassName = it },
                        label = { Text("Class Name *") },
                        placeholder = { Text("e.g. CLASS 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_class_name_input")
                    )
                    OutlinedTextField(
                        value = newClassTeacher,
                        onValueChange = { newClassTeacher = it },
                        label = { Text("Class Teacher") },
                        placeholder = { Text("e.g. Teacher Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_class_loc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newClassName.isNotBlank()) {
                            classViewModel.addClass(
                                className = newClassName.trim(),
                                roomOrLocation = "",
                                teacherId = "USR_ADMIN_1",
                                teacherName = newClassTeacher.trim().ifBlank { "Admin" }
                            )
                            newClassName = ""
                            newClassTeacher = ""
                            showAddClassDialog = false
                        }
                    },
                    enabled = newClassName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_add_class_btn")
                ) {
                    Text("Add Class", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddClassDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Classes Help & Tips", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tip: Long press on any class to Modify or Delete it.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("• Tap on any class to view and mark attendance.")
                    Text("• Tap '+' at the top right to add a new batch or class anytime.")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

