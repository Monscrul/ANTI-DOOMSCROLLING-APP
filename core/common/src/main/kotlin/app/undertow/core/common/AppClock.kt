package app.undertow.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single source of "now" in the app. Every day-boundary, freshness and
 * "today" calculation goes through this, so tests can control time instead of
 * sleeping, and so a device clock change is observable in one place.
 */
class AppClock(private val clock: Clock = Clock.systemDefaultZone()) {

    fun zone(): ZoneId = clock.zone

    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.now(clock)
}
