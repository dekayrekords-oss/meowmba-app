import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Drop javalite everywhere so it cannot collide with the full protobuf runtime
// added below. Keeping both would duplicate com.google.protobuf.* classes and
// fail dexing outright.
configurations.configureEach {
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
}

// Release signing. Reads keystore.properties (gitignored) from the project
// root when present. Release builds never fall back to a debug certificate.
// Example keystore.properties:
//   storeFile=/path/to/meowmba-release.keystore
//   storePassword=...
//   keyAlias=meowmba
//   keyPassword=...
val keystorePropsFile = rootProject.file(
    providers.gradleProperty("releaseSigningProperties").orNull ?: "keystore.properties"
)
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
val hasCompleteReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
    !keystoreProps.getProperty(it).isNullOrBlank()
}

// Debug development without glasses may omit the SDK. Release builds below
// fail explicitly instead of silently shipping a broken glasses card.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasMwdat = gradle.extra["mwdat.enabled"] as Boolean
val hasMwdatDownloadToken = gradle.extra["mwdat.hasGithubToken"] as Boolean
val mwdatApplicationId = gradle.extra["mwdat.applicationId"] as String
val mwdatClientToken = gradle.extra["mwdat.clientToken"] as String

// Debug signing: `adb install -r` only replaces the on-device build if the
// signature matches, and the phone's build was signed with meow's debug key —
// any other debug key makes every install fail with UPDATE_INCOMPATIBLE and
// the only way out is an uninstall that wipes Room. Point debugKeystore at
// that key in local.properties (gitignored); without it the default
// per-machine debug keystore is used, as before.
val debugKeystorePath = (localProps.getProperty("debugKeystore") ?: "").trim()
// For a debug-only device test of Kotlin/UI changes, reuse the unchanged JNI
// bridges from a verified previous APK. Release always uses the pinned sources.
val prebuiltDebugNativeDir = providers.gradleProperty("prebuiltDebugNativeDir").orNull
println(
    when {
        hasMwdat -> "MWDAT (Meta glasses SDK): ENABLED"
        else -> "MWDAT: disabled for debug development; release requires the SDK. See docs/META_GLASSES_SETUP.md"
    }
)

