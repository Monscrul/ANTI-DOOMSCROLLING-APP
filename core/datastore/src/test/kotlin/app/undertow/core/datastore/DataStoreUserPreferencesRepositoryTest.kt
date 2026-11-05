package app.undertow.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.undertow.core.model.LimitMode
import app.undertow.core.model.PrecisionPreference
import app.undertow.core.model.ThemeMode
import app.undertow.core.model.UserPreferences
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercised against a real file-backed DataStore on the JVM: no Android context and no
 * emulator. Reopening the store on the same file is what "persists across process
 * death" means in practice.
 */
class DataStoreUserPreferencesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(tempFolder.root, "user_preferences.preferences_pb")
        repository = openRepository()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun openRepository(): UserPreferencesRepository = DataStoreUserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )

    /**
     * Releases the current store (DataStore allows one live instance per file) and
     * builds a new one over the same file, which is what a process restart does.
     */
    private suspend fun reopenRepository(): UserPreferencesRepository {
        val job = scope.coroutineContext[Job]!!
        scope.cancel()
        job.join()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return openRepository()
    }

    @Test
    fun `absent file yields documented defaults`() = runBlocking {
        val defaults = repository.current()

        assertEquals(UserPreferences(), defaults)
        assertNull(defaults.dailyLimitUnits)
        assertFalse(defaults.onboardingComplete)
    }

    @Test
    fun `every setting round trips`() = runBlocking {
        repository.setDailyLimitUnits(45)
        repository.setLimitMode(LimitMode.DURATION)
        repository.setWeekStart(DayOfWeek.SUNDAY)
        repository.setTrackedPackages(setOf("com.instagram.android", "com.snapchat.android"))
        repository.setPrecisionPreference(PrecisionPreference.COUNTED_ONLY)
        repository.setEstimationSecondsPerScroll(mapOf("com.instagram.android" to 6))
        repository.setMascotEnabled(false)
        repository.setAlertThresholds(setOf(25, 100))
        repository.setNotificationsEnabled(false)
        repository.setOnboardingComplete(true)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setLastIngestCursorUtcMillis(1_760_000_000_000L)

        val stored = repository.current()

        assertEquals(45, stored.dailyLimitUnits)
        assertEquals(LimitMode.DURATION, stored.limitMode)
        assertEquals(DayOfWeek.SUNDAY, stored.weekStart)
        assertEquals(setOf("com.instagram.android", "com.snapchat.android"), stored.trackedPackages)
        assertEquals(PrecisionPreference.COUNTED_ONLY, stored.precisionPreference)
        assertEquals(mapOf("com.instagram.android" to 6), stored.estimationSecondsPerScroll)
        assertFalse(stored.mascotEnabled)
        assertEquals(setOf(25, 100), stored.alertThresholds)
        assertFalse(stored.notificationsEnabled)
        assertTrue(stored.onboardingComplete)
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(1_760_000_000_000L, stored.lastIngestCursorUtcMillis)
    }

    @Test
    fun `values survive reopening the store from disk`() = runBlocking {
        repository.setDailyLimitUnits(30)
        repository.setOnboardingComplete(true)
        repository.setAlertThresholds(setOf(80))

        // A fresh store over the same file: the state that a process restart would rebuild.
        val reloaded = reopenRepository().current()

        assertEquals(30, reloaded.dailyLimitUnits)
        assertTrue(reloaded.onboardingComplete)
        assertEquals(setOf(80), reloaded.alertThresholds)
    }

    @Test
    fun `clearing the limit removes it rather than storing zero`() = runBlocking {
        repository.setDailyLimitUnits(20)
        assertEquals(20, repository.current().dailyLimitUnits)

        repository.setDailyLimitUnits(null)

        assertNull(repository.current().dailyLimitUnits)
    }

    @Test
    fun `clear restores defaults including the ingest watermark`() = runBlocking {
        repository.setDailyLimitUnits(12)
        repository.setLastIngestCursorUtcMillis(42L)
        repository.setThemeMode(ThemeMode.LIGHT)

        repository.clear()

        assertEquals(UserPreferences(), repository.current())
        assertNull(repository.current().lastIngestCursorUtcMillis)
    }
}
