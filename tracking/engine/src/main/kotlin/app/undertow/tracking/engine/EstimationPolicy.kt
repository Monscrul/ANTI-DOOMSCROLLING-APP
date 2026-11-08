package app.undertow.tracking.engine

/** Converts measured app foreground time into a clearly labelled scroll estimate. */
data class EstimationPolicy(val defaultSecondsPerScroll: Int = 22) {
    init { require(defaultSecondsPerScroll > 0) }

    fun estimate(durationMillis: Long, secondsPerScroll: Int = defaultSecondsPerScroll): Int {
        require(secondsPerScroll > 0)
        return (durationMillis.coerceAtLeast(0) / (secondsPerScroll * 1000L))
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
