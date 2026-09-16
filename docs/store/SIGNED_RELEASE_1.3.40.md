# Signed release 1.3.40 (44)

Built on Meow from `3d4571d` on 2026-09-07 (America/Phoenix).
Source pushed to `feat/image-services-settings` in the canonical bare repo
`/home/claude/git/meowmba-app.git`.

## Artifact

- Package: `com.meow.meowmba`; versionCode 44; versionName 1.3.40.
- Isolated checkout: `/home/claude/meowmba-release-1.3.40.hU2Coi`.
- Bundle: `app/build/outputs/bundle/release/app-release.aab`.
- Local copy: `/Users/prog/Meowmba-Releases/meowmba-1.3.40-code44-release.aab`.
- Size: 194,119,086 bytes.
- SHA-256: `130edbe701670292636ce6af77e9689365366f49c5515dec0af6618cc65b7d75`.
- Remote and local checksums match.
- Upload certificate SHA-256: `21:03:E4:B1:4A:CF:74:A4:62:21:8B:25:F2:A0:44:19:85:6C:5C:26:48:D1:3D:B1:4F:6F:F8:C4:FA:24:7F:C0`.

## Verification

- `bundleRelease` succeeds with `--no-configuration-cache`.
- Initial build produced the same bundle but failed while storing Gradle's
  configuration cache; the no-cache rerun completed successfully.
- Release vital lint and signing/voice/MWDAT configuration gates passed.
- Meta wearable SDK enabled; credentials remain outside source control.
- Bundletool 1.18.3 validation exited successfully.
- Jarsigner reports `jar verified`; self-signed/no-timestamp and ZIP stream-order
  warnings remain, as with prior releases. No claim of warning-free verification.
- All packaged native libraries pass the repository's 16 KB ELF alignment check.
- Required voice, Needle and image-generation JNI libraries/exports present.
- No GLB/GLTF/Blender assets included.

## Scope and limitations

Agent workflows, improved onboarding/context, guarded phone planning, model
selection, Bluetooth setup, desktop-bridge compatibility and thermal improvements.
Live debug QA is recorded in `docs/CROSS_DEVICE_CONTROL_2026-09-07.md` and
`docs/PHONE_PLANNING_2026-09-07.md`; it is not release-device QA.
Universal app control and unattended business sending are not verified or promised.

## Play status

Uploaded, processed and saved for existing closed-testing Alpha release 7.
Play confirms version 44 (1.3.40), "Ready to release", and no lost supported devices.
Publishing overview lists exactly one change: this Alpha release, full rollout.
Status: "Changes not yet submitted for review"; quick checks running, initially
showing up to 14 minutes remaining. Review submission and tester availability are
not asserted. Internal testing and production unchanged.
