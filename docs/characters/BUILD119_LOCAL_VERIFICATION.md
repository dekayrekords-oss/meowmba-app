# Build 119 — offline Mac verification and lessons for Meow

The owner asked to continue without the phone. Meow SSH was also unavailable,
so the existing Mac toolchain was used to finish the Android JVM acceptance
checks. No production behavior changed during this continuation. Current
counts and release limitations are in `BUILD_119_VERIFICATION.json` and
`MEOW_BUILD_119_PASS_DOWN.md`.

## What changed, how and why

`scripts/verify_avatar_forms.sh` records the acceptance suite once, allowing
the same cases to run on either host with host-specific Gradle arguments. The
first local run passed 235 tests. An independent comparison of the script,
Kotlin declarations and XML reports caught a selector mistake: `ToolCallBrainTest`
is a filename, not a test class. Gradle did not reject that unmatched filter
because the other filters matched. The script now selects its five real classes:
`ToolCallPromptTest`, `ToolCallParserTest`, `CommandHeuristicsTest`,
`LlmToolCallRouterTest` and `AgentBrainViewModelTest`. They add 26 intended tests.
The Meow archive verifier also requires every expected suite, so a green exit
code with missing coverage cannot finalize this build.

`verification/build119_low_memory.gradle` provides an optional 2 GiB test-worker
limit and one test fork. The Gradle command separately caps its heap at 2 GiB,
uses one worker and compiles Kotlin in process. These are local verification
overrides; the app's production build settings are unchanged.

The initial run with larger default heaps reached Kotlin compilation but was
stopped by a task-owned free-space monitor before tests ran. Only two verified
reproducible CMake cache directories were removed: the untracked
`stable_diffusion_cpp-build` objects and the clean `stable_diffusion_cpp-src`
FetchContent checkout. The latter matched the exact `SD_PIN` commit
`ea7f0c87cfe4c673263b4c201c596c7f1cbe2528` in `app/src/main/cpp/CMakeLists.txt`.
No app source, model, asset, signing material or release was removed. Cleanup
reports remain in `build-out/habitat/build119/local-verification/`.

## Reproduce on this Mac

Use the bundled Android Studio JDK 21 explicitly; the ordinary Java-home
selector advertises JDK 17. The SDK and dependencies were already cached.

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/bobrossjr/Library/Android/sdk'
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p /private/tmp/meow-build119-jvm-only-native
bash scripts/verify_avatar_forms.sh \
  --offline --no-daemon --no-configuration-cache --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' \
  -I docs/characters/verification/build119_low_memory.gradle \
  -Dorg.gradle.java.installations.auto-download=false \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PincludeMwdat=false \
  -PprebuiltDebugNativeDir=/private/tmp/meow-build119-jvm-only-native
```

The empty native directory disables CMake for this JVM-only run. It contains
no production JNI libraries and must never be used to assemble an installable
APK. Robolectric native rendering is covered; the phone's graphics driver and
the actual native inference runtime are not. Meow packaging must use its
established real prebuilt native directory and protected runtime assets.

On a nearly full disk, use the retained task-owned monitor rather than running
the above command unattended. Its saved Python source and exact invocation are
alongside the final log. It checks free space every second and terminates only
its own process group below 600 MiB. The earlier monitored stop was a resource
abort, not an assertion failure. Avoid broad cache deletion or terminating
other agents' processes.

## Evidence and future debugging

- `build-out/habitat/build119/local-verification-low-memory/` retains the first
  successful 235-test run, XML hashes and eight renderer/setup previews.
- `build-out/habitat/build119/local-verification-final/` retains the corrected
  acceptance run, its exact source commit and command, log, XML, previews and
  SHA-256 evidence. These ignored local artifacts are not uploaded publicly.
- When selecting tests, inspect class declarations, not filenames. Compare
  every selector with XML suite names and count actual testcase elements.
- The repaired `AgentViewModelTest` speech assertions now pass with the scoped
  Android clock and joined ViewModel shutdown. Keep the real speech assertions;
  do not weaken them to hide an unmocked Android API.
- Cached platform-specific Gradle artifacts may live under `*-jvm` or
  `*-android` coordinates. A missing generic JAR does not prove the dependency
  is unavailable. Use an offline build to verify the actual resolved variants.
- Existing local LM Studio state provided only an embedding model and no
  running text-generation endpoint. No model was downloaded, no paid fallback
  was used and no server settings changed. Avoid `lms ps` for strictly passive
  checks: it may try to wake its service. The attempted wake was interrupted.

Meow's existing live model previously returned no content before its deadlines;
neither deterministic provider tests nor these example renders establish live
generation success. The latest teaching files must still be delivered through
the authorized private Git/SSH path when Meow is reachable. Do not report that
delivery, a signed final archive or phone installation before it happens.
