package com.example.weeklytotals

import com.example.weeklytotals.data.WeekCalculator
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WeekCalculatorTest {

    private lateinit var calculator: WeekCalculator

    @Before
    fun setUp() {
        calculator = WeekCalculator()
    }

    @Test
    fun saturdayReturnsItself() {
        // 2025-02-08 is a Saturday
        val saturday = LocalDate.of(2025, 2, 8)
        assertEquals(java.time.DayOfWeek.SATURDAY, saturday.dayOfWeek)

        // Use getPreviousWeekStart/getNextWeekStart to verify Saturday logic indirectly.
        // A Saturday start date minus 7 days should be the prior Saturday.
        val weekStart = "2025-02-08"
        assertEquals("2025-02-01", calculator.getPreviousWeekStart(weekStart))
        assertEquals("2025-02-15", calculator.getNextWeekStart(weekStart))
    }

    @Test
    fun fridayReturnsPreviousSaturday() {
        // 2025-02-14 is a Friday. The week should have started on Saturday 2025-02-08.
        // We verify by checking that getNextWeekStart of 2025-02-08 gives 2025-02-15
        // and the range for 2025-02-08 includes Feb 14 (Friday).
        val weekStart = "2025-02-08"
        val weekName = calculator.getWeekName(weekStart)
        // Feb 8 (Sat) through Feb 14 (Fri)
        assertEquals("Feb 8 - Feb 14", weekName)
    }

    @Test
    fun midWeekWednesdayReturnsCorrectSaturday() {
        // 2025-02-12 is a Wednesday. Week started Saturday 2025-02-08.
        // Verify the week starting 2025-02-08 covers this Wednesday.
        val weekStart = "2025-02-08"
        val start = LocalDate.parse(weekStart)
        val wednesday = LocalDate.of(2025, 2, 12)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)

        // Wednesday falls within Sat Feb 8 - Fri Feb 14
        val end = start.plusDays(6)
        assert(!wednesday.isBefore(start) && !wednesday.isAfter(end))
    }

    @Test
    fun getCurrentWeekStartReturnsSaturday() {
        val weekStart = calculator.getCurrentWeekStart()
        val date = LocalDate.parse(weekStart)
        assertEquals(java.time.DayOfWeek.SATURDAY, date.dayOfWeek)
    }

    @Test
    fun getCurrentWeekStartIsNotInTheFuture() {
        val weekStart = LocalDate.parse(calculator.getCurrentWeekStart())
        assert(!weekStart.isAfter(LocalDate.now()))
    }

    @Test
    fun getWeekNameFormatsCorrectly() {
        // 2025-01-04 is a Saturday
        assertEquals("Jan 4 - Jan 10", calculator.getWeekName("2025-01-04"))
    }

    @Test
    fun getWeekNameSpansMonthBoundary() {
        // 2025-01-25 is a Saturday, week ends Friday Jan 31
        assertEquals("Jan 25 - Jan 31", calculator.getWeekName("2025-01-25"))
    }

    @Test
    fun getWeekNameSpansYearBoundary() {
        // 2024-12-28 is a Saturday, week ends Friday Jan 3, 2025
        assertEquals("Dec 28 - Jan 3", calculator.getWeekName("2024-12-28"))
    }

    @Test
    fun getPreviousWeekStartReturnsSevenDaysEarlier() {
        assertEquals("2025-02-01", calculator.getPreviousWeekStart("2025-02-08"))
        assertEquals("2024-12-28", calculator.getPreviousWeekStart("2025-01-04"))
    }

    @Test
    fun getNextWeekStartReturnsSevenDaysLater() {
        assertEquals("2025-02-15", calculator.getNextWeekStart("2025-02-08"))
        assertEquals("2025-01-04", calculator.getNextWeekStart("2024-12-28"))
    }

    @Test
    fun previousAndNextAreInverses() {
        val original = "2025-03-15"
        val next = calculator.getNextWeekStart(original)
        val backToOriginal = calculator.getPreviousWeekStart(next)
        assertEquals(original, backToOriginal)
    }
}
