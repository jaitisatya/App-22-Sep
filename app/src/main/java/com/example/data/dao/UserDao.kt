package com.example.data.dao

import androidx.room.*
import com.example.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY fullName ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY fullName ASC")
    suspend fun getAllUsersDirect(): List<UserEntity>

    @Query("SELECT * FROM users WHERE LOWER(collaborationStatus) = 'pending' OR LOWER(collaborationStatus) = 'restricted' ORDER BY fullName ASC")
    fun getPendingUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'TEACHER' AND active = 1 ORDER BY fullName ASC")
    fun getAllTeachers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:input) OR LOWER(email) = LOWER(:input) LIMIT 1")
    suspend fun getUserByUsernameOrEmail(input: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)

    @Query("DELETE FROM users WHERE userId IN ('USR_ADMIN_DIRECT', 'USR_ADMIN_SIMPLE', 'USR_ADMIN_1', 'USR_TEACHER_1', 'USR_TEACHER_2', 'USR_TEACHER_1_ALIAS', 'USR_TEACHER_2_ALIAS')")
    suspend fun deleteLegacyDemoUsers()
}
