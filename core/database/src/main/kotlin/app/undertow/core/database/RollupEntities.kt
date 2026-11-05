package app.undertow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.undertow.core.common.DateKey
import app.undertow.core.model.Precision

/** Per-app contribution to a day. Queryable breakdown without JSON blobs. */
@Entity(tableName = "daily_total", primaryKeys = ["dateKey", "packageName"])
data class DailyTotalEntity(
    val dateKey: DateKey,
    val packageName: String,
    val countedUnits: Int,
    val estimatedUnits: Int,
    val durationMillis: Long,
    val sessionCount: Int,
    val precision: Precision,
)

/** Per-day totals: the fast read behind Home and Stats. */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val dateKey: DateKey,
    val countedUnits: Int,
    val estimatedUnits: Int,
    val durationMillis: Long,
    val dominantPrecision: Precision,
    /** Snapshot of the limit on that day, so history stays truthful after edits. */
    val limitUnitsAtTime: Int?,
    val computedAtUtcMillis: Long,
    val revision: Int,
    // Reserved for the sync phase; unused (and null) until then.
    val remoteId: String? = null,
    val updatedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
    val syncState: String? = null,
)

/** One ingest attempt: the diagnostics screen's evidence, and support's evidence too. */
@Entity(tableName = "ingest_run")
data class IngestRunEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val startedAtUtcMillis: Long,
    val finishedAtUtcMillis: Long?,
    val eventsRead: Int,
    val sessionsWritten: Int,
    val outcome: IngestOutcome,
    val errorCode: String?,
)

enum class IngestOutcome {
    SUCCESS,
    PARTIAL,
    PERMISSION_DENIED,
    GAP_DETECTED,
    ERROR,
}

/**
 * A user correction. Always additive to the measured series — never a replacement,
 * so the real data stays intact and the correction stays visible as such.
 */
@Entity(tableName = "manual_entry")
data class ManualEntryEntity(
    @PrimaryKey val id: String,
    val dateKey: DateKey,
    val deltaUnits: Int,
    val note: String?,
    val createdAtUtcMillis: Long,
    val remoteId: String? = null,
    val updatedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
    val syncState: String? = null,
)
