package app.undertow.tracking

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.undertow.core.common.DateKey
import app.undertow.core.database.DailySummaryEntity
import app.undertow.core.database.DailyTotalEntity
import app.undertow.core.database.IngestRunEntity
import app.undertow.core.database.IngestOutcome
import app.undertow.core.database.UsageEventEntity
import app.undertow.core.database.ManualEntryEntity
import app.undertow.core.database.UndertowDatabase
import app.undertow.core.datastore.createUserPreferencesRepository
import app.undertow.core.model.Precision
import app.undertow.core.model.ObservationType
import app.undertow.core.model.ObservationSource
import app.undertow.tracking.engine.EstimationPolicy
import app.undertow.tracking.engine.ForegroundEvent
import app.undertow.tracking.engine.ForegroundKind
import app.undertow.tracking.engine.ForegroundTimeAggregator
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.UUID

data class DayUsage(
    val day: LocalDate,
    val byPackage: Map<String, Long>,
    val updatedAt: Long,
    val events: List<ForegroundEvent> = emptyList(),
) {
    val totalMillis: Long get() = byPackage.values.sum()
}

data class ScrollCounts(val counted: Int, val byPackage: Map<String, Int>)

class UsageTracker(private val context: Context) {
    private val trackingState = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(UsageStatsManager::class.java)
    private val operations = context.getSystemService(AppOpsManager::class.java)
    private val database by lazy {
        Room.databaseBuilder(context, UndertowDatabase::class.java, UndertowDatabase.NAME).build()
    }

