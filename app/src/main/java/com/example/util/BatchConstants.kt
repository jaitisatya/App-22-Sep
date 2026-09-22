package com.example.util

object BatchConstants {
    val STANDARD_BATCH_OPTIONS = listOf(
        "J Prep",
        "J1",
        "J2",
        "J3",
        "J4",
        "J5"
    )

    val STANDARD_SCHOOL_CLASS_OPTIONS = listOf(
        "Nursery",
        "LKG",
        "UKG",
        "Prep",
        "Class 1",
        "Class 2",
        "Class 3",
        "Class 4",
        "Class 5",
        "Class 6",
        "Class 7",
        "Class 8",
        "Class 9",
        "Class 10",
        "Class 11",
        "Class 12"
    )

    fun getStandardClassId(batchName: String): String {
        val clean = batchName.trim().uppercase()
        return when {
            clean == "J PREP" || clean == "J_PREP" || clean == "PREP" || clean == "CLASS_PREP" -> "CLASS_J_PREP"
            clean == "J1" || clean == "CLASS 1" || clean == "CLASS_1" || clean == "CLASS_J1" -> "CLASS_J1"
            clean == "J2" || clean == "CLASS 2" || clean == "CLASS_2" || clean == "CLASS_J2" -> "CLASS_J2"
            clean == "J3" || clean == "CLASS 3" || clean == "CLASS_3" || clean == "CLASS_J3" -> "CLASS_J3"
            clean == "J4" || clean == "CLASS 4" || clean == "CLASS_4" || clean == "CLASS_J4" -> "CLASS_J4"
            clean == "J5" || clean == "CLASS 5" || clean == "CLASS_5" || clean == "CLASS_J5" -> "CLASS_J5"
            clean == "J6" || clean == "CLASS 6" || clean == "CLASS_6" || clean == "CLASS_J6" -> "CLASS_J6"
            clean == "J7" || clean == "CLASS 7" || clean == "CLASS_7" || clean == "CLASS_J7" -> "CLASS_J7"
            clean == "J8" || clean == "CLASS 8" || clean == "CLASS_8" || clean == "CLASS_J8" -> "CLASS_J8"
            clean == "J9" || clean == "CLASS 9" || clean == "CLASS_9" || clean == "CLASS_J9" -> "CLASS_J9"
            clean == "J10" || clean == "CLASS 10" || clean == "CLASS_10" || clean == "CLASS_J10" -> "CLASS_J10"
            clean == "J11" || clean == "CLASS 11" || clean == "CLASS_11" || clean == "CLASS_J11" -> "CLASS_J11"
            clean == "J12" || clean == "CLASS 12" || clean == "CLASS_12" || clean == "CLASS_J12" -> "CLASS_J12"
            clean == "NURSERY" || clean == "J NURSERY" -> "CLASS_J_NURSERY"
            clean == "LKG" || clean == "J LKG" -> "CLASS_J_LKG"
            clean == "UKG" || clean == "J UKG" -> "CLASS_J_UKG"
            clean.startsWith("CLASS_") -> clean
            clean.startsWith("CLASS ") -> "CLASS_" + clean.removePrefix("CLASS ").trim().replace(" ", "_")
            else -> "CLASS_$clean"
        }
    }

    fun formatBatchDisplayName(classId: String): String {
        val clean = classId.trim()
        val upper = clean.uppercase()
        return when {
            upper == "CLASS_J_PREP" || upper == "CLASS_PREP" || upper == "PREP" || upper == "J PREP" -> "J Prep"
            upper == "CLASS_J1" || upper == "CLASS_1" || upper == "J1" -> "J1"
            upper == "CLASS_J2" || upper == "CLASS_2" || upper == "J2" -> "J2"
            upper == "CLASS_J3" || upper == "CLASS_3" || upper == "J3" -> "J3"
            upper == "CLASS_J4" || upper == "CLASS_4" || upper == "J4" -> "J4"
            upper == "CLASS_J5" || upper == "CLASS_5" || upper == "J5" -> "J5"
            upper == "CLASS_J6" || upper == "CLASS_6" || upper == "J6" -> "J6"
            upper == "CLASS_J7" || upper == "CLASS_7" || upper == "J7" -> "J7"
            upper == "CLASS_J8" || upper == "CLASS_8" || upper == "J8" -> "J8"
            upper == "CLASS_J9" || upper == "CLASS_9" || upper == "J9" -> "J9"
            upper == "CLASS_J10" || upper == "CLASS_10" || upper == "J10" -> "J10"
            upper == "CLASS_J11" || upper == "CLASS_11" || upper == "J11" -> "J11"
            upper == "CLASS_J12" || upper == "CLASS_12" || upper == "J12" -> "J12"
            upper == "CLASS_J_NURSERY" || upper == "CLASS_NURSERY" || upper == "NURSERY" -> "Nursery"
            upper == "CLASS_J_LKG" || upper == "CLASS_LKG" || upper == "LKG" -> "LKG"
            upper == "CLASS_J_UKG" || upper == "CLASS_UKG" || upper == "UKG" -> "UKG"
            clean.startsWith("CLASS_J", ignoreCase = true) -> clean.substring(6)
            clean.startsWith("CLASS_", ignoreCase = true) -> {
                val suffix = clean.substring(6)
                if (suffix.toIntOrNull() != null) "J$suffix" else suffix
            }
            clean.startsWith("CLASS ", ignoreCase = true) -> {
                val suffix = clean.substring(6)
                if (suffix.toIntOrNull() != null) "J$suffix" else clean
            }
            else -> clean
        }
    }

    fun getBatchSortOrder(className: String): Int {
        val upper = className.uppercase().trim()
        return when {
            upper.contains("NURSERY") -> 1
            upper.contains("LKG") -> 2
            upper.contains("UKG") -> 3
            upper.contains("PREP") -> 4
            upper.contains("J10") || upper.contains("CLASS 10") || upper == "CLASS_10" || upper == "CLASS_J10" -> 100
            upper.contains("J11") || upper.contains("CLASS 11") || upper == "CLASS_11" || upper == "CLASS_J11" -> 110
            upper.contains("J12") || upper.contains("CLASS 12") || upper == "CLASS_12" || upper == "CLASS_J12" -> 120
            upper.contains("J1") || upper.contains("CLASS 1") || upper == "CLASS_1" || upper == "CLASS_J1" -> 10
            upper.contains("J2") || upper.contains("CLASS 2") || upper == "CLASS_2" || upper == "CLASS_J2" -> 20
            upper.contains("J3") || upper.contains("CLASS 3") || upper == "CLASS_3" || upper == "CLASS_J3" -> 30
            upper.contains("J4") || upper.contains("CLASS 4") || upper == "CLASS_4" || upper == "CLASS_J4" -> 40
            upper.contains("J5") || upper.contains("CLASS 5") || upper == "CLASS_5" || upper == "CLASS_J5" -> 50
            upper.contains("J6") || upper.contains("CLASS 6") || upper == "CLASS_6" || upper == "CLASS_J6" -> 60
            upper.contains("J7") || upper.contains("CLASS 7") || upper == "CLASS_7" || upper == "CLASS_J7" -> 70
            upper.contains("J8") || upper.contains("CLASS 8") || upper == "CLASS_8" || upper == "CLASS_J8" -> 80
            upper.contains("J9") || upper.contains("CLASS 9") || upper == "CLASS_9" || upper == "CLASS_J9" -> 90
            else -> 500
        }
    }
}
