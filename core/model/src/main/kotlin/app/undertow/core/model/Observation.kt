package app.undertow.core.model

/**
 * A raw foreground/screen signal as reported by a provider.
 *
 * Providers emit observations and nothing more: they never decide what a "scroll" is
 * and never aggregate. Interpretation lives in `:tracking:engine`, so swapping a
 * provider cannot change counting semantics.
 */
data class Observation(
    val packageName: String,
    val type: ObservationType,
    val atUtcMillis: Long,
    val source: ObservationSource,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }

    /**
     * Mirrors the unique index on `usage_event` (package, type, timestamp, source).
     * Re-ingesting an overlap window therefore collapses to a no-op instead of
     * double-counting, and `RESUMED@t` does not collide with `PAUSED@t`.
     */
    val identity: Identity get() = Identity(packageName, type, atUtcMillis, source)

    data class Identity(
        val packageName: String,
        val type: ObservationType,
        val atUtcMillis: Long,
        val source: ObservationSource,
    )
}

enum class ObservationType {
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_SHUTDOWN,
    DEVICE_STARTUP,
    ;

    /** True for events that end a session regardless of the app's own activity events. */
    val closesSession: Boolean
        get() = this == ACTIVITY_PAUSED ||
            this == ACTIVITY_STOPPED ||
            this == SCREEN_NON_INTERACTIVE ||
            this == KEYGUARD_SHOWN ||
            this == DEVICE_SHUTDOWN
}

enum class ObservationSource {
    USAGE_STATS,
    ACCESSIBILITY,
    MANUAL,
}
