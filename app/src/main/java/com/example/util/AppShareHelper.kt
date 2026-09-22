package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AppShareHelper {

    const val DEFAULT_APP_URL = "https://ais-pre-4huzitokrhjjdbtweappkh-837919443968.asia-southeast1.run.app"
    const val APP_NAME = "Jaiti Foundation – Attendance"
    private const val PREFS_NAME = "jaiti_app_share_prefs"
    private const val KEY_CUSTOM_DOWNLOAD_URL = "custom_download_url"
    private const val TAG = "AppShareHelper"

    fun getSavedDownloadUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_DOWNLOAD_URL, "")
        return if (!saved.isNullOrBlank()) saved else ""
    }

    fun saveDownloadUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_DOWNLOAD_URL, url.trim()).apply()
    }

    fun getShareText(context: Context, customUrl: String = ""): String {
        val urlToUse = customUrl.ifBlank { getSavedDownloadUrl(context) }
        val linkSection = if (urlToUse.isNotBlank()) {
            "\n🔗 *Download & Install Link:*\n$urlToUse\n"
        } else {
            "\n💡 *How to Install:*\nTap the shared .apk file directly in WhatsApp or File Manager, then click 'Install'.\n"
        }

        return """
            📲 *Jaiti Foundation – Attendance App*
            
            Official daily student attendance, educator management, and reporting app for Jaiti Foundation Learning Centres.
            $linkSection
            ✓ 100% Free & Offline-first attendance
            ✓ Class & Student records with photos
            ✓ Automated CSV Reports & Cloud Sync
        """.trimIndent()
    }

    /**
     * Share Web Link / Installation URL via Android Share Sheet
     */
    fun shareDownloadLink(context: Context, customUrl: String = "") {
        try {
            val urlToUse = customUrl.ifBlank { getSavedDownloadUrl(context) }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Download Jaiti Foundation Attendance App")
                putExtra(Intent.EXTRA_TEXT, getShareText(context, urlToUse))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share App Link via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening share sheet: ${e.message}", e)
            Toast.makeText(context, "Could not open share sheet: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copy download link to clipboard
     */
    fun copyLinkToClipboard(context: Context, url: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Jaiti App Link", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy link", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share the actual installed APK file directly (via WhatsApp, Quick Share, Bluetooth, Drive, etc.)
     * Uses background coroutine for smooth copying, sets correct FileProvider permissions and flags.
     */
    fun shareApkFile(context: Context) {
        Toast.makeText(context, "Preparing APK for sharing...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appInfo = context.applicationInfo
                val sourceApkPath = appInfo.sourceDir
                val sourceApk = File(sourceApkPath)

                if (!sourceApk.exists() || !sourceApk.canRead()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot read source APK directly. Opening download link...", Toast.LENGTH_SHORT).show()
                        shareDownloadLink(context)
                    }
                    return@launch
                }

                // Prepare destination directory
                val sharedDir = File(context.cacheDir, "shared_apk")
                if (!sharedDir.exists()) {
                    sharedDir.mkdirs()
                }

                val destApk = File(sharedDir, "Jaiti_Foundation_Attendance.apk")

                // Only copy if not already copied or if source is newer/different size
                if (!destApk.exists() || destApk.length() != sourceApk.length()) {
                    destApk.delete()
                    FileInputStream(sourceApk).use { input ->
                        FileOutputStream(destApk).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                            output.flush()
                        }
                    }
                }

                destApk.setReadable(true, false)

                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, destApk)

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "Jaiti_Foundation_Attendance.apk", uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Jaiti Foundation Attendance App (APK)")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    // Grant explicit read permissions to all apps that can handle this intent
                    val resInfoList = context.packageManager.queryIntentActivities(
                        shareIntent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                    for (resolveInfo in resInfoList) {
                        val packageName = resolveInfo.activityInfo.packageName
                        try {
                            context.grantUriPermission(
                                packageName,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not grant URI permission to $packageName: ${e.message}")
                        }
                    }

                    val chooser = Intent.createChooser(shareIntent, "Share APK File (WhatsApp / Quick Share / Bluetooth)").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing APK: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Direct APK share failed (${e.localizedMessage}). Sharing link...", Toast.LENGTH_LONG).show()
                    shareDownloadLink(context)
                }
            }
        }
    }
}
