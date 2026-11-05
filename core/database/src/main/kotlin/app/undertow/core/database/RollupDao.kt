package app.undertow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.undertow.core.common.DateKey

@Dao
interface RollupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummaries(summaries: List<DailySummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTotals(totals: List<DailyTotalEntity>)

    @Query("DELETE FROM daily_total WHERE dateKey = :dateKey")
    suspend fun deleteTotalsForDay(dateKey: DateKey)

    @Query("SELECT * FROM daily_summary WHERE dateKey = :dateKey")
    suspend fun summaryFor(dateKey: DateKey): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE dateKey >= :from AND dateKey <= :to ORDER BY dateKey ASC")
    suspend fun summariesBetween(from: DateKey, to: DateKey): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_total WHERE dateKey = :dateKey ORDER BY durationMillis DESC")
    suspend fun totalsForDay(dateKey: DateKey): List<DailyTotalEntity>

    @Query("SELECT * FROM daily_total WHERE dateKey >= :from AND dateKey <= :to")
    suspend fun totalsBetween(from: DateKey, to: DateKey): List<DailyTotalEntity>
}

@Dao
interface DiagnosticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: IngestRunEntity)

    @Query("SELECT * FROM ingest_run ORDER BY startedAtUtcMillis DESC LIMIT :limit")
    suspend fun recentRuns(limit: Int): List<IngestRunEntity>

    @Query("SELECT * FROM ingest_run WHERE id = :id")
    suspend fun run(id: String): IngestRunEntity?

    @Query("DELETE FROM ingest_run WHERE startedAtUtcMillis < :beforeUtcMillis")
    suspend fun deleteRunsBefore(beforeUtcMillis: Long): Int
}

@Dao
interface ManualEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ManualEntryEntity)

    @Query("SELECT * FROM manual_entry WHERE dateKey = :dateKey ORDER BY createdAtUtcMillis ASC")
    suspend fun entriesForDay(dateKey: DateKey): List<ManualEntryEntity>

    @Query("DELETE FROM manual_entry WHERE id = :id")
    suspend fun delete(id: String)
}
