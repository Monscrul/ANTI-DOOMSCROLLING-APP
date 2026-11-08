package app.undertow.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.undertow.core.model.LimitMode
import app.undertow.core.model.PrecisionPreference
import app.undertow.core.model.ThemeMode
import app.undertow.core.model.UserPreferences
import java.io.IOException
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "user_preferences"

/** The delegate guarantees one DataStore instance per process, which the API requires. */
val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

private object Keys {
    val DAILY_LIMIT_UNITS = intPreferencesKey("daily_limit_units")
    val LIMIT_MODE = stringPreferencesKey("limit_mode")
    val WEEK_START = stringPreferencesKey("week_start")
    val TRACKED_PACKAGES = stringSetPreferencesKey("tracked_packages")
    val PRECISION_PREFERENCE = stringPreferencesKey("precision_preference")
    val ESTIMATION_OVERRIDES = stringPreferencesKey("estimation_overrides")
    val MASCOT_ENABLED = booleanPreferencesKey("mascot_enabled")
    val ALERT_THRESHOLDS = stringSetPreferencesKey("alert_thresholds")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LAST_INGEST_CURSOR = longPreferencesKey("last_ingest_cursor_utc_millis")
    val SCHEMA_VERSION = intPreferencesKey("schema_version")
}

class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { cause ->
            // An unreadable or corrupt file must degrade to defaults, not crash at launch.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { stored -> stored.toUserPreferences() }

    override suspend fun current(): UserPreferences = preferences.first()

    override suspend fun setDailyLimitUnits(units: Int?) {
        dataStore.edit { prefs ->
            if (units == null) prefs.remove(Keys.DAILY_LIMIT_UNITS)
            else prefs[Keys.DAILY_LIMIT_UNITS] = units
        }
    }

    override suspend fun setLimitMode(mode: LimitMode) {
        dataStore.edit { prefs -> prefs[Keys.LIMIT_MODE] = mode.name }
    }

    override suspend fun setWeekStart(day: DayOfWeek) {
        dataStore.edit { prefs -> prefs[Keys.WEEK_START] = day.name }
    }

    override suspend fun setTrackedPackages(packages: Set<String>) {
        dataStore.edit { prefs -> prefs[Keys.TRACKED_PACKAGES] = packages }
    }

    override suspend fun setPrecisionPreference(preference: PrecisionPreference) {
        dataStore.edit { prefs -> prefs[Keys.PRECISION_PREFERENCE] = preference.name }
    }

    override suspend fun setEstimationSecondsPerScroll(overrides: Map<String, Int>) {
        dataStore.edit { prefs ->
            prefs[Keys.ESTIMATION_OVERRIDES] = EstimationOverrideCodec.encode(overrides)
        }
    }

    override suspend fun setMascotEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.MASCOT_ENABLED] = enabled }
    }

    override suspend fun setAlertThresholds(thresholds: Set<Int>) {
        dataStore.edit { prefs -> prefs[Keys.ALERT_THRESHOLDS] = thresholds.map { it.toString() }.toSet() }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setLastIngestCursorUtcMillis(utcMillis: Long?) {
        dataStore.edit { prefs ->
            if (utcMillis == null) prefs.remove(Keys.LAST_INGEST_CURSOR)
            else prefs[Keys.LAST_INGEST_CURSOR] = utcMillis
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}

/**
 * Creates the repository for the given context.
 *
 * Kept as a plain factory rather than a Hilt module so the storage layer can be
 * exercised by tests before the DI graph exists.
 */
fun createUserPreferencesRepository(context: Context): UserPreferencesRepository =
    DataStoreUserPreferencesRepository(context.applicationContext.userPreferencesDataStore)

private fun Preferences.toUserPreferences(): UserPreferences {
    val thresholds = this[Keys.ALERT_THRESHOLDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { it in 1..100 }
        ?.toSet()
        ?: UserPreferences.DEFAULT_ALERT_THRESHOLDS

    val trackedPackages = this[Keys.TRACKED_PACKAGES]
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: UserPreferences.DEFAULT_TRACKED_PACKAGES

    return UserPreferences(
        dailyLimitUnits = this[Keys.DAILY_LIMIT_UNITS],
        limitMode = this[Keys.LIMIT_MODE].enumOrDefault(LimitMode.UNITS),
        weekStart = this[Keys.WEEK_START].enumOrDefault(DayOfWeek.MONDAY),
        trackedPackages = trackedPackages,
        precisionPreference = this[Keys.PRECISION_PREFERENCE].enumOrDefault(PrecisionPreference.AUTO),
        estimationSecondsPerScroll = EstimationOverrideCodec.decode(this[Keys.ESTIMATION_OVERRIDES]),
        mascotEnabled = this[Keys.MASCOT_ENABLED] ?: true,
        alertThresholds = thresholds,
        notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: true,
        onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: false,
        themeMode = this[Keys.THEME_MODE].enumOrDefault(ThemeMode.SYSTEM),
        lastIngestCursorUtcMillis = this[Keys.LAST_INGEST_CURSOR],
        schemaVersion = this[Keys.SCHEMA_VERSION] ?: UserPreferences.CURRENT_SCHEMA_VERSION,
    )
}

/** Unknown names fall back to the default so a downgrade cannot crash the app. */
private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T {
    if (this == null) return default
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}
