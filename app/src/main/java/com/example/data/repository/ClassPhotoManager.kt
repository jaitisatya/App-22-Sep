package com.example.data.repository

import android.content.Context
import android.util.Log
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

object ClassPhotoManager {
    private const val PREFS_NAME = "jaiti_daily_class_photos"
    private const val TAG = "ClassPhotoManager"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine error safely caught: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val _photosVersion = MutableStateFlow(0L)
    val photosVersion: StateFlow<Long> = _photosVersion.asStateFlow()

    private var photosListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun normalizeClassId(classId: String): String {
        val trimmed = classId.trim()
        if (trimmed.equals("CLASS_EDUCATORS", ignoreCase = true) || trimmed.contains("EDUCATOR", ignoreCase = true)) {
            return "CLASS_EDUCATORS"
        }
        return com.example.util.BatchConstants.getStandardClassId(trimmed).replace("/", "_")
    }

    fun getKey(classId: String, dateIso: String): String {
        val cleanClassId = normalizeClassId(classId)
        val cleanDate = dateIso.trim()
        return "class_photo_${cleanClassId}_$cleanDate"
    }

    /**
     * Starts a real-time Firestore listener for all daily class photos so any upload or deletion
     * on one device instantly updates on all logged-in devices without manual refresh.
     */
    fun startListening(context: Context) {
        if (photosListener != null) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()
            photosListener = db.collection("daily_class_photos")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w(TAG, "daily_class_photos snapshot error: ${e.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        var hasChanges = false

                        // Populate / update all existing photos in snapshot
                        for (doc in snapshots.documents) {
                            val rawClassId = doc.getString("classId") ?: ""
                            val dateIso = doc.getString("dateIso") ?: ""
                            val photoUri = doc.getString("photoUrl")
                                ?: doc.getString("photoUri")
                                ?: ""
                            if (rawClassId.isNotBlank() && dateIso.isNotBlank() && photoUri.isNotBlank()) {
                                val normClassId = normalizeClassId(rawClassId)
                                val key = getKey(normClassId, dateIso)
                                val existing = prefs.getString(key, null)
                                if (existing != photoUri) {
                                    editor.putString(key, photoUri)
                                    hasChanges = true
                                }
                            }
                        }

                        // Remove deleted photos
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val rawClassId = doc.getString("classId") ?: ""
                            val dateIso = doc.getString("dateIso") ?: ""
                            if (rawClassId.isNotBlank() && dateIso.isNotBlank()) {
                                val normClassId = normalizeClassId(rawClassId)
                                val key = getKey(normClassId, dateIso)
                                if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                    editor.remove(key)
                                    hasChanges = true
                                }
                            }
                        }

