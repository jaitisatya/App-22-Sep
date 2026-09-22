package com.example.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.util.ImageUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request

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

    fun getUrlKey(classId: String, dateIso: String): String {
        val cleanClassId = normalizeClassId(classId)
        val cleanDate = dateIso.trim()
        return "class_photo_url_${cleanClassId}_$cleanDate"
    }

    fun getPathKey(classId: String, dateIso: String): String {
        val cleanClassId = normalizeClassId(classId)
        val cleanDate = dateIso.trim()
        return "class_photo_path_${cleanClassId}_$cleanDate"
    }

    fun getRefKey(classId: String, dateIso: String): String {
        val cleanClassId = normalizeClassId(classId)
        val cleanDate = dateIso.trim()
        return "class_photo_ref_${cleanClassId}_$cleanDate"
    }

    /**
     * Returns the cloud photo URL (or fallback local photo) for attendance records.
     */
    fun getPhotoUrl(context: Context, classId: String, dateIso: String): String? {
        val normClassId = normalizeClassId(classId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cloudUrl = prefs.getString(getUrlKey(normClassId, dateIso), null)
        if (!cloudUrl.isNullOrBlank()) return cloudUrl
        val local = prefs.getString(getKey(normClassId, dateIso), null)
        return if (!local.isNullOrBlank()) local else null
    }

    /**
     * Returns the Firebase Storage path if uploaded.
     */
    fun getPhotoStoragePath(context: Context, classId: String, dateIso: String): String? {
        val normClassId = normalizeClassId(classId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(getPathKey(normClassId, dateIso), null)?.ifBlank { null }
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
     * Saves and syncs daily class photo directly to Cloud Storage & Cloud Firestore.
     * Synchronizes to both daily_class_photos AND all corresponding attendance_records
     * documents in Firestore in real-time.
     */
    suspend fun saveClassPhoto(
        context: Context,
        classId: String,
        className: String,
        dateIso: String,
        photoUri: String
    ): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (photoUri.isBlank()) return@withContext false
        val cleanPhoto = if (photoUri.startsWith("data:image/")) {
            photoUri
        } else {
            ImageUtils.fileOrUriToDataUrl(context, photoUri) ?: photoUri
        }

        val normClassId = normalizeClassId(classId)
        val key = getKey(normClassId, dateIso)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Save locally first for instant snappy response on current device
        prefs.edit().putString(key, cleanPhoto).apply()
        _photosVersion.value = System.currentTimeMillis()

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            // 1. Upload to Firebase Storage if possible
            var cloudPhotoUrl: String = cleanPhoto
            var storagePath: String = ""
            try {
                val bytes = if (cleanPhoto.startsWith("data:image/")) {
                    val b64 = cleanPhoto.substringAfter("base64,")
                    Base64.decode(b64, Base64.DEFAULT)
                } else {
                    val f = File(cleanPhoto)
                    if (f.exists()) f.readBytes() else null
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val storage = FirebaseStorage.getInstance()
                    val path = "class_photos/${normClassId}_${dateIso.trim()}.jpg"
                    val ref = storage.reference.child(path)
                    val metadata = StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .setCustomMetadata("classId", normClassId)
                        .setCustomMetadata("date", dateIso.trim())
                        .build()
                    ref.putBytes(bytes, metadata).await()
                    val downloadUrl = ref.downloadUrl.await().toString()
                    cloudPhotoUrl = downloadUrl
                    storagePath = path
                    Log.d(TAG, "Uploaded photo to Firebase Storage: $storagePath -> $cloudPhotoUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage upload note (using cloud data fallback): ${e.message}")
            }

            // Update local cache with cloud references
            val stableRef = if (storagePath.isNotBlank()) storagePath else cloudPhotoUrl
            prefs.edit()
                .putString(getUrlKey(normClassId, dateIso), cloudPhotoUrl)
                .putString(getPathKey(normClassId, dateIso), storagePath)
                .putString(getRefKey(normClassId, dateIso), stableRef)
                .apply()

            val db = FirebaseFirestore.getInstance()
            val docId = "${normClassId}_${dateIso.trim()}"
            val now = System.currentTimeMillis()
            val data = hashMapOf(
                "classId" to normClassId,
                "className" to className.trim(),
                "dateIso" to dateIso.trim(),
                "photoUrl" to cloudPhotoUrl,
                "photoStoragePath" to storagePath,
                "photoUri" to cloudPhotoUrl,
                "lastUpdated" to now,
                "updatedAt" to now
            )

            // 2. Push to daily_class_photos
            db.collection("daily_class_photos")
                .document(docId)
                .set(data, SetOptions.merge())
                .await()

            // 3. Synchronize to all existing attendance_records documents in Firestore for this class session
            try {
                val attQuery = db.collection("attendance_records")
                    .whereEqualTo("classId", normClassId)
                    .whereEqualTo("date", dateIso.trim())
                    .get().await()
                if (!attQuery.isEmpty) {
                    val batch = db.batch()
                    for (attDoc in attQuery.documents) {
                        batch.update(attDoc.reference, mapOf(
                            "photoUrl" to cloudPhotoUrl,
                            "photoStoragePath" to storagePath,
                            "lastModifiedTimestamp" to now
                        ))
                    }
                    batch.commit().await()
                    Log.d(TAG, "Updated ${attQuery.size()} attendance records with photo in Firestore")
                }
            } catch (attEx: Exception) {
                Log.w(TAG, "Updating attendance_records with photo note: ${attEx.message}")
            }

            Log.d(TAG, "Successfully pushed daily class photo to Cloud Firestore for $normClassId ($dateIso)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cloud Firestore push failed/queued for daily photo: ${e.message}")
            true
        }
    }

    /**
     * Handles incoming real-time photo update from attendance_records or daily_class_photos.
     * Prevents duplicate downloads by checking existing stable reference.
     */
    fun onAttendancePhotoReceived(
        context: Context,
        classId: String,
        dateIso: String,
        cloudPhotoUrl: String?,
        photoStoragePath: String?
    ) {
        val normClassId = normalizeClassId(classId)
        val cleanUrl = cloudPhotoUrl?.trim().orEmpty()
        val cleanPath = photoStoragePath?.trim().orEmpty()

        if (cleanUrl.isBlank() && cleanPath.isBlank()) {
            return
        }

        val stableRef = if (cleanPath.isNotBlank()) cleanPath else cleanUrl
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedRef = prefs.getString(getRefKey(normClassId, dateIso), null)
        val cachedPhoto = prefs.getString(getKey(normClassId, dateIso), null)

        // Avoid duplicate download if already cached with this stable reference
        if (cachedRef == stableRef && !cachedPhoto.isNullOrBlank()) {
            return
        }

        scope.launch {
            try {
                if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                    var bytes: ByteArray? = null
                    try {
                        if (cleanPath.isNotBlank()) {
                            val storage = FirebaseStorage.getInstance()
                            val ref = storage.reference.child(cleanPath)
                            bytes = ref.getBytes(2 * 1024 * 1024L).await()
                        }
                    } catch (stEx: Exception) {
                        Log.w(TAG, "Storage getBytes note: ${stEx.message}")
                    }

                    if (bytes == null || bytes.isEmpty()) {
                        try {
                            val client = OkHttpClient()
                            val request = Request.Builder().url(cleanUrl).build()
                            client.newCall(request).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    bytes = resp.body?.bytes()
                                }
                            }
                        } catch (httpEx: Exception) {
                            Log.w(TAG, "Http download photo note: ${httpEx.message}")
                        }
                    }

                    val finalBytes = bytes
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        val dir = File(context.filesDir, "class_photos").apply { if (!exists()) mkdirs() }
                        val file = File(dir, "${normClassId}_${dateIso.trim()}.jpg")
                        FileOutputStream(file).use { it.write(finalBytes) }

                        prefs.edit()
                            .putString(getKey(normClassId, dateIso), file.absolutePath)
                            .putString(getUrlKey(normClassId, dateIso), cleanUrl)
                            .putString(getPathKey(normClassId, dateIso), cleanPath)
                            .putString(getRefKey(normClassId, dateIso), stableRef)
                            .apply()

                        _photosVersion.value = System.currentTimeMillis()
                        Log.d(TAG, "Downloaded and cached class photo for $normClassId on $dateIso")
                    } else {
                        // Fallback: point directly to cloud URL
                        prefs.edit()
                            .putString(getKey(normClassId, dateIso), cleanUrl)
                            .putString(getUrlKey(normClassId, dateIso), cleanUrl)
                            .putString(getPathKey(normClassId, dateIso), cleanPath)
                            .putString(getRefKey(normClassId, dateIso), stableRef)
                            .apply()

                        _photosVersion.value = System.currentTimeMillis()
                    }
                } else if (cleanUrl.startsWith("data:image/")) {
                    prefs.edit()
                        .putString(getKey(normClassId, dateIso), cleanUrl)
                        .putString(getUrlKey(normClassId, dateIso), cleanUrl)
                        .putString(getPathKey(normClassId, dateIso), cleanPath)
                        .putString(getRefKey(normClassId, dateIso), stableRef)
                        .apply()

                    _photosVersion.value = System.currentTimeMillis()
                    Log.d(TAG, "Updated class photo data URL for $normClassId on $dateIso")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming attendance photo: ${e.message}", e)
            }
        }
    }

    /**
     * Deletes daily class photo locally, in Cloud Storage, and in Cloud Firestore.
     */
    fun deleteClassPhoto(context: Context, classId: String, dateIso: String) {
        val normClassId = normalizeClassId(classId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storagePath = prefs.getString(getPathKey(normClassId, dateIso), null)

        prefs.edit()
            .remove(getKey(normClassId, dateIso))
            .remove(getUrlKey(normClassId, dateIso))
            .remove(getPathKey(normClassId, dateIso))
            .remove(getRefKey(normClassId, dateIso))
            .apply()
        _photosVersion.value = System.currentTimeMillis()

        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                val docId = "${normClassId}_${dateIso.trim()}"
                db.collection("daily_class_photos").document(docId).delete().await()

                // Remove photo reference from attendance_records
                val attDocs = db.collection("attendance_records")
                    .whereEqualTo("classId", normClassId)
                    .whereEqualTo("date", dateIso.trim())
                    .get().await()
                if (!attDocs.isEmpty) {
                    val batch = db.batch()
                    for (attDoc in attDocs.documents) {
                        batch.update(attDoc.reference, mapOf(
                            "photoUrl" to "",
                            "photoStoragePath" to "",
                            "lastModifiedTimestamp" to System.currentTimeMillis()
                        ))
                    }
                    batch.commit().await()
                }

                if (!storagePath.isNullOrBlank()) {
                    try {
                        val storage = FirebaseStorage.getInstance()
                        storage.reference.child(storagePath).delete().await()
                    } catch (_: Exception) {}
                }

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
                            // Directly overwrite local storage with latest cloud photo
                            prefs.edit().putString(key, photo).apply()
                            _photosVersion.value = System.currentTimeMillis()
                            onPhotoChanged(photo)
                            Log.d(TAG, "Class photo updated from cloud for $normClassId on $dateIso")
                        } else {
                            prefs.edit().remove(key).apply()
                            _photosVersion.value = System.currentTimeMillis()
                            onPhotoChanged(null)
                        }
                    } else if (snapshot != null && !snapshot.exists()) {
                        // Only clear if confirmed by the server (not merely a local cache miss)
                        val isFromCache = snapshot.metadata.isFromCache
                        if (!isFromCache) {
                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            val key = getKey(normClassId, dateIso)
                            val existing = prefs.getString(key, null)
                            if (existing != null) {
                                prefs.edit().remove(key).apply()
                                _photosVersion.value = System.currentTimeMillis()
                            }
                            onPhotoChanged(null)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error creating targeted listener for $docId: ${e.message}")
            return null
        }
    }
}
