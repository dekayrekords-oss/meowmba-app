# 1.3.22 / versionCode 26 — voice runtime hotfix

The SDK-enabled 1.3.21 bundle omitted the gitignored arm64 Sherpa JNI and
ONNX Runtime libraries. Build success alone did not validate the final
artifact, and downloading model files cannot repair missing native libraries.

This release restores both libraries from the existing Sherpa v1.13.4
runtime cache on Meow. Meta SDK configuration, signing, and the Play package
`com.meow.meowmba` remain configured through the existing external properties.
The paused Blender model remains excluded.

`preReleaseBuild` now rejects missing/empty voice runtime inputs.
`bundleRelease` now runs `verifyReleaseBundle` as a final verification task,
using the existing packaged-library and 16 KB ELF alignment checker.

The missing-library preflight was tested before restoring the files and
failed with the expected diagnostic. Always inspect the artifact verifier's
result before uploading. Physical microphone and glasses behavior still
requires a connected phone and glasses.

## Verified artifact

- Release build succeeded, including `verifyReleaseBundle`.
- No required native libraries missing; all packaged native ELF libraries
  pass the 16 KB alignment check.
- Bundletool validation passed; jarsigner reports `jar verified` (standard
  self-signed upload certificate and ZIP manifest ordering warnings remain).
- DEX inspection confirms the real Meta bridge, Meta SDK, and Sherpa API.
- The GitHub package-download token is absent from the archive contents.
- No `.blend`, `.glb`, or `.gltf` model asset is included.
- SHA-256: `23448bca7ab05f0f9a86b2e06407b45e9e77ab116a1f1e2e323b052a59ee8e67`.
- Mac upload copy: `/Users/prog/Meowmba-Releases/meowmba-1.3.22-code26-mwdat-voice.aab`.
