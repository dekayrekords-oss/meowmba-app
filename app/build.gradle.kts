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
// root when present; fresh checkouts fall back to the debug keystore so the
// build never breaks. Example keystore.properties:
//   storeFile=/path/to/meowmba-release.keystore
//   storePassword=...
//   keyAlias=meowmba
//   keyPassword=...
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(keystorePropsFile.inputStream())
}

// MWDAT (Meta glasses SDK) gating — same conditional-compile pattern as
// meta-memory-app so a tokenless checkout still builds clean.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val githubToken = (localProps.getProperty("github_token") ?: System.getenv("GITHUB_TOKEN") ?: "").trim()

// Debug signing: `adb install -r` only replaces the on-device build if the
// signature matches, and the phone's build was signed with meow's debug key —
// any other debug key makes every install fail with UPDATE_INCOMPATIBLE and
// the only way out is an uninstall that wipes Room. Point debugKeystore at
// that key in local.properties (gitignored); without it the default
// per-machine debug keystore is used, as before.
val debugKeystorePath = (localProps.getProperty("debugKeystore") ?: "").trim()
val hasMwdat = githubToken.isNotBlank() &&
    Regex("^(ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{22,255})$").matches(githubToken)
println(
    when {
        hasMwdat -> "MWDAT (Meta glasses SDK): ENABLED"
        githubToken.isBlank() -> "MWDAT: disabled — add github_token to local.properties"
        else -> "MWDAT: disabled — github_token format not recognized"
    }
)

android {
    namespace = "com.example.meowmba"
    compileSdk = 37

    ndkVersion = "27.0.12077973"  // match whatever Task 1 confirmed available

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        // Kept on the legacy id on purpose: phones (and their Room data) are
        // installed under com.example.meowmba; switching ids orphans that data.
        applicationId = "com.example.meowmba"
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "1.3.10"

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
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
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
            // Real release keystore when configured, debug key otherwise
            // (same behaviour as before this block existed).
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Meta Wearables DAT (Ray-Ban glasses). Compiles the real SDK bridge only
    // when local.properties carries a valid github_token; without one the
    // feature degrades honestly ("no MWDAT SDK in this build") like MetaMemory.
    sourceSets {
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
}

dependencies {
    if (hasMwdat) {
        implementation("com.meta.wearable:mwdat-core:0.8.0")
        implementation("com.meta.wearable:mwdat-camera:0.8.0")
        implementation("com.meta.wearable:mwdat-mockdevice:0.8.0")
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}