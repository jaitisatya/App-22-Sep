package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UserEntity
import com.example.data.model.Role
import com.example.ui.components.AccessRestrictedDialog
import com.example.ui.theme.JaitiGreenCardBg
import com.example.ui.theme.JaitiGreenDark
import com.example.ui.theme.JaitiGreenLight
import com.example.ui.theme.JaitiGreenPrimary
import com.example.util.DateUtils
import com.example.viewmodel.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String) {
    object ClassesTab : Screen("classes_tab", "Classes")
    object EducatorsTab : Screen("educators_tab", "Educators")
    object AttendanceTab : Screen("attendance_tab", "Attendance")
    object StudentsTab : Screen("students_tab", "Students")
    object MoreTab : Screen("more_tab", "More")
    object TeacherProfileScreen : Screen("teacher_profile", "Teacher Profile")
    object ReportsScreen : Screen("reports_screen", "Reports")
    object CollaborationScreen : Screen("collaboration_screen", "Collaboration")
    object AdminDashboardScreen : Screen("admin_dashboard_screen", "Admin Dashboard")
    data class ClassDetail(val classId: String, val className: String) : Screen("class_detail", "Class Detail")
    data class ClassStudentsScreen(val classId: String, val className: String) : Screen("class_students", "Class Students")
    data class ClassTests(val classId: String, val className: String) : Screen("class_tests", "Class Tests")
    data class AddEditTest(val classId: String, val className: String, val testId: String? = null) : Screen("add_edit_test", "Test Marks Entry")
    data class DailyAttendance(val classId: String, val className: String, val dateIso: String? = null) : Screen("daily_attendance", "Class Attendance")
    data class AttendanceHistory(val filterStudentId: String? = null) : Screen("history", "Attendance History")
}

