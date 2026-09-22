package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.data.entity.ClassEntity
import com.example.data.model.AttendanceStatus
import com.example.data.repository.ClassPhotoManager
import com.example.data.repository.ClassTimingManager
import com.example.data.repository.EducatorManager
import com.example.ui.components.ClassPhotoUploadDialog
import com.example.ui.components.FullScreenImageViewerDialog
import com.example.ui.components.ShareAppDialog
import com.example.ui.theme.*
import com.example.util.AttendanceReminderManager
import com.example.util.CalendarDayItem
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.viewmodel.AttendanceViewModel
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.ReportViewModel
import com.example.viewmodel.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceTabScreen(
    classViewModel: ClassViewModel,
    studentViewModel: StudentViewModel,
    attendanceViewModel: AttendanceViewModel,
    reportViewModel: ReportViewModel,
    onNavigateToTakeAttendance: (classId: String, className: String, dateIso: String) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        EducatorManager.init(context)
        ClassTimingManager.startListening(context)
        ClassPhotoManager.startListening(context)
    }

    val classes by classViewModel.activeClasses.collectAsState()
    val students by studentViewModel.allStudents.collectAsState()
    val educators by EducatorManager.educators.collectAsState()
    val historyRecords by reportViewModel.allRecords.collectAsState()
    val isRefreshing by attendanceViewModel.isRefreshing.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDateIso by remember { mutableStateOf(DateUtils.getTodayIso()) }
    val isSelectedDateToday = remember(selectedDateIso) {
        selectedDateIso == DateUtils.getTodayIso()
    }
    val isSelectedDateSunday = remember(selectedDateIso) {
        DateUtils.isSunday(selectedDateIso)
    }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }

    // Dialog state for Class Photo Upload (Today's date)
    var photoUploadTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(classId, className)

    // Dialog state for Full Screen Photo Viewing (Past dates or any date)
    var fullScreenPhotoTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // Triple(photoBase64, title, subtitle)

    // Observe class photos and class timings changes to trigger reactive UI updates
    val classPhotosVersion by ClassPhotoManager.photosVersion.collectAsState()
    val timingsVersion by ClassTimingManager.timingsVersion.collectAsState()

    // Configured default Attendance Time from Settings & ClassTimingManager (reactive to timingsVersion)
    val defaultAttendanceTime = remember(timingsVersion) {
        ClassTimingManager.getGlobalAttendanceTime(context)
    }

    // Dynamic Class Timings Map (Synced locally and across all devices via Firestore)
    val classTimings = remember(timingsVersion) {
        ClassTimingManager.getAllTimings(context)
    }

    // Dynamic monthly session count from actual attendance records for the selected month
    val selectedMonthPrefix = remember(selectedDateIso) {
        if (selectedDateIso.length >= 7) selectedDateIso.substring(0, 7) else ""
    }
    val dynamicMonthlySessionsCount = remember(historyRecords, selectedMonthPrefix) {
        if (selectedMonthPrefix.isBlank()) 0
        else historyRecords
            .filter { it.date.startsWith(selectedMonthPrefix) }
            .map { it.date }
            .distinct()
            .size
    }

    // Dynamic registered active students count
    val totalActiveRegisteredStudents = remember(students) {
        students.count { it.active }
    }

    val activeStudentIds = remember(students) {
        students.filter { it.active }.map { it.studentId }.toSet()
    }

    // Unique children marked PRESENT for selected date
    val uniquePresentStudentsToday = remember(historyRecords, selectedDateIso, activeStudentIds) {
        historyRecords
            .filter { it.date == selectedDateIso && it.status == AttendanceStatus.PRESENT && it.classId != "CLASS_EDUCATORS" && it.studentId in activeStudentIds }
            .map { it.studentId }
            .distinct()
            .size
    }

    // Calculate student count per class across all classes
    val classStudentCounts = remember(classes, students) {
        classes.associate { cls ->
            cls.classId to students.count {
                it.active && (it.classId.equals(cls.classId, ignoreCase = true) || it.classId.equals(cls.className, ignoreCase = true))
            }
        }
    }

    // Dynamic filtering: Display valid classes/batches (J Prep, J1 to J5) having at least 1 student
    val activeClassesWithStudents = remember(classes, classStudentCounts) {
        classes.filter { cls ->
            val name = cls.className.trim().uppercase()
            val id = cls.classId.trim().uppercase()
            val isValidBatch = name == "J PREP" || name == "PREP" || id == "CLASS_J_PREP" ||
                    name in listOf("J1", "J2", "J3", "J4", "J5") ||
                    id in listOf("CLASS_J1", "CLASS_J2", "CLASS_J3", "CLASS_J4", "CLASS_J5")
            val count = classStudentCounts[cls.classId] ?: 0
            isValidBatch && count > 0
        }.sortedWith(Comparator { c1, c2 ->
            com.example.util.BatchConstants.getBatchSortOrder(c1.className)
                .compareTo(com.example.util.BatchConstants.getBatchSortOrder(c2.className))
        })
    }

    // Calculate done classes for selected date
    val doneClassIds = remember(historyRecords, selectedDateIso) {
        historyRecords.filter { it.date == selectedDateIso }.map { it.classId }.toSet()
    }
    val doneCount = activeClassesWithStudents.count { doneClassIds.contains(it.classId) }
    val totalClasses = activeClassesWithStudents.size

    // Dynamic all-time session count from actual attendance records
    val totalAllTimeSessionsCount = remember(historyRecords) {
        historyRecords
            .map { it.date }
            .distinct()
            .size
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Attendance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                actions = {
                    // Manual 1-Tap Sync with Cloud Firestore
                    IconButton(
                        onClick = {
                            attendanceViewModel.refreshAttendanceData { success, msg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = if (success) "✓ $msg" else "⚠️ $msg",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        },
                        modifier = Modifier.testTag("attendance_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync from Firestore",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showShareAppDialog = true },
                        modifier = Modifier.testTag("attendance_share_app_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share App",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help",
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 731 Days rolling window (-365 to +365 days) for bi-directional endless scrolling
            val rollingDays = remember {
                DateUtils.getRollingDaysRange(pastDays = 365, futureDays = 365)
            }

            val initialIndex = remember {
                val idx = rollingDays.indexOfFirst { it.isoDate == selectedDateIso }
                if (idx != -1) maxOf(0, idx - 3) else 362
            }
            val calendarStripState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

            // When selectedDateIso changes (e.g. from calendar picker), center it in the view
            LaunchedEffect(selectedDateIso) {
                val idx = rollingDays.indexOfFirst { it.isoDate == selectedDateIso }
                if (idx != -1) {
                    val targetFirstVisible = maxOf(0, idx - 3)
                    if (kotlin.math.abs(calendarStripState.firstVisibleItemIndex - targetFirstVisible) > 12) {
                        calendarStripState.scrollToItem(targetFirstVisible)
                    } else {
                        calendarStripState.animateScrollToItem(targetFirstVisible)
                    }
                }
            }

            // Middle visible item in the strip
            val centerVisibleItemIndex by remember {
                derivedStateOf {
                    val visibleItems = calendarStripState.layoutInfo.visibleItemsInfo
                    if (visibleItems.isNotEmpty()) {
                        val mid = visibleItems.size / 2
                        visibleItems[mid].index
                    } else {
                        calendarStripState.firstVisibleItemIndex
                    }
                }
            }

            val currentVisibleDayItem = remember(centerVisibleItemIndex, rollingDays) {
                rollingDays.getOrNull(centerVisibleItemIndex)
            }

            // Month and Year displayed on top dynamically updates when user scrolls into other months!
            val visibleMonthYear = currentVisibleDayItem?.monthYear?.ifBlank {
                DateUtils.getMonthYearDisplay(selectedDateIso)
            } ?: DateUtils.getMonthYearDisplay(selectedDateIso)

            val selectedDayItem = remember(selectedDateIso, rollingDays) {
                rollingDays.firstOrNull { it.isoDate == selectedDateIso }
            }

            // Date number displayed before Month, Year (e.g., "15 Sep, 2026")
            val isSelectedInVisibleMonth = (selectedDayItem?.monthYear == visibleMonthYear)
            val headerDateNumber = if (isSelectedInVisibleMonth && selectedDayItem != null) {
                "${selectedDayItem.dayOfMonth}"
            } else if (currentVisibleDayItem != null) {
                "${currentVisibleDayItem.dayOfMonth}"
            } else {
                ""
            }

            val headerDisplayText = if (headerDateNumber.isNotBlank()) {
                "$headerDateNumber $visibleMonthYear"
            } else {
                visibleMonthYear
            }

            // Material Date Picker Dialog launcher
            val openDatePicker = {
                val cal = Calendar.getInstance(Locale.US)
                try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDateIso)
                    if (parsed != null) cal.time = parsed
                } catch (_: Exception) {}

                val dialog = android.app.DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val newCal = Calendar.getInstance(Locale.US)
                        newCal.set(year, month, dayOfMonth)
                        val newIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(newCal.time)
                        selectedDateIso = newIso
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                dialog.show()
            }

            // Header row with Clickable Date + Month + Year and Sessions count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { openDatePicker() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("attendance_date_header_btn")
                ) {
                    Text(
                        text = headerDisplayText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick Date from Calendar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$dynamicMonthlySessionsCount Sessions",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("attendance_monthly_sessions_count")
                    )
                }
            }

            // Endless Horizontal Scrolling Calendar Strip (Both Left and Right)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val itemWidth = (maxWidth / 7).coerceAtLeast(46.dp)

                LazyRow(
                    state = calendarStripState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("attendance_calendar_strip_lazy_row"),
                    horizontalArrangement = Arrangement.Start
                ) {
                    items(
                        items = rollingDays,
                        key = { it.isoDate }
                    ) { dayItem ->
                        val isSelected = dayItem.isoDate == selectedDateIso
                        val hasRecords = historyRecords.any { it.date == dayItem.isoDate }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(itemWidth)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedDateIso = dayItem.isoDate }
                                .padding(vertical = 4.dp)
                                .testTag("attendance_day_${dayItem.isoDate}")
                        ) {
                            Text(
                                text = dayItem.dayName,
                                fontSize = 13.sp,
                                color = if (dayItem.isSunday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (dayItem.isSunday) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (dayItem.isSunday) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${dayItem.dayOfMonth}",
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White
                                    else if (dayItem.isSunday) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            // Status dot or "OFF" label for Sunday
                            if (dayItem.isSunday && !hasRecords) {
                                Text(
                                    text = "OFF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasRecords || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-header Bar: "TODAY" with Chevron and Present/Total count, rounded top corners
            Surface(
                color = if (isSelectedDateSunday) Color(0xFF1E3A8A) else MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val dateLabel = if (selectedDateIso == DateUtils.getTodayIso()) "TODAY" else DateUtils.formatIsoToShortDisplay(selectedDateIso).uppercase()
                    val headerTitle = if (isSelectedDateSunday) "$dateLabel • SUNDAY OFF" else dateLabel

                    Text(
                        text = headerTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )

                    Text(
                        text = "$uniquePresentStudentsToday / $totalActiveRegisteredStudents",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("attendance_today_count")
                    )
                }
            }

            // Session / Class Cards List with Pull-to-Refresh Gesture
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    attendanceViewModel.refreshAttendanceData { success, msg ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (success) "✓ $msg" else "⚠️ $msg",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("attendance_pull_to_refresh")
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sunday Holiday Banner
                    if (isSelectedDateSunday) {
                        item(key = "sunday_holiday_banner") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sunday — Weekly Holiday",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "No regular attendance scheduled today. Tap any batch below if conducting an extra/revision class.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Regular Student Classes (Only batches with added students)
                    items(activeClassesWithStudents, key = { it.classId }) { cls ->
                        val studentCount = classStudentCounts[cls.classId] ?: 0
                        val isDone = doneClassIds.contains(cls.classId) || doneClassIds.contains(cls.className)
                        val currentTime = classTimings[cls.classId] ?: defaultAttendanceTime
                        val classStudentIds = remember(students, cls.classId, cls.className) {
                            students.filter {
                                it.active && (it.classId.equals(cls.classId, ignoreCase = true) || it.classId.equals(cls.className, ignoreCase = true))
                            }.map { it.studentId }.toSet()
                        }
                        val presentCount = remember(historyRecords, selectedDateIso, classStudentIds) {
                            historyRecords.count { record ->
                                record.date == selectedDateIso &&
                                record.status == AttendanceStatus.PRESENT &&
                                record.studentId in classStudentIds
                            }
                        }

                        // Daily class photo for selected date with direct document SnapshotListener
                        var classPhoto by remember(cls.classId, selectedDateIso) {
                            mutableStateOf(ClassPhotoManager.getClassPhoto(context, cls.classId, selectedDateIso))
                        }
                        DisposableEffect(cls.classId, selectedDateIso) {
                            val registration = ClassPhotoManager.listenToClassPhoto(context, cls.classId, selectedDateIso) { updatedPhoto ->
                                classPhoto = updatedPhoto
                            }
                            onDispose {
                                registration?.remove()
                            }
                        }

                        val rawTeacher = cls.primaryTeacherName.ifBlank { cls.roomOrLocation }
                        val safeTeacher = rawTeacher.trim()

                        ClassSessionCard(
                            time = currentTime,
                            className = cls.className.uppercase(),
                            teacherName = safeTeacher,
                            studentCount = studentCount,
                            isDone = isDone,
                            isSunday = isSelectedDateSunday,
                            presentCount = presentCount,
                            photoBase64 = classPhoto,
                            onPhotoClick = {
                                val currentPhoto = classPhoto
                                if (!currentPhoto.isNullOrBlank()) {
                                    fullScreenPhotoTarget = Triple(
                                        currentPhoto,
                                        cls.className.uppercase(),
                                        DateUtils.formatIsoToDisplay(selectedDateIso)
                                    )
                                } else if (isSelectedDateToday) {
                                    photoUploadTarget = Pair(cls.classId, cls.className)
                                }
                            },
                            onTimeChange = { newTime ->
                                ClassTimingManager.setTiming(context, cls.classId, newTime)
                            },
                            onClick = {
                                onNavigateToTakeAttendance(cls.classId, cls.className, selectedDateIso)
                            }
                        )
                    }

                    // Educators / Staff Class Card
                    item(key = "card_educators") {
                        val isEducatorsDone = doneClassIds.contains("CLASS_EDUCATORS") || doneClassIds.any { it.contains("EDUCATOR", ignoreCase = true) }
                        val eduTime = classTimings["CLASS_EDUCATORS"] ?: "08:30 AM"
                        val eduPresentCount = historyRecords.count { record ->
                            record.date == selectedDateIso &&
                            record.status == AttendanceStatus.PRESENT &&
                            (record.classId.equals("CLASS_EDUCATORS", ignoreCase = true) || record.classId.contains("EDUCATOR", ignoreCase = true))
                        }

                        // Daily photo for educators with direct document SnapshotListener
                        var eduPhoto by remember(selectedDateIso) {
                            mutableStateOf(ClassPhotoManager.getClassPhoto(context, "CLASS_EDUCATORS", selectedDateIso))
                        }
                        DisposableEffect(selectedDateIso) {
                            val registration = ClassPhotoManager.listenToClassPhoto(context, "CLASS_EDUCATORS", selectedDateIso) { updatedPhoto ->
                                eduPhoto = updatedPhoto
                            }
                            onDispose {
                                registration?.remove()
                            }
                        }

                        ClassSessionCard(
                            time = eduTime,
                            className = "Educators",
                            teacherName = "",
                            studentCount = educators.size,
                            isDone = isEducatorsDone,
                            isSunday = isSelectedDateSunday,
                            presentCount = eduPresentCount,
                            photoBase64 = eduPhoto,
                            onPhotoClick = {
                                val currentPhoto = eduPhoto
                                if (!currentPhoto.isNullOrBlank()) {
                                    fullScreenPhotoTarget = Triple(
                                        currentPhoto,
                                        "Educators",
                                        DateUtils.formatIsoToDisplay(selectedDateIso)
                                    )
                                } else if (isSelectedDateToday) {
                                    photoUploadTarget = Pair("CLASS_EDUCATORS", "Educators")
                                }
                            },
                            onTimeChange = { newTime ->
                                ClassTimingManager.setTiming(context, "CLASS_EDUCATORS", newTime)
                            },
                            onClick = {
                                onNavigateToTakeAttendance("CLASS_EDUCATORS", "Educators", selectedDateIso)
                            }
                        )
                    }
                }
            }
        }
    }

    // Class Photo Upload Dialog for Today's date (Clicking photo thumbnail on card opens this form)
    photoUploadTarget?.let { (classId, className) ->
        ClassPhotoUploadDialog(
            classId = classId,
            className = className,
            selectedDateIso = selectedDateIso,
            onDismiss = { photoUploadTarget = null }
        )
    }

    // Full Screen Photo Viewer Dialog for Past Dates (View-only)
    fullScreenPhotoTarget?.let { (photoBase64, title, subtitle) ->
        FullScreenImageViewerDialog(
            photoModel = photoBase64,
            title = title,
            subtitle = subtitle,
            onDismiss = { fullScreenPhotoTarget = null }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("How to Take Attendance", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "1. Tap on any Class or Educators card to open its attendance list.\n" +
                    "2. Tap the Time on the left to change its scheduled time.\n" +
                    "3. Tap 'P' (Green) or 'A' (Red) next to each person.\n" +
                    "4. Tap photo to zoom in for face verification.\n" +
                    "5. Tap 'Save' at the top right to record today's attendance."
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("Got it")
                }
            }
        )
    }

    if (showShareAppDialog) {
        ShareAppDialog(onDismiss = { showShareAppDialog = false })
    }
}

@Composable
fun ClassSessionCard(
    time: String,
    className: String,
    teacherName: String = "",
    studentCount: Int,
    isDone: Boolean,
    isSunday: Boolean = false,
    presentCount: Int = 0,
    photoBase64: String? = null,
    onPhotoClick: () -> Unit = {},
    onTimeChange: (String) -> Unit,
    onClick: () -> Unit
) {
    var showTimeMenu by remember { mutableStateOf(false) }
    val availableTimes = listOf(
        "08:00 AM", "08:30 AM", "09:00 AM", "09:30 AM",
        "10:00 AM", "10:30 AM", "11:00 AM", "11:30 AM",
        "12:00 PM", "12:30 PM", "01:00 PM", "02:00 PM",
        "03:00 PM", "04:00 PM", "05:00 PM", "05:30 PM"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class_session_card_${className}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left side: Dedicated SQUARE Card/Box for Photo (where time used to be, outside the main card)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .size(54.dp)
                .clickable { onPhotoClick() }
                .testTag("class_card_photo_${className}")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val imageModel = remember(photoBase64) {
                    ImageUtils.getImageModel(photoBase64)
                }
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Class Photo for $className",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Add Class Photo",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 2. Right side: Main Section Card (keeps its original size & shape)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left/Center inside Card: Row 1 = Class Name + Time Badge; Row 2 = Teacher Name (single row)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = className,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Attendance Time Badge next to Class Name
                        Box {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .clickable { showTimeMenu = true }
                                    .testTag("time_picker_${className}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = time,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showTimeMenu,
                                onDismissRequest = { showTimeMenu = false }
                            ) {
                                availableTimes.forEach { slot ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = slot,
                                                fontWeight = if (slot == time) FontWeight.Bold else FontWeight.Normal,
                                                color = if (slot == time) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onTimeChange(slot)
                                            showTimeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Teacher Name in single clean row
                    if (teacherName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = teacherName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right side: Attendance Status & Total Count in 2 rows each (icon on top, count below) close to each other
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Column: Green checkmark if done, or Sunday Off tag if Sunday and not done
                    if (isDone) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = PresentGreen,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "$presentCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PresentGreen,
                                maxLines = 1
                            )
                        }
                    } else if (isSunday) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Right Column (Far Right): User icon (top) + Total Student Count (bottom)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Students",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "$studentCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
