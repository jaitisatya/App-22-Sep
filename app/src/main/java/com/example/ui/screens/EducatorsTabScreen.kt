package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.EducatorProfile
import com.example.data.model.Role
import com.example.data.repository.EducatorManager
import com.example.ui.components.EducatorAttendanceHistoryDialog
import com.example.ui.components.EducatorFormDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducatorsTabScreen(
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel? = null,
    currentUser: UserEntity? = null,
    onNavigateToEducatorAttendance: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = currentUser?.isMasterAdmin == true || currentUser?.role == Role.ADMIN

    LaunchedEffect(Unit) {
        EducatorManager.init(context)
    }

    val educators by EducatorManager.educators.collectAsState()
    val allAttendanceRecords by studentViewModel?.allAttendanceRecords?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    val firstDayOfMonth = remember { DateUtils.getFirstDayOfCurrentMonthIso() }
    val todayIso = remember { DateUtils.getTodayIso() }
    val currentMonthShort = remember { DateUtils.getCurrentMonthShortName() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedEducatorForEdit by remember { mutableStateOf<EducatorProfile?>(null) }
    var educatorForFullScreenPhoto by remember { mutableStateOf<EducatorProfile?>(null) }
    var educatorForAttendanceHistory by remember { mutableStateOf<Pair<EducatorProfile, List<AttendanceRecordEntity>>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredEducators = remember(educators, searchQuery) {
        if (searchQuery.isBlank()) {
            educators
        } else {
            educators.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.subject.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Educators",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${educators.size} Total Educators",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_educator_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Educator",
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
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_educator_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Educator", modifier = Modifier.size(26.dp))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search by Name or Subject...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("educator_search_input")
            )

            // Staff Attendance Quick Action Banner
            Surface(
                onClick = onNavigateToEducatorAttendance,
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Take Educator Attendance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${educators.size} Staff Members • Tap to mark P/A",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Table Column Header Bar (Exactly matching Students tab style)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EDUCATORS (${filteredEducators.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 66.dp)
                    )

                    // 1st Column Header: Dynamic Current Month (Sep, Oct, etc.)
                    Box(
                        modifier = Modifier.width(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentMonthShort,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2nd Column Header: Total
                    Box(
                        modifier = Modifier.width(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Total",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))

                        // 3rd Column Header: Edit
                        Box(
                            modifier = Modifier.width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Empty State
            if (filteredEducators.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No educators match '$searchQuery'" else "No Educators Added Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching with a different name or subject" else "Tap the '+' button above to add your first educator with Name, Photo, Phone & Subject.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        if (searchQuery.isBlank() && isAdmin) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Educator")
                            }
                        }
                    }
                }
            } else {
                // Educators List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredEducators, key = { it.id }) { educator ->
                        val eduRecords = remember(allAttendanceRecords, educator.name) {
                            allAttendanceRecords.filter { it.studentName.equals(educator.name, ignoreCase = true) }
                        }
                        val monthPresentCount = remember(eduRecords, firstDayOfMonth, todayIso) {
                            eduRecords.count { it.date >= firstDayOfMonth && it.date <= todayIso && it.status == AttendanceStatus.PRESENT }
                        }
                        val totalPresentCount = remember(eduRecords) {
                            eduRecords.count { it.status == AttendanceStatus.PRESENT }
                        }

                        EducatorItemRow(
                            educator = educator,
                            monthPresent = monthPresentCount,
                            totalPresent = totalPresentCount,
                            isAdmin = isAdmin,
                            onPhotoClick = { educatorForFullScreenPhoto = educator },
                            onAttendanceClick = {
                                educatorForAttendanceHistory = Pair(educator, eduRecords)
                            },
                            onEditClick = { selectedEducatorForEdit = educator }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }

    // 1. Full Screen Photo Zoom Dialog
    educatorForFullScreenPhoto?.let { educator ->
        FullScreenPhotoDialog(
            name = educator.name,
            photoUri = educator.photoUri.ifBlank { null },
            fatherName = if (educator.subject.isNotBlank()) "Subject: ${educator.subject}" else null,
            className = "Jaiti Educator",
            phoneNumber = educator.phone.ifBlank { null },
            onEditInfoClick = if (isAdmin) {
                {
                    val edu = educator
                    educatorForFullScreenPhoto = null
                    selectedEducatorForEdit = edu
                }
            } else null,
            onDismiss = { educatorForFullScreenPhoto = null }
        )
    }

    // 2. Educator Attendance History Dialog
    educatorForAttendanceHistory?.let { (educator, records) ->
        EducatorAttendanceHistoryDialog(
            educator = educator,
            allRecords = records,
            onDismiss = { educatorForAttendanceHistory = null }
        )
    }

    // 3. Add Educator Dialog (Fresh start - Admin only)
    if (showAddDialog && isAdmin) {
        EducatorFormDialog(
            initialEducator = null,
            readOnly = false,
            onDismiss = { showAddDialog = false },
            onSave = { name, email, photoUri, phone, subject ->
                EducatorManager.addEducator(
                    context = context,
                    name = name,
                    email = email,
                    photoUri = photoUri,
                    phone = phone,
                    subject = subject
                )
                showAddDialog = false
            }
        )
    }

    // 4. Edit / View Educator Dialog
    selectedEducatorForEdit?.let { currentEdu ->
        EducatorFormDialog(
            initialEducator = currentEdu,
            readOnly = !isAdmin,
            onDismiss = { selectedEducatorForEdit = null },
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
                }
                selectedEducatorForEdit = null
            },
            onDelete = if (isAdmin) {
                { eduId ->
                    EducatorManager.deleteEducator(context, eduId)
                    selectedEducatorForEdit = null
                }
            } else null
        )
    }
}

@Composable
fun EducatorItemRow(
    educator: EducatorProfile,
    monthPresent: Int,
    totalPresent: Int,
    isAdmin: Boolean,
    onPhotoClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isAdmin) onEditClick() else onAttendanceClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("educator_row_${educator.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Squircle Photo / Avatar (52dp)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable { onPhotoClick() }
                .testTag("educator_photo_${educator.name}"),
            contentAlignment = Alignment.Center
        ) {
            if (educator.photoUri.isNotBlank()) {
                AsyncImage(
                    model = ImageUtils.getImageModel(educator.photoUri),
                    contentDescription = educator.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initialLetter = educator.name.trim().firstOrNull()?.uppercase() ?: "E"
                Text(
                    text = initialLetter,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name only
        Text(
            text = educator.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Column 1: Current Month Present Count (Pill, Clickable) - 46.dp width
        Box(
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onAttendanceClick() }
                .padding(vertical = 5.dp)
                .testTag("educator_month_att_${educator.name}"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$monthPresent",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Column 2: Overall Total Present Count (Pill, Clickable) - 46.dp width
        Box(
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { onAttendanceClick() }
                .padding(vertical = 5.dp)
                .testTag("educator_total_att_${educator.name}"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$totalPresent",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )
        }

        // Pencil (Edit) Icon Button - ONLY FOR ADMIN (36.dp width)
        if (isAdmin) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onEditClick() }
                    .testTag("edit_educator_btn_${educator.name}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Educator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
