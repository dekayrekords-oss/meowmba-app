# Reproducible native dependency recovery

Use cached dependencies when the build host cannot reach upstream. This does
not disable TLS verification or the pinned archive/source checks.

Prerequisites: Android Studio JDK, Android SDK/NDK and the project's Gradle
dependencies. A fresh checkout also needs `scripts/fetch_kws_assets.sh` for
the gitignored voice runtime. Do not commit tokens, keystores, caches or models.

## Cache inputs

- `-PnativeNeedleLibrary=/absolute/path/libneedle.a`: Android arm64 static
  archive with SHA-256
  `2a5286cd8d4d750f8ee6eb885f76fd57aacee5daefa2e4e378bcfe583e7e999e`.
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
is beneath `app/.cxx/Debug/5t3y1z3e/arm64-v8a/`: `needle3/libneedle.a` and
`_deps/stable_diffusion_cpp-src`. Generated cache IDs differ between hosts.

An unsigned bundle is for inspection only. Configure the existing release key
via gitignored `keystore.properties` and omit `allowUnsignedRelease` for a real
upload artifact. Do not substitute a debug certificate or create a replacement
key for an existing Play app.

Source override uses CMake's documented
[FetchContent source directory override](https://cmake.org/cmake/help/v3.22/module/FetchContent.html).

## CPU compatibility (candidate 151)

App-built JNI and Stable Diffusion/GGML code now use `-march=armv8-a`, with
`GGML_NATIVE=OFF` and an explicit baseline `GGML_CPU_ARM_ARCH`. A development
machine or newest phone must not silently select instructions for every user.

Needle is a separately pinned prebuilt archive. Inspection of that exact archive
found SDOT instructions (187 occurrences). Its bridge is loaded only after the
small baseline `meow_cpu_features` JNI probe checks Linux `HWCAP_ASIMDDP` via
`getauxval(AT_HWCAP)`. Unsupported devices keep other configured assistants and
basic commands; loading the optional model explains its unavailability. Updating
Needle's pinned bytes requires repeating the instruction-set audit.

See [Android CPU feature guidance](https://developer.android.com/ndk/guides/cpu-features).
Unit tests verify the unsupported path never invokes the vendor loader. Device tests
exercise the real probe and native entry points. These checks do not replace actual
older-arm64 or 16 KB-device acceptance of every packaged third-party library.
