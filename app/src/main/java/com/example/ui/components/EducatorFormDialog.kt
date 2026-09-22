package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.EducatorProfile
import com.example.ui.theme.*
import com.example.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducatorFormDialog(
    initialEducator: EducatorProfile? = null,
    readOnly: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, photoUri: String, phone: String, subject: String) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val isEditMode = initialEducator != null

    var name by remember { mutableStateOf(initialEducator?.name ?: "") }
    var email by remember { mutableStateOf(initialEducator?.email ?: "") }
    var photoUri by remember { mutableStateOf(initialEducator?.photoUri ?: "") }
    var phone by remember { mutableStateOf(initialEducator?.phone ?: "") }
    var subject by remember { mutableStateOf(initialEducator?.subject ?: "") }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoPickerSheet by remember { mutableStateOf(false) }

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
                    phone = cleaned
                    Toast.makeText(context, "Contact selected: $cleaned", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No phone number found in this contact", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val suggestedSubjects = listOf("Mathematics", "Science", "English", "Hindi", "Social Studies", "Arts & Activity", "Computer / Tech")

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
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("educator_form_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (readOnly) "Educator Details" else if (isEditMode) "Edit Educator" else "Add New Educator",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (readOnly) "View educator profile & contact information" else "Enter educator profile details",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = safeDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Form Fields
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 2. Photo Section (Avatar / Camera / Gallery)
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable(enabled = !readOnly) { showPhotoPickerSheet = true }
                                .testTag("educator_photo_picker_box"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageUtils.getImageModel(photoUri),
                                    contentDescription = "Educator Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = "Add Photo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Text(
                                        text = if (readOnly) "No Photo" else "Add Photo",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Edit badge (only when editable)
                            if (!readOnly) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (photoUri.isNotBlank()) Icons.Default.Edit else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!readOnly) {
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
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 1. Name Field (Mandatory)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (!readOnly) name = it },
                            readOnly = readOnly,
                            label = {
                                Row {
                                    Text("Educator Name ")
                                    if (!readOnly) {
                                        Text("*", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            placeholder = { Text("e.g. Rahul Verma / Sunita Sharma") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            singleLine = true,
                            isError = !readOnly && name.isBlank(),
                            supportingText = {
                                if (!readOnly && name.isBlank()) {
                                    Text("Required field", color = Color(0xFFDC2626), fontSize = 11.sp)
                                }
                            },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("educator_name_input")
                        )

                        // 2. Email Field (For Whitelisting / Login Access)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { if (!readOnly) email = it },
                            readOnly = readOnly,
                            label = { Text("Educator Email (For App Sign-In)") },
                            placeholder = { Text("e.g. educator@gmail.com") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("educator_email_input")
                        )

                        // 3. Phone Number Field
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { if (!readOnly) phone = it },
                            readOnly = readOnly,
                            label = { Text("Phone Number") },
                            placeholder = { Text("e.g. 9876543210") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = if (!readOnly) {
                                {
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
                                        modifier = Modifier.testTag("pick_educator_contact_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContactPhone,
                                            contentDescription = "Pick Phone Number from Contacts",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("educator_phone_input")
                        )

                        // 4. Subject Field
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = subject,
                                onValueChange = { if (!readOnly) subject = it },
                                readOnly = readOnly,
                                label = { Text("Subject / Specialization") },
                                placeholder = { Text("e.g. Mathematics, Science, English") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                .fillMaxWidth()
                                .testTag("educator_subject_input")
                            )

                            if (!readOnly) {
                                // Quick subject suggestion chips
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Quick Suggestions:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Math", "Science", "English", "Arts").forEach { s ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (subject.equals(s, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable { subject = s }
                                        ) {
                                            Text(
                                                text = s,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (subject.equals(s, ignoreCase = true)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (readOnly) {
                        // Contact & Close actions for non-admins
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (phone.isNotBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot initiate call", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Call Educator")
                                }
                            }

                            OutlinedButton(
                                onClick = safeDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close")
                            }
                        }
                    } else {
                        // Action Buttons (Save / Cancel / Delete)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditMode && onDelete != null) {
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier
                                        .background(Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                                        .size(44.dp)
                                        .testTag("delete_educator_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Educator",
                                        tint = Color(0xFFDC2626)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = safeDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onSave(name.trim(), email.trim(), photoUri.trim(), phone.trim(), subject.trim())
                                    }
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_educator_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isEditMode) "Save Changes" else "Save Educator",
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
            title = {
                Text(
                    text = "Choose Photo Source",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Option 1: Camera
                    Surface(
                        onClick = {
                            showPhotoPickerSheet = false
                            cameraLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take Photo with Camera", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Option 2: Gallery
                    Surface(
                        onClick = {
                            showPhotoPickerSheet = false
                            galleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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

    // Delete Confirmation Dialog (Safe inside Edit mode)
    if (showDeleteConfirm && initialEducator != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Educator?", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
            },
            text = {
                Text("Are you sure you want to remove ${initialEducator.name} from the educators directory?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(initialEducator.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
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
