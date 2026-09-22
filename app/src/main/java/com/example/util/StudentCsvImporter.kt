package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.entity.StudentEntity
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

data class CsvImportResult(
    val totalRowsRead: Int,
    val validNewStudents: List<StudentEntity>,
    val skippedDuplicates: List<Pair<StudentEntity, String>>, // Student and reason
    val invalidRowsCount: Int,
    val errorMessage: String? = null
)

object StudentCsvImporter {

    /**
     * Checks if incoming student is a duplicate of existing student based on:
     * - Name is identical (ignoring case & whitespace), AND
     * - At least one of Father's Name, Mother's Name, or Date of Birth (DOB) is identical.
     */
    fun isDuplicateStudent(incoming: StudentEntity, existing: StudentEntity): Boolean {
        val incName = incoming.studentName.trim()
        val extName = existing.studentName.trim()

        if (incName.isBlank() || extName.isBlank()) return false
        if (!incName.equals(extName, ignoreCase = true)) return false

        val incFather = incoming.fatherName.trim()
        val extFather = existing.fatherName.trim()
        val fatherMatch = incFather.isNotBlank() && extFather.isNotBlank() && incFather.equals(extFather, ignoreCase = true)

        val incMother = incoming.motherName.trim()
        val extMother = existing.motherName.trim()
        val motherMatch = incMother.isNotBlank() && extMother.isNotBlank() && incMother.equals(extMother, ignoreCase = true)

        val incDob = incoming.dob.trim()
        val extDob = existing.dob.trim()
        val dobMatch = incDob.isNotBlank() && extDob.isNotBlank() && incDob.equals(extDob, ignoreCase = true)

        return fatherMatch || motherMatch || dobMatch
    }

