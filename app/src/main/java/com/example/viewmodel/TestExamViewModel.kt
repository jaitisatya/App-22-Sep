package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.data.repository.TestExamRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TestStats(
    val totalStudents: Int = 0,
    val appearedStudents: Int = 0,
    val absentStudents: Int = 0,
    val averageMarks: Double = 0.0,
    val highestMarks: Double = 0.0,
    val lowestMarks: Double = 0.0,
    val passPercentage: Double = 0.0
)

class TestExamViewModel(
    private val repository: TestExamRepository
) : ViewModel() {

    private val _selectedClassId = MutableStateFlow("")
    val selectedClassId: StateFlow<String> = _selectedClassId.asStateFlow()

    val testsForSelectedClass: StateFlow<List<ClassTestEntity>> = _selectedClassId
        .flatMapLatest { classId ->
            if (classId.isBlank()) flowOf(emptyList())
            else repository.getTestsByClass(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTests: StateFlow<List<ClassTestEntity>> = repository.getAllTests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTestId = MutableStateFlow("")
    val activeTestMarks: StateFlow<List<StudentTestMarksEntity>> = _activeTestId
        .flatMapLatest { testId ->
            if (testId.isBlank()) flowOf(emptyList())
            else repository.getMarksByTest(testId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun setClassId(classId: String) {
        _selectedClassId.value = classId
    }

    fun setActiveTestId(testId: String) {
        _activeTestId.value = testId
    }

    fun getMarksForTest(testId: String): Flow<List<StudentTestMarksEntity>> {
        return repository.getMarksByTest(testId)
    }

    suspend fun getMarksForTestDirect(testId: String): List<StudentTestMarksEntity> {
        return repository.getMarksByTestDirect(testId)
    }

    suspend fun getTestByIdDirect(testId: String): ClassTestEntity? {
        return repository.getTestByIdDirect(testId)
    }

    fun saveTest(
        test: ClassTestEntity,
        marks: List<StudentTestMarksEntity>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                repository.saveTestAndMarks(test, marks)
                _isSaving.value = false
                onSuccess()
            } catch (e: Exception) {
                _isSaving.value = false
                onError(e.message ?: "Failed to save test")
            }
        }
    }

    fun deleteTest(
        testId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.deleteTest(testId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete test")
            }
        }
    }

    companion object {
        fun calculateStats(test: ClassTestEntity, marks: List<StudentTestMarksEntity>): TestStats {
            if (marks.isEmpty()) return TestStats()
            val total = marks.size
            val absents = marks.count { it.isAbsent }
            val appeared = marks.filter { !it.isAbsent && it.obtainedMarks != null }
            if (appeared.isEmpty()) {
                return TestStats(
                    totalStudents = total,
                    appearedStudents = 0,
                    absentStudents = absents,
                    averageMarks = 0.0,
                    highestMarks = 0.0,
                    lowestMarks = 0.0,
                    passPercentage = 0.0
                )
            }
            val scores = appeared.mapNotNull { it.obtainedMarks }
            val avg = if (scores.isNotEmpty()) scores.average() else 0.0
            val max = scores.maxOrNull() ?: 0.0
            val min = scores.minOrNull() ?: 0.0
            val passingScore = if (test.passingMarks > 0.0) test.passingMarks else (test.totalMarks * 0.33)
            val passedCount = appeared.count { (it.obtainedMarks ?: 0.0) >= passingScore }
            val passPct = (passedCount.toDouble() / appeared.size) * 100.0

            return TestStats(
                totalStudents = total,
                appearedStudents = appeared.size,
                absentStudents = absents,
                averageMarks = avg,
                highestMarks = max,
                lowestMarks = min,
                passPercentage = passPct
            )
        }
    }
}
