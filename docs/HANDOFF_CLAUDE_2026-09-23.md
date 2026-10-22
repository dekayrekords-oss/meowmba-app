# Handoff from Claude to Codex — September 23, 2026 (evening)

Read `BUILD_MASTER.md` first, then this file. Everything below is committed on
`feature/meow-flow-core` (head `86507d9` at writing) in
`/Users/prog/AndroidStudioProjects/Meowmba-flow-20260922`. Nothing was pushed anywhere.

## 1. Where things stand

| | |
|---|---|
| Installed | **1.5.0 / code 166** on SM-F971U (serial `R5GL75XFCXD`), in place, data kept (first install 09:05:20). The Fold SM-F966U1 (`RFCY61NJEVM`) still has Codex's **159**. |
| Next version code | **167**. Never reuse a code; every installable APK gets a new one. |
| Tests / lint | 2,908 JVM tests, zero failures; `:app:lintDebug` zero errors. |
| Build owner | Codex again from here. The owner asked Claude to install 161–166 directly. |
| Receipts | `/Users/prog/Meowmba-Releases/context161-20260923/` … `context166-20260923/` (APK, SHA-256, README). |

### How the owner-requested builds were made (keep the full runtime!)

```bash
./gradlew :app:assembleDaily -PincludeMwdat=true \
  -PmwdatProperties=/Users/prog/AndroidStudioProjects/Meowmba/mwdat.properties \
  -PprebuiltDebugNativeDir=/Users/prog/Meowmba-Releases/flow156-20260923/verified-jni
```

