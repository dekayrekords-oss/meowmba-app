# Signed release 1.3.20

Build completed successfully on Meow. This supersedes the earlier unsigned
candidate status in the September 5 preparation notes.

## Artifact validation

- AAB: `app/build/outputs/bundle/release/app-release.aab` in the checkout below.
- 188,786,090 bytes; SHA-256
  `c8fd38c838cd6957ff0e9bc23353f4f7de4299908ce410d67c4d9379d1df7593`.
- Bundletool 1.18.3 validation exits successfully; manifest confirms
  `com.meow.meowmba`, versionCode 24, versionName 1.3.20.
- Jarsigner reports `jar verified`; the AAB's signer matches the registered
  upload certificate below. It also reports self-signed/no-timestamp and ZIP
  stream-order warnings; this is not a claim of zero validation warnings.
- All 13 native libraries have 16 KB ELF LOAD alignment; required voice,
  Needle and Stable Diffusion libraries are present.
- No GLB/GLTF/Blender assets. Release lint passed; device QA remains pending.
- Build source: `de8dfbf`; subsequent `fb509fe` only strengthens the bundle
  inspection script and does not change app code.

## Verified identity

Play Console app: Meow Assistant, package `com.meow.meowmba`.
Debug remains `com.example.meowmba`; do not uninstall or clear its data.

Upload certificate SHA-256:
`21:03:E4:B1:4A:CF:74:A4:62:21:8B:25:F2:A0:44:19:85:6C:5C:26:48:D1:3D:B1:4F:6F:F8:C4:FA:24:7F:C0`.
The existing private key on Meow matches and unlocks. No reset or new key.

## Build

Isolated checkout on Meow: `/home/claude/meowmba-release-1.3.20.wJWAy6`.
Signing stays outside the checkout. Never commit the key or properties.

```sh
JAVA_HOME=/home/claude/java/jdk-21 \
ANDROID_HOME=/home/claude/.local-toolchain/android-sdk \
PATH=/home/claude/java/jdk-21/bin:$PATH \
./gradlew :app:bundleRelease \
  -PreleaseSigningProperties=/home/claude/meowmba-app/keystore.properties \
  --no-daemon --no-configuration-cache --max-workers=4
```

Fresh checkout voice libraries come from the official sherpa-onnx v1.13.4
Android archive. Required native runtime presence is checked by
`scripts/check_release_bundle.py` in addition to ELF alignment.

## Remaining publication gates

Signing is not production readiness. Confirm versionCode 24 is unused in Play,
finish release-device QA, and resolve the accessibility automation policy review
and privacy/data-safety/listing updates described in
`RELEASE_PREP_2026-09-05.md`. MWDAT is disabled without its build-time token.
The 3D prototype is excluded. No device install or Play submission is authorized
by this build step.
