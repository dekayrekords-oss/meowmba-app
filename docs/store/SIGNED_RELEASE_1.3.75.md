# Signed release 1.3.75 (79)

Built on Meow on September 9, 2026, from the current Mac working tree, including
its uncommitted implementation changes. The baseline commit is `ba01fb2`; this
is not a claim that the release contents are all committed at that revision.
The source was copied to a new isolated directory without overwriting the
server's existing checkout. No private signing files were copied to the Mac.

## Build and identity

- Isolated source: `/home/claude/meowmba-release-1.3.75.lvxQ6w` on SSH host `meow`.
- Signing properties: `/home/claude/meowmba-app/keystore.properties`.
- Meta SDK properties: `/home/claude/meowmba-app/mwdat.properties`.
- Package: `com.meow.meowmba`; versionCode 79; versionName 1.3.75.
- Local bundle: `/Users/prog/Meowmba-Releases/meowmba-1.3.75-code79-release.aab`.
- Size: 187,404,621 bytes.
- SHA-256: `cef7601c72d948cfb7d4f9bca110bffba5001c58c6439239f80f5e4e85a816e1`.
- Upload certificate SHA-256:
  `21:03:E4:B1:4A:CF:74:A4:62:21:8B:25:F2:A0:44:19:85:6C:5C:26:48:D1:3D:B1:4F:6F:F8:C4:FA:24:7F:C0`.
  This matches the existing Play upload certificate documented for 1.3.40.

## Verification

`bundleRelease` completed successfully in 7m35s. Release signing, voice runtime,
Meta registration and vital lint gates passed. Bundletool 1.18.3 validation
passed; package and version were inspected from the bundle manifest. All
packaged native libraries passed the 16KB ELF alignment checks, with required
speech, Needle and image-generation JNI libraries/exports present.

Jarsigner reports `jar verified`. Self-signed/no-timestamp and ZIP stream-order
warnings remain as documented for previous releases; this is not a claim of
warning-free verification. Remote and local SHA-256 digests match.

Prior implementation verification: 2,077 tests, zero failures/errors, five skips;
the final wording-only correction passed all nine provider UI tests. Debug
1.3.75 was installed and inspected on the Fold. This is not release-device QA.
The provider files and Gradle version match the saved phone-verification hashes.

The build log is archived at
`/Users/prog/Meowmba-Releases/play-1.3.75-code79/release-build.log`.

## Scope and account limitations

Floating animal features and animations; voice/session recovery; thermal work
limits; named-place navigation; bug intake; project/desktop/Logic improvements;
OpenAI model discovery, Claude Messages, custom APIs and OpenCode Zen settings.
Existing phone permissions, approvals and audit remain in effect.

OpenAI account discovery returned 54 models on the phone, but generation failed
with HTTP429/no credits. Earlier OpenRouter transcription returned HTTP402.
These provider-account limits are not fixed by this release. Universal app
control, every DAW plugin and production readiness are not asserted.

## Play status

Uploaded and accepted by Google Play in the existing closed-testing Alpha track
for Meow Assistant on 2026-09-09. Console recognized version 1.3.75/code79,
including ReTrace mapping and native debug symbols, and reported Ready to release.
Release name: `1.3.75 – Voice, avatars and AI provider settings`.
The previous Alpha release was 1.3.40/code44, available to selected testers.
Saved a 100% rollout to Alpha and confirmed Send changes for review. Publishing
overview now lists this release under Changes in review. Automated quick checks
are still running (Console estimated up to 14 minutes); the submission proceeds
to review after successful checks. Approval and tester availability are pending.
Managed publishing remains off. No production or internal-testing changes made.
Supported device counts were unchanged from the previous release.

Console: https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/publishing
