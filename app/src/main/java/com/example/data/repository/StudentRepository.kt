package com.example.data.repository

import com.example.data.dao.StudentDao
import com.example.data.entity.StudentEntity
import com.example.data.firebase.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow

class StudentRepository(
    private val studentDao: StudentDao,
    private val firestoreSyncManager: FirestoreSyncManager? = null
) {

    val allStudents: Flow<List<StudentEntity>> = studentDao.getAllStudents()
    val totalStudentCount: Flow<Int> = studentDao.getActiveStudentCount()

    fun getStudentsByClass(classId: String): Flow<List<StudentEntity>> {
        return studentDao.getStudentsByClass(classId)
    }

    fun getStudentsByClasses(classIds: List<String>): Flow<List<StudentEntity>> {
        return studentDao.getStudentsByClasses(classIds)
    }

    fun getStudentCountByClass(classId: String): Flow<Int> {
        return studentDao.getStudentCountByClass(classId)
    }

    suspend fun getStudentById(studentId: String): StudentEntity? {
        return studentDao.getStudentById(studentId)
    }

    suspend fun addStudent(student: StudentEntity) {
        studentDao.insertStudent(student)
        firestoreSyncManager?.pushStudent(student)
    }

    suspend fun addStudents(students: List<StudentEntity>) {
        if (students.isEmpty()) return
        studentDao.insertStudents(students)
        for (st in students) {
            firestoreSyncManager?.pushStudent(st)
        }
    }

    suspend fun insertStudent(student: StudentEntity) {
        studentDao.insertStudent(student)
        firestoreSyncManager?.pushStudent(student)
    }

    suspend fun updateStudent(student: StudentEntity) {
        studentDao.updateStudent(student)
        firestoreSyncManager?.pushStudent(student)
    }

    suspend fun deleteStudent(student: StudentEntity) {
        studentDao.deleteStudent(student)
        firestoreSyncManager?.deleteStudent(student.studentId)
    }

    suspend fun clearAllStudents() {
        studentDao.deleteAllStudents()
        firestoreSyncManager?.clearAllStudentsInCloud()
    }

    suspend fun toggleStudentActiveState(studentId: String, newActive: Boolean) {
        val student = studentDao.getStudentById(studentId) ?: return
        val updated = student.copy(active = newActive)
        studentDao.updateStudent(updated)
        firestoreSyncManager?.pushStudent(updated)
    }
}
