package dev.tomng.alarme.core.model.alarm

import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaysOfWeekTest {

    @Test
    fun `empty mask is not repeating`() {
        assertFalse(DaysOfWeek.NONE.isRepeating)
    }

    @Test
    fun `any bit set is repeating`() {
        assertTrue(DaysOfWeek.of(DayOfWeek.MONDAY).isRepeating)
        assertTrue(DaysOfWeek.EVERY_DAY.isRepeating)
    }

    @Test
    fun `of builds mask per day`() {
        val mask = DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertTrue(mask.contains(DayOfWeek.MONDAY))
        assertTrue(mask.contains(DayOfWeek.FRIDAY))
        assertFalse(mask.contains(DayOfWeek.SATURDAY))
    }

    @Test
    fun `every day contains all seven days`() {
        DayOfWeek.entries.forEach { day ->
            assertTrue("$day should be contained", DaysOfWeek.EVERY_DAY.contains(day))
        }
    }

    @Test
    fun `round trip from set to mask`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
        val mask = DaysOfWeek.ofSet(days)
        assertTrue(days == mask.toSet())
    }

    @Test
    fun `monday is bit 1 and sunday is bit 64`() {
        org.junit.Assert.assertEquals(1, DaysOfWeek.of(DayOfWeek.MONDAY).bits)
        org.junit.Assert.assertEquals(64, DaysOfWeek.of(DayOfWeek.SUNDAY).bits)
        org.junit.Assert.assertEquals(127, DaysOfWeek.EVERY_DAY.bits)
    }
}
