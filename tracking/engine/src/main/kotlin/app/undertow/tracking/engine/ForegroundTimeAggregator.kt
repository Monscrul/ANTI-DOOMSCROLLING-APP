package app.undertow.tracking.engine

enum class ForegroundKind { RESUMED, PAUSED, SCREEN_OFF }

data class ForegroundEvent(val packageName: String, val atMillis: Long, val kind: ForegroundKind)

/** Reconstructs completed foreground intervals and clips them to a calendar-day window. */
object ForegroundTimeAggregator {
    fun aggregate(events: List<ForegroundEvent>, packages: Set<String>, from: Long, to: Long): Map<String, Long> {
        require(to >= from)
        val active = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()
        events.sortedBy { it.atMillis }.forEach { event ->
            val at = event.atMillis.coerceIn(from, to)
            when (event.kind) {
                ForegroundKind.RESUMED -> if (event.packageName in packages) active.putIfAbsent(event.packageName, at)
                ForegroundKind.PAUSED -> {
                    val start = active.remove(event.packageName)
                    if (start != null) totals[event.packageName] = (totals[event.packageName] ?: 0) + (at - start).coerceAtLeast(0)
                }
                ForegroundKind.SCREEN_OFF -> {
                    active.forEach { (pkg, start) -> totals[pkg] = (totals[pkg] ?: 0) + (at - start).coerceAtLeast(0) }
                    active.clear()
                }
            }
        }
        return totals.filterValues { it > 0 }
    }
}
