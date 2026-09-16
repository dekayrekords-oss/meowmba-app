# Verified phone agent and learned search workflows — September 9, 2026

Candidate **1.3.61 / code65**, built and not installed. Implements the useful Meridian ideas inside Meowmba's existing Kotlin phone controller. The source package was reviewed as reference; its Python orchestrator and incomplete Android executor were not imported.

## Delivered behavior

- `VerifiedPhoneRunner` owns sequential planning, grounding, dispatch, settling, verification, and result reporting. Each attempted decision counts toward the bounded budget, planning has a 30-second deadline, and the whole run has a 180-second deadline. Cancellation is checked after asynchronous planning/observation and before dispatch. A failed or uncertain action is not automatically repeated.
- `PhoneVerification` adds validated semantic selectors and typed expected results: package, exact visible text, uniquely identified control, exact editable value, or a changed screen. Changed-screen checks cannot finish a goal. Completion without a valid observed condition is rejected. Final feedback states what was observed rather than repeating a model's unsupported sent/saved summary. An already-present requested condition can be reported without pretending an action occurred.
- The actual accessibility service exposes bounded snapshots with app version, control identities, values, and content. Password values are excluded. Ambiguous/missing selectors do not fall back to unrelated nodes. Controls are checked again before dispatch; unrelated noninteractive text changes do not renumber a still-valid control. Existing consequential-action checks still inspect the real target and preserve separate confirmation/manual handoff.
- Screen readiness uses repeated observations with a bounded timeout instead of a fixed 1.5-second delay. Phone-agent swipes await Android's callback without blocking the UI thread. Readiness is conservative: oversized control lists or unreadable/unstable screens stop rather than imply verification.
- Existing text in unrelated draft fields is not overwritten. Search fields can replace their current query. Typing explicitly prohibited by the user remains prohibited.
- `PhoneRecipeStore` compiles supported, fully verified search traces into declarative data. Three matching independently planned runs promote a candidate. Replay substitutes a new query as data, needs no planning call for a matching recipe, verifies every step, and is tied to the observed app versions. Failed replay suspends the candidate; app changes require planning again. Necessary unsupported actions cause compilation to be rejected rather than silently dropped.
- Recipe storage contains at most 24 candidates with at most 11 steps each, including the verified ending. Query values are replaced by a placeholder and are not saved in recipe files. Only bounded search/control shapes are accepted; drafts, messages, arbitrary pixel actions, scripts, and credentials are outside this learning pilot. Storage is versioned and atomically replaced; corrupt files fall back to normal planning.
- Settings → Phone & device includes Learn search workflows, candidate/ready counts, Refresh workflows, and Forget workflows. Disabling or forgetting revokes the active recipe epoch so a running task cannot restore deleted recipes or continue replay under old settings.
- `phone.run_metrics` records elapsed time, verified-step count, planning-call count, replay use, and termination reason, without screen/query contents. Diagnostic write failure does not invalidate a verified outcome.

## Using learned searches

Ask **“use my phone to search for birds in YouTube”** (substitute an installed app/query). This explicitly routes through the approved phone controller. A compatible app needs an accessible search field and an observable result. After three matching verified runs, later searches can replay the learned steps with a different query. Queries containing compound instructions are not compiled.

Ordinary searches with existing direct app search URLs retain those faster routes. Ordinary location/web searches are not redirected into phone control merely because they contain “in” or “on”. The app must be resolved from the installed catalog before learning or accepting a learned search result in it.

The compiler deliberately supports a narrow initial repertoire: opening the requested app, identifying search/navigation controls, entering one query, and observing a supported result. It is not arbitrary task learning or a replacement for the durable business workflow engine. Unsupported traces still run through normal planning but are not saved as recipes.

## Verification

Final command: `:app:testDebugUnitTest :app:assembleDebug` — **1,908 tests total; 1,903 passed, five skipped, zero failures/errors**, successful build in 44 seconds.

27 new tests cover real-snapshot privacy/identity, settings revocation and deletion, exact result checks, ambiguous/missing selectors, stale planning, noninteractive clock changes, no-repeat-on-uncertainty, cancellation including a non-cooperative planner, time/action budgets, typed-field protection, app resolution/version changes, query substitution with quotes, three genuine planned runs before promotion, replay without model calls, corrupt storage, learning disable/revocation, and diagnostics failure. A native Compose settings capture was visually reviewed.

APK package/version and debug signature verified. All **125 prior assets/native-library entries** have unchanged CRCs relative to 1.3.60, preserving the bundled speech runtime. `git diff --check` passed. Earlier focused failures were in the new test helper's duplicate-node assumption and its screenshot capture method; both were corrected and the final full suite passed.

No real microphone/camera capture, phone-control action, live model inference, external account mutation, installation, or physical-base action was performed. Synthetic tests demonstrate behavior, not real-device accuracy or latency. Physical control remains quarantined. LAN SSH fetch failed; remote currency was not verified. Existing local/uncommitted changes were preserved.

## Release evidence

`/Users/prog/Meowmba-Releases/verified-agent-2026-09-09/` contains the APK, final logs and XML results, package/signature checks, settings capture, before/after source archives, incremental patch, and hashes.

APK SHA-256: `f1f5961ab8221691edc34d342cc8f0df367d26b7f453e4992acea9d0ff1e7c43`.
