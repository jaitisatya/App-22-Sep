package com.example.util

import com.example.data.entity.StudentEntity

object StudentIdUtils {
    private const val PREFIX = "JF"

    /**
     * Extracts numerical sequence from a student ID like "JF0001" or "JF0108" or "STU_123".
     */
    fun extractSequenceNumber(studentId: String): Int? {
        val trimmed = studentId.trim()
        if (trimmed.startsWith(PREFIX, ignoreCase = true)) {
            val numPart = trimmed.substring(PREFIX.length).trimStart('0')
            return numPart.toIntOrNull() ?: if (trimmed.substring(PREFIX.length).all { it == '0' }) 0 else null
        }
        return null
    }

    /**
     * Formats integer number into standard "JF0001" representation.
     */
    fun formatStudentId(number: Int): String {
        return "$PREFIX%04d".format(number.coerceAtLeast(1))
    }

    /**
     * Calculates the next available JF ID based on existing students in the system.
     */
    fun getNextStudentId(existingStudents: List<StudentEntity>): String {
        var maxNum = 0
        for (st in existingStudents) {
            val num = extractSequenceNumber(st.studentId)
            if (num != null && num > maxNum) {
                maxNum = num
            }
        }
        // If there are existing students with old format (e.g. STU_...) and no JF prefix yet,
        // we start counting from existing size + 1
        if (maxNum == 0 && existingStudents.isNotEmpty()) {
            maxNum = existingStudents.size
        }
        return formatStudentId(maxNum + 1)
    }

    /**
     * Returns a displayable or formatted student ID.
     */
    fun toDisplayId(studentId: String): String {
        val trimmed = studentId.trim()
        if (trimmed.startsWith(PREFIX, ignoreCase = true)) {
            return trimmed.uppercase()
        }
        return trimmed
    }
}
