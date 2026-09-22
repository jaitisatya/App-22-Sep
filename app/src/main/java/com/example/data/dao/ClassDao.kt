package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ClassEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY className ASC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes ORDER BY className ASC")
    suspend fun getAllClassesDirect(): List<ClassEntity>

    @Query("SELECT * FROM classes WHERE active = 1 ORDER BY className ASC")
    fun getActiveClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE classId = :classId LIMIT 1")
    suspend fun getClassById(classId: String): ClassEntity?

    @Query("SELECT * FROM classes WHERE classId IN (:classIds) AND active = 1 ORDER BY className ASC")
    fun getClassesByIds(classIds: List<String>): Flow<List<ClassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classEntity: ClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<ClassEntity>)

    @Update
    suspend fun updateClass(classEntity: ClassEntity)

    @Delete
    suspend fun deleteClass(classEntity: ClassEntity)

    @Query("DELETE FROM classes WHERE classId = :classId")
    suspend fun deleteClassById(classId: String)

    @Query("DELETE FROM classes")
    suspend fun deleteAllClasses()
}
