package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserEntity
import com.example.data.model.Role
import com.example.ui.components.ReminderSettingsDialog
import com.example.ui.components.ShareAppDialog
import com.example.ui.components.ThemeSelectionDialog
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.ui.theme.ThemeManager
import com.example.util.AttendanceReminderManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreTabScreen(
    currentUser: UserEntity,
    onNavigateToTeacherProfile: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToCollaboration: () -> Unit = {},
    onNavigateToAdminDashboard: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = currentUser.isMasterAdmin || currentUser.role == Role.ADMIN

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val currentAccentHex by ThemeManager.accentHex.collectAsState()
    val teacherTitle = remember { ThemeManager.getTeacherLocalTitle() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "More",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = { showShareAppDialog = true },
                            modifier = Modifier.testTag("more_share_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share App",
                                tint = Color.White
                            )
                        }
                    }
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("more_logout_btn")
                    ) {
                        Text(
                            text = "Logout",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                // User Header Badge (Clickable only for Admin to open Theme & Profile customization)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isAdmin) {
                                Modifier.clickable { onNavigateToTeacherProfile() }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("more_user_header_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.fullName.firstOrNull()?.uppercase() ?: "T",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser.fullName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = currentUser.role.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isAdmin) "$teacherTitle • Jaiti Foundation" else "Teacher • Jaiti Foundation",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            if (isAdmin) {
                                Text(
                                    text = "Tap to customize profile & accent color (Device only)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isAdmin) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Customize Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Quick Share Banner Card (Admin Only)
            if (isAdmin) {
                item {
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable { showShareAppDialog = true }
                            .testTag("more_share_banner_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Application",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Share App with Educators",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF14532D)
                                )
                                Text(
                                    text = "Send APK file directly to install on other phones",
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Button(
                                onClick = { showShareAppDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Menu Items
            item {
                if (isAdmin) {
                    // 0. Admin Dashboard & User Approvals (Only Admin)
                    MoreMenuItemRow(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = "Admin Dashboard & User Approvals",
                        subtitle = "Review pending registrations, approve educators & sync status",
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToAdminDashboard
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

                    // 1. Reports & Analytics (Only Admin)
                    MoreMenuItemRow(
                        icon = Icons.Outlined.Assessment,
                        title = "Reports & Analytics",
                        subtitle = "Class attendance metrics & 100% Free CSV exports",
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToReports
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))

                    // 2. Attendance Time & Reminder Settings (Only Admin)
                    val (attHour, attMinute) = remember(showReminderDialog) { AttendanceReminderManager.getAttendanceTime(context) }
                    val attTimeFormatted = AttendanceReminderManager.formatTimeString(attHour, attMinute)

                    val reminderEnabled = remember(showReminderDialog) { AttendanceReminderManager.isReminderEnabled(context) }
                    val (reminderHour, reminderMinute) = remember(showReminderDialog) { AttendanceReminderManager.getReminderTime(context) }
                    val reminderTimeFormatted = AttendanceReminderManager.formatTimeString(reminderHour, reminderMinute)

                    val timingSubtitle = if (reminderEnabled) {
                        "Attendance: $attTimeFormatted • Reminder: $reminderTimeFormatted (Active)"
                    } else {
                        "Attendance: $attTimeFormatted • Reminder: Off"
                    }

                    MoreMenuItemRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Attendance Time & Reminder",
                        subtitle = timingSubtitle,
                        titleColor = if (reminderEnabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface,
                        onClick = { showReminderDialog = true }
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))
                }

                // App Theme (Light / Dark) - Only shown for non-admin Teachers (Admin accesses it via top profile card)
                if (!isAdmin) {
                    val themeSubtitle = when (currentThemeMode) {
                        AppThemeMode.SYSTEM -> "System default"
                        AppThemeMode.LIGHT -> "Light mode"
                        AppThemeMode.DARK -> "Dark mode"
                    }
                    MoreMenuItemRow(
                        icon = if (currentThemeMode == AppThemeMode.DARK) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        title = "App Theme (Light / Dark)",
                        subtitle = "Current: $themeSubtitle • Tap to switch",
                        titleColor = MaterialTheme.colorScheme.primary,
                        onClick = { showThemeDialog = true }
                    )
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))
                }

                // Delete Account - Available for both Teacher and Admin
                MoreMenuItemRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Delete My Account",
                    subtitle = "Remove local session and cached identity data",
                    titleColor = Color(0xFFEF4444),
                    onClick = { showDeleteAccountDialog = true }
                )
            }

            // App Footer
            item {
                Spacer(modifier = Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Jaiti Foundation Attendance System",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Version 4.1.5 (Build 2026) • All features Free",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs
    if (showShareAppDialog) {
        ShareAppDialog(onDismiss = { showShareAppDialog = false })
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            onDismiss = { showThemeDialog = false },
            onOpenTeacherProfile = if (isAdmin) onNavigateToTeacherProfile else null
        )
    }

    if (showReminderDialog) {
        ReminderSettingsDialog(onDismiss = { showReminderDialog = false })
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Confirm Logout & Clear Cache", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Text("Are you sure you want to log out and clear the active local session cache?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Confirm Logout")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MoreMenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (titleColor == Color(0xFFEF4444)) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
