package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.entity.UserEntity
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.model.Role
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CollaborationRequest(
    val id: String,
    val senderName: String,
    val senderEmail: String,
    val receiverEmail: String,
    val centerCode: String = "JAITI-2026",
    val role: String = "TEACHER",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val canTakeAllAttendance: Boolean = true,
    val canManageStudents: Boolean = true,
    val canManageClasses: Boolean = true,
    val canViewReports: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

object CollaborationManager {
    const val DEFAULT_CENTER_CODE = "JAITI-2026"
    const val MASTER_ADMIN_EMAIL = "jaitifoundation@gmail.com"

    private var prefs: SharedPreferences? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("CollaborationManager", "Background coroutine error handled: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private var invitesListener: ListenerRegistration? = null

    private val _requests = MutableStateFlow<List<CollaborationRequest>>(emptyList())
    val requests: StateFlow<List<CollaborationRequest>> = _requests.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("jaiti_collaboration_prefs", Context.MODE_PRIVATE)
            loadLocalRequests()
            startFirestoreListener()
        }
    }

    private fun loadLocalRequests() {
        val json = prefs?.getString("requests_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<CollaborationRequest>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CollaborationRequest(
                            id = obj.getString("id"),
                            senderName = obj.getString("senderName"),
                            senderEmail = obj.getString("senderEmail"),
                            receiverEmail = obj.optString("receiverEmail", MASTER_ADMIN_EMAIL),
                            centerCode = obj.optString("centerCode", DEFAULT_CENTER_CODE),
                            role = obj.optString("role", "TEACHER"),
                            status = obj.optString("status", "PENDING"),
                            canTakeAllAttendance = obj.optBoolean("canTakeAllAttendance", true),
                            canManageStudents = obj.optBoolean("canManageStudents", true),
                            canManageClasses = obj.optBoolean("canManageClasses", true),
                            canViewReports = obj.optBoolean("canViewReports", true),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                _requests.value = list
            } catch (e: Exception) {
                Log.e("CollaborationManager", "Error parsing local requests: ${e.message}")
            }
        }
    }

    private fun saveLocalRequests(list: List<CollaborationRequest>) {
        _requests.value = list
        try {
            val array = JSONArray()
            list.forEach { req ->
                val obj = JSONObject()
                obj.put("id", req.id)
                obj.put("senderName", req.senderName)
                obj.put("senderEmail", req.senderEmail)
                obj.put("receiverEmail", req.receiverEmail)
                obj.put("centerCode", req.centerCode)
                obj.put("role", req.role)
                obj.put("status", req.status)
                obj.put("canTakeAllAttendance", req.canTakeAllAttendance)
                obj.put("canManageStudents", req.canManageStudents)
                obj.put("canManageClasses", req.canManageClasses)
                obj.put("canViewReports", req.canViewReports)
                obj.put("timestamp", req.timestamp)
                array.put(obj)
            }
            prefs?.edit()?.putString("requests_json", array.toString())?.apply()
        } catch (e: Exception) {
            Log.e("CollaborationManager", "Error saving local requests: ${e.message}")
        }
    }

    private fun startFirestoreListener() {
        try {
            val db = FirebaseFirestore.getInstance()
            invitesListener?.remove()
            invitesListener = db.collection("collaboration_requests").addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CollaborationManager", "Firestore listener failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    val list = mutableListOf<CollaborationRequest>()
                    for (doc in snapshots.documents) {
                        try {
                            val id = doc.getString("id") ?: doc.id
                            val senderName = doc.getString("senderName") ?: ""
                            val senderEmail = doc.getString("senderEmail") ?: ""
                            val receiverEmail = doc.getString("receiverEmail") ?: MASTER_ADMIN_EMAIL
                            val centerCode = doc.getString("centerCode") ?: DEFAULT_CENTER_CODE
                            val role = doc.getString("role") ?: "TEACHER"
                            val status = doc.getString("status") ?: "PENDING"
                            val canTakeAllAttendance = doc.getBoolean("canTakeAllAttendance") ?: true
                            val canManageStudents = doc.getBoolean("canManageStudents") ?: true
                            val canManageClasses = doc.getBoolean("canManageClasses") ?: true
                            val canViewReports = doc.getBoolean("canViewReports") ?: true
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            list.add(
                                CollaborationRequest(
                                    id = id,
                                    senderName = senderName,
                                    senderEmail = senderEmail,
                                    receiverEmail = receiverEmail,
                                    centerCode = centerCode,
                                    role = role,
                                    status = status,
                                    canTakeAllAttendance = canTakeAllAttendance,
                                    canManageStudents = canManageStudents,
                                    canManageClasses = canManageClasses,
                                    canViewReports = canViewReports,
                                    timestamp = timestamp
                                )
                            )
                        } catch (ex: Exception) {
                            Log.e("CollaborationManager", "Error parsing doc: ${ex.message}")
                        }
                    }
                    saveLocalRequests(list)
                }
            }
        } catch (e: Exception) {
            Log.w("CollaborationManager", "Firestore unavailable for collaboration requests: ${e.message}")
        }
    }

    suspend fun sendCollaborationRequest(
        senderName: String,
        senderEmail: String,
        receiverEmail: String = MASTER_ADMIN_EMAIL,
        centerCode: String = DEFAULT_CENTER_CODE,
        role: String = "TEACHER"
    ) = withContext(Dispatchers.IO) {
        val id = "REQ_${System.currentTimeMillis()}"
        val req = CollaborationRequest(
            id = id,
            senderName = senderName,
            senderEmail = senderEmail,
            receiverEmail = receiverEmail,
            centerCode = centerCode,
            role = role,
            status = "PENDING"
        )
        val current = _requests.value.filter { it.senderEmail != senderEmail || it.receiverEmail != receiverEmail } + req
        saveLocalRequests(current)

        try {
            val db = FirebaseFirestore.getInstance()
            val map = hashMapOf(
                "id" to req.id,
                "senderName" to req.senderName,
                "senderEmail" to req.senderEmail,
                "receiverEmail" to req.receiverEmail,
                "centerCode" to req.centerCode,
                "role" to req.role,
                "status" to req.status,
                "canTakeAllAttendance" to req.canTakeAllAttendance,
                "canManageStudents" to req.canManageStudents,
                "canManageClasses" to req.canManageClasses,
                "canViewReports" to req.canViewReports,
                "timestamp" to req.timestamp
            )
            db.collection("collaboration_requests").document(req.id).set(map, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("CollaborationManager", "Failed to push request to Firestore: ${e.message}")
        }
    }

    suspend fun approveRequest(
        requestId: String,
        database: AppDatabase?,
        firestoreSyncManager: FirestoreSyncManager?,
        canTakeAllAttendance: Boolean = true,
        canManageStudents: Boolean = true,
        canManageClasses: Boolean = true,
        canViewReports: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val target = _requests.value.find { it.id == requestId } ?: return@withContext
        val updated = target.copy(
            status = "APPROVED",
            canTakeAllAttendance = canTakeAllAttendance,
            canManageStudents = canManageStudents,
            canManageClasses = canManageClasses,
            canViewReports = canViewReports
        )
        val updatedList = _requests.value.map { if (it.id == requestId) updated else it }
        saveLocalRequests(updatedList)

        // Update User in Room DB and Firestore
        database?.let { db ->
            val user = db.userDao().getUserByUsernameOrEmail(target.senderEmail)
            if (user != null) {
                val updatedUser = user.copy(
                    collaborationStatus = "APPROVED",
                    canTakeAllAttendance = canTakeAllAttendance,
                    canManageStudents = canManageStudents,
                    canManageClasses = canManageClasses,
                    canViewReports = canViewReports
                )
                db.userDao().insertUser(updatedUser)
                firestoreSyncManager?.pushUser(updatedUser)
            }
        }

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("collaboration_requests").document(requestId)
                .set(mapOf("status" to "APPROVED"), SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("CollaborationManager", "Failed to update status in Firestore: ${e.message}")
        }
    }

    suspend fun declineRequest(requestId: String) = withContext(Dispatchers.IO) {
        val updatedList = _requests.value.map {
            if (it.id == requestId) it.copy(status = "REJECTED") else it
        }
        saveLocalRequests(updatedList)

        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("collaboration_requests").document(requestId)
                .set(mapOf("status" to "REJECTED"), SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("CollaborationManager", "Failed to update rejection in Firestore: ${e.message}")
        }
    }
}