Then: compare `lib/arm64-v8a/*` with `backup160-20260923/meow-preview-1.4.4-code160.apk`
(all 86 must be present; 161+ adds only `libonnxruntime4j_jni.so`), `apksigner verify`
(cert `b7850d5f…3379ff`), `zipalign -c -P 16 4`, archive, `adb install -r`, launch, check the
crash buffer. **Without the two `-P` flags the Meta glasses SDK and bridges are silently dropped**
(Claude's 13:00–15:00 installs on the new phone did this; fixed from 161).

## 2. What was built today (by commit)

| Commit | What |
|---|---|
| `639ebd3`…`a854481` | Flow/Desk repair after the 157 review (real app data in Flow, Desk as a mode, tappable desk, desk fills the screen). |
| `5154ebd` | Heat: motion sensor off in Flow/Desk; Flow ignores non-people notifications; Flow mic + voice line; wrapping dateline. |
| `763d535` | Home swipes on Flow/Desk (later remapped, see `7cc8544`). |
| `c197e39` | **Laya on the phone**: `core/flow/laya/*` — tokenizer + sequence port (24/24 golden parity), ORT 1.27.0 engine sharing sherpa's `libonnxruntime.so`, budgeted `LayaAssessor`, Wi-Fi `LayaDownloader` with SHA-256 manifest, Adjust › Thinking (Download / Check Laya). R8 keep rule for `ai.onnxruntime` in `src/main/keepRules/rules.keep`. |
| `31b8f9a` | `docs/MEOW_SCENARIOS.md` — ~150 life scenarios; the requirement for the context system. |
| `c02fd55` | **Meow context C1–C2**: `core/context/live/*` — activity transitions + balanced background location (PendingIntent → `ContextReceiver`), `LiveContext`, Room **v12** journal (`life_events`, `location_samples`, migration 11→12 tested), every utterance stamped, `JournalRecall` "right now" line + when/where recall in chat. |
| `fa5f29d` | Talk less (`SpeechMode`), "heading home"/"I'm home now" place intents, mic switch in Flow/Desk, **+** attachments (camera/photo/file/paste → on-phone OCR/PDF/DOCX → chat; tracking numbers → Flow "Package on its way"). |
| `7cc8544` | Desk clock → Android Clock; drawers = app/tool folders; **edge dock** (6 slots, Lumen-tab behaviour); `openTool`; SpeechMode default IMPORTANT; **idle avatar** (Lumen/Orb/off) with Flow ribbon; swipes L→R Flow, R→L chat. |

## 3. Owner decisions made today (keep them)

- **Proactive more than reactive, but not chatty.** Show/notify proactively; speak on its own only
  when it matters (driving/headphones); typed turns get typed replies; spoken replies are short.
  Never promise an action (e.g. "opening Maps") unless code performs it.
- **Remember everything with context** — see `docs/MEOW_CONTEXT_PLAN.md` and `docs/MEOW_SCENARIOS.md`.
- **Laya integrated and on**, overriding the earlier "defer Laya" note in the 159 receipt. The owner
  downloaded it; files are on the SM-F971U at
  `/sdcard/Android/data/com.example.meowmba/files/models/laya/v1/` (Fold: use the in-app download
  on Wi-Fi, or adb push and `chmod 777` the created directories).
- Swipes: **left-to-right = Flow, right-to-left = chat**, up = apps, down = menu. Browser moved to the menu.
- The dock must behave like the Lumen screen's edge tab (hidden grip; grip never jumps).
- Idle avatar default Lumen after 2 minutes.

## 4. Measured facts you can rely on

- Heat (SM-F971U): app ≈3% of one core over 28 min on screen; Flow/Desk idle ~0.8% and 0 fps.
  The hot period (skin 38 °C) coincided with Wi-Fi off, weak signal and cellular high-TX power.
- Laya on-device check: 26/26 agree with the reference, max Δp 0.0158, ~1.05 s/question at
  4 threads, load 0.3 s. 26 back-to-back questions raised skin 33 → 40 °C, hence production limits:
  2 threads, ≤2 items per burst, 3-minute gap, answers reused while the situation is unchanged,
  abstain at thermal LIGHT.

## 5. Open work, in priority order

1. **Physical check of 166 on the phone** (not done by hand): idle avatar heat with Lumen (consider
   Orb as default if warm), ribbon on a real message, dock drag/tuck, drawers, clock, + camera flow,
   "heading home" (the owner must first say "I'm home now" at home to save it).
2. **Context C3 — places**: dwell clustering from `location_samples` → Home/Work/others with
   reverse-geocoded names, arrive/leave journal rows, parking spot map link, commute inference.
   Owner must grant physical activity + "Allow all the time" location (Flow shows Allow rows).
3. **C4 — situation statements** beyond places ("I'll be home by 6", "back in 20 minutes",
   "keys are in the drawer" as object→place memory) feeding Flow evidence.
4. **C6 — surroundings**: Bluetooth car/headphones stamps exist; add DND and call state (ask the owner
   before READ_PHONE_STATE / call log), Health Connect (steps/sleep).
5. **Backup**: `UserDataBackupStore` includes the new tables automatically but its 100,000-row cap
   will eventually be hit by `life_events`; add a per-table cap or chunked export.
6. **Speech**: ~140 `speak()` call sites now pass through `SpeechMode`; review proactive lines that
   should be marked `urgent = true` (only crash check-ins are today).
7. Fold: install 167+ there too when the owner asks (it is on 159 and lacks everything above).

## 6. Gotchas found today

- `/usr/bin/git` and `/usr/bin/python3` stop working whenever the Xcode licence is un-accepted
  (owner ran `sudo xcodebuild -license accept`). Scripted edits via python silently no-op then.
- `CompanionState` is at the JVM 255-slot limit — add new fields to `CompanionFeatures`
  (`RecoveryStateContractTest` enforces it).
- VM tests that type text and assert spoken output must set `SpeechMode.ALWAYS`; typed turns are
  silent by default. A silent reply re-arms always-on listening, which can spin forever under
  `advanceUntilIdle` in tests that don't.
- Compose tests that mutate state from the test thread with a paused clock need
  `Snapshot.sendApplyNotifications()` before stepping frames.
- Android's regex rejects `Pattern.UNICODE_CHARACTER_CLASS` (JVM accepts it) — see `LayaTokenizer`.
- Files adb-pushed into `Android/data/<pkg>/files` land in shell-owned 770 directories the app
  cannot read; `chmod 777` the directories.
- `app/kws.tar.bz2.part` in the worktree is an unfinished download that isn't Claude's; left untracked.
