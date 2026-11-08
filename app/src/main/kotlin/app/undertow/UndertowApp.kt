package app.undertow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.undertow.core.datastore.createUserPreferencesRepository
import app.undertow.core.database.IngestRunEntity
import app.undertow.core.model.UserPreferences
import app.undertow.tracking.DayUsage
import app.undertow.tracking.ScrollCounts
import app.undertow.tracking.UsageTracker
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UndertowApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val tracker = remember { UsageTracker(context.applicationContext) }
    val prefsRepo = remember { createUserPreferencesRepository(context.applicationContext) }
    var access by remember { mutableStateOf(tracker.hasAccess()) }
    var paused by remember { mutableStateOf(tracker.isPaused()) }
    var usage by remember { mutableStateOf<DayUsage?>(null) }
    var counts by remember { mutableStateOf(ScrollCounts(0, emptyMap())) }
    var manualCount by remember { mutableStateOf(0) }
    var manualDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf<List<app.undertow.core.database.DailySummaryEntity>>(emptyList()) }
    var runs by remember { mutableStateOf<List<IngestRunEntity>>(emptyList()) }
    var prefs by remember { mutableStateOf(UserPreferences()) }
    var tab by remember { mutableStateOf("Today") }
    var onboardingStep by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val overlayPrefs = remember { context.getSharedPreferences("overlay", 0) }
    var overlayEnabled by remember { mutableStateOf(overlayPrefs.getBoolean("enabled", false)) }
    var exportCsv by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportCsv) }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { prefsRepo.setNotificationsEnabled(granted); revision++ }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) {
        access = tracker.hasAccess()
        paused = tracker.isPaused()
        if (overlayEnabled && Settings.canDrawOverlays(context)) {
            overlayPrefs.edit().putLong("refresh_at", System.currentTimeMillis()).apply()
        }
        try {
            prefs = withContext(Dispatchers.IO) { prefsRepo.current() }
            counts = withContext(Dispatchers.IO) { tracker.counts() }
            manualCount = withContext(Dispatchers.IO) { tracker.manualCount() }
            if (access && !paused) {
                usage = withContext(Dispatchers.IO) { tracker.collect() }
                estimate = withContext(Dispatchers.IO) { tracker.estimate(usage!!) }
            } else usage = null
            history = withContext(Dispatchers.IO) { tracker.history(14) }
            runs = withContext(Dispatchers.IO) { tracker.recentRuns() }
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Could not load usage data"
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF071620), Color(0xFF133E4C), Color(0xFF10222F))))) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x5549A8AC), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.12f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.85f, size.height * 0.12f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x334282A0), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.82f),
                radius = size.width * 0.7f,
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.1f, size.height * 0.82f),
        )
    }
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("UNDERTOW", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA2D8D8))
            Text("See the pull.", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (paused) "Tracking paused" else if (counts.counted > 0) "${counts.counted} scrolls detected" else if (access) "≈ $estimate estimated scrolls" else "Tracking off",
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC23525E)).padding(horizontal = 22.dp, vertical = 12.dp),
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("Today", "History", "Limits", "Settings").forEach { name ->
                    TextButton(onClick = { tab = name }) { Text(name) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!prefs.onboardingComplete && tab == "Today") {
                    GlassPanel {
                        Text("GET STARTED · ${onboardingStep + 1}/3", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA2D8D8))
                        Text(when (onboardingStep) {
                            0 -> "Know what we measure"
                            1 -> "Choose your access"
                            else -> "Your data stays yours"
                        }, style = MaterialTheme.typography.titleLarge)
                        Text(when (onboardingStep) {
                            0 -> "Undertow measures time spent in selected apps. An optional service can detect scroll gestures, but cannot verify video views."
                            1 -> "Grant Usage Access for app time. The personal build can also enable scroll detection and a floating count through Android Settings."
                            else -> "Data is stored on this device. You can export it as CSV or delete it in Settings. Tracking begins when access is granted."
                        })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (onboardingStep < 2) onboardingStep++
                                else scope.launch { prefsRepo.setOnboardingComplete(true); revision++ }
                            }) { Text(if (onboardingStep < 2) "Next" else "Finish") }
                            TextButton(onClick = { scope.launch { prefsRepo.setOnboardingComplete(true); revision++ } }) { Text("Skip") }
                        }
                    }
                }
                if (!access && tab != "Settings") {
                    Text("Usage access needed", style = MaterialTheme.typography.headlineSmall)
                    Text("Allow Undertow to read app usage in Android Settings. Tracking starts after access is granted. Only app-level foreground time is available; reels and feed time cannot be separated.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("Open usage access settings")
                    }
                }
                when (tab) {
                    "Today" -> {
                        GlassPanel {
                        Text("Today · ${LocalDate.now()}", style = MaterialTheme.typography.headlineSmall)
                        Text("App foreground time · measured", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(formatTime(usage?.totalMillis ?: 0), style = MaterialTheme.typography.displayMedium)
                        val depth = prefs.dailyLimitUnits?.takeIf { it > 0 }?.let { limit ->
                            ((usage?.totalMillis ?: 0) / (limit * 60_000f)).coerceIn(0f, 1f)
                        } ?: 0f
                        DrifterMeter(depth)
                        if (usage?.byPackage.isNullOrEmpty()) Text("Nothing measured yet. Android may delay recent usage events.")
                        usage?.byPackage?.toList()?.sortedByDescending { it.second }?.forEach { (pkg, millis) ->
                            Text("${appName(pkg)}: ${formatTime(millis)}")
                        }
                        Text("App time includes all screens in each app, not just reels or feeds. Recent activity can take a few minutes to appear.")
                        Text("${counts.counted} gestures detected · ≈ $estimate estimated from app time")
                        Text("$manualCount scrolls entered by you")
                        OutlinedButton(onClick = { manualDialog = true }) { Text("Add a manual count") }
                        prefs.dailyLimitUnits?.takeIf { it > 0 }?.let { limit ->
                            LinearProgressIndicator(progress = { ((usage?.totalMillis ?: 0) / (limit * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
                        }
                    }
                    "History" -> {
                        GlassPanel {
                        Text("History", style = MaterialTheme.typography.headlineSmall)
                        val recent = history.takeLast(7)
                        val peak = (recent.maxOfOrNull { it.durationMillis } ?: 1L).coerceAtLeast(1L)
                        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                            val step = size.width / 7
                            recent.forEachIndexed { index, row ->
                                val height = size.height * row.durationMillis.toFloat() / peak
                                drawRoundRect(
                                    color = Color(0xFF86D7CF),
                                    topLeft = Offset(index * step + 4.dp.toPx(), size.height - height),
                                    size = Size((step - 8.dp.toPx()).coerceAtLeast(2f), height),
                                    cornerRadius = CornerRadius(7.dp.toPx()),
                                )
                            }
                        }
                        Text("Last 7 days · ${formatTime(recent.sumOf { it.durationMillis })} total")
                        if (history.isEmpty()) Text("No saved days yet")
                        history.reversed().forEach { row ->
                            Text("${row.dateKey}: ${formatTime(row.durationMillis)} · ≈${row.estimatedUnits} estimated scrolls")
                        }
                        Text("Scrolls detected today: ${counts.counted}")
                        Text("Manual entries today: $manualCount")
                        }
                    }
                    "Limits" -> {
                        GlassPanel {
                        Text("Daily time goal", style = MaterialTheme.typography.headlineSmall)
                        Text("A goal is informational; it does not block other apps.")
                        Text("Current: ${prefs.dailyLimitUnits?.let { "$it minutes" } ?: "off"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 120).forEach { minutes ->
                                OutlinedButton(onClick = {
                                    scope.launch { prefsRepo.setDailyLimitUnits(minutes); revision++ }
                                }) { Text("$minutes min") }
                            }
                        }
                        OutlinedButton(onClick = {
                            scope.launch { prefsRepo.setDailyLimitUnits(null); revision++ }
                        }) { Text("Turn off") }
                        prefs.dailyLimitUnits?.takeIf { it > 0 }?.let { limit ->
                            val percent = ((usage?.totalMillis ?: 0) / (limit * 60_000.0) * 100).toInt()
                            Text("Today: $percent% of goal")
                        }
                        Text("Reminder thresholds")
                        listOf(50, 80, 100).forEach { threshold ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(threshold in prefs.alertThresholds, onCheckedChange = { checked ->
                                    scope.launch {
                                        prefsRepo.setAlertThresholds(if (checked) prefs.alertThresholds + threshold else prefs.alertThresholds - threshold)
                                        revision++
                                    }
                                })
                                Text("$threshold% of goal")
                            }
                        }
                        }
                    }
                    "Settings" -> {
                        GlassPanel {
                        Text("Tracked apps", style = MaterialTheme.typography.headlineSmall)
                        if (paused) Button(onClick = { tracker.resume(); revision++ }) { Text("Resume tracking") }
                        (UserPreferences.DEFAULT_TRACKED_PACKAGES + setOf("com.instagram.lite", "com.facebook.lite")).sorted().forEach { pkg ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(pkg in prefs.trackedPackages, onCheckedChange = { checked ->
                                    scope.launch {
                                        prefsRepo.setTrackedPackages(if (checked) prefs.trackedPackages + pkg else prefs.trackedPackages - pkg)
                                        revision++
                                    }
                                })
                                Text(appName(pkg))
                            }
                            var seconds by remember(pkg, prefs.estimationSecondsPerScroll[pkg]) {
                                mutableStateOf((prefs.estimationSecondsPerScroll[pkg] ?: 22).toString())
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = seconds,
                                    onValueChange = { seconds = it.filter(Char::isDigit).take(3) },
                                    label = { Text("${appName(pkg)} seconds per estimated scroll") },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    val value = seconds.toIntOrNull()?.takeIf { it > 0 } ?: return@TextButton
                                    scope.launch {
                                        prefsRepo.setEstimationSecondsPerScroll(prefs.estimationSecondsPerScroll + (pkg to value))
                                        revision++
                                    }
                                }) { Text("Save") }
                            }
                        }
                        Text("Data stays on this device. Usage access can be revoked in Android Settings.")
                        Text("Last collection: ${runs.firstOrNull()?.let { java.text.DateFormat.getDateTimeInstance().format(it.finishedAtUtcMillis ?: it.startedAtUtcMillis) } ?: "never"}")
                        Text("Last outcome: ${runs.firstOrNull()?.outcome ?: "none"}")
                        if (runs.firstOrNull()?.outcome == app.undertow.core.database.IngestOutcome.GAP_DETECTED) {
                            Text("A collection gap was detected. Older app activity may no longer be available from Android.")
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) { Text("Usage access settings") }
                        if (BuildConfig.FLAVOR == "personal") {
                            val accessibilityEnabled = context.getSystemService(AccessibilityManager::class.java)
                                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                                .any { it.resolveInfo.serviceInfo.packageName == context.packageName &&
                                    it.resolveInfo.serviceInfo.name == "app.undertow.tracking.ScrollAccessibilityService" }
                            Text("Scroll detection: ${if (accessibilityEnabled) "on" else "off"}")
                            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                                Text("Scroll detection settings")
                            }
                            Text("The optional service detects gestures in selected apps. It cannot verify that each gesture advanced a video.")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(overlayEnabled, onCheckedChange = { enabled ->
                                    overlayEnabled = enabled
                                    overlayPrefs.edit().putBoolean("enabled", enabled).apply()
                                    if (enabled && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                    }
                                })
                                Text("Floating scroll-count pill")
                            }
                            Text("Requires Display over other apps and the scroll detection service.")
                            Text("Overlay permission: ${if (Settings.canDrawOverlays(context)) "granted" else "missing"}")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            Switch(prefs.notificationsEnabled && notificationsGranted, onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else scope.launch { prefsRepo.setNotificationsEnabled(enabled); revision++ }
                            })
                            Text("Goal notifications")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                exportCsv = withContext(Dispatchers.IO) { tracker.exportCsv() }
                                exportLauncher.launch("undertow-usage.csv")
                            }
                        }) { Text("Export CSV") }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete all app data") }
                        }
                    }
                }
                error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete Undertow data?") },
        text = { Text("This removes all saved usage, scroll events, and settings from this device. Revoke Usage Access separately in Android Settings.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch { withContext(Dispatchers.IO) { tracker.deleteAll() }; overlayEnabled = false; revision++ }
        }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    if (manualDialog) AlertDialog(
        onDismissRequest = { manualDialog = false },
        title = { Text("Add a manual count") },
        text = { OutlinedTextField(manualInput, onValueChange = { manualInput = it.filter(Char::isDigit).take(5) }, label = { Text("Scrolls") }) },
        confirmButton = { TextButton(onClick = {
            val number = manualInput.toIntOrNull()?.takeIf { it > 0 } ?: return@TextButton
            manualDialog = false
            manualInput = ""
            scope.launch { withContext(Dispatchers.IO) { tracker.addManual(number) }; revision++ }
        }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { manualDialog = false }) { Text("Cancel") } },
    )
}

