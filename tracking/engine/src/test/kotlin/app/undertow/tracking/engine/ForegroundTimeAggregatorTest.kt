package app.undertow.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTimeAggregatorTest {
    @Test fun clipsSessionCrossingMidnight() {
        val events = listOf(ForegroundEvent("a", 90, ForegroundKind.RESUMED), ForegroundEvent("a", 130, ForegroundKind.PAUSED))
        assertEquals(mapOf("a" to 30L), ForegroundTimeAggregator.aggregate(events, setOf("a"), 100, 200))
    }

    @Test fun screenOffClosesSession() {
        val events = listOf(ForegroundEvent("a", 10, ForegroundKind.RESUMED), ForegroundEvent("", 25, ForegroundKind.SCREEN_OFF))
        assertEquals(mapOf("a" to 15L), ForegroundTimeAggregator.aggregate(events, setOf("a"), 0, 100))
    }

    @Test fun unfinishedSessionIsNotProjectedToNow() {
        assertEquals(emptyMap<String, Long>(), ForegroundTimeAggregator.aggregate(
            listOf(ForegroundEvent("a", 10, ForegroundKind.RESUMED)), setOf("a"), 0, 100,
        ))
    }
}
