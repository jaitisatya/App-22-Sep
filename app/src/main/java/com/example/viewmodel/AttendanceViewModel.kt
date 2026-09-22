package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.model.AttendanceStatus
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.EducatorManager
import com.example.data.repository.StudentRepository
import com.example.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AttendanceState(
    val selectedClassId: String = "",
    val selectedClassName: String = "",
    val selectedDateIso: String = DateUtils.getTodayIso(),
    val students: List<StudentEntity> = emptyList(),
    val attendanceMap: Map<String, AttendanceStatus> = emptyMap(), // studentId -> PRESENT/ABSENT
    val remarkMap: Map<String, String> = emptyMap(), // studentId -> remark
    val isAlreadyTaken: Boolean = false,
    val searchQuery: String = "",
    val isSaving: Boolean = false,
    val isRefreshing: Boolean = false,
    val saveMessage: String? = null,
    val errorMessage: String? = null,
    val lastSyncTimestamp: Long = 0L
)

class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val studentRepository: StudentRepository,
    private val classRepository: ClassRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceState())
    val uiState: StateFlow<AttendanceState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var attendanceFlowJob: kotlinx.coroutines.Job? = null

    fun selectClassAndDate(classId: String, className: String, dateIso: String = DateUtils.getTodayIso()) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedClassId = classId,
                    selectedClassName = className,
                    selectedDateIso = dateIso,
                    students = emptyList(),
                    attendanceMap = emptyMap(),
                    remarkMap = emptyMap()
                )
            }
            checkAlreadyTaken(classId, dateIso)
            loadStudentsAndExistingAttendance(classId, dateIso)
        }
    }

    private suspend fun checkAlreadyTaken(classId: String, dateIso: String) {
        val alreadyTaken = attendanceRepository.hasAttendanceBeenTaken(classId, dateIso)
        _uiState.update { it.copy(isAlreadyTaken = alreadyTaken) }
    }

    private fun loadStudentsAndExistingAttendance(classId: String, dateIso: String) {
        attendanceFlowJob?.cancel()
        attendanceFlowJob = viewModelScope.launch {
            val studentFlow: Flow<List<StudentEntity>> = if (classId == "CLASS_EDUCATORS") {
                EducatorManager.educators.map { educatorsList ->
                    educatorsList.map { edu ->
                        StudentEntity(
                            studentId = edu.id,
                            studentName = edu.name,
                            fatherName = if (edu.subject.isNotBlank()) "Subject: ${edu.subject}" else "Educator / Staff",
                            motherName = "",
                            classId = "CLASS_EDUCATORS",
                            phoneNumber = edu.phone,
                            photoUri = edu.photoUri,
                            gender = "",
                            areaName = edu.subject.ifBlank { "Staff" },
                            schoolName = "Jaiti Learning Centre",
                            active = true,
                            createdTimestamp = edu.createdTimestamp
                        )
                    }
                }
            } else {
                studentRepository.getStudentsByClass(classId)
            }

            combine(
                studentFlow,
                attendanceRepository.getAttendanceByClassAndDate(classId, dateIso)
            ) { studentList, existingRecords ->
                val activeStudents = studentList.filter { it.active }
                val existingMap = existingRecords.associateBy { it.studentId }

                _uiState.update { currentState ->
                    val scopedAttendanceMap = mutableMapOf<String, AttendanceStatus>()
                    val scopedRemarkMap = mutableMapOf<String, String>()

                    for (student in activeStudents) {
                        val record = existingMap[student.studentId]
                        if (record != null) {
                            // Live record from Room/Firestore update
                            scopedAttendanceMap[student.studentId] = record.status
                            scopedRemarkMap[student.studentId] = record.remark
                        } else if (currentState.attendanceMap.containsKey(student.studentId)) {
                            // Retain current user session edit for this student
                            scopedAttendanceMap[student.studentId] = currentState.attendanceMap[student.studentId] ?: AttendanceStatus.PRESENT
                            scopedRemarkMap[student.studentId] = currentState.remarkMap[student.studentId] ?: ""
                        } else {
                            // Default to PRESENT if unmarked
                            scopedAttendanceMap[student.studentId] = AttendanceStatus.PRESENT
                        }
                    }

                    currentState.copy(
                        students = activeStudents,
                        attendanceMap = scopedAttendanceMap,
                        remarkMap = scopedRemarkMap,
                        isAlreadyTaken = existingRecords.isNotEmpty(),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                }
            }.collect()
        }
    }

    fun setStudentStatus(
        studentId: String,
        status: AttendanceStatus,
        teacherId: String = "",
        teacherName: String = ""
    ) {
        _uiState.update { state ->
            val updatedMap = state.attendanceMap.toMutableMap()
            updatedMap[studentId] = status
            state.copy(attendanceMap = updatedMap)
        }

        // Live auto-save and push to cloud in background for real-time multi-device sync
        val state = _uiState.value
        if (state.selectedClassId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    val existingList = attendanceRepository.getAttendanceByClassAndDateDirect(state.selectedClassId, state.selectedDateIso)
                    val existing = existingList.find { it.studentId == studentId }
                    val studentName = state.students.find { it.studentId == studentId }?.studentName
                        ?: existing?.studentName
                        ?: ""
                    val record = AttendanceRecordEntity(
                        attendanceId = existing?.attendanceId ?: "ATT_${state.selectedDateIso}_${studentId}",
                        studentId = studentId,
                        studentName = studentName,
                        classId = state.selectedClassId,
                        className = state.selectedClassName,
                        date = state.selectedDateIso,
                        status = status,
                        remark = state.remarkMap[studentId] ?: existing?.remark ?: "",
                        teacherId = teacherId.ifBlank { existing?.teacherId ?: "" },
                        teacherName = teacherName.ifBlank { existing?.teacherName ?: "" },
                        createdTimestamp = existing?.createdTimestamp ?: now,
                        lastModifiedTimestamp = now
                    )
                    attendanceRepository.saveSingleAttendanceRecord(record)
                    _uiState.update { it.copy(isAlreadyTaken = true) }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceViewModel", "Auto-sync single attendance error: ${e.message}")
                }
            }
        }
    }

    fun setStudentRemark(
        studentId: String,
        remark: String,
        teacherId: String = "",
        teacherName: String = ""
    ) {
        _uiState.update { state ->
            val updatedMap = state.remarkMap.toMutableMap()
            updatedMap[studentId] = remark
            state.copy(remarkMap = updatedMap)
        }

        val state = _uiState.value
        if (state.selectedClassId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    val existingList = attendanceRepository.getAttendanceByClassAndDateDirect(state.selectedClassId, state.selectedDateIso)
                    val existing = existingList.find { it.studentId == studentId }
                    val currentStatus = state.attendanceMap[studentId] ?: existing?.status ?: AttendanceStatus.PRESENT
                    val studentName = state.students.find { it.studentId == studentId }?.studentName
                        ?: existing?.studentName
                        ?: ""
                    val record = AttendanceRecordEntity(
                        attendanceId = existing?.attendanceId ?: "ATT_${state.selectedDateIso}_${studentId}",
                        studentId = studentId,
                        studentName = studentName,
                        classId = state.selectedClassId,
                        className = state.selectedClassName,
                        date = state.selectedDateIso,
                        status = currentStatus,
                        remark = remark,
                        teacherId = teacherId.ifBlank { existing?.teacherId ?: "" },
                        teacherName = teacherName.ifBlank { existing?.teacherName ?: "" },
                        createdTimestamp = existing?.createdTimestamp ?: now,
                        lastModifiedTimestamp = now
                    )
                    attendanceRepository.saveSingleAttendanceRecord(record)
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceViewModel", "Auto-sync remark error: ${e.message}")
                }
            }
        }
    }

    fun markAll(
        status: AttendanceStatus,
        teacherId: String = "",
        teacherName: String = ""
    ) {
        _uiState.update { state ->
            val updatedMap = state.attendanceMap.toMutableMap()
            for (student in state.students) {
                updatedMap[student.studentId] = status
            }
            state.copy(attendanceMap = updatedMap)
        }

        val state = _uiState.value
        if (state.selectedClassId.isNotBlank() && state.students.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    val existingRecords = attendanceRepository.getAttendanceByClassAndDateDirect(state.selectedClassId, state.selectedDateIso)
                    val existingMap = existingRecords.associateBy { it.studentId }
                    val records = state.students.map { student ->
                        val existing = existingMap[student.studentId]
                        AttendanceRecordEntity(
                            attendanceId = existing?.attendanceId ?: "ATT_${state.selectedDateIso}_${student.studentId}",
                            studentId = student.studentId,
                            studentName = student.studentName,
                            classId = state.selectedClassId,
                            className = state.selectedClassName,
                            date = state.selectedDateIso,
                            status = status,
                            remark = state.remarkMap[student.studentId] ?: existing?.remark ?: "",
                            teacherId = teacherId.ifBlank { existing?.teacherId ?: "" },
                            teacherName = teacherName.ifBlank { existing?.teacherName ?: "" },
                            createdTimestamp = existing?.createdTimestamp ?: now,
                            lastModifiedTimestamp = now
                        )
                    }
                    attendanceRepository.saveAttendanceRecords(records)
                    _uiState.update { it.copy(isAlreadyTaken = true) }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceViewModel", "Error in markAll sync: ${e.message}")
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun saveAttendance(teacherId: String, teacherName: String, onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.selectedClassId.isBlank() || state.students.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveMessage = null) }
            try {
                val existingRecords = attendanceRepository.getAttendanceByClassFlexibleDirect(
                    state.selectedClassId,
                    state.selectedClassName,
                    state.selectedDateIso
                )
                val existingMap = existingRecords.associateBy { it.studentId }
                val now = System.currentTimeMillis()

                val currentStudentIds = state.students.map { it.studentId }.toSet()
                val orphanRecords = existingRecords.filter { it.studentId !in currentStudentIds }
                val orphanIds = orphanRecords.map { it.attendanceId }

                val records = state.students.map { student ->
                    val status = state.attendanceMap[student.studentId] ?: AttendanceStatus.PRESENT
                    val remark = state.remarkMap[student.studentId] ?: ""
                    val existing = existingMap[student.studentId]
                    AttendanceRecordEntity(
                        attendanceId = existing?.attendanceId ?: "ATT_${state.selectedDateIso}_${student.studentId}",
                        studentId = student.studentId,
                        studentName = student.studentName,
                        classId = state.selectedClassId,
                        className = state.selectedClassName,
                        date = state.selectedDateIso,
                        status = status,
                        remark = remark,
                        teacherId = teacherId.ifBlank { existing?.teacherId ?: "" },
                        teacherName = teacherName.ifBlank { existing?.teacherName ?: "" },
                        createdTimestamp = existing?.createdTimestamp ?: now,
                        lastModifiedTimestamp = now
                    )
                }

                // Delete orphan records locally first so they never skew counts
                if (orphanIds.isNotEmpty()) {
                    attendanceRepository.deleteAttendanceRecordsLocally(orphanIds)
                }

                // Save locally first so UI is immediately updated and responsive
                attendanceRepository.saveAttendanceRecordsLocally(records)

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isAlreadyTaken = true,
                        saveMessage = "Attendance saved successfully for ${state.selectedClassName} (${DateUtils.formatIsoToDisplay(state.selectedDateIso)})."
                    )
                }
                onSuccess()

                // Push to Firestore in background without blocking the UI
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        if (orphanIds.isNotEmpty()) {
                            attendanceRepository.deleteAttendanceRecordsFromCloud(orphanIds)
                        }
                        attendanceRepository.pushAttendanceRecordsToCloud(records)
                    } catch (syncEx: Exception) {
                        android.util.Log.e("AttendanceViewModel", "Background cloud sync error: ${syncEx.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Error saving attendance: ${e.message}") }
            }
        }
    }

    fun addStudentDirectly(student: StudentEntity) {
        viewModelScope.launch {
            studentRepository.insertStudent(student)
        }
    }

    /**
     * Triggered by Pull-to-Refresh on Attendance screens to force-sync latest student status & attendance from Firestore.
     */
    fun refreshAttendanceData(
        classId: String? = null,
        className: String? = null,
        dateIso: String? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.update { it.copy(isRefreshing = true) }
            val result = attendanceRepository.forceSyncFromFirestore()
            
            val targetClassId = classId ?: _uiState.value.selectedClassId
            val targetClassName = className ?: _uiState.value.selectedClassName
            val targetDateIso = dateIso ?: _uiState.value.selectedDateIso

            if (targetClassId.isNotBlank()) {
                checkAlreadyTaken(targetClassId, targetDateIso)
                loadStudentsAndExistingAttendance(targetClassId, targetDateIso)
            }

            _isRefreshing.value = false
            _uiState.update { it.copy(isRefreshing = false) }

            if (result.isSuccess) {
                val msg = result.getOrNull() ?: "Latest attendance & student statuses synced from Firestore"
                onComplete?.invoke(true, msg)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to sync with Firestore. Using offline data."
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(saveMessage = null, errorMessage = null) }
    }
}
