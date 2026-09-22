package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DatabaseInitializer
import com.example.data.dao.UserDao
import com.example.data.entity.UserEntity
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val userDao: UserDao,
    private val database: AppDatabase? = null,
    private val firestoreSyncManager: FirestoreSyncManager? = null,
    private val context: Context? = null
) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("jaiti_auth_session", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Always starts in restoring state so UI shows SplashScreen rather than briefly flickering to LoginScreen
    private val _isSessionRestoring = MutableStateFlow<Boolean>(true)
    val isSessionRestoring: StateFlow<Boolean> = _isSessionRestoring.asStateFlow()

    init {
        // Immediately load synchronous cached profile from SharedPreferences if available
        val cached = getCachedUser()
        if (cached != null && cached.active) {
            _currentUser.value = cached
            _isSessionRestoring.value = false
            Log.d("AuthRepository", "Loaded cached session instantly on init: ${cached.fullName}")
        }
        // Automatically restore saved user session on app launch
        restoreSession()
    }

    private fun restoreSession() {
        val savedUserId = prefs?.getString(KEY_SAVED_USER_ID, null)

        // Fast path: Immediately load synchronous cached profile from SharedPreferences if available
        val cachedUser = getCachedUser()
        if (cachedUser != null && cachedUser.active) {
            _currentUser.value = cachedUser
            _isSessionRestoring.value = false
            Log.d("AuthRepository", "Loaded cached session instantly for: ${cachedUser.fullName}")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure cached user exists in local Room DB
                if (cachedUser != null && cachedUser.active) {
                    val local = userDao.getUserById(cachedUser.userId)
                    if (local == null) {
                        userDao.insertUser(cachedUser)
                    }
                }

                var user: UserEntity? = null

                if (savedUserId != null) {
                    // 1. Look up fresh data in local Room DB
                    user = userDao.getUserById(savedUserId)
                    if (user == null) {
                        // Try by username or email fallback
                        user = userDao.getUserByUsernameOrEmail(savedUserId)
                    }

                    // 2. If not found locally, try pulling from Firestore
                    if (user == null) {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val doc = db.collection("users").document(savedUserId).get().await()
                            if (doc.exists()) {
                                val userId = doc.getString("userId") ?: doc.id
                                val username = doc.getString("username") ?: ""
                                val passwordHash = doc.getString("passwordHash") ?: ""
                                val fullName = doc.getString("fullName") ?: "User"
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

                                val restored = UserEntity(
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
                                userDao.insertUser(restored)
                                user = restored
                            }
                        } catch (ex: Exception) {
                            Log.w("AuthRepository", "Failed to restore user from Firestore: ${ex.message}")
                        }
                    }
                }

                // If no saved user session exists (e.g. user logged out), remain logged out
                if (user == null && cachedUser == null) {
                    _currentUser.value = null
                    Log.d("AuthRepository", "No active session found; user remains logged out")
                }

                if (user != null && user.active) {
                    _currentUser.value = user
                    cacheUser(user)
                    Log.d("AuthRepository", "Restored active session for user: ${user.fullName} (${user.userId})")
                } else if (user != null && !user.active) {
                    // Explicitly deactivated by admin
                    clearSession()
                    _currentUser.value = null
                } else if (user == null && cachedUser != null && cachedUser.active) {
                    // Keep user logged in with cached credentials; do not wipe on network errors or startup delay
                    _currentUser.value = cachedUser
                    userDao.insertUser(cachedUser)
                    Log.d("AuthRepository", "Preserved session from cache: ${cachedUser.fullName}")
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Error restoring user session: ${e.message}")
                if (cachedUser != null && cachedUser.active) {
                    _currentUser.value = cachedUser
                }
            } finally {
                _isSessionRestoring.value = false
            }
        }
    }

    private fun cacheUser(user: UserEntity) {
        try {
            prefs?.edit()?.apply {
                putString(KEY_SAVED_USER_ID, user.userId)
                putString(KEY_CACHED_USERNAME, user.username)
                putString(KEY_CACHED_FULL_NAME, user.fullName)
                putString(KEY_CACHED_ROLE, user.role.name)
                putString(KEY_CACHED_EMAIL, user.email)
                putString(KEY_CACHED_PHONE, user.phone)
                putBoolean(KEY_CACHED_ACTIVE, user.active)
                putString(KEY_CACHED_ASSIGNED_CLASSES, user.assignedClassIdsCsv)
                putBoolean(KEY_CACHED_CAN_ATTENDANCE, user.canTakeAllAttendance)
                putBoolean(KEY_CACHED_CAN_STUDENTS, user.canManageStudents)
                putBoolean(KEY_CACHED_CAN_CLASSES, user.canManageClasses)
                putBoolean(KEY_CACHED_CAN_REPORTS, user.canViewReports)
                putString(KEY_CACHED_COLLAB_STATUS, user.collaborationStatus)
                apply()
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to cache user: ${e.message}")
        }
    }

    private fun getCachedUser(): UserEntity? {
        val userId = prefs?.getString(KEY_SAVED_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_CACHED_USERNAME, null) ?: return null
        val fullName = prefs.getString(KEY_CACHED_FULL_NAME, "User") ?: "User"
        val roleStr = prefs.getString(KEY_CACHED_ROLE, "TEACHER") ?: "TEACHER"
        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.TEACHER }
        val email = prefs.getString(KEY_CACHED_EMAIL, "") ?: ""
        val phone = prefs.getString(KEY_CACHED_PHONE, "") ?: ""
        val active = prefs.getBoolean(KEY_CACHED_ACTIVE, true)
        val assignedClassIdsCsv = prefs.getString(KEY_CACHED_ASSIGNED_CLASSES, "") ?: ""
        val canTakeAllAttendance = prefs.getBoolean(KEY_CACHED_CAN_ATTENDANCE, true)
        val canManageStudents = prefs.getBoolean(KEY_CACHED_CAN_STUDENTS, true)
        val canManageClasses = prefs.getBoolean(KEY_CACHED_CAN_CLASSES, true)
        val canViewReports = prefs.getBoolean(KEY_CACHED_CAN_REPORTS, true)
        val collabStatus = prefs.getString(KEY_CACHED_COLLAB_STATUS, "APPROVED") ?: "APPROVED"

        return UserEntity(
            userId = userId,
            username = username,
            passwordHash = "",
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
            collaborationStatus = collabStatus
        )
    }

    private fun saveSession(user: UserEntity) {
        try {
            prefs?.edit()?.putString(KEY_SAVED_USER_ID, user.userId)?.apply()
            cacheUser(user)
            if (context != null) {
                ActiveDeviceSessionManager.registerSession(context, user)
            }
            Log.d("AuthRepository", "Saved session and cached user: ${user.userId}")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to save session: ${e.message}")
        }
    }

    private fun saveSession(userId: String) {
        try {
            prefs?.edit()?.putString(KEY_SAVED_USER_ID, userId)?.apply()
            Log.d("AuthRepository", "Saved session for user: $userId")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to save session: ${e.message}")
        }
    }

    private fun clearSession() {
        try {
            prefs?.edit()?.clear()?.apply()
            Log.d("AuthRepository", "Cleared saved user session and cache")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to clear session: ${e.message}")
        }
    }

    suspend fun login(usernameOrEmailInput: String, passwordInput: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        val trimmedInput = usernameOrEmailInput.trim()
        val trimmedPass = passwordInput.trim()

        if (trimmedInput.isEmpty() || trimmedPass.isEmpty()) {
            return@withContext Result.failure(Exception("Please enter both Email/Username and Password"))
        }

        // 1. Check local Room database first (by username or email)
        var user = userDao.getUserByUsernameOrEmail(trimmedInput)

        // 2. If not found locally, check Firebase Firestore Cloud
        if (user == null) {
            try {
                val db = FirebaseFirestore.getInstance()
                // Check by username
                val queryUser = db.collection("users")
                    .whereEqualTo("username", trimmedInput)
                    .limit(1)
                    .get()
                    .await()

                val doc = if (!queryUser.isEmpty) {
                    queryUser.documents.first()
                } else {
                    // Check by email
                    val queryEmail = db.collection("users")
                        .whereEqualTo("email", trimmedInput)
                        .limit(1)
                        .get()
                        .await()
                    if (!queryEmail.isEmpty) queryEmail.documents.first() else null
                }

                if (doc != null) {
                    val userId = doc.getString("userId") ?: doc.id
                    val username = doc.getString("username") ?: trimmedInput
                    val passwordHash = doc.getString("passwordHash") ?: ""
                    val fullName = doc.getString("fullName") ?: "User"
                    val roleStr = doc.getString("role") ?: "ADMIN"
                    val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.ADMIN }
                    val email = doc.getString("email") ?: trimmedInput
                    val phone = doc.getString("phone") ?: ""
                    val active = doc.getBoolean("active") ?: true
                    val assignedClassIdsCsv = doc.getString("assignedClassIdsCsv") ?: "ALL"

                    val isMasterAdmin = email.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
                            username.equals("jaitifoundation@gmail.com", ignoreCase = true)

                    val cloudUser = UserEntity(
                        userId = userId,
                        username = username,
                        passwordHash = passwordHash,
                        fullName = fullName,
                        role = if (isMasterAdmin) Role.ADMIN else role,
                        email = email,
                        phone = phone,
                        active = active,
                        assignedClassIdsCsv = if (isMasterAdmin) "ALL" else assignedClassIdsCsv,
                        canTakeAllAttendance = isMasterAdmin || (doc.getBoolean("canTakeAllAttendance") ?: true),
                        canManageStudents = isMasterAdmin || (doc.getBoolean("canManageStudents") ?: true),
                        canManageClasses = isMasterAdmin || (doc.getBoolean("canManageClasses") ?: true),
                        canViewReports = isMasterAdmin || (doc.getBoolean("canViewReports") ?: true),
                        collaborationStatus = if (isMasterAdmin) "APPROVED" else (doc.getString("collaborationStatus") ?: "APPROVED")
                    )
                    userDao.insertUser(cloudUser)
                    user = cloudUser
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firestore cloud login check skipped: ${e.message}")
            }
        }

        val isMasterAdminLogin = trimmedInput.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
                trimmedInput.equals("jaitifoundation", ignoreCase = true)

        // Permanent master admin safeguard: If logging in as master admin with credentials, ensure account exists and is restored
        if (isMasterAdminLogin && trimmedPass == "Admin@123") {
            if (user == null || !user.active || user.passwordHash != "Admin@123") {
                val restoredAdmin = UserEntity(
                    userId = user?.userId ?: "USR_MASTER_ADMIN_JAITI",
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
                userDao.insertUser(restoredAdmin)
                user = restoredAdmin

                try {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users").document(restoredAdmin.userId).set(
                        hashMapOf(
                            "userId" to restoredAdmin.userId,
                            "username" to restoredAdmin.username,
                            "passwordHash" to restoredAdmin.passwordHash,
                            "fullName" to restoredAdmin.fullName,
                            "role" to "ADMIN",
                            "email" to restoredAdmin.email,
                            "phone" to restoredAdmin.phone,
                            "active" to true,
                            "assignedClassIdsCsv" to "ALL",
                            "canTakeAllAttendance" to true,
                            "canManageStudents" to true,
                            "canManageClasses" to true,
                            "canViewReports" to true,
                            "collaborationStatus" to "APPROVED"
                        )
                    ).await()
                } catch (ex: Exception) {
                    Log.w("AuthRepository", "Firestore sync master admin note: ${ex.message}")
                }
            }
        }

        if (user == null) {
            return@withContext Result.failure(Exception("Account not found. Please Sign Up to create your account."))
        }

        if (!user.active) {
            return@withContext Result.failure(Exception("This account is currently deactivated."))
        }

        if (user.passwordHash != trimmedPass) {
            return@withContext Result.failure(Exception("Incorrect password. Please try again."))
        }

        _currentUser.value = user
        saveSession(user)
        return@withContext Result.success(user)
    }

    suspend fun signUp(
        fullName: String,
        email: String,
        phone: String,
        passwordInput: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        val trimmedName = fullName.trim()
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()
        val trimmedPassword = passwordInput.trim()

        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(Exception("Please enter your full name"))
        }
        if (trimmedEmail.isEmpty()) {
            return@withContext Result.failure(Exception("Please enter your email or username"))
        }
        if (trimmedPassword.length < 4) {
            return@withContext Result.failure(Exception("Password must be at least 4 characters"))
        }

        val isMasterAdmin = trimmedEmail.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
                trimmedEmail.equals("admin@jaiti.in", ignoreCase = true)

        // Check if user already exists in local DB
        val existing = userDao.getUserByUsernameOrEmail(trimmedEmail)
        if (existing != null) {
            if (isMasterAdmin || existing.userId.startsWith("USR_MASTER_ADMIN") || existing.userId.startsWith("USR_ADMIN_")) {
                // Allow Master Admin to update password & details on Sign Up
                val updatedAdmin = existing.copy(
                    passwordHash = trimmedPassword,
                    fullName = if (trimmedName.isNotBlank()) trimmedName else existing.fullName,
                    phone = if (trimmedPhone.isNotBlank()) trimmedPhone else existing.phone,
                    role = Role.ADMIN,
                    email = trimmedEmail,
                    assignedClassIdsCsv = "ALL",
                    canTakeAllAttendance = true,
                    canManageStudents = true,
                    canManageClasses = true,
                    canViewReports = true,
                    collaborationStatus = "APPROVED",
                    active = true
                )
                userDao.insertUser(updatedAdmin)
                firestoreSyncManager?.pushUser(updatedAdmin)
                return@withContext Result.success(updatedAdmin)
            } else {
                return@withContext Result.failure(Exception("An account with this email/username already exists. Please Sign In."))
            }
        }

        val userId = if (isMasterAdmin) "USR_MASTER_ADMIN_JAITI" else "USR_${System.currentTimeMillis()}"
        val isWhitelisted = isMasterAdmin || EducatorManager.isPreRegistered(trimmedEmail, trimmedPhone, trimmedName)

        val newUser = UserEntity(
            userId = userId,
            username = trimmedEmail,
            passwordHash = trimmedPassword,
            fullName = trimmedName,
            role = if (isMasterAdmin) Role.ADMIN else Role.TEACHER,
            email = trimmedEmail,
            phone = trimmedPhone,
            active = true,
            assignedClassIdsCsv = if (isWhitelisted) "ALL" else "",
            canTakeAllAttendance = isWhitelisted,
            canManageStudents = isWhitelisted,
            canManageClasses = isWhitelisted,
            canViewReports = isWhitelisted,
            collaborationStatus = if (isWhitelisted) "APPROVED" else "PENDING"
        )

        // Save locally in Room
        userDao.insertUser(newUser)

        // Push to Firebase Firestore so other devices can log in immediately
        firestoreSyncManager?.pushUser(newUser)

        _currentUser.value = newUser
        saveSession(newUser)

        // If not pre-registered, notify Admin with a collaboration request
        if (!isWhitelisted) {
            try {
                CollaborationManager.sendCollaborationRequest(
                    senderName = trimmedName,
                    senderEmail = trimmedEmail,
                    centerCode = CollaborationManager.DEFAULT_CENTER_CODE,
                    role = "TEACHER"
                )
            } catch (e: Exception) {
                Log.w("AuthRepository", "Auto collaboration request failed: ${e.message}")
            }
        }

        return@withContext Result.success(newUser)
    }

    suspend fun updateTeacherPermissions(
        user: UserEntity,
        canTakeAllAttendance: Boolean,
        canManageStudents: Boolean,
        canManageClasses: Boolean,
        canViewReports: Boolean,
        collaborationStatus: String = "APPROVED"
    ): UserEntity = withContext(Dispatchers.IO) {
        val updated = user.copy(
            canTakeAllAttendance = canTakeAllAttendance,
            canManageStudents = canManageStudents,
            canManageClasses = canManageClasses,
            canViewReports = canViewReports,
            collaborationStatus = collaborationStatus
        )
        userDao.insertUser(updated)
        firestoreSyncManager?.pushUser(updated)
        if (_currentUser.value?.userId == user.userId) {
            _currentUser.value = updated
        }
        updated
    }

    val allUsers: kotlinx.coroutines.flow.Flow<List<UserEntity>> = userDao.getAllUsers()
    val pendingUsers: kotlinx.coroutines.flow.Flow<List<UserEntity>> = userDao.getPendingUsers()

    suspend fun approveUserRegistration(
        userId: String,
        canTakeAllAttendance: Boolean = true,
        canManageStudents: Boolean = true,
        canManageClasses: Boolean = true,
        canViewReports: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId) ?: return@withContext false
            val updated = user.copy(
                collaborationStatus = "APPROVED",
                canTakeAllAttendance = canTakeAllAttendance,
                canManageStudents = canManageStudents,
                canManageClasses = canManageClasses,
                canViewReports = canViewReports,
                active = true
            )
            userDao.insertUser(updated)
            firestoreSyncManager?.pushUser(updated)

            // Also update any pending CollaborationRequest in Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val reqDocs = db.collection("collaboration_requests")
                    .whereEqualTo("senderEmail", user.email)
                    .get()
                    .await()
                for (doc in reqDocs.documents) {
                    doc.reference.update("status", "APPROVED").await()
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Collaboration request sync note: ${e.message}")
            }

            if (_currentUser.value?.userId == userId) {
                _currentUser.value = updated
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error approving user $userId: ${e.message}")
            false
        }
    }

    suspend fun rejectUserRegistration(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId) ?: return@withContext false
            val updated = user.copy(
                collaborationStatus = "REJECTED",
                active = false
            )
            userDao.insertUser(updated)
            firestoreSyncManager?.pushUser(updated)

            // Also update any pending CollaborationRequest in Firestore
            try {
                val db = FirebaseFirestore.getInstance()
                val reqDocs = db.collection("collaboration_requests")
                    .whereEqualTo("senderEmail", user.email)
                    .get()
                    .await()
                for (doc in reqDocs.documents) {
                    doc.reference.update("status", "REJECTED").await()
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Collaboration request sync note: ${e.message}")
            }

            if (_currentUser.value?.userId == userId) {
                _currentUser.value = updated
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error rejecting user $userId: ${e.message}")
            false
        }
    }

    suspend fun toggleUserActiveStatus(userId: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId) ?: return@withContext false
            val updated = user.copy(
                active = active,
                collaborationStatus = if (active) "APPROVED" else "DEACTIVATED"
            )
            userDao.insertUser(updated)
            firestoreSyncManager?.pushUser(updated)

            if (!active && _currentUser.value?.userId == userId) {
                _currentUser.value = null
                clearSession()
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error toggling active status for user $userId: ${e.message}")
            false
        }
    }

    suspend fun deleteUserAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserById(userId)
            val userEmail = user?.email ?: ""

            userDao.deleteUser(userId)
            firestoreSyncManager?.deleteUser(userId)

            // Also purge any pending/past collaboration requests for this email in Firestore
            if (userEmail.isNotBlank()) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val reqDocs = db.collection("collaboration_requests")
                        .whereEqualTo("senderEmail", userEmail)
                        .get()
                        .await()
                    for (doc in reqDocs.documents) {
                        doc.reference.delete().await()
                        Log.d("AuthRepository", "Deleted collaboration request for $userEmail")
                    }
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Failed to cleanup collaboration requests on delete: ${e.message}")
                }
            }

            if (_currentUser.value?.userId == userId) {
                _currentUser.value = null
                clearSession()
            }
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error deleting user $userId: ${e.message}")
            false
        }
    }

    /**
     * Purges all non-admin users, demo teachers, and collaboration requests
     * from both the local Room database and Cloud Firestore.
     * Keeps ONLY the Master/Super Admin account untouched.
     */
    suspend fun purgeAllNonAdminUsers(): Boolean = withContext(Dispatchers.IO) {
        try {
            val allLocalUsers = userDao.getAllUsersDirect()
            val nonAdminUsers = allLocalUsers.filter { !it.isMasterAdmin && it.role != Role.ADMIN }

            // 1. Delete each non-admin user locally & from Cloud
            for (user in nonAdminUsers) {
                userDao.deleteUser(user.userId)
                firestoreSyncManager?.deleteUser(user.userId)
            }

            // 2. Also wipe any stray documents in Firestore 'users' collection where role != 'ADMIN'
            try {
                val db = FirebaseFirestore.getInstance()
                val firestoreUsers = db.collection("users").get().await()
                for (doc in firestoreUsers.documents) {
                    val role = doc.getString("role") ?: ""
                    val isMaster = doc.getBoolean("isMasterAdmin") ?: false
                    val email = doc.getString("email") ?: ""
                    if (!isMaster && !role.equals("ADMIN", ignoreCase = true) && !email.contains("admin", ignoreCase = true)) {
                        doc.reference.delete().await()
                        Log.d("AuthRepository", "Purged cloud user document: ${doc.id} ($email)")
                    }
                }

                // 3. Clear all collaboration_requests in Firestore
                val collabRequests = db.collection("collaboration_requests").get().await()
                for (doc in collabRequests.documents) {
                    doc.reference.delete().await()
                    Log.d("AuthRepository", "Purged cloud collaboration_request: ${doc.id}")
                }
            } catch (cloudEx: Exception) {
                Log.w("AuthRepository", "Cloud purge notice: ${cloudEx.message}")
            }

            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to purge all non-admin users: ${e.message}")
            false
        }
    }

    suspend fun syncUsersWithCloud() = withContext(Dispatchers.IO) {
        firestoreSyncManager?.fetchAndSyncUsersNow()
    }

    fun logout() {
        val user = _currentUser.value
        if (context != null && user != null) {
            ActiveDeviceSessionManager.unregisterSession(context, user)
        }
        _currentUser.value = null
        clearSession()
    }

    suspend fun refreshCurrentUser() {
        val curr = _currentUser.value ?: return
        val updated = userDao.getUserById(curr.userId)
        if (updated != null) {
            _currentUser.value = updated
        }
    }

    companion object {
        private const val KEY_SAVED_USER_ID = "saved_logged_in_user_id"
        private const val KEY_CACHED_USERNAME = "cached_username"
        private const val KEY_CACHED_FULL_NAME = "cached_full_name"
        private const val KEY_CACHED_ROLE = "cached_role"
        private const val KEY_CACHED_EMAIL = "cached_email"
        private const val KEY_CACHED_PHONE = "cached_phone"
        private const val KEY_CACHED_ACTIVE = "cached_active"
        private const val KEY_CACHED_ASSIGNED_CLASSES = "cached_assigned_classes"
        private const val KEY_CACHED_CAN_ATTENDANCE = "cached_can_attendance"
        private const val KEY_CACHED_CAN_STUDENTS = "cached_can_students"
        private const val KEY_CACHED_CAN_CLASSES = "cached_can_classes"
        private const val KEY_CACHED_CAN_REPORTS = "cached_can_reports"
        private const val KEY_CACHED_COLLAB_STATUS = "cached_collab_status"
    }
}
