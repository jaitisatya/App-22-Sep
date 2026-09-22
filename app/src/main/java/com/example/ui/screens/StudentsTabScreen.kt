package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role
import com.example.ui.components.CsvImportDialog
import com.example.ui.components.ExportStudentsDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.StudentAttendanceHistoryDialog
import com.example.ui.components.StudentFormDialog
import com.example.ui.theme.SkyBlueDark
import com.example.ui.theme.SkyBluePrimary
import com.example.util.BatchConstants
import com.example.util.DateUtils
import com.example.util.ImageUtils
import com.example.viewmodel.ClassViewModel
import com.example.viewmodel.StudentViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import com.example.R
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

data class DayMeta(
    val isoDate: String,
    val dayLabel: String,
    val isSunday: Boolean,
    val isToday: Boolean
)

data class DayStatusUI(
    val text: String,
    val bgColor: Color,
    val textColor: Color,
    val borderColor: Color
)

private val STATUS_SUNDAY = DayStatusUI("S", Color(0xFFFEF3C7), Color(0xFFB45309), Color(0xFFFDE68A))
private val STATUS_PRESENT = DayStatusUI("P", Color(0xFFDCFCE7), Color(0xFF15803D), Color(0xFF86EFAC))
private val STATUS_ABSENT = DayStatusUI("A", Color(0xFFFEE2E2), Color(0xFFDC2626), Color(0xFFFCA5A5))
private val STATUS_TODAY_EMPTY = DayStatusUI("", Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFFCBD5E1))
private val STATUS_DASH = DayStatusUI("-", Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFFE2E8F0))

enum class GenderFilter(val label: String) {
    ALL("All"),
    BOYS("Only Boys"),
    GIRLS("Only Girls")
}

enum class SchoolStatusFilter(val label: String) {
    ALL("All"),
    SCHOOL_GOING("School Going"),
    NON_SCHOOL_GOING("Non-School Going")
}

enum class StudentSortOption(val label: String, val description: String) {
    ALPHABETICAL("Name", "Alphabetical (A to Z)"),
    ATTENDANCE_HIGH_TO_LOW("Attendance High to low", "Sabse regular bachhe upar"),
    ATTENDANCE_LOW_TO_HIGH("Attendance Low to high", "Kam attendance wale pehle"),
    AGE_YOUNGEST_TO_OLDEST("Age: Youngest to Oldest", "Chhote bachhe pehle (DOB basis)"),
    CLASS_WISE("Class in School", "Nursery to Class 12 sequence"),
    NON_SCHOOL_FIRST("Non-School Going First", "Prioritize out-of-school children")
}

fun isSchoolGoingStudent(student: StudentEntity): Boolean {
    val clean = student.schoolClass.trim()
    return clean.isNotBlank() &&
            !clean.equals("Not Enrolled / None", ignoreCase = true) &&
            !clean.equals("Not Enrolled", ignoreCase = true) &&
            !clean.equals("None", ignoreCase = true)
}

fun getSchoolClassRank(schoolClass: String): Int {
    val clean = schoolClass.trim()
    val idx = BatchConstants.STANDARD_SCHOOL_CLASS_OPTIONS.indexOfFirst { it.equals(clean, ignoreCase = true) }
    return when {
        idx >= 0 -> idx
        clean.isBlank() || clean.contains("Not Enrolled", ignoreCase = true) || clean.contains("None", ignoreCase = true) -> 999
        else -> 500
    }
}

fun parseDobToEpoch(dob: String): Long {
    if (dob.isBlank()) return Long.MIN_VALUE
    val clean = dob.trim()
    val formats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.isLenient = false
            val parsed = sdf.parse(clean)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return Long.MIN_VALUE
}