@Composable
fun MainScreen(
    currentUser: UserEntity,
    authViewModel: AuthViewModel,
    attendanceViewModel: AttendanceViewModel,
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel,
    reportViewModel: ReportViewModel,
    testExamViewModel: TestExamViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.AttendanceTab) }
    val backStack = remember { mutableStateListOf<Screen>() }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    var showRestrictedDialog by remember { mutableStateOf(currentUser.isPendingApproval) }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun handleBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.lastIndex)
        } else if (currentScreen !is Screen.AttendanceTab) {
            currentScreen = Screen.AttendanceTab
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // System back button handler
    BackHandler(enabled = true) {
        handleBack()
    }

    LaunchedEffect(currentUser.collaborationStatus, currentUser.active) {
        if (!currentUser.isMasterAdmin && (!currentUser.active || currentUser.collaborationStatus.equals("DEACTIVATED", ignoreCase = true))) {
            Toast.makeText(context, "Your account has been deactivated by administrator.", Toast.LENGTH_LONG).show()
            authViewModel.logout()
            onLogout()
        } else if (currentUser.isJaitiApproved) {
            showRestrictedDialog = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Hide bottom bar when inside specific class attendance view or sub-screens
            if (currentScreen !is Screen.DailyAttendance && currentScreen !is Screen.ClassDetail && currentScreen !is Screen.ClassStudentsScreen && currentScreen !is Screen.ReportsScreen && currentScreen !is Screen.CollaborationScreen && currentScreen !is Screen.AdminDashboardScreen && currentScreen !is Screen.TeacherProfileScreen && currentScreen !is Screen.ClassTests && currentScreen !is Screen.AddEditTest) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                    // 1. Classes Tab
                    NavigationBarItem(
                        selected = currentScreen is Screen.ClassesTab,
                        onClick = { navigateTo(Screen.ClassesTab) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.ClassesTab) Icons.Default.Class else Icons.Outlined.Class,
                                contentDescription = "Classes"
                            )
                        },
                        label = {
                            Text(
                                text = "Classes",
                                fontSize = 10.sp,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_classes")
                    )

                    // 2. Educators Tab
                    NavigationBarItem(
                        selected = currentScreen is Screen.EducatorsTab,
                        onClick = { navigateTo(Screen.EducatorsTab) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.EducatorsTab) Icons.Default.Badge else Icons.Outlined.Badge,
                                contentDescription = "Educators"
                            )
                        },
                        label = {
                            Text(
                                text = "Educators",
                                fontSize = 10.sp,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_educators")
                    )

                    // 3. Attendance Tab (Center Home)
                    NavigationBarItem(
                        selected = currentScreen is Screen.AttendanceTab,
                        onClick = { navigateTo(Screen.AttendanceTab) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.AttendanceTab) Icons.Default.Today else Icons.Outlined.Today,
                                contentDescription = "Attendance"
                            )
                        },
                        label = {
                            Text(
                                text = "Attendance",
                                fontSize = 10.sp,
                                letterSpacing = (-0.4).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_attendance")
                    )

                    // 4. Students Tab
                    NavigationBarItem(
                        selected = currentScreen is Screen.StudentsTab,
                        onClick = { navigateTo(Screen.StudentsTab) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.StudentsTab) Icons.Default.People else Icons.Outlined.People,
                                contentDescription = "Students"
                            )
                        },
                        label = {
                            Text(
                                text = "Students",
                                fontSize = 10.sp,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_students")
                    )

                    // 5. More Tab
                    NavigationBarItem(
                        selected = currentScreen is Screen.MoreTab,
                        onClick = { navigateTo(Screen.MoreTab) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.MoreTab) Icons.Default.MoreHoriz else Icons.Outlined.MoreHoriz,
                                contentDescription = "More"
                            )
                        },
                        label = {
                            Text(
                                text = "More",
                                fontSize = 10.sp,
                                letterSpacing = (-0.3).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_more")
                    )
                }
            }
        }
    }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
            // Pending Approval Top Banner
            if (currentUser.isPendingApproval) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRestrictedDialog = true }
                        .testTag("restricted_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Access Restricted: Awaiting Jaiti Registration",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                        Text(
                            text = "Contact Jaiti",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (val screen = currentScreen) {
                    is Screen.AttendanceTab -> {
                        AttendanceTabScreen(
                            classViewModel = classViewModel,
                            studentViewModel = studentViewModel,
                            attendanceViewModel = attendanceViewModel,
                            reportViewModel = reportViewModel,
                            onNavigateToTakeAttendance = { classId, className, dateIso ->
                                navigateTo(Screen.DailyAttendance(classId, className, dateIso))
                            }
                        )
                    }

                    is Screen.ClassesTab -> {
                        ClassesTabScreen(
                            classViewModel = classViewModel,
                            studentViewModel = studentViewModel,
                            currentUser = currentUser,
                            onNavigateToClassDetail = { classId, className ->
                                navigateTo(Screen.ClassDetail(classId, className))
                            },
                            onNavigateToTakeAttendance = { classId, className ->
                                navigateTo(Screen.DailyAttendance(classId, className))
                            }
                        )
                    }

                    is Screen.EducatorsTab -> {
                        EducatorsTabScreen(
                            classViewModel = classViewModel,
                            studentViewModel = studentViewModel,
                            currentUser = currentUser,
                            onNavigateToEducatorAttendance = {
                                navigateTo(Screen.DailyAttendance("CLASS_EDUCATORS", "Educators (Staff)"))
                            }
                        )
                    }

                    is Screen.StudentsTab -> {
                        StudentsTabScreen(
                            studentViewModel = studentViewModel,
                            classViewModel = classViewModel,
                            currentUser = currentUser,
                            onViewStudentHistory = { studentId ->
                                navigateTo(Screen.AttendanceHistory(filterStudentId = studentId))
                            }
                        )
                    }

                    is Screen.MoreTab -> {
                        MoreTabScreen(
                            currentUser = currentUser,
                            onNavigateToTeacherProfile = {
                                navigateTo(Screen.TeacherProfileScreen)
                            },
                            onNavigateToReports = {
                                navigateTo(Screen.ReportsScreen)
                            },
                            onNavigateToCollaboration = {
                                navigateTo(Screen.CollaborationScreen)
                            },
                            onNavigateToAdminDashboard = {
                                navigateTo(Screen.AdminDashboardScreen)
                            },
                            onLogout = {
                                authViewModel.logout()
                                onLogout()
                            }
                        )
                    }

                    is Screen.TeacherProfileScreen -> {
                        if (currentUser.isMasterAdmin || currentUser.role == Role.ADMIN) {
                            TeacherProfileScreen(
                                currentUser = currentUser,
                                onBack = {
                                    handleBack()
                                }
                            )
                        } else {
                            // Non-admins redirected back safely
                            LaunchedEffect(Unit) {
                                handleBack()
                            }
                        }
                    }

                    is Screen.AdminDashboardScreen -> {
                        if (currentUser.isMasterAdmin || currentUser.role == Role.ADMIN) {
                            AdminDashboardScreen(
                                currentUser = currentUser,
                                authViewModel = authViewModel,
                                classViewModel = classViewModel,
                                studentViewModel = studentViewModel,
                                reportViewModel = reportViewModel,
                                onBack = {
                                    handleBack()
                                },
                                onNavigateToClasses = {
                                    navigateTo(Screen.ClassesTab)
                                },
                                onNavigateToStudents = {
                                    navigateTo(Screen.StudentsTab)
                                },
                                onNavigateToReports = {
                                    navigateTo(Screen.ReportsScreen)
                                }
                            )
                        } else {
                            // Non-admins redirected back safely
                            LaunchedEffect(Unit) {
                                handleBack()
                            }
                        }
                    }

                    is Screen.CollaborationScreen -> {
                        CollaborationScreen(
                            currentUser = currentUser,
                            authViewModel = authViewModel,
                            classViewModel = classViewModel,
                            onBack = {
                                handleBack()
                            }
                        )
                    }

                    is Screen.ReportsScreen -> {
                        ReportsTabScreen(
                            reportViewModel = reportViewModel,
                            classViewModel = classViewModel,
                            studentViewModel = studentViewModel,
                            currentUser = currentUser,
                            onBack = {
                                handleBack()
                            }
                        )
                    }

                    is Screen.ClassDetail -> {
                        ClassDetailScreen(
                            classId = screen.classId,
                            className = screen.className,
                            classViewModel = classViewModel,
                            studentViewModel = studentViewModel,
                            currentUser = currentUser,
                            onBack = {
                                handleBack()
                            },
                            onNavigateToClassStudents = { classId, className ->
                                navigateTo(Screen.ClassStudentsScreen(classId, className))
                            },
                            onNavigateToClassTests = { classId, className ->
                                navigateTo(Screen.ClassTests(classId, className))
                            },
                            onNavigateToAddTestMarks = { classId, className ->
                                navigateTo(Screen.AddEditTest(classId, className))
                            }
                        )
                    }

                    is Screen.ClassTests -> {
                        ClassTestsScreen(
                            classId = screen.classId,
                            className = screen.className,
                            currentUser = currentUser,
                            testExamViewModel = testExamViewModel,
                            onBack = {
                                handleBack()
                            },
                            onNavigateToAddTest = {
                                navigateTo(Screen.AddEditTest(screen.classId, screen.className))
                            },
                            onNavigateToEditTest = { testId ->
                                navigateTo(Screen.AddEditTest(screen.classId, screen.className, testId))
                            }
                        )
                    }

                    is Screen.AddEditTest -> {
                        AddEditTestScreen(
                            classId = screen.classId,
                            className = screen.className,
                            existingTestId = screen.testId,
                            currentUser = currentUser,
                            testExamViewModel = testExamViewModel,
                            studentViewModel = studentViewModel,
                            onBack = {
                                handleBack()
                            }
                        )
                    }

                    is Screen.ClassStudentsScreen -> {
                        StudentsTabScreen(
                            studentViewModel = studentViewModel,
                            classViewModel = classViewModel,
                            currentUser = currentUser,
                            initialClassFilter = screen.className,
                            onBack = {
                                handleBack()
                            },
                            onViewStudentHistory = { studentId ->
                                navigateTo(Screen.AttendanceHistory(filterStudentId = studentId))
                            }
                        )
                    }

                    is Screen.DailyAttendance -> {
                        DailyAttendanceScreen(
                            classId = screen.classId,
                            className = screen.className,
                            dateIso = screen.dateIso ?: DateUtils.getTodayIso(),
                            currentUser = currentUser,
                            attendanceViewModel = attendanceViewModel,
                            studentViewModel = studentViewModel,
                            classViewModel = classViewModel,
                            onBack = {
                                handleBack()
                            }
                        )
                    }

                    is Screen.AttendanceHistory -> {
                        AttendanceHistoryScreen(
                            reportViewModel = reportViewModel,
                            classViewModel = classViewModel,
                            initialStudentIdFilter = screen.filterStudentId,
                            onBack = {
                                handleBack()
                            }
                        )
                    }
                }
            }
        }
    }
}

    // Access Restricted Dialog for Unregistered Users
    if (showRestrictedDialog) {
        AccessRestrictedDialog(
            currentUser = currentUser,
            onDismiss = { showRestrictedDialog = false },
            onRefreshStatus = {
                scope.launch {
                    authViewModel.refreshCurrentUser()
                    Toast.makeText(context, "Checking registration status with Jaiti Admin...", Toast.LENGTH_SHORT).show()
                }
            },
            onLogout = {
                showRestrictedDialog = false
                authViewModel.logout()
                onLogout()
            }
        )
    }
}
