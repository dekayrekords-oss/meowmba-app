pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // foojay-resolver-convention disabled for the local build: it is unreachable
    // from this machine (plugins-artifacts.gradle.org). The local JDK 17 satisfies
    // the project's Java 11 target, so no toolchain auto-provisioning is needed.
    // id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Meta Wearables DAT (Ray-Ban glasses). Needs github_token (read:packages)
        // in local.properties — builds without one simply skip the mwdat source set.
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = ""
                password = java.util.Properties().apply {
                    val f = File(rootDir, "local.properties")
                    if (f.exists()) f.inputStream().use { load(it) }
                }.getProperty("github_token") ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
            content { includeGroup("com.meta.wearable") }
        }
    }
}

rootProject.name = "Meowmba"
include(":app")
