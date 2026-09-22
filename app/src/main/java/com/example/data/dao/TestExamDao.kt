package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ClassTestEntity
import com.example.data.entity.StudentTestMarksEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestExamDao {

    @Query("SELECT * FROM class_tests WHERE classId = :classId ORDER BY testDate DESC, createdTimestamp DESC")
    fun getTestsByClass(classId: String): Flow<List<ClassTestEntity>>

    @Query("SELECT * FROM class_tests ORDER BY testDate DESC, createdTimestamp DESC")
    fun getAllTests(): Flow<List<ClassTestEntity>>

    @Query("SELECT * FROM class_tests ORDER BY testDate DESC, createdTimestamp DESC")
    suspend fun getAllTestsDirect(): List<ClassTestEntity>

    @Query("SELECT * FROM class_tests WHERE testId = :testId LIMIT 1")
    fun getTestById(testId: String): Flow<ClassTestEntity?>

    @Query("SELECT * FROM class_tests WHERE testId = :testId LIMIT 1")
    suspend fun getTestByIdDirect(testId: String): ClassTestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: ClassTestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTests(tests: List<ClassTestEntity>)

    @Query("DELETE FROM class_tests WHERE testId = :testId")
    suspend fun deleteTest(testId: String)

    @Query("SELECT * FROM student_test_marks WHERE testId = :testId ORDER BY studentId ASC")
    fun getMarksByTest(testId: String): Flow<List<StudentTestMarksEntity>>

    @Query("SELECT * FROM student_test_marks WHERE testId = :testId ORDER BY studentId ASC")
    suspend fun getMarksByTestDirect(testId: String): List<StudentTestMarksEntity>

    @Query("SELECT * FROM student_test_marks ORDER BY testId ASC, studentId ASC")
    suspend fun getAllMarksDirect(): List<StudentTestMarksEntity>

    @Query("SELECT * FROM student_test_marks WHERE studentId = :studentId ORDER BY lastModifiedTimestamp DESC")
    fun getMarksByStudent(studentId: String): Flow<List<StudentTestMarksEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarks(marks: List<StudentTestMarksEntity>)

    @Query("DELETE FROM student_test_marks WHERE testId = :testId")
    suspend fun deleteMarksByTest(testId: String)
}
