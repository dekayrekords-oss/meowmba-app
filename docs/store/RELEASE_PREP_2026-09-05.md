# Release preparation — September 5, 2026

## Scope

This release-prep commit contains the Android app changes currently in the
worktree. The standalone Blender studies under `art/avatars/` and their build
scripts are intentionally not part of the app release. The app does not bundle
or install the 3D model.

## Current release identity

- Application ID: `com.example.meowmba`
- Version code: `24`
- Version name: `1.3.20`
- Play artifact: Android App Bundle (`app-release.aab`)

## Build status

The native dependency failure was resolved using the verified local cache.
`:app:bundleRelease` succeeded with `-PallowUnsignedRelease=true` and the
cache properties documented in [NATIVE_BUILD.md](../NATIVE_BUILD.md).
The candidate is **unsigned**, not ready for Play upload.

- Artifact: `app/build/outputs/bundle/release/app-release.aab` (about 180 MiB).
- `scripts/check_release_bundle.py` inspected all 13 bundled arm64 libraries:
  every ELF LOAD segment has at least 16 KB alignment, including sherpa/ONNX.
- No JAR signature entries and no GLB/GLTF/Blender assets in the bundle.
- Filament dependencies and the 3D avatar entry/picker/renderer are excluded.
- Focused unit/UI tests: Appearance (6), ChatOverlay (4), CleanupDocumentsProvider
  (3), OrbOrientation (3), OrbScatter (1): 17 tests, no failures/skips.
- Full regression, Play-generated APK validation and 16 KB device testing remain
  pending. ELF alignment alone is not end-to-end 16 KB compatibility proof.
- MWDAT is disabled because no build-time SDK token was available. Do not
  advertise direct Meta glasses SDK support for this candidate.

## Signing

Release builds no longer fall back to a debug certificate. A normal release
build fails without `keystore.properties`; an unsigned validation artifact
requires the explicit `-PallowUnsignedRelease=true` switch. A Play release must
use the existing upload/release key; never commit the key or passwords.
No production signing key was available in the checked locations.

## Remaining Play Console gates

1. Confirm the actual Play Console package and upload certificate. Code currently
   preserves `com.example.meowmba`, while historical store docs proposed
   `com.meow.meowmba`. Do not silently change the installed app's identity or
   uninstall it. Verify that versionCode 24 exceeds the Console's existing code.
2. Build with the real signing configuration; validate using bundletool and
   Play-generated APKs. Test supported CPU/device coverage: native compile flags
   currently request ARMv8.7-A, not a generic older arm64 baseline.
3. Update and verify the draft listing, privacy policy, data-safety, content-rating,
   target-API and app-access forms. Current cloud, MCP and connected-service data
   flows require review; older 'all data stays on device' wording is insufficient.
4. Capture current phone screenshots and a feature graphic; do not use Blender
   previews as app screenshots.
5. Run the release smoke checklist on the Fold: onboarding/key persistence,
   voice/Whisper, MCP linking, chat collapse/streaming, cleanup browser,
   permissions, background/resume and account logout.
6. Upload to an internal/closed Play test track and inspect crash/ANR reports
   before production rollout.
7. Review AccessibilityService phone automation against current Play policy;
   autonomous initiation/planning/execution is restricted, and a generic
   'user-enabled, confirm first' declaration is not proof of compliance.
   See [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

The existing listing/privacy drafts and historical master-plan completion
claims are not verified publication declarations. See the
[Android 16 KB guide](https://developer.android.com/guide/practices/page-sizes)
for the remaining native-device validation steps.

No Play Console upload or production publication was performed in this task.
