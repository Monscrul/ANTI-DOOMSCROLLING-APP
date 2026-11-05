package app.undertow.core.model

import app.undertow.core.common.DateKey

/**
 * One day's totals.
 *
 * Counted and estimated units are kept in separate columns on purpose. A single
 * mixed `total` would make `42` indistinguishable from `≈42`, and every honesty
 * rule in the UI depends on that distinction surviving aggregation.
 */
data class DailySummary(
    val dateKey: DateKey,
    val countedUnits: Int,
    val estimatedUnits: Int,
    val durationMillis: Long,
    val dominantPrecision: Precision,
    /** The limit as it was *on that day*, so history stays truthful after edits. */
    val limitUnitsAtTime: Int?,
    val computedAtUtcMillis: Long,
    val revision: Int,
) {
    init {
        require(countedUnits >= 0) { "countedUnits must not be negative: $countedUnits" }
        require(estimatedUnits >= 0) { "estimatedUnits must not be negative: $estimatedUnits" }
        require(durationMillis >= 0) { "durationMillis must not be negative: $durationMillis" }
        require(limitUnitsAtTime == null || limitUnitsAtTime >= 0) {
            "limitUnitsAtTime must be null or non-negative: $limitUnitsAtTime"
        }
    }

    /** True when the day mixes measured and derived units, so the UI must show both. */
    val hasMixedPrecision: Boolean get() = countedUnits > 0 && estimatedUnits > 0

    val hasAnyUnits: Boolean get() = countedUnits > 0 || estimatedUnits > 0
}

/** One app's contribution to a day; the per-app breakdown without a JSON blob. */
data class DailyAppTotal(
    val dateKey: DateKey,
    val packageName: String,
    val countedUnits: Int,
    val estimatedUnits: Int,
    val durationMillis: Long,
    val sessionCount: Int,
    val precision: Precision,
) {
    init {
        require(countedUnits >= 0) { "countedUnits must not be negative: $countedUnits" }
        require(estimatedUnits >= 0) { "estimatedUnits must not be negative: $estimatedUnits" }
        require(durationMillis >= 0) { "durationMillis must not be negative: $durationMillis" }
        require(sessionCount >= 0) { "sessionCount must not be negative: $sessionCount" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }
}
