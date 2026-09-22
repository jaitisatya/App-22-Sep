package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserEntity
import com.example.data.repository.CollaborationManager
import com.example.data.repository.CollaborationRequest
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ClassViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborationScreen(
    currentUser: UserEntity,
    authViewModel: AuthViewModel,
    classViewModel: ClassViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        CollaborationManager.init(context)
    }

    val requests by CollaborationManager.requests.collectAsState()
    val allTeachers by classViewModel.allTeachers.collectAsState()

    var showInviteDialog by remember { mutableStateOf(false) }
    var showJoinRequestDialog by remember { mutableStateOf(false) }
    var selectedUserForPermissions by remember { mutableStateOf<UserEntity?>(null) }
    var selectedRequestForApproval by remember { mutableStateOf<CollaborationRequest?>(null) }

    val isMasterAdmin = currentUser.isMasterAdmin

    val pendingRequests = remember(requests) {
        requests.filter { it.status == "PENDING" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Team Collaboration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isMasterAdmin) "Admin Hub • Full Control" else "Connected Educator",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isMasterAdmin) {
                        IconButton(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.testTag("invite_educator_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Invite", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SkyBluePrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Center Invite Code Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Center Collaboration Code",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CollaborationManager.DEFAULT_CENTER_CODE,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SkyBlueDark,
                                    letterSpacing = 2.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Center Code", CollaborationManager.DEFAULT_CENTER_CODE)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Center Code Copied: ${CollaborationManager.DEFAULT_CENTER_CODE}", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F2FE)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = SkyBlueDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", color = SkyBlueDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Admin: ${CollaborationManager.MASTER_ADMIN_EMAIL}",
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 2. Shared Substitute Attendance Banner
            item {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SkyBlueDark,
                            modifier = Modifier.size(24.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Universal Substitute Attendance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E3A8A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Any teacher can take attendance for any class if a class teacher is absent. All logs sync with the teacher's name.",
                                fontSize = 12.sp,
                                color = Color(0xFF3B82F6),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 3. For Teachers: Join Center Action
            if (!isMasterAdmin) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Your Collaboration Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "● ACTIVE COLLABORATOR",
                                        color = Color(0xFF15803D),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = currentUser.email,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Your Granted Permissions:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            PermissionCheckRow(title = "Take Attendance for All Classes (Substitute)", isGranted = currentUser.canTakeAllAttendance)
                            PermissionCheckRow(title = "Add & Edit Student Profiles", isGranted = currentUser.canManageStudents)
                            PermissionCheckRow(title = "Create & Modify Classes", isGranted = currentUser.canManageClasses)
                            PermissionCheckRow(title = "View Class Reports & Export", isGranted = currentUser.canViewReports)

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = { showJoinRequestDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send New Request to Admin")
                            }
                        }
                    }
                }
            }

            // 4. For Admin: Pending Join Requests
            if (isMasterAdmin && pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Collaboration Requests (${pendingRequests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                items(pendingRequests, key = { it.id }) { req ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE047)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = req.senderName.ifBlank { "Educator" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF713F12)
                                    )
                                    Text(
                                        text = req.senderEmail,
                                        fontSize = 12.sp,
                                        color = Color(0xFF854D0E)
                                    )
                                }
                                Surface(
                                    color = Color(0xFFFEF08A),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "PENDING",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF713F12),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            CollaborationManager.declineRequest(req.id)
                                            Toast.makeText(context, "Request Declined", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Decline", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        selectedRequestForApproval = req
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Active Team Members Section (For Master Admin)
            if (isMasterAdmin) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connected Educators (${allTeachers.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )

                        TextButton(onClick = { showInviteDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = SkyBlueDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Invite", color = SkyBlueDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (allTeachers.isEmpty()) {
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No other teachers registered yet. Share the center code or tap 'Invite' to add your teachers.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(allTeachers, key = { it.userId }) { teacher ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(SkyBlueDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = teacher.fullName.firstOrNull()?.uppercase() ?: "T",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = teacher.fullName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            Text(
                                                text = teacher.email.ifBlank { teacher.username },
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { selectedUserForPermissions = teacher }) {
                                        Icon(Icons.Outlined.Settings, contentDescription = "Permissions", tint = SkyBlueDark)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "All Classes Attendance",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF15803D)
                                        )
                                    }

                                    TextButton(
                                        onClick = { selectedUserForPermissions = teacher },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Edit Access", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SkyBlueDark)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // --- DIALOGS ---

    // 1. Invite Educator Dialog
    if (showInviteDialog) {
        var inviteEmail by remember { mutableStateOf("") }
        var inviteName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SkyBluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite Teacher to Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter teacher's Gmail to send a collaboration invitation. Once they log in on their device, they will have access.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        label = { Text("Teacher Name") },
                        placeholder = { Text("e.g. Pooja Sharma") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Teacher Gmail / Email *") },
                        placeholder = { Text("e.g. pooja.teacher@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteEmail.isNotBlank()) {
                            scope.launch {
                                CollaborationManager.sendCollaborationRequest(
                                    senderName = currentUser.fullName,
                                    senderEmail = currentUser.email,
                                    receiverEmail = inviteEmail.trim(),
                                    role = "TEACHER"
                                )
                                // Also create a placeholder user in db if needed
                                classViewModel.addTeacher(
                                    username = inviteEmail.trim(),
                                    password = "password123",
                                    fullName = if (inviteName.isNotBlank()) inviteName.trim() else "Teacher",
                                    phone = "",
                                    assignedClassIds = emptyList()
                                )
                                Toast.makeText(context, "Invitation Sent to ${inviteEmail.trim()}!", Toast.LENGTH_LONG).show()
                                showInviteDialog = false
                            }
                        }
                    },
                    enabled = inviteEmail.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Send Invite", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInviteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Send Join Request Dialog (Teacher -> Admin)
    if (showJoinRequestDialog) {
        var inputCenterCode by remember { mutableStateOf(CollaborationManager.DEFAULT_CENTER_CODE) }

        AlertDialog(
            onDismissRequest = { showJoinRequestDialog = false },
            title = { Text("Join Foundation Center", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter Center Code to send join request to Jaiti Foundation Admin:")
                    OutlinedTextField(
                        value = inputCenterCode,
                        onValueChange = { inputCenterCode = it },
                        label = { Text("Center Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            CollaborationManager.sendCollaborationRequest(
                                senderName = currentUser.fullName,
                                senderEmail = currentUser.email,
                                receiverEmail = CollaborationManager.MASTER_ADMIN_EMAIL,
                                centerCode = inputCenterCode.trim()
                            )
                            Toast.makeText(context, "Collaboration Request Sent to Admin!", Toast.LENGTH_LONG).show()
                            showJoinRequestDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showJoinRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Approve Request Dialog (with permission toggles)
    if (selectedRequestForApproval != null) {
        val req = selectedRequestForApproval!!
        var canTakeAttendance by remember { mutableStateOf(true) }
        var canManageStudents by remember { mutableStateOf(true) }
        var canManageClasses by remember { mutableStateOf(true) }
        var canViewReports by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { selectedRequestForApproval = null },
            title = { Text("Approve Access for ${req.senderName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configure permissions for this teacher before approving:")

                    PermissionSwitchRow(
                        title = "Take Attendance for All Classes",
                        subtitle = "Allows substitute attendance when class teachers are absent",
                        checked = canTakeAttendance,
                        onCheckedChange = { canTakeAttendance = it }
                    )
                    PermissionSwitchRow(
                        title = "Add / Edit Students",
                        subtitle = "Manage student profiles and enrollment",
                        checked = canManageStudents,
                        onCheckedChange = { canManageStudents = it }
                    )
                    PermissionSwitchRow(
                        title = "Manage Classes",
                        subtitle = "Add, modify and arrange classes",
                        checked = canManageClasses,
                        onCheckedChange = { canManageClasses = it }
                    )
                    PermissionSwitchRow(
                        title = "Reports & Analytics",
                        subtitle = "View attendance rates & export CSV",
                        checked = canViewReports,
                        onCheckedChange = { canViewReports = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            CollaborationManager.approveRequest(
                                requestId = req.id,
                                database = null,
                                firestoreSyncManager = null,
                                canTakeAllAttendance = canTakeAttendance,
                                canManageStudents = canManageStudents,
                                canManageClasses = canManageClasses,
                                canViewReports = canViewReports
                            )
                            Toast.makeText(context, "Collaboration Approved!", Toast.LENGTH_SHORT).show()
                            selectedRequestForApproval = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Confirm & Approve", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedRequestForApproval = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Edit Permissions Dialog for Existing Teacher
    if (selectedUserForPermissions != null) {
        val teacher = selectedUserForPermissions!!
        var canTakeAttendance by remember { mutableStateOf(teacher.canTakeAllAttendance) }
        var canManageStudents by remember { mutableStateOf(teacher.canManageStudents) }
        var canManageClasses by remember { mutableStateOf(teacher.canManageClasses) }
        var canViewReports by remember { mutableStateOf(teacher.canViewReports) }

        AlertDialog(
            onDismissRequest = { selectedUserForPermissions = null },
            title = { Text("Permissions: ${teacher.fullName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermissionSwitchRow(
                        title = "Take Attendance for All Classes",
                        subtitle = "Allows substitute attendance when class teachers are absent",
                        checked = canTakeAttendance,
                        onCheckedChange = { canTakeAttendance = it }
                    )
                    PermissionSwitchRow(
                        title = "Add & Edit Students",
                        subtitle = "Can update student records",
                        checked = canManageStudents,
                        onCheckedChange = { canManageStudents = it }
                    )
                    PermissionSwitchRow(
                        title = "Add & Modify Classes",
                        subtitle = "Can configure classes and timings",
                        checked = canManageClasses,
                        onCheckedChange = { canManageClasses = it }
                    )
                    PermissionSwitchRow(
                        title = "View Reports & Export CSV",
                        subtitle = "Access to analytics & exports",
                        checked = canViewReports,
                        onCheckedChange = { canViewReports = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            classViewModel.updateTeacher(
                                teacher.copy(
                                    canTakeAllAttendance = canTakeAttendance,
                                    canManageStudents = canManageStudents,
                                    canManageClasses = canManageClasses,
                                    canViewReports = canViewReports
                                )
                            )
                            Toast.makeText(context, "Permissions Saved!", Toast.LENGTH_SHORT).show()
                            selectedUserForPermissions = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBluePrimary)
                ) {
                    Text("Save Permissions", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedUserForPermissions = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionCheckRow(title: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF16A34A) else Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            color = if (isGranted) Color(0xFF1E293B) else Color(0xFF94A3B8),
            fontWeight = if (isGranted) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun PermissionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text(text = subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SkyBluePrimary
            )
        )
    }
}
