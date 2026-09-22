package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.StudentEntity
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun generateStudentsCsv(
        context: Context,
        students: List<StudentEntity>,
        classes: List<ClassEntity>,
        fileNamePrefix: String = "Jaiti_Students_List"
    ): File? {
        try {
            val exportDir = File(context.cacheDir, "csv_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "${fileNamePrefix}_$timeStamp.csv")
            val writer = FileWriter(file)

            val classMap = classes.associateBy { it.classId }

            // CSV Header with only Add Student form fields
            writer.append("Student Name,Date of Birth (DOB),J Class,Class in School,Father Name,Mother Name,Phone Number,Photo\n")

            // CSV Rows
            for (s in students) {
                val className = classMap[s.classId]?.className ?: BatchConstants.formatBatchDisplayName(s.classId).ifBlank { s.classId }
                val hasPhoto = if (s.photoUri.isNotBlank()) "Yes" else "No"
                writer.append("\"${escapeCsv(s.studentName)}\",")
                writer.append("\"${escapeCsv(s.dob)}\",")
                writer.append("\"${escapeCsv(className)}\",")
                writer.append("\"${escapeCsv(s.schoolClass)}\",")
                writer.append("\"${escapeCsv(s.fatherName)}\",")
                writer.append("\"${escapeCsv(s.motherName)}\",")
                writer.append("\"${escapeCsv(s.phoneNumber)}\",")
                writer.append("\"${hasPhoto}\"\n")
            }

            writer.flush()
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Directly saves CSV to device public Downloads folder using MediaStore (Android 10+) or Environment
     */
    fun saveStudentsCsvToDownloads(
        context: Context,
        students: List<StudentEntity>,
        classes: List<ClassEntity>,
        fileNamePrefix: String = "Jaiti_Students_List"
    ): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${fileNamePrefix}_$timeStamp.csv"
        val classMap = classes.associateBy { it.classId }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Jaiti_Foundation")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.append("Student Name,Date of Birth (DOB),J Class,Class in School,Father Name,Mother Name,Phone Number,Photo\n")
                        for (s in students) {
                            val className = classMap[s.classId]?.className ?: BatchConstants.formatBatchDisplayName(s.classId).ifBlank { s.classId }
                            val hasPhoto = if (s.photoUri.isNotBlank()) "Yes" else "No"
                            writer.append("\"${escapeCsv(s.studentName)}\",")
                            writer.append("\"${escapeCsv(s.dob)}\",")
                            writer.append("\"${escapeCsv(className)}\",")
                            writer.append("\"${escapeCsv(s.schoolClass)}\",")
                            writer.append("\"${escapeCsv(s.fatherName)}\",")
                            writer.append("\"${escapeCsv(s.motherName)}\",")
                            writer.append("\"${escapeCsv(s.phoneNumber)}\",")
                            writer.append("\"${hasPhoto}\"\n")
                        }
                        writer.flush()
                    }
                }
                return "Downloads/Jaiti_Foundation/$fileName"
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "Jaiti_Foundation")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)

                FileWriter(targetFile).use { writer ->
                    writer.append("Student Name,Date of Birth (DOB),J Class,Class in School,Father Name,Mother Name,Phone Number,Photo\n")
                    for (s in students) {
                        val className = classMap[s.classId]?.className ?: BatchConstants.formatBatchDisplayName(s.classId).ifBlank { s.classId }
                        val hasPhoto = if (s.photoUri.isNotBlank()) "Yes" else "No"
                        writer.append("\"${escapeCsv(s.studentName)}\",")
                        writer.append("\"${escapeCsv(s.dob)}\",")
                        writer.append("\"${escapeCsv(className)}\",")
                        writer.append("\"${escapeCsv(s.schoolClass)}\",")
                        writer.append("\"${escapeCsv(s.fatherName)}\",")
                        writer.append("\"${escapeCsv(s.motherName)}\",")
                        writer.append("\"${escapeCsv(s.phoneNumber)}\",")
                        writer.append("\"${hasPhoto}\"\n")
                    }
                    writer.flush()
                }
                return targetFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareStudentsCsv(context: Context, students: List<StudentEntity>, classes: List<ClassEntity>) {
        val file = generateStudentsCsv(context, students, classes) ?: run {
            android.widget.Toast.makeText(context, "Failed to generate CSV", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Jaiti Foundation - Students Enrolled List")
                putExtra(Intent.EXTRA_TEXT, "Attached is the enrolled students database export (${students.size} students) from Jaiti Foundation.")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(context.contentResolver, "Students_List.csv", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Students Data CSV").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not share CSV: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAttendanceCsv(
        context: Context,
        records: List<AttendanceRecordEntity>,
        fileNamePrefix: String = "Jaiti_Attendance_Report"
    ): File? {
        try {
            val exportDir = File(context.cacheDir, "csv_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, "${fileNamePrefix}_${System.currentTimeMillis()}.csv")
            val writer = FileWriter(file)

            // CSV Header
            writer.append("Attendance ID,Date,Student ID,Student Name,Class ID,Class Name,Status,Remark,Teacher ID,Teacher Name,Recorded At\n")

            // CSV Rows
            for (r in records) {
                val recordedAt = DateUtils.formatIsoToDisplay(r.date)
                writer.append("\"${escapeCsv(r.attendanceId)}\",")
                writer.append("\"${escapeCsv(r.date)}\",")
                writer.append("\"${escapeCsv(r.studentId)}\",")
                writer.append("\"${escapeCsv(r.studentName)}\",")
                writer.append("\"${escapeCsv(r.classId)}\",")
                writer.append("\"${escapeCsv(r.className)}\",")
                writer.append("\"${escapeCsv(r.status.name)}\",")
                writer.append("\"${escapeCsv(r.remark)}\",")
                writer.append("\"${escapeCsv(r.teacherId)}\",")
                writer.append("\"${escapeCsv(r.teacherName)}\",")
                writer.append("\"${recordedAt}\"\n")
            }

            writer.flush()
            writer.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareCsvFile(context: Context, csvFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, csvFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Jaiti Foundation Attendance Report CSV")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(context.contentResolver, "Attendance_Report.csv", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Grant explicit read permissions to apps handling this intent
            val resInfoList = context.packageManager.queryIntentActivities(
                shareIntent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
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
                    // Ignore per-package failures
                }
            }

            val chooser = Intent.createChooser(shareIntent, "Export Attendance CSV").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not export CSV: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
