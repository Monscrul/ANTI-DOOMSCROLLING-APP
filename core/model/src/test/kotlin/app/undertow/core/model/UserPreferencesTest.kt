package app.undertow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun `defaults are usable without any stored preference`() {
        val defaults = UserPreferences()

        assertNull(defaults.dailyLimitUnits)
        assertFalse(defaults.hasLimit)
        assertFalse(defaults.onboardingComplete)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(PrecisionPreference.AUTO, defaults.precisionPreference)
        assertEquals(UserPreferences.CURRENT_SCHEMA_VERSION, defaults.schemaVersion)
    }

    @Test
    fun `default tracked apps are the four the product targets`() {
        assertEquals(
            setOf(
                "com.instagram.android",
                "com.google.android.youtube",
                "com.facebook.katana",
                "com.snapchat.android",
            ),
            UserPreferences.DEFAULT_TRACKED_PACKAGES,
        )
    }

    @Test
    fun `default alert thresholds are percentages`() {
        assertTrue(UserPreferences.DEFAULT_ALERT_THRESHOLDS.all { it in 1..100 })
        assertEquals(setOf(50, 80, 100), UserPreferences.DEFAULT_ALERT_THRESHOLDS)
    }

    @Test
    fun `a limit of zero is a limit, not the absence of one`() {
        val blocked = UserPreferences(dailyLimitUnits = 0)

        assertTrue(blocked.hasLimit)
        assertEquals(0, blocked.dailyLimitUnits)
    }

    @Test
    fun `invalid preferences are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserPreferences(dailyLimitUnits = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserPreferences(alertThresholds = setOf(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserPreferences(alertThresholds = setOf(150))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserPreferences(estimationSecondsPerScroll = mapOf("com.a" to 0))
        }
    }
}
