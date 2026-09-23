# Master plan — Meow Flow, Laya decision engine and Meow's Desk

Status: **plan, not implemented.** Written 2026-09-22 by Claude for Codex (integrator)
to take over at any phase. Nothing here changes the build authority in
`BUILD_MASTER.md`: Codex remains the single integrator, build owner and installer.
Workers never build or install. No Android source goes to public GitHub (AGENTS.md).

Companion documents (read in this order):
1. `docs/MEOW_FLOW_PASS_DOWN.md` — product behaviour, screen anatomy, settings, decision pipeline.
2. `docs/PRO_DESK_AVATAR_PASS_DOWN.md` — Meow's Desk scene and micro cat.
3. `docs/design/meow-flow.html`, `docs/design/pro-desk-avatar.html` — interactive references (open in a browser).
4. Codex catalogue: `~/.codex/.chatgpt-projects/g-p-6a92436f93688191b6e16e2b2f9bbef5/work/meow-flow-situations.md` (600 situations) and `meow-flow-three-directions.md` (intention model).

---

## 0. Owner decisions already made (do not re-litigate)

| # | Decision | Date |
|---|---|---|
| D1 | **Meow Flow** is a selectable home mode: no avatar, no Care mode, paper/ink visual language, flow layout exactly as the demo. | 09-22 |
| D2 | **Meow's Desk** (illustrated office + micro cat) is kept as a **separate selectable avatar**. No Care control. | 09-22 |
| D3 | Interruptions are **spoken and shown**, and appear as a **system notification when the user is in another app**. Voice and notifications are **individually switchable**. | 09-22 |
| D4 | The user controls interruptions: **how many per day, how often, how urgent**. | 09-22 |
| D5 | Work schedule is **learned**, but **context decides** when work is over. | 09-22 |
| D6 | Meow handles **life as a whole**: intentions come from what the user says and does; no required lists/files. | 09-22 |
| D7 | Colours are **user-changeable** (6 themes + custom accent + after-work look). | 09-22 |
| D8 | Decision engine: **Laya** (open, Apache-2.0) **on the phone** via ONNX Runtime (Codex recommendation, owner-accepted). **No Meow Server fallback** (owner, 09-22). **Jev** optional, off by default. Fallback when Laya is unavailable = rules-only. | 09-22 |
| D10 | Background location **approved**; notification access reuse and usage access **approved**. | 09-22 |
| D11 | Meow Flow **sits beside** Professional mode; it does not replace it. | 09-22 |
| D12 | Store stock: **no stock sources in v1.** Meow never claims an item is in stock; it says a shop "usually carries" it and offers to call or check the store's page. Revisit only if the owner asks. | 09-22 |
| D9 | Work first; leisure suggestions only after work is wrapped. Items are offers; nothing executes without the user. | 09-22 |

---

## 1. Architecture — one engine, two faces

```
            ┌──────────────── evidence (events, never polling) ────────────────┐
            │ location/geofence · DrivingDetector · notifications · foreground │
            │ app · calendar · user speech/chat · media session · desktop bridge│
            └──────────────────────────────┬───────────────────────────────────┘
                                           ▼
   core/flow/EvidenceBus ──► core/flow/IntentionStore (Room) ──► core/flow/SituationBuilder
                                                                        │ compact state (≤ ~400 tokens)
                                                                        ▼
                                  core/flow/decide/DecisionEngine  (interface)
                                   ├─ LayaOnDeviceEngine  (ONNX Runtime, default)
                                   ├─ JevEngine           (TypeSafe cloud, opt-in)
                                   └─ RulesOnlyEngine     (always available, last resort)
                                                                        │ typed answers + probabilities
                                                                        ▼
                         core/flow/FlowPolicy (pure Kotlin: user settings, caps, facts, feasibility)
                                                                        │ FlowSnapshot
                         ┌──────────────────────────────┬───────────────┴──────────────┐
                         ▼                              ▼                              ▼
              ui/flow/MeowFlowHome           ui/desk/DeskHabitat (avatar)    core/flow/InterruptDelivery
              (AppTheme.FLOW)                (AvatarStyle.DESK)              band · TTS · notification
                                                                        │
                                                    outcomes ──► core/flow/OutcomeLog ──► calibration/learning
```