android {
    namespace = "com.example.meowmba"
    compileSdk = 37

    ndkVersion = "27.0.12077973"  // match whatever Task 1 confirmed available

    if (prebuiltDebugNativeDir == null) externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        // Verified against the existing Play Console app. The debug variant
        // retains com.example.meowmba below, preserving its installed data.
        applicationId = "com.meow.meowmba"
        minSdk = 31
        targetSdk = 36
        // Bumped from 20 for the appearance upgrade: the phone is already on
        // versionCode 20 (1.3.16), so an install at 20 would not register as an
        // upgrade and preserving app data would be a coin flip. 21 > 20 makes it
        // a genuine in-place upgrade that keeps the Room database and models.
        versionCode = 44
        versionName = "1.3.40"

        buildConfigField("boolean", "HAS_MWDAT_SDK", hasMwdat.toString())
        manifestPlaceholders["mwdat_application_id"] = mwdatApplicationId
        manifestPlaceholders["mwdat_client_token"] = mwdatClientToken

        ndk {
            abiFilters += "arm64-v8a"
            debugSymbolLevel = "SYMBOL_TABLE"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_PLATFORM=android-31",
                    "-DCMAKE_C_FLAGS=-march=armv8.7a",
                    "-DCMAKE_CXX_FLAGS=-march=armv8.7a",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
                abiFilters += "arm64-v8a"
                providers.gradleProperty("nativeNeedleLibrary").orNull?.let {
                    arguments += "-DNEEDLE_LIBRARY=$it"
                }
                providers.gradleProperty("nativeSdSource").orNull?.let {
                    arguments += "-DFETCHCONTENT_SOURCE_DIR_STABLE_DIFFUSION_CPP=$it"
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            if (hasCompleteReleaseSigning) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        if (debugKeystorePath.isNotBlank() && file(debugKeystorePath).exists()) {
            getByName("debug") {
                storeFile = file(debugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Shrink + obfuscate for Play. JNI / Room / MediaPipe kept via
            // src/main/keepRules/rules.keep.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep",
            )
            optimization {
                enable = false
            }
            // An explicitly requested unsigned candidate can be inspected,
            // but can never be mistaken for a signed Play upload.
            signingConfig = if (hasCompleteReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Meta Wearables DAT (Ray-Ban glasses). Compiles the real SDK bridge only
    // when configured in settings.gradle.kts. verifyMwdatReleaseConfiguration
    // prevents the tokenless development variant from becoming a Play build.
    sourceSets {
        if (prebuiltDebugNativeDir != null) getByName("debug").jniLibs.srcDir(prebuiltDebugNativeDir)
        getByName("main") {
            if (hasMwdat) {
                java.srcDir("src/mwdat/java")
                kotlin.srcDir("src/mwdat/java")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true

    // MediaPipe / LiteRT AARs ship every ABI. We only ship arm64 (the Fold).
    // Without this the Play APK is ~390 MB and over the 200 MB cap.
    packaging {
        jniLibs {
            // Uncompressed + 16 KB zip-aligned — Play rejects 4 KB-only ELF
            // alignment on targetSdk 36 devices.
            useLegacyPackaging = false
            excludes += listOf(
                "**/lib/x86/**",
                "**/lib/x86_64/**",
                "**/lib/armeabi-v7a/**",
                "lib/x86/**",
                "lib/x86_64/**",
                "lib/armeabi-v7a/**",
                // Optional GPU helper; CPU image-gen still works. ~10 MB, gets us
                // under Play's 200 MB install cap.
                "**/libimagegenerator_gpu.so",
            )
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        // Fold still has the old id. Release / Play stays com.meow.meowmba.
        variant.applicationId.set("com.example.meowmba")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Unit tests run on a virtual coroutine clock: the forever-loop inside
// IdleAnimationDirector would spin forever there, so keep it off for the
// whole test task. IdleBehaviorTest re-enables it explicitly.
tasks.withType<Test>().configureEach {
    systemProperty("meowmba.idleAnimations", "false")
    if (providers.gradleProperty("testProgress").orNull == "true") testLogging.events("started", "failed", "skipped")

    // The full suite died with OutOfMemoryError twice under the old defaults
    // (512m worker heap, no JVM recycling). Raising the heap alone did NOT fix
    // it — it still died at 3g — because Robolectric retains an SDK classloader
    // per test class, so the heap climbs monotonically across the run and never
    // gives anything back. The fix is to recycle the worker JVM after every test
    // class: slower, but each JVM then only ever holds one class's worth of
    // Robolectric state.
    //
    // One worker at a time, holding a large heap. Two concurrent workers made
    // the OOM worse, not better: the host was already swapping (5.4 of 6.1 GB
    // swap in use) while this ran, so total footprint matters more than
    // throughput. The daemon is trimmed to 4g in gradle.properties to leave the
    // worker room.
    maxHeapSize = "6g"
    maxParallelForks = 1

    // forkEvery = 1 (recycling the worker JVM after every test class) did stop
    // the OutOfMemoryError, but it made the suite unusably slow: zero results
    // written in 20 minutes, because each of the ~118 Robolectric cold starts
    // re-instruments the whole SDK on a host that was already swapping.
    //
    // Recycling is therefore OFF by default. Re-enable it (forkEvery = 1) only if
    // the suite OOMs again on a machine that actually has free RAM — it trades a
    // lot of wall-clock for a bounded heap.
    // forkEvery = 1

    // Deliberately NO -XX:+HeapDumpOnOutOfMemoryError here: on the first OOM it
    // wrote a 5.0 GB dump into app/build/ and nearly filled the disk, and the
    // dump told us nothing that forkEvery does not already address.
}

val verifyReleaseSigningConfiguration = tasks.register("verifyReleaseSigningConfiguration") {
    // Capture values at configuration time; task execution must not access
    // the Project/script object (configuration-cache compatibility).
    val allowUnsigned = providers.gradleProperty("allowUnsignedRelease").orNull == "true"
    val hasSigningFile = keystorePropsFile.exists()
    val completeSigning = hasCompleteReleaseSigning
    val releaseKeyFile = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { file(it) }
    doLast {
        check(hasSigningFile || allowUnsigned) {
            "Release signing is missing. Configure keystore.properties, or use -PallowUnsignedRelease=true for an unsigned validation artifact."
        }
        if (hasSigningFile) {
            check(completeSigning) { "keystore.properties is incomplete" }
            check(releaseKeyFile?.isFile == true) { "Release keystore does not exist" }
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigningConfiguration)
    doFirst { check(prebuiltDebugNativeDir == null) { "Release cannot use prebuiltDebugNativeDir. Build the pinned native sources." } }
}

// These gitignored runtimes must be restored in every fresh release checkout.
// Downloadable voice models cannot replace missing native executable libraries.
val verifyVoiceReleaseConfiguration = tasks.register("verifyVoiceReleaseConfiguration") {
    val requiredLibraries = listOf("libsherpa-onnx-jni.so", "libonnxruntime.so")
        .map { layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/$it").asFile }
    doLast {
        val missing = requiredLibraries.filter { !it.isFile || it.length() == 0L }
        check(missing.isEmpty()) {
            "Missing native voice runtimes: ${missing.joinToString { it.name }}. Run scripts/fetch_kws_assets.sh before building a release."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyVoiceReleaseConfiguration)
}

// Check the packaged artifact as well as the inputs, catching packaging omissions
// and incompatible ELF alignment before the bundle can be handed off to Play.
val verifyReleaseBundle = tasks.register<Exec>("verifyReleaseBundle") {
    commandLine("python3", rootProject.file("scripts/check_release_bundle.py").absolutePath,
        layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile.absolutePath)
}
tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy(verifyReleaseBundle)
}

val verifyMwdatSdkConfiguration = tasks.register("verifyMwdatSdkConfiguration") {
    val included = hasMwdat
    val tokenPresent = hasMwdatDownloadToken
    doLast {
        check(included && tokenPresent) {
            "Meta glasses SDK is missing. Configure a GitHub classic PAT with read:packages in an external mwdat.properties file, then build with -PincludeMwdat=true -PmwdatProperties=/absolute/path/mwdat.properties. See docs/META_GLASSES_SETUP.md."
        }
    }
}
val verifyMwdatReleaseConfiguration = tasks.register("verifyMwdatReleaseConfiguration") {
    dependsOn(verifyMwdatSdkConfiguration)
    val registeredApp = mwdatApplicationId != "0" && mwdatClientToken != "0"
    doLast {
        check(registeredApp) {
            "Meta glasses release registration is missing. Set mwdat_application_id and mwdat_client_token from the Wearables Developer Center. Zero values are for debug Developer Mode only. See docs/META_GLASSES_SETUP.md."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyMwdatReleaseConfiguration)
}
if (hasMwdat) {
    tasks.matching { it.name == "preDebugBuild" }.configureEach {
        dependsOn(verifyMwdatSdkConfiguration)
    }
}

dependencies {
    if (hasMwdat) {
        implementation("com.meta.wearable:mwdat-core:0.8.0")
        implementation("com.meta.wearable:mwdat-camera:0.8.0")
    }
    // SSH client (mwiede fork is the maintained jsch).
    implementation("com.github.mwiede:jsch:0.2.21")
    // Unpack the sherpa KWS tar.bz2 on first launch (Play cannot ship it in the AAB).
    implementation("org.apache.commons:commons-compress:1.27.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mediapipe.tasks.vision.image.generator)
    // tasks-vision-image-generator ships code compiled against FULL protobuf
    // (its Any$Builder.build() returns Any), but every MediaPipe artifact
    // declares protobuf-javalite, where that method does not exist. The result
    // is a runtime NoSuchMethodError the moment image generation starts:
    //   No virtual method build()Lcom/google/protobuf/Any;
    //     in class Lcom/google/protobuf/Any$Builder;
    // protobuf-java contains GeneratedMessageLite as well as GeneratedMessage,
    // so swapping the runtime satisfies the LLM path too.
    implementation(libs.protobuf.java)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.litert.lm.android)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.pose.detection)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit)
    // Real org.json for unit tests. The android.jar stub throws
    // "Method put in org.json.JSONArray not mocked", so anything that persists JSON
    // (VoiceProfileStore) is untestable without it.
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockitoCore)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