    /**
     * Parse CSV from an Android content Uri and filter out duplicate entries
     * against both existing database students and earlier rows in the same CSV batch.
     */
    fun parseAndFilterCsv(
        context: Context,
        uri: Uri,
        existingStudents: List<StudentEntity>,
        defaultClassId: String = ""
    ): CsvImportResult {
        val validNewList = mutableListOf<StudentEntity>()
        val skippedDuplicates = mutableListOf<Pair<StudentEntity, String>>()
        var invalidCount = 0
        var totalRows = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return CsvImportResult(0, emptyList(), emptyList(), 0, "Could not open selected CSV file")

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val lines = mutableListOf<String>()
            var rawLine: String?
            while (reader.readLine().also { rawLine = it } != null) {
                val trimmed = rawLine?.trim() ?: ""
                if (trimmed.isNotEmpty()) {
                    lines.add(trimmed)
                }
            }
            reader.close()
            inputStream.close()

            if (lines.isEmpty()) {
                return CsvImportResult(0, emptyList(), emptyList(), 0, "CSV file is empty")
            }

            // Detect delimiter (, or ; or \t)
            val headerLine = lines.first().removePrefix("\uFEFF") // Remove potential UTF-8 BOM
            val delimiter = when {
                headerLine.contains(",") -> ','
                headerLine.contains(";") -> ';'
                headerLine.contains("\t") -> '\t'
                else -> ','
            }

            val headers = parseCsvRow(headerLine, delimiter).map { normalizeHeader(it) }
            val dataLines = lines.drop(1)

            // Determine starting sequence number for new imports
            var nextSeq = 0
            for (st in existingStudents) {
                val num = StudentIdUtils.extractSequenceNumber(st.studentId)
                if (num != null && num > nextSeq) {
                    nextSeq = num
                }
            }
            if (nextSeq == 0 && existingStudents.isNotEmpty()) {
                nextSeq = existingStudents.size
            }

            // Find column indices
            val nameIdx = findHeaderIndex(headers, listOf("name", "studentname", "student_name", "student", "fullname", "full_name", "naam", "bachhe_ka_naam"))
            val fatherIdx = findHeaderIndex(headers, listOf("father", "fathername", "father_name", "fathers_name", "father's_name", "guardian", "guardian_name", "pita", "pita_ka_naam"))
            val motherIdx = findHeaderIndex(headers, listOf("mother", "mothername", "mother_name", "mothers_name", "mother's_name", "mata", "mata_ka_naam"))
            val dobIdx = findHeaderIndex(headers, listOf("dob", "dateofbirth", "date_of_birth", "birthdate", "birth_date", "d_o_b", "d.o.b.", "janm_tithi", "birthday"))
            val classIdx = findHeaderIndex(headers, listOf("class", "classid", "class_id", "classname", "class_name", "batch", "jaitibatch", "jaiti_batch", "jaiticlass", "jaiti_class", "section"))
            val schoolClassIdx = findHeaderIndex(headers, listOf("schoolclass", "school_class", "classinschool", "class_in_school", "standard", "std", "grade", "kaksha"))
            val phoneIdx = findHeaderIndex(headers, listOf("phone", "phonenumber", "phone_number", "mobile", "mobilenumber", "mobile_number", "contact", "contactnumber", "contact_no", "phone_no"))
            val areaIdx = findHeaderIndex(headers, listOf("area", "areaname", "area_name", "slum", "slum_area", "location", "address", "pata"))
            val schoolIdx = findHeaderIndex(headers, listOf("school", "schoolname", "school_name", "vidyalaya"))
            val genderIdx = findHeaderIndex(headers, listOf("gender", "sex", "ling"))
            val ageIdx = findHeaderIndex(headers, listOf("age", "umar", "aayu"))

            for (line in dataLines) {
                totalRows++
                val cells = parseCsvRow(line, delimiter)
                if (cells.isEmpty()) {
                    invalidCount++
                    continue
                }

                val studentName = if (nameIdx != -1 && nameIdx < cells.size) cells[nameIdx].trim() else ""
                if (studentName.isBlank()) {
                    invalidCount++
                    continue
                }

                val fatherName = if (fatherIdx != -1 && fatherIdx < cells.size) cells[fatherIdx].trim() else ""
                val motherName = if (motherIdx != -1 && motherIdx < cells.size) cells[motherIdx].trim() else ""
                val dob = if (dobIdx != -1 && dobIdx < cells.size) cells[dobIdx].trim() else ""
                val rawClass = if (classIdx != -1 && classIdx < cells.size) cells[classIdx].trim() else ""
                val schoolClass = if (schoolClassIdx != -1 && schoolClassIdx < cells.size) cells[schoolClassIdx].trim() else ""
                val phone = if (phoneIdx != -1 && phoneIdx < cells.size) cells[phoneIdx].trim() else ""
                val area = if (areaIdx != -1 && areaIdx < cells.size) cells[areaIdx].trim() else ""
                val schoolName = if (schoolIdx != -1 && schoolIdx < cells.size) cells[schoolIdx].trim() else ""
                val gender = if (genderIdx != -1 && genderIdx < cells.size) cells[genderIdx].trim() else "Male"
                val ageStr = if (ageIdx != -1 && ageIdx < cells.size) cells[ageIdx].trim() else "0"
                val age = ageStr.filter { it.isDigit() }.toIntOrNull() ?: 0

                val classId = when {
                    rawClass.isNotBlank() -> BatchConstants.getStandardClassId(rawClass)
                    defaultClassId.isNotBlank() -> defaultClassId
                    else -> BatchConstants.getStandardClassId("J Prep")
                }

                val candidateId = StudentIdUtils.formatStudentId(nextSeq + validNewList.size + 1)
                val candidate = StudentEntity(
                    studentId = candidateId,
                    studentName = studentName,
                    fatherName = fatherName,
                    motherName = motherName,
                    dob = dob,
                    classId = classId,
                    schoolClass = schoolClass,
                    phoneNumber = phone,
                    areaName = area.ifBlank { "Sanjay Camp" },
                    schoolName = schoolName.ifBlank { "Jaiti Learning Centre" },
                    gender = if (gender.startsWith("F", ignoreCase = true) || gender.contains("Female", ignoreCase = true) || gender.contains("Girl", ignoreCase = true)) "Female" else "Male",
                    age = age,
                    photoUri = "",
                    notes = "Imported from CSV",
                    active = true,
                    createdTimestamp = System.currentTimeMillis()
                )

                // 1. Check duplicate against existing database students
                val existingMatch = existingStudents.firstOrNull { isDuplicateStudent(candidate, it) }
                if (existingMatch != null) {
                    val matchDetail = when {
                        existingMatch.fatherName.isNotBlank() && existingMatch.fatherName.equals(candidate.fatherName, ignoreCase = true) -> "Father: ${candidate.fatherName}"
                        existingMatch.motherName.isNotBlank() && existingMatch.motherName.equals(candidate.motherName, ignoreCase = true) -> "Mother: ${candidate.motherName}"
                        existingMatch.dob.isNotBlank() && existingMatch.dob.equals(candidate.dob, ignoreCase = true) -> "DOB: ${candidate.dob}"
                        else -> "Same Identity"
                    }
                    skippedDuplicates.add(Pair(candidate, "Already in database ($matchDetail)"))
                    continue
                }

                // 2. Check duplicate against earlier processed rows in the same CSV
                val batchMatch = validNewList.firstOrNull { isDuplicateStudent(candidate, it) }
                if (batchMatch != null) {
                    skippedDuplicates.add(Pair(candidate, "Duplicate row in CSV batch"))
                    continue
                }

                // Clean & unique entry!
                validNewList.add(candidate)
            }

            return CsvImportResult(
                totalRowsRead = totalRows,
                validNewStudents = validNewList,
                skippedDuplicates = skippedDuplicates,
                invalidRowsCount = invalidCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return CsvImportResult(0, emptyList(), emptyList(), 0, "Failed to parse CSV: ${e.localizedMessage}")
        }
    }

