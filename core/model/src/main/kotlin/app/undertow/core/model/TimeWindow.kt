package app.undertow.core.model

/**
 * A half-open window `[startUtcMillis, endUtcMillis)` in epoch millis UTC.
 *
 * Half-open by convention because adjacent windows must not both contain the same
 * instant: ingest windows overlap deliberately, and an inclusive end would
 * double-count the boundary event.
 */
data class TimeWindow(
    val startUtcMillis: Long,
    val endUtcMillis: Long,
) {
    init {
        require(endUtcMillis > startUtcMillis) {
            "Window must have positive length: start=$startUtcMillis end=$endUtcMillis"
        }
    }

    val durationMillis: Long get() = endUtcMillis - startUtcMillis

    operator fun contains(instantMillis: Long): Boolean =
        instantMillis >= startUtcMillis && instantMillis < endUtcMillis

    /** Extend both ends — used for the ingest overlap that catches late-arriving events. */
    fun expandedBy(millis: Long): TimeWindow {
        require(millis >= 0) { "Overlap must not be negative: $millis" }
        return TimeWindow(startUtcMillis - millis, endUtcMillis + millis)
    }

    fun overlaps(other: TimeWindow): Boolean =
        startUtcMillis < other.endUtcMillis && other.startUtcMillis < endUtcMillis

    fun contains(other: TimeWindow): Boolean =
        other.startUtcMillis >= startUtcMillis && other.endUtcMillis <= endUtcMillis
}
