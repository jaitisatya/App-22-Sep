package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import com.example.util.BatchConstants
import com.example.util.StudentIdUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StudentFilterState(
    val searchQuery: String = "",
    val selectedClassId: String? = null,
    val selectedGender: String? = null,
    val showOnlyActive: Boolean = true
)

class StudentViewModel(
    private val studentRepository: StudentRepository,
    private val classRepository: ClassRepository,
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(StudentFilterState())
    val filterState: StateFlow<StudentFilterState> = _filterState.asStateFlow()

    val allStudents: StateFlow<List<StudentEntity>> = studentRepository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendanceRecords: StateFlow<List<com.example.data.entity.AttendanceRecordEntity>> = attendanceRepository.allAttendanceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getAttendanceForStudent(studentId: String): Flow<List<com.example.data.entity.AttendanceRecordEntity>> {
        return attendanceRepository.getAttendanceByStudent(studentId)
    }

    val filteredStudents: StateFlow<List<StudentEntity>> = combine(
        studentRepository.allStudents,
        _filterState
    ) { studentList, filter ->
        studentList.filter { student ->
            val matchesQuery = filter.searchQuery.isBlank() ||
                    student.studentName.contains(filter.searchQuery, ignoreCase = true) ||
                    student.studentId.contains(filter.searchQuery, ignoreCase = true) ||
                    student.fatherName.contains(filter.searchQuery, ignoreCase = true) ||
                    student.motherName.contains(filter.searchQuery, ignoreCase = true) ||
                    student.areaName.contains(filter.searchQuery, ignoreCase = true)

            val matchesClass = filter.selectedClassId == null || student.classId == filter.selectedClassId
            val matchesGender = filter.selectedGender == null || student.gender.equals(filter.selectedGender, ignoreCase = true)
            val matchesActive = !filter.showOnlyActive || student.active

            matchesQuery && matchesClass && matchesGender && matchesActive
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTeacherStudents(assignedClassIds: List<String>): Flow<List<StudentEntity>> {
        if (assignedClassIds.isEmpty()) return flowOf(emptyList())
        return combine(
            studentRepository.getStudentsByClasses(assignedClassIds),
            _filterState
        ) { studentList, filter ->
            studentList.filter { student ->
                val matchesQuery = filter.searchQuery.isBlank() ||
                        student.studentName.contains(filter.searchQuery, ignoreCase = true) ||
                        student.studentId.contains(filter.searchQuery, ignoreCase = true) ||
                        student.fatherName.contains(filter.searchQuery, ignoreCase = true) ||
                        student.motherName.contains(filter.searchQuery, ignoreCase = true)

                val matchesClass = filter.selectedClassId == null || student.classId == filter.selectedClassId
                val matchesGender = filter.selectedGender == null || student.gender.equals(filter.selectedGender, ignoreCase = true)
                val matchesActive = !filter.showOnlyActive || student.active

                matchesQuery && matchesClass && matchesGender && matchesActive
            }
        }
    }

    fun setSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun setClassFilter(classId: String?) {
        _filterState.update { it.copy(selectedClassId = classId) }
    }

    fun setGenderFilter(gender: String?) {
        _filterState.update { it.copy(selectedGender = gender) }
    }

    fun toggleShowOnlyActive(onlyActive: Boolean) {
        _filterState.update { it.copy(showOnlyActive = onlyActive) }
    }

    fun addStudent(
        name: String,
        fatherName: String = "",
        motherName: String = "",
        classId: String = "",
        phoneNumber: String = "",
        photoUri: String = "",
        age: Int = 0,
        gender: String = "Male",
        areaName: String = "",
        schoolName: String = "",
        dob: String = "",
        notes: String = "",
        schoolClass: String = "",
        aadharCardUri: String = "",
        birthCertificateUri: String = "",
        consentFormUri: String = ""
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val studentId = StudentIdUtils.getNextStudentId(allStudents.value)
            val student = StudentEntity(
                studentId = studentId,
                studentName = name.trim(),
                fatherName = fatherName.trim(),
                motherName = motherName.trim(),
                classId = classId.trim(),
                phoneNumber = phoneNumber.trim(),
                photoUri = photoUri.trim(),
                age = age,
                gender = gender,
                areaName = areaName.trim(),
                schoolName = schoolName.trim(),
                active = true,
                dob = dob.trim(),
                notes = notes.trim(),
                schoolClass = schoolClass.trim(),
                aadharCardUri = aadharCardUri.trim(),
                birthCertificateUri = birthCertificateUri.trim(),
                consentFormUri = consentFormUri.trim()
            )
            // Ensure class exists in database if classId is provided
            if (classId.isNotBlank()) {
                val existing = classRepository.getClassById(classId.trim())
                if (existing == null) {
                    val displayName = BatchConstants.formatBatchDisplayName(classId.trim())
                    classRepository.addClass(
                        ClassEntity(
                            classId = classId.trim(),
                            className = displayName,
                            active = true,
                            createdTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            studentRepository.addStudent(student)
        }
    }

    fun bulkAddStudents(students: List<StudentEntity>, onComplete: (count: Int) -> Unit = {}) {
        if (students.isEmpty()) {
            onComplete(0)
            return
        }
        viewModelScope.launch {
            // Auto-ensure classes exist
            val uniqueClassIds = students.map { it.classId.trim() }.filter { it.isNotBlank() }.distinct()
            for (cid in uniqueClassIds) {
                val existing = classRepository.getClassById(cid)
                if (existing == null) {
                    val displayName = BatchConstants.formatBatchDisplayName(cid)
                    classRepository.addClass(
                        ClassEntity(
                            classId = cid,
                            className = displayName,
                            active = true,
                            createdTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            studentRepository.addStudents(students)
            onComplete(students.size)
        }
    }

    fun updateStudent(student: StudentEntity) {
        if (student.studentName.isBlank()) return
        viewModelScope.launch {
            if (student.classId.isNotBlank()) {
                val existing = classRepository.getClassById(student.classId.trim())
                if (existing == null) {
                    val displayName = BatchConstants.formatBatchDisplayName(student.classId.trim())
                    classRepository.addClass(
                        ClassEntity(
                            classId = student.classId.trim(),
                            className = displayName,
                            active = true,
                            createdTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            studentRepository.updateStudent(student)
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            studentRepository.deleteStudent(student)
        }
    }

    fun clearAllStudents() {
        viewModelScope.launch {
            studentRepository.clearAllStudents()
        }
    }

    fun toggleStudentActive(studentId: String, currentActive: Boolean) {
        viewModelScope.launch {
            studentRepository.toggleStudentActiveState(studentId, !currentActive)
        }
    }
}
