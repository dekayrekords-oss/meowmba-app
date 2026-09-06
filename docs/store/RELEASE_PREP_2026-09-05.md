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

`./gradlew :app:bundleRelease` was attempted with Android Studio's bundled JDK.
Gradle configuration, resource processing, signing validation and Kotlin/KSP
tasks started successfully. The build stopped in native CMake configuration
because the host could not resolve GitHub while fetching
`stable-diffusion.cpp`; the downloaded `needle2/libneedle.a` was empty and
failed its pinned SHA-256 check. Retry on a network-enabled build host or
provide the already-verified dependency through the documented cache. Do not
ship an artifact from a failed or unverified native build.

## Signing

`app/build.gradle.kts` uses `keystore.properties` when present and otherwise
falls back to the debug key so development builds remain reproducible. A Play
release must be built with the existing upload/release keystore; never commit
that keystore or its passwords. Verify the signer and Play Console package
identity before upload.

## Remaining Play Console gates

1. Build a clean release AAB after native dependency/network recovery.
2. Verify signing certificate, version code, ABI packaging and native libraries.
3. Complete the existing listing, privacy policy, data-safety, content-rating,
   target-API and app-access forms in `docs/store/PLAY_LISTING.md`.
4. Capture current phone screenshots and a feature graphic; do not use Blender
   previews as app screenshots.
5. Run the release smoke checklist on the Fold: onboarding/key persistence,
   voice/Whisper, MCP linking, chat collapse/streaming, cleanup browser,
   permissions, background/resume and account logout.
6. Upload to an internal/closed Play test track and inspect crash/ANR reports
   before production rollout.

No Play Console upload or production publication was performed in this task.