@androidx.compose.runtime.Composable
private fun GlassPanel(content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

private fun formatTime(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

private fun appName(pkg: String): String = when (pkg) {
    "com.instagram.android" -> "Instagram"
    "com.instagram.lite" -> "Instagram Lite"
    "com.google.android.youtube" -> "YouTube"
    "com.facebook.katana" -> "Facebook"
    "com.facebook.lite" -> "Facebook Lite"
    "com.snapchat.android" -> "Snapchat"
    else -> pkg
}

@Composable
private fun DrifterMeter(depth: Float) {
    val band = when {
        depth < 0.5f -> "Surface"
        depth < 0.8f -> "Shallows"
        depth < 1f -> "Deep water"
        else -> "At your goal"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(126.dp).semantics { contentDescription = "Depth indicator: $band" }) {
            val center = Offset(size.width / 2, size.height / 2)
            val outer = size.height * 0.42f
            drawCircle(Color(0x228DE0D8), outer * 1.1f, center)
            drawCircle(Color(0xFF8EDDD5), outer, center, style = Stroke(2.dp.toPx()))
            drawCircle(Color(0x7791DDD8), outer * 0.7f, center, style = Stroke(1.dp.toPx()))
            drawCircle(Color(0x5591DDD8), outer * 0.4f, center, style = Stroke(1.dp.toPx()))
            drawLine(Color(0xFF91DDD8), Offset(center.x, center.y - outer * 0.48f),
                Offset(center.x, center.y + outer * 0.55f), strokeWidth = 3.dp.toPx())
            drawLine(Color(0xFF91DDD8), Offset(center.x - outer * 0.19f, center.y + outer * 0.33f),
                Offset(center.x, center.y + outer * 0.55f), strokeWidth = 3.dp.toPx())
            drawLine(Color(0xFF91DDD8), Offset(center.x + outer * 0.19f, center.y + outer * 0.33f),
                Offset(center.x, center.y + outer * 0.55f), strokeWidth = 3.dp.toPx())
        }
        Text(band, color = Color(0xFF91DDD8), style = MaterialTheme.typography.labelLarge)
    }
}
