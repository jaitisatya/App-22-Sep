package com.example

import android.app.Application
import android.util.Log
import com.example.data.repository.EducatorManager
import com.google.firebase.FirebaseApp

class JaitiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            // Initialize Firebase App Check for database security
            val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                try {
                    val debugToken = "0E16BB07-B000-4B4A-B972-B5D327E53571"
                    val app = FirebaseApp.getInstance()
                    val persistenceKey = app.persistenceKey
                    val prefsName = "com.google.firebase.appcheck.debug.store.$persistenceKey"
                    getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugToken)
                        .apply()
                    Log.d("JaitiApplication", "Pre-seeded Firebase App Check debug token: $debugToken")
                } catch (pe: Throwable) {
                    Log.w("JaitiApplication", "Debug token preset notice: ${pe.message}")
                }
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                )
                Log.d("JaitiApplication", "Firebase App Check initialized with DebugAppCheckProviderFactory")
            } else {
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d("JaitiApplication", "Firebase App Check initialized with PlayIntegrityAppCheckProviderFactory")
            }

            // Configure Firestore offline persistence before any Firestore instance operations
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                            .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
                Log.d("JaitiApplication", "Firestore persistent cache configured with unlimited disk cache for offline attendance")
            } catch (fe: Throwable) {
                Log.w("JaitiApplication", "Firestore persistent settings note: ${fe.message}")
            }
        } catch (e: Throwable) {
            Log.w("JaitiApplication", "FirebaseApp / AppCheck init note: ${e.message}")
        }

        try {
            EducatorManager.init(this)
        } catch (e: Throwable) {
            Log.w("JaitiApplication", "EducatorManager init note: ${e.message}")
        }

        try {
            com.example.util.AttendanceReminderManager.scheduleReminder(this)
        } catch (e: Throwable) {
            Log.w("JaitiApplication", "AttendanceReminderManager schedule note: ${e.message}")
        }
    }
}

