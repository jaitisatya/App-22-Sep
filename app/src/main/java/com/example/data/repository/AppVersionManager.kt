package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppVersionConfig
import com.example.data.model.VersionStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AppVersionManager {

    private const val TAG = "AppVersionManager"
    private const val COLLECTION_APP_CONFIG = "app_config"
    private const val DOC_APP_VERSION = "app_version"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val _versionStatus = MutableStateFlow(VersionStatus.CHECKING)
    val versionStatus: StateFlow<VersionStatus> = _versionStatus.asStateFlow()

    private val _versionConfig = MutableStateFlow(AppVersionConfig())
    val versionConfig: StateFlow<AppVersionConfig> = _versionConfig.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    /**
     * Synchronous boolean check for repositories, sync managers, and background routines.
     * Only returns true if version validation explicitly succeeded.
     */
    fun isVersionSupported(): Boolean {
        return _versionStatus.value == VersionStatus.SUPPORTED
    }

    /**
     * Checks the application version against Firestore `app_config/app_version`.
     * Blocks execution until the check completes or fails.
     */
    suspend fun checkVersion(): VersionStatus = withContext(Dispatchers.IO) {
        _versionStatus.value = VersionStatus.CHECKING
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection(COLLECTION_APP_CONFIG).document(DOC_APP_VERSION).get().await()

            if (doc.exists()) {
                val minSupported = doc.getLong("minSupportedVersionCode")?.toInt() ?: 1
                val latestCode = doc.getLong("latestVersionCode")?.toInt() ?: 1
                val latestName = doc.getString("latestVersionName") ?: "1.0"
                val forceUpdate = doc.getBoolean("forceUpdate") ?: false
                val updateUrl = doc.getString("updateUrl") ?: ""
                val title = doc.getString("title") ?: "Please Update"
                val message = doc.getString("message")
                    ?: "A new version of the Jaiti Foundation App is available. Please update the app to continue."

                val config = AppVersionConfig(
                    minSupportedVersionCode = minSupported,
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    forceUpdate = forceUpdate,
                    updateUrl = updateUrl,
                    title = title,
                    message = message
                )
                _versionConfig.value = config

                val currentVersionCode = BuildConfig.VERSION_CODE
                Log.d(TAG, "Version Check: Installed=$currentVersionCode, MinSupported=$minSupported, Latest=$latestCode")

                val status = if (currentVersionCode < minSupported) {
                    VersionStatus.UNSUPPORTED
                } else {
                    VersionStatus.SUPPORTED
                }
                _versionStatus.value = status

                // Start real-time monitoring of version changes
                startVersionListener(db)
                status
            } else {
                Log.w(TAG, "Document '$DOC_APP_VERSION' not found in '$COLLECTION_APP_CONFIG'. Assuming current version is valid.")
                // Fresh configuration default
                val config = AppVersionConfig(minSupportedVersionCode = 1)
                _versionConfig.value = config
                val status = VersionStatus.SUPPORTED
                _versionStatus.value = status
                startVersionListener(db)
                status
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version check failed: ${e.message}", e)
            _versionStatus.value = VersionStatus.CHECK_FAILED
            VersionStatus.CHECK_FAILED
        }
    }

    /**
     * Attaches a real-time listener to `app_config/app_version` so that if an admin bumps
     * minSupportedVersionCode while an outdated app is open, it gets immediately blocked.
     */
    private fun startVersionListener(db: FirebaseFirestore) {
        if (listenerRegistration != null) return
        listenerRegistration = db.collection(COLLECTION_APP_CONFIG).document(DOC_APP_VERSION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Version snapshot listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val minSupported = snapshot.getLong("minSupportedVersionCode")?.toInt() ?: 1
                    val latestCode = snapshot.getLong("latestVersionCode")?.toInt() ?: 1
                    val latestName = snapshot.getString("latestVersionName") ?: "1.0"
                    val forceUpdate = snapshot.getBoolean("forceUpdate") ?: false
                    val updateUrl = snapshot.getString("updateUrl") ?: ""
                    val title = snapshot.getString("title") ?: "Please Update"
                    val message = snapshot.getString("message")
                        ?: "A new version of the Jaiti Foundation App is available. Please update the app to continue."

                    val config = AppVersionConfig(
                        minSupportedVersionCode = minSupported,
                        latestVersionCode = latestCode,
                        latestVersionName = latestName,
                        forceUpdate = forceUpdate,
                        updateUrl = updateUrl,
                        title = title,
                        message = message
                    )
                    _versionConfig.value = config

                    val currentVersionCode = BuildConfig.VERSION_CODE
                    if (currentVersionCode < minSupported) {
                        Log.w(TAG, "Live version check: Current version ($currentVersionCode) is now lower than min supported ($minSupported)")
                        _versionStatus.value = VersionStatus.UNSUPPORTED
                    } else if (_versionStatus.value == VersionStatus.UNSUPPORTED && currentVersionCode >= minSupported) {
                        _versionStatus.value = VersionStatus.SUPPORTED
                    }
                }
            }
    }

    fun cleanup() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
