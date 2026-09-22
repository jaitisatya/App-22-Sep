package com.example.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Manages class scheduled timings, persisting both locally (SharedPreferences)
 * and in Cloud Firestore under "class_timings" collection so that timings sync
 * across all teacher and admin devices in real-time.
 */
object ClassTimingManager {
    private const val PREFS_NAME = "class_timings_prefs"
    private const val TAG = "ClassTimingManager"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val _timingsVersion = MutableStateFlow(0L)
    val timingsVersion: StateFlow<Long> = _timingsVersion.asStateFlow()

    const val KEY_GLOBAL_DEFAULT_TIME = "GLOBAL_DEFAULT_ATTENDANCE_TIME"

    fun getGlobalAttendanceTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GLOBAL_DEFAULT_TIME, null)
        if (!saved.isNullOrBlank()) return saved
        val (hour, minute) = com.example.util.AttendanceReminderManager.getAttendanceTime(context)
        return com.example.util.AttendanceReminderManager.formatTimeString(hour, minute)
    }

    fun setGlobalAttendanceTime(context: Context, newTiming: String, hour: Int, minute: Int) {
        val clean = newTiming.trim()
        if (clean.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GLOBAL_DEFAULT_TIME, clean).apply()
        com.example.util.AttendanceReminderManager.setAttendanceTime(context, hour, minute)
        _timingsVersion.value = System.currentTimeMillis()

        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                val docData = hashMapOf(
                    "classId" to KEY_GLOBAL_DEFAULT_TIME,
                    "timing" to clean,
                    "hour" to hour,
                    "minute" to minute,
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection("class_timings")
                    .document(KEY_GLOBAL_DEFAULT_TIME)
                    .set(docData, SetOptions.merge())
                    .await()
                Log.d(TAG, "Synced global attendance time to Firestore: $clean")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync global attendance time to Firestore: ${e.message}")
            }
        }
    }

    fun getAllTimings(context: Context): Map<String, String> {
        val globalDefault = getGlobalAttendanceTime(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableMapOf(
            "CLASS_J_PREP" to globalDefault,
            "CLASS_J1" to globalDefault,
            "CLASS_J2" to globalDefault,
            "CLASS_J3" to globalDefault,
            "CLASS_J4" to globalDefault,
            "CLASS_J5" to globalDefault,
            "CLASS_EDUCATORS" to "08:30 AM",
            "CLASS_STAFF" to "09:00 AM",
            "CLASS_VISITORS" to "03:00 PM"
        )
        prefs.all.forEach { (k, v) ->
            if (v is String && v.isNotBlank()) {
                result[k] = v
            }
        }
        return result
    }

    fun getTiming(context: Context, classId: String, fallback: String? = null): String {
        val actualFallback = fallback ?: if (classId.contains("EDUCATOR", ignoreCase = true)) "08:30 AM" else getGlobalAttendanceTime(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(classId, actualFallback) ?: actualFallback
    }

    fun setTiming(context: Context, classId: String, newTiming: String) {
        val cleanTiming = newTiming.trim()
        if (cleanTiming.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(classId, cleanTiming).apply()
        _timingsVersion.value = System.currentTimeMillis()

        // Push to Firestore so other devices sync instantly
        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                val docData = hashMapOf(
                    "classId" to classId,
                    "timing" to cleanTiming,
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection("class_timings")
                    .document(classId.replace("/", "_"))
                    .set(docData, SetOptions.merge())
                    .await()
                Log.d(TAG, "Synced class timing to Firestore for $classId: $cleanTiming")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync class timing to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Listens to real-time changes in Firestore "class_timings" collection.
     */
    fun startListening(context: Context) {
        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val db = FirebaseFirestore.getInstance()
                db.collection("class_timings").addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w(TAG, "Firestore class timings listener failed", e)
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        var changed = false
                        for (doc in snapshots.documents) {
                            val cId = doc.getString("classId") ?: doc.id
                            val time = doc.getString("timing") ?: ""
                            if (cId.isNotBlank() && time.isNotBlank()) {
                                val current = prefs.getString(cId, null)
                                if (current != time) {
                                    editor.putString(cId, time)
                                    changed = true
                                }
                                if (cId == KEY_GLOBAL_DEFAULT_TIME) {
                                    val h = doc.getLong("hour")?.toInt()
                                    val m = doc.getLong("minute")?.toInt()
                                    if (h != null && m != null) {
                                        com.example.util.AttendanceReminderManager.setAttendanceTime(context, h, m)
                                    }
                                }
                            }
                        }
                        if (changed) {
                            editor.apply()
                            _timingsVersion.value = System.currentTimeMillis()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start Firestore class timings listener: ${e.message}")
            }
        }
    }
}
