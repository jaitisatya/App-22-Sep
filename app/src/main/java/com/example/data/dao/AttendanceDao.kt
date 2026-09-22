package com.example.data.dao

import androidx.room.*
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.model.AttendanceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY date DESC, studentName ASC")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records ORDER BY date DESC, studentName ASC")
    suspend fun getAllAttendanceRecordsDirect(): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE date = :date ORDER BY className ASC, studentName ASC")
    fun getAttendanceByDate(date: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND date = :date ORDER BY studentName ASC")
    fun getAttendanceByClassAndDate(classId: String, date: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND date = :date")
    suspend fun getAttendanceByClassAndDateDirect(classId: String, date: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceByStudent(studentId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId ORDER BY date DESC, studentName ASC")
    fun getAttendanceByClass(classId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, studentName ASC")
    fun getAttendanceByDateRange(startDate: String, endDate: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE date = :date AND status = :status")
    fun getCountByDateAndStatus(date: String, status: AttendanceStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE date = :date")
    fun getTotalCountByDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE classId = :classId AND date = :date AND status = :status")
    fun getCountByClassDateAndStatus(classId: String, date: String, status: AttendanceStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE studentId = :studentId AND status = :status")
    fun getStudentStatusCount(studentId: String, status: AttendanceStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE studentId = :studentId")
    fun getStudentTotalDays(studentId: String): Flow<Int>

    @Query("SELECT * FROM attendance_records WHERE (classId = :classId OR classId = :className OR className = :className) AND date = :date")
    suspend fun getAttendanceByClassFlexibleDirect(classId: String, className: String, date: String): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecordEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM attendance_records WHERE classId = :classId AND date = :date)")
    suspend fun existsByClassAndDate(classId: String, date: String): Boolean

    @Query("DELETE FROM attendance_records WHERE attendanceId = :attendanceId")
    suspend fun deleteAttendanceRecordById(attendanceId: String)

    @Query("DELETE FROM attendance_records WHERE attendanceId IN (:attendanceIds)")
    suspend fun deleteAttendanceRecordsByIds(attendanceIds: List<String>)

    @Query("UPDATE attendance_records SET studentId = :newStudentId WHERE studentId = :oldStudentId")
    suspend fun updateStudentIdForAttendance(oldStudentId: String, newStudentId: String)

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAllAttendanceRecords()
}
