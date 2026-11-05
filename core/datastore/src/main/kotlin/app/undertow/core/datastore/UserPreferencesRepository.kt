package app.undertow.core.datastore

import app.undertow.core.model.LimitMode
import app.undertow.core.model.PrecisionPreference
import app.undertow.core.model.ThemeMode
import app.undertow.core.model.UserPreferences
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

/**
 * The single entry point for user settings: one observable snapshot plus explicit
 * mutators. There is deliberately no `save(UserPreferences)` — a whole-object write
 * would let two screens overwrite each other's fields with stale values.
 */
interface UserPreferencesRepository {

    val preferences: Flow<UserPreferences>

    /** One-shot read, for code that needs the value once rather than a subscription. */
    suspend fun current(): UserPreferences

    suspend fun setDailyLimitUnits(units: Int?)

    suspend fun setLimitMode(mode: LimitMode)

    suspend fun setWeekStart(day: DayOfWeek)

    suspend fun setTrackedPackages(packages: Set<String>)

    suspend fun setPrecisionPreference(preference: PrecisionPreference)

    suspend fun setEstimationSecondsPerScroll(overrides: Map<String, Int>)

    suspend fun setMascotEnabled(enabled: Boolean)

    suspend fun setAlertThresholds(thresholds: Set<Int>)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setOnboardingComplete(complete: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLastIngestCursorUtcMillis(utcMillis: Long?)

    /** Clears everything, including the ingest watermark. Part of the delete-data promise. */
    suspend fun clear()
}
