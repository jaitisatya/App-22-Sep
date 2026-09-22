package com.example.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entity.UserEntity
import com.example.data.model.ActiveDeviceSession
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ActiveDeviceSessionManager {
    private const val TAG = "ActiveDeviceSessionMgr"
    private const val PREFS_NAME = "jaiti_active_device_sessions_prefs"
    private const val KEY_SESSIONS = "key_cached_sessions"
    private const val KEY_DEVICE_ID = "key_unique_device_id"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine error safely handled: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val _activeSessions = MutableStateFlow<List<ActiveDeviceSession>>(emptyList())
    val activeSessions: StateFlow<List<ActiveDeviceSession>> = _activeSessions.asStateFlow()

    private var allSessionsListener: ListenerRegistration? = null
    private var currentSessionListener: ListenerRegistration? = null
    private var isListening = false

    /**
     * Retrieves a stable, permanent device identifier.
     * Uses Android hardware Settings.Secure.ANDROID_ID first so that emulator/phone rebuilds
     * and app reinstalls consistently identify the exact same physical/virtual device.
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            } catch (_: Exception) { null }

            id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                val hwFingerprint = "${Build.BRAND}_${Build.MODEL}_${Build.HARDWARE}".replace(" ", "_")
                hwFingerprint.ifBlank { UUID.randomUUID().toString().replace("-", "").take(10) }
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    /**
     * Formats real phone model from Android hardware properties (e.g. Build.MANUFACTURER + Build.MODEL).
     * Strictly reads actual device information without hardcoded simulator names.
     */
    fun getFormattedDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.trim()?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } ?: ""

        val rawModel = Build.MODEL?.trim() ?: ""

        val model = when {
            rawModel.isNotBlank() -> rawModel
            !Build.DEVICE.isNullOrBlank() -> Build.DEVICE.trim()
            else -> "Android Device"
        }

        return when {
            manufacturer.isBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    /**
     * Format timestamp to readable string: e.g. "Today at 10:30 AM" or "12 Sep, 04:15 PM"
     */
    fun formatSessionTime(timestamp: Long): String {
        if (timestamp <= 0) return "Recently"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val dayInMillis = 24 * 60 * 60 * 1000L

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestamp))

        return when {
            diff < 2 * 60 * 1000L -> "Active just now"
            diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}m ago ($timeStr)"
            diff < dayInMillis -> "Today at $timeStr"
            diff < 2 * dayInMillis -> "Yesterday at $timeStr"
            else -> {
                val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }

    /**
     * Registers or refreshes current device's active session in Firestore and local storage.
     * Automatically supersedes and purges older duplicate sessions for the same user/email.
     */
    fun registerSession(context: Context, user: UserEntity) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }

                val deviceId = getDeviceId(appContext)
                val sessionId = "${user.userId}_$deviceId"
                val deviceModel = getFormattedDeviceModel()
                val now = System.currentTimeMillis()

                val session = ActiveDeviceSession(
                    sessionId = sessionId,
                    userId = user.userId,
                    userName = user.fullName,
                    userEmail = user.email,
                    userPhone = user.phone,
                    userRole = user.role.name,
                    deviceModel = deviceModel,
                    deviceId = deviceId,
                    loginTime = now,
                    lastActiveTime = now,
                    status = "ACTIVE"
                )

                // 1. Update local cache (remove any prior session for this user to avoid duplicates)
                val currentList = _activeSessions.value.toMutableList()
                currentList.removeAll {
                    it.sessionId == sessionId ||
                    (user.email.isNotBlank() && it.userEmail.equals(user.email, ignoreCase = true)) ||
                    (user.userId.isNotBlank() && it.userId == user.userId)
                }
                currentList.add(0, session)
                _activeSessions.value = currentList
                saveSessionsToLocalStorage(appContext, currentList)

                // 2. Push to Firestore
                val db = FirebaseFirestore.getInstance()
                val map = hashMapOf(
                    "sessionId" to sessionId,
                    "userId" to user.userId,
                    "userName" to user.fullName,
                    "userEmail" to user.email,
                    "userPhone" to user.phone,
                    "userRole" to user.role.name,
                    "deviceModel" to deviceModel,
                    "deviceId" to deviceId,
                    "loginTime" to now,
                    "lastActiveTime" to now,
                    "status" to "ACTIVE"
                )

                db.collection("active_sessions")
                    .document(sessionId)
                    .set(map, SetOptions.merge())
                    .await()

                // Deduplicate: Clean up any older duplicate session documents in Firestore for this email or userId
                try {
                    val query = if (user.email.isNotBlank()) {
                        db.collection("active_sessions").whereEqualTo("userEmail", user.email).get().await()
                    } else {
                        db.collection("active_sessions").whereEqualTo("userId", user.userId).get().await()
                    }
                    for (oldDoc in query.documents) {
                        if (oldDoc.id != sessionId) {
                            oldDoc.reference.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Deduplication note: ${e.message}")
                }

                Log.d(TAG, "Registered active session for ${user.fullName} on $deviceModel (sessionId=$sessionId)")
            } catch (e: Exception) {
                Log.w(TAG, "Error registering session: ${e.message}")
            }
        }
    }

    /**
     * Unregisters or deletes active session when user logs out.
     */
    fun unregisterSession(context: Context, user: UserEntity) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }
                val deviceId = getDeviceId(appContext)
                val sessionId = "${user.userId}_$deviceId"
                val db = FirebaseFirestore.getInstance()

                try {
                    db.collection("active_sessions")
                        .document(sessionId)
                        .delete()
                        .await()
                } catch (_: Exception) {}

                val updatedList = _activeSessions.value.filter { it.sessionId != sessionId }
                _activeSessions.value = updatedList
                saveSessionsToLocalStorage(appContext, updatedList)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering session: ${e.message}")
            }
        }
    }

    /**
     * Starts listening to all active device sessions across the entire system for Admin view.
     * Automatically deduplicates any multiple sessions belonging to the same user.
     */
    fun startListeningAllSessions(context: Context) {
        if (isListening && allSessionsListener != null) return
        isListening = true
        val appContext = context.applicationContext

        // Load local cache first
        loadSessionsFromLocalStorage(appContext)

        scope.launch {
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }

                val db = FirebaseFirestore.getInstance()
                allSessionsListener?.remove()

                allSessionsListener = db.collection("active_sessions")
                    .addSnapshotListener { snapshots, error ->
                        if (error != null) {
                            Log.w(TAG, "Active sessions cloud listen error: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshots != null) {
                            val list = mutableListOf<ActiveDeviceSession>()
                            val docsToDelete = mutableListOf<String>()

                            for (doc in snapshots.documents) {
                                try {
                                    val status = doc.getString("status") ?: "ACTIVE"
                                    if (status.equals("DELETED", ignoreCase = true)) {
                                        docsToDelete.add(doc.id)
                                        continue
                                    }
                                    val sessionId = doc.getString("sessionId") ?: doc.id
                                    val userId = doc.getString("userId") ?: ""
                                    val userName = doc.getString("userName") ?: "Teacher"
                                    val userEmail = doc.getString("userEmail") ?: ""
                                    val userPhone = doc.getString("userPhone") ?: ""
                                    val userRole = doc.getString("userRole") ?: "TEACHER"
                                    val rawDeviceModel = doc.getString("deviceModel") ?: "Android Device"
                                    val deviceModel = if (rawDeviceModel.contains("Virtual Device", ignoreCase = true) || rawDeviceModel.contains("Google Pixel 7 Pro", ignoreCase = true)) {
                                        getFormattedDeviceModel()
                                    } else {
                                        rawDeviceModel
                                    }
                                    val deviceId = doc.getString("deviceId") ?: ""
                                    val loginTime = doc.getLong("loginTime") ?: 0L
                                    val lastActiveTime = doc.getLong("lastActiveTime") ?: loginTime

                                    list.add(
                                        ActiveDeviceSession(
                                            sessionId = sessionId,
                                            userId = userId,
                                            userName = userName,
                                            userEmail = userEmail,
                                            userPhone = userPhone,
                                            userRole = userRole,
                                            deviceModel = deviceModel,
                                            deviceId = deviceId,
                                            loginTime = loginTime,
                                            lastActiveTime = lastActiveTime,
                                            status = status
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing session doc: ${e.message}")
                                }
                            }

                            // Deduplicate: Keep only the most recent session per userEmail or userId
                            val deduplicated = mutableListOf<ActiveDeviceSession>()
                            val grouped = list.groupBy {
                                val emailKey = it.userEmail.trim().lowercase()
                                if (emailKey.isNotBlank()) emailKey else it.userId
                            }

                            for ((_, userSessions) in grouped) {
                                if (userSessions.size == 1) {
                                    deduplicated.add(userSessions.first())
                                } else {
                                    // Sort by last active or login time descending
                                    val sorted = userSessions.sortedByDescending {
                                        it.lastActiveTime.coerceAtLeast(it.loginTime)
                                    }
                                    // Keep latest
                                    deduplicated.add(sorted.first())
                                    // Clean up all older duplicate documents from Firestore
                                    for (i in 1 until sorted.size) {
                                        docsToDelete.add(sorted[i].sessionId)
                                    }
                                }
                            }

                            // Clean up duplicate/deleted session documents in Firestore in background
                            if (docsToDelete.isNotEmpty()) {
                                scope.launch {
                                    for (docId in docsToDelete) {
                                        try {
                                            db.collection("active_sessions").document(docId).delete()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }

                            // Sort: ACTIVE first, then by last active timestamp descending
                            val sorted = deduplicated.sortedWith(
                                compareByDescending<ActiveDeviceSession> { it.isActive }
                                    .thenByDescending { it.lastActiveTime }
                            )

                            _activeSessions.value = sorted
                            saveSessionsToLocalStorage(appContext, sorted)
                            Log.d(TAG, "Fetched ${sorted.size} active device sessions (deduplicated ${docsToDelete.size} stale sessions)")
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "startListeningAllSessions init note: ${e.message}")
            }
        }
    }

    /**
     * Explicit cleanup function that purges any duplicate or stale sessions in Firestore.
     */
    suspend fun cleanupDuplicateAndStaleSessions(context: Context, onComplete: ((Int) -> Unit)? = null) {
        val appContext = context.applicationContext
        var cleanedCount = 0
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            val db = FirebaseFirestore.getInstance()
            val snapshots = db.collection("active_sessions").get().await()

            val grouped = snapshots.documents.groupBy {
                val email = it.getString("userEmail")?.trim()?.lowercase() ?: ""
                val uid = it.getString("userId")?.trim() ?: ""
                if (email.isNotBlank()) email else uid
            }

            for ((_, docs) in grouped) {
                if (docs.size > 1) {
                    val sorted = docs.sortedByDescending {
                        it.getLong("lastActiveTime") ?: it.getLong("loginTime") ?: 0L
                    }
                    for (i in 1 until sorted.size) {
                        try {
                            sorted[i].reference.delete().await()
                            cleanedCount++
                        } catch (_: Exception) {}
                    }
                }
            }

            // Also delete any with DELETED status
            for (doc in snapshots.documents) {
                if (doc.getString("status").equals("DELETED", ignoreCase = true)) {
                    try {
                        doc.reference.delete().await()
                        cleanedCount++
                    } catch (_: Exception) {}
                }
            }

            onComplete?.invoke(cleanedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning duplicate sessions: ${e.message}")
            onComplete?.invoke(cleanedCount)
        }
    }

    /**
     * Monitors current device's active session. If admin deactivates or deletes it,
     * this callback is triggered immediately.
     */
    fun startMonitoringCurrentSession(
        context: Context,
        userId: String,
        onSessionInvalidated: (reason: String, canRelogin: Boolean) -> Unit
    ) {
        val appContext = context.applicationContext
        val deviceId = getDeviceId(appContext)
        val sessionId = "${userId}_$deviceId"

        scope.launch {
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }

                val db = FirebaseFirestore.getInstance()
                currentSessionListener?.remove()

                currentSessionListener = db.collection("active_sessions")
                    .document(sessionId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null && snapshot.exists()) {
                            val status = snapshot.getString("status") ?: "ACTIVE"
                            if (status.equals("DEACTIVATED", ignoreCase = true)) {
                                onSessionInvalidated("Your session has been deactivated by administrator. You can sign in again.", true)
                            } else if (status.equals("DELETED", ignoreCase = true)) {
                                onSessionInvalidated("Your account access has been revoked by administrator.", false)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "startMonitoringCurrentSession error: ${e.message}")
            }
        }
    }

    /**
     * Deactivates a teacher's session:
     * - Marks session as DEACTIVATED in Firestore
     * - Deactivates user account (sets active = false)
     * - Forces teacher logout on that device
     * - Teacher can sign in again with their password anytime
     */
    suspend fun deactivateSession(
        context: Context,
        session: ActiveDeviceSession,
        onComplete: (Boolean) -> Unit
    ) {
        // Master Admin cannot be deactivated
        if (session.userEmail.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
            session.userRole.equals("ADMIN", ignoreCase = true)) {
            Log.w(TAG, "Attempted to deactivate Master Admin session blocked.")
            onComplete(false)
            return
        }

        val appContext = context.applicationContext
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            val db = FirebaseFirestore.getInstance()

            // 1. Update session status in Firestore
            db.collection("active_sessions")
                .document(session.sessionId)
                .update("status", "DEACTIVATED", "lastActiveTime", System.currentTimeMillis())
                .await()

            // 2. Update user status in Firestore
            try {
                db.collection("users")
                    .document(session.userId)
                    .update("active", false, "collaborationStatus", "DEACTIVATED")
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "User doc update on deactivate note: ${e.message}")
            }

            // 3. Update local DB
            val dbLocal = AppDatabase.getInstance(appContext)
            val localUser = dbLocal.userDao().getUserById(session.userId)
            if (localUser != null) {
                dbLocal.userDao().insertUser(
                    localUser.copy(active = false, collaborationStatus = "DEACTIVATED")
                )
            }

            // 4. Update in-memory state
            val updatedList = _activeSessions.value.map {
                if (it.sessionId == session.sessionId) it.copy(status = "DEACTIVATED") else it
            }
            _activeSessions.value = updatedList
            saveSessionsToLocalStorage(appContext, updatedList)

            onComplete(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating session: ${e.message}")
            onComplete(false)
        }
    }

    /**
     * Deletes a teacher's login access completely:
     * - Marks session as DELETED and removes document from Firestore
     * - Deletes user account from Firestore and local DB
     * - Forces teacher logout immediately
     * - If teacher is listed in Educator section (EducatorManager), they can sign up again and get pre-approved
     * - If NOT listed in Educator section, their signup will enter the "Pending" section for Admin approval
     */
    suspend fun deleteSessionAndUser(
        context: Context,
        session: ActiveDeviceSession,
        onComplete: (Boolean) -> Unit
    ) {
        // Master Admin cannot be deleted
        if (session.userEmail.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
            session.userRole.equals("ADMIN", ignoreCase = true)) {
            Log.w(TAG, "Attempted to delete Master Admin session blocked.")
            onComplete(false)
            return
        }

        val appContext = context.applicationContext
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            val db = FirebaseFirestore.getInstance()

            // 1. Notify device by setting status to DELETED first
            try {
                db.collection("active_sessions")
                    .document(session.sessionId)
                    .update("status", "DELETED")
                    .await()
            } catch (_: Exception) {}

            // 2. Delete active_sessions document
            try {
                db.collection("active_sessions")
                    .document(session.sessionId)
                    .delete()
                    .await()
            } catch (_: Exception) {}

            // 3. Delete user document from Firestore
            try {
                db.collection("users")
                    .document(session.userId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Delete user doc from Firestore note: ${e.message}")
            }

            // 4. Remove any pending collaboration requests for this email
            if (session.userEmail.isNotBlank()) {
                try {
                    val reqDocs = db.collection("collaboration_requests")
                        .whereEqualTo("senderEmail", session.userEmail)
                        .get()
                        .await()
                    for (doc in reqDocs.documents) {
                        doc.reference.delete().await()
                    }
                } catch (_: Exception) {}
            }

            // 5. Delete from local Room DB
            val dbLocal = AppDatabase.getInstance(appContext)
            dbLocal.userDao().deleteUser(session.userId)

            // 6. Update in-memory state
            val updatedList = _activeSessions.value.filter { it.sessionId != session.sessionId }
            _activeSessions.value = updatedList
            saveSessionsToLocalStorage(appContext, updatedList)

            onComplete(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session and user: ${e.message}")
            onComplete(false)
        }
    }

    private fun saveSessionsToLocalStorage(context: Context, list: List<ActiveDeviceSession>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("sessionId", item.sessionId)
                obj.put("userId", item.userId)
                obj.put("userName", item.userName)
                obj.put("userEmail", item.userEmail)
                obj.put("userPhone", item.userPhone)
                obj.put("userRole", item.userRole)
                obj.put("deviceModel", item.deviceModel)
                obj.put("deviceId", item.deviceId)
                obj.put("loginTime", item.loginTime)
                obj.put("lastActiveTime", item.lastActiveTime)
                obj.put("status", item.status)
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSIONS, array.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sessions locally: ${e.message}")
        }
    }

    private fun loadSessionsFromLocalStorage(context: Context) {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ActiveDeviceSession>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawModel = obj.optString("deviceModel", "Android Device")
                val cleanModel = if (rawModel.contains("Virtual Device", ignoreCase = true) || rawModel.contains("Google Pixel 7 Pro", ignoreCase = true)) {
                    getFormattedDeviceModel()
                } else {
                    rawModel
                }
                list.add(
                    ActiveDeviceSession(
                        sessionId = obj.optString("sessionId", ""),
                        userId = obj.optString("userId", ""),
                        userName = obj.optString("userName", ""),
                        userEmail = obj.optString("userEmail", ""),
                        userPhone = obj.optString("userPhone", ""),
                        userRole = obj.optString("userRole", "TEACHER"),
                        deviceModel = cleanModel,
                        deviceId = obj.optString("deviceId", ""),
                        loginTime = obj.optLong("loginTime", 0L),
                        lastActiveTime = obj.optLong("lastActiveTime", 0L),
                        status = obj.optString("status", "ACTIVE")
                    )
                )
            }
            if (list.isNotEmpty()) {
                _activeSessions.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local sessions: ${e.message}")
        }
    }
}
