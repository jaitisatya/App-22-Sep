package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDayItem(
    val isoDate: String,
    val dayName: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isSunday: Boolean = false,
    val monthShort: String = "",
    val monthYear: String = ""
)

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val shortDisplayFormat = SimpleDateFormat("dd MMM", Locale.US)
    private val dateMonthYearFormat = SimpleDateFormat("d MMM, yyyy", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMM, yyyy", Locale.US)

    fun getDateMonthYearDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            dateMonthYearFormat.format(date)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun getRollingDaysRange(pastDays: Int = 365, futureDays: Int = 365): List<CalendarDayItem> {
        val list = ArrayList<CalendarDayItem>(pastDays + futureDays + 1)
        val cal = Calendar.getInstance(Locale.US)
        cal.add(Calendar.DAY_OF_YEAR, -pastDays)
        val todayIso = getTodayIso()
        val dayNameFormat = SimpleDateFormat("EEE", Locale.US)
        val monthShortFormat = SimpleDateFormat("MMM", Locale.US)

        for (i in 0..(pastDays + futureDays)) {
            val iso = isoFormat.format(cal.time)
            list.add(
                CalendarDayItem(
                    isoDate = iso,
                    dayName = dayNameFormat.format(cal.time),
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    isToday = (iso == todayIso),
                    isSunday = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY),
                    monthShort = monthShortFormat.format(cal.time),
                    monthYear = monthYearFormat.format(cal.time)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    fun isSunday(isoDate: String): Boolean {
        return try {
            val date = isoFormat.parse(isoDate) ?: return false
            val cal = Calendar.getInstance(Locale.US)
            cal.time = date
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (_: Exception) {
            false
        }
    }

    fun getTodayIso(): String {
        return isoFormat.format(Date())
    }

    fun getTodayFormatted(): String {
        return displayFormat.format(Date())
    }

    fun formatIsoToDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            displayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatIsoToShortDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            shortDisplayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun getPastDaysIso(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return isoFormat.format(calendar.time)
    }

    fun getFirstDayOfCurrentMonthIso(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return isoFormat.format(calendar.time)
    }

    fun getCurrentMonthShortName(): String {
        val format = SimpleDateFormat("MMM", Locale.US)
        return format.format(Date())
    }

    fun getMonthYearDisplay(isoDate: String): String {
        return try {
            val cal = Calendar.getInstance(Locale.US)
            val parsed = isoFormat.parse(isoDate)
            if (parsed != null) cal.time = parsed
            val monthYearFormat = SimpleDateFormat("MMM, yyyy", Locale.US)
            monthYearFormat.format(cal.time)
        } catch (_: Exception) {
            ""
        }
    }

    fun getYesterdayIso(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return isoFormat.format(calendar.time)
    }

    fun parseIsoToMillis(isoDate: String): Long? {
        return try {
            isoFormat.parse(isoDate)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun formatMillisToIso(millis: Long): String {
        return isoFormat.format(Date(millis))
    }

    data class MonthCalendarDay(
        val dayNumber: Int, // 1 to 31, or 0 for blank offset cell
        val isoDate: String, // "yyyy-MM-dd" or ""
        val isSunday: Boolean,
        val isToday: Boolean
    )

    fun getMonthCalendarDays(year: Int, month: Int): List<MonthCalendarDay> {
        val cal = Calendar.getInstance(Locale.US)
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday...
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayIso = getTodayIso()

        val list = mutableListOf<MonthCalendarDay>()
        // Blank cells before first day
        for (i in 1 until firstDayOfWeek) {
            list.add(MonthCalendarDay(dayNumber = 0, isoDate = "", isSunday = false, isToday = false))
        }

        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val iso = format.format(cal.time)
            val isSun = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            list.add(
                MonthCalendarDay(
                    dayNumber = day,
                    isoDate = iso,
                    isSunday = isSun,
                    isToday = (iso == todayIso)
                )
            )
        }
        return list
    }

    fun getWeekDaysAround(selectedIsoDate: String): List<CalendarDayItem> {
        val result = mutableListOf<CalendarDayItem>()
        val cal = Calendar.getInstance(Locale.US)
        try {
            val parsed = isoFormat.parse(selectedIsoDate)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {}

        // Find Sunday of this week
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        cal.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - 1))

        val dayNameFormat = SimpleDateFormat("EEE", Locale.US)
        val todayIso = getTodayIso()

        for (i in 0..6) {
            val iso = isoFormat.format(cal.time)
            val dayName = dayNameFormat.format(cal.time)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val isSun = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            result.add(
                CalendarDayItem(
                    isoDate = iso,
                    dayName = dayName,
                    dayOfMonth = dayOfMonth,
                    isToday = (iso == todayIso),
                    isSunday = isSun
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}
