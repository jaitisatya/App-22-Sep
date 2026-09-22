package com.example.ui.screens

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.model.AttendanceStatus
import com.example.ui.components.FullScreenDocumentDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.util.BatchConstants
import com.example.util.DateUtils
import com.example.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentInfoScreen(
    student: StudentEntity,
    classes: List<ClassEntity>,
    attendanceRecords: List<AttendanceRecordEntity> = emptyList(),
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onSave: (updatedStudent: StudentEntity) -> Unit,
    onDelete: ((student: StudentEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Handle physical device back button
    BackHandler(onBack = onBack)

    var studentName by remember(student) { mutableStateOf(student.studentName) }
    var gender by remember(student) { mutableStateOf(if (student.gender.equals("Female", ignoreCase = true)) "Female" else "Male") }
    var fatherName by remember(student) { mutableStateOf(student.fatherName) }
    var motherName by remember(student) { mutableStateOf(student.motherName) }
    var selectedClassId by remember(student) { mutableStateOf(student.classId) }
    var phoneNumber by remember(student) { mutableStateOf(student.phoneNumber) }
    var areaName by remember(student) { mutableStateOf(student.areaName) }
    var photoUri by remember(student) { mutableStateOf(student.photoUri) }
    var dob by remember(student) { mutableStateOf(student.dob) }
    var notes by remember(student) { mutableStateOf(student.notes) }
    var schoolClass by remember(student) { mutableStateOf(student.schoolClass) }
    var aadharCardUri by remember(student) { mutableStateOf(student.aadharCardUri) }
    var birthCertificateUri by remember(student) { mutableStateOf(student.birthCertificateUri) }
    var consentFormUri by remember(student) { mutableStateOf(student.consentFormUri) }

    // Document Management state
    var selectedDocType by remember { mutableStateOf("Aadhar Card") }
    var docDropdownExpanded by remember { mutableStateOf(false) }
    var showDocOptionsDialog by remember { mutableStateOf(false) }
    var showFullScreenDoc by remember { mutableStateOf(false) }
    var activeTargetDocType by remember { mutableStateOf("Aadhar Card") }

    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showFullScreenPhoto by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var schoolClassDropdownExpanded by remember { mutableStateOf(false) }

    // Idea 2: Monthly Attendance Calendar State
    val initialCalendar = Calendar.getInstance(Locale.US)
    var calendarYear by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR)) }
    var calendarMonth by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) } // 0-indexed
    var selectedCalendarDateIso by remember { mutableStateOf(DateUtils.getTodayIso()) }

    val monthDays = remember(calendarYear, calendarMonth) {
        DateUtils.getMonthCalendarDays(calendarYear, calendarMonth)
    }

    val recordsByDate = remember(attendanceRecords) {
        attendanceRecords.associateBy { it.date }
    }

    val monthTitle = remember(calendarYear, calendarMonth) {
        val cal = Calendar.getInstance(Locale.US)
        cal.set(Calendar.YEAR, calendarYear)
        cal.set(Calendar.MONTH, calendarMonth)
        SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    val monthStats = remember(monthDays, recordsByDate) {
        var presentCount = 0
        var absentCount = 0
        monthDays.filter { it.dayNumber > 0 }.forEach { day ->
            val rec = recordsByDate[day.isoDate]
            if (rec != null) {
                if (rec.status == AttendanceStatus.PRESENT) presentCount++
                else if (rec.status == AttendanceStatus.ABSENT) absentCount++
            }
        }
        val total = presentCount + absentCount
        val rate = if (total > 0) ((presentCount * 100) / total) else 0
        Triple(presentCount, absentCount, rate)
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ImageUtils.saveUriToInternalStorage(context, uri)
            if (savedPath.isNotBlank()) {
                photoUri = savedPath
                Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedPath = ImageUtils.saveBitmapToInternalStorage(context, bitmap)
            if (savedPath != null) {
                photoUri = savedPath
                Toast.makeText(context, "Photo captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Document Gallery Picker
    val docGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ImageUtils.saveUriToInternalStorage(context, uri)
            if (savedPath.isNotBlank()) {
                when (activeTargetDocType) {
                    "Aadhar Card" -> aadharCardUri = savedPath
                    "Birth Certificate" -> birthCertificateUri = savedPath
                    "Consent Form" -> consentFormUri = savedPath
                }
                Toast.makeText(context, "$activeTargetDocType uploaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Document Camera Launcher
    val docCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedPath = ImageUtils.saveBitmapToInternalStorage(context, bitmap)
            if (savedPath != null) {
                when (activeTargetDocType) {
                    "Aadhar Card" -> aadharCardUri = savedPath
                    "Birth Certificate" -> birthCertificateUri = savedPath
                    "Consent Form" -> consentFormUri = savedPath
                }
                Toast.makeText(context, "$activeTargetDocType captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Contact Picker for Phone Number
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                val extractedNumber = extractPhoneFromContact(context, contactUri)
                if (!extractedNumber.isNullOrBlank()) {
                    val cleaned = extractedNumber
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("(", "")
                        .replace(")", "")
                        .trim()
                    phoneNumber = cleaned
                    Toast.makeText(context, "Contact selected: $cleaned", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No phone number found in contact", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Date Picker Dialog for DOB
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val monthNames = arrayOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                dob = "$dayOfMonth ${monthNames.getOrElse(month) { "Jan" }} $year"
            },
            calendar.get(Calendar.YEAR) - 8,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val selectedClassName = remember(classes, selectedClassId) {
        classes.firstOrNull { it.classId == selectedClassId }?.className ?: "Select Class / Batch"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Student Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("student_info_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (onDelete != null && isAdmin) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("student_info_top_delete_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete Student",
                                tint = Color.White
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (studentName.isBlank()) {
                                Toast.makeText(context, "Student Name cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val updated = student.copy(
                                studentName = studentName.trim(),
                                gender = gender,
                                fatherName = fatherName.trim(),
                                motherName = motherName.trim(),
                                classId = selectedClassId.trim(),
                                phoneNumber = phoneNumber.trim(),
                                photoUri = photoUri.trim(),
                                dob = dob.trim(),
                                notes = notes.trim(),
                                schoolClass = schoolClass.trim(),
                                areaName = areaName.trim()
                            )
                            onSave(updated)
                            Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("student_info_save_btn")
                    ) {
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
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
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ==========================================
            // 1. TOP PROFILE HEADER CARD (Photo + Name + JID)
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Squircle Photo Avatar with Camera badge
                    Box(
                        modifier = Modifier
                            .size(width = 82.dp, height = 90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable {
                                if (photoUri.isNotBlank()) {
                                    showFullScreenPhoto = true
                                } else {
                                    showPhotoOptionsDialog = true
                                }
                            }
                            .testTag("student_info_photo_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri.isNotBlank()) {
                            AsyncImage(
                                model = ImageUtils.getImageModel(photoUri),
                                contentDescription = "Photo of $studentName",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showPhotoOptionsDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddAPhoto,
                                    contentDescription = "Add Photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Add Photo",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name + JID + Active Status
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "STUDENT FULL NAME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.3.sp
                        )

                        // Student Name Input
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            BasicTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        if (studentName.isEmpty()) {
                                            Text(
                                                "Enter Student Name",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 15.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_info_name_input")
                            )
                        }

                        // JID Badge & Phone Quick Dial Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "ID: ${student.studentId}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            if (phoneNumber.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.12f),
                                    border = BorderStroke(0.7.dp, Color(0xFF16A34A).copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        try {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                                            context.startActivity(dialIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot dial: $phoneNumber", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Phone,
                                            contentDescription = "Call",
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Call Parent",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. CARD: PERSONAL & ACADEMIC (2 Details per line)
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ACADEMIC & IDENTITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.4.sp
                    )

                    // ----------------------------------------------------
                    // LINE 1: DOB (Left) + GENDER (Right)
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left: Date of Birth
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Date of Birth (DOB)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clickable { datePickerDialog.show() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dob.ifBlank { "Select DOB" },
                                        fontSize = 13.5.sp,
                                        fontWeight = if (dob.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                                        color = if (dob.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "Pick Date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Right: Gender (Male / Female Segmented Pill)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gender (लिंग)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isMale = gender.equals("Male", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isMale) Color(0xFF1E88E5).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(
                                        width = if (isMale) 1.5.dp else 0.8.dp,
                                        color = if (isMale) Color(0xFF1E88E5) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { gender = "Male" }
                                        .testTag("student_info_gender_male")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Male",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isMale) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isMale) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val isFemale = gender.equals("Female", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFemale) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(
                                        width = if (isFemale) 1.5.dp else 0.8.dp,
                                        color = if (isFemale) Color(0xFFE91E63) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { gender = "Female" }
                                        .testTag("student_info_gender_female")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Female",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isFemale) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isFemale) Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // LINE 2: J CLASS (Left) + SCHOOL CLASS (Right)
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left: J Class
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "J Class (बैच)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val batchDisplayName = classes.find { it.classId == selectedClassId }?.className
                                    ?: BatchConstants.formatBatchDisplayName(selectedClassId).ifBlank { "Select J Class" }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clickable { classDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = batchDisplayName,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selectedClassId.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select J Class",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = classDropdownExpanded,
                                    onDismissRequest = { classDropdownExpanded = false }
                                ) {
                                    val batchOptions = BatchConstants.STANDARD_BATCH_OPTIONS
                                    batchOptions.forEach { opt ->
                                        val bId = classes.find { it.className.equals(opt, true) }?.classId
                                            ?: BatchConstants.getStandardClassId(opt)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = opt,
                                                    fontWeight = if (selectedClassId == bId) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedClassId == bId) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                )
                                            },
                                            onClick = {
                                                selectedClassId = bId
                                                classDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Right: School Class
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Class in School",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clickable { schoolClassDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (schoolClass.isNotBlank()) schoolClass else "Select Class",
                                            fontSize = 13.5.sp,
                                            fontWeight = if (schoolClass.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                                            color = if (schoolClass.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select School Class",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = schoolClassDropdownExpanded,
                                    onDismissRequest = { schoolClassDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None / Not Enrolled", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = {
                                            schoolClass = ""
                                            schoolClassDropdownExpanded = false
                                        }
                                    )
                                    BatchConstants.STANDARD_SCHOOL_CLASS_OPTIONS.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = opt,
                                                    fontWeight = if (schoolClass == opt) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (schoolClass == opt) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                )
                                            },
                                            onClick = {
                                                schoolClass = opt
                                                schoolClassDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. CARD: PARENTS & CONTACT (2 Details per line)
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PARENTS & CONTACT INFORMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.4.sp
                    )

                    // ----------------------------------------------------
                    // LINE 3: FATHER'S NAME (Left) + MOTHER'S NAME (Right)
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left: Father's Name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Father's Name",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                BasicTextField(
                                    value = fatherName,
                                    onValueChange = { fatherName = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (fatherName.isEmpty()) {
                                                Text(
                                                    "e.g. Kakku",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_info_father_input")
                                )
                            }
                        }

                        // Right: Mother's Name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mother's Name",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                BasicTextField(
                                    value = motherName,
                                    onValueChange = { motherName = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (motherName.isEmpty()) {
                                                Text(
                                                    "e.g. Aasha",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_info_mother_input")
                                )
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // LINE 4: PHONE (Left) + AREA / SLUM (Right)
                    // ----------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left: Phone Number with contact picker button
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Number",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 10.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = phoneNumber,
                                        onValueChange = { phoneNumber = it },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (phoneNumber.isEmpty()) {
                                                    Text(
                                                        "9793408263",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("student_info_phone_input")
                                    )

                                    // Pick from Contacts Button
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Intent.ACTION_PICK,
                                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                                )
                                                contactPickerLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val intent = Intent(
                                                        Intent.ACTION_PICK,
                                                        ContactsContract.Contacts.CONTENT_URI
                                                    )
                                                    contactPickerLauncher.launch(intent)
                                                } catch (ex: Exception) {
                                                    Toast.makeText(context, "Cannot open contacts", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("student_info_pick_contact_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContactPhone,
                                            contentDescription = "Pick Contact",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right: Area / Slum Name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Slum / Area Name",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                BasicTextField(
                                    value = areaName,
                                    onValueChange = { areaName = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (areaName.isEmpty()) {
                                                Text(
                                                    "e.g. Ambedkar Nagar",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // LINE 5: STUDENT DOCUMENTS (Aadhar Card, Birth Certificate, Consent Form)
                    // (Admin upload, edit, change, remove, and view)
                    // ----------------------------------------------------
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Student Documents (दस्तावेज)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (!isAdmin) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "Admin Only",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val activeDocUri = when (selectedDocType) {
                            "Aadhar Card" -> aadharCardUri
                            "Birth Certificate" -> birthCertificateUri
                            "Consent Form" -> consentFormUri
                            else -> ""
                        }
                        val hasDoc = activeDocUri.isNotBlank()

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                // Dropdown Selector for Document Type
                                Text(
                                    text = "Select Document Name",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clickable { docDropdownExpanded = true }
                                            .testTag("student_doc_type_dropdown")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when (selectedDocType) {
                                                        "Aadhar Card" -> Icons.Outlined.Badge
                                                        "Birth Certificate" -> Icons.Outlined.Cake
                                                        else -> Icons.Outlined.Assignment
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = selectedDocType,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Expand",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = docDropdownExpanded,
                                        onDismissRequest = { docDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        val docOptions = listOf("Aadhar Card", "Birth Certificate", "Consent Form")
                                        docOptions.forEach { option ->
                                            val isUploaded = when (option) {
                                                "Aadhar Card" -> aadharCardUri.isNotBlank()
                                                "Birth Certificate" -> birthCertificateUri.isNotBlank()
                                                "Consent Form" -> consentFormUri.isNotBlank()
                                                else -> false
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = option,
                                                            fontWeight = if (selectedDocType == option) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (isUploaded) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFFDCFCE7)
                                                            ) {
                                                                Text(
                                                                    text = "Uploaded",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF16A34A),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedDocType = option
                                                    docDropdownExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = when (option) {
                                                            "Aadhar Card" -> Icons.Outlined.Badge
                                                            "Birth Certificate" -> Icons.Outlined.Cake
                                                            else -> Icons.Outlined.Assignment
                                                        },
                                                        contentDescription = null,
                                                        tint = if (selectedDocType == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Document Card for the chosen type
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (hasDoc) Color(0xFF86EFAC) else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (hasDoc) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable(enabled = hasDoc) {
                                                        activeTargetDocType = selectedDocType
                                                        showFullScreenDoc = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (hasDoc) {
                                                    AsyncImage(
                                                        model = activeDocUri,
                                                        contentDescription = selectedDocType,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Outlined.UploadFile,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = selectedDocType,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (hasDoc) "Document uploaded • Tap to view" else "Not uploaded yet",
                                                    fontSize = 11.sp,
                                                    color = if (hasDoc) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Action buttons (Only Admin can upload, change, edit or remove)
                                        if (isAdmin) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (hasDoc) {
                                                    IconButton(
                                                        onClick = {
                                                            activeTargetDocType = selectedDocType
                                                            showFullScreenDoc = true
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("view_doc_btn")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Fullscreen,
                                                            contentDescription = "View",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            activeTargetDocType = selectedDocType
                                                            showDocOptionsDialog = true
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("edit_doc_btn")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Edit,
                                                            contentDescription = "Change",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            when (selectedDocType) {
                                                                "Aadhar Card" -> aadharCardUri = ""
                                                                "Birth Certificate" -> birthCertificateUri = ""
                                                                "Consent Form" -> consentFormUri = ""
                                                            }
                                                            Toast.makeText(context, "$selectedDocType removed", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("remove_doc_btn")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Remove",
                                                            tint = Color(0xFFDC2626),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            activeTargetDocType = selectedDocType
                                                            showDocOptionsDialog = true
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.height(34.dp).testTag("upload_doc_btn")
                                                    ) {
                                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Upload", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (hasDoc) {
                                                OutlinedButton(
                                                    onClick = {
                                                        activeTargetDocType = selectedDocType
                                                        showFullScreenDoc = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("View", fontSize = 11.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // LINE 6: NOTES / REMARKS (Full width)
                    // ----------------------------------------------------
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Notes / Remarks",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                minLines = 2,
                                maxLines = 4,
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        if (notes.isEmpty()) {
                                            Text(
                                                "Add any special notes, behavior or remarks...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_info_notes_input")
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. IDEA 2: MONTHLY ATTENDANCE CALENDAR & LOG
            // ==========================================
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Month Navigation (< Month Year >)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Attendance Calendar",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (calendarMonth == 0) {
                                        calendarMonth = 11
                                        calendarYear -= 1
                                    } else {
                                        calendarMonth -= 1
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = monthTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (calendarMonth == 11) {
                                        calendarMonth = 0
                                        calendarYear += 1
                                    } else {
                                        calendarMonth += 1
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Month Stats Summary Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Present
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(0.7.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Present: ${monthStats.first}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        // Absent
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(0.7.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDC2626))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Absent: ${monthStats.second}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }

                        // Rate %
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${monthStats.third}% Rate",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Calendar Day-of-Week Header
                    val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        daysOfWeek.forEach { dayName ->
                            val isSun = dayName == "Sun"
                            Text(
                                text = dayName,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSun) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.8.dp)

                    // Calendar Days Grid (7 Columns)
                    val chunkedWeeks = monthDays.chunked(7)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        chunkedWeeks.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (i in 0 until 7) {
                                    val day = week.getOrNull(i)
                                    if (day == null || day.dayNumber == 0) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        val record = recordsByDate[day.isoDate]
                                        val isSelected = day.isoDate == selectedCalendarDateIso
                                        val isToday = day.isToday
                                        val isSunday = day.isSunday

                                        val (statusColor, statusBg, statusBorder) = when {
                                            record != null && record.status == AttendanceStatus.PRESENT ->
                                                Triple(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF86EFAC))
                                            record != null && record.status == AttendanceStatus.ABSENT ->
                                                Triple(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFFFCA5A5))
                                            isSunday ->
                                                Triple(Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFFFDE68A))
                                            else ->
                                                Triple(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), Color.Transparent)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else statusBg,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.5.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primary else statusBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(1.5.dp)
                                                .clickable { selectedCalendarDateIso = day.isoDate }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "${day.dayNumber}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (isSunday) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                                                    style = TextStyle(
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                        textAlign = TextAlign.Center
                                                    )
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                // Dot or letter indicator
                                                if (record != null) {
                                                    val letter = if (record.status == AttendanceStatus.PRESENT) "P" else "A"
                                                    Box(
                                                        modifier = Modifier
                                                            .size(11.dp)
                                                            .clip(CircleShape)
                                                            .background(statusColor),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = letter,
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        )
                                                    }
                                                } else if (isSunday) {
                                                    Text(
                                                        text = "Sun",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309),
                                                        style = TextStyle(
                                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // Selected Date Detail Card (Instant tap feedback)
                    // ----------------------------------------------------
                    val selectedRecord = recordsByDate[selectedCalendarDateIso]
                    val isSelectedSunday = DateUtils.isSunday(selectedCalendarDateIso)
                    val formattedSelectedDate = DateUtils.getDateMonthYearDisplay(selectedCalendarDateIso)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formattedSelectedDate,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Status Badge
                                when {
                                    selectedRecord != null && selectedRecord.status == AttendanceStatus.PRESENT -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = BorderStroke(0.8.dp, Color(0xFF86EFAC))
                                        ) {
                                            Text(
                                                text = "✓ PRESENT",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    selectedRecord != null && selectedRecord.status == AttendanceStatus.ABSENT -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEE2E2),
                                            border = BorderStroke(0.8.dp, Color(0xFFFCA5A5))
                                        ) {
                                            Text(
                                                text = "✗ ABSENT",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    isSelectedSunday -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(0.8.dp, Color(0xFFFDE68A))
                                        ) {
                                            Text(
                                                text = "SUNDAY (OFF)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "NOT RECORDED",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Record details (Time, Teacher, Remark)
                            if (selectedRecord != null) {
                                val timeStr = try {
                                    val ts = if (selectedRecord.createdTimestamp > 0) selectedRecord.createdTimestamp else selectedRecord.lastModifiedTimestamp
                                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
                                } catch (e: Exception) {
                                    ""
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (timeStr.isNotBlank()) {
                                        Text(
                                            text = "Marked Time: $timeStr",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selectedRecord.teacherName.isNotBlank()) {
                                        Text(
                                            text = "By: ${selectedRecord.teacherName}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (selectedRecord.remark.isNotBlank()) {
                                    Text(
                                        text = "Remark: ${selectedRecord.remark}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. ACTION BUTTONS (Save Changes & Delete)
            // ==========================================
            Button(
                onClick = {
                    if (studentName.isBlank()) {
                        Toast.makeText(context, "Student Name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val updated = student.copy(
                        studentName = studentName.trim(),
                        gender = gender,
                        fatherName = fatherName.trim(),
                        motherName = motherName.trim(),
                        classId = selectedClassId.trim(),
                        phoneNumber = phoneNumber.trim(),
                        photoUri = photoUri.trim(),
                        dob = dob.trim(),
                        notes = notes.trim(),
                        schoolClass = schoolClass.trim(),
                        areaName = areaName.trim(),
                        aadharCardUri = aadharCardUri.trim(),
                        birthCertificateUri = birthCertificateUri.trim(),
                        consentFormUri = consentFormUri.trim()
                    )
                    onSave(updated)
                    Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("student_info_bottom_save_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }

            if (onDelete != null && isAdmin) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("student_info_delete_btn")
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Student", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Full Screen Photo View Dialog
    if (showFullScreenPhoto) {
        FullScreenPhotoDialog(
            name = studentName,
            photoUri = photoUri,
            fatherName = fatherName,
            motherName = motherName,
            className = selectedClassName,
            phoneNumber = phoneNumber,
            gender = student.gender,
            onEditInfoClick = {
                showFullScreenPhoto = false
                showPhotoOptionsDialog = true
            },
            onPhotoChanged = { newPhotoUri ->
                photoUri = newPhotoUri
            },
            onDismiss = { showFullScreenPhoto = false }
        )
    }

    // Photo Options Dialog
    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = { Text("Student Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (photoUri.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                showFullScreenPhoto = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("View Full Screen", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take Photo with Camera", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        }
                    }

                    if (photoUri.isNotBlank()) {
                        TextButton(
                            onClick = {
                                photoUri = ""
                                showPhotoOptionsDialog = false
                                Toast.makeText(context, "Photo removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Remove Photo", color = Color(0xFFDC2626), fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Student?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"$studentName\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(student)
                        Toast.makeText(context, "Student deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Screen Document View Dialog
    if (showFullScreenDoc) {
        val currentDocUri = when (activeTargetDocType) {
            "Aadhar Card" -> aadharCardUri
            "Birth Certificate" -> birthCertificateUri
            "Consent Form" -> consentFormUri
            else -> ""
        }
        FullScreenDocumentDialog(
            docTitle = activeTargetDocType,
            studentName = studentName,
            docUri = currentDocUri,
            isAdmin = isAdmin,
            onChangeClick = {
                showDocOptionsDialog = true
            },
            onDismiss = { showFullScreenDoc = false }
        )
    }

    // Document Options Dialog (Camera vs Gallery)
    if (showDocOptionsDialog && isAdmin) {
        AlertDialog(
            onDismissRequest = { showDocOptionsDialog = false },
            title = {
                Text(
                    text = "Upload $activeTargetDocType",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentDocUri = when (activeTargetDocType) {
                        "Aadhar Card" -> aadharCardUri
                        "Birth Certificate" -> birthCertificateUri
                        "Consent Form" -> consentFormUri
                        else -> ""
                    }

                    if (currentDocUri.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showDocOptionsDialog = false
                                showFullScreenDoc = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("View Fullscreen", fontSize = 14.sp)
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            showDocOptionsDialog = false
                            docCameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take Photo with Camera", fontSize = 14.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            showDocOptionsDialog = false
                            docGalleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", fontSize = 14.sp)
                        }
                    }

                    if (currentDocUri.isNotBlank()) {
                        TextButton(
                            onClick = {
                                when (activeTargetDocType) {
                                    "Aadhar Card" -> aadharCardUri = ""
                                    "Birth Certificate" -> birthCertificateUri = ""
                                    "Consent Form" -> consentFormUri = ""
                                }
                                showDocOptionsDialog = false
                                Toast.makeText(context, "$activeTargetDocType removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Remove Document", color = Color(0xFFDC2626), fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDocOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Helper to extract phone number from contact picker Uri
 */
private fun extractPhoneFromContact(context: Context, uri: Uri): String? {
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIdx != -1) {
                    val num = c.getString(numberIdx)
                    if (!num.isNullOrBlank()) return num
                }

                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                if (idIdx != -1) {
                    val contactId = c.getString(idIdx)
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )
                    phoneCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (pIdx != -1) {
                                return pc.getString(pIdx)
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
