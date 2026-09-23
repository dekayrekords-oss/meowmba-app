# Meow Flow + Meow's Desk — wiring guide for the integrator

Branch `feature/meow-flow-core` (worktree `/Users/prog/AndroidStudioProjects/Meowmba-flow-20260922`)
contains the engine, UI and tests, **not wired into the app**. Everything below touches
integrator-owned files (BUILD_MASTER / master plan §12), so Claude did not edit them.
Estimated integrator diff: ~120 lines across 6 files.

What's on the branch (all `com.example.meowmba`):

| Area | Files |
|---|---|
| Engine | `core/flow/FlowModel.kt`, `FlowPolicy.kt`, `FlowController.kt`, `FlowSettings.kt`, `FlowThemes.kt`, `SituationBuilder.kt`, `WorkStateDetector.kt`, `IntentionCapture.kt`, `FlowStores.kt`, `FlowRuntime.kt` |
| Decisions | `core/flow/decide/DecisionEngine.kt` (typed questions, Laya/Jev wire shape), `Assessors.kt` (model/rules chain) |
| Delivery | `core/flow/delivery/InterruptDelivery.kt` (speech gate, heads-up notification, action receiver) |
| Context | `core/flow/sources/ContextSources.kt` (evidence mappers, SituationAssembler), `CalendarSource.kt`, `UsageSource.kt` |
| Storage | `core/flow/db/FlowRoom.kt` (entities, DAO, RoomIntentionStore, migration statements) |
| UI | `ui/flow/MeowFlowHome.kt`, `FlowAdjustSheet.kt`, `FlowTokens.kt`, `FlowSamples.kt`; `ui/desk/DeskModel.kt`, `DeskPainter.kt`, `DeskHabitat.kt` |
| Tests | `app/src/test/java/com/example/meowmba/flow/*` + `resources/flow/cases/policy_cases_v0.json` |

## 1. Enums (AppAppearance.kt, AvatarStyle.kt)

```kotlin
// AppAppearance.kt — Meow Flow sits beside Professional (owner D11)
enum class AppTheme(val key: String, val label: String) {
    COMPANION("companion", "Companion"),
    PROFESSIONAL("professional", "Professional"),
    FLOW("flow", "Meow Flow");
    ...
}

// AvatarStyle.kt — Meow's Desk is a separate avatar (owner D2); isLumen stays false
DESK(
    key = "desk",
    label = "Meow's Desk",
    description = "A little office: your project, messages, money and next thing, with a micro cat.",
),
```

## 2. Runtime ownership (ViewModel hook file, e.g. `ui/companion/FlowHooks.kt`)

Keep `CompanionViewModel.kt` edits tiny by holding the runtime in a hook, like
`CompanionAgenticHooks.kt`:

```kotlin
val flow = FlowRuntime(
    context = app,
    scope = viewModelScope,
    speaker = SynthesizerSpeaker(tts) { !isListening && !isSpeaking },   // existing speech gates
    store = RoomIntentionStore(db.flowDao(), viewModelScope),            // after §4
    calendar = CalendarSource(app)::upcoming,                             // core/flow/sources/CalendarSource.kt
    surface = { if (appInForeground && appTheme == AppTheme.FLOW) Surface.FLOW_ON_SCREEN
                else if (appInForeground && avatarStyle == AvatarStyle.DESK) Surface.DESK_ON_SCREEN
                else Surface.OTHER_APP },
    workContext = { WorkContext(minutesSinceWorkApp = …, activelyWorking = …, atWorkPlace = …, computerClosed = desktopBridgeIdle) },
    laya = null,  // Phase 2: ModelAssessor(LayaOnDeviceEngine(...)) once S1/S2 pass
    jev = null,   // Phase 8, opt-in only
)
// start when Flow or Desk is selected, or when interruptions should work in the background:
flow.start()
```

Evidence (each is one line where the event already arrives):

