# Undertow — Implementation Plan

> **Implementation update (8 November 2026):** The original inventory below is historical. The local app now builds in `store` and `personal` variants. Usage Access collection, daily storage, history, limits and reminders, manual entries, CSV export, deletion, diagnostics, and a glass-style UI are implemented. The `personal` variant adds opt-in accessibility scroll detection and a floating count overlay. Detected gestures are not verified video views. Firebase and social phases are deferred by user choice. Both variants build, pass lint, and the model and tracking unit tests pass. Live permission and overlay behavior still need a permitted installation and manual device check; the connected device rejected installation of the personal APK (`INSTALL_FAILED_USER_RESTRICTED`).

> **Status:** Planning only. Nothing in this document has been implemented. No Gradle files, Kotlin sources, manifests, or resources exist yet.
> **Working title:** *Undertow* (placeholder — see [§1.4](#14-naming-and-original-identity)). Package id placeholder: `app.undertow.tracker` — **must** be replaced with a domain you own before the first Play upload.
> **Date:** 4 November 2026

---

## 1. Repository & environment findings

### 1.1 Repository state

| Check | Result |
|---|---|
| Contents | **Empty.** Only a `.freebuff/` tool directory (`project-id`, `.gitignore`) |
| `git` | **Not a repository** (`git status` → `fatal: not a git repository`) |
| Kotlin / Gradle / Java sources | **Zero files** |
| `AGENTS.md` / `CLAUDE.md` | Do not exist |
| Existing dependencies / conventions | None — nothing to preserve or match |

**Conclusion:** there is no existing structure to analyse. This is a greenfield project, so every convention in this document is a *proposal*, not a description of existing code. Section 2 is therefore "recommended architecture", not "current architecture".

### 1.2 Local toolchain (this machine)

| Tool | State | Impact |
|---|---|---|
| JDK 17.0.12 | ✅ Installed (`/Library/Java/JavaVirtualMachines/jdk-17.jdk`) | Exactly what AGP 9 requires (min **and** default JDK = 17) |
| JDK 25.0.2 | ✅ Installed, and it is the default `java` on `PATH` | ⚠️ Should **not** run the Gradle daemon — target 17 via `org.gradle.java.home` or a toolchain |
| Android SDK (`ANDROID_HOME`) | ❌ Unset; no `sdkmanager`, no `adb` | **Blocker** — cannot compile any Android code |
| Android Studio | ❌ Not installed | Not strictly required (CLI build works), but no emulator/device tooling |
| Gradle CLI | ❌ Not installed | Not needed — the wrapper supplies Gradle; only needs network on first run |
| `git` | ✅ 2.50.1 (Apple) | Repo needs initialising |
| Node | ✅ v20.17.0 | Irrelevant to this project |
| **Free disk space** | ⚠️ **4.4 GiB** on `/` | **Blocker.** Android SDK (platform + build-tools + one emulator image) + Gradle dependency cache ≈ **10–12 GB** |

**Phase 0 cannot start until:** (a) ~12 GB is freed, (b) the Android SDK command-line tools + `platforms;android-37` + `build-tools;36.0.0` are installed, (c) `local.properties` points at the SDK. Until then the plan is verified only against documentation, not a compiler.

### 1.3 What the reference app actually does (facts, for feature parity only)

From the public listing (`play.google.com/store/apps/details?id=com.brainrot.android`), the developer's own site, and third-party write-ups:

- Counts **reels/Shorts scrolled** across Instagram, YouTube, Facebook, Snapchat — not a generic screen-time app.
- A **live brain-visual** that degrades as the count rises.
- Friend-vs-friend **"Scroll Battles"** (lower count wins), plus a home-screen widget.
- *Blocking* reels/shorts once a cap is hit (paid tier).
- The listing states plainly: *"BrainPal uses the **Accessibility Service API** to detect scrolling inside selected apps."*
- ~1M+ installs, 4.5★. Despite "data never leaves your device" marketing, its Play **Data safety** declaration says it collects Personal info and App activity.

**Nothing here will be copied** — no name, no package id, no artwork, no copy, no colour scheme, no mascot, no string resources. Only the *category of problem* is shared, which is legitimate.

### 1.4 Naming and original identity

| Item | Proposal | Why |
|---|---|---|
| Name | **Undertow** | The pull that drags you under — a behaviour metaphor, no "brain", no "pal", no "-rot" |
| Alternates (if you dislike it) | *Fathom*, *Slack Water* | Same nautical register, both short and unclaimed-feeling |
| Tagline | "See the pull." | Not a count promise — deliberately aligned with what is actually measurable |
| Mascot | **The Drifter** — a line-drawn, hex-outlined cortical form that **sinks** through four depth bands | Submersion is a different metaphor from the reference's *cracking/rotting* brain |
| Palette | Ink `#0B1A20`, Paper `#F2EDE4`, Accent Ember `#E2653B`, Depth Teal `#2E7F86`, Salt `#8FA6A8` | No purple gradients, no neon, no glossy 3D — an analogue instrument, not a toy |
| Type | Display: tight geometric sans; Numerals: **tabular mono** for every count | Counts must not jitter as they tick |
| Motion | One idea — depth. 240–320 ms, ease-out; no bounce, no confetti | Original motion signature |
| Icon | Bathymetric contour lines forming a downward arrow head | Distinct silhouette |

Deliberately avoided: brains as branding, "rot", red/neon degradation, mascots with facial cracks, and any string that could read as a medical claim.

---

## 2. The decision that governs everything: what can we actually measure?

This is the single most important section. The brief asks for "count reels and shorts". **Android does not expose that number to third-party apps.** The architecture has to encode that honestly instead of pretending.

### 2.1 Measurement options, ranked

| # | Source | What it really gives you | Reel count? | Play-policy risk |
|---|---|---|---|---|
| A | `UsageStatsManager.queryEvents` (`PACKAGE_USAGE_STATS` special access) | Package-level foreground **session boundaries** (`ACTIVITY_RESUMED`/`PAUSED`, screen on/off, keyguard) with minute-scale latency | ❌ No — only that Instagram was foreground | **None** (public API, user-granted) |
| B | `UsageStatsManager.queryUsageStats` (aggregated) | Bucketed totals per day/week — lossy, re-aggregated retroactively | ❌ No | None |
| C | **AccessibilityService** (`typeViewScrolled`) | Real scroll events per package → genuine per-scroll counting | ✅ Yes (the reference app's method) | **High** — Play names *"monitoring apps"* as **not** accessibility tools; requires declaration + demo video + consent flow; may be rejected |
| D | Notification listener | Only that a notification was posted | ❌ No | High (sensitive user data), and useless for timing |
| E | Screen capture / `MediaProjection` | Pixels; could infer reels by OCR/vision | ⚠️ Technically, with a permanent screen-share consent banner | High, plus battery/privacy cost and constant user-visible indicator |
| F | Manual entry / correction | Exactly what the user says | ✅ Trivially | None |

**Hard limits of option A that shape the UI:**
- *"The last few minutes of the event log will be truncated to prevent abuse"* (platform javadoc) → there is **no real-time counter**. Any "live" number lags by minutes and must be labelled.
- *"Events are only kept by the system for a few days"* → history before install is unrecoverable; gaps after long offline periods are permanent.
- Feed vs. Reels live inside **one Activity** in all four apps → you cannot separate "reels" from "feed" by activity name. Snapchat/Facebook reels are indistinguishable from the main feed entirely.
- No callback exists for "active app changed" → polling only (WorkManager, 15-min floor, Doze-deferred).

### 2.2 Consequence: capability tiers, not one tracker

The app ships **one honest metric with two precision tiers**, selected by what the user has granted:

```
Tier 2  COUNTED    accessibility granted   exact scroll events        "counted"
Tier 1  ESTIMATED  usage access only       time-derived estimate      "estimated"
Tier 0  MANUAL     nothing granted         user-entered corrections   "manual"
```

Every number in the UI carries its tier. No screen may show a Tier-1 figure as if it were counted. This is enforced by type: the display model carries `Precision`, and the presentation layer has exactly one place that decides the badge.

**Estimation policy (Tier 1), explicit and documented in-app:**
- Primary Tier-1 metric is **time in feed**, which is measured, not estimated.
- Secondary "≈ scrolls" is derived by a single, configurable policy object — e.g. `secondsPerScroll` per package (default 22 s, overridable per app in Settings) — and is **always** rendered with `≈` and the word *estimated*, with a link to the explanation. Users can disable it entirely.
- The constant lives in `EstimationPolicy` (a data class in `:tracking:engine`), never inline in a ViewModel or composable.

### 2.3 Store-safe vs. personal builds (the packaging answer)

Two product flavours from one codebase:

| Flavour | Contains | Distribution |
|---|---|---|
| `store` | Tier 1 + Tier 0 only. `AccessibilityScrollProvider` is a stub that reports `UNAVAILABLE`. **No** `BIND_ACCESSIBILITY_SERVICE` in the merged manifest. No `INTERNET` permission. | Play Store, no declarations needed |
| `personal` | Adds Tier 2 (accessibility service, `canRetrieveWindowContent=false`, `packageNames` restricted to the 4–6 targets, `typeViewScrolled` only) | Sideload / internal track / own device |

This is the only way to satisfy "count every reel" *and* keep a shippable Play build — and it is a genuine architectural decision, not a workaround. The UI is capability-driven, so the same screens work in both.

### 2.4 What we will *not* claim

- No medical, neurological, or "brain damage" claims. The mascot is a metaphor; onboarding says so in one sentence.
- No "real-time" claim. Home shows `Updated 2 min ago` from a real `Freshness` value.
- No retroactive history promise. Onboarding states tracking starts from the day access is granted.

---

## 3. Recommended architecture

### 3.1 Layer model

```
┌──────────────────────────────────────────────────────────────────────┐
│ :app            Application, MainActivity, NavHost, Hilt entry points│
├──────────────────────────────────────────────────────────────────────┤
│ :feature:*      Compose screens + ViewModels (MVVM, unidirectional)  │
│                 no Room, no DataStore, no Android framework services │
├──────────────────────────────────────────────────────────────────────┤
│ :core:data      Repository INTERFACES + implementations              │
│                 coordinates room ⨯ datastore ⨯ tracking ⨯ engine     │
├──────────────────────────────────────────────────────────────────────┤
│ :tracking:api   ScrollTrackingProvider contract, Capability, Ingest  │
│ :tracking:engine PURE KOTLIN: stitching, dedupe, aggregation, policy │
│ :tracking:usagestats  UsageStatsManager adapter (Android)            │
│ :tracking:accessibility (personal flavour only)                      │
├──────────────────────────────────────────────────────────────────────┤
│ :core:database  Room entities, DAOs, migrations   :core:datastore    │
│ :core:designsystem  tokens, theme, primitives     :core:model        │
│ :core:common    Clock, dispatchers, Result, time    (pure JVM)       │
└──────────────────────────────────────────────────────────────────────┘
```

**Dependency rule (enforced, not aspirational):** dependencies point **downward only**. `:core:model` and `:tracking:engine` are pure-Kotlin JVM modules with **zero Android dependencies** — this is what makes the counting logic testable without an emulator, and it is where most of the risk lives, so it gets the fastest test loop.

`:feature:*` modules must never import `androidx.room`, `androidx.datastore`, or `android.provider.*`. This is checkable with a Konsist rule (optional) or a CI grep gate.

### 3.2 Module list

| Module | Type | Responsibility |
|---|---|---|
| `:app` | Android app | `@HiltAndroidApp`, `MainActivity`, `NavHost`, flavour-specific manifest merge, app-level DI |
| `:core:model` | **JVM** | `TrackedApp`, `ScrollObservation`, `ScrollSession`, `DailySummary`, `ActivityBand`, `Precision`, `TimeWindow` — no Android, no I/O |
| `:core:common` | **JVM** | `AppClock`/`Clock`, `Dispatchers` qualifiers, `Result` wrapper, `DateKey` (local-day arithmetic), `io`/`default` scopes |
| `:core:database` | Android lib | Room DB, entities, DAOs, type converters, schema export, migrations |
| `:core:datastore` | Android lib | Preferences DataStore, `UserPreferences`, `UserPreferencesRepository` |
| `:core:designsystem` | Android lib | `UndertowTheme`, tokens (colour/type/space/motion), `DrifterCanvas`, gauges, chart primitives |
| `:tracking:api` | **JVM** | Provider interfaces, `Capability`, `ProviderAvailability`, `IngestBatch`, registry contract |
| `:tracking:engine` | **JVM** | Event→session stitching, midnight splitting, dedupe, aggregation, `EstimationPolicy`, `ActivityBand` resolution |
| `:tracking:usagestats` | Android lib | `UsageStatsScrollProvider`, permission check, WorkManager worker |
| `:tracking:accessibility` | Android lib | (personal flavour) `ScrollAccessibilityService` + `AccessibilityScrollProvider` |
| `:core:data` | Android lib | `ScrollRepository`, `LimitRepository`, `PreferencesRepository` impls, ingest orchestration |
| `:feature:onboarding` | Android lib | Permission education + staged grant |
| `:feature:home` | Android lib | Today: mascot, count, progress, freshness |
| `:feature:stats` | Android lib | Day / week / history, per-app breakdown |
| `:feature:limits` | Android lib | Daily limits, progress, alert thresholds |
| `:feature:settings` | Android lib | Tracked apps, precision, export/delete, diagnostics |

16 modules. **If you want a leaner start**, merge `:feature:onboarding`+`:feature:settings` and keep the rest — the important split is *engine/api/provider/repository/feature*, not one module per screen. (Open decision D6.)

### 3.3 MVVM, unidirectionally

```
Compose screen ──event──▶ ViewModel.onEvent(e) ──▶ Repository (suspend/Flow)
      ▲                        │
      └── StateFlow<UiState> ◀──┘        one-shot effects via Channel<UiEffect>
```

- `UiState` = one immutable `data class` per screen, `@Immutable`, **no Android or domain-entity types** — a mapper converts domain → UI model.
- Events: `sealed interface XEvent` (`OnLimitChanged`, `OnRetryTap`, `OnWeekSelected`).
- Effects: `Channel<XUiEffect>` for navigation/toasts (consume-once), never state.
- ViewModels expose `StateFlow`, built with `stateIn(viewModelScope, WhileSubscribed(5_000), Initial)`.
- Use-case classes **only** where logic is shared across ViewModels or non-trivial (`ResolveDayProgress`, `ResolveActivityBand`, `BuildWeeklySummary`). No one-line `UseCase` ceremony around DAOs.
- Collection is lifecycle-aware (`collectAsStateWithLifecycle`).

### 3.4 Threading & time

- Injected `CoroutineDispatcher`s via qualifiers (`@IoDispatcher`, `@DefaultDispatcher`) — never `Dispatchers.IO` inline.
- Injected `java.time.Clock` (interface `AppClock`) — **every** "now" in the app comes from it. This is why the day-boundary logic is testable.
- Day boundaries: `DateKey` = local `LocalDate` at `ZoneId.systemDefault()`, with the zone recorded on each session so DST and travel can be audited after the fact.
- `minSdk 26` recommended → `java.time` is available natively, **no core-library desugaring** needed. (If `minSdk 24` is required: enable `isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs`. Open decision D4.)

---

## 4. Data design

### 4.1 Room schema

Raw observations are stored **before** interpretation, so any change to counting/aggregation logic can be replayed over real history instead of invalidating it.

| Table | Key columns | Purpose |
|---|---|---|
| `usage_event` | `id`, `packageName`, `type` (RESUMED/PAUSED/STOPPED/SCREEN_ON/SCREEN_OFF/KEYGUARD/BOOT), `atUtcMillis`, `source` (USAGE_STATS/A11Y), `ingestId` — **unique index** `(package, type, atUtcMillis, source)` | Idempotent raw log; dedupe on re-ingest of the overlap window |
| `scroll_session` | `id`, `packageName`, `startUtcMillis`, `endUtcMillis`, `durationMillis`, `dateKey`, `zoneId`, `source`, `confidence` (HIGH/MEDIUM/LOW), `unitCount`, `precision` — index `(dateKey, packageName)`, unique `(package, startUtcMillis, source)` | Derived foreground sessions; recomputable from `usage_event` |
| `daily_total` | PK `(dateKey, packageName)`: `countedUnits`, `estimatedUnits`, `durationMillis`, `sessionCount` | Queryable per-app breakdown, no JSON blobs |
| `daily_summary` | PK `dateKey`: `countedUnits`, `estimatedUnits`, `durationMillis`, `dominantPrecision`, `limitUnitsAtTime`, `computedAtUtcMillis`, `revision` | Fast home/stats reads; `limitUnitsAtTime` snapshots the limit so history stays truthful after the user changes it |
| `ingest_run` | `id`, `startedAt`, `finishedAt`, `providerId`, `eventsRead`, `sessionsWritten`, `outcome`, `errorCode` | Diagnostics screen + support without guessing |
| `manual_entry` | `id`, `dateKey`, `deltaUnits`, `note`, `createdAt` | Tier-0 corrections; always additive to the real series, never a replacement |

Deliberate choices: epoch **millis UTC** for instants (traceable), ISO-8601 `String` for `dateKey` (sortable, range-queryable, DST-auditable). **Room entities are never `@Parcelize`** — a known AGP-9/KSP bug breaks `@Entity + @Parcelize` type resolution.
Schema exported via the Room Gradle plugin (`schemaDirectory`) and covered by `MigrationTestHelper`.

### 4.2 DataStore (user preferences)

`UserPreferences` (typed DataStore, single repository, `Flow` + `suspend` updates):

```
dailyLimitUnits: Int?          // null = no limit
limitMode: LimitMode           // UNITS | DURATION
weekStart: DayOfWeek
trackedPackages: Set<String>   // seeded defaults, user-editable
precisionPreference: PrecisionPreference  // AUTO | COUNTED_ONLY | ESTIMATED_OK
estimationSecondsPerScroll: Map<String, Int>
mascotEnabled: Boolean
alertThresholds: Set<Int>      // e.g. 50%, 80%, 100%
notificationsEnabled: Boolean
onboardingComplete: Boolean
themeMode: ThemeMode
lastIngestCursorUtcMillis: Long  // ingest watermark
schemaVersion: Int
```

Limits live here (they are preferences), while `daily_summary.limitUnitsAtTime` preserves what the limit *was* on that day — the two together give accurate historical progress bars.

### 4.3 Future Firebase compatibility (design now, build later)

Cheap decisions now that prevent a rewrite at Phase 7:

- All local rows carry a **UUID string** primary id (not autoincrement) → safe to replicate.
- Reserved nullable sync columns: `remoteId`, `updatedAtMillis`, `deletedAtMillis` (soft delete), `syncState`.
- Repositories are **already interfaces**; Phase 7 adds a `SyncRepository` decorator plus an outbox table. No feature code changes.
- **Leaderboards must be server-authoritative.** Client-reported counts are trivially spoofable; friends' scores get validated/written by a Cloud Function, and the local count is only a *claim*. This constraint belongs in the design now, because it dictates that `daily_summary` stays the canonical local fact and sync is one-directional-ish (local → claim → server → feed).
- No `INTERNET` permission until this phase. That is a verifiable privacy property of v1 and should be kept as long as possible.

---

## 5. Tracking providers (the abstraction the brief asks for)

```kotlin
// :tracking:api — pure JVM, no Android imports
enum class Capability { SESSION_BOUNDARIES, FOREGROUND_TIME, SCROLL_EVENTS, SCREEN_STATE }

enum class ProviderAvailability { GRANTED, NOT_GRANTED, UNSUPPORTED, RATE_LIMITED }

interface ScrollTrackingProvider {
    val id: ProviderId                      // USAGE_STATS | ACCESSIBILITY | MANUAL
    val capabilities: Set<Capability>
    val precision: Precision

    suspend fun availability(): ProviderAvailability
    suspend fun collect(window: TimeWindow): IngestOutcome   // reads raw observations
    fun observeLiveSignals(): Flow<TrackingSignal>           // empty for Tier 1
}
```

Three rules that make this abstraction real rather than decorative:

1. **Providers emit observations; the engine interprets them.** A provider never decides what a "scroll" is, never writes to the DB, and never touches `daily_summary`. So swapping implementations cannot change counting semantics.
2. **Capabilities drive the UI.** A screen asks the registry what is available and degrades explicitly. No screen may assume Tier 2.
3. **Selection is a resolved policy, not an `if`.** `ProviderResolver` picks the active provider from availability + user preference + precision preference, and returns a `TrackingProfile(active, precision, freshnessWindow)`. The `store` flavour's accessibility provider reports `UNSUPPORTED`, so the same resolver code produces the correct profile in both flavours.

Registry: Hilt `@IntoSet` multibinding into `Set<ScrollTrackingProvider>`; no service-locator, no static instance.

**No mock data in production logic** is enforced structurally, not by discipline:
- Fakes live in `src/test` / `src/androidTest` / `src/debug` source sets only. `main` cannot reference them.
- There is **no** "demo data" code path in `main`. A debug-only `SeedDebugData` flag lives in `src/debug` and is excluded from release by the flavour/source-set boundary.
- Empty states are real: "No scrolls recorded yet — access granted today at 14:02".
- A CI gate (`grep -r 'Fake\|Mock\|sampleData' --include=*.kt app/src/main core feature tracking/*/src/main`) fails the build on hits.

---

## 6. Ingest & aggregation algorithm

This is the core logic of the product and the highest-risk area, so it is specified precisely and lives in the pure-JVM `:tracking:engine`.

```
1.  watermark = prefs.lastIngestCursorUtcMillis (or now-24h on first run)
2.  window    = [watermark - 2min overlap, now]        // overlap catches late events
3.  rawEvents = provider.collect(window)               // UsageEvents, filtered to trackedPackages
4.  events    = rawEvents.filter { capabilitySupported && package in trackedPackages }
5.  events   → INSERT OR IGNORE into usage_event        // unique index = idempotency
6.  sessions  = stitch(events)                          // RESUMED opens, PAUSED/STOPPED/screen-off/lock closes
7.  sessions  = splitAtMidnight(sessions, zoneId)       // per-minute attribution, not per-session
8.  sessions  = mergeAdjacent(sessions, gapMs <= 90s)   // activity churn inside one app
9.  sessions  = dropAnomalies(sessions)                 // end<=start, duration>18h, unclosed across BOOT
10. for each affected dateKey (not all history):
        recompute daily_total / daily_summary in ONE transaction   // incremental, not full rebuild
11. prefs.lastIngestCursorUtcMillis = now - 1min
12. write ingest_run row (eventsRead, sessionsWritten, outcome)
13. emit Freshness(lastEventAt, lagMs) to the UI
```

Edge cases the tests must cover (each one is a named unit test):

| Case | Handling |
|---|---|
| Two activities in one package churn resume/pause | `mergeAdjacent` with 90 s gap tolerance → one session |
| Screen off with no `ACTIVITY_PAUSED` | `SCREEN_NON_INTERACTIVE` / `KEYGUARD_SHOWN` closes the session |
| Session spans local midnight | split; each part attributed to its own `dateKey` |
| Session spans a timezone change | split at the zone transition; both `zoneId`s recorded |
| Device shutdown mid-session | `DEVICE_SHUTDOWN` closes; unclosed opens across `DEVICE_STARTUP` dropped |
| DST spring-forward / fall-back | day length ≠ 24 h; `DateKey` arithmetic is zone-aware, tested in both directions |
| Overlap re-ingest of the same events | unique index makes it a no-op; asserted by an idempotency test |
| Duplicate events at identical timestamp | dedupe key includes `type`, so `RESUMED@t` and `PAUSED@t` both survive |
| Truncated "last few minutes" | watermark backs off 1–2 min; UI shows `Freshness.lagMs` |
| Event log rotated/gap (device off for days) | `confidence = LOW`, `ingest_run.outcome = GAP_DETECTED`, surfaced in diagnostics — never silently smoothed |
| Multiple users / work profile | provider reports per-user; v1 tracks the primary user only, documented limitation |
| Clock change / manual time set | sessions with absurd overlap are flagged; no negative durations |

**Live-feel without a foreground service:** reconcile on app resume + `WorkManager` periodic (15 min floor) + refresh whenever Home is on screen. A `specialUse` foreground service is *deferred* (Phase 6, optional) because it needs a Play declaration and a demo video for marginal benefit.

---

## 7. UI plan

### 7.1 Screens

| Screen | Contents | Notes |
|---|---|---|
| **Onboarding** (3 steps) | What is measured and what is *not*; how to grant Usage Access (deep link `ACTION_USAGE_ACCESS_SETTINGS`, verified via `AppOpsManager` on return); optional Tier 2 explainer (personal flavour only); privacy statement | Staged, skippable, resumable; never blocks on a permission |
| **Home** | `DrifterCanvas`, today's effective count vs. limit, progress arc, band label, `Freshness` ("Updated 2 min ago"), per-app strip, `≈` badge if estimated | Single primary action: "Set a limit" when none exists |
| **Stats** | Day / Week / History tabs; bar chart per day, calendar heat map, per-app breakdown, range selector, "counted vs estimated" legend | Custom Canvas charts — no third-party chart lib |
| **Limits** | Daily limit (units or duration), alert thresholds, live preview of the mascot at 50/80/100 %, limit history read-back | Writes `UserPreferences`; snapshots into `limitUnitsAtTime` |
| **Settings** | Tracked apps picker, precision controls, `secondsPerScroll` overrides, notifications, theme, diagnostics (`ingest_run` list + provider status), export CSV, delete all data | Export/delete is a Play requirement *and* a trust feature |
| **Diagnostics** | Provider availability, last ingest outcome, event counts, gap warnings | The honesty screen — cheap to build, saves hours of support |

### 7.2 The Drifter (mascot), specified as a pure function

The visual is **not** an animation framework problem; it is a state-derivation problem, so it is fully unit-testable without Compose:

```kotlin
// :tracking:engine — pure Kotlin
data class MascotVisualState(
    val band: ActivityBand,        // CLEAR, SWELL, UNDERTOW, SUBMERGED, NO_LIMIT
    val fillFraction: Float,       // 0f..1f, clamped
    val eyeState: EyeState,        // OPEN, HALF, CLOSED, X
    val bobPhaseSeconds: Float,
    val bubbleCount: Int,
    val accentRole: AccentRole,
)

fun resolveMascotState(effectiveUnits: Int, limitUnits: Int?, precision: Precision): MascotVisualState
```

- `DrifterCanvas` is a thin `@Composable` that only *renders* that state, with `animateFloatAsState` on `fillFraction`.
- Original geometry: hex-outline cortical silhouette + six interior folds, drawn as strokes from a small path spec; **no** assets borrowed or traced, no bitmaps (crisp at any density, tiny APK).
- Accessibility: `contentDescription` states the band in words; `LocalMotionOverride` honours reduced-motion by freezing the bob; band is conveyed by shape + label, never colour alone.
- Dark-first, light theme derived from the same tokens.

### 7.3 Design system rules
- All colour/type/space from `UndertowTheme` tokens — no literal `Color(0xFF…)` in feature modules.
- Tabular numerals everywhere a count appears.
- `@Preview` for every screen against synthetic `UiState` fixtures in `src/debug` (fixtures are UI-test data, not product data).

---

## 8. Dependency injection (Hilt)

**Verified constraint:** Hilt's Gradle plugin now requires **AGP 9**, and Hilt/Room run through **KSP** (kapt is incompatible with AGP 9's built-in Kotlin). AGP 9 also **removes** the need for `org.jetbrains.kotlin.android`.

| Module | Provides |
|---|---|
| `AppModule` | `@IoDispatcher`, `@DefaultDispatcher`, `@ApplicationScope`, `AppClock` (wraps `Clock.systemDefaultZone()`) |
| `DatabaseModule` | `UndertowDatabase`, each DAO; `@Singleton` |
| `DataStoreModule` | `DataStore<Preferences>`, `UserPreferencesRepository` |
| `TrackingModule` | `@Binds` each provider; `@IntoSet` registry; `ProviderResolver`; `ScrollAggregator` |
| `RepositoryModule` | `@Binds` repo interfaces → impls |

Scopes: `@Singleton` for DB/prefs/repos/engine; `@HiltViewModel` for ViewModels; nothing else.
Testing: `@TestInstallIn` to swap dispatchers/clock/providers; `@UninstallModules` where needed; `HiltTestApplication` + `runTest`.
Rule: no `@Inject` in `:core:model` or `:tracking:engine` (they stay framework-free); their wiring happens in `:core:data`.

---

## 9. Manifest, permissions, and Play compliance

### 9.1 Permissions actually needed (v1, `store` flavour)

| Declaration | Type | Why | Friction |
|---|---|---|---|
| `android.permission.PACKAGE_USAGE_STATS` (`tools:ignore="ProtectedPermissions"`) | Special app-op | The entire Tier-1 measurement engine | User must grant in Settings manually; **no runtime dialog exists**; verified via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)` on `ON_RESUME` (there is no result callback) |
| `<queries>` for `com.instagram.android`, `com.instagram.lite`… `com.google.android.youtube`, `com.facebook.katana`, `com.facebook.lite`, `com.snapchat.android` | Package visibility | Resolve labels/icons for tracked apps | **Never** `QUERY_ALL_PACKAGES` — it needs a Play declaration and a "core purpose" argument this app cannot honestly make |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Limit alerts | Declined → alerts silently become in-app only; must handle |
| *not* `INTERNET` | — | Local-first, verifiable | Deliberate omission; added only at Phase 7 |
| *not* `FOREGROUND_SERVICE` | — | Deferred | Only if Phase 6 live service is approved |
| `BIND_ACCESSIBILITY_SERVICE` | `personal` flavour only | Tier 2 counting | Play-restricted; `canRetrieveWindowContent="false"`, `packageNames` allowlist, `typeViewScrolled` only |

### 9.2 Play policy work (Phase 6, schedule it early)

- **Accessibility declaration** — only if Tier 2 ships to Play. Policy lists "monitoring apps" as non-accessibility-tools, so plan for possible rejection; the `store` flavour is the fallback and is already compliant.
- **Data safety form** — declare App activity (inferred usage) as collected *on-device*; contradiction between marketing and declaration is what gets apps in trouble, so the form must match reality exactly.
- **Foreground service declaration** — only if Phase 6 FGS is approved (type `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` + demo video).
- **targetSdk 36** required for new apps/updates since 31 Aug 2026; `compileSdk 37`; **bump targetSdk to 37 before Aug 2027.**
- Account/data deletion path (export + delete-all) must exist in-app.

---

## 10. Dependencies (version catalog)

Pinned from current documentation; **every version must be re-validated by an actual build in Phase 0** — treat this table as the starting hypothesis, not gospel.

| Dependency | Version | Module(s) |
|---|---|---|
| AGP | `9.5.0` (Gradle `9.6.0`+ ; JDK **17**) | build |
| Kotlin | via AGP built-in (catalog `kotlin` only pins the Compose compiler plugin) | build |
| KSP | `2.3.13` (2.3.10+ required for AGP 9) | build |
| Compose BOM | `2026.09.00` (Compose 1.12.x, requires compileSdk 37) | all UI |
| Material3 | `1.4.0` (via BOM) | designsystem, features |
| activity-compose | `1.13.0` | app, features |
| lifecycle-runtime-compose / viewmodel-compose | `2.11.0` | features |
| navigation-compose | `2.10.2` (type-safe routes) | app |
| core-ktx | `1.19.1` | android modules |
| Hilt | `2.60.1` + `androidx.hilt:hilt-compiler 1.4.0`, hilt-navigation-compose, hilt-work | app, data |
| Room | `2.8.5` (+ `room-testing`) | database |
| DataStore Preferences | `1.3.0` | datastore |
| WorkManager | `2.12.0` | usagestats, data |
| kotlinx-coroutines | `1.11.0` | everywhere |
| kotlinx-serialization | `1.12.0` (plugin version = Kotlin) | model (export/settings blobs) |
| JUnit4, Truth, **Turbine `1.2.1`**, MockK | current | unit tests |
| Robolectric `4.17` | SDK 37 support | android unit tests |
| Compose ui-test-junit4, Hilt testing | via BOM / Hilt | instrumented |
| Detekt (optional), Konsist (optional) | current | static gates |

**Deliberately excluded:** chart libraries (custom Canvas), analytics/crash SDKs (no INTERNET in v1), kapt, ksp-based Hilt alternatives, any network stack, `QUERY_ALL_PACKAGES`, and `androidx.room` "Room3" (separate API line; unnecessary churn).

**Build-config gotchas already identified** (so Phase 0 doesn't stall): no `org.jetbrains.kotlin.android` anywhere (root, module, or catalog); `kotlin { compilerOptions { } }` replaces `kotlinOptions`; `namespace` distinct per module; set `targetSdk` explicitly; run the daemon on JDK 17, not the installed JDK 25; don't `@Parcelize` Room entities.

---

## 11. Testing strategy

| Layer | Tools | What is proven |
|---|---|---|
| `:tracking:engine`, `:core:model`, `:core:common` | JUnit4 + `kotlin.test`, **no Android** | Stitching, midnight/DST splitting, dedupe idempotency, anomaly rejection, band resolution, estimation policy — the 14 edge cases in §6 |
| `:core:database` | Robolectric + `MigrationTestHelper` | DAO queries, unique-index behaviour, migrations from every shipped schema version |
| `:core:datastore` | Robolectric, temp files | Defaults, partial updates, corrupt-file recovery |
| `:tracking:usagestats` | Robolectric shadows + a fake `UsageEventsReader` seam | Event mapping, permission-state detection, watermark advance |
| ViewModels | Turbine + `TestDispatcher` + fake repos | State sequences, one-shot effects, error/empty paths |
| Compose UI | `createComposeRule`, semantics assertions | Key states render correct copy and badges; mascot band labels |
| Cross-module | Konsist/grep gate | No `androidx.room`/`datastore`/`android.provider` imports in `:feature:*`; no fake/demo strings in `main` source sets |
| E2E | Manual + (optional) `adb shell cmd usagestats` seeding on a real device | Real-world accuracy; emulators have no genuine usage data — this must be a device test |

Target: engine ≥ 90 % line coverage (it is pure logic and cheap to test); features lighter, asserting behaviour not implementation.

---

## 12. Phased roadmap

Estimates are focused solo-dev days, deliberately ranged.

| Phase | Deliverable | Exit criteria |
|---|---|---|
| **0. Toolchain & skeleton** (~1–2 d) | Install SDK (`platforms;android-37.0`, `build-tools;37.0.0`); `git init`; `.gitignore`; version catalog; 16 modules; Hilt app; blank Home | `./gradlew assembleDebug lint` green; app launches with an empty state, no fake data |
| **1. Foundations** (~3–4 d) | `:core:model`, `:core:common` (Clock), design tokens + theme, Room schema + migration test, DataStore repo, nav shell | Migration test passes; prefs persist across process death; tokens only, zero literal colours |
| **2. Tracking core, pure logic** (~4–6 d) | `:tracking:api` contracts, engine stitching/aggregation/`EstimationPolicy`, `ProviderResolver`, `ingest_run` logging — **TDD, no UI** | All §6 edge cases covered and green; idempotency proven |
| **3. Real data ingestion** (~3–4 d) | `UsageStatsScrollProvider`, onboarding permission flow, WorkManager ingest, watermark, backfill, diagnostics screen | On a real device, granting access yields a correct today-count that survives reboot; revoked access degrades cleanly |
| **4. Product UI** (~6–8 d) | Home + Drifter, Stats (day/week/history + charts), Limits + alerts, Settings, export/delete | Progress bar matches DB; alert fires once per threshold per day; export round-trips |
| **5. Precision tier (optional)** (~3–4 d) | `personal` flavour + `ScrollAccessibilityProvider`; capability-driven badges | Counted vs estimated is unambiguous in every screen; `store` build contains no a11y service in its merged manifest |
| **6. Polish & release** (~4–6 d) | Mascot motion, a11y pass, light theme, perf/baseline profile, copy review, Play internal track, data-safety form, signing | TalkBack pass; cold start < 1 s on a mid-range device; internal track install with correct declarations |
| **7. Backend (future)** (~8–12 d) | Firebase Auth + Firestore behind `SyncRepository`, outbox, server-authoritative count claims, App Check | Two-account sync; offline edits converge; spoofed client counts rejected server-side |
| **8. Social** (~8–12 d) | Friends, weekly leaderboards, scroll battles (lower wins), home-screen widget | Battles resolve server-side; no client-trusted scores |

**Critical path:** 0 → 1 → 2 → 3 → 4. Phase 2 before Phase 3 is deliberate: the counting logic is the risky part and is cheapest to get right with no Android in the loop.

---

## 13. Technical limitations (read this before approving)

**Blocking / scope-shaping**
1. **Exact reel counts are impossible without an AccessibilityService.** Public APIs give package-level foreground time only. If Tier 2 is off the table, the product must be framed as *time in feed + estimated scrolls*, and the mascot must be driven by time, not by a scroll count.
2. **No real-time counter exists.** The platform truncates the last few minutes of the event log; every "live" number lags by minutes. Any UI implying otherwise would be dishonest.
3. **No retroactive history.** Only a few days of events are retained; data before install is unrecoverable. Gaps after long offline periods are permanent.
4. **Play approval is not guaranteed for Tier 2.** "Monitoring apps" are explicitly excluded from the accessibility-tool exemption.
5. **Reels cannot be separated from the feed** inside Instagram/YouTube/Facebook/Snapchat — same Activity. Report at app level.
6. **Browser/mobile-web consumption is invisible** (instagram.com in Chrome, YouTube in a browser).
7. **Local build environment (resolved, with one gap):** the SDK is installed at `~/Library/Android/sdk` (`platforms;android-37.0`, `build-tools;37.0.0`) and `assembleDebug` plus `lint` are green on JDK 17. The gap is a device: `adb devices` is empty, no AVD exists and the emulator package is not installed, so **nothing requiring instrumentation can be verified on this machine** — Room `MigrationTestHelper`, DataStore process-death tests, Compose UI tests and real `UsageStats` data all need a physical device or an AVD image. Free space is also low (~4 GiB), so an emulator image needs space freed first.

**Operational**
8. WorkManager's 15-minute floor + Doze/OEM battery managers → tracking is batched, not continuous; some OEMs will starve background work entirely. Mitigate with resume-time reconciliation and an honest diagnostics screen.
9. Package visibility: regional/`Lite` app variants must be in the tracked-package config, user-editable; `QUERY_ALL_PACKAGES` is not an option.
10. Multi-user / work profiles: UsageStats is per-Android-user; v1 covers the primary user only.
11. Clock/travel/DST: handled by design (zone recorded per session) but it is real complexity, not an afterthought.
12. Emulators produce no genuine usage data → accuracy tuning needs a physical device.
13. Firebase leaderboards cannot trust client counts; server validation is mandatory, which means Cloud Functions (a cost + a deploy surface), not pure Firestore rules.

**Product/ethical**
14. No medical framing; the mascot is a metaphor and onboarding must say so.
15. Play Data safety declaration must match actual on-device behaviour (the reference app's own listing is arguably inconsistent here — explicitly avoid that trap).
16. Shipping `INTERNET` in v1 would weaken the local-first claim, so social features remain blocked on Phase 7.

---

## 14. Open decisions needed before any code

| # | Decision | Recommendation |
|---|---|---|
| **D1** | **Tier 2 strategy** — accessibility provider or not? | Build it, but **`personal` flavour only**; ship `store` with Tier 1 and let the UI be capability-driven |
| **D2** | **Primary metric** — time, or scroll units? | **Time in feed** as the measured truth; "≈ scrolls" as a labelled estimate; counted units when Tier 2 is on |
| **D3** | **Distribution** — Play Store, or personal/sideload? | Both, via the two-flavour split above |
| **D4** | **minSdk** | **26** (native `java.time`, no desugaring). Use 24 only if reach outweighs build complexity |
| **D5** | **Foreground service / home-screen widget in v1?** | **No** — defer to Phase 6; avoid the Play FGS declaration for v1 |
| **D6** | **Module count** | Start with all 16; merging `onboarding`+`settings` is fine if you prefer 15 |
| **D7** | **App name + applicationId** | *Undertow* + a domain you own (placeholder `app.undertow.tracker` is not publishable) |
| **D8** | **Local SDK setup** | Approval needed to free ~12 GB and install the Android SDK — without it, Phase 0 cannot begin and nothing can be compiled or verified |

---

## 15. Recommended approval scope

Approve **Phases 0–2** first. Phases 0–2 produce a compiling, tested, UI-less skeleton plus the entire counting engine under test — the two things that are expensive to get wrong later. Phase 3+ (real device ingestion) can be approved once you have seen the engine's test suite and the mascot's state model is agreed.
