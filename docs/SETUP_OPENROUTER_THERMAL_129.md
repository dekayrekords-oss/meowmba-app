# Setup finish, OpenRouter guide, idle thermal — build 129

## Codex findings (`origin/codex/release129-computer-acceptance`)

Branch tip `5efed2f` (not cherry-picked wholesale — diverged after `7748ed3` from
drive/CarPlay tip `3a1dc0d`) includes:

1. **`perf(companion): remove redundant idle work…` (`5efed2f`)** — allocation-free
   animation sampling, iris fibre colour cache, illustrated room layer split,
   motion sensor array reuse, neural diagnostics only while Brain/Settings open.
2. **`feat(onboarding): guide avatar fitting…` (`a383254`)** — SaveableStateHolder
   so Terminal peek does not remount setup at Hi; avatar-first welcome flow.
3. **`feat(computer): add clear hub and guided setup…` (`6ae59be`)** — Computer hub UX.

Applied surgically onto `fix/setup-finish-openrouter-thermal` (idle hotspots +
SaveableStateHolder). Full branch merge deferred (conflicts with drive morph / OBD).

## Setup UX

- MainActivity hosts wizard in `rememberSaveableStateHolder`; finishing clears state.
- Removed duplicate `SetupWizardRoute` from CompanionScreen (was remounting at Hi
  when MainActivity paused for Terminal).
- Background step: gallery + live `AppBackground` preview; **Finish setup** marks
  onboarding done and lands on companion (no return to Hi).
- Guided OpenRouter step in onboarding + Settings LLM card (key → consent → model →
  test; optional local/custom endpoint vs OpenRouter cloud).

## Thermal / idle

- Idle form cycler pauses when decorative animation is disallowed or status is
  moderate+, and while backgrounded / driving.
- Neural diagnostics snapshots only while Neural Info or Settings visible.
- MotionSense reuses linear-acceleration buffer; CustomAnimation lerp/step allocate-free;
  Lumen iris fibres use cached blend; AvatarRenderer samples lean/lurch via State.

## Ship

- Preview `versionCode` **129**, applicationId `com.example.meowmba`.
