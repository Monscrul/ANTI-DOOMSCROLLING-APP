package app.undertow.core.model

import app.undertow.core.common.DateKey

/**
 * A foreground stretch in one tracked app, already stitched and split so that it
 * belongs to exactly one local day.
 *
 * The constructor refuses degenerate input: a session always has positive duration.
 * Anomalies (`end <= start`, unclosed across a reboot, implausible length) are
 * rejected by the engine *before* a session is built, rather than represented here
 * and filtered later.
 */
data class ScrollSession(
    val id: String,
    val packageName: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val dateKey: DateKey,
    val zoneId: String,
    val source: ObservationSource,
    val confidence: Confidence,
    val unitCount: Int,
    val precision: Precision,
) {
    init {
        require(endUtcMillis > startUtcMillis) {
            "Session must have positive duration: start=$startUtcMillis end=$endUtcMillis"
        }
        require(unitCount >= 0) { "unitCount must not be negative: $unitCount" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(zoneId.isNotBlank()) { "zoneId must not be blank" }
    }

    val durationMillis: Long get() = endUtcMillis - startUtcMillis
}

/**
 * How much the engine trusts a session's boundaries.
 *
 * [LOW] is not a failure to hide: it means the event log had a gap (permission
 * revoked, device off, log rotated) and the UI must say so instead of smoothing it.
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}
