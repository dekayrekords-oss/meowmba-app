# Cross-device control acceptance — September 7, 2026

## Installed versus implemented

Phone preview: **1.3.40 / code44**, `com.example.meowmba`, installed with
`adb install -r` on `RFCY61NJEVM` (SM_F966U1). This is the connected Fold, not the
previously discussed Flip6. Existing downloads, keys and data were preserved.

APK: `/Users/prog/Meowmba-Releases/meowmba-1.3.40-code44-cross-device-preview.apk`

SHA-256: `fca743965ea800f2f84153c9b67af9cdd5d06c38e3e04bb63a34f203c51469c4`

Desktop helper source/package is **0.7 preview**, not a paired/authorized service:
`/Users/prog/Meowmba-Releases/meow-desktop-bridge-0.7-preview.zip`.
Its [setup and limitations](../bridge/README.md) are authoritative.

## Added functionality

- Preserve complete multi-app goals for Reddit, Instagram, X, ChatGPT, Gemini
  and other installed apps; no per-app MCP claim. Accessible screen controls
  drive the loop, not a remembered layout. Passwords and unavailable/custom
  canvas controls remain manual.
- OpenRouter controller decisions now use an actual function tool, named
  choice, one-action parsing and bounded output. Prose cannot count as an
  executed action. Local LiteRT retains structured generation. App context is
  sent off-device only when a cloud model is selected/authorized.
- A changed foreground app, interactive content, editable value or bounds
  invalidates a pending phone plan. Full editable-value fingerprints stay
  local, so changes beyond the shortened model label are still detected.
- Real indexed editable-field input, ambiguous-field rejection and no password
  text entry. Explicit navigation-only/no-typing limits are enforced outside
  the LLM. A draft task cannot authorize a final social submission.
- `brain.models` lists installed compatible files and a previously verified
  cloud choice without keys. `brain.select_model` requires approval and runs
  planning/context probes before changing saved setup. No new paid choice or
  model download is silently authorized; in-progress control blocks switching.
- Bluetooth paired-device inventory and guided Android Settings navigation.
  Pairing/PIN confirmation stays with the person; paired ≠ connected, and
  specific device protocols still need real integrations.
- Desktop navigation uses approved substeps, fresh one-use UI snapshots,
  bounded budgets and cancellation. No second parent goal can consume the
  slot required by its child. Native UI adapter currently targets macOS only.
- USB disconnection and failed pings no longer delete pairing. USB subnet
  membership does not bypass code-based trust. `--auto-pair` now uses a short
  owner-confirmed code; pairing attempts are serialized.
- Blender's new adapter validates 1–80 simple primitives and fixed scene
  parameters, runs a separate factory-startup background process, and exports
  .blend/.glb/optional PNG with hashes. It never edits the open document,
  executes model-generated Python, or incorporates a 3D avatar into Android.

## Measured evidence

- JVM/Compose: **1,248 tests, 0 failures, 0 errors, 5 conditional skips**.
- Bridge tests: **14 passed**, including stale/replayed UI frames, password,
  oversized/disabled/invalid controls, data-not-code input, scene bounds and
  USB/expired/reused pairing-code rejection. Fake UI tests are not native QA.
- Device inventory/configuration test passed: Facebook, Reddit, Instagram, X,
  ChatGPT and Gemini all resolve as installed apps. The 2.588 GB local Gemma
  file survived the upgrade. No credentials were printed.
- Code43 normal-app `list your models` completed, correctly identifying local
  selection. Its Reddit trial launched Reddit and tapped Search, then wandered
  at an unlabeled field. That failure caused the index/stopping-point/goal-limit
  fixes in code44. It is not counted as a successful whole task.
- Code44 final UI trial could not start: instrumentation had caused Android to
  mark Meow Accessibility as **crashed/disconnected**, although still enabled.
  No matching new Meow Java exception was in the inspected crash log. Reconnect
  the service using its Android Settings toggle before final normal-app tests.
  Do not run UIAutomator dumps while the phone-agent loop is active: they
  suppress Accessibility and invalidate the measurement.
- Saved OpenRouter key exists; original `openrouter/free` was not previously
  verified and failed the synthetic navigation readiness check. A failed check
  preserved the previous local selection. The live public catalog confirmed
  `google/gemma-4-31b-it:free` supports tools at zero price, but its opt-in
  synthetic probe returned **HTTP429**. No paid model was enabled, no cloud
  selection was persisted, and no real phone screen was sent in these probes.
  Rate limits were not hammered with repeated retries. These two failed device
  probes are separate from the successful JVM and configuration-retention tests.
- Actual Blender **5.2.1** smoke produced all three artifacts in
  `/tmp/meow-blender-smoke.fxPfr1`. Preview was visually inspected: a basic
  primitive cat head, deliberately not a production character. Initial test
  caught Blender's renamed EEVEE engine enum; runtime detection fixed it.
- Native macOS UI read failed with OS error **-25211, assistive access denied**.
  No native desktop click/type has been claimed as verified.
- Meow SSH/fetch became reachable. Its inspected session was `tty`, with no
  Blender or xdotool on PATH and no running desktop helper found. This does not
  prove it has no graphical session elsewhere, but it is not ready evidence.
- Latest observed battery temperature: 36.6°C. This is a spot reading, not
  thermal/performance certification. No overnight test or Play submission.

## Remaining acceptance / required owner input

1. Reconnect Meow Accessibility on the phone; rerun read-only Reddit search
   goal, then other apps, without touching the screen during inference.
2. Select and verify a capable cloud model (or consciously retain local with
   limitations); test repeated unfamiliar layouts and long-context tasks.
   Free routing can change model/availability between requests; no “all apps”
   promise follows from two synthetic probes.
   A paid option requires the owner's pricing/budget decision; do not silently
   turn on paid usage to get past the free provider's rate limit.
3. Test native final-action Cancel, timeout, changed-screen rejection and a
   separately user-approved positive delivery. The previous Facebook draft
   `meow ai test` remains a draft; no test has published it.
4. Owner chooses the computer/workspace, starts/pairs the helper and grants OS
   permissions. Linux/Windows need an actual compatible UI adapter; macOS GUI
   acceptance still pending. Blender adapter does not solve custom-canvas UI.
5. Pair a named real Bluetooth device and verify its actual connected profile.
6. Finish service-specific email/signup connectors, scoped auto-reply policies,
   deduplication/delivery reconciliation and Android long-horizon acceptance
   from the previous plan. These are not implemented by installed social apps.

Primary references: [OpenRouter function calling](https://openrouter.ai/docs/guides/features/tool-calling),
[free router behavior](https://openrouter.ai/docs/guides/routing/routers/free-router),
[Android Bluetooth connections](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices),
[Android Accessibility](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).
