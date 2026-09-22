package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.EducatorProfile
import com.example.util.ImageUtils
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

object EducatorManager {
    private const val PREFS_NAME = "jaiti_educators_prefs"
    private const val KEY_EDUCATORS = "key_educator_list_clean_v3"
    private const val KEY_PURGED = "key_old_demo_purged_v3"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("EducatorManager", "Coroutine error caught safely: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val _educators = MutableStateFlow<List<EducatorProfile>>(emptyList())
    val educators: StateFlow<List<EducatorProfile>> = _educators.asStateFlow()

    private var isInitialized = false
    private var firestoreListener: ListenerRegistration? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Ensure old demo dummy data is completely purged on fresh start
        val purged = prefs.getBoolean(KEY_PURGED, false)
        if (!purged) {
            prefs.edit()
                .remove(KEY_EDUCATORS)
                .remove("key_educator_list_v2")
                .remove("key_educators")
                .putBoolean(KEY_PURGED, true)
                .apply()
            _educators.value = emptyList()
        } else {
            val jsonStr = prefs.getString(KEY_EDUCATORS, null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val array = JSONArray(jsonStr)
                    val list = mutableListOf<EducatorProfile>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            EducatorProfile(
                                id = obj.optString("id", "EDU_${System.currentTimeMillis()}"),
                                name = obj.optString("name", ""),
                                email = obj.optString("email", ""),
                                photoUri = obj.optString("photoUri", ""),
                                phone = obj.optString("phone", ""),
                                subject = obj.optString("subject", ""),
                                createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis())
                            )
                        )
                    }
                    _educators.value = list
                } catch (e: Exception) {
                    _educators.value = emptyList()
                }
            } else {
                _educators.value = emptyList()
            }
        }

        // Start Firebase Firestore real-time cloud sync
        startFirestoreSync(appContext)
    }

    private fun startFirestoreSync(context: Context) {
        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()

                firestoreListener?.remove()
                firestoreListener = db.collection("educators").addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("EducatorManager", "Educators cloud listen failed: ${e.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val cloudList = mutableListOf<EducatorProfile>()
                        for (doc in snapshots.documents) {
                            try {
                                val id = doc.getString("id") ?: doc.id
                                val name = doc.getString("name") ?: ""
                                val email = doc.getString("email") ?: ""
                                val photoUri = doc.getString("photoUri") ?: ""
                                val phone = doc.getString("phone") ?: ""
                                val subject = doc.getString("subject") ?: ""
                                val createdTimestamp = doc.getLong("createdTimestamp") ?: System.currentTimeMillis()

                                cloudList.add(
                                    EducatorProfile(
                                        id = id,
                                        name = name,
                                        email = email,
                                        photoUri = photoUri,
                                        phone = phone,
                                        subject = subject,
                                        createdTimestamp = createdTimestamp
                                    )
                                )
                            } catch (ex: Exception) {
                                Log.e("EducatorManager", "Error parsing educator doc: ${ex.message}")
                            }
                        }

                        if (cloudList.isNotEmpty()) {
                            // Sort by creation time
                            val sorted = cloudList.sortedBy { it.createdTimestamp }
                            _educators.value = sorted
                            saveToLocalStorage(context, sorted)
                            Log.d("EducatorManager", "Synced ${sorted.size} educators from cloud")
                        }
                    } else if (snapshots != null && snapshots.isEmpty && _educators.value.isNotEmpty()) {
                        // Cloud collection is currently empty but local has items -> seed local items to cloud only if version is supported
                        if (AppVersionManager.isVersionSupported()) {
                            pushAllToCloud(db, _educators.value)
                        } else {
                            Log.w("EducatorManager", "Blocked pushAllToCloud: App version is not verified or unsupported")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w("EducatorManager", "Firestore init note for educators: ${t.message}")
            }
        }
    }

    private fun saveToLocalStorage(context: Context, list: List<EducatorProfile>) {
        try {
            val array = JSONArray()
            for (edu in list) {
                val obj = JSONObject()
                obj.put("id", edu.id)
                obj.put("name", edu.name)
                obj.put("email", edu.email)
                obj.put("photoUri", edu.photoUri)
                obj.put("phone", edu.phone)
                obj.put("subject", edu.subject)
                obj.put("createdTimestamp", edu.createdTimestamp)
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EDUCATORS, array.toString())
                .putBoolean(KEY_PURGED, true)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist(context: Context, list: List<EducatorProfile>) {
        _educators.value = list
        saveToLocalStorage(context, list)
    }

    fun syncFromCloud(context: Context, list: List<EducatorProfile>) {
        val sorted = list.sortedBy { it.createdTimestamp }
        _educators.value = sorted
        saveToLocalStorage(context, sorted)
    }

    /**
     * Checks whether an email, phone, or name belongs to a pre-registered Jaiti Educator or Master Admin
     */
    fun isPreRegistered(email: String, phone: String = "", fullName: String = ""): Boolean {
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val cleanName = fullName.trim().lowercase()

        // Master admin accounts
        if (cleanEmail == "jaitifoundation@gmail.com" || cleanEmail == "admin@jaiti.in" || cleanEmail == "admin" || cleanEmail == "teacher1") {
            return true
        }

        // Check if matching any educator in current list
        return _educators.value.any { edu ->
            val eduEmail = edu.email.trim().lowercase()
            val eduPhone = edu.phone.replace(Regex("[^0-9]"), "")
            val eduName = edu.name.trim().lowercase()
            (cleanEmail.isNotBlank() && eduEmail.isNotBlank() && eduEmail == cleanEmail) ||
            (cleanPhone.isNotBlank() && eduPhone.isNotBlank() && (eduPhone == cleanPhone || eduPhone.endsWith(cleanPhone) || cleanPhone.endsWith(eduPhone))) ||
            (cleanName.isNotBlank() && eduName.isNotBlank() && (eduName == cleanName || eduName.contains(cleanName) || cleanName.contains(eduName)))
        }
    }

    fun addEducator(context: Context, name: String, email: String = "", photoUri: String = "", phone: String = "", subject: String = ""): EducatorProfile {
        val syncablePhoto = if (photoUri.isNotBlank() && !photoUri.startsWith("data:image/")) {
            ImageUtils.fileOrUriToDataUrl(context, photoUri) ?: photoUri
        } else {
            photoUri
        }

        val newEdu = EducatorProfile(
            id = "EDU_${System.currentTimeMillis()}",
            name = name.trim(),
            email = email.trim(),
            photoUri = syncablePhoto.trim(),
            phone = phone.trim(),
            subject = subject.trim(),
            createdTimestamp = System.currentTimeMillis()
        )
        val updated = _educators.value + newEdu
        persist(context, updated)

        // Push to Firebase Cloud
        scope.launch {
            if (!AppVersionManager.isVersionSupported()) {
                Log.w("EducatorManager", "Blocked cloud push for new educator: App version unsupported")
                return@launch
            }
            try {
                val db = FirebaseFirestore.getInstance()
                val map = hashMapOf(
                    "id" to newEdu.id,
                    "name" to newEdu.name,
                    "email" to newEdu.email,
                    "photoUri" to newEdu.photoUri,
                    "phone" to newEdu.phone,
                    "subject" to newEdu.subject,
                    "createdTimestamp" to newEdu.createdTimestamp
                )
                db.collection("educators").document(newEdu.id).set(map, SetOptions.merge()).await()
                Log.d("EducatorManager", "Successfully pushed new educator ${newEdu.name} to Firestore cloud")
            } catch (e: Exception) {
                Log.e("EducatorManager", "Failed to push educator to cloud: ${e.message}")
            }
        }

        return newEdu
    }

    fun updateEducator(context: Context, educator: EducatorProfile) {
        val syncablePhoto = if (educator.photoUri.isNotBlank() && !educator.photoUri.startsWith("data:image/")) {
            ImageUtils.fileOrUriToDataUrl(context, educator.photoUri) ?: educator.photoUri
        } else {
            educator.photoUri
        }
        val readyEducator = educator.copy(photoUri = syncablePhoto.trim())

        val updated = _educators.value.map {
            if (it.id == readyEducator.id) readyEducator else it
        }
        persist(context, updated)

        // Push to Firebase Cloud
        scope.launch {
            if (!AppVersionManager.isVersionSupported()) {
                Log.w("EducatorManager", "Blocked cloud update for educator: App version unsupported")
                return@launch
            }
            try {
                val db = FirebaseFirestore.getInstance()
                val map = hashMapOf(
                    "id" to readyEducator.id,
                    "name" to readyEducator.name,
                    "email" to readyEducator.email,
                    "photoUri" to readyEducator.photoUri,
                    "phone" to readyEducator.phone,
                    "subject" to readyEducator.subject,
                    "createdTimestamp" to readyEducator.createdTimestamp
                )
                db.collection("educators").document(readyEducator.id).set(map, SetOptions.merge()).await()
                Log.d("EducatorManager", "Successfully pushed updated educator ${readyEducator.name} to Firestore cloud")
            } catch (e: Exception) {
                Log.e("EducatorManager", "Failed to update educator in cloud: ${e.message}")
            }
        }
    }

    fun deleteEducator(context: Context, educatorId: String) {
        val updated = _educators.value.filter { it.id != educatorId }
        persist(context, updated)

        // Delete from Firebase Cloud
        scope.launch {
            if (!AppVersionManager.isVersionSupported()) {
                Log.w("EducatorManager", "Blocked cloud deletion for educator: App version unsupported")
                return@launch
            }
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("educators").document(educatorId).delete().await()
                Log.d("EducatorManager", "Successfully deleted educator $educatorId from Firestore cloud")
            } catch (e: Exception) {
                Log.e("EducatorManager", "Failed to delete educator in cloud: ${e.message}")
            }
        }
    }

    private fun pushAllToCloud(db: FirebaseFirestore, list: List<EducatorProfile>) {
        scope.launch {
            if (!AppVersionManager.isVersionSupported()) {
                Log.w("EducatorManager", "Blocked pushAllToCloud: App version unsupported")
                return@launch
            }
            try {
                val batch = db.batch()
                for (edu in list) {
                    val docRef = db.collection("educators").document(edu.id)
                    val map = hashMapOf(
                        "id" to edu.id,
                        "name" to edu.name,
                        "email" to edu.email,
                        "photoUri" to edu.photoUri,
                        "phone" to edu.phone,
                        "subject" to edu.subject,
                        "createdTimestamp" to edu.createdTimestamp
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                batch.commit().await()
                Log.d("EducatorManager", "Pushed all ${list.size} educators to cloud")
            } catch (e: Exception) {
                Log.e("EducatorManager", "Failed to push all educators to cloud: ${e.message}")
            }
        }
    }
}

