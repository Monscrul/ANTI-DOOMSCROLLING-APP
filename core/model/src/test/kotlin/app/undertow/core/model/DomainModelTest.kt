package app.undertow.core.model

import app.undertow.core.common.DateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {

    private fun session(
        start: Long = 1_000L,
        end: Long = 61_000L,
        units: Int = 12,
        precision: Precision = Precision.ESTIMATED,
    ) = ScrollSession(
        id = "s-1",
        packageName = "com.example.feed",
        startUtcMillis = start,
        endUtcMillis = end,
        dateKey = DateKey("2026-11-04"),
        zoneId = "Europe/Berlin",
        source = ObservationSource.USAGE_STATS,
        confidence = Confidence.HIGH,
        unitCount = units,
        precision = precision,
    )

    @Test
    fun `session duration is derived from its bounds`() {
        assertEquals(60_000L, session().durationMillis)
    }

    @Test
    fun `an anomalous session cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) { session(start = 5_000L, end = 5_000L) }
        assertThrows(IllegalArgumentException::class.java) { session(start = 6_000L, end = 5_000L) }
        assertThrows(IllegalArgumentException::class.java) { session(units = -1) }
    }

    @Test
    fun `window is half-open and rejects empty ranges`() {
        val window = TimeWindow(startUtcMillis = 1_000L, endUtcMillis = 2_000L)

        assertTrue(1_000L in window)
        assertTrue(1_999L in window)
        assertFalse(2_000L in window)
        assertEquals(1_000L, window.durationMillis)

        assertThrows(IllegalArgumentException::class.java) { TimeWindow(1_000L, 1_000L) }
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(2_000L, 1_000L) }
    }

    @Test
    fun `expanding a window widens both ends for the ingest overlap`() {
        val expanded = TimeWindow(10_000L, 20_000L).expandedBy(120_000L)

        assertEquals(TimeWindow(-110_000L, 140_000L), expanded)
        assertThrows(IllegalArgumentException::class.java) {
            TimeWindow(10_000L, 20_000L).expandedBy(-1L)
        }
    }

    @Test
    fun `windows overlap only when they share an instant`() {
        val a = TimeWindow(0L, 100L)
        val b = TimeWindow(100L, 200L)
        val c = TimeWindow(99L, 200L)

        assertFalse(a.overlaps(b))
        assertTrue(a.overlaps(c))
        assertFalse(a.contains(c))
        assertTrue(a.contains(TimeWindow(10L, 90L)))
    }

    @Test
    fun `observation identity dedupes re-ingested events but keeps distinct types`() {
        val resumed = Observation("com.example.feed", ObservationType.ACTIVITY_RESUMED, 500L, ObservationSource.USAGE_STATS)
        val sameResumed = Observation("com.example.feed", ObservationType.ACTIVITY_RESUMED, 500L, ObservationSource.USAGE_STATS)
        val paused = Observation("com.example.feed", ObservationType.ACTIVITY_PAUSED, 500L, ObservationSource.USAGE_STATS)

        assertEquals(resumed.identity, sameResumed.identity)
        assertNotEquals(resumed.identity, paused.identity)
    }

    @Test
    fun `only session-closing event types close a session`() {
        assertTrue(ObservationType.ACTIVITY_PAUSED.closesSession)
        assertTrue(ObservationType.SCREEN_NON_INTERACTIVE.closesSession)
        assertTrue(ObservationType.KEYGUARD_SHOWN.closesSession)
        assertFalse(ObservationType.ACTIVITY_RESUMED.closesSession)
        assertFalse(ObservationType.SCREEN_INTERACTIVE.closesSession)
    }

    @Test
    fun `weakest precision wins when aggregating`() {
        assertEquals(Precision.COUNTED, Precision.weakest(Precision.COUNTED, Precision.COUNTED))
        assertEquals(Precision.ESTIMATED, Precision.weakest(Precision.COUNTED, Precision.ESTIMATED))
        assertEquals(Precision.MANUAL, Precision.weakest(Precision.ESTIMATED, Precision.MANUAL))
    }

    @Test
    fun `a day can report that it mixes measured and derived units`() {
        fun summary(counted: Int, estimated: Int) = DailySummary(
            dateKey = DateKey("2026-11-04"),
            countedUnits = counted,
            estimatedUnits = estimated,
            durationMillis = 60_000L,
            dominantPrecision = if (estimated > 0) Precision.ESTIMATED else Precision.COUNTED,
            limitUnitsAtTime = null,
            computedAtUtcMillis = 0L,
            revision = 1,
        )

        assertTrue(summary(3, 4).hasMixedPrecision)
        assertFalse(summary(3, 0).hasMixedPrecision)
        assertFalse(summary(0, 0).hasAnyUnits)
        assertThrows(IllegalArgumentException::class.java) { summary(-1, 0) }
    }
}