Key rule: **the Desk is a second presentation of the same `FlowSnapshot`.** Desk
objects (letter tray, jar, corkboard, monitor, sticky note, lamp, window) render
the same intentions Meow Flow lists. Build the engine once.

Pure-Kotlin, JVM-testable core (`core/flow/**` except the ONNX adapter and Android
listeners) — follow the repo convention that tests are pure JVM where possible.

---

## 2. Codebase anchors (verified 2026-09-22 on `recovery/known-good-138` @ 637697f)

| Concern | Existing code | Plan |
|---|---|---|
| Home mode switch | `ui/companion/AppAppearance.kt` `enum AppTheme { COMPANION, PROFESSIONAL }`; `CompanionScreen.kt` ~L158 routes `PROFESSIONAL` → `ProfessionalWorkspace` | Add `AppTheme.FLOW("flow","Meow Flow")` → `MeowFlowHome`. Keep PROFESSIONAL untouched. |
| Avatar selection | `ui/companion/AvatarStyle.kt` enum (LUMEN, FULL_FLY, LUMEN_CAT, PEARL, SIGNAL, NEO, CLASSIC); `fromKey` falls back safely | Add `DESK("desk","Meow's Desk", …)`. `isLumen` stays false for DESK. |
| Care control | `HomeControlSettings.kt` `features.careVisible` | Force hidden (not persisted-changed) while `AppTheme.FLOW` or `AvatarStyle.DESK` is active. |
| Frame pacing / heat | `AvatarFrameCadence.kt`, `SceneClock.kt`, `IdlePerformancePolicy.kt`, `core/thermal` | Desk cat and Flow leader-line pulse must use these; no independent loops. Note: idle-heat changes in `/Users/prog/AndroidStudioProjects/Meowmba-perf-20260922` (SharedFramePhase) are uncommitted and awaiting integrator review. |
| Eyes | `LumenEyeMaterials.kt` (mint #4DFF85 lens) | Desk cat eyes reuse this material. |
| Driving | `core/car/DrivingDetector.kt` | Source of `activity=driving`, speed, car connection. |
| Notifications in | `core/alerts/RoadAlertListener.kt` is the only `NotificationListenerService` | Generalise to dispatch to both `RoadAlertBus` and `flow/EvidenceBus`. **Do not add a second listener** (user would need a second grant). |
| Notifications out | `core/notification/NotificationHelper.kt` channel `meowmba_companion` | Add channel `meow_flow_interrupts` (importance HIGH, user-controllable) and `meow_flow_quiet` (LOW). |
| Voice out | `core/audio/TextToSpeechManager.kt` `speak(text)` | Interruption voice path; respect `MicOwner`/speech session gates so Meow never talks over the user. |
| Calendar | `core/agent/tools/CalendarTools.kt` (`CalendarContract`), permission already declared | Reuse queries; add read-only upcoming-events provider for flow. |
| Location | `ACCESS_FINE/COARSE_LOCATION` declared; `core/nav/SavedPlaceStore.kt` | Geofences need Play Services location + **background location** (new, Play policy review). |
| Screen context | `core/context/ContextDirector.kt` (screen watch → ContextEvent) | Optional evidence source; off by default. |
| Room | `core/database/MeowmbaDatabase.kt` **v10**, schemas exported | v11 migration adds flow tables (§5). Never destructive. |
| ONNX Runtime | `app/src/main/jniLibs/arm64-v8a/libonnxruntime.so` **1.27.0** (native only, shipped with sherpa-onnx v1.13.4, gitignored, fetched by `scripts/fetch_kws_assets.sh`, checked by `verifyVoiceReleaseConfiguration`) | See §6.2 — share one runtime, don't ship two. |
| Model downloads | `core/download/DownloadService` + `ModelDownloadViewModel` | Laya weights (~524 MB Q8) downloaded on demand, never in the APK/AAB. |
| Other ML | MediaPipe tasks, LiteRT-LM (`libs.litert.lm.android`) | Unaffected; LiteRT is plan B for Laya (§6.5). |
| Settings screen | `SettingsScreen.kt`, `SettingsCategory.kt` | New "Meow Flow" category (mirrors the in-mode Adjust sheet). |

---

## 3. Phases and gates

Each phase ends in a **gate** the integrator must pass before the next depends on
it. Phases 1 and 7 (Desk) can run in parallel after Phase 0.

| Phase | Name | Output | Gate |
|---|---|---|---|
| 0 | Spikes & decisions | ORT sharing proof, Laya I/O contract, eval set v0 | §4 checklist all answered |
| 1 | Flow core (no UI) | intentions, evidence bus, settings, policy, rules engine | JVM tests green; no behaviour change in existing modes |
| 2 | Laya on device | `LayaOnDeviceEngine`, model download, golden parity | parity + budget gate (§6.6) |
| 3 | Meow Flow home UI | `AppTheme.FLOW` screen matching demo | screenshot/UI tests; heat unchanged when idle |
| 4 | Interruptions | band, TTS, notification delivery + settings gate | device acceptance script (§9.3) |
| 5 | Context sources | geofences, foreground app, notification evidence, calendar, speech intentions, work-end | permission flows + privacy review |
| 6 | Learning & eval | outcome log, calibration, 600-situation replay | eval thresholds (§8) |
| 7 | Meow's Desk avatar | `AvatarStyle.DESK` scene bound to FlowSnapshot | render/heat gate, objects reflect snapshot |
| 8 | Jev escalation (optional) | opt-in `JevEngine` | offline/online matrix |
| 9 | Release | data-safety + Play declarations, manual, receipt | BUILD_MASTER release checklist |

---

## 4. Phase 0 — spikes and decisions (do first, small, throwaway code allowed)

**S1. ONNX Runtime sharing.** The app already packages ORT **1.27.0** native for
sherpa-onnx. The Java API (`com.microsoft.onnxruntime:onnxruntime-android`) also
packages `libonnxruntime.so` + `libonnxruntime4j_jni.so`. Two copies with the same
name cannot both ship.
- Try A: add `onnxruntime-android:1.27.0` (exact match — verify the artifact exists),
  `packaging.jniLibs.pickFirsts += "**/libonnxruntime.so"`, determine which copy wins,
  and run the voice regression (KWS wake, VAD, Sherpa ASR, speaker verify) on the Fold.
- Try B (if A's copy lacks ops sherpa needs, or the versions can't match): keep
  sherpa's `libonnxruntime.so`, ship **only** `libonnxruntime4j_jni.so` from the 1.27.0 AAR.
- Either way: record hashes; the release "149 protected runtime/model entries"
  check will flag a changed `libonnxruntime.so` — only accept with explicit note in
  the receipt. Update `verifyVoiceReleaseConfiguration`/`check_release_bundle.py`
  expectations if the JNI file is added.
- Confirm the ORT build includes the quantized ops the Q8 model uses
  (`DynamicQuantizeLinear`, `MatMulInteger`/`QLinearMatMul` — inspect the graph).

**S2. Laya model contract.** Download (owner permission required — third-party
files) `nvkudva/laya-web-q8` (English, Q8, ~524 MB) and
`mizchi/laya-multilingual-onnx` (322M, FP16). Both are **community conversions**:
- Record file list, SHA-256, licence (upstream Apache-2.0; confirm the conversion's).
- Record ONNX input/output names and shapes, opset, quantisation.
- From the upstream Python package (`pip install laya`, v0.3.x;
  github.com/NandhaKishorM/laya) extract **exactly** how `(state, questions)` is
  serialised into tokens and how `choice`/`score`/`noul` are decoded from outputs.
  The Kotlin port must reproduce this byte-for-byte; write it down as
  `docs/LAYA_IO_CONTRACT.md`.
- Tokenizer: ModernBERT BPE (English) / mmBERT (multilingual). Decide: pure-Kotlin
  tokenizer port (preferred; small, testable) vs. bundling a native tokenizer.
- Produce **golden fixtures**: 200 `(state, questions) → answers/probabilities`
  pairs from the Python reference using the *same* ONNX file, saved to
  `app/src/test/resources/flow/laya_golden/*.json`.

**S3. Device budget probe.** On the Fold (integrator only): load Q8 session, run
50 typical batches (4–7 questions, ~300-token state). Record p50/p95 latency, peak
RSS/PSS delta, session create time, battery temp delta, thermal status. This sets
the real budgets in §6.6 — nothing is claimed before this.

**S4. Eval set v0.** Convert 120 representative catalogue situations (≥ 2 per
area, **incl. ≥ 30 "stay quiet" cases**) into JSON cases (§8.1). Owner labels the
expected outcome; this is the first calibration/test split.

**S5. Owner confirmations** — answered 09-22, see §15 (D8, D10–D12). Record them in BUILD_MASTER when work starts.

Exit: S1 chosen path + voice regression pass, `LAYA_IO_CONTRACT.md`, golden
fixtures, S3 numbers, eval v0.

---

## 5. Phase 1 — Flow core (pure Kotlin, no UI)

Package: `com.example.meowmba.core.flow`. Worker lane **W-core**. No edits to
`CompanionViewModel.kt`/`CompanionState.kt` (integrator-owned).

### 5.1 Types

```kotlin
// core/flow/Model.kt
enum class Kind { WORK, MONEY, PEOPLE, HOME, ERRAND, LEISURE, HEALTH, OTHER }
enum class Urgency { NOT_NOW, USEFUL, WORTH_IT, URGENT }      // user gate uses USEFUL..URGENT
enum class Channel { SPEAK_AND_SHOW, SHOW_ONLY, NOTIFY, HOLD }
enum class Certainty { CONFIRMED, LIKELY, UNKNOWN }           // shown in copy ("in stock" only if CONFIRMED)

data class Intention(
    val id: String, val text: String,            // "Fix the rear brake light"
    val kind: Kind, val source: String,          // "said Sun 19:14: 'the rear light is out again'"
    val createdAt: Long, val dueAt: Long?, val placeHint: String?,
    val facts: Map<String, String>,              // e.g. part=7443, confirmed by lookup
    val status: Status,                          // OPEN, SNOOZED, DONE, DROPPED
)
data class Evidence(val type: String, val at: Long, val payload: Map<String, String>, val fresh: Boolean)
data class Situation(val now: Long, val activity: String, val place: String?,
    val foregroundApp: String?, val calendar: List<String>, val route: RouteFacts?,
    val recent: List<Evidence>, val workState: WorkState)
data class Judgment(val questionId: String, val answer: String, val probability: Double, val engine: String)
data class FlowItem(val intention: Intention, val headline: String, val because: String,
    val primary: Action, val secondary: Action?, val urgency: Urgency, val certainty: Certainty,
    val sources: List<String> /* dateline keys for leader lines */, val heldReason: String?)
data class FlowSnapshot(val dateline: List<Signal>, val main: FlowItem?, val more: List<FlowItem> /* ≤3 */,
    val interrupt: Interrupt?, val evening: Boolean, val judgments: List<Judgment>)
```

### 5.2 Components

| File | Responsibility |
|---|---|
| `EvidenceBus.kt` | `SharedFlow<Evidence>`; producers never block; coalesce bursts (≥ 2 s debounce per type). |
| `IntentionStore.kt` + Room DAO | CRUD, open-intentions query by kind/place/time, auto-close on satisfied evidence (catalogue #295, #600). |
| `SituationBuilder.kt` | Evidence + intentions → `Situation` and **compact state JSON** (hard cap; truncates oldest evidence first; never includes secrets, full message bodies, account numbers). |
| `FlowSettings.kt` | DataStore/SharedPreferences keys (§5.4) with defaults from the pass-down. |
| `FlowPolicy.kt` | Pure function `(situation, judgments, settings, counters) → FlowSnapshot`. Interrupt gate, ranking, held reasons, evening switch, max 1 main + 3 more. **Settings changes re-run policy, not inference.** |
| `RulesOnlyEngine.kt` | Deterministic fallback: deadlines ≤ 24 h, calendar ≤ 20 min, due bills ≤ 48 h; never interrupts for errands/leisure. |
| `WorkStateDetector.kt` | Learned schedule prior (per weekday start/end histogram) + context evidence; outputs `WORKING / WRAPPING / OFF` with probability. Context overrides the prior (D5). |
| `OutcomeLog.kt` | Records shown/acted/dismissed/too-late/too-early/requested-later per item for learning. |
| `FlowController.kt` | Orchestrates: evidence → (debounced) decide → snapshot `StateFlow<FlowSnapshot>`. Single coroutine scope; paused when not needed (§6.6). |

### 5.3 Room v11 (migration 10→11, additive)

```
flow_intentions(id TEXT PK, text, kind, source, created_at, due_at, place_hint, facts_json, status, updated_at)
flow_outcomes(id INTEGER PK AUTOINCREMENT, intention_id, shown_at, channel, urgency, outcome, latency_ms, situation_hash)
flow_work_days(day TEXT PK, start_min INTEGER, end_min INTEGER, evidence_json)
```
Export schema; add a migration test — no destructive fallback.

### 5.4 Settings keys (prefs `meow_flow`)

| Key | Type | Default |
|---|---|---|
| `flow.voice` | bool | true |
| `flow.notify_other_apps` | bool | true |
| `flow.max_per_day` | int 0–10 | 3 |
| `flow.min_gap_min` | 15/30/60/120 | 60 |
| `flow.min_urgency` | USEFUL/WORTH_IT/URGENT | WORTH_IT |
| `flow.detect_work_end` | bool | true |
| `flow.theme` | paper/linen/stone/sand/white/graphite | paper |
| `flow.accent` | #RRGGBB or empty | empty |
| `flow.evening_look` | auto/day/night | auto |
| `flow.allow_jev` | bool | false |

### 5.5 Phase 1 tests (JVM)

`FlowPolicyTest` (gate: level, cap, gap, channel toggles, held reasons text),
`SituationBuilderTest` (token cap, redaction), `WorkStateDetectorTest` (late-work
override, multi-signal wrap), `IntentionStoreTest` (auto-close), `RulesOnlyEngineTest`,
Room migration test 10→11 (the repo has `SavedPlaceMigrationTest.kt` as a pattern; add a Room `MigrationTestHelper` instrumented test). Gate: full suite green, zero behaviour change in
existing modes (no UI wiring yet).

---

## 6. Phase 2 — Laya on device

Lane **W-laya**. Files: `core/flow/decide/{DecisionEngine,LayaOnDeviceEngine,LayaTokenizer,LayaCodec,LayaModelStore}.kt`.

### 6.1 Interface

```kotlin
interface DecisionEngine {
    val id: String                       // "laya-q8-phone", "laya-server", "jev", "rules"
    suspend fun available(): Boolean
    suspend fun decide(stateJson: String, questions: List<Question>): List<Judgment>  // one batch
}
sealed interface Question { val id: String; val instructions: String
    data class Noul(...) ; data class Choice(..., val criteria: Map<String,String>) ; data class Score(..., val levels: List<String>) }
```
Prefer `Choice` + several `Noul`s; Laya's `score` is its weakest primitive.

### 6.2 Runtime

- ONNX Runtime Java API per S1 outcome; one `OrtEnvironment`, one lazily created
  `OrtSession` (CPU EP; intra-op threads 2; test XNNPACK EP only if S3 shows benefit).
- Session released after 10 min without decisions, on `onTrimMemory(MODERATE+)`,
  and when thermal status ≥ SEVERE (then fall back to rules-only).
- All inference on a dedicated single-thread dispatcher; never on main; never in a loop.

### 6.3 Model delivery

- `LayaModelStore` registers the model in the existing download catalog
  (`DownloadService`), Wi-Fi-only by default, pinned SHA-256 from S2, licence text
  shown, stored in app-private files. Deleting the model returns to rules/server.
- Never ship weights in the APK/AAB (Play size limits; protected runtime entries).

### 6.4 Parity

`LayaCodecTest` + `LayaTokenizerTest` (JVM) against S2 golden fixtures:
tokens exact; probabilities within ±0.002 of the Python reference using the same
ONNX file (JVM run uses onnxruntime JVM artifact in tests, or instrumented test on device).

### 6.5 Plan B

If Q8 fails budgets or ops: (1) multilingual FP16 → int8 dynamic quant; (2) convert
to LiteRT (already a dependency); (3) a smaller distilled checkpoint. **No server
fallback (D8)** — if none pass, ship Flow on the rules-only engine and ask the owner.
Record the decision in BUILD_MASTER.

### 6.6 Budgets (fill from S3; placeholders are targets, not claims)

| Metric | Target to confirm |
|---|---|
| Decision batch p50 / p95 (5 questions) | ≤ 250 ms / ≤ 600 ms |
| Session create | ≤ 3 s, off main thread |
| Added PSS while loaded | measure; must not trigger low-memory kills on Fold |
| Decisions per hour, typical day | ≤ 40 (event-driven) |
| Idle CPU with Flow active | no increase vs. build 155 idle baseline |
| Thermal | no new severe events in the 10-min candidate run protocol (BUILD_MASTER) |

---

## 7. Phase 3–5 — Meow Flow UI, interruptions, context sources

### 7.1 UI (lane **W-flow-ui**, files `ui/flow/**`)

Match `docs/design/meow-flow.html` exactly (see pass-down §2):
`MeowFlowHome` (dateline, main thought, "also for you", ask line), `LeaderLines`
(Canvas; draw once per snapshot change; the travelling dot is a 900 ms one-shot
animation, then static), `InterruptBand` (pushes content, compacts it), `FlowAdjustSheet`
(all §5.4 settings), `FlowThemes` (6 themes × day/night tokens, on-accent text chosen by
higher contrast — see `docs/design/README.md`), `WhyPanel` (optional debug: judgments list — owner found it useful).

- Fonts: serif for headlines/because lines, sans for metadata. Use bundled OFL fonts
  (e.g. Source Serif 4, Archivo — both OFL) or system serif; no network fonts.
- The flow screen is static between snapshots: zero continuous animation.
- Accessibility: every item a single focusable node; TalkBack order dateline → main
  → actions → more; minimum 48 dp targets; respect reduced motion.
- Integrator wiring: `AppTheme.FLOW` branch in `CompanionScreen`, collect
  `FlowController.snapshot` in the ViewModel via a hook file (pattern of
  `CompanionAgenticHooks.kt`) to keep `CompanionViewModel.kt` edits minimal.

### 7.2 Interruption delivery (`core/flow/InterruptDelivery.kt`)

| Where the user is | Voice on | Notify on | Result |
|---|---|---|---|
| Meow Flow on screen | ✓ | any | band + spoken line |
| Meow Flow on screen | ✗ | any | band only |
| Another app | ✓ | ✓ | heads-up notification (actions: primary / Not today / Open Meow) + spoken line |
| Another app | ✓ | ✗ | spoken line only |
| Another app | ✗ | ✓ | notification only |
| Another app | ✗ | ✗ | held in flow with reason |
| Driving | — | — | spoken first, one decision, large targets; no reading (catalogue #26) |

Never speak while the user or another app holds the mic/audio focus (`MicOwner`,
speech session gates). Notification actions are `PendingIntent`s into
`FlowActionReceiver` → `OutcomeLog`. The band's countdown uses route facts from code.

### 7.3 Context sources (lane **W-context**; each behind its own permission and toggle)

| Source | Mechanism | Notes |
|---|---|---|
| Driving / movement | `DrivingDetector` | already exists |
| Place | Play Services geofences on saved places + stores relevant to open intentions | **background location** → Play declaration; register ≤ 20 active fences, refreshed on intention changes |
| Foreground app | `UsageStatsManager` events (usage access) | only package + category; no content |
| Notifications | generalised `RoadAlertListener` → `EvidenceBus` | title/sender/category + short summary; bodies not stored |
| Calendar | `CalendarContract` read | existing permission |
| User speech/chat | existing chat pipeline → intention capture (§7.4) | |
| Media | `MediaSessionManager` (needs notification access) | for leisure suggestions |
| Desktop | existing desktop bridge | "laptop closed" / active work app |
| Weather | `WeatherRepository` | evening walk suggestions |

### 7.4 Intention capture (D6)

On each user utterance/chat turn: Laya `Noul` "does this express a need or
intention?" → if ≥ θ, `Choice` of intention type over a code-built shortlist
(buy/fix/reply/attend/return/pick up/other). Values (part numbers, dates, names)
are **selected from candidates found in code**, never generated (e.g. vehicle model
from `core/car/CarModels.kt` → bulb type via lookup table). Confirm silently in the
flow ("I'll keep an eye out for a 7443 bulb"), editable/deletable.

---

## 8. Phase 6 — evaluation, calibration, learning

### 8.1 Case format (`app/src/test/resources/flow/cases/*.json`)

```json
{ "id": "S037", "catalogue": [37, 23, 17], "state": { ... compact state ... },
  "intentions": [ ... ], "settings": { "min_urgency": "WORTH_IT", "max_per_day": 3 },
  "expect": { "outcome": "INTERRUPT", "channel": "SPEAK_AND_SHOW", "urgency_at_least": "WORTH_IT",
              "must_mention": ["7443", "usually carries"], "must_not_claim": ["in stock"] } }
```
`outcome ∈ {INTERRUPT, FLOW_MAIN, FLOW_MORE, QUIET}`. Every catalogue area gets
quiet cases.

### 8.2 Harness

`FlowReplayTest` runs cases through `SituationBuilder → engine → FlowPolicy`.
JVM with a recorded-judgment engine (fast, CI) and on-device instrumented run
with real Laya (integrator, per candidate). Report: interruption precision/recall,
quiet precision, channel accuracy, ECE per question type, latency.

### 8.3 Initial acceptance thresholds (owner may tighten)

- Interruption precision ≥ 0.90 on the held-out split; false interruptions ≤ 1 per 50 quiet cases.
- Missed "chance passing" opportunities ≤ 15 %.
- Zero cases claiming unconfirmed facts as confirmed.
- ECE ≤ 0.10 after temperature fitting (Laya README: refit one temperature per question type × option count).

### 8.4 Learning loop

Nightly (charging + idle, WorkManager, constraints): refit temperatures and
per-situation lead times/thresholds from `flow_outcomes` (catalogue §59). No
on-device fine-tuning in v1. Optional later: export anonymised cases (owner opt-in)
to fine-tune Laya offline on a development machine; the tuned model still runs only on the phone.

---

## 9. Phase 7 — Meow's Desk avatar (can start after Phase 0; lane **W-desk**)

Spec: `docs/PRO_DESK_AVATAR_PASS_DOWN.md`. Reference: `docs/design/pro-desk-avatar.html`.

### 9.1 Implementation shape (`ui/desk/**`)

- `AvatarStyle.DESK` → `DeskHabitat` composable used by `AvatarMotionHost`.
- Layers: **static** (wall, desk, window frame, corkboard frame, clock face) drawn
  once into a retained layer (same approach as `IDLE_LOAD_138_PASS_DOWN.md`);
  **dynamic** (tray pile height, jar note, sticky text, lamp glow, monitor text)
  redrawn only on `FlowSnapshot` change; **cat** layer driven by
  `AvatarFrameCadence` (walk = active phase, standing = idle rate, blink ≤ 1/5 s).
- Micro cat: vector drawing from the mockup (dark body #0E1D23, outline #4F7079,
  `LumenEyeMaterials` lens eyes, pink blush), walk cycle 2 frames @ ~9 fps,
  tail sway, speech bubble (paper #F4EEDB, 2.5 dp ink border, ≤ 70 chars).
- Object ↔ data binding (from FlowSnapshot):

| Object | Shows |
|---|---|
| Corkboard index card | top WORK intention (e.g. Meowmba Play status, tester-day tally) |
| Pinned note | latest build/test result (existing Needle/build info) |
| Monitor | current work app / last test run |
| Sticky note | next calendar item |
| Letter tray | count of PEOPLE items + badge |
| Jar | next MONEY item note |
| Lamp | glows when an AI suggestion is queued |
| Window | weather (existing provider) |
| Wall clock | time |

- Tap object → eyes glance → cat walks (1.1 s) → one line → bottom sheet with the
  same `FlowItem` detail/actions as Meow Flow. Empty states: empty tray, empty jar.
- Interruptions while Desk is active use the same `InterruptDelivery` (band over scene).
- No Care control (D2). Handwriting font only on in-world paper (bundled OFL).

### 9.2 Tests

`DeskBindingTest` (snapshot → object states), `DeskLayerCacheTest` (static layer not
redrawn on snapshot change), cadence test (standing cat renders at idle rate),
screenshot test portrait/landscape/unfolded.

### 9.3 Device acceptance (integrator)

10-minute candidate run with Desk active, per BUILD_MASTER protocol: CPU, thermal,
battery temp vs. build-155 Lumen baseline; must not exceed it.

---

## 10. Phase 8 — Jev escalation (optional)

- **No Meow Server engine** (owner D8). Do not build `LayaServerEngine`.
- `JevEngine`: TypeSafe `POST /v1/systemone`, `jev-latest`; key stored on device
  in EncryptedSharedPreferences/vault; **off by default**; state redacted; used only
  for low-confidence/long-context escalations. The AIOS host's Jev adapter is a
  separate system (key missing, live inference unverified) — do not couple them.
- Engine order: phone Laya → Jev (only if the user enabled it) → rules-only.
- Matrix test: airplane mode, Jev key missing/invalid, Laya model deleted, thermal severe.

## 11. Phase 9 — release

- Play Console: background location declaration + video, notification access and
  usage access rationale; update `docs/store/DATA_SAFETY_*` inventory (location,
  app activity, messages metadata — processed on device; server/Jev only if enabled).
- Manual: new chapters "Meow Flow" and "Meow's Desk" in `docs/manual/`.
- Website copy may describe features; **no Android source** publicly.
- Standard receipt `docs/FLOW_DESK_<code>_<date>.md` with suite counts, lint, device
  checks, heat run, protected-entry diff, model hashes.

---

## 12. Lanes and file ownership

| Lane | Owns | Must not touch |
|---|---|---|
| **Integrator (Codex)** | `CompanionScreen.kt`, `CompanionViewModel.kt`, `CompanionState.kt`, `AppAppearance.kt`, `AvatarStyle.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `libs.versions.toml`, Room database class + migrations, `RoadAlertListener.kt` generalisation, builds, installs, receipts | — |
| W-core | `core/flow/*` (not `decide/`), JVM tests | shared screen/state/VM, gradle |
| W-laya | `core/flow/decide/*`, golden fixtures, `docs/LAYA_IO_CONTRACT.md` | gradle (request deps from integrator) |
| W-flow-ui | `ui/flow/*`, fonts under `res/font/flow_*` | CompanionScreen wiring |
| W-context | `core/flow/sources/*` | manifest (request entries) |
| W-desk | `ui/desk/*` | AvatarRenderer/Lumen files |
| W-eval | `app/src/test/resources/flow/cases/*`, `FlowReplayTest` | production code |

Workers deliver: source commit, changed files, tests, limitations, handoff note.

---

## 13. Risks

| Risk | Mitigation |
|---|---|
| Two `libonnxruntime.so` / version mismatch breaks voice | S1 before any Laya code; voice regression on device; protected-entry diff in receipt |
| Community ONNX conversions differ from upstream | pin hashes; golden parity vs. Python on the same file; prefer converting ourselves from `convaiinnovations/laya-typed-decisions` if parity fails |
| Laya near-chance zero-shot | use typed-decisions checkpoint; calibrate; eval gates §8.3; rules fallback |
| 512/1024-token context | `SituationBuilder` hard cap; compact JSON; shortlist in code |
| RAM/heat on Fold | lazy session, release policy, thermal fallback, 10-min run gate |
| Over-interruption annoys user | user caps + min gap + held-with-reason; learning from dismissals |
| Play policy (background location, notification/usage access — owner-approved D10) | declarations in Phase 9; features degrade gracefully without them |
| Privacy | on-device default; redaction; Jev opt-in; learned facts viewable/deletable |
| CompanionViewModel size (13k lines) conflicts | hook-file pattern; integrator-only edits |

---

## 14. Takeover checklist (for whoever picks this up)

1. `git fetch --all`; confirm branch/HEAD per BUILD_MASTER; run `scripts/fetch_kws_assets.sh` in a fresh worktree.
2. Read §0 decisions, pass-downs, demos, catalogue.
3. Find the current phase: check BUILD_MASTER "Meow Flow" entry (add one when you start) and `docs/FLOW_DESK_*` receipts.
4. Never start Phase 2 before S1 + S2 are recorded; never ship before §8.3 + heat gate.
5. Ask the owner before: downloading third-party model files, new dangerous permissions, installing, Play uploads, enabling Jev/server by default.
6. Record progress as a dated section in BUILD_MASTER + a receipt; keep this plan's §0 table updated if the owner changes a decision.

## 15. Owner answers (2026-09-22)

1. Background location — **yes** (D10).
2. Notification access reuse + usage access — **yes** (D10).
3. Meow Server as Laya host — **no** (D8).
4. Store stock sources — owner unsure; default **D12**: no stock lookups in v1, never
   claim "in stock", say "usually carries it" and offer to call/check the store page.
5. Meow Flow vs Professional — **sits beside it** (D11).

No open owner questions remain for Phases 0–7. Ask again before: downloading
third-party model files, installing, Play uploads, enabling Jev by default.
