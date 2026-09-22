package com.example.data

import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role
import com.example.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object DatabaseInitializer {

    suspend fun seedDatabaseIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val userDao = db.userDao()
        // Clean up legacy hardcoded demo users so fresh Sign Up works smoothly
        userDao.deleteLegacyDemoUsers()

        // Ensure permanent Master Admin always exists, is active, and synced to Firestore
        val existingAdmin = userDao.getUserByUsernameOrEmail("jaitifoundation@gmail.com")
        if (existingAdmin == null || !existingAdmin.active || existingAdmin.passwordHash != "Admin@123") {
            val masterAdmin = UserEntity(
                userId = "USR_MASTER_ADMIN_JAITI",
                username = "jaitifoundation@gmail.com",
                passwordHash = "Admin@123",
                fullName = "Jaiti",
                role = Role.ADMIN,
                email = "jaitifoundation@gmail.com",
                phone = "6367916384",
                active = true,
                assignedClassIdsCsv = "ALL",
                canTakeAllAttendance = true,
                canManageStudents = true,
                canManageClasses = true,
                canViewReports = true,
                collaborationStatus = "APPROVED"
            )
            userDao.insertUser(masterAdmin)
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("users").document(masterAdmin.userId).set(
                    hashMapOf(
                        "userId" to masterAdmin.userId,
                        "username" to masterAdmin.username,
                        "passwordHash" to masterAdmin.passwordHash,
                        "fullName" to masterAdmin.fullName,
                        "role" to "ADMIN",
                        "email" to masterAdmin.email,
                        "phone" to masterAdmin.phone,
                        "active" to true,
                        "assignedClassIdsCsv" to "ALL",
                        "canTakeAllAttendance" to true,
                        "canManageStudents" to true,
                        "canManageClasses" to true,
                        "canViewReports" to true,
                        "collaborationStatus" to "APPROVED"
                    )
                ).await()
            } catch (_: Exception) {}
        }
    }

    suspend fun purgeDemoStudents(db: AppDatabase, syncManager: com.example.data.firebase.FirestoreSyncManager?) = withContext(Dispatchers.IO) {
        val studentDao = db.studentDao()
        val all = studentDao.getAllStudentsDirect()
        val demoStudents = all.filter {
            it.studentId.matches(Regex("STU_\\d{1,2}")) ||
            it.areaName == "Sanjay Camp Slum" ||
            it.areaName.contains("Slum", ignoreCase = true) ||
            it.studentName.contains("Demo", ignoreCase = true)
        }
        for (student in demoStudents) {
            studentDao.deleteStudent(student)
            syncManager?.deleteStudent(student.studentId)
        }
    }

    suspend fun syncStandardClasses(db: AppDatabase, syncManager: com.example.data.firebase.FirestoreSyncManager?) = withContext(Dispatchers.IO) {
        val classDao = db.classDao()
        val existing = classDao.getAllClassesDirect()

        val validBatches = listOf(
            Triple("CLASS_J_PREP", "J Prep", ""),
            Triple("CLASS_J1", "J1", ""),
            Triple("CLASS_J2", "J2", ""),
            Triple("CLASS_J3", "J3", ""),
            Triple("CLASS_J4", "J4", ""),
            Triple("CLASS_J5", "J5", "")
        )
        val validClassIds = validBatches.map { it.first }.toSet()

        // 1. Remove unwanted classes (like J6 to J12) from local DB and Firestore
        for (cls in existing) {
            val upperId = cls.classId.trim().uppercase()
            val upperName = cls.className.trim().uppercase()
            val isExtraBatch = upperId.matches(Regex("CLASS_J(6|7|8|9|10|11|12).*")) ||
                    upperName.matches(Regex(".*J(6|7|8|9|10|11|12).*")) ||
                    (!validClassIds.contains(cls.classId) && !cls.classId.startsWith("CLASS_") && (upperName.startsWith("J6") || upperName.startsWith("J7") || upperName.startsWith("J8") || upperName.startsWith("J9") || upperName.startsWith("J10") || upperName.startsWith("J11") || upperName.startsWith("J12")))

            if (isExtraBatch) {
                classDao.deleteClass(cls)
                syncManager?.deleteClass(cls.classId)
            }
        }

        // 2. Ensure standard 6 batches exist without overwriting user-edited teacher names
        for ((cId, cName, defaultTeacher) in validBatches) {
            val found = classDao.getClassById(cId)
            if (found == null) {
                // Check if Firestore already has this class with an assigned teacher!
                var teacherFromCloud = defaultTeacher
                var teacherIdFromCloud = "USR_ADMIN_1"
                var locationFromCloud = ""
                try {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val doc = firestore.collection("classes").document(cId).get().await()
                    if (doc.exists()) {
                        val cloudTeacher = doc.getString("primaryTeacherName") ?: ""
                        val cloudTeacherId = doc.getString("primaryTeacherId") ?: ""
                        val cloudLocation = doc.getString("roomOrLocation") ?: ""
                        if (cloudTeacher.isNotBlank()) {
                            teacherFromCloud = cloudTeacher
                        }
                        if (cloudTeacherId.isNotBlank()) {
                            teacherIdFromCloud = cloudTeacherId
                        }
                        if (cloudLocation.isNotBlank()) {
                            locationFromCloud = cloudLocation
                        }
                    }
                } catch (_: Exception) {}

                val newEntity = ClassEntity(
                    classId = cId,
                    className = cName,
                    roomOrLocation = locationFromCloud,
                    primaryTeacherId = teacherIdFromCloud,
                    primaryTeacherName = teacherFromCloud,
                    active = true,
                    createdTimestamp = System.currentTimeMillis()
                )
                classDao.insertClass(newEntity)
                // Only push to Firestore if we have teacher information, never overwrite with empty
                if (teacherFromCloud.isNotBlank()) {
                    syncManager?.pushClass(newEntity)
                }
            }
        }
    }

    suspend fun purgeAllNonAdminTeachers(
        db: AppDatabase,
        syncManager: com.example.data.firebase.FirestoreSyncManager?,
        context: android.content.Context
    ) = withContext(Dispatchers.IO) {
        // Safe no-op: Preserves all active teacher accounts and active sessions permanently
    }

    /**
     * Assigns/syncs sequential student IDs (JF0001, JF0002, ..., JF0108) to all existing students
     * ordered alphabetically (A to Z) by studentName.
     * Also updates corresponding local attendance records and synchronizes with Firestore.
     */
    suspend fun syncAndAssignStudentIdsAlphabetically(
        db: AppDatabase,
        syncManager: com.example.data.firebase.FirestoreSyncManager?
    ) = withContext(Dispatchers.IO) {
        val studentDao = db.studentDao()
        val attendanceDao = db.attendanceDao()
        val existingStudents = studentDao.getAllStudentsDirect()
        if (existingStudents.isEmpty()) return@withContext

        // Sort all existing students alphabetically by studentName (case-insensitive)
        val sortedStudents = existingStudents.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.studentName.trim() }
        )

        // Check if already assigned correctly to avoid redundant writes
        var needsUpdate = false
        for ((index, student) in sortedStudents.withIndex()) {
            val expectedId = com.example.util.StudentIdUtils.formatStudentId(index + 1)
            if (student.studentId != expectedId) {
                needsUpdate = true
                break
            }
        }

        if (!needsUpdate) return@withContext

        val firestore = try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }

        for ((index, student) in sortedStudents.withIndex()) {
            val oldId = student.studentId
            val newId = com.example.util.StudentIdUtils.formatStudentId(index + 1)

            if (oldId != newId) {
                // 1. Delete old ID entity if key is changed
                studentDao.deleteStudentById(oldId)

                // 2. Insert student with new JF ID
                val updatedStudent = student.copy(studentId = newId)
                studentDao.insertStudent(updatedStudent)

                // 3. Update attendance records linked to the student
                attendanceDao.updateStudentIdForAttendance(oldId, newId)

                // 4. Update Firestore
                if (syncManager != null) {
                    try {
                        syncManager.deleteStudent(oldId)
                        syncManager.pushStudent(updatedStudent)
                    } catch (_: Exception) {}
                } else if (firestore != null) {
                    try {
                        firestore.collection("students").document(oldId).delete()
                        firestore.collection("students").document(newId).set(updatedStudent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Reconciles all local attendance records with the assigned JF student IDs by matching studentName.
     * Ensures any attendance taken under old IDs or synced from Firestore matches the student's active JF ID.
     */
    suspend fun reconcileAttendanceRecordsWithStudents(
        db: AppDatabase,
        syncManager: com.example.data.firebase.FirestoreSyncManager?
    ) = withContext(Dispatchers.IO) {
        val studentDao = db.studentDao()
        val attendanceDao = db.attendanceDao()

        val allStudents = studentDao.getAllStudentsDirect()
        if (allStudents.isEmpty()) return@withContext

        val studentByName = allStudents.associateBy { it.studentName.trim().lowercase() }
        val studentById = allStudents.associateBy { it.studentId }
        val allAttendance = attendanceDao.getAllAttendanceRecordsDirect()
        if (allAttendance.isEmpty()) return@withContext

        val toUpdate = mutableListOf<com.example.data.entity.AttendanceRecordEntity>()
        for (rec in allAttendance) {
            val matchedStudent = studentById[rec.studentId]
                ?: studentByName[rec.studentName.trim().lowercase()]

            if (matchedStudent != null && rec.studentId != matchedStudent.studentId) {
                toUpdate.add(rec.copy(studentId = matchedStudent.studentId))
            }
        }

        if (toUpdate.isNotEmpty()) {
            attendanceDao.insertAttendanceRecords(toUpdate)
            syncManager?.pushAttendanceRecords(toUpdate)
        }
    }
}
