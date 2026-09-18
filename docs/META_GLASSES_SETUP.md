# Fixing Meta Ray-Ban SDK availability

## Why the installed app says the SDK is missing

The 1.3.20 release was built without a GitHub Packages download token. The old
build silently excluded both Meta's dependencies and the entire real glasses
bridge. No phone setting, runtime AI key, or Bluetooth pairing can add those
compiled classes afterward: an updated APK/AAB is required.

The build now uses the same configuration for Maven authentication and SDK
inclusion, accepts credentials without guessing GitHub token formats, and fails
a release preflight if the SDK or Meta release registration is missing. Debug
builds can still explicitly use `-PincludeMwdat=false` for unrelated development.
The existing bridge remains pinned to SDK 0.8.0; this is not an untested API
migration to 0.9.0.

## One-time build-machine configuration

1. Create a GitHub **personal access token (classic)** with `read:packages`.
   Do not supply your GitHub password or add the token to phone Settings.
2. Copy `mwdat.properties.example` to a private file outside the checkout, for
   example `/home/claude/.config/meowmba/mwdat.properties` on Meow. Restrict it
   with `chmod 600`. Fill in `github_token` locally, without sharing it in chat.
3. In Wearables Developer Center, register the Play app `com.meow.meowmba`, use
   the appropriate release channel/testers, and configure its return scheme
   `com.meow.meowmba.mwdat`. Follow Meta's current app/signing requirements using
   the **Play app-signing certificate**, not just the upload certificate.
4. Put that Meta app's Application ID and **Client Token** into the two
   `mwdat_*` fields. They are SDK app-attestation configuration intentionally
   embedded in the manifest. Never substitute a Meta app secret or the GitHub
   package token. Debug Developer Mode can use zero values; release cannot.

Environment overrides: `GITHUB_TOKEN`, `MWDAT_APPLICATION_ID`,
`MWDAT_CLIENT_TOKEN`. Config-file selector: `-PmwdatProperties=...` or
`MWDAT_PROPERTIES_FILE`. Precedence is nonblank environment → external file →
legacy local.properties. Default external filename is `mwdat.properties` and
is gitignored. No credentials are printed by the preflight.

```sh
./gradlew :app:verifyMwdatReleaseConfiguration \
  -PincludeMwdat=true \
  -PmwdatProperties=/home/claude/.config/meowmba/mwdat.properties
```

The preflight checks configuration, not remote credential validity. A successful
SDK-enabled compile must still fetch the real `mwdat-core`/`mwdat-camera` AARs.
GitHub 401/403 means the PAT is invalid, expired, or lacks package access.

## Build and verify the next update

- Bump versionCode above 24 and choose the next versionName when preparing the
  actual successor; do not replace/relabel the already submitted 1.3.20 bundle.
- Use the external config arguments above with `:app:bundleRelease` and the
  existing release-signing configuration on Meow.
- Run unit tests with and without SDK inclusion, compile the real bridge, and
  inspect the merged manifest for Application ID, Client Token, callback scheme,
  and analytics opt-out. Never print the values in logs.
- Run `scripts/check_release_bundle.py`; verify SDK/bridge classes survive
  release shrinking and recheck new native libraries for 16 KB alignment.
- Install only with the user's separate authorization, preserving installed
  data/signature. Pair the glasses in Meta AI, use **Setup glasses**, approve
  registration and camera access, then connect. Verify fresh real frames,
  denial, disconnect/reconnect, and Bluetooth audio on the physical phone.
- Review privacy/data-safety declarations for the SDK before another Play
  submission. Google Play approval is separate from Meta's app/release access.

## Runtime safety changes

Missing SDK no longer starts a mock camera or marks the glasses connected.
Mock frames remain an explicit developer facility, not an automatic fallback.
Photo requests reject missing SDK, simulated sources, and stale/disconnected
frames. Setup does not start capture if Meta camera permission is denied. The
Hardware card explains when an app update is needed and disables unusable
setup/connect actions.

The existing release keep rule for `com.example.meowmba.**` already retains the
reflectively loaded bridge; no blanket SDK keep/dontwarn workaround is added.

## Validation checkpoint — 2026-09-05

- Isolated Meow checkout: `/home/claude/meowmba-mwdat.452TEC`.
- Tokenless debug app and test code compiled successfully. Five
  `MetaGlassesUnavailableTest` tests passed, with zero failures/errors/skips.
- Release preflight with SDK disabled failed as intended with the missing-SDK
  configuration message. Explicit SDK inclusion plus an external token file
  reached the separate Meta release-registration guard, which correctly
  rejected unset/zero app-attestation values.
- Merged debug manifest verified: developer-mode zeros, analytics opt-out, and
  `com.example.meowmba.mwdat` callback scheme.
- An existing GitHub token was found in the older Memba project's
  `/home/claude/memba-app/local.properties`. GitHub rejected it with HTTP 401
  for both package access and basic account authentication. It was never
  displayed, copied into this repository, or modified.
- That older app uses `com.example.memba2`; its Meta registration must not be
  assumed valid for Play package `com.meow.meowmba`.
- A working classic PAT with `read:packages`, Meowmba-specific Meta release
  configuration, actual SDK-enabled compilation/shrinking, and physical glasses
  testing remain outstanding. No SDK-enabled APK/AAB has been produced,
  installed, or submitted. The current Play review is unchanged.

## Official references

- [Meta Android SDK repository and package access requirements](https://github.com/facebook/meta-wearables-dat-android)
- [Meta Android setup and attestation configuration](https://github.com/facebook/meta-wearables-dat-android/blob/main/plugins/mwdat-android/skills/getting-started/SKILL.md)
- [Official camera sample manifest](https://github.com/facebook/meta-wearables-dat-android/blob/main/samples/CameraAccess/app/src/main/AndroidManifest.xml)

These references were checked on 2026-09-05. Device and production integration
are not certified by a tokenless unit-test run.

## Meow command bridge

Voice / SSH / OpenCode integration (Hey Meta limits, tools, Settings): see [META_GLASSES_MEOW_BRIDGE.md](META_GLASSES_MEOW_BRIDGE.md).
