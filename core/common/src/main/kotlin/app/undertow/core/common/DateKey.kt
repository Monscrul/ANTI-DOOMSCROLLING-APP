package app.undertow.core.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * A local calendar day, stored as an ISO-8601 date (`2026-11-04`).
 *
 * Days are not 24 hours: a DST transition makes one 23 hours and another 25, and
 * some zones skip local midnight entirely. Every conversion here takes a [ZoneId]
 * so those days stay correct instead of being modelled as fixed slices of epoch time.
 *
 * Stored as a sortable string because it is range-queryable in SQLite and auditable
 * in the database after the fact.
 */
@JvmInline
value class DateKey(val isoDate: String) : Comparable<DateKey> {

    init {
        require(isWellFormed(isoDate)) { "Not an ISO-8601 local date: '$isoDate'" }
    }

    val localDate: LocalDate get() = LocalDate.parse(isoDate)

    fun plusDays(days: Int): DateKey = DateKey(localDate.plusDays(days.toLong()).toString())

    /** Calendar days from this day to [other]; negative when [other] is earlier. */
    fun daysUntil(other: DateKey): Long = ChronoUnit.DAYS.between(localDate, other.localDate)

    /**
     * First instant of this day in [zone]. When local midnight does not exist
     * (a zone that springs forward at 00:00) this is the first instant that does.
     */
    fun startInstant(zone: ZoneId): Instant = localDate.atStartOfDay(zone).toInstant()

    /** First instant of the following day, i.e. the exclusive end of this day. */
    fun endInstantExclusive(zone: ZoneId): Instant =
        localDate.plusDays(1).atStartOfDay(zone).toInstant()

    /** Real elapsed length of this day in [zone]: 23 h, 24 h or 25 h. */
    fun length(zone: ZoneId): Duration =
        Duration.between(startInstant(zone), endInstantExclusive(zone))

    /** True when [instant] falls inside this local day in [zone]. */
    fun contains(instant: Instant, zone: ZoneId): Boolean =
        !instant.isBefore(startInstant(zone)) && instant.isBefore(endInstantExclusive(zone))

    override fun compareTo(other: DateKey): Int = isoDate.compareTo(other.isoDate)

    override fun toString(): String = isoDate

    companion object {
        private fun isWellFormed(value: String): Boolean = try {
            LocalDate.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }

        fun of(date: LocalDate): DateKey = DateKey(date.toString())

        fun of(instant: Instant, zone: ZoneId): DateKey =
            DateKey(instant.atZone(zone).toLocalDate().toString())

        fun of(epochMillis: Long, zone: ZoneId): DateKey =
            of(Instant.ofEpochMilli(epochMillis), zone)
    }
}
