package app.undertow.core.model

/**
 * How a number was obtained.
 *
 * This travels with every count, all the way to the UI, so that a screen can never
 * present an estimate as a measurement by accident: a single `Int` with no precision
 * is exactly the bug this type exists to prevent.
 */
enum class Precision {
    /** Individually observed units — only possible with a scroll-capable provider. */
    COUNTED,

    /** Derived from time-in-feed by an estimation policy. Always rendered with `≈`. */
    ESTIMATED,

    /** Entered by the user. Additive to the measured series, never a replacement for it. */
    MANUAL;

    companion object {
        /** The least trustworthy of two precisions, used when aggregating mixed rows. */
        fun weakest(a: Precision, b: Precision): Precision = if (a.ordinal >= b.ordinal) a else b
    }
}
