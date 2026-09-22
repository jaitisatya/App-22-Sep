package com.example.data.dao

import androidx.room.*
import com.example.data.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY studentName ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY studentName ASC")
    suspend fun getAllStudentsDirect(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY studentName ASC")
    fun getStudentsByClass(classId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE classId IN (:classIds) ORDER BY studentName ASC")
    fun getStudentsByClasses(classIds: List<String>): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE studentId = :studentId LIMIT 1")
    suspend fun getStudentById(studentId: String): StudentEntity?

    @Query("SELECT * FROM students WHERE LOWER(TRIM(studentName)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getStudentByNameDirect(name: String): StudentEntity?

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    fun getActiveStudentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM students WHERE classId = :classId AND active = 1")
    fun getStudentCountByClass(classId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE studentId = :studentId")
    suspend fun deleteStudentById(studentId: String)

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
}
