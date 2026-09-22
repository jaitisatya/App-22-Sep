package com.example.ui.components

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.ui.theme.*
import com.example.util.BatchConstants
import com.example.util.ImageUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormDialog(
    initialStudent: StudentEntity? = null,
    classes: List<ClassEntity>,
    isAdmin: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        fatherName: String,
        motherName: String,
        classId: String,
        phoneNumber: String,
        photoUri: String,
        dob: String,
        schoolClass: String,
        gender: String,
        aadharCardUri: String,
        birthCertificateUri: String,
        consentFormUri: String
    ) -> Unit,
    onDelete: ((StudentEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val isEditMode = initialStudent != null

    var name by remember { mutableStateOf(initialStudent?.studentName ?: "") }
    var gender by remember { mutableStateOf(if (initialStudent?.gender.equals("Female", ignoreCase = true)) "Female" else "Male") }
    var fatherName by remember { mutableStateOf(initialStudent?.fatherName ?: "") }
    var motherName by remember { mutableStateOf(initialStudent?.motherName ?: "") }
    var selectedClassId by remember {
        mutableStateOf(
            initialStudent?.classId ?: BatchConstants.getStandardClassId("J Prep")
        )
    }
    var schoolClass by remember { mutableStateOf(initialStudent?.schoolClass ?: "") }
    var dob by remember { mutableStateOf(initialStudent?.dob ?: "") }
    var phoneNumber by remember { mutableStateOf(initialStudent?.phoneNumber ?: "") }
    var photoUri by remember { mutableStateOf(initialStudent?.photoUri ?: "") }
    var aadharCardUri by remember { mutableStateOf(initialStudent?.aadharCardUri ?: "") }
    var birthCertificateUri by remember { mutableStateOf(initialStudent?.birthCertificateUri ?: "") }
    var consentFormUri by remember { mutableStateOf(initialStudent?.consentFormUri ?: "") }

    // Document Management state
    var selectedDocType by remember { mutableStateOf("Aadhar Card") }
    var docDropdownExpanded by remember { mutableStateOf(false) }
    var showDocOptionsDialog by remember { mutableStateOf(false) }
    var showFullScreenDoc by remember { mutableStateOf(false) }
    var activeTargetDocType by remember { mutableStateOf("Aadhar Card") }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var jaitiBatchDropdownExpanded by remember { mutableStateOf(false) }
    var schoolClassDropdownExpanded by remember { mutableStateOf(false) }
    var showPhotoPickerSheet by remember { mutableStateOf(false) }

    // Only standard J classes: J Prep, J1 to J12 (removes Class 1 to Class 5 and Pre-Primary)
    val allBatchOptions = BatchConstants.STANDARD_BATCH_OPTIONS

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = ImageUtils.saveUriToInternalStorage(context, it)
            photoUri = savedPath
        }
    }

    // Camera Capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val savedPath = ImageUtils.saveBitmapToInternalStorage(context, it)
            photoUri = savedPath
        }
    }

    // Document Gallery Picker
    val docGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = ImageUtils.saveUriToInternalStorage(context, it)
            when (activeTargetDocType) {
                "Aadhar Card" -> aadharCardUri = savedPath
                "Birth Certificate" -> birthCertificateUri = savedPath
                "Consent Form" -> consentFormUri = savedPath
            }
            Toast.makeText(context, "$activeTargetDocType uploaded successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // Document Camera Capture
    val docCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val savedPath = ImageUtils.saveBitmapToInternalStorage(context, it)
            when (activeTargetDocType) {
                "Aadhar Card" -> aadharCardUri = savedPath
                "Birth Certificate" -> birthCertificateUri = savedPath
                "Consent Form" -> consentFormUri = savedPath
            }
            Toast.makeText(context, "$activeTargetDocType captured successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // Contact Picker for Phone Number
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                val extractedNumber = extractPhoneNumberFromUri(context, contactUri)
                if (!extractedNumber.isNullOrBlank()) {
                    val cleaned = extractedNumber
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("(", "")
                        .replace(")", "")
                        .trim()
                    phoneNumber = cleaned
                    Toast.makeText(context, "Number selected: $cleaned", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No phone number found in this contact", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Date of Birth Date Picker
    val showDatePicker = {
        val calendar = Calendar.getInstance()
        if (dob.isNotBlank()) {
            try {
                val parts = dob.split("-", "/")
                if (parts.size == 3) {
                    if (parts[0].length == 4) { // YYYY-MM-DD
                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    } else { // DD-MM-YYYY
                        calendar.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                    }
                }
            } catch (_: Exception) {}
        } else {
            // Default ~8 years ago for school students
            calendar.add(Calendar.YEAR, -8)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                dob = "$year-$formattedMonth-$formattedDay"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Dialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = safeDismiss)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
                    .testTag("student_form_card"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEditMode) "Edit Student Details" else "Add New Student",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Jaiti Foundation Enrollment",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = safeDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable form fields
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Photo Picker
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                .clickable { showPhotoPickerSheet = true }
                                .testTag("student_photo_picker_box"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageUtils.getImageModel(photoUri),
                                    contentDescription = "Student Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Add Photo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Photo",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (photoUri.isNotBlank()) {
                            TextButton(
                                onClick = { photoUri = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Remove Photo", color = Color(0xFFDC2626), fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "Tap to upload or take a photo (Optional)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 1. Student Name (Mandatory)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = {
                                Row {
                                    Text("Student Name ")
                                    Text("*", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                }
                            },
                            placeholder = { Text("e.g. Ajay Kumar") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            isError = name.isBlank() && isEditMode,
                            supportingText = {
                                if (name.isBlank()) {
                                    Text("Required field", color = Color(0xFFDC2626), fontSize = 11.sp)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_student_name")
                        )

                        // ROW 1: DOB (Left) + GENDER (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date of Birth (DOB) Field with Date Picker
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { dob = it },
                                readOnly = true,
                                label = { Text("DOB") },
                                placeholder = { Text("YYYY-MM-DD") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "DOB Calendar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker() }) {
                                        Icon(Icons.Default.EditCalendar, contentDescription = "Select Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showDatePicker() }
                                    .testTag("input_student_dob")
                            )

                            // Gender Selector (Male / Female)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Gender *",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val isMale = gender.equals("Male", ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isMale) Color(0xFF1E88E5).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (isMale) 1.5.dp else 1.dp,
                                            color = if (isMale) Color(0xFF1E88E5) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { gender = "Male" }
                                            .testTag("gender_male_option")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Male",
                                                fontWeight = if (isMale) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isMale) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    val isFemale = gender.equals("Female", ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isFemale) Color(0xFFE91E63).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (isFemale) 1.5.dp else 1.dp,
                                            color = if (isFemale) Color(0xFFE91E63) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { gender = "Female" }
                                            .testTag("gender_female_option")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Female",
                                                fontWeight = if (isFemale) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isFemale) Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ROW 2: J CLASS (Left) + SCHOOL CLASS (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Jaiti Batch Dropdown
                            ExposedDropdownMenuBox(
                                expanded = jaitiBatchDropdownExpanded,
                                onExpandedChange = { jaitiBatchDropdownExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentClassName = classes.find { it.classId == selectedClassId }?.className
                                    ?: BatchConstants.formatBatchDisplayName(selectedClassId).ifBlank { "Select" }

                                OutlinedTextField(
                                    value = currentClassName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("J Class *") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Class, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jaitiBatchDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("dropdown_jaiti_batch")
                                )
                                ExposedDropdownMenu(
                                    expanded = jaitiBatchDropdownExpanded,
                                    onDismissRequest = { jaitiBatchDropdownExpanded = false }
                                ) {
                                    allBatchOptions.forEach { batchOption ->
                                        val bClassId = classes.find { it.className.equals(batchOption, true) }?.classId
                                            ?: BatchConstants.getStandardClassId(batchOption)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = batchOption,
                                                    fontWeight = if (selectedClassId == bClassId) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedClassId == bClassId) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                )
                                            },
                                            onClick = {
                                                selectedClassId = bClassId
                                                jaitiBatchDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Class in School Dropdown
                            ExposedDropdownMenuBox(
                                expanded = schoolClassDropdownExpanded,
                                onExpandedChange = { schoolClassDropdownExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = if (schoolClass.isNotBlank()) schoolClass else "None",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("School Class") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.School, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolClassDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("dropdown_school_class")
                                )
                                ExposedDropdownMenu(
                                    expanded = schoolClassDropdownExpanded,
                                    onDismissRequest = { schoolClassDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

                        // ROW 3: FATHER'S NAME (Left) + MOTHER'S NAME (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = fatherName,
                                onValueChange = { fatherName = it },
                                label = { Text("Father's Name") },
                                placeholder = { Text("e.g. Raju") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_student_father")
                            )

                            OutlinedTextField(
                                value = motherName,
                                onValueChange = { motherName = it },
                                label = { Text("Mother's Name") },
                                placeholder = { Text("e.g. Sita") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Outlined.FamilyRestroom, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_student_mother")
                            )
                        }

                        // 7. Phone Number with Contact Picker
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number (Optional)") },
                            placeholder = { Text("e.g. 9876543210") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = {
                                Icon(Icons.Outlined.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
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
                                    modifier = Modifier.testTag("pick_contact_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContactPhone,
                                        contentDescription = "Pick Phone Number from Contacts",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_student_phone")
                        )

                        // 8. STUDENT DOCUMENTS (Admin Only) - Dropdown for Aadhar Card, Birth Certificate, Consent Form
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.FolderShared,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Student Documents",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isAdmin) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = if (isAdmin) "Admin Access" else "View Only",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdmin) MaterialTheme.colorScheme.primary else Color(0xFFB45309),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val activeDocUri = when (selectedDocType) {
                                    "Aadhar Card" -> aadharCardUri
                                    "Birth Certificate" -> birthCertificateUri
                                    "Consent Form" -> consentFormUri
                                    else -> ""
                                }
                                val hasDoc = activeDocUri.isNotBlank()

                                // Dropdown Button to select Document Name
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { docDropdownExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
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

                                        // Action buttons (Admin only can upload, edit/change or remove)
                                        if (isAdmin) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (hasDoc) {
                                                    IconButton(
                                                        onClick = {
                                                            activeTargetDocType = selectedDocType
                                                            showFullScreenDoc = true
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("dialog_view_doc_btn")
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
                                                        modifier = Modifier.size(36.dp).testTag("dialog_edit_doc_btn")
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
                                                        modifier = Modifier.size(36.dp).testTag("dialog_remove_doc_btn")
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
                                                        modifier = Modifier.height(34.dp).testTag("dialog_upload_doc_btn")
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

                        // Safe padding at the bottom of form content so bottom fields scroll comfortably
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditMode && onDelete != null) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                                    .testTag("delete_student_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Student",
                                    tint = Color(0xFFDC2626)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = safeDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onSave(
                                        name.trim(),
                                        fatherName.trim(),
                                        motherName.trim(),
                                        selectedClassId.trim(),
                                        phoneNumber.trim(),
                                        photoUri.trim(),
                                        dob.trim(),
                                        schoolClass.trim(),
                                        gender.trim(),
                                        aadharCardUri.trim(),
                                        birthCertificateUri.trim(),
                                        consentFormUri.trim()
                                    )
                                }
                            },
                            enabled = name.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f).height(46.dp).testTag("save_student_btn")
                        ) {
                            Text(
                                text = if (isEditMode) "Save Changes" else "Save Student",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Photo Source Picker Dialog (Camera / Gallery)
    if (showPhotoPickerSheet) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerSheet = false },
            title = { Text("Choose Student Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = {
                            showPhotoPickerSheet = false
                            cameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Take Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Use device camera", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showPhotoPickerSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Choose from Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Select existing photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoPickerSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && initialStudent != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Student?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${initialStudent.studentName}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        safeDismiss()
                        onDelete(initialStudent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Document Source Selection Dialog (Camera or Gallery)
    if (showDocOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showDocOptionsDialog = false },
            title = {
                Text(
                    text = "Upload $activeTargetDocType",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            showDocOptionsDialog = false
                            docCameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Take Photo with Camera", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Capture document clearly", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            showDocOptionsDialog = false
                            docGalleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Choose from Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Select photo or PDF screenshot", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Full Screen Document Viewer
    if (showFullScreenDoc) {
        val targetUri = when (activeTargetDocType) {
            "Aadhar Card" -> aadharCardUri
            "Birth Certificate" -> birthCertificateUri
            "Consent Form" -> consentFormUri
            else -> ""
        }
        FullScreenDocumentDialog(
            docTitle = activeTargetDocType,
            studentName = if (name.isNotBlank()) name else "Student",
            docUri = targetUri,
            isAdmin = isAdmin,
            onChangeClick = if (isAdmin) {
                {
                    showFullScreenDoc = false
                    showDocOptionsDialog = true
                }
            } else null,
            onDismiss = { showFullScreenDoc = false }
        )
    }
}

/**
 * Extracts phone number string from contact content URI
 */
private fun extractPhoneNumberFromUri(context: Context, contactUri: Uri): String? {
    var phoneNumber: String? = null
    val cursor = context.contentResolver.query(
        contactUri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null
    )
    cursor?.use {
        if (it.moveToFirst()) {
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (phoneIdx != -1) {
                phoneNumber = it.getString(phoneIdx)
            }
        }
    }
    return phoneNumber
}
