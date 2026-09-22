package com.example.data.repository

import com.example.data.dao.ClassDao
import com.example.data.entity.ClassEntity
import com.example.data.firebase.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow

class ClassRepository(
    private val classDao: ClassDao,
    private val firestoreSyncManager: FirestoreSyncManager? = null
) {

    val allClasses: Flow<List<ClassEntity>> = classDao.getAllClasses()
    val activeClasses: Flow<List<ClassEntity>> = classDao.getActiveClasses()

    fun getClassesByIds(classIds: List<String>): Flow<List<ClassEntity>> {
        return classDao.getClassesByIds(classIds)
    }

    suspend fun getClassById(classId: String): ClassEntity? {
        return classDao.getClassById(classId)
    }

    suspend fun addClass(classEntity: ClassEntity) {
        classDao.insertClass(classEntity)
        firestoreSyncManager?.pushClass(classEntity)
    }

    suspend fun updateClass(classEntity: ClassEntity) {
        classDao.insertClass(classEntity)
        firestoreSyncManager?.pushClass(classEntity)
    }

    suspend fun deleteClass(classEntity: ClassEntity) {
        classDao.deleteClass(classEntity)
        firestoreSyncManager?.deleteClass(classEntity.classId)
    }

    suspend fun clearAllClasses() {
        classDao.deleteAllClasses()
    }
}
