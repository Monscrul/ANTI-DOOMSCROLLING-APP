package app.undertow.tracking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.room.Room
import app.undertow.core.database.UndertowDatabase
import app.undertow.core.database.UsageEventEntity
import app.undertow.core.datastore.createUserPreferencesRepository
import app.undertow.core.model.ObservationSource
import app.undertow.core.model.ObservationType
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Opt-in gesture counter. It observes package and event type only, never view contents. */
class ScrollAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val lastEvent = mutableMapOf<String, Long>()
    private val db by lazy { Room.databaseBuilder(this, UndertowDatabase::class.java, UndertowDatabase.NAME).build() }
    private val window by lazy { getSystemService(WindowManager::class.java) }
    private var pill: TextView? = null
    private val settings by lazy { getSharedPreferences("overlay", Context.MODE_PRIVATE) }
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings.registerOnSharedPreferenceChangeListener(listener)
        updateOverlay()
        refreshCount()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString() ?: return
        val at = System.currentTimeMillis()
        synchronized(lastEvent) {
            // A single swipe often produces a burst of view-scrolled events.
            if (at - (lastEvent[pkg] ?: 0) < 700L) return
            lastEvent[pkg] = at
        }
        scope.launch {
            if (UsageTracker(applicationContext).isPaused()) return@launch
            val tracked = createUserPreferencesRepository(applicationContext).current().trackedPackages
            if (pkg !in tracked) return@launch
            db.usageLogDao().insertEvents(listOf(UsageEventEntity(
                UUID.randomUUID().toString(), pkg, ObservationType.VIEW_SCROLLED, at,
                ObservationSource.ACCESSIBILITY, "accessibility",
            )))
            refreshCount()
        }
    }

    private fun refreshCount() {
        scope.launch {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val count = db.usageLogDao().scrollCountIn(start, System.currentTimeMillis() + 1)
            pill?.post { pill?.text = "$count  scrolls detected" }
        }
    }

    private fun updateOverlay() {
        val enabled = settings.getBoolean("enabled", false) && Settings.canDrawOverlays(this)
        if (!enabled) {
            pill?.let { window.removeView(it) }
            pill = null
            return
        }
        if (pill != null) return
        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(245, 25, 46, 56), Color.argb(245, 44, 81, 91)),
        ).apply {
            cornerRadius = 100f
            setStroke(1, Color.argb(130, 181, 227, 231))
        }
        val view = TextView(this).apply {
            text = "0  scrolls detected"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(36, 14, 36, 14)
            this.background = background
            elevation = 14f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 14 }
        try {
            window.addView(view, params)
            pill = view
            refreshCount()
        } catch (_: WindowManager.BadTokenException) {
            // Overlay permission can be revoked while Settings is returning.
        } catch (_: SecurityException) {
            // The system may revoke overlay permission between the check and addView.
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settings.unregisterOnSharedPreferenceChangeListener(listener)
        pill?.let { window.removeView(it) }
        pill = null
        scope.cancel()
        super.onDestroy()
    }
}