                        if (hasChanges) {
                            editor.apply()
                            _photosVersion.value = System.currentTimeMillis()
                            Log.d(TAG, "Live photo sync applied across all devices: ${_photosVersion.value}")
                        }
                    }
                }
            Log.d(TAG, "Started real-time listener on daily_class_photos")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start live listener for class photos: ${t.message}")
        }
    }

    /**
     * Proactively syncs all daily class photos from Cloud Firestore.
     */
    suspend fun syncAllDailyPhotosFromCloud(context: Context) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()
            val snapshots = db.collection("daily_class_photos").get().await()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var hasChanges = false
            for (doc in snapshots.documents) {
                val rawClassId = doc.getString("classId") ?: ""
                val dateIso = doc.getString("dateIso") ?: ""
                val photoUri = doc.getString("photoUrl")
                    ?: doc.getString("photoUri")
                    ?: ""
                if (rawClassId.isNotBlank() && dateIso.isNotBlank() && photoUri.isNotBlank()) {
                    val normClassId = normalizeClassId(rawClassId)
                    val key = getKey(normClassId, dateIso)
                    val existing = prefs.getString(key, null)
                    if (existing != photoUri) {
                        editor.putString(key, photoUri)
                        hasChanges = true
                    }
                }
            }
            if (hasChanges) {
                editor.apply()
                _photosVersion.value = System.currentTimeMillis()
                Log.d(TAG, "Full sync of daily class photos completed (${snapshots.size()} docs)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncAllDailyPhotosFromCloud error: ${e.message}")
        }
    }

    /**
     * Retrieves the daily photo URI/DataUrl for a specific class and date.
     */
    fun getClassPhoto(context: Context, classId: String, dateIso: String): String? {
        val normClassId = normalizeClassId(classId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getKey(normClassId, dateIso)
        val saved = prefs.getString(key, null)
        if (!saved.isNullOrBlank()) return saved

        // Fetch from Firestore asynchronously in background if not present in local cache
        fetchFromFirestore(context, normClassId, dateIso)
        return null
    }

    /**
     * Saves and syncs daily class photo (< 100 KB) to local preferences and Cloud Firestore.
     */
    fun saveClassPhoto(
        context: Context,
        classId: String,
        className: String,
        dateIso: String,
        photoUri: String
    ) {
        if (photoUri.isBlank()) return
        val cleanPhoto = if (photoUri.startsWith("data:image/")) {
            photoUri
        } else {
            ImageUtils.fileOrUriToDataUrl(context, photoUri) ?: photoUri
        }

        val normClassId = normalizeClassId(classId)
        val key = getKey(normClassId, dateIso)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, cleanPhoto).apply()
        _photosVersion.value = System.currentTimeMillis()

        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                val docId = "${normClassId}_${dateIso.trim()}"
                val now = System.currentTimeMillis()
                val data = hashMapOf(
                    "classId" to normClassId,
                    "className" to className.trim(),
                    "dateIso" to dateIso.trim(),
                    "photoUrl" to cleanPhoto,
                    "photoUri" to cleanPhoto,
                    "lastUpdated" to now,
                    "updatedAt" to now
                )
                db.collection("daily_class_photos")
                    .document(docId)
                    .set(data, SetOptions.merge())
                    .await()
                Log.d(TAG, "Successfully synced daily class photo for $normClassId ($dateIso) to Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync note for daily photo: ${e.message}")
            }
        }
    }

    /**
     * Deletes daily class photo locally and in Cloud Firestore.
     */
    fun deleteClassPhoto(context: Context, classId: String, dateIso: String) {
        val normClassId = normalizeClassId(classId)
        val key = getKey(normClassId, dateIso)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
        _photosVersion.value = System.currentTimeMillis()

        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val docId = "${normClassId}_${dateIso.trim()}"
                db.collection("daily_class_photos").document(docId).delete().await()
                Log.d(TAG, "Deleted daily class photo for $normClassId on $dateIso")
            } catch (e: Exception) {
                Log.w(TAG, "Delete daily class photo note: ${e.message}")
            }
        }
    }

    private fun fetchFromFirestore(context: Context, classId: String, dateIso: String) {
        val normClassId = normalizeClassId(classId)
        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                val docId = "${normClassId}_${dateIso.trim()}"
                val doc = db.collection("daily_class_photos").document(docId).get().await()
                if (doc.exists()) {
                    val photoUri = doc.getString("photoUrl")
                        ?: doc.getString("photoUri")
                    if (!photoUri.isNullOrBlank()) {
                        val key = getKey(normClassId, dateIso)
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putString(key, photoUri).apply()
                        _photosVersion.value = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore fetch note: ${e.message}")
            }
        }
    }

    /**
     * Attaches a targeted SnapshotListener to the specific class photo document.
     * Guarantees all devices receive and display updated class photos immediately.
     */
    fun listenToClassPhoto(
        context: Context,
        classId: String,
        dateIso: String,
        onPhotoChanged: (String?) -> Unit
    ): ListenerRegistration? {
        val normClassId = normalizeClassId(classId)
        val docId = "${normClassId}_${dateIso.trim()}"
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val db = FirebaseFirestore.getInstance()
            return db.collection("daily_class_photos")
                .document(docId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w(TAG, "Targeted listener error for $docId: ${e.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val photo = snapshot.getString("photoUrl")
                            ?: snapshot.getString("photoUri")
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val key = getKey(normClassId, dateIso)
                        if (!photo.isNullOrBlank()) {
                            prefs.edit().putString(key, photo).apply()
                            _photosVersion.value = System.currentTimeMillis()
                            onPhotoChanged(photo)
                        } else {
                            prefs.edit().remove(key).apply()
                            _photosVersion.value = System.currentTimeMillis()
                            onPhotoChanged(null)
                        }
                    } else {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val key = getKey(normClassId, dateIso)
                        prefs.edit().remove(key).apply()
                        _photosVersion.value = System.currentTimeMillis()
                        onPhotoChanged(null)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error creating targeted listener for $docId: ${e.message}")
            return null
        }
    }
}