    private fun normalizeHeader(header: String): String {
        return header.lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace("'", "")
            .replace("\"", "")
            .replace(".", "")
            .trim()
    }

    private fun findHeaderIndex(headers: List<String>, candidateKeys: List<String>): Int {
        for (candidate in candidateKeys) {
            val normalized = normalizeHeader(candidate)
            val index = headers.indexOfFirst { it == normalized || it.contains(normalized) }
            if (index != -1) return index
        }
        return -1
    }

    private fun parseCsvRow(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val curVal = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    curVal.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(curVal.toString().trim())
                curVal.setLength(0)
            } else {
                curVal.append(c)
            }
            i++
        }
        result.add(curVal.toString().trim())
        return result
    }

    /**
     * Generates a sample CSV template file for teachers to fill and shares it.
     */
    fun shareSampleCsvTemplate(context: Context) {
        try {
            val templateDir = File(context.cacheDir, "csv_templates")
            if (!templateDir.exists()) templateDir.mkdirs()

            val file = File(templateDir, "Jaiti_Students_Sample_Template.csv")
            val writer = FileWriter(file)

            // Standard clean CSV Header
            writer.append("Student Name,Father Name,Mother Name,Date of Birth,Jaiti Batch,School Class,Phone Number,Slum Area,School Name,Gender\n")
            // Sample Rows
            writer.append("\"Aarav Kumar\",\"Ramesh Kumar\",\"Sunita Devi\",\"2015-04-12\",\"J 1\",\"3rd\",\"9876543210\",\"Sanjay Camp\",\"Govt Boys Sr Sec School\",\"Male\"\n")
            writer.append("\"Ananya Sharma\",\"Rajesh Sharma\",\"Pooja Sharma\",\"2016-08-25\",\"J 2\",\"2nd\",\"9812345678\",\"Tigri\",\"Govt Girls School\",\"Female\"\n")
            writer.append("\"Rahul Verma\",\"Suresh Verma\",\"Rekha Verma\",\"2014-11-05\",\"J Prep\",\"4th\",\"9988776655\",\"Sanjay Camp\",\"MCD Primary School\",\"Male\"\n")

            writer.flush()
            writer.close()

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Jaiti Students CSV Template")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(context.contentResolver, "Jaiti_Students_Sample_Template.csv", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Download/Share Student CSV Template").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