    fun hasAccess(): Boolean = operations.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
    ) == AppOpsManager.MODE_ALLOWED

    fun isPaused(): Boolean = trackingState.getBoolean("paused", false)

    fun resume() {
        trackingState.edit().putBoolean("paused", false)
            .putLong("first_access_at", System.currentTimeMillis()).apply()
        schedule(context)
    }

    /** Reads activity boundaries and clips each interval to the selected local day. */
    fun measure(day: LocalDate, packages: Set<String>, earliestMillis: Long = Long.MIN_VALUE): DayUsage {
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val stop = minOf(end, now)
        if (!hasAccess() || stop <= start) return DayUsage(day, emptyMap(), now)
        val effectiveStart = maxOf(start, earliestMillis)
        if (effectiveStart >= stop) return DayUsage(day, emptyMap(), now)
        // Include the preceding day to detect an activity already open at midnight.
        val events = manager.queryEvents(
            start - TimeUnit.DAYS.toMillis(1),
            minOf(end + TimeUnit.DAYS.toMillis(1), now),
        )
        val event = UsageEvents.Event()
        val observed = mutableListOf<ForegroundEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName.orEmpty()
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundKind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundKind.SCREEN_OFF
                else -> null
            }
            if (kind != null) observed += ForegroundEvent(pkg, event.timeStamp, kind)
        }
        // Recent events can be delayed by Android. Do not count an unclosed activity
        // through the present; the next reconciliation will close it when the event arrives.
        return DayUsage(day, ForegroundTimeAggregator.aggregate(observed, packages, effectiveStart, stop), now,
            observed.filter { it.atMillis in effectiveStart until stop })
    }

    suspend fun collect(): DayUsage {
        val prefs = createUserPreferencesRepository(context).current()
        val today = LocalDate.now()
        if (isPaused()) return DayUsage(today, emptyMap(), System.currentTimeMillis())
        val firstAccessAt = trackingState.getLong("first_access_at", 0L).takeIf { it > 0 }
            ?: System.currentTimeMillis().also { trackingState.edit().putLong("first_access_at", it).apply() }
        val result = measure(today, prefs.trackedPackages, firstAccessAt)
        if (!hasAccess()) return result
        val yesterday = measure(today.minusDays(1), prefs.trackedPackages, firstAccessAt)
        val policy = EstimationPolicy()
        val previousCursor = prefs.lastIngestCursorUtcMillis
        val runId = UUID.randomUUID().toString()
        database.withTransaction {
            listOfNotNull(yesterday.takeIf { it.events.isNotEmpty() || it.byPackage.isNotEmpty() }, result).forEach { usage ->
                val key = DateKey.of(usage.day)
                database.usageLogDao().insertEvents(usage.events.map { event ->
                    UsageEventEntity(
                        UUID.randomUUID().toString(), event.packageName,
                        when (event.kind) {
                            ForegroundKind.RESUMED -> ObservationType.ACTIVITY_RESUMED
                            ForegroundKind.PAUSED -> ObservationType.ACTIVITY_PAUSED
                            ForegroundKind.SCREEN_OFF -> ObservationType.SCREEN_NON_INTERACTIVE
                        },
                        event.atMillis, ObservationSource.USAGE_STATS, runId,
                    )
                })
                val estimates = usage.byPackage.mapValues { (pkg, millis) ->
                    policy.estimate(millis, prefs.estimationSecondsPerScroll[pkg] ?: policy.defaultSecondsPerScroll)
                }
                database.rollupDao().deleteTotalsForDay(key)
                database.rollupDao().upsertTotals(usage.byPackage.map { (pkg, millis) ->
                    DailyTotalEntity(key, pkg, 0, estimates[pkg] ?: 0, millis, 0, Precision.ESTIMATED)
                })
                database.rollupDao().upsertSummaries(listOf(DailySummaryEntity(
                    key, 0, estimates.values.sum(), usage.totalMillis, Precision.ESTIMATED,
                    prefs.dailyLimitUnits, usage.updatedAt, 1,
                )))
            }
            database.diagnosticsDao().insertRun(IngestRunEntity(
                id = runId, providerId = "usage_stats", startedAtUtcMillis = result.updatedAt,
                finishedAtUtcMillis = System.currentTimeMillis(),
                eventsRead = result.events.size + yesterday.events.size,
                sessionsWritten = result.byPackage.size + yesterday.byPackage.size,
                outcome = if (previousCursor != null &&
                    result.updatedAt - previousCursor > TimeUnit.DAYS.toMillis(2))
                    IngestOutcome.GAP_DETECTED else IngestOutcome.SUCCESS,
                errorCode = null,
            ))
        }
        createUserPreferencesRepository(context).setLastIngestCursorUtcMillis(result.updatedAt)
        maybeNotify(result, prefs)
        return result
    }

    suspend fun recentRuns(): List<IngestRunEntity> = database.diagnosticsDao().recentRuns(5)

    private fun maybeNotify(usage: DayUsage, prefs: app.undertow.core.model.UserPreferences) {
        val limit = prefs.dailyLimitUnits?.takeIf { it > 0 } ?: return
        if (!prefs.notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val reached = prefs.alertThresholds.filter { usage.totalMillis * 100 >= limit.toLong() * 60_000 * it }.maxOrNull() ?: return
        val store = context.getSharedPreferences("goal_alerts", Context.MODE_PRIVATE)
        val key = "${usage.day}:$reached"
        if (store.getBoolean(key, false)) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("goals", "Daily goals", NotificationManager.IMPORTANCE_DEFAULT))
        notifications.notify(reached, Notification.Builder(context, "goals")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$reached% of your daily goal")
            .setContentText("You’ve used ${usage.totalMillis / 60_000} minutes in selected apps today.")
            .setAutoCancel(true)
            .build())
        store.edit().putBoolean(key, true).apply()
    }

    suspend fun history(days: Int = 7): List<DailySummaryEntity> = database.rollupDao().summariesBetween(
        DateKey.of(LocalDate.now().minusDays((days - 1).toLong())), DateKey.of(LocalDate.now()),
    )

    suspend fun counts(day: LocalDate = LocalDate.now()): ScrollCounts {
        val zone = ZoneId.systemDefault()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return ScrollCounts(
            database.usageLogDao().scrollCountIn(from, to),
            database.usageLogDao().scrollCountsByPackage(from, to).associate { it.packageName to it.count },
        )
    }

    suspend fun manualCount(day: LocalDate = LocalDate.now()): Int =
        database.manualEntryDao().entriesForDay(DateKey.of(day)).sumOf { it.deltaUnits }

    suspend fun addManual(count: Int) {
        require(count > 0)
        val now = System.currentTimeMillis()
        val key = DateKey.of(LocalDate.now())
        database.withTransaction {
            database.manualEntryDao().insert(ManualEntryEntity(UUID.randomUUID().toString(), key, count, null, now))
            if (database.rollupDao().summaryFor(key) == null) {
                database.rollupDao().upsertSummaries(listOf(DailySummaryEntity(
                    key, 0, 0, 0, Precision.MANUAL, null, now, 1,
                )))
            }
        }
    }

    suspend fun estimate(usage: DayUsage): Int {
        val overrides = createUserPreferencesRepository(context).current().estimationSecondsPerScroll
        val policy = EstimationPolicy()
        return usage.byPackage.entries.sumOf { (pkg, millis) ->
            policy.estimate(millis, overrides[pkg] ?: policy.defaultSecondsPerScroll)
        }
    }

    suspend fun exportCsv(): String {
        val history = database.rollupDao().summariesBetween(
            DateKey.of(LocalDate.of(1970, 1, 1)), DateKey.of(LocalDate.now()),
        )
        return buildString {
            appendLine("date,app_foreground_minutes,detected_scrolls,manual_scrolls")
            history.forEach { row ->
                appendLine("${row.dateKey},${row.durationMillis / 60_000},${counts(row.dateKey.localDate).counted},${manualCount(row.dateKey.localDate)}")
            }
        }
    }

    suspend fun deleteAll() {
        trackingState.edit().putBoolean("paused", true).apply()
        trackingState.edit().remove("first_access_at").apply()
        WorkManager.getInstance(context).cancelUniqueWork("usage_reconciliation")
        database.clearAllTables()
        createUserPreferencesRepository(context).clear()
        context.getSharedPreferences("goal_alerts", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("overlay", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage_reconciliation", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UsageWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}

class UsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val tracker = UsageTracker(applicationContext)
        if (tracker.hasAccess()) tracker.collect()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
