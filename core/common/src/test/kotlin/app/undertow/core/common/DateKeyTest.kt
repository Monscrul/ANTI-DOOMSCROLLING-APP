package app.undertow.core.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DateKeyTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    @Test
    fun `maps an instant to the local day in that zone`() {
        val instant = Instant.parse("2026-01-01T00:30:00Z")

        assertEquals("2026-01-01", DateKey.of(instant, berlin).isoDate)
        assertEquals("2025-12-31", DateKey.of(instant, losAngeles).isoDate)
    }

    @Test
    fun `accepts a LocalDate and round-trips it`() {
        val date = LocalDate.of(2026, 11, 4)

        assertEquals("2026-11-04", DateKey.of(date).isoDate)
        assertEquals(date, DateKey.of(date).localDate)
    }

    @Test
    fun `orders chronologically as a sortable string`() {
        val shuffled = listOf("2026-03-01", "2025-12-31", "2026-01-02", "2026-02-28")
            .map(::DateKey)
            .sorted()

        assertEquals(
            listOf("2025-12-31", "2026-01-02", "2026-02-28", "2026-03-01"),
            shuffled.map { it.isoDate },
        )
    }

    @Test
    fun `plusDays crosses month and year boundaries`() {
        assertEquals("2026-03-01", DateKey("2026-02-28").plusDays(1).isoDate)
        assertEquals("2027-01-01", DateKey("2026-12-31").plusDays(1).isoDate)
        assertEquals("2025-12-31", DateKey("2026-01-01").plusDays(-1).isoDate)
    }

    @Test
    fun `daysUntil counts calendar days in both directions`() {
        assertEquals(30L, DateKey("2026-03-01").daysUntil(DateKey("2026-03-31")))
        assertEquals(-30L, DateKey("2026-03-31").daysUntil(DateKey("2026-03-01")))
        assertEquals(0L, DateKey("2026-03-01").daysUntil(DateKey("2026-03-01")))
    }

    @Test
    fun `a spring-forward day is 23 hours long`() {
        // Europe/Berlin loses 02:00-03:00 on the last Sunday of March.
        val day = DateKey("2026-03-29")

        assertEquals(Duration.ofHours(23), day.length(berlin))
    }

    @Test
    fun `an autumn fall-back day is 25 hours long`() {
        // Europe/Berlin repeats 02:00-03:00 on the last Sunday of October.
        val day = DateKey("2026-10-25")

        assertEquals(Duration.ofHours(25), day.length(berlin))
    }

    @Test
    fun `an ordinary day is 24 hours long`() {
        assertEquals(Duration.ofHours(24), DateKey("2026-11-04").length(berlin))
    }

    @Test
    fun `consecutive days are contiguous across a DST transition`() {
        val before = DateKey("2026-03-29")
        val after = before.plusDays(1)

        // No instant is lost or double-counted at the seam: the end of one day is the start of the next.
        assertEquals(before.endInstantExclusive(berlin), after.startInstant(berlin))
    }

    @Test
    fun `contains is half-open at the day boundaries`() {
        val day = DateKey("2026-11-04")
        val start = day.startInstant(berlin)
        val end = day.endInstantExclusive(berlin)

        assertTrue(day.contains(start, berlin))
        assertTrue(day.contains(start.plusSeconds(1), berlin))
        assertFalse(day.contains(start.minusMillis(1), berlin))
        assertFalse(day.contains(end, berlin))
        assertTrue(day.contains(end.minusMillis(1), berlin))
    }

    @Test
    fun `rejects input that is not an ISO local date`() {
        listOf("2026-02-30", "not-a-date", "2026-1-1", "", "2026-11-04T10:00:00Z").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { DateKey(bad) }
        }
    }
}
