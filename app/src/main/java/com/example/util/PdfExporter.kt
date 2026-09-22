package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.entity.AttendanceRecordEntity
import com.example.data.model.AttendanceStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // Standard A4 points at 72 DPI (width)
    private const val PAGE_HEIGHT = 842 // Standard A4 points at 72 DPI (height)
    private const val MARGIN = 36f

    fun generateAttendancePdf(
        context: Context,
        records: List<AttendanceRecordEntity>,
        startDateIso: String,
        endDateIso: String,
        classFilterName: String = "All Classes",
        fileNamePrefix: String = "Jaiti_Attendance_Report"
    ): File? {
        if (records.isEmpty()) return null

        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Summary calculations
        val totalRecords = records.size
        val presentCount = records.count { it.status == AttendanceStatus.PRESENT }
        val absentCount = records.count { it.status == AttendanceStatus.ABSENT }
        val attendancePercentage = if (totalRecords > 0) (presentCount.toFloat() / totalRecords) * 100f else 0f

        var currentY = MARGIN

        // Function to draw header
        fun drawHeader(c: Canvas) {
            // Header Top Bar Background
            paint.color = Color.rgb(14, 165, 233) // Sky Blue
            val headerRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 48f)
            c.drawRoundRect(headerRect, 8f, 8f, paint)

            // Header Title
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("JAITI FOUNDATION - ATTENDANCE REPORT", MARGIN + 14f, currentY + 22f, paint)

            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            c.drawText("Generated on: $generatedDate", MARGIN + 14f, currentY + 38f, paint)

            currentY += 58f
        }

        // Draw First Page Header & Summary Card
        drawHeader(canvas)

        // Summary Info Box
        paint.color = Color.rgb(241, 245, 249) // Slate 100
        val summaryRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 68f)
        canvas.drawRoundRect(summaryRect, 6f, 6f, paint)

        // Border around summary
        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(summaryRect, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        // Summary Text Items
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val formattedStart = DateUtils.formatIsoToDisplay(startDateIso)
        val formattedEnd = DateUtils.formatIsoToDisplay(endDateIso)
        canvas.drawText("Period: $formattedStart to $formattedEnd", MARGIN + 12f, currentY + 20f, paint)
        canvas.drawText("Class / Center: $classFilterName", MARGIN + 12f, currentY + 38f, paint)
        canvas.drawText("Total Recorded: $totalRecords", MARGIN + 12f, currentY + 56f, paint)

        // Metrics on the right side
        paint.color = Color.rgb(22, 163, 74) // Present Green
        canvas.drawText("Present: $presentCount", PAGE_WIDTH - MARGIN - 170f, currentY + 20f, paint)

        paint.color = Color.rgb(220, 38, 38) // Absent Red
        canvas.drawText("Absent: $absentCount", PAGE_WIDTH - MARGIN - 170f, currentY + 38f, paint)

        paint.color = Color.rgb(14, 165, 233) // Sky Blue
        paint.textSize = 12f
        canvas.drawText("Attendance: %.1f%%".format(attendancePercentage), PAGE_WIDTH - MARGIN - 170f, currentY + 58f, paint)

        currentY += 82f

        // Table Header
        fun drawTableHeader(c: Canvas) {
            paint.color = Color.rgb(226, 232, 240) // Slate 200
            val thRect = RectF(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + 22f)
            c.drawRect(thRect, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            c.drawText("#", MARGIN + 6f, currentY + 14f, paint)
            c.drawText("Date", MARGIN + 28f, currentY + 14f, paint)
            c.drawText("Student Name", MARGIN + 95f, currentY + 14f, paint)
            c.drawText("Class", MARGIN + 235f, currentY + 14f, paint)
            c.drawText("Status", MARGIN + 335f, currentY + 14f, paint)
            c.drawText("Marked By / Remark", MARGIN + 395f, currentY + 14f, paint)

            currentY += 24f
        }

        drawTableHeader(canvas)

        // Table Rows
        val rowHeight = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f

        for ((index, rec) in records.withIndex()) {
            // Check if row exceeds page height (leave margin for footer)
            if (currentY + rowHeight > PAGE_HEIGHT - MARGIN - 20f) {
                // Draw Footer for current page
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 8f
                canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f - 15f, PAGE_HEIGHT - MARGIN + 10f, paint)

                document.finishPage(page)

                // Start new page
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN

                // Draw header & table header on next page
                drawHeader(canvas)
                drawTableHeader(canvas)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 8.5f
            }

            // Alternating row background
            if (index % 2 == 0) {
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN, currentY - 2f, PAGE_WIDTH - MARGIN, currentY + rowHeight - 2f, paint)
            }

            // S.No
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("${index + 1}", MARGIN + 6f, currentY + 11f, paint)

            // Date
            canvas.drawText(DateUtils.formatIsoToDisplay(rec.date), MARGIN + 28f, currentY + 11f, paint)

            // Student Name (Truncate if too long)
            val truncatedName = if (rec.studentName.length > 22) rec.studentName.take(20) + ".." else rec.studentName
            canvas.drawText(truncatedName, MARGIN + 95f, currentY + 11f, paint)

            // Class Name
            val truncatedClass = if (rec.className.length > 16) rec.className.take(14) + ".." else rec.className
            canvas.drawText(truncatedClass, MARGIN + 235f, currentY + 11f, paint)

            // Status Pill
            if (rec.status == AttendanceStatus.PRESENT) {
                paint.color = Color.rgb(22, 163, 74) // Green
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PRESENT", MARGIN + 335f, currentY + 11f, paint)
            } else {
                paint.color = Color.rgb(220, 38, 38) // Red
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("ABSENT", MARGIN + 335f, currentY + 11f, paint)
            }
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            // Marked By / Remark
            paint.color = Color.rgb(100, 116, 139)
            val info = if (rec.remark.isNotBlank()) rec.remark else rec.teacherName.ifBlank { "Recorded" }
            val truncatedInfo = if (info.length > 20) info.take(18) + ".." else info
            canvas.drawText(truncatedInfo, MARGIN + 395f, currentY + 11f, paint)

            currentY += rowHeight
        }

        // Draw Footer for last page
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 8f
        canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f - 15f, PAGE_HEIGHT - MARGIN + 10f, paint)

        document.finishPage(page)

        // Save PDF file
        return try {
            val exportDir = File(context.cacheDir, "pdf_exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val sanitizedStart = startDateIso.replace("-", "")
            val sanitizedEnd = endDateIso.replace("-", "")
            val file = File(exportDir, "${fileNamePrefix}_${sanitizedStart}_${sanitizedEnd}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            document.close()
            outputStream.flush()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }

    fun sharePdfFile(context: Context, pdfFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "Jaiti Foundation Attendance Report (PDF)")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Attendance_Report.pdf", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Grant permissions to apps handling intent
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
                } catch (_: Exception) {}
            }

            val chooser = Intent.createChooser(shareIntent, "Export & Share Attendance PDF").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not share PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
