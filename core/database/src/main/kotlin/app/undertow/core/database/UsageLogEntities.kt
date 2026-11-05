package app.undertow.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.undertow.core.common.DateKey
import app.undertow.core.model.Confidence
import app.undertow.core.model.ObservationSource
import app.undertow.core.model.ObservationType
import app.undertow.core.model.Precision

/**
 * The idempotent raw log.
 *
 * Observations are persisted *before* interpretation so that any later change to
 * stitching or estimation can be replayed over real history instead of invalidating it.
 * The unique index is what makes re-reading an overlap window a no-op.
 */
@Entity(
    tableName = "usage_event",
    indices = [
        Index(
            value = ["packageName", "type", "atUtcMillis", "source"],
            unique = true,
            name = "index_usage_event_identity",
        ),
        Index(value = ["atUtcMillis"], name = "index_usage_event_at"),
    ],
)
data class UsageEventEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val type: ObservationType,
    val atUtcMillis: Long,
    val source: ObservationSource,
    /** Which ingest run first saw it; useful when diagnosing a gap. */
    val ingestId: String,
)

/**
 * A derived foreground session. Fully recomputable from [UsageEventEntity], which is
 * why it is a cache of engine output rather than a source of truth.
 *
 * No sync columns: this table is device-local and rebuilt, never replicated.
 */
@Entity(
    tableName = "scroll_session",
    indices = [
        Index(
            value = ["packageName", "startUtcMillis", "source"],
            unique = true,
            name = "index_scroll_session_identity",
        ),
        Index(value = ["dateKey", "packageName"], name = "index_scroll_session_day"),
    ],
)
data class ScrollSessionEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val durationMillis: Long,
    val dateKey: DateKey,
    val zoneId: String,
    val source: ObservationSource,
    val confidence: Confidence,
    val unitCount: Int,
    val precision: Precision,
)
