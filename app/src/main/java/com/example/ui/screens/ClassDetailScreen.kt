package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserEntity
import com.example.data.model.EducatorProfile
import com.example.data.model.Role
import com.example.data.repository.EducatorManager
import com.example.ui.components.EducatorFormDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    classId: String,
    className: String,
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel,
    currentUser: UserEntity? = null,
    onBack: () -> Unit,
    onNavigateToClassStudents: (classId: String, className: String) -> Unit = { _, _ -> },
    onNavigateToClassTests: (classId: String, className: String) -> Unit = { _, _ -> },
    onNavigateToAddTestMarks: (classId: String, className: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val isAdmin = currentUser?.isMasterAdmin == true || currentUser?.role == Role.ADMIN

    // Ensure educator list is active
    LaunchedEffect(Unit) {
        EducatorManager.init(context)
    }

    val educators by EducatorManager.educators.collectAsState()
    val classes by classViewModel.allClasses.collectAsState()
    val students by studentViewModel.allStudents.collectAsState()

    // Find the current class entity dynamically
    val currentClass = remember(classes, classId, className) {
        classes.find {
            it.classId.equals(classId, ignoreCase = true) ||
            it.className.equals(className, ignoreCase = true)
        }
    }

    // Display Class Name (e.g. J1, J5)
    val displayClassName = (currentClass?.className ?: className).uppercase()

    // Teacher Name
    val rawTeacher = currentClass?.primaryTeacherName?.ifBlank { currentClass.roomOrLocation } ?: ""
    val displayTeacher = if (rawTeacher.isNotBlank()) rawTeacher.trim() else "Not Assigned"

    // Number of students in this class
    val studentCount = remember(students, classId, className, currentClass) {
        val targetId = currentClass?.classId ?: classId
        val targetName = currentClass?.className ?: className
        students.count { s ->
            s.classId.equals(targetId, ignoreCase = true) ||
            s.classId.equals(targetName, ignoreCase = true) ||
            s.classId.replace("CLASS_", "", ignoreCase = true).equals(targetName.replace("CLASS_", "", ignoreCase = true), ignoreCase = true)
        }
    }

    // Dialog and BottomSheet States
    var selectedEducatorForProfile by remember { mutableStateOf<EducatorProfile?>(null) }
    var educatorForFullScreenPhoto by remember { mutableStateOf<EducatorProfile?>(null) }
    var showTestExamOptionsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayClassName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Batch Overview",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("class_detail_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Class Name Card (Header Display)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("class_detail_name_card")
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayClassName.take(3),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Class Name",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayClassName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Class Teacher Button (Clickable -> Shows assigned teacher's full educator profile)
            ClassDetailActionButton(
                testTag = "btn_class_teacher",
                title = "Class Teacher",
                valueText = displayTeacher,
                icon = Icons.Outlined.Person,
                iconBgColor = Color(0xFFE0F2FE),
                iconTint = Color(0xFF0284C7),
                onClick = {
                    if (displayTeacher.isNotBlank() && displayTeacher != "Not Assigned") {
                        // Find matching educator in educator database
                        val matched = educators.find { edu ->
                            edu.name.trim().equals(displayTeacher.trim(), ignoreCase = true) ||
                            edu.name.contains(displayTeacher, ignoreCase = true) ||
                            displayTeacher.contains(edu.name, ignoreCase = true)
                        }
                        if (matched != null) {
                            selectedEducatorForProfile = matched
                        } else {
                            // Synthesize profile if not present in explicit list
                            selectedEducatorForProfile = EducatorProfile(
                                id = "temp_${displayTeacher.hashCode()}",
                                name = displayTeacher,
                                email = "",
                                photoUri = "",
                                phone = "",
                                subject = "$displayClassName Class Teacher"
                            )
                        }
                    } else {
                        Toast.makeText(context, "No class teacher assigned yet to $displayClassName", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 3. Class Students Button (Clickable -> Shows only number of students, click opens class student list)
            ClassDetailActionButton(
                testTag = "btn_class_students",
                title = "Class Students",
                valueText = "$studentCount ${if (studentCount == 1) "Student" else "Students"}",
                icon = Icons.Outlined.Group,
                iconBgColor = Color(0xFFDCFCE7),
                iconTint = Color(0xFF16A34A),
                onClick = {
                    val targetName = currentClass?.className ?: className
                    val targetId = currentClass?.classId ?: classId
                    onNavigateToClassStudents(targetId, targetName)
                }
            )

            // 4. Class Test / Exam Button (Clickable -> Opens sheet with 2 buttons: Add New Test Marks & View Previous Test Marks)
            ClassDetailActionButton(
                testTag = "btn_class_test_exam",
                title = "Class Test / Exam",
                valueText = "View & Manage Tests",
                icon = Icons.Outlined.Assignment,
                iconBgColor = Color(0xFFFEF3C7),
                iconTint = Color(0xFFD97706),
                onClick = {
                    showTestExamOptionsSheet = true
                }
            )
        }
    }

    // Modal BottomSheet for Class Test / Exam options
    if (showTestExamOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTestExamOptionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Class Test / Exam • $displayClassName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select an option to manage examination records:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Button 1: Add New Test Marks
                Button(
                    onClick = {
                        showTestExamOptionsSheet = false
                        val targetName = currentClass?.className ?: className
                        val targetId = currentClass?.classId ?: classId
                        onNavigateToAddTestMarks(targetId, targetName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_add_new_test_marks"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Add New Test Marks",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Button 2: View Previous Test Marks
                OutlinedButton(
                    onClick = {
                        showTestExamOptionsSheet = false
                        val targetName = currentClass?.className ?: className
                        val targetId = currentClass?.classId ?: classId
                        onNavigateToClassTests(targetId, targetName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_view_previous_test_marks"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AssignmentTurnedIn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "View Previous Test Marks",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Full Educator Profile Dialog for Class Teacher
    selectedEducatorForProfile?.let { currentEdu ->
        EducatorFormDialog(
            initialEducator = currentEdu,
            readOnly = !isAdmin,
            onDismiss = { selectedEducatorForProfile = null },
            onSave = { name, email, photoUri, phone, subject ->
                if (isAdmin) {
                    val updated = currentEdu.copy(
                        name = name,
                        email = email,
                        photoUri = photoUri,
                        phone = phone,
                        subject = subject
                    )
                    EducatorManager.updateEducator(context, updated)
                    Toast.makeText(context, "Educator profile updated successfully", Toast.LENGTH_SHORT).show()
                }
                selectedEducatorForProfile = null
            },
            onDelete = if (isAdmin && !currentEdu.id.startsWith("temp_")) {
                { eduId ->
                    EducatorManager.deleteEducator(context, eduId)
                    selectedEducatorForProfile = null
                    Toast.makeText(context, "Educator deleted successfully", Toast.LENGTH_SHORT).show()
                }
            } else null
        )
    }

    // Full Screen Photo Zoom Dialog if needed
    educatorForFullScreenPhoto?.let { educator ->
        FullScreenPhotoDialog(
            name = educator.name,
            photoUri = educator.photoUri.ifBlank { null },
            fatherName = if (educator.subject.isNotBlank()) "Subject: ${educator.subject}" else null,
            className = "Class Teacher • $displayClassName",
            phoneNumber = educator.phone.ifBlank { null },
            onDismiss = { educatorForFullScreenPhoto = null }
        )
    }
}

@Composable
private fun ClassDetailActionButton(
    testTag: String,
    title: String,
    valueText: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBgColor,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = valueText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
