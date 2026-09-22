package com.example.data.repository

import com.example.data.dao.AttendanceDao
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.model.AttendanceStatus
import kotlinx.coroutines.flow.Flow

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val firestoreSyncManager: FirestoreSyncManager? = null
) {

    val allAttendanceRecords: Flow<List<AttendanceRecordEntity>> = attendanceDao.getAllAttendanceRecords()

    fun getAttendanceByDate(date: String): Flow<List<AttendanceRecordEntity>> {
        return attendanceDao.getAttendanceByDate(date)
    }

    fun getAttendanceByClassAndDate(classId: String, date: String): Flow<List<AttendanceRecordEntity>> {
        return attendanceDao.getAttendanceByClassAndDate(classId, date)
    }

    suspend fun getAttendanceByClassAndDateDirect(classId: String, date: String): List<AttendanceRecordEntity> {
        return attendanceDao.getAttendanceByClassAndDateDirect(classId, date)
    }

    suspend fun getAttendanceByClassFlexibleDirect(classId: String, className: String, date: String): List<AttendanceRecordEntity> {
        return attendanceDao.getAttendanceByClassFlexibleDirect(classId, className, date)
    }

    fun getAttendanceByStudent(studentId: String): Flow<List<AttendanceRecordEntity>> {
        return attendanceDao.getAttendanceByStudent(studentId)
    }

    fun getAttendanceByClass(classId: String): Flow<List<AttendanceRecordEntity>> {
        return attendanceDao.getAttendanceByClass(classId)
    }

    fun getAttendanceByDateRange(startDate: String, endDate: String): Flow<List<AttendanceRecordEntity>> {
        return attendanceDao.getAttendanceByDateRange(startDate, endDate)
    }

    fun getPresentCountByDate(date: String): Flow<Int> {
        return attendanceDao.getCountByDateAndStatus(date, AttendanceStatus.PRESENT)
    }

    fun getAbsentCountByDate(date: String): Flow<Int> {
        return attendanceDao.getCountByDateAndStatus(date, AttendanceStatus.ABSENT)
    }

    fun getTotalCountByDate(date: String): Flow<Int> {
        return attendanceDao.getTotalCountByDate(date)
    }

    fun getStudentPresentCount(studentId: String): Flow<Int> {
        return attendanceDao.getStudentStatusCount(studentId, AttendanceStatus.PRESENT)
    }

    fun getStudentAbsentCount(studentId: String): Flow<Int> {
        return attendanceDao.getStudentStatusCount(studentId, AttendanceStatus.ABSENT)
    }

    fun getStudentTotalDays(studentId: String): Flow<Int> {
        return attendanceDao.getStudentTotalDays(studentId)
    }

    suspend fun saveAttendanceRecords(records: List<AttendanceRecordEntity>) {
        attendanceDao.insertAttendanceRecords(records)
        firestoreSyncManager?.pushAttendanceRecords(records)
    }

    suspend fun saveAttendanceRecordsLocally(records: List<AttendanceRecordEntity>) {
        attendanceDao.insertAttendanceRecords(records)
    }

    suspend fun deleteAttendanceRecordsLocally(attendanceIds: List<String>) {
        if (attendanceIds.isNotEmpty()) {
            attendanceDao.deleteAttendanceRecordsByIds(attendanceIds)
        }
    }

    suspend fun deleteAttendanceRecordsFromCloud(attendanceIds: List<String>) {
        if (attendanceIds.isNotEmpty()) {
            firestoreSyncManager?.deleteAttendanceRecords(attendanceIds)
        }
    }

    suspend fun pushAttendanceRecordsToCloud(records: List<AttendanceRecordEntity>) {
        firestoreSyncManager?.pushAttendanceRecords(records)
    }

    suspend fun saveSingleAttendanceRecord(record: AttendanceRecordEntity) {
        attendanceDao.insertAttendanceRecord(record)
        firestoreSyncManager?.pushSingleAttendanceRecord(record)
    }

    suspend fun hasAttendanceBeenTaken(classId: String, date: String): Boolean {
        return attendanceDao.existsByClassAndDate(classId, date)
    }

    suspend fun forceSyncFromFirestore(): Result<String> {
        return firestoreSyncManager?.forceSyncLatestFromFirestore()
            ?: Result.failure(Exception("Cloud sync manager is not available"))
    }
}
