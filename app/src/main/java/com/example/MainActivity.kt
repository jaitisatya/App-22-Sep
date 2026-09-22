package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.DatabaseInitializer
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.model.VersionStatus
import com.example.data.repository.AppVersionManager
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.TestExamRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UpdateRequiredScreen
import com.example.ui.screens.VersionCheckFailedScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val syncManager by lazy { FirestoreSyncManager(applicationContext, database) }
    private val authRepository by lazy { AuthRepository(database.userDao(), database, syncManager, applicationContext) }
    private val authViewModel by lazy { AuthViewModel(authRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Theme Provider / Preferences
        com.example.ui.theme.ThemeManager.initialize(applicationContext)

        // Set global Coil ImageLoader with Memory + Disk cache
        coil.Coil.setImageLoader(com.example.util.ImageUtils.getImageLoader(applicationContext))

        // Background startup sync and seeding (Never blocks main thread / UI rendering)
        // STRICT GATE: Abort immediately if app version is unsupported or fails validation
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val status = AppVersionManager.checkVersion()
                if (status != VersionStatus.SUPPORTED) {
                    Log.w("MainActivity", "Startup cloud sync aborted: Version status is $status")
                    return@launch
                }
                DatabaseInitializer.seedDatabaseIfEmpty(database)
                DatabaseInitializer.purgeDemoStudents(database, syncManager)
                DatabaseInitializer.syncStandardClasses(database, syncManager)
                // Note: Keep assigned student IDs stable; do not reshuffle IDs alphabetically on startup
                DatabaseInitializer.reconcileAttendanceRecordsWithStudents(database, syncManager)
                syncManager.fetchAllAttendanceFromCloud()
                syncManager.syncAllLocalToCloud()

                // Real-time listen and initial sync for daily class attendance photos across all devices
                com.example.data.repository.ClassPhotoManager.startListening(applicationContext)
                com.example.data.repository.ClassPhotoManager.syncAllDailyPhotosFromCloud(applicationContext)
            } catch (e: Throwable) {
                Log.w("MainActivity", "Background startup sync/seed (non-blocking): ${e.message}")
            }
        }

        // Render Compose UI immediately
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val versionStatus by AppVersionManager.versionStatus.collectAsState()
                    val versionConfig by AppVersionManager.versionConfig.collectAsState()

                    when (versionStatus) {
                        VersionStatus.CHECKING -> {
                            // Smooth branding splash screen while checking version
                            SplashScreen()
                        }
                        VersionStatus.UNSUPPORTED -> {
                            // Mandatory update screen: no bypass, locks out entire app
                            UpdateRequiredScreen(config = versionConfig)
                        }
                        VersionStatus.CHECK_FAILED -> {
                            // Offline or network error: block cloud writes, show retry screen
                            VersionCheckFailedScreen(
                                onRetry = {
                                    lifecycleScope.launch {
                                        AppVersionManager.checkVersion()
                                    }
                                }
                            )
                        }
                        VersionStatus.SUPPORTED -> {
                            val currentUser by authViewModel.currentUser.collectAsState()
                            val isSessionRestoring by authViewModel.isSessionRestoring.collectAsState()

                            if (isSessionRestoring && currentUser == null) {
                                // Smooth branding splash screen while instant session is restoring (no login screen flicker)
                                SplashScreen()
                            } else if (currentUser == null) {
                                LoginScreen(
                                    authViewModel = authViewModel,
                                    onLoginSuccess = { }
                                )
                            } else {
                                val user = currentUser!!

                                // Monitor active device session in real-time (instant force logout if admin deactivates/deletes)
                                androidx.compose.runtime.LaunchedEffect(user.userId) {
                                    com.example.data.repository.ActiveDeviceSessionManager.startMonitoringCurrentSession(
                                        applicationContext,
                                        user.userId
                                    ) { reason: String, _ ->
                                        lifecycleScope.launch(Dispatchers.Main) {
                                            android.widget.Toast.makeText(applicationContext, reason, android.widget.Toast.LENGTH_LONG).show()
                                            authViewModel.logout()
                                        }
                                    }
                                }

                                val classRepository = remember { ClassRepository(database.classDao(), syncManager) }
                                val studentRepository = remember { StudentRepository(database.studentDao(), syncManager) }
                                val attendanceRepository = remember { AttendanceRepository(database.attendanceDao(), syncManager) }

                                val attendanceViewModel = remember { AttendanceViewModel(attendanceRepository, studentRepository, classRepository) }
                                val classViewModel = remember { ClassViewModel(classRepository, studentRepository, database.userDao()) }
                                val studentViewModel = remember { StudentViewModel(studentRepository, classRepository, attendanceRepository) }
                                val reportViewModel = remember { ReportViewModel(attendanceRepository, classRepository, studentRepository) }
                                val testExamRepository = remember { TestExamRepository(database.testExamDao(), syncManager) }
                                val testExamViewModel = remember { TestExamViewModel(testExamRepository) }

                                MainScreen(
                                    currentUser = user,
                                    authViewModel = authViewModel,
                                    attendanceViewModel = attendanceViewModel,
                                    classViewModel = classViewModel,
                                    studentViewModel = studentViewModel,
                                    reportViewModel = reportViewModel,
                                    testExamViewModel = testExamViewModel,
                                    onLogout = { }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            syncManager.cleanup()
            AppVersionManager.cleanup()
        } catch (e: Throwable) {
            Log.w("MainActivity", "Cleanup note: ${e.message}")
        }
    }
}


