package app.undertow.core.model

import java.time.DayOfWeek

/**
 * Everything the user can configure, as one immutable value.
 *
 * Persistence lives in `:core:datastore`; this type has no Android dependency so that
 * features and tests can use it without knowing how it is stored.
 */
data class UserPreferences(
    /** `null` means "no limit set", which is different from a limit of 0. */
    val dailyLimitUnits: Int? = null,
    val limitMode: LimitMode = LimitMode.UNITS,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val trackedPackages: Set<String> = DEFAULT_TRACKED_PACKAGES,
    val precisionPreference: PrecisionPreference = PrecisionPreference.AUTO,
    /** Per-package override of the estimation rate, in seconds per scroll. */
    val estimationSecondsPerScroll: Map<String, Int> = emptyMap(),
    val mascotEnabled: Boolean = true,
    /** Percentages of the daily limit at which to alert. */
    val alertThresholds: Set<Int> = DEFAULT_ALERT_THRESHOLDS,
    val notificationsEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Ingest watermark: events before this instant have already been read. */
    val lastIngestCursorUtcMillis: Long? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(dailyLimitUnits == null || dailyLimitUnits >= 0) {
            "dailyLimitUnits must be null or non-negative: $dailyLimitUnits"
        }
        require(alertThresholds.all { it in 1..100 }) {
            "Alert thresholds are percentages in 1..100: $alertThresholds"
        }
        require(estimationSecondsPerScroll.values.all { it > 0 }) {
            "Estimation rates must be positive: $estimationSecondsPerScroll"
        }
    }

    val hasLimit: Boolean get() = dailyLimitUnits != null

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * The apps the product is about. Real package identifiers, not sample data:
         * these are the editable defaults, and regional or `Lite` variants must be
         * added by the user rather than guessed here.
         */
        val DEFAULT_TRACKED_PACKAGES: Set<String> = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android",
        )

        val DEFAULT_ALERT_THRESHOLDS: Set<Int> = setOf(50, 80, 100)
    }
}

enum class LimitMode {
    /** Limit expressed in scroll units. */
    UNITS,

    /** Limit expressed in feed time. */
    DURATION,
}

enum class PrecisionPreference {
    /** Use whatever the best available provider can give. */
    AUTO,

    /** Never show an estimate; degraded but literal. */
    COUNTED_ONLY,

    /** Accept estimates when nothing can count. */
    ESTIMATED_OK,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
