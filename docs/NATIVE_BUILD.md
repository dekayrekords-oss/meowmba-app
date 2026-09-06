# Reproducible native dependency recovery

Use cached dependencies when the build host cannot reach upstream. This does
not disable TLS verification or the pinned archive/source checks.

Prerequisites: Android Studio JDK, Android SDK/NDK and the project's Gradle
dependencies. A fresh checkout also needs `scripts/fetch_kws_assets.sh` for
the gitignored voice runtime. Do not commit tokens, keystores, caches or models.

## Cache inputs

- `-PnativeNeedleLibrary=/absolute/path/libneedle.a`: Android arm64 static
  archive with SHA-256
  `93738ae3a9488cbc3104eb65bf49093683c26eb01b0d257e980e499d1d06a9f4`.
- `-PnativeSdSource=/absolute/path/stable-diffusion.cpp`: clean Git checkout
  at `ea7f0c87cfe4c673263b4c201c596c7f1cbe2528`, including all pinned recursive
  submodules. A mismatched or dirty cache fails configuration.

These are optional; without them CMake fetches the pinned upstream dependencies.
Needle downloads use a temporary file and only replace the archive after hash
verification, so a failed download cannot truncate a previously verified copy.
`--offline` applies to Gradle, not arbitrary CMake networking: supply both cache
inputs for an offline native build.

```sh
./gradlew :app:bundleRelease \
  -PallowUnsignedRelease=true \
  -PnativeNeedleLibrary=/absolute/path/libneedle.a \
  -PnativeSdSource=/absolute/path/stable-diffusion.cpp \
  --offline --no-daemon --no-configuration-cache --max-workers=4
python3 scripts/check_release_bundle.py app/build/outputs/bundle/release/app-release.aab
```

Replace the example paths with verified cache locations. The tested Mac cache
is beneath `app/.cxx/Debug/5t3y1z3e/arm64-v8a/`: `needle2/libneedle.a` and
`_deps/stable_diffusion_cpp-src`. Generated cache IDs differ between hosts.

An unsigned bundle is for inspection only. Configure the existing release key
via gitignored `keystore.properties` and omit `allowUnsignedRelease` for a real
upload artifact. Do not substitute a debug certificate or create a replacement
key for an existing Play app.

Source override uses CMake's documented
[FetchContent source directory override](https://cmake.org/cmake/help/v3.22/module/FetchContent.html).