data class StudentAttendanceStat(
    val monthPresent: Int,
    val monthTotal: Int,
    val overallPresent: Int,
    val overallTotal: Int,
    val records: List<AttendanceRecordEntity>,
    val last7DaysStatus: List<DayStatusUI> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsTabScreen(
    studentViewModel: StudentViewModel,
    classViewModel: ClassViewModel,
    currentUser: UserEntity? = null,
    initialClassFilter: String? = null,
    onBack: (() -> Unit)? = null,
    onViewStudentHistory: (studentId: String) -> Unit
) {
    val context = LocalContext.current
    val isAdmin = currentUser?.isMasterAdmin == true || currentUser?.role == Role.ADMIN
    val students by studentViewModel.allStudents.collectAsState()
    val classes by classViewModel.allClasses.collectAsState()
    val allAttendanceRecords by studentViewModel.allAttendanceRecords.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStudentForInfo by remember { mutableStateOf<StudentEntity?>(null) }
    var studentForFullScreenPhoto by remember { mutableStateOf<StudentEntity?>(null) }
    var studentForAttendanceHistory by remember { mutableStateOf<Pair<StudentEntity, List<AttendanceRecordEntity>>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Scroll state and track last edited/viewed student to restore scroll position
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var targetScrollStudentId by remember { mutableStateOf<String?>(null) }
    var highlightedStudentId by remember { mutableStateOf<String?>(null) }

    val classMap = remember(classes) { classes.associateBy { it.classId } }

    val currentMonthShort = remember { DateUtils.getCurrentMonthShortName() }
    val firstDayOfMonthIso = remember { DateUtils.getFirstDayOfCurrentMonthIso() }
    val todayIso = remember { DateUtils.getTodayIso() }

    // Precompute last 8 days metadata (oldest 7 days ago -> today as 8th slot)
    val last8DaysMeta = remember(todayIso) {
        (7 downTo 0).map { daysAgo ->
            val iso = DateUtils.getPastDaysIso(daysAgo)
            val dayLetter = try {
                val cal = Calendar.getInstance()
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
                if (parsed != null) cal.time = parsed
                when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "M"
                    Calendar.TUESDAY -> "T"
                    Calendar.WEDNESDAY -> "W"
                    Calendar.THURSDAY -> "T"
                    Calendar.FRIDAY -> "F"
                    Calendar.SATURDAY -> "S"
                    Calendar.SUNDAY -> "S"
                    else -> "—"
                }
            } catch (_: Exception) {
                "—"
            }
            val isSunday = DateUtils.isSunday(iso)
            val isToday = (daysAgo == 0)
            DayMeta(isoDate = iso, dayLabel = dayLetter, isSunday = isSunday, isToday = isToday)
        }
    }

    val default8DaysStatus = remember(last8DaysMeta) {
        last8DaysMeta.map { dayMeta ->
            when {
                dayMeta.isSunday -> STATUS_SUNDAY
                dayMeta.isToday -> STATUS_TODAY_EMPTY
                else -> STATUS_DASH
            }
        }
    }

    // Pre-calculate attendance statistics per student for O(1) row rendering
    val studentStatsMap = remember(allAttendanceRecords, students, firstDayOfMonthIso, todayIso, last8DaysMeta) {
        val map = mutableMapOf<String, StudentAttendanceStat>()
        if (students.isEmpty()) return@remember map

        // Pre-build lookup index by studentId and studentName for rapid reconciliation
        val studentById = students.associateBy { it.studentId }
        val studentByName = students.associateBy { it.studentName.trim().lowercase() }

        // Group attendance records under canonical student.studentId
        val recordsByCanonicalStudentId = mutableMapOf<String, MutableList<AttendanceRecordEntity>>()
        for (rec in allAttendanceRecords) {
            val matchedStudent = studentById[rec.studentId]
                ?: studentByName[rec.studentName.trim().lowercase()]
            if (matchedStudent != null) {
                recordsByCanonicalStudentId.getOrPut(matchedStudent.studentId) { mutableListOf() }.add(rec)
            }
        }

        students.forEach { student ->
            val list = recordsByCanonicalStudentId[student.studentId] ?: emptyList()
            val monthP = list.count { it.date >= firstDayOfMonthIso && it.date <= todayIso && it.status == AttendanceStatus.PRESENT }
            val monthT = list.count { it.date >= firstDayOfMonthIso && it.date <= todayIso }
            val overallP = list.count { it.status == AttendanceStatus.PRESENT }
            val overallT = list.size

            // Prefer PRESENT if multiple records exist for the same day
            val recByDate = mutableMapOf<String, AttendanceStatus>()
            list.forEach { rec ->
                val existing = recByDate[rec.date]
                if (existing == null || rec.status == AttendanceStatus.PRESENT) {
                    recByDate[rec.date] = rec.status
                }
            }

            val dayStatuses = last8DaysMeta.map { dayMeta ->
                val status = recByDate[dayMeta.isoDate]
                when {
                    dayMeta.isSunday -> STATUS_SUNDAY
                    status == AttendanceStatus.PRESENT -> STATUS_PRESENT
                    status == AttendanceStatus.ABSENT -> STATUS_ABSENT
                    dayMeta.isToday -> STATUS_TODAY_EMPTY
                    else -> STATUS_DASH
                }
            }
            val stat = StudentAttendanceStat(
                monthPresent = monthP,
                monthTotal = monthT,
                overallPresent = overallP,
                overallTotal = overallT,
                records = list,
                last7DaysStatus = dayStatuses
            )
            map[student.studentId] = stat
            map[student.studentName.trim().lowercase()] = stat
        }
        map
    }

    fun makeCall(student: StudentEntity) {
        val sanitized = student.phoneNumber.filter { it.isDigit() || it == '+' }
        if (sanitized.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$sanitized")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No phone number added for ${student.studentName}", Toast.LENGTH_SHORT).show()
        }
    }

    // Filter & Sort States
    var selectedGenderFilter by remember { mutableStateOf(GenderFilter.ALL) }
    var selectedSchoolStatusFilter by remember { mutableStateOf(SchoolStatusFilter.ALL) }
    var selectedSchoolClassFilter by remember { mutableStateOf<String?>(null) }
    var selectedSortOption by remember { mutableStateOf(StudentSortOption.ALPHABETICAL) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showClassFilterMenu by remember { mutableStateOf(false) }

    fun resetAllFilters() {
        searchQuery = ""
        selectedGenderFilter = GenderFilter.ALL
        selectedSchoolStatusFilter = SchoolStatusFilter.ALL
        selectedSchoolClassFilter = null
        selectedSortOption = StudentSortOption.ALPHABETICAL
    }

    val isAnyFilterActive = selectedGenderFilter != GenderFilter.ALL ||
            selectedSchoolStatusFilter != SchoolStatusFilter.ALL ||
            selectedSchoolClassFilter != null ||
            selectedSortOption != StudentSortOption.ALPHABETICAL ||
            searchQuery.isNotBlank()

    // Filtered and Sorted students list
    val filteredStudents: List<StudentEntity> = remember(
        students,
        searchQuery,
        selectedGenderFilter,
        selectedSchoolStatusFilter,
        selectedSchoolClassFilter,
        selectedSortOption,
        studentStatsMap,
        initialClassFilter
    ) {
        // 0. Base filter: If opened for a specific batch/class, show only that class's students
        var list = if (!initialClassFilter.isNullOrBlank()) {
            students.filter {
                it.classId.equals(initialClassFilter, ignoreCase = true) ||
                it.classId.replace("CLASS_", "", ignoreCase = true).equals(initialClassFilter.replace("CLASS_", "", ignoreCase = true), ignoreCase = true)
            }
        } else {
            students
        }

        // 1. Text Search Filter
        list = if (searchQuery.isBlank()) {
            list
        } else {
            list.filter {
                it.studentName.contains(searchQuery, ignoreCase = true) ||
                it.fatherName.contains(searchQuery, ignoreCase = true) ||
                it.motherName.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                it.schoolClass.contains(searchQuery, ignoreCase = true)
            }
        }

        // 2. Gender Filter (Boys / Girls)
        list = when (selectedGenderFilter) {
            GenderFilter.ALL -> list
            GenderFilter.BOYS -> list.filter {
                it.gender.equals("Male", ignoreCase = true) || it.gender.equals("M", ignoreCase = true)
            }
            GenderFilter.GIRLS -> list.filter {
                it.gender.equals("Female", ignoreCase = true) || it.gender.equals("F", ignoreCase = true)
            }
        }

        // 3. School Status Filter (School Going vs Non-School Going based on Class in School)
        list = when (selectedSchoolStatusFilter) {
            SchoolStatusFilter.ALL -> list
            SchoolStatusFilter.SCHOOL_GOING -> list.filter { isSchoolGoingStudent(it) }
            SchoolStatusFilter.NON_SCHOOL_GOING -> list.filter { !isSchoolGoingStudent(it) }
        }

        // 4. Specific School Class Filter (Nursery, LKG, Class 1 ... Class 12)
        if (selectedSchoolClassFilter != null) {
            list = if (selectedSchoolClassFilter == "Not Enrolled / None") {
                list.filter { !isSchoolGoingStudent(it) }
            } else {
                list.filter { it.schoolClass.trim().equals(selectedSchoolClassFilter, ignoreCase = true) }
            }
        }

        // 5. Sorting Options (Alphabetical, Attendance High to Low, Age Youngest to Oldest, Class-wise, Non-School First)
        when (selectedSortOption) {
            StudentSortOption.ALPHABETICAL -> {
                list.sortedBy { it.studentName.trim().lowercase() }
            }
            StudentSortOption.CLASS_WISE -> {
                list.sortedWith(
                    compareBy<StudentEntity> { getSchoolClassRank(it.schoolClass) }
                        .thenBy { it.studentName.trim().lowercase() }
                )
            }
            StudentSortOption.ATTENDANCE_HIGH_TO_LOW -> {
                list.sortedWith(
                    compareByDescending<StudentEntity> { student ->
                        val stats = studentStatsMap[student.studentId]
                        val pres = stats?.overallPresent ?: 0
                        val tot = stats?.overallTotal ?: 0
                        if (tot > 0) (pres.toDouble() / tot) else 0.0
                    }.thenByDescending { student ->
                        studentStatsMap[student.studentId]?.overallPresent ?: 0
                    }.thenBy { it.studentName.trim().lowercase() }
                )
            }
            StudentSortOption.ATTENDANCE_LOW_TO_HIGH -> {
                list.sortedWith(
                    compareBy<StudentEntity> { student ->
                        val stats = studentStatsMap[student.studentId]
                        val pres = stats?.overallPresent ?: 0
                        val tot = stats?.overallTotal ?: 0
                        if (tot > 0) (pres.toDouble() / tot) else 0.0
                    }.thenBy { student ->
                        studentStatsMap[student.studentId]?.overallPresent ?: 0
                    }.thenBy { it.studentName.trim().lowercase() }
                )
            }
            StudentSortOption.AGE_YOUNGEST_TO_OLDEST -> {
                list.sortedWith(
                    compareByDescending<StudentEntity> { parseDobToEpoch(it.dob) }
                        .thenBy { it.studentName.trim().lowercase() }
                )
            }
            StudentSortOption.NON_SCHOOL_FIRST -> {
                list.sortedWith(
                    compareBy<StudentEntity> { if (!isSchoolGoingStudent(it)) 0 else 1 }
                        .thenBy { it.studentName.trim().lowercase() }
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("students_class_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    if (initialClassFilter != null) {
                        Column {
                            Text(
                                text = "Students • $initialClassFilter",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${filteredStudents.size} Students Enrolled",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.jaiti_logo),
                                    contentDescription = "Jaiti Foundation Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Jaiti Foundation",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        text = "Educate, Agitate, Organize",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569),
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = { showCsvImportDialog = true },
                            modifier = Modifier.testTag("import_csv_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Import CSV",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.testTag("export_students_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export Students Data",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF00838F)
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
            // Control & Filter Bar (matching Demo.jpg layout)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Date Box (matching Demo.jpg)
                    val todayDateStr = remember {
                        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    }
                    Box(
                        modifier = Modifier
                            .border(1.5.dp, Color(0xFF00838F), RoundedCornerShape(4.dp))
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = todayDateStr,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    // 2. Filters Dropdown (replacing Name with up/down arrows)
                    var showFilterDropdown by remember { mutableStateOf(false) }
                    val currentFilterLabel = when {
                        selectedGenderFilter == GenderFilter.BOYS -> "Only Boys"
                        selectedGenderFilter == GenderFilter.GIRLS -> "Only Girls"
                        selectedSchoolStatusFilter == SchoolStatusFilter.SCHOOL_GOING -> "School Going"
                        selectedSchoolStatusFilter == SchoolStatusFilter.NON_SCHOOL_GOING -> "Non-School going"
                        selectedSortOption == StudentSortOption.ATTENDANCE_HIGH_TO_LOW -> "Attendance High to low"
                        selectedSortOption == StudentSortOption.ATTENDANCE_LOW_TO_HIGH -> "Attendance Low to high"
                        else -> "Name"
                    }

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, Color(0xFF00838F), RoundedCornerShape(4.dp))
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .clickable { showFilterDropdown = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = Color(0xFF00838F),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentFilterLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Filter",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterDropdown,
                            onDismissRequest = { showFilterDropdown = false }
                        ) {
                            // 1. Name (Alphabetical - Default)
                            DropdownMenuItem(
                                text = { Text("Name (Alphabetical order)", fontWeight = if (selectedSortOption == StudentSortOption.ALPHABETICAL && selectedGenderFilter == GenderFilter.ALL && selectedSchoolStatusFilter == SchoolStatusFilter.ALL) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = Color(0xFF00838F)) },
                                onClick = {
                                    selectedSortOption = StudentSortOption.ALPHABETICAL
                                    selectedGenderFilter = GenderFilter.ALL
                                    selectedSchoolStatusFilter = SchoolStatusFilter.ALL
                                    showFilterDropdown = false
                                }
                            )
                            // 2. Attendance High to low
                            DropdownMenuItem(
                                text = { Text("Attendance High to low", fontWeight = if (selectedSortOption == StudentSortOption.ATTENDANCE_HIGH_TO_LOW) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF15803D)) },
                                onClick = {
                                    selectedSortOption = StudentSortOption.ATTENDANCE_HIGH_TO_LOW
                                    showFilterDropdown = false
                                }
                            )
                            // 3. Attendance Low to high
                            DropdownMenuItem(
                                text = { Text("Attendance Low to high", fontWeight = if (selectedSortOption == StudentSortOption.ATTENDANCE_LOW_TO_HIGH) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFFD97706)) },
                                onClick = {
                                    selectedSortOption = StudentSortOption.ATTENDANCE_LOW_TO_HIGH
                                    showFilterDropdown = false
                                }
                            )
                            HorizontalDivider()
                            // 4. School Going
                            DropdownMenuItem(
                                text = { Text("School Going", fontWeight = if (selectedSchoolStatusFilter == SchoolStatusFilter.SCHOOL_GOING) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF1D4ED8)) },
                                onClick = {
                                    selectedSchoolStatusFilter = SchoolStatusFilter.SCHOOL_GOING
                                    showFilterDropdown = false
                                }
                            )
                            // 5. Non-School going
                            DropdownMenuItem(
                                text = { Text("Non-School going", fontWeight = if (selectedSchoolStatusFilter == SchoolStatusFilter.NON_SCHOOL_GOING) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFDC2626)) },
                                onClick = {
                                    selectedSchoolStatusFilter = SchoolStatusFilter.NON_SCHOOL_GOING
                                    showFilterDropdown = false
                                }
                            )
                            HorizontalDivider()
                            // 6. Only Boys
                            DropdownMenuItem(
                                text = { Text("Only Boys", fontWeight = if (selectedGenderFilter == GenderFilter.BOYS) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.Male, contentDescription = null, tint = Color(0xFF0284C7)) },
                                onClick = {
                                    selectedGenderFilter = GenderFilter.BOYS
                                    showFilterDropdown = false
                                }
                            )
                            // 7. Only Girls
                            DropdownMenuItem(
                                text = { Text("Only Girls", fontWeight = if (selectedGenderFilter == GenderFilter.GIRLS) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(Icons.Default.Female, contentDescription = null, tint = Color(0xFFDB2777)) },
                                onClick = {
                                    selectedGenderFilter = GenderFilter.GIRLS
                                    showFilterDropdown = false
                                }
                            )
                        }
                    }

                    // 3. Total Student Count Box (between Name and + button - only number shown)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 36.dp)
                            .border(1.5.dp, Color(0xFF00838F), RoundedCornerShape(4.dp))
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${filteredStudents.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00838F)
                        )
                    }

                    // 4. Add Student Button: Green Box with '+' Icon (matching Demo.jpg)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E7D32))
                            .clickable { showAddDialog = true }
                            .testTag("add_student_green_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Student",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Clean Custom Single-Line Search Bar (perfectly vertically centered, no clipped text)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search Students...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            maxLines = 1,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("students_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Reset Filters row if filter active (STUDENTS text removed)
            if (isAnyFilterActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset Filters",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.clickable { resetAllFilters() }
                    )
                }
            }

            // Empty State
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isAnyFilterActive) "No Students Match the Criteria" else "No Students Added Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isAnyFilterActive)
                                "Try clearing some filters or searching for another student."
                            else
                                "Tap the + button above to add your students.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isAnyFilterActive) {
                            Button(
                                onClick = { resetAllFilters() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Filters", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Student", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Adaptive layout based on selectedSortOption:
                // 1. Alphabetical: A-Z section headers
                // 2. Class-wise: School class section headers
                // 3. Non-School First: Non-School vs School headers
                // 4. Attendance / Age: Direct ranked listing
                val groupedStudents: Map<String, List<StudentEntity>>? = remember(filteredStudents, selectedSortOption) {
                    when (selectedSortOption) {
                        StudentSortOption.ALPHABETICAL -> {
                            filteredStudents.groupBy { student ->
                                (student.studentName.trim().firstOrNull()?.uppercaseChar() ?: 'A').toString()
                            }
                        }
                        StudentSortOption.CLASS_WISE -> {
                            filteredStudents.groupBy { student ->
                                if (isSchoolGoingStudent(student)) student.schoolClass.trim() else "Not Enrolled / Non-School"
                            }
                        }
                        StudentSortOption.NON_SCHOOL_FIRST -> {
                            filteredStudents.groupBy { student ->
                                if (!isSchoolGoingStudent(student)) "Non-School Going Children" else "School Going Children"
                            }
                        }
                        else -> null // Flat list for Attendance High to Low and Age Youngest to Oldest
                    }
                }

                // Calculate scroll index for targeted student if needed
                LaunchedEffect(targetScrollStudentId, groupedStudents, filteredStudents) {
                    val targetId = targetScrollStudentId
                    if (targetId != null) {
                        var foundIndex = -1
                        if (groupedStudents != null) {
                            var currentIndex = 0
                            for ((_, studentList) in groupedStudents) {
                                val itemIdx = studentList.indexOfFirst { it.studentId == targetId }
                                if (itemIdx != -1) {
                                    // Header is at currentIndex, student item is at currentIndex + 1 + itemIdx
                                    foundIndex = currentIndex + 1 + itemIdx
                                    break
                                }
                                // 1 header item + size of studentList
                                currentIndex += 1 + studentList.size
                            }
                        } else {
                            foundIndex = filteredStudents.indexOfFirst { it.studentId == targetId }
                        }
                        if (foundIndex != -1) {
                            try {
                                listState.animateScrollToItem(index = foundIndex, scrollOffset = -20)
                            } catch (_: Exception) {
                                try {
                                    listState.scrollToItem(foundIndex)
                                } catch (_: Exception) {}
                            }
                        }
                        targetScrollStudentId = null
                        // Clear highlight after a short delay
                        kotlinx.coroutines.delay(2500)
                        highlightedStudentId = null
                    }
                }

                // Reset scroll to top when sort option or filter changes
                LaunchedEffect(selectedSortOption, selectedGenderFilter, selectedSchoolStatusFilter, selectedSchoolClassFilter) {
                    try {
                        listState.scrollToItem(0)
                    } catch (_: Exception) {}
                }

                // Mini 7-day strip legend bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 3.5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Students (${filteredStudents.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "8 Days:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(13.5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color(0xFFDCFCE7)).border(0.6.dp, Color(0xFF86EFAC), RoundedCornerShape(2.5.dp)), contentAlignment = Alignment.Center) {
                                Text(
                                    "P",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            Text("Present", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(modifier = Modifier.size(13.5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color(0xFFFEE2E2)).border(0.6.dp, Color(0xFFFCA5A5), RoundedCornerShape(2.5.dp)), contentAlignment = Alignment.Center) {
                                Text(
                                    "A",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            Text("Absent", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(modifier = Modifier.size(13.5.dp).clip(RoundedCornerShape(2.5.dp)).background(Color(0xFFFEF3C7)).border(0.6.dp, Color(0xFFFDE68A), RoundedCornerShape(2.5.dp)), contentAlignment = Alignment.Center) {
                                Text(
                                    "S",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            Text("Sun", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (groupedStudents != null) {
                        groupedStudents.forEach { (sectionHeader, studentList) ->
                            // Header Band
                            item(key = "header_$sectionHeader", contentType = "section_header") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sectionHeader,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${studentList.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            items(
                                items = studentList,
                                key = { it.studentId },
                                contentType = { "student_card" }
                            ) { student ->
                                val stats = studentStatsMap[student.studentId] ?: studentStatsMap[student.studentName.trim().lowercase()]
                                val monthPresent = stats?.monthPresent ?: 0
                                val overallPresent = stats?.overallPresent ?: 0
                                val studentRecords = stats?.records ?: emptyList()
                                val dayStatuses = stats?.last7DaysStatus ?: default8DaysStatus

                                StudentListItemCard1Jpg(
                                    student = student,
                                    currentMonthShort = currentMonthShort,
                                    monthPresentCount = monthPresent,
                                    overallPresentCount = overallPresent,
                                    studentRecords = studentRecords,
                                    last8DaysMeta = last8DaysMeta,
                                    last8DaysStatus = dayStatuses,
                                    isHighlighted = student.studentId == highlightedStudentId,
                                    onPhotoClick = {
                                        studentForFullScreenPhoto = student
                                    },
                                    onAttendanceClick = {
                                        studentForAttendanceHistory = Pair(student, studentRecords)
                                    },
                                    onCallClick = {
                                        makeCall(student)
                                    },
                                    onRowClick = {
                                        selectedStudentForInfo = student
                                    }
                                )
                            }
                        }
                    } else {
                        // Flat List (Attendance High to Low OR Age Youngest to Oldest)
                        items(
                            items = filteredStudents,
                            key = { it.studentId },
                            contentType = { "student_card" }
                        ) { student ->
                            val stats = studentStatsMap[student.studentId] ?: studentStatsMap[student.studentName.trim().lowercase()]
                            val monthPresent = stats?.monthPresent ?: 0
                            val overallPresent = stats?.overallPresent ?: 0
                            val studentRecords = stats?.records ?: emptyList()
                            val dayStatuses = stats?.last7DaysStatus ?: default8DaysStatus

                            StudentListItemCard1Jpg(
                                student = student,
                                currentMonthShort = currentMonthShort,
                                monthPresentCount = monthPresent,
                                overallPresentCount = overallPresent,
                                studentRecords = studentRecords,
                                last8DaysMeta = last8DaysMeta,
                                last8DaysStatus = dayStatuses,
                                isHighlighted = student.studentId == highlightedStudentId,
                                onPhotoClick = {
                                    studentForFullScreenPhoto = student
                                },
                                onAttendanceClick = {
                                    studentForAttendanceHistory = Pair(student, studentRecords)
                                },
                                onCallClick = {
                                    makeCall(student)
                                },
                                onRowClick = {
                                    selectedStudentForInfo = student
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Date-Wise Attendance History & Parent Sharing Dialog
    studentForAttendanceHistory?.let { (student, recs) ->
        val clsName = classMap[student.classId]?.className ?: ""
        StudentAttendanceHistoryDialog(
            student = student,
            className = clsName,
            allStudentRecords = recs,
            onDismiss = { studentForAttendanceHistory = null }
        )
    }

    // 2. Full Screen Photo Dialog (Zoom view on photo click)
    studentForFullScreenPhoto?.let { student ->
        val clsName = classMap[student.classId]?.className
        FullScreenPhotoDialog(
            name = student.studentName,
            photoUri = student.photoUri,
            fatherName = student.fatherName,
            motherName = student.motherName,
            className = clsName,
            phoneNumber = student.phoneNumber,
            gender = student.gender,
            onEditInfoClick = {
                studentForFullScreenPhoto = null
                selectedStudentForInfo = student
            },
            onPhotoChanged = { newPhotoUri ->
                val updatedStudent = student.copy(photoUri = newPhotoUri)
                studentViewModel.updateStudent(updatedStudent)
                studentForFullScreenPhoto = updatedStudent
            },
            onDismiss = { studentForFullScreenPhoto = null }
        )
    }

    // 3. Add Student Dialog
    if (showAddDialog) {
        StudentFormDialog(
            initialStudent = null,
            classes = classes,
            isAdmin = isAdmin,
            onDismiss = { showAddDialog = false },
            onSave = { name, father, mother, classId, phone, photo, dob, schoolClass, gender, aadharUri, birthCertUri, consentUri ->
                studentViewModel.addStudent(
                    name = name,
                    fatherName = father,
                    motherName = mother,
                    classId = classId,
                    phoneNumber = phone,
                    photoUri = photo,
                    age = 0,
                    gender = gender,
                    areaName = "",
                    schoolName = "",
                    dob = dob,
                    schoolClass = schoolClass,
                    aadharCardUri = aadharUri,
                    birthCertificateUri = birthCertUri,
                    consentFormUri = consentUri
                )
                showAddDialog = false
            }
        )
    }

    // 4. CSV Import Dialog with Duplicate Filtering
    if (showCsvImportDialog) {
        CsvImportDialog(
            existingStudents = students,
            classes = classes,
            defaultClassId = classes.firstOrNull()?.classId ?: "",
            onDismiss = { showCsvImportDialog = false },
            onImportConfirmed = { newStudents, skippedCount ->
                showCsvImportDialog = false
                studentViewModel.bulkAddStudents(newStudents) { count ->
                    val msg = if (skippedCount > 0) {
                        "Successfully added $count students ($skippedCount duplicate entries skipped)"
                    } else {
                        "Successfully added $count students"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // 5. CSV Export Dialog (Save to Downloads or Share via Apps)
    if (showExportDialog) {
        ExportStudentsDialog(
            students = filteredStudents,
            classes = classes,
            onDismiss = { showExportDialog = false }
        )
    }

    // 6. Sort Options Modal Bottom Sheet
    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort Students",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedSortOption != StudentSortOption.ALPHABETICAL) {
                        TextButton(onClick = {
                            selectedSortOption = StudentSortOption.ALPHABETICAL
                            showSortBottomSheet = false
                        }) {
                            Text("Reset to Default", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                StudentSortOption.values().forEach { option ->
                    val isSelected = selectedSortOption == option
                    Surface(
                        onClick = {
                            selectedSortOption = option
                            showSortBottomSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedSortOption = option
                                    showSortBottomSheet = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (option == StudentSortOption.ALPHABETICAL) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 7. Full Student Info / Edit Screen Overlay (renders on top of the list so LazyColumn scroll position is completely preserved)
    selectedStudentForInfo?.let { student ->
        BackHandler {
            targetScrollStudentId = student.studentId
            highlightedStudentId = student.studentId
            selectedStudentForInfo = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val studentAttendanceList = studentStatsMap[student.studentId]?.records
                ?: allAttendanceRecords.filter { it.studentId == student.studentId }
            StudentInfoScreen(
                student = student,
                classes = classes,
                attendanceRecords = studentAttendanceList,
                isAdmin = isAdmin,
                onBack = {
                    targetScrollStudentId = student.studentId
                    highlightedStudentId = student.studentId
                    selectedStudentForInfo = null
                },
                onSave = { updatedStudent ->
                    studentViewModel.updateStudent(updatedStudent)
                    targetScrollStudentId = updatedStudent.studentId
                    highlightedStudentId = updatedStudent.studentId
                    selectedStudentForInfo = null
                },
                onDelete = if (isAdmin) {
                    { studentToDelete ->
                        studentViewModel.deleteStudent(studentToDelete)
                        selectedStudentForInfo = null
                    }
                } else null
            )
        }
    }
}

/**
 * Student Card matching the Demo.jpg specification:
 * - Squircle portrait photo (54x64dp, tap to zoom full-screen)
 * - Uppercase student name
 * - JID & Father's Name (fallback NA)
 * - GEN & DOB (fallback NA)
 * - School status tag
 * - Current month attendance badge (clickable -> attendance history)
 * - Total attendance badge 'Tot' (clickable -> attendance history)
 * - Quick phone call button
 */
@Composable
fun StudentListItemCard1Jpg(
    student: StudentEntity,
    currentMonthShort: String = "Sep",
    monthPresentCount: Int,
    overallPresentCount: Int,
    studentRecords: List<AttendanceRecordEntity> = emptyList(),
    last8DaysMeta: List<DayMeta> = emptyList(),
    last8DaysStatus: List<DayStatusUI> = emptyList(),
    isHighlighted: Boolean = false,
    onPhotoClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onCallClick: () -> Unit = {},
    onRowClick: () -> Unit
) {
    val context = LocalContext.current
    val fatherStr = student.fatherName.trim()

    val imageModel = remember(student.photoUri) {
        ImageUtils.getImageModel(student.photoUri, context)
    }

    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { onRowClick() }
            .testTag("student_item_${student.studentId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Portrait Student Photo (40dp x 46dp, tap to zoom full-screen)
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(5.dp))
                    .clickable { onPhotoClick() }
                    .testTag("student_photo_${student.studentId}"),
                contentAlignment = Alignment.Center
            ) {
                if (student.photoUri.isNotBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Photo of ${student.studentName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val initialLetter = student.studentName.trim().firstOrNull()?.uppercase() ?: "S"
                    Text(
                        text = initialLetter,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00838F)
                    )
                }
            }

            // 2. Student Details: Name, Student ID (below name), Father's Name (DOB removed from card)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 7.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Row 1: Student Name
                Text(
                    text = student.studentName.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                // Row 2: Student ID (Directly below student name)
                Text(
                    text = student.studentId,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 11.sp
                )

                // Row 3: Father's Name (clean display only if not blank)
                if (fatherStr.isNotBlank()) {
                    Text(
                        text = fatherStr,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 11.sp
                    )
                }
            }

            // 3. 8 Days Rolling Attendance Strip (Oldest to Today) - Compact boxes to provide ample room for student name
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onAttendanceClick() }
            ) {
                for (i in last8DaysMeta.indices) {
                    val dayMeta = last8DaysMeta[i]
                    val dayUI = last8DaysStatus.getOrElse(i) { STATUS_DASH }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dayMeta.dayLabel,
                            fontSize = 7.sp,
                            fontWeight = if (dayMeta.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (dayMeta.isToday) Color(0xFF0284C7) else Color(0xFF94A3B8),
                            lineHeight = 8.5.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(dayUI.bgColor)
                                .border(BorderStroke(0.6.dp, dayUI.borderColor), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayUI.text,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = dayUI.textColor,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }

            // 4. Right Side: Month (Sep) & Total (Tot) Attendance Badges (Call button removed from card, available in profile)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(42.dp)
            ) {
                // Row 1: Month Attendance Badge (e.g. Sep : 14)
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFE0F2FE),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAttendanceClick() }
                        .testTag("student_month_att_${student.studentId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentMonthShort,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                        Text(
                            text = "$monthPresentCount",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }
                }

                // Row 2: Total Attendance Badge (e.g. Tot : 42)
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFF3E8FF),
                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAttendanceClick() }
                        .testTag("student_total_att_${student.studentId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tot",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                        Text(
                            text = "$overallPresentCount",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                    }
                }
            }
        }
    }
}

