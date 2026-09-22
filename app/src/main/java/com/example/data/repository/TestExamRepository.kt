package com.example.data.repository

import com.example.data.dao.TestExamDao
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentTestMarksEntity
import com.example.data.firebase.FirestoreSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TestExamRepository(
    private val testExamDao: TestExamDao,
    private val firestoreSyncManager: FirestoreSyncManager? = null
) {
    fun getTestsByClass(classId: String): Flow<List<ClassTestEntity>> {
        return testExamDao.getTestsByClass(classId)
    }

    fun getAllTests(): Flow<List<ClassTestEntity>> {
        return testExamDao.getAllTests()
    }

    fun getTestById(testId: String): Flow<ClassTestEntity?> {
        return testExamDao.getTestById(testId)
    }

    suspend fun getTestByIdDirect(testId: String): ClassTestEntity? {
        return testExamDao.getTestByIdDirect(testId)
    }

    fun getMarksByTest(testId: String): Flow<List<StudentTestMarksEntity>> {
        return testExamDao.getMarksByTest(testId)
    }

    suspend fun getMarksByTestDirect(testId: String): List<StudentTestMarksEntity> {
        return testExamDao.getMarksByTestDirect(testId)
    }

    suspend fun saveTestAndMarks(
        test: ClassTestEntity,
        marks: List<StudentTestMarksEntity>
    ) = withContext(Dispatchers.IO) {
        testExamDao.insertTest(test)
        if (marks.isNotEmpty()) {
            testExamDao.insertMarks(marks)
        }
        firestoreSyncManager?.pushTest(test)
        if (marks.isNotEmpty()) {
            firestoreSyncManager?.pushTestMarks(test.testId, marks)
        }
    }

    suspend fun deleteTest(testId: String) = withContext(Dispatchers.IO) {
        testExamDao.deleteTest(testId)
        testExamDao.deleteMarksByTest(testId)
        firestoreSyncManager?.deleteTest(testId)
    }
}
