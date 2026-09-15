# Onboarding and menu consistency — Build 133

Requested: include current features in first-run onboarding, match the existing
visual theme throughout app menus/submenus, require avatar selection, auto-size
first and then offer adjustment.

## Delivered behavior

- Welcome tour covers characters/habitats, voice/chat, memory and named places,
  bug intake, Computer/browser/Terminal/SSH, configured sequential agent teams,
  business approvals/audit, providers/integrations, the optional neural server,
  creative tools and regional drive help. Dependencies and unfinished broad
  capabilities are stated instead of promising universal autonomous control.
- Avatar selection includes every current style and all sixteen Lumen forms.
  Tapping a look or animal is required before continuing. The quick finish path
  also visits selection and fit review.
- Auto-fit uses actual display proportions and the native animal envelope, with
  room for motion and lower controls. Tall ears and different eye/face geometry
  participate in the fit. The preview can expand to full screen. The slider is
  initially hidden: Keep this size or Adjust size. Auto-fit again discards the
  manual override. The accepted scale persists through the existing avatar-size
  preference and ViewModel callback. Rotation/unfolding recalculates the automatic
  fit while this setup step is open; a manual override is retained.
- The frozen setup preview uses settled native anatomy. It does not open sensors,
  connect a neural server or start an animation clock. Normal live morphs remain
  unchanged. Animals remain floating features; no full head was added.
- The provider step reuses production cards for OpenAI, Claude, OpenRouter,
  OpenCode Zen and custom APIs, plus existing local-model setup. Existing model
  discovery, billing consent, connection checks and encrypted storage apply.
  Simply finishing onboarding does not claim that an AI is ready.
- Optional server setup opens the real desktop or neural pairing screen. Removed
  the old disconnected host/password form, which only wrote unused preferences.
- Navigation remembers actual previous pages. Setup survives a Terminal peek,
  clears transient wizard state on completion and does not re-enroll existing users.
- Shared themed wrappers now include dropdown menus, alert dialogs and modal
  bottom sheets; searchable model menus use the same surface and border. Remaining
  dark-only colors in app settings, voice/model screens, browser chrome, Terminal,
  weather controls, inventory and agent panels now use the existing theme roles.
  Light/dark and Professional choices remain intact.

## Verification

All **2,533 JVM tests passed**, with no failures, errors or skips, on the final
app source. Both APK builds and release lint passed. All35 changed/new app source
files matched the recorded hashes in the Mac and both Meow build checkouts.
The signed133 APK is installed on RFCY61NJEVM by USB in-place upgrade; package
version and23:02:35 update time were checked. Cold launch returned OK in290ms,
with no AndroidRuntime error for the new process. Native/model assets are
preserved:75 entries checked in debug and149 in release (only the two generated
ART profiles changed). No app data was cleared. Debug133/test APKs are archived;
the installed debug app stays132. Play remains the recorded130 submission.

The phone remains locked. Native Robolectric screen captures and interaction
checks passed, but the isolated133 phone UI fixture was **not run**, and the
signed app's menus were not visually checked through the lock screen. The owner
was asked to unlock it; no security bypass was attempted.

Final build, runtime preservation and installation receipts are recorded in
`docs/verification/onboarding133-20260914/summary.json`. Native captures are in
`/Users/prog/Meowmba-Releases/onboarding133-20260914/previews/`.

The focused coverage exercises required selection, automatic/manual/reset sizing,
back navigation, persistence callbacks, saved-instance restoration, feature/provider
reachability, small/landscape screens and large fonts, plus native disabled,
selection, dropdown and dialog semantics. Geometry tests bound all Lumen animal
envelopes on cover, inner and landscape display proportions.

`Onboarding133DeviceTest` is a separate phone UI fixture with isolated state. It
never clears onboarding, writes avatar preferences, touches keys or requests
permissions. A locked phone must be unlocked by its owner before counting that
interactive visual check as verified.