```kotlin
drivingDetector.driving.collect { flow.publish(EvidenceMappers.fromDriving(it, now())) }
// RoadAlertListener.onNotificationPosted (generalise; do NOT add a second listener):
EvidenceMappers.fromNotification(sbn.packageName, label, sbn.notification.category,
    sbn.notification.extras.getString(Notification.EXTRA_TITLE), sbn.postTime, sbn.isOngoing)?.let(flow::publish)
// usage access (owner D10): UsageSource(app) { workApps }.foregroundEvidence(lastCheck, now).forEach(flow::publish)
//   on screen-on/unlock/Flow opened — never a polling loop; workContext = { usageSource.workContext(now()).copy(atWorkPlace = …, computerClosed = …) }
// geofences (background location, owner D10): EvidenceMappers.fromPlace(name, entering, now)
// chat turns: IntentionCapture(engine, lookup).capture(text, now)?.let { flow.store.upsert(it); flow.publish(EvidenceMappers.fromCapturedNeed(it.text, now)) }
```

## 3. Screen (CompanionScreen.kt, next to the PROFESSIONAL branch ~L158)

```kotlin
} else if (uiState.appTheme == AppTheme.FLOW) {
    val snap by viewModel.flow.snapshot.collectAsState()
    val settings by viewModel.flow.settings.collectAsState()
    var adjust by remember { mutableStateOf(false) }
    MeowFlowHome(
        snapshot = snap, settings = settings, dateLabel = flowDateLabel(snap.at),
        distanceRemainingMeters = viewModel.flowDistanceMeters(),   // from route/location, null if none
        speaking = uiState.isSpeaking,
        onPrimary = { scope.launch { viewModel.flow.onPrimary(it.intentionId) } },
        onLater = { scope.launch { viewModel.flow.onLater(it.intentionId) } },
        onInterruptAccept = { scope.launch { viewModel.flow.onPrimary(it.item.intentionId) } },
        onInterruptDecline = { scope.launch { viewModel.flow.onLater(it.item.intentionId) } },
        onAdjust = { adjust = true }, onAsk = viewModel::openChat, onMic = viewModel::onMicClick,
    )
    if (adjust) ModalBottomSheet(onDismissRequest = { adjust = false }) {
        FlowAdjustSheet(settings, learnedHoursText(viewModel.flow.work.history()),
            night = snap.evening, onChange = viewModel.flow::updateSettings, onDone = { adjust = false })
    }
}
```

Desk (inside `AvatarMotionHost` where styles are chosen):

```kotlin
AvatarStyle.DESK -> DeskHabitat(
    state = DeskState.from(viewModel.flow.snapshot.collectAsState().value, deskFacts()),
    thermalStatus = uiState.thermalStatus, fpsTarget = uiState.avatarFpsTarget,
    lineFor = { deskLine(it) }, onArrive = { obj -> viewModel.openDeskSheet(obj) },
)
```
`deskFacts()` fills project/build/weather from existing Needle/Play status, build info and
`WeatherRepository`. Hide the Care control while `AppTheme.FLOW` or `AvatarStyle.DESK` is active
(owner D1/D2) without changing the saved `careVisible` preference.

## 4. Room v11 (MeowmbaDatabase.kt)

```kotlin
entities = [..., FlowIntentionEntity::class, FlowOutcomeEntity::class], version = 11
abstract fun flowDao(): FlowDao
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_10_11_STATEMENTS.forEach(db::execSQL)
}
```
Export the v11 schema; add a MigrationTestHelper instrumented test (pattern: `SavedPlaceMigrationTest.kt`).

## 5. Manifest

```xml
<receiver android:name=".core.flow.delivery.FlowActionReceiver" android:exported="false" />
<!-- owner-approved D10, request at first use with plain-language rationale: -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
```
Play Console: background-location declaration + video; update DATA_SAFETY inventory.

## 6. Fonts (needs owner OK — third-party files)

Bundle Source Serif 4 (400/400i/600), Archivo (400–800) and Patrick Hand (all OFL) under
`res/font/`, then set `FlowFonts.serif/sans` and `DeskPainter.hand` at startup. Until then
the UI uses system serif/sans/cursive (see screenshots in `app/build/flow-shots/`).

## 7. Verification the integrator should run

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.meowmba.flow.*'
```
Compare `app/build/flow-shots/*.png` with `docs/design/screens/*.png`. Then the usual
full suite, lint, build and the BUILD_MASTER 10-minute heat run with Flow and with Desk active.
