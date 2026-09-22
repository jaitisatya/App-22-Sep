package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import com.example.data.entity.UserEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role
import com.example.data.repository.ClassPhotoManager
import com.example.util.ImageUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("FirestoreSyncManager", "Coroutine error caught safely: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private var firestore: FirebaseFirestore? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("Initializing Cloud...")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private var classesListener: ListenerRegistration? = null
    private var studentsListener: ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    init {
        scope.launch {
            initFirebase()
        }
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
            } catch (se: Throwable) {
                Log.w("FirestoreSyncManager", "Firestore settings note: ${se.message}")
            }
            firestore = db
            _isCloudConnected.value = true
            _syncStatusMessage.value = "Cloud Sync Active"
            Log.d("FirestoreSyncManager", "Firebase Firestore initialized successfully with offline cache")
            
            // Start real-time listeners
            startRealtimeListeners()
            com.example.data.repository.ClassPhotoManager.startListening(context)

            // Run photo migration & sync in background only if version is supported
            scope.launch {
                if (com.example.data.repository.AppVersionManager.isVersionSupported()) {
                    migrateAndSyncLocalPhotosToCloud()
                } else {
                    Log.w("FirestoreSyncManager", "Skipping background photo migration: App version unsupported")
                }
            }
        } catch (e: Throwable) {
            Log.e("FirestoreSyncManager", "Firestore init handled gracefully: ${e.message}")
            _isCloudConnected.value = false
            _syncStatusMessage.value = "Local Mode (Offline-First)"
        }
    }

    /**
     * Start live snapshot listeners on Firestore collections to sync cloud changes to local Room DB.
     */
    fun startRealtimeListeners() {
        try {
            val db = firestore ?: return

            // 1. Listen for Classes
            classesListener?.remove()
            classesListener = db.collection("classes").addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirestoreSyncManager", "Classes listen failed", e)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    scope.launch {
                        // Handle deleted classes
                        for (change in snapshots.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val classId = change.document.getString("classId") ?: change.document.id
                                database.classDao().deleteClassById(classId)
                                Log.d("FirestoreSyncManager", "Removed class locally: $classId")
                            }
                        }

                        // Upsert active/updated classes
                        val classesList = mutableListOf<ClassEntity>()
                        for (doc in snapshots.documents) {
                            try {
                                val classId = doc.getString("classId") ?: doc.id
                                val className = doc.getString("className") ?: "Class"
                                val roomOrLocation = doc.getString("roomOrLocation") ?: ""
                                val primaryTeacherId = doc.getString("primaryTeacherId") ?: ""
                                val primaryTeacherName = doc.getString("primaryTeacherName") ?: ""
                                val active = doc.getBoolean("active") ?: true
                                val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()

                                val existingLocal = database.classDao().getClassById(classId)
                                val finalTeacherName = if (primaryTeacherName.isNotBlank()) {
                                    primaryTeacherName
                                } else {
                                    existingLocal?.primaryTeacherName ?: ""
                                }
                                val finalTeacherId = if (primaryTeacherId.isNotBlank()) {
                                    primaryTeacherId
                                } else {
                                    existingLocal?.primaryTeacherId ?: ""
                                }

                                classesList.add(
                                    ClassEntity(
                                        classId = classId,
                                        className = className,
                                        roomOrLocation = roomOrLocation,
                                        primaryTeacherId = finalTeacherId,
                                        primaryTeacherName = finalTeacherName,
                                        active = active,
                                        createdTimestamp = createdTimestamp
                                    )
                                )
                            } catch (ex: Exception) {
                                Log.e("FirestoreSyncManager", "Error parsing class doc: ${ex.message}")
                            }
                        }
                        if (classesList.isNotEmpty()) {
                            database.classDao().insertClasses(classesList)
                        }
                    }
                }
            }

            // 2. Listen for Students (Sync Updates & Deletions to all devices)
            studentsListener?.remove()
            studentsListener = db.collection("students").addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirestoreSyncManager", "Students listen failed", e)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    scope.launch {
                        // 1. Reconcile deletions across all devices:
                        // Gather all active student IDs currently present in Cloud Firestore
                        val cloudStudentIds = snapshots.documents.map { it.getString("studentId") ?: it.id }.toSet()
                        if (cloudStudentIds.isNotEmpty()) {
                            val localStudents = database.studentDao().getAllStudentsDirect()
                            for (localSt in localStudents) {
                                if (!cloudStudentIds.contains(localSt.studentId)) {
                                    database.studentDao().deleteStudentById(localSt.studentId)
                                    Log.d("FirestoreSyncManager", "Reconciled deleted student locally on startup/sync: ${localSt.studentId}")
                                }
                            }
                        }

                        // Handle document change REMOVED explicitly as well
                        for (change in snapshots.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val studentId = change.document.getString("studentId") ?: change.document.id
                                database.studentDao().deleteStudentById(studentId)
                                Log.d("FirestoreSyncManager", "Removed student locally on all devices: $studentId")
                            }
                        }

                        val studentsList = mutableListOf<StudentEntity>()
                        for (doc in snapshots.documents) {
                            try {
                                val studentId = doc.getString("studentId") ?: doc.id
                                val studentName = doc.getString("studentName") ?: ""
                                val fatherName = doc.getString("fatherName") ?: ""
                                val motherName = doc.getString("motherName") ?: ""
                                val classId = doc.getString("classId") ?: ""
                                val phoneNumber = doc.getString("phoneNumber") ?: ""
                                val photoUri = doc.getString("photoUri") ?: ""
                                val age = doc.getLong("age")?.toInt() ?: 0
                                val gender = doc.getString("gender") ?: "Male"
                                val areaName = doc.getString("areaName") ?: ""
                                val schoolName = doc.getString("schoolName") ?: ""
                                val active = doc.getBoolean("active") ?: true
                                val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()

                                val dob = doc.getString("dob") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val schoolClass = doc.getString("schoolClass") ?: ""
                                val aadharCardUri = doc.getString("aadharCardUri") ?: ""
                                val birthCertificateUri = doc.getString("birthCertificateUri") ?: ""
                                val consentFormUri = doc.getString("consentFormUri") ?: ""

                                val localExisting = database.studentDao().getStudentById(studentId)
                                val finalPhotoUri = if (doc.contains("photoUri")) {
                                    photoUri
                                } else {
                                    localExisting?.photoUri ?: ""
                                }

                                val finalAadharUri = if (doc.contains("aadharCardUri")) aadharCardUri else (localExisting?.aadharCardUri ?: "")
                                val finalBirthCertUri = if (doc.contains("birthCertificateUri")) birthCertificateUri else (localExisting?.birthCertificateUri ?: "")
                                val finalConsentUri = if (doc.contains("consentFormUri")) consentFormUri else (localExisting?.consentFormUri ?: "")

                                val studentToSave = StudentEntity(
                                    studentId = studentId,
                                    studentName = studentName,
                                    fatherName = fatherName,
                                    motherName = motherName,
                                    classId = classId,
                                    phoneNumber = phoneNumber,
                                    photoUri = finalPhotoUri,
                                    age = age,
                                    gender = gender,
                                    areaName = areaName,
                                    schoolName = schoolName,
                                    active = active,
                                    createdTimestamp = createdTimestamp,
                                    dob = dob,
                                    notes = notes,
                                    schoolClass = schoolClass,
                                    aadharCardUri = finalAadharUri,
                                    birthCertificateUri = finalBirthCertUri,
                                    consentFormUri = finalConsentUri
                                )

                                studentsList.add(studentToSave)

                                // If local had photo but cloud was empty, push updated photo to cloud
                                if (photoUri.isBlank() && !localExisting?.photoUri.isNullOrBlank()) {
                                    scope.launch {
                                        pushStudent(studentToSave)
                                    }
                                }
                            } catch (ex: Exception) {
                                Log.e("FirestoreSyncManager", "Error parsing student doc: ${ex.message}")
                            }
                        }
                        if (studentsList.isNotEmpty()) {
                            database.studentDao().insertStudents(studentsList)
                        }
                    }
                }
            }

            // 3. Listen for Attendance Records
            attendanceListener?.remove()
            attendanceListener = db.collection("attendance_records").addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirestoreSyncManager", "Attendance listen failed", e)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    scope.launch {
                        val toUpsert = mutableListOf<AttendanceRecordEntity>()
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val attendanceId = doc.getString("attendanceId") ?: doc.id
                            if (change.type == DocumentChange.Type.REMOVED) {
                                database.attendanceDao().deleteAttendanceRecordById(attendanceId)
                                Log.d("FirestoreSyncManager", "Removed attendance locally: $attendanceId")
                            } else {
                                try {
                                    var studentId = doc.getString("studentId") ?: ""
                                    val studentName = doc.getString("studentName") ?: ""
                                    val classId = doc.getString("classId") ?: ""
                                    val className = doc.getString("className") ?: ""
                                    val date = doc.getString("date") ?: ""
                                    val statusStr = doc.getString("status") ?: "PRESENT"
                                    val status = try { AttendanceStatus.valueOf(statusStr) } catch (_: Exception) { AttendanceStatus.PRESENT }
                                    val remark = doc.getString("remark") ?: ""
                                    val teacherId = doc.getString("teacherId") ?: ""
                                    val teacherName = doc.getString("teacherName") ?: ""
                                    val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()
                                    val lastModifiedTimestamp = doc.getLong("lastModifiedTimestamp") ?: System.currentTimeMillis()

                                    // Real-time photo synchronization across devices
                                    val photoUrl = doc.getString("photoUrl") ?: doc.getString("photoUri") ?: ""
                                    val photoStoragePath = doc.getString("photoStoragePath") ?: ""
                                    if (classId.isNotBlank() && date.isNotBlank() && (photoUrl.isNotBlank() || photoStoragePath.isNotBlank())) {
                                        ClassPhotoManager.onAttendancePhotoReceived(context, classId, date, photoUrl, photoStoragePath)
                                    }

                                    if (studentName.isNotBlank() && (!studentId.startsWith("JF") || studentId.isBlank())) {
                                        val matched = database.studentDao().getStudentByNameDirect(studentName)
                                        if (matched != null) {
                                            studentId = matched.studentId
                                        }
                                    }

                                    toUpsert.add(
                                        AttendanceRecordEntity(
                                            attendanceId = attendanceId,
                                            studentId = studentId,
                                            studentName = studentName,
                                            classId = classId,
                                            className = className,
                                            date = date,
                                            status = status,
                                            remark = remark,
                                            teacherId = teacherId,
                                            teacherName = teacherName,
                                            createdTimestamp = createdTimestamp,
                                            lastModifiedTimestamp = lastModifiedTimestamp
                                        )
                                    )
                                } catch (ex: Exception) {
                                    Log.e("FirestoreSyncManager", "Error parsing attendance doc change: ${ex.message}")
                                }
                            }
                        }
                        if (toUpsert.isNotEmpty()) {
                            database.attendanceDao().insertAttendanceRecords(toUpsert)
                            Log.d("FirestoreSyncManager", "Synced ${toUpsert.size} attendance records locally from Firestore change")
                        }
                    }
                }
            }

            // 4. Listen for Users
            usersListener?.remove()
            usersListener = db.collection("users").addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirestoreSyncManager", "Users listen failed", e)
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    scope.launch {
                        // Handle deleted users
                        for (change in snapshots.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val userId = change.document.getString("userId") ?: change.document.id
                                database.userDao().deleteUser(userId)
                                Log.d("FirestoreSyncManager", "Removed user locally: $userId")
                            }
                        }

                        val userList = mutableListOf<UserEntity>()
                        for (doc in snapshots.documents) {
                            try {
                                val userId = doc.getString("userId") ?: doc.id
                                val username = doc.getString("username") ?: ""
                                val passwordHash = doc.getString("passwordHash") ?: ""
                                val fullName = doc.getString("fullName") ?: ""
                                val roleStr = doc.getString("role") ?: "ADMIN"
                                val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.ADMIN }
                                val email = doc.getString("email") ?: ""
                                val phone = doc.getString("phone") ?: ""
                                val active = doc.getBoolean("active") ?: true
                                val assignedClassIdsCsv = doc.getString("assignedClassIdsCsv") ?: ""
                                val canTakeAllAttendance = doc.getBoolean("canTakeAllAttendance") ?: true
                                val canManageStudents = doc.getBoolean("canManageStudents") ?: true
                                val canManageClasses = doc.getBoolean("canManageClasses") ?: true
                                val canViewReports = doc.getBoolean("canViewReports") ?: true
                                val collaborationStatus = doc.getString("collaborationStatus")
                                    ?: doc.getString("status")
                                    ?: "APPROVED"

                                userList.add(
                                    UserEntity(
                                        userId = userId,
                                        username = username,
                                        passwordHash = passwordHash,
                                        fullName = fullName,
                                        role = role,
                                        email = email,
                                        phone = phone,
                                        active = active,
                                        assignedClassIdsCsv = assignedClassIdsCsv,
                                        canTakeAllAttendance = canTakeAllAttendance,
                                        canManageStudents = canManageStudents,
                                        canManageClasses = canManageClasses,
                                        canViewReports = canViewReports,
                                        collaborationStatus = collaborationStatus
                                    )
                                )
                            } catch (ex: Exception) {
                                Log.e("FirestoreSyncManager", "Error parsing user doc: ${ex.message}")
                            }
                        }
                        if (userList.isNotEmpty()) {
                            database.userDao().insertUsers(userList)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("FirestoreSyncManager", "Listeners error handled: ${t.message}")
        }
    }

    // --- PUSH OPERATIONS TO FIRESTORE ---

    private fun canWriteToCloud(): Boolean {
        return com.example.data.repository.AppVersionManager.isVersionSupported()
    }

    suspend fun pushClass(classEntity: ClassEntity) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushClass blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            var teacherNameToPush = classEntity.primaryTeacherName
            var teacherIdToPush = classEntity.primaryTeacherId
            if (teacherNameToPush.isBlank()) {
                try {
                    val existingDoc = db.collection("classes").document(classEntity.classId).get().await()
                    if (existingDoc.exists()) {
                        val existingTeacher = existingDoc.getString("primaryTeacherName") ?: ""
                        if (existingTeacher.isNotBlank()) {
                            teacherNameToPush = existingTeacher
                            teacherIdToPush = existingDoc.getString("primaryTeacherId") ?: teacherIdToPush
                        }
                    }
                } catch (_: Exception) {}
            }

            val map = hashMapOf(
                "classId" to classEntity.classId,
                "className" to classEntity.className,
                "roomOrLocation" to classEntity.roomOrLocation,
                "primaryTeacherId" to teacherIdToPush,
                "primaryTeacherName" to teacherNameToPush,
                "active" to classEntity.active,
                "createdTimestamp" to classEntity.createdTimestamp
            )
            db.collection("classes").document(classEntity.classId).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push class ${classEntity.classId}: ${e.message}")
        }
    }

    suspend fun deleteClass(classId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "deleteClass blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("classes").document(classId).delete().await()
            Log.d("FirestoreSyncManager", "Deleted class $classId from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete class $classId: ${e.message}")
        }
    }

    suspend fun pushStudent(student: StudentEntity) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushStudent blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            // If photoUri is a local file/content URI, convert it to portable Data URL
            var syncablePhotoUri = student.photoUri
            if (syncablePhotoUri.isNotBlank() && !syncablePhotoUri.startsWith("data:image/")) {
                val converted = ImageUtils.fileOrUriToDataUrl(context, syncablePhotoUri)
                if (!converted.isNullOrBlank()) {
                    syncablePhotoUri = converted
                    // Update local Room database with syncable format
                    database.studentDao().insertStudent(student.copy(photoUri = converted))
                }
            }

            val map = hashMapOf(
                "studentId" to student.studentId,
                "studentName" to student.studentName,
                "fatherName" to student.fatherName,
                "motherName" to student.motherName,
                "classId" to student.classId,
                "phoneNumber" to student.phoneNumber,
                "photoUri" to syncablePhotoUri,
                "age" to student.age,
                "gender" to student.gender,
                "areaName" to student.areaName,
                "schoolName" to student.schoolName,
                "active" to student.active,
                "createdTimestamp" to student.createdTimestamp,
                "dob" to student.dob,
                "notes" to student.notes,
                "schoolClass" to student.schoolClass,
                "aadharCardUri" to student.aadharCardUri,
                "birthCertificateUri" to student.birthCertificateUri,
                "consentFormUri" to student.consentFormUri
            )
            db.collection("students").document(student.studentId).set(map, SetOptions.merge()).await()
            Log.d("FirestoreSyncManager", "Successfully synced student ${student.studentName} with cloud photo")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push student ${student.studentId}: ${e.message}")
        }
    }

    suspend fun clearAllStudentsInCloud() = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "clearAllStudentsInCloud blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            val snapshot = db.collection("students").get().await()
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Log.d("FirestoreSyncManager", "Cleared all students in Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to clear students in cloud: ${e.message}")
        }
    }

    suspend fun deleteStudent(studentId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "deleteStudent blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("students").document(studentId).delete().await()
            Log.d("FirestoreSyncManager", "Deleted student $studentId from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete student $studentId: ${e.message}")
        }
    }

    suspend fun deleteAttendanceRecord(attendanceId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) return@withContext
        val db = firestore ?: return@withContext
        try {
            db.collection("attendance_records").document(attendanceId).delete().await()
            Log.d("FirestoreSyncManager", "Deleted attendance $attendanceId from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete attendance $attendanceId: ${e.message}")
        }
    }

    suspend fun deleteAttendanceRecords(attendanceIds: List<String>) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud() || attendanceIds.isEmpty()) return@withContext
        val db = firestore ?: return@withContext
        try {
            val chunks = attendanceIds.chunked(450)
            for (chunk in chunks) {
                val batch = db.batch()
                for (id in chunk) {
                    batch.delete(db.collection("attendance_records").document(id))
                }
                batch.commit().await()
            }
            Log.d("FirestoreSyncManager", "Deleted ${attendanceIds.size} attendance records from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete attendance records: ${e.message}")
        }
    }

    suspend fun pushAttendanceRecords(records: List<AttendanceRecordEntity>) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushAttendanceRecords blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        if (records.isEmpty()) return@withContext
        try {
            // Firestore batch has a limit of 500 operations. Chunk into batches of 450.
            val chunks = records.chunked(450)
            for (chunk in chunks) {
                val batch = db.batch()
                for (record in chunk) {
                    val docRef = db.collection("attendance_records").document(record.attendanceId)
                    val photoUrl = ClassPhotoManager.getPhotoUrl(context, record.classId, record.date) ?: ""
                    val photoStoragePath = ClassPhotoManager.getPhotoStoragePath(context, record.classId, record.date) ?: ""
                    val map = hashMapOf(
                        "attendanceId" to record.attendanceId,
                        "studentId" to record.studentId,
                        "studentName" to record.studentName,
                        "classId" to record.classId,
                        "className" to record.className,
                        "date" to record.date,
                        "status" to record.status.name,
                        "remark" to record.remark,
                        "teacherId" to record.teacherId,
                        "teacherName" to record.teacherName,
                        "createdTimestamp" to record.createdTimestamp,
                        "lastModifiedTimestamp" to record.lastModifiedTimestamp,
                        "photoUrl" to photoUrl,
                        "photoStoragePath" to photoStoragePath
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                batch.commit().await()
            }
            Log.d("FirestoreSyncManager", "Successfully pushed ${records.size} attendance records to cloud")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push attendance records: ${e.message}")
        }
    }

    suspend fun pushSingleAttendanceRecord(record: AttendanceRecordEntity) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushSingleAttendanceRecord blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            val docRef = db.collection("attendance_records").document(record.attendanceId)
            val photoUrl = ClassPhotoManager.getPhotoUrl(context, record.classId, record.date) ?: ""
            val photoStoragePath = ClassPhotoManager.getPhotoStoragePath(context, record.classId, record.date) ?: ""
            val map = hashMapOf(
                "attendanceId" to record.attendanceId,
                "studentId" to record.studentId,
                "studentName" to record.studentName,
                "classId" to record.classId,
                "className" to record.className,
                "date" to record.date,
                "status" to record.status.name,
                "remark" to record.remark,
                "teacherId" to record.teacherId,
                "teacherName" to record.teacherName,
                "createdTimestamp" to record.createdTimestamp,
                "lastModifiedTimestamp" to record.lastModifiedTimestamp,
                "photoUrl" to photoUrl,
                "photoStoragePath" to photoStoragePath
            )
            docRef.set(map, SetOptions.merge()).await()
            Log.d("FirestoreSyncManager", "Real-time pushed attendance for ${record.studentName}: ${record.status.name}")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to real-time push attendance record: ${e.message}")
        }
    }

    /**
     * Pulls all attendance records from Firestore on startup and resolves student IDs dynamically.
     */
    suspend fun fetchAllAttendanceFromCloud() = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val attSnapshots = db.collection("attendance_records").get().await()
            if (!attSnapshots.isEmpty) {
                val allStudents = database.studentDao().getAllStudentsDirect()
                val studentByName = allStudents.associateBy { it.studentName.trim().lowercase() }
                val records = mutableListOf<AttendanceRecordEntity>()
                for (doc in attSnapshots.documents) {
                    try {
                        val attendanceId = doc.getString("attendanceId") ?: doc.id
                        var studentId = doc.getString("studentId") ?: ""
                        val studentName = doc.getString("studentName") ?: ""
                        val classId = doc.getString("classId") ?: ""
                        val className = doc.getString("className") ?: ""
                        val date = doc.getString("date") ?: ""
                        val statusStr = doc.getString("status") ?: "PRESENT"
                        val status = try { AttendanceStatus.valueOf(statusStr) } catch (_: Exception) { AttendanceStatus.PRESENT }
                        val remark = doc.getString("remark") ?: ""
                        val teacherId = doc.getString("teacherId") ?: ""
                        val teacherName = doc.getString("teacherName") ?: ""
                        val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()
                        val lastModifiedTimestamp = doc.getLong("lastModifiedTimestamp") ?: System.currentTimeMillis()

                        val photoUrl = doc.getString("photoUrl") ?: doc.getString("photoUri") ?: ""
                        val photoStoragePath = doc.getString("photoStoragePath") ?: ""
                        if (classId.isNotBlank() && date.isNotBlank() && (photoUrl.isNotBlank() || photoStoragePath.isNotBlank())) {
                            ClassPhotoManager.onAttendancePhotoReceived(context, classId, date, photoUrl, photoStoragePath)
                        }

                        val matchedStudent = studentByName[studentName.trim().lowercase()]
                        if (matchedStudent != null && (studentId.isBlank() || !studentId.startsWith("JF"))) {
                            studentId = matchedStudent.studentId
                        }

                        records.add(
                            AttendanceRecordEntity(
                                attendanceId = attendanceId,
                                studentId = studentId,
                                studentName = studentName,
                                classId = classId,
                                className = className,
                                date = date,
                                status = status,
                                remark = remark,
                                teacherId = teacherId,
                                teacherName = teacherName,
                                createdTimestamp = createdTimestamp,
                                lastModifiedTimestamp = lastModifiedTimestamp
                            )
                        )
                    } catch (ex: Exception) {
                        Log.e("FirestoreSyncManager", "Error parsing attendance doc on startup: ${ex.message}")
                    }
                }
                if (records.isNotEmpty()) {
                    database.attendanceDao().insertAttendanceRecords(records)
                    Log.d("FirestoreSyncManager", "Fetched ${records.size} attendance records from cloud on startup")
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to fetch attendance records from cloud on startup: ${e.message}")
        }
    }

    suspend fun pushTest(test: com.example.data.entity.ClassTestEntity) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushTest blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "testId" to test.testId,
                "classId" to test.classId,
                "className" to test.className,
                "testTitle" to test.testTitle,
                "subject" to test.subject,
                "testDate" to test.testDate,
                "totalMarks" to test.totalMarks,
                "passingMarks" to test.passingMarks,
                "teacherId" to test.teacherId,
                "teacherName" to test.teacherName,
                "createdTimestamp" to test.createdTimestamp,
                "lastModifiedTimestamp" to test.lastModifiedTimestamp
            )
            db.collection("class_tests").document(test.testId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d("FirestoreSyncManager", "Pushed test ${test.testId} to Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push test ${test.testId}: ${e.message}")
        }
    }

    suspend fun deleteTest(testId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "deleteTest blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("class_tests").document(testId).delete().await()
            val marksSnap = db.collection("student_test_marks").whereEqualTo("testId", testId).get().await()
            val batch = db.batch()
            for (doc in marksSnap.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Log.d("FirestoreSyncManager", "Deleted test $testId and its marks from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete test $testId: ${e.message}")
        }
    }

    suspend fun pushTestMarks(testId: String, marks: List<com.example.data.entity.StudentTestMarksEntity>) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushTestMarks blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            val batch = db.batch()
            for (m in marks) {
                val docRef = db.collection("student_test_marks").document(m.markId)
                val map = hashMapOf(
                    "markId" to m.markId,
                    "testId" to m.testId,
                    "studentId" to m.studentId,
                    "studentName" to m.studentName,
                    "classId" to m.classId,
                    "obtainedMarks" to m.obtainedMarks,
                    "isAbsent" to m.isAbsent,
                    "remark" to m.remark,
                    "lastModifiedTimestamp" to m.lastModifiedTimestamp
                )
                batch.set(docRef, map, com.google.firebase.firestore.SetOptions.merge())
            }
            batch.commit().await()
            Log.d("FirestoreSyncManager", "Pushed ${marks.size} test marks to Firestore for test $testId")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push test marks: ${e.message}")
        }
    }

    suspend fun pushUser(user: UserEntity) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushUser blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            val map = hashMapOf(
                "userId" to user.userId,
                "username" to user.username,
                "passwordHash" to user.passwordHash,
                "fullName" to user.fullName,
                "role" to user.role.name,
                "email" to user.email,
                "phone" to user.phone,
                "active" to user.active,
                "assignedClassIdsCsv" to user.assignedClassIdsCsv,
                "canTakeAllAttendance" to user.canTakeAllAttendance,
                "canManageStudents" to user.canManageStudents,
                "canManageClasses" to user.canManageClasses,
                "canViewReports" to user.canViewReports,
                "collaborationStatus" to user.collaborationStatus,
                "status" to user.collaborationStatus,
                "updatedTimestamp" to System.currentTimeMillis()
            )
            db.collection("users").document(user.userId).set(map, SetOptions.merge()).await()
            Log.d("FirestoreSyncManager", "Pushed user ${user.userId} with status ${user.collaborationStatus} to Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push user ${user.userId}: ${e.message}")
        }
    }

    suspend fun deleteUser(userId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "deleteUser blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("users").document(userId).delete().await()
            Log.d("FirestoreSyncManager", "Deleted user $userId from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete user $userId: ${e.message}")
        }
    }

    suspend fun fetchAndSyncUsersNow() = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val snapshots = db.collection("users").get().await()
            if (!snapshots.isEmpty) {
                val userList = mutableListOf<UserEntity>()
                for (doc in snapshots.documents) {
                    try {
                        val userId = doc.getString("userId") ?: doc.id
                        val username = doc.getString("username") ?: ""
                        val passwordHash = doc.getString("passwordHash") ?: ""
                        val fullName = doc.getString("fullName") ?: ""
                        val roleStr = doc.getString("role") ?: "TEACHER"
                        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.TEACHER }
                        val email = doc.getString("email") ?: ""
                        val phone = doc.getString("phone") ?: ""
                        val active = doc.getBoolean("active") ?: true
                        val assignedClassIdsCsv = doc.getString("assignedClassIdsCsv") ?: ""
                        val canTakeAllAttendance = doc.getBoolean("canTakeAllAttendance") ?: true
                        val canManageStudents = doc.getBoolean("canManageStudents") ?: true
                        val canManageClasses = doc.getBoolean("canManageClasses") ?: true
                        val canViewReports = doc.getBoolean("canViewReports") ?: true
                        val collaborationStatus = doc.getString("collaborationStatus")
                            ?: doc.getString("status")
                            ?: "APPROVED"

                        userList.add(
                            UserEntity(
                                userId = userId,
                                username = username,
                                passwordHash = passwordHash,
                                fullName = fullName,
                                role = role,
                                email = email,
                                phone = phone,
                                active = active,
                                assignedClassIdsCsv = assignedClassIdsCsv,
                                canTakeAllAttendance = canTakeAllAttendance,
                                canManageStudents = canManageStudents,
                                canManageClasses = canManageClasses,
                                canViewReports = canViewReports,
                                collaborationStatus = collaborationStatus
                            )
                        )
                    } catch (ex: Exception) {
                        Log.e("FirestoreSyncManager", "Error parsing user during manual fetch: ${ex.message}")
                    }
                }
                if (userList.isNotEmpty()) {
                    database.userDao().insertUsers(userList)
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Manual fetchAndSyncUsersNow error: ${e.message}")
        }
    }

    suspend fun pushEducator(educator: com.example.data.model.EducatorProfile) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "pushEducator blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            var syncablePhotoUri = educator.photoUri
            if (syncablePhotoUri.isNotBlank() && !syncablePhotoUri.startsWith("data:image/")) {
                val converted = ImageUtils.fileOrUriToDataUrl(context, syncablePhotoUri)
                if (!converted.isNullOrBlank()) {
                    syncablePhotoUri = converted
                }
            }

            val map = hashMapOf(
                "id" to educator.id,
                "name" to educator.name,
                "photoUri" to syncablePhotoUri,
                "phone" to educator.phone,
                "subject" to educator.subject,
                "createdTimestamp" to educator.createdTimestamp
            )
            db.collection("educators").document(educator.id).set(map, SetOptions.merge()).await()
            Log.d("FirestoreSyncManager", "Pushed educator ${educator.name} with syncable photo to Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to push educator ${educator.id}: ${e.message}")
        }
    }

    /**
     * Scans local database for any photos stored as local file paths,
     * converts them to cross-device syncable Base64 Data URLs and pushes to Cloud.
     */
    suspend fun migrateAndSyncLocalPhotosToCloud() = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "migrateAndSyncLocalPhotosToCloud blocked: App version unsupported")
            return@withContext
        }
        try {
            val students = database.studentDao().getAllStudentsDirect()
            for (student in students) {
                if (student.photoUri.isNotBlank() && !student.photoUri.startsWith("data:image/")) {
                    val dataUrl = ImageUtils.fileOrUriToDataUrl(context, student.photoUri)
                    if (!dataUrl.isNullOrBlank()) {
                        val updated = student.copy(photoUri = dataUrl)
                        database.studentDao().insertStudent(updated)
                        pushStudent(updated)
                        Log.d("FirestoreSyncManager", "Migrated local photo for student: ${student.studentName}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error in migrateAndSyncLocalPhotosToCloud: ${e.message}")
        }
    }

    suspend fun deleteEducator(educatorId: String) = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "deleteEducator blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        try {
            db.collection("educators").document(educatorId).delete().await()
            Log.d("FirestoreSyncManager", "Deleted educator $educatorId from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete educator $educatorId: ${e.message}")
        }
    }

    /**
     * Force-sync latest student status, attendance records, classes and educators directly from Cloud Firestore.
     * Called when teachers pull to refresh on attendance screens.
     */
    suspend fun forceSyncLatestFromFirestore(): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firestore not initialized"))
        _isSyncing.value = true
        _syncStatusMessage.value = "Syncing latest data from Firestore..."
        try {
            var studentCount = 0
            var attendanceCount = 0
            var classCount = 0

            // 1. Fetch Students
            try {
                val studentSnapshots = db.collection("students").get().await()
                if (!studentSnapshots.isEmpty) {
                    val studentsList = mutableListOf<StudentEntity>()
                    for (doc in studentSnapshots.documents) {
                        try {
                            val studentId = doc.getString("studentId") ?: doc.id
                            val studentName = doc.getString("studentName") ?: ""
                            val fatherName = doc.getString("fatherName") ?: ""
                            val motherName = doc.getString("motherName") ?: ""
                            val classId = doc.getString("classId") ?: ""
                            val phoneNumber = doc.getString("phoneNumber") ?: ""
                            val photoUri = doc.getString("photoUri") ?: ""
                            val age = doc.getLong("age")?.toInt() ?: 0
                            val gender = doc.getString("gender") ?: "Male"
                            val areaName = doc.getString("areaName") ?: ""
                            val schoolName = doc.getString("schoolName") ?: ""
                            val active = doc.getBoolean("active") ?: true
                            val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()
                            val dob = doc.getString("dob") ?: ""
                            val notes = doc.getString("notes") ?: ""
                            val schoolClass = doc.getString("schoolClass") ?: ""

                            val localExisting = database.studentDao().getStudentById(studentId)
                            val finalPhotoUri = if (doc.contains("photoUri")) photoUri else (localExisting?.photoUri ?: "")

                            studentsList.add(
                                StudentEntity(
                                    studentId = studentId,
                                    studentName = studentName,
                                    fatherName = fatherName,
                                    motherName = motherName,
                                    classId = classId,
                                    phoneNumber = phoneNumber,
                                    photoUri = finalPhotoUri,
                                    age = age,
                                    gender = gender,
                                    areaName = areaName,
                                    schoolName = schoolName,
                                    active = active,
                                    createdTimestamp = createdTimestamp,
                                    dob = dob,
                                    notes = notes,
                                    schoolClass = schoolClass
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e("FirestoreSyncManager", "Error parsing student during pull-to-refresh: ${ex.message}")
                        }
                    }
                    if (studentsList.isNotEmpty()) {
                        database.studentDao().insertStudents(studentsList)
                        studentCount = studentsList.size
                    }
                }
            } catch (se: Exception) {
                Log.e("FirestoreSyncManager", "Fetch students failed: ${se.message}")
            }

            // 2. Fetch Attendance Records
            try {
                val attSnapshots = db.collection("attendance_records").get().await()
                if (!attSnapshots.isEmpty) {
                    val records = mutableListOf<AttendanceRecordEntity>()
                    for (doc in attSnapshots.documents) {
                        try {
                            val attendanceId = doc.getString("attendanceId") ?: doc.id
                            val studentId = doc.getString("studentId") ?: ""
                            val studentName = doc.getString("studentName") ?: ""
                            val classId = doc.getString("classId") ?: ""
                            val className = doc.getString("className") ?: ""
                            val date = doc.getString("date") ?: ""
                            val statusStr = doc.getString("status") ?: "PRESENT"
                            val status = try { AttendanceStatus.valueOf(statusStr) } catch (_: Exception) { AttendanceStatus.PRESENT }
                            val remark = doc.getString("remark") ?: ""
                            val teacherId = doc.getString("teacherId") ?: ""
                            val teacherName = doc.getString("teacherName") ?: ""
                            val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()
                            val lastModifiedTimestamp = doc.getLong("lastModifiedTimestamp") ?: System.currentTimeMillis()

                            val photoUrl = doc.getString("photoUrl") ?: doc.getString("photoUri") ?: ""
                            val photoStoragePath = doc.getString("photoStoragePath") ?: ""
                            if (classId.isNotBlank() && date.isNotBlank() && (photoUrl.isNotBlank() || photoStoragePath.isNotBlank())) {
                                ClassPhotoManager.onAttendancePhotoReceived(context, classId, date, photoUrl, photoStoragePath)
                            }

                            records.add(
                                AttendanceRecordEntity(
                                    attendanceId = attendanceId,
                                    studentId = studentId,
                                    studentName = studentName,
                                    classId = classId,
                                    className = className,
                                    date = date,
                                    status = status,
                                    remark = remark,
                                    teacherId = teacherId,
                                    teacherName = teacherName,
                                    createdTimestamp = createdTimestamp,
                                    lastModifiedTimestamp = lastModifiedTimestamp
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e("FirestoreSyncManager", "Error parsing attendance doc during pull-to-refresh: ${ex.message}")
                        }
                    }
                    if (records.isNotEmpty()) {
                        database.attendanceDao().insertAttendanceRecords(records)
                        attendanceCount = records.size
                    }
                }
            } catch (ae: Exception) {
                Log.e("FirestoreSyncManager", "Fetch attendance records failed: ${ae.message}")
            }

            // 3. Fetch Classes
            try {
                val classSnapshots = db.collection("classes").get().await()
                if (!classSnapshots.isEmpty) {
                    val classesList = mutableListOf<ClassEntity>()
                    for (doc in classSnapshots.documents) {
                        try {
                            val classId = doc.getString("classId") ?: doc.id
                            val className = doc.getString("className") ?: "Class"
                            val roomOrLocation = doc.getString("roomOrLocation") ?: ""
                            val primaryTeacherId = doc.getString("primaryTeacherId") ?: ""
                            val primaryTeacherName = doc.getString("primaryTeacherName") ?: ""
                            val active = doc.getBoolean("active") ?: true
                            val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()

                            val existingLocal = database.classDao().getClassById(classId)
                            val finalTeacherName = if (primaryTeacherName.isNotBlank()) primaryTeacherName else (existingLocal?.primaryTeacherName ?: "")
                            val finalTeacherId = if (primaryTeacherId.isNotBlank()) primaryTeacherId else (existingLocal?.primaryTeacherId ?: "")

                            classesList.add(
                                ClassEntity(
                                    classId = classId,
                                    className = className,
                                    roomOrLocation = roomOrLocation,
                                    primaryTeacherId = finalTeacherId,
                                    primaryTeacherName = finalTeacherName,
                                    active = active,
                                    createdTimestamp = createdTimestamp
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e("FirestoreSyncManager", "Error parsing class during pull-to-refresh: ${ex.message}")
                        }
                    }
                    if (classesList.isNotEmpty()) {
                        database.classDao().insertClasses(classesList)
                        classCount = classesList.size
                    }
                }
            } catch (ce: Exception) {
                Log.e("FirestoreSyncManager", "Fetch classes failed: ${ce.message}")
            }

            // 4. Fetch Tests and Test Marks
            try {
                val testsSnap = db.collection("class_tests").get().await()
                if (!testsSnap.isEmpty) {
                    val tests = mutableListOf<com.example.data.entity.ClassTestEntity>()
                    for (doc in testsSnap.documents) {
                        try {
                            tests.add(
                                com.example.data.entity.ClassTestEntity(
                                    testId = doc.getString("testId") ?: doc.id,
                                    classId = doc.getString("classId") ?: "",
                                    className = doc.getString("className") ?: "",
                                    testTitle = doc.getString("testTitle") ?: "",
                                    subject = doc.getString("subject") ?: "",
                                    testDate = doc.getString("testDate") ?: "",
                                    totalMarks = doc.getDouble("totalMarks") ?: 20.0,
                                    passingMarks = doc.getDouble("passingMarks") ?: 0.0,
                                    teacherId = doc.getString("teacherId") ?: "",
                                    teacherName = doc.getString("teacherName") ?: "",
                                    createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis(),
                                    lastModifiedTimestamp = doc.getLong("lastModifiedTimestamp") ?: System.currentTimeMillis()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    if (tests.isNotEmpty()) {
                        database.testExamDao().insertTests(tests)
                    }
                }
                val marksSnap = db.collection("student_test_marks").get().await()
                if (!marksSnap.isEmpty) {
                    val marks = mutableListOf<com.example.data.entity.StudentTestMarksEntity>()
                    for (doc in marksSnap.documents) {
                        try {
                            marks.add(
                                com.example.data.entity.StudentTestMarksEntity(
                                    markId = doc.getString("markId") ?: doc.id,
                                    testId = doc.getString("testId") ?: "",
                                    studentId = doc.getString("studentId") ?: "",
                                    studentName = doc.getString("studentName") ?: "",
                                    classId = doc.getString("classId") ?: "",
                                    obtainedMarks = doc.getDouble("obtainedMarks"),
                                    isAbsent = doc.getBoolean("isAbsent") ?: false,
                                    remark = doc.getString("remark") ?: "",
                                    lastModifiedTimestamp = doc.getLong("lastModifiedTimestamp") ?: System.currentTimeMillis()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                    if (marks.isNotEmpty()) {
                        database.testExamDao().insertMarks(marks)
                    }
                }
            } catch (te: Exception) {
                Log.e("FirestoreSyncManager", "Fetch tests/marks failed: ${te.message}")
            }

            _syncStatusMessage.value = "Synced with Firestore"
            val message = "Synced $studentCount students & $attendanceCount attendance records"
            Log.d("FirestoreSyncManager", "forceSyncLatestFromFirestore successful: $message")
            Result.success(message)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Manual force-sync failed: ${e.message}")
            _syncStatusMessage.value = "Sync error: ${e.message}"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Initial one-click cloud backup: Seeds existing local database items to Cloud Firestore if cloud is empty.
     */
    suspend fun syncAllLocalToCloud() = withContext(Dispatchers.IO) {
        if (!canWriteToCloud()) {
            Log.w("FirestoreSyncManager", "syncAllLocalToCloud blocked: App version unsupported")
            return@withContext
        }
        val db = firestore ?: return@withContext
        _isSyncing.value = true
        try {
            // Push users
            val users = database.userDao().getUserById("USR_ADMIN_1")
            // Push all classes
            val classes = database.classDao().getAllClassesDirect()
            for (c in classes) {
                pushClass(c)
            }
            // Push all students ONLY if cloud students collection is empty.
            // This prevents stale local database state on startup from resurrecting deleted students or overwriting edits made on other devices.
            val stuSnap = db.collection("students").limit(1).get().await()
            if (stuSnap.isEmpty) {
                val students = database.studentDao().getAllStudentsDirect()
                for (s in students) {
                    pushStudent(s)
                }
                Log.d("FirestoreSyncManager", "Seeded ${students.size} local students to empty cloud")
            } else {
                Log.d("FirestoreSyncManager", "Cloud students collection is not empty; skipping startup overwrite from local DB")
            }
            // Push all attendance ONLY if cloud attendance collection is empty.
            // This prevents stale local database state on startup from overwriting newer edits made on other devices.
            val attSnap = db.collection("attendance_records").limit(1).get().await()
            if (attSnap.isEmpty) {
                val attendance = database.attendanceDao().getAllAttendanceRecordsDirect()
                if (attendance.isNotEmpty()) {
                    pushAttendanceRecords(attendance)
                    Log.d("FirestoreSyncManager", "Seeded ${attendance.size} local attendance records to empty cloud")
                }
            } else {
                Log.d("FirestoreSyncManager", "Cloud attendance collection is not empty; skipping startup overwrite from local DB")
            }
            // Push educators
            val educatorsList = com.example.data.repository.EducatorManager.educators.value
            for (edu in educatorsList) {
                pushEducator(edu)
            }
            _syncStatusMessage.value = "Synced with Cloud"
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Manual sync failed: ${e.message}")
        } finally {
            _isSyncing.value = false
        }
    }

    fun cleanup() {
        classesListener?.remove()
        studentsListener?.remove()
        attendanceListener?.remove()
        usersListener?.remove()
    }
}
