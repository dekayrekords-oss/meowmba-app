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
        applicationId = "com.example.meowmba"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    }

    buildTypes {
        release {
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
    buildFeatures {
        compose = true
        buildConfig = true
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
    // SSH client (mwiede fork is the maintained jsch).
    implementation("com.github.mwiede:jsch:0.2.21")

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
    implementation(libs.aicore.edge)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.face.detection)
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