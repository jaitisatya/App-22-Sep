package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserEntity
import com.example.data.model.ActiveDeviceSession
import com.example.data.model.Role
import com.example.data.repository.ActiveDeviceSessionManager
import com.example.data.repository.CollaborationManager
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel
import com.example.viewmodel.StudentViewModel
import kotlinx.coroutines.launch

enum class UserFilterTab {
    ACTIVE_DEVICES,
    PENDING,
    ALL,
    APPROVED,
    DEACTIVATED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    currentUser: UserEntity,
    authViewModel: AuthViewModel,
    classViewModel: ClassViewModel,
    reportViewModel: ReportViewModel,
    studentViewModel: StudentViewModel? = null,
    onBack: (() -> Unit)? = null,
    onNavigateToClasses: () -> Unit = {},
    onNavigateToStudents: () -> Unit = {},
    onNavigateToReports: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect all users and pending requests
    val allUsers by authViewModel.allUsers.collectAsState(initial = emptyList())
    val rawClasses by classViewModel.allClasses.collectAsState(initial = emptyList())
    val allClasses = remember(rawClasses) {
        rawClasses.filter { cls ->
            val name = cls.className.trim().uppercase()
            val id = cls.classId.trim().uppercase()
            name == "J PREP" || name == "PREP" || id == "CLASS_J_PREP" ||
                    name in listOf("J1", "J2", "J3", "J4", "J5") ||
                    id in listOf("CLASS_J1", "CLASS_J2", "CLASS_J3", "CLASS_J4", "CLASS_J5")
        }
    }
    val allStudents by (studentViewModel?.allStudents?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })
    val nonAdminCount = remember(allUsers) { allUsers.count { !it.isMasterAdmin && it.role != Role.ADMIN } }
    val isLoading by authViewModel.isLoading.collectAsState()
    val todayFormatted = remember { DateUtils.getTodayFormatted() }
    val activeSessions by authViewModel.activeSessions.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.startListeningSessions(context)
    }

    var selectedTab by remember { mutableStateOf(UserFilterTab.ACTIVE_DEVICES) }
    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    // Dialog States
    var sessionForDeactivate by remember { mutableStateOf<ActiveDeviceSession?>(null) }
    var sessionForDelete by remember { mutableStateOf<ActiveDeviceSession?>(null) }
    var userForApprovalDetails by remember { mutableStateOf<UserEntity?>(null) }
    var userForPermissionsEdit by remember { mutableStateOf<UserEntity?>(null) }
    var userForDeleteConfirm by remember { mutableStateOf<UserEntity?>(null) }
    var showPurgeAllConfirm by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    // Compute stats
    val pendingUsers = remember(allUsers) {
        allUsers.filter {
            !it.isMasterAdmin && (it.collaborationStatus.equals("PENDING", ignoreCase = true) ||
                    it.collaborationStatus.equals("RESTRICTED", ignoreCase = true))
        }
    }

    val approvedUsers = remember(allUsers) {
        allUsers.filter {
            it.isMasterAdmin || (it.active && it.collaborationStatus.equals("APPROVED", ignoreCase = true))
        }
    }

    val deactivatedUsers = remember(allUsers) {
        allUsers.filter {
            !it.isMasterAdmin && (!it.active || it.collaborationStatus.equals("DEACTIVATED", ignoreCase = true) || it.collaborationStatus.equals("REJECTED", ignoreCase = true))
        }
    }

    // Filtered device sessions based on search
    val displayedSessions = remember(activeSessions, searchQuery) {
        if (searchQuery.isBlank()) {
            activeSessions
        } else {
            val q = searchQuery.trim().lowercase()
            activeSessions.filter {
                it.userName.lowercase().contains(q) ||
                        it.deviceModel.lowercase().contains(q) ||
                        it.userEmail.lowercase().contains(q) ||
                        it.userPhone.lowercase().contains(q)
            }
        }
    }

    // Filtered list based on selected tab and search query
    val displayedUsers = remember(allUsers, selectedTab, searchQuery) {
        val baseList = when (selectedTab) {
            UserFilterTab.ACTIVE_DEVICES -> emptyList()
            UserFilterTab.PENDING -> pendingUsers
            UserFilterTab.ALL -> allUsers
            UserFilterTab.APPROVED -> approvedUsers
            UserFilterTab.DEACTIVATED -> deactivatedUsers
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter {
                it.fullName.lowercase().contains(q) ||
                        it.email.lowercase().contains(q) ||
                        it.phone.lowercase().contains(q) ||
                        it.username.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Admin Dashboard",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Color.White
                            )
                            if (pendingUsers.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFEF4444),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${pendingUsers.size} PENDING",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "User Approvals & Access Management",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    // Sync with Firestore Button
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                authViewModel.syncUsersWithCloud()
                                authViewModel.cleanupDuplicateSessions(context)
                                isSyncing = false
                                Toast.makeText(context, "Synced & Cleaned with Cloud", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("admin_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Cloud",
                            tint = Color.White
                        )
                    }

                    // Add/Invite User
                    IconButton(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier.testTag("admin_add_user_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Invite Educator",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showInviteDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("admin_fab_invite")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Educator")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Registration", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Welcome & Organization Status Card (Clean & Modern)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = "Admin Workspace",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF16A34A), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Live Sync • $todayFormatted",
                                            fontSize = 11.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Center Code Copy Chip
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Center Code", CollaborationManager.DEFAULT_CENTER_CODE)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Center Code Copied: ${CollaborationManager.DEFAULT_CENTER_CODE}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = CollaborationManager.DEFAULT_CENTER_CODE,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Metrics Quick Stat Counters (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Students Stat
                        AdminStatMetricCard(
                            title = "Students",
                            count = "${allStudents.size}",
                            subtitle = "Enrolled Children",
                            accentColor = Color(0xFF2563EB),
                            containerColor = Color.White,
                            icon = Icons.Default.School,
                            isSelected = false,
                            onClick = onNavigateToStudents,
                            modifier = Modifier.weight(1f)
                        )

                        // Batches / Classes Stat
                        AdminStatMetricCard(
                            title = "Classes",
                            count = "${allClasses.size}",
                            subtitle = "Active Batches",
                            accentColor = Color(0xFF7C3AED),
                            containerColor = Color.White,
                            icon = Icons.Default.Class,
                            isSelected = false,
                            onClick = onNavigateToClasses,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Active Logged-in Devices Stat (Replacing Staff)
                        val activeDevicesCount = activeSessions.count { it.isActive }
                        AdminStatMetricCard(
                            title = "Active Devices",
                            count = "$activeDevicesCount",
                            subtitle = if (activeDevicesCount == 1) "1 Logged-in Device" else "$activeDevicesCount Logged-in Devices",
                            accentColor = Color(0xFF16A34A),
                            containerColor = if (selectedTab == UserFilterTab.ACTIVE_DEVICES) Color(0xFFDCFCE7) else Color.White,
                            icon = Icons.Default.Devices,
                            isSelected = selectedTab == UserFilterTab.ACTIVE_DEVICES,
                            onClick = { selectedTab = UserFilterTab.ACTIVE_DEVICES },
                            modifier = Modifier.weight(1f)
                        )

                        // Pending Registrations Stat (Highlighted if pending > 0)
                        AdminStatMetricCard(
                            title = "Pending",
                            count = "${pendingUsers.size}",
                            subtitle = if (pendingUsers.isNotEmpty()) "Action Required" else "All Reviewed",
                            accentColor = if (pendingUsers.isNotEmpty()) Color(0xFFD97706) else Color(0xFF64748B),
                            containerColor = if (selectedTab == UserFilterTab.PENDING && pendingUsers.isNotEmpty()) Color(0xFFFEF3C7) else Color.White,
                            icon = Icons.Default.PendingActions,
                            isSelected = selectedTab == UserFilterTab.PENDING,
                            onClick = { selectedTab = UserFilterTab.PENDING },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- SEARCH BAR ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_search_bar"),
                        placeholder = {
                            Text(
                                if (selectedTab == UserFilterTab.ACTIVE_DEVICES) "Search teacher or phone model..."
                                else "Search name, phone, or email...",
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF16A34A),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )
                }
            }

            // --- TAB CONTENT ---
            if (selectedTab == UserFilterTab.ACTIVE_DEVICES) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Active Logged-in Devices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                        ) {
                            Text(
                                text = "${displayedSessions.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        IconButton(
                            onClick = {
                                authViewModel.cleanupDuplicateSessions(context) { cleaned ->
                                    Toast.makeText(
                                        context,
                                        if (cleaned > 0) "Cleaned $cleaned duplicate device session(s)" else "Device sessions are already up-to-date",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(28.dp)
                                .testTag("btn_clean_duplicate_sessions")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clean Duplicate Sessions",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (displayedSessions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching logged-in devices found" else "No active logged-in devices",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "When educators sign in, their phone model and login details appear here with Deactivate and Delete controls.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(displayedSessions, key = { it.sessionId }) { session ->
                        ActiveDeviceSessionCard(
                            session = session,
                            onDeactivate = { sessionForDeactivate = session },
                            onDelete = { sessionForDelete = session },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            } else if (selectedTab == UserFilterTab.PENDING) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PendingActions,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pending Approvals",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Text(
                                text = "${displayedUsers.size} Pending",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (displayedUsers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "All Caught Up!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No pending approval requests. When unlisted teachers sign up, they will appear here for your review.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(displayedUsers, key = { it.userId }) { user ->
                        AdminUserRegistrationCard(
                            user = user,
                            isPending = true,
                            onApproveQuick = {
                                scope.launch {
                                    val ok = authViewModel.approveUser(user.userId)
                                    if (ok) {
                                        Toast.makeText(context, "${user.fullName} approved!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onApproveWithCustomPermissions = {
                                userForApprovalDetails = user
                            },
                            onReject = {
                                scope.launch {
                                    val ok = authViewModel.rejectUser(user.userId)
                                    if (ok) {
                                        Toast.makeText(context, "Registration rejected", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onToggleActive = { active ->
                                scope.launch {
                                    authViewModel.setUserActiveStatus(user.userId, active)
                                }
                            },
                            onEditPermissions = {
                                userForPermissionsEdit = user
                            },
                            onDeleteUser = {
                                userForDeleteConfirm = user
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                // ALL, APPROVED, or DEACTIVATED tabs
                items(displayedUsers, key = { it.userId }) { user ->
                    AdminUserRegistrationCard(
                        user = user,
                        isPending = user.isPendingApproval,
                        onApproveQuick = {
                            scope.launch {
                                authViewModel.approveUser(user.userId)
                            }
                        },
                        onApproveWithCustomPermissions = {
                            userForApprovalDetails = user
                        },
                        onReject = {
                            scope.launch {
                                authViewModel.rejectUser(user.userId)
                            }
                        },
                        onToggleActive = { active ->
                            scope.launch {
                                authViewModel.setUserActiveStatus(user.userId, active)
                            }
                        },
                        onEditPermissions = {
                            userForPermissionsEdit = user
                        },
                        onDeleteUser = {
                            userForDeleteConfirm = user
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // --- DIALOGS ---

    // Active Device Session - Deactivate Confirmation Dialog
    if (sessionForDeactivate != null) {
        val session = sessionForDeactivate!!
        AlertDialog(
            onDismissRequest = { sessionForDeactivate = null },
            icon = {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Deactivate Session?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will force log out ${session.userName} on device:",
                        fontSize = 14.sp,
                        color = Color(0xFF334155)
                    )
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = session.deviceModel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            if (session.userEmail.isNotBlank()) {
                                Text(
                                    text = session.userEmail,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                    Text(
                        text = "The teacher's app will immediately log out. They can log in again anytime using their account credentials.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = authViewModel.deactivateDeviceSession(context, session)
                            sessionForDeactivate = null
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    "Session deactivated for ${session.userName}. Device logged out.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Deactivate", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionForDeactivate = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Active Device Session - Delete Access Confirmation Dialog
    if (sessionForDelete != null) {
        val session = sessionForDelete!!
        AlertDialog(
            onDismissRequest = { sessionForDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Access for ${session.userName}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will permanently revoke ${session.userName}'s access and immediately log them out on:",
                        fontSize = 14.sp,
                        color = Color(0xFF334155)
                    )
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = session.deviceModel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                            if (session.userEmail.isNotBlank()) {
                                Text(
                                    text = session.userEmail,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "What happens next?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "• If their details are listed in your Educator (Staff) section, they can sign up again and will be auto-approved.",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "• If they are NOT in the Educator section, any future signup attempt will go to your 'Pending' section for approval.",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = authViewModel.deleteDeviceSessionAndUser(context, session)
                            sessionForDelete = null
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    "Access deleted for ${session.userName}.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Access", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionForDelete = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // 1. Approve User Dialog with Permissions Selection
    if (userForApprovalDetails != null) {
        val user = userForApprovalDetails!!
        var canTakeAttendance by remember { mutableStateOf(true) }
        var canManageStudents by remember { mutableStateOf(true) }
        var canManageClasses by remember { mutableStateOf(true) }
        var canViewReports by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { userForApprovalDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF16A34A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Approve Access for ${user.fullName}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Review granted privileges for this educator. Status will be saved as APPROVED in Firestore:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Account: ${user.email}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF166534))
                            if (user.phone.isNotBlank()) {
                                Text("Phone: ${user.phone}", fontSize = 12.sp, color = Color(0xFF15803D))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    AdminPermissionSwitchRow(
                        title = "Take Attendance for All Classes",
                        subtitle = "Substitute attendance permission",
                        checked = canTakeAttendance,
                        onCheckedChange = { canTakeAttendance = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "Manage Student Directory",
                        subtitle = "Add, edit student details and photos",
                        checked = canManageStudents,
                        onCheckedChange = { canManageStudents = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "Manage Classes & Timings",
                        subtitle = "Configure class schedules and rooms",
                        checked = canManageClasses,
                        onCheckedChange = { canManageClasses = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "View Reports & Export",
                        subtitle = "Access analytics and CSV downloads",
                        checked = canViewReports,
                        onCheckedChange = { canViewReports = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = authViewModel.approveUser(
                                userId = user.userId,
                                canTakeAllAttendance = canTakeAttendance,
                                canManageStudents = canManageStudents,
                                canManageClasses = canManageClasses,
                                canViewReports = canViewReports
                            )
                            if (ok) {
                                Toast.makeText(context, "${user.fullName} is now APPROVED!", Toast.LENGTH_SHORT).show()
                            }
                            userForApprovalDetails = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve & Save in Firestore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { userForApprovalDetails = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Edit Permissions Dialog for Existing User
    if (userForPermissionsEdit != null) {
        val user = userForPermissionsEdit!!
        var canTakeAttendance by remember { mutableStateOf(user.canTakeAllAttendance) }
        var canManageStudents by remember { mutableStateOf(user.canManageStudents) }
        var canManageClasses by remember { mutableStateOf(user.canManageClasses) }
        var canViewReports by remember { mutableStateOf(user.canViewReports) }
        var isAccountApproved by remember { mutableStateOf(user.isJaitiApproved) }

        AlertDialog(
            onDismissRequest = { userForPermissionsEdit = null },
            title = { Text("Edit Access: ${user.fullName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Access Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(if (isAccountApproved) "Approved & Active" else "Restricted / Pending", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Switch(
                                checked = isAccountApproved,
                                onCheckedChange = { isAccountApproved = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF16A34A))
                            )
                        }
                    }

                    AdminPermissionSwitchRow(
                        title = "Take Attendance for All Classes",
                        subtitle = "Substitute attendance permission",
                        checked = canTakeAttendance,
                        onCheckedChange = { canTakeAttendance = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "Manage Student Directory",
                        subtitle = "Add, edit student profiles",
                        checked = canManageStudents,
                        onCheckedChange = { canManageStudents = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "Manage Classes",
                        subtitle = "Configure classes and timings",
                        checked = canManageClasses,
                        onCheckedChange = { canManageClasses = it }
                    )
                    AdminPermissionSwitchRow(
                        title = "Reports & CSV Export",
                        subtitle = "View attendance metrics & reports",
                        checked = canViewReports,
                        onCheckedChange = { canViewReports = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            authViewModel.updateTeacherPermissions(
                                user = user,
                                canTakeAllAttendance = canTakeAttendance,
                                canManageStudents = canManageStudents,
                                canManageClasses = canManageClasses,
                                canViewReports = canViewReports,
                                collaborationStatus = if (isAccountApproved) "APPROVED" else "RESTRICTED"
                            )
                            Toast.makeText(context, "Updated permissions for ${user.fullName} in Firestore", Toast.LENGTH_SHORT).show()
                            userForPermissionsEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Permissions", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { userForPermissionsEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Complete Delete User Dialog
    if (userForDeleteConfirm != null) {
        val user = userForDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { userForDeleteConfirm = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account Completely", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to completely delete ${user.fullName} (${user.email})?",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "This will permanently purge this account from both local storage and Cloud Firestore. Once deleted, the teacher can sign up freshly again with the same email and phone number.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = authViewModel.deleteUser(user.userId)
                            if (ok) {
                                Toast.makeText(context, "Account deleted. They can now sign up again.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to delete account. Please try again.", Toast.LENGTH_SHORT).show()
                            }
                            userForDeleteConfirm = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Completely", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { userForDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3b. Purge All Non-Admin Users Confirmation Dialog
    if (showPurgeAllConfirm) {
        val count = allUsers.count { !it.isMasterAdmin && it.role != Role.ADMIN }
        AlertDialog(
            onDismissRequest = { showPurgeAllConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Test Teacher Accounts", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete all $count teacher / test accounts (Titu, Aasharam, Satyawan, etc.)?",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "• Only the Super Admin (${currentUser.email}) will be kept.\n• All other signup and teacher accounts will be completely wiped from both the local database and Cloud Firestore.\n• Teachers can sign up freshly afterwards.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = authViewModel.purgeAllNonAdminUsers()
                            if (ok) {
                                Toast.makeText(context, "All non-admin accounts cleared successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to clean accounts. Please check connection.", Toast.LENGTH_SHORT).show()
                            }
                            showPurgeAllConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Yes, Clear All Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPurgeAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Invite / Pre-register Educator Dialog
    if (showInviteDialog) {
        var inviteName by remember { mutableStateOf("") }
        var inviteEmail by remember { mutableStateOf("") }
        var invitePhone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Register / Invite Educator", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add an educator to the system. They will be pre-approved so they can log in immediately with full access:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        label = { Text("Full Name *") },
                        placeholder = { Text("e.g. Suman Devi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Email / Username *") },
                        placeholder = { Text("e.g. suman@jaiti.in") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_invite_email_input")
                    )

                    OutlinedTextField(
                        value = invitePhone,
                        onValueChange = { invitePhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("e.g. 9876543210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteName.isNotBlank() && inviteEmail.isNotBlank()) {
                            scope.launch {
                                classViewModel.addTeacher(
                                    username = inviteEmail.trim(),
                                    password = "password123",
                                    fullName = inviteName.trim(),
                                    phone = invitePhone.trim(),
                                    assignedClassIds = emptyList()
                                )
                                Toast.makeText(context, "Added and Approved ${inviteName.trim()}!", Toast.LENGTH_LONG).show()
                                showInviteDialog = false
                            }
                        }
                    },
                    enabled = inviteName.isNotBlank() && inviteEmail.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Register & Pre-Approve", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInviteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminStatMetricCard(
    title: String,
    count: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        border = if (isSelected) BorderStroke(2.dp, accentColor) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AdminQuickActionTile(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (badgeText != null) {
                    Surface(
                        color = iconTint.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = iconTint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActiveDeviceSessionCard(
    session: ActiveDeviceSession,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (session.isActive) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Device Icon + Teacher Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (session.isActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (session.isActive) Color(0xFF16A34A) else Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.userName.ifBlank { "Teacher" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (session.userRole.equals("ADMIN", ignoreCase = true)) Color(0xFFEDE9FE) else Color(0xFFE0F2FE)
                        ) {
                            Text(
                                text = session.userRole.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (session.userRole.equals("ADMIN", ignoreCase = true)) Color(0xFF6D28D9) else Color(0xFF0369A1),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Phone Model with company name (e.g. Samsung Galaxy S22)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = session.deviceModel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Active / Inactive Status Indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (session.isActive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, if (session.isActive) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (session.isActive) Color(0xFF16A34A) else Color(0xFFDC2626))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (session.isActive) "Online" else "Inactive",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (session.isActive) Color(0xFF166534) else Color(0xFF991B1B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details metadata: Email, Phone & Login Time
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (session.userEmail.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = session.userEmail, fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }
                    if (session.userPhone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = session.userPhone, fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Logged in: ${ActiveDeviceSessionManager.formatSessionTime(session.loginTime)}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            val isAdminSession = session.userRole.equals("ADMIN", ignoreCase = true) ||
                    session.userEmail.equals("jaitifoundation@gmail.com", ignoreCase = true)

            if (!isAdminSession) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons: 1. Deactivate, 2. Delete (Only for teachers/collaborators)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Deactivate Button
                    OutlinedButton(
                        onClick = onDeactivate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFFBEB),
                            contentColor = Color(0xFFB45309)
                        )
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFB45309)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Deactivate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // 2. Delete Button
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Primary Admin Session • Protected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserRegistrationCard(
    user: UserEntity,
    isPending: Boolean,
    onApproveQuick: () -> Unit,
    onApproveWithCustomPermissions: () -> Unit,
    onReject: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEditPermissions: () -> Unit,
    onDeleteUser: () -> Unit
) {
    val isApproved = user.isJaitiApproved
    val isEffectivelyActive = user.active && isApproved
    val isDeactivated = !user.isMasterAdmin && (!user.active || user.collaborationStatus.equals("DEACTIVATED", ignoreCase = true) || user.collaborationStatus.equals("REJECTED", ignoreCase = true))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPending -> Color(0xFFFFFBEB)
                isDeactivated -> Color(0xFFFAFAFA)
                else -> Color.White
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            when {
                isPending -> Color(0xFFFDE68A)
                isDeactivated -> Color(0xFFFECACA)
                else -> Color(0xFFE2E8F0)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_card_${user.userId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Avatar, Name, Email, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    user.isMasterAdmin -> Color(0xFF1E293B)
                                    isPending -> Color(0xFFD97706)
                                    isDeactivated -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.fullName.firstOrNull()?.uppercase() ?: "U",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.fullName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user.isMasterAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "MASTER",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = user.email.ifBlank { user.username },
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (user.phone.isNotBlank()) {
                            Text(
                                text = "📞 ${user.phone}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    color = when {
                        user.isMasterAdmin -> Color(0xFFE2E8F0)
                        isPending -> Color(0xFFFEF3C7)
                        isDeactivated -> Color(0xFFFEE2E2)
                        else -> Color(0xFFDCFCE7)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when {
                            user.isMasterAdmin -> "SUPER ADMIN"
                            isPending -> "PENDING"
                            isDeactivated -> "BLOCKED"
                            else -> "ACTIVE"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            user.isMasterAdmin -> Color(0xFF1E293B)
                            isPending -> Color(0xFF92400E)
                            isDeactivated -> Color(0xFF991B1B)
                            else -> Color(0xFF15803D)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons based on status
            if (isPending) {
                // Pending State: Prominent Approve and Reject buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_reject_btn_${user.userId}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onApproveQuick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("admin_approve_btn_${user.userId}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = onApproveWithCustomPermissions,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Custom Permissions", tint = Color(0xFF64748B))
                    }
                }
            } else {
                // Non-Pending State: Active or Deactivated Educator Account
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isEffectivelyActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isEffectivelyActive) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEffectivelyActive) "Account Active • Access Enabled" else "Account Deactivated • Access Blocked",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isEffectivelyActive) Color(0xFF15803D) else Color(0xFF991B1B)
                            )
                        }

                        if (!user.isMasterAdmin) {
                            TextButton(
                                onClick = onEditPermissions,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Admin Action Buttons: Activate / Deactivate + Delete
                    if (!user.isMasterAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEffectivelyActive) {
                                // When Active: Show "Deactivate" and "Delete"
                                OutlinedButton(
                                    onClick = { onToggleActive(false) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("admin_deactivate_btn_${user.userId}")
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Deactivate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                // When Deactivated: Show "Activate" and "Delete"
                                Button(
                                    onClick = { onToggleActive(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("admin_activate_btn_${user.userId}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Activate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Delete button (Purges completely from Cloud Firestore and Local DB)
                            OutlinedButton(
                                onClick = onDeleteUser,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_delete_btn_${user.userId}")
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPermissionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF16A34A)
                )
            )
        }
    }
}

