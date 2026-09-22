package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.model.AttendanceStatus
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import com.example.util.CsvExporter
import com.example.util.DateUtils
import com.example.util.PdfExporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ClassAttendanceSummary(
    val classEntity: ClassEntity,
    val totalStudents: Int,
    val presentCount: Int,
    val absentCount: Int,
    val attendancePercentage: Float
)

data class DashboardSummary(
    val totalStudents: Int = 0,
    val totalClasses: Int = 0,
    val presentToday: Int = 0,
    val absentToday: Int = 0,
    val overallPercentage: Float = 0f,
    val classSummaries: List<ClassAttendanceSummary> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _selectedDateIso = MutableStateFlow(DateUtils.getTodayIso())
    val selectedDateIso: StateFlow<String> = _selectedDateIso.asStateFlow()

    private val _selectedStartDateIso = MutableStateFlow(DateUtils.getFirstDayOfCurrentMonthIso())
    val selectedStartDateIso: StateFlow<String> = _selectedStartDateIso.asStateFlow()

    private val _selectedEndDateIso = MutableStateFlow(DateUtils.getTodayIso())
    val selectedEndDateIso: StateFlow<String> = _selectedEndDateIso.asStateFlow()

    private val _selectedClassIdFilter = MutableStateFlow<String?>(null)
    val selectedClassIdFilter: StateFlow<String?> = _selectedClassIdFilter.asStateFlow()

    val allRecords: StateFlow<List<AttendanceRecordEntity>> = attendanceRepository.allAttendanceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        studentRepository.allStudents,
        classRepository.allClasses,
        _selectedDateIso.flatMapLatest { date -> attendanceRepository.getAttendanceByDate(date) }
    ) { studentList, classList, todayRecords ->
        val activeStudents = studentList.filter { it.active }
        val activeClasses = classList.filter { it.active }

        val presentToday = todayRecords.count { it.status == AttendanceStatus.PRESENT }
        val absentToday = todayRecords.count { it.status == AttendanceStatus.ABSENT }
        val totalRecorded = todayRecords.size

        val overallPercentage = if (totalRecorded > 0) {
            (presentToday.toFloat() / totalRecorded) * 100f
        } else 0f

        val classSummaries = activeClasses.map { cls ->
            val classStudents = activeStudents.filter { it.classId == cls.classId }
            val classRecords = todayRecords.filter { it.classId == cls.classId }
            val clsPresent = classRecords.count { it.status == AttendanceStatus.PRESENT }
            val clsAbsent = classRecords.count { it.status == AttendanceStatus.ABSENT }
            val clsTotalRecorded = classRecords.size
            val clsPercentage = if (clsTotalRecorded > 0) {
                (clsPresent.toFloat() / clsTotalRecorded) * 100f
            } else 0f

            ClassAttendanceSummary(
                classEntity = cls,
                totalStudents = classStudents.size,
                presentCount = clsPresent,
                absentCount = clsAbsent,
                attendancePercentage = clsPercentage
            )
        }

        DashboardSummary(
            totalStudents = activeStudents.size,
            totalClasses = activeClasses.size,
            presentToday = presentToday,
            absentToday = absentToday,
            overallPercentage = overallPercentage,
            classSummaries = classSummaries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val reportRecords: StateFlow<List<AttendanceRecordEntity>> = combine(
        _selectedStartDateIso,
        _selectedEndDateIso,
        _selectedClassIdFilter
    ) { start, end, classId ->
        Triple(start, end, classId)
    }.flatMapLatest { (start, end, classId) ->
        attendanceRepository.getAttendanceByDateRange(start, end).map { records ->
            if (classId.isNull_or_blank()) records else records.filter { it.classId == classId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(dateIso: String) {
        _selectedDateIso.value = dateIso
    }

    fun setDateRange(startDateIso: String, endDateIso: String) {
        _selectedStartDateIso.value = startDateIso
        _selectedEndDateIso.value = endDateIso
    }

    fun setClassFilter(classId: String?) {
        _selectedClassIdFilter.value = classId
    }

    fun exportReportToCsv(
        context: Context,
        startDateIso: String = _selectedStartDateIso.value,
        endDateIso: String = _selectedEndDateIso.value,
        classFilter: String? = _selectedClassIdFilter.value,
        onExportDone: (File?) -> Unit
    ) {
        viewModelScope.launch {
            val records = if (startDateIso == _selectedStartDateIso.value && endDateIso == _selectedEndDateIso.value && classFilter == _selectedClassIdFilter.value) {
                reportRecords.value
            } else {
                val fetched = attendanceRepository.getAttendanceByDateRange(startDateIso, endDateIso).first()
                if (classFilter.isNull_or_blank()) fetched else fetched.filter { it.classId == classFilter }
            }

            val csvFile = CsvExporter.generateAttendanceCsv(
                context = context,
                records = records,
                fileNamePrefix = "Jaiti_Attendance_${startDateIso}_to_${endDateIso}"
            )
            if (csvFile != null) {
                CsvExporter.shareCsvFile(context, csvFile)
            }
            onExportDone(csvFile)
        }
    }

    fun exportReportToPdf(
        context: Context,
        startDateIso: String = _selectedStartDateIso.value,
        endDateIso: String = _selectedEndDateIso.value,
        classFilter: String? = _selectedClassIdFilter.value,
        classFilterName: String = "All Classes",
        onExportDone: (File?) -> Unit
    ) {
        viewModelScope.launch {
            val records = if (startDateIso == _selectedStartDateIso.value && endDateIso == _selectedEndDateIso.value && classFilter == _selectedClassIdFilter.value) {
                reportRecords.value
            } else {
                val fetched = attendanceRepository.getAttendanceByDateRange(startDateIso, endDateIso).first()
                if (classFilter.isNull_or_blank()) fetched else fetched.filter { it.classId == classFilter }
            }

            val pdfFile = PdfExporter.generateAttendancePdf(
                context = context,
                records = records,
                startDateIso = startDateIso,
                endDateIso = endDateIso,
                classFilterName = classFilterName,
                fileNamePrefix = "Jaiti_Attendance_Report"
            )
            if (pdfFile != null) {
                PdfExporter.sharePdfFile(context, pdfFile)
            }
            onExportDone(pdfFile)
        }
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.isBlank()
}
