package app.undertow.core.model

/**
 * Where the user is in the day, from the mascot's point of view.
 *
 * The band is derived, never stored as a UI flag, and it is conveyed by shape *and*
 * label so it never depends on colour alone. The metaphor is a water line: the
 * mascot is the Drifter, and it sinks as the feed wins.
 */
enum class ActivityBand {
    /** Well below the limit (or a light day). */
    CLEAR,

    /** Rising: the day is building toward the limit. */
    SWELL,

    /** Past the alert threshold; the current is pulling. */
    UNDERTOW,

    /** Limit reached or exceeded. */
    SUBMERGED,

    /** No limit set — the band cannot express progress, and the UI must not imply one. */
    NO_LIMIT,
}
