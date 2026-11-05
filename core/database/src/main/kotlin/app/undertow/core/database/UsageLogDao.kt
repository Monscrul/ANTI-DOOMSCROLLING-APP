package app.undertow.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.undertow.core.common.DateKey

@Dao
interface UsageLogDao {

    /**
     * Returns one row id per event, `-1` for events the unique index already held.
     * Callers use that to report how many events were genuinely new.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<UsageEventEntity>): List<Long>

    @Query(
        """
        SELECT * FROM usage_event
        WHERE packageName IN (:packages)
          AND atUtcMillis >= :fromUtcMillis
          AND atUtcMillis < :toUtcMillis
        ORDER BY atUtcMillis ASC, type ASC
        """,
    )
    suspend fun eventsIn(
        packages: Set<String>,
        fromUtcMillis: Long,
        toUtcMillis: Long,
    ): List<UsageEventEntity>

    @Query("SELECT MIN(atUtcMillis) FROM usage_event")
    suspend fun earliestEventAtUtcMillis(): Long?

    @Query("SELECT MAX(atUtcMillis) FROM usage_event")
    suspend fun latestEventAtUtcMillis(): Long?

    @Query("SELECT COUNT(*) FROM usage_event")
    suspend fun eventCount(): Int

    @Query("DELETE FROM usage_event WHERE atUtcMillis < :beforeUtcMillis")
    suspend fun deleteEventsBefore(beforeUtcMillis: Long): Int
}

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<ScrollSessionEntity>)

    @Query("DELETE FROM scroll_session WHERE dateKey = :dateKey")
    suspend fun deleteSessionsForDay(dateKey: DateKey)

    @Query("SELECT * FROM scroll_session WHERE dateKey = :dateKey ORDER BY startUtcMillis ASC")
    suspend fun sessionsForDay(dateKey: DateKey): List<ScrollSessionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM scroll_session
        WHERE startUtcMillis >= :fromUtcMillis AND startUtcMillis < :toUtcMillis
        """,
    )
    suspend fun sessionCountIn(fromUtcMillis: Long, toUtcMillis: Long): Int
}
