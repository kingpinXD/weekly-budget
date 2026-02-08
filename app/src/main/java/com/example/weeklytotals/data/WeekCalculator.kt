package com.example.weeklytotals.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class WeekCalculator {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayMonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    fun getCurrentWeekStart(): String {
        return getWeekStart(LocalDate.now())
    }

    fun getWeekName(weekStartDate: String): String {
        val start = LocalDate.parse(weekStartDate, dateFormat)
        val end = start.plusDays(6)
        return "${start.format(displayMonthDay)} - ${end.format(displayMonthDay)}"
    }

    fun getPreviousWeekStart(weekStartDate: String): String {
        val date = LocalDate.parse(weekStartDate, dateFormat)
        return date.minusWeeks(1).format(dateFormat)
    }

    fun getNextWeekStart(weekStartDate: String): String {
        val date = LocalDate.parse(weekStartDate, dateFormat)
        return date.plusWeeks(1).format(dateFormat)
    }

    private fun getWeekStart(date: LocalDate): String {
        val start = if (date.dayOfWeek == DayOfWeek.SATURDAY) {
            date
        } else {
            date.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))
        }
        return start.format(dateFormat)
    }
}
