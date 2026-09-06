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
// Keep package-download credentials on the build machine, not in BuildConfig,
// Android resources, or the repository. An external file survives fresh release
// checkouts on Meow. Blank values must not hide a configured fallback.
val mwdatLocalProperties = java.util.Properties().apply {
    val source = file("local.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
val mwdatPropertiesPath = providers.gradleProperty("mwdatProperties").orNull
    ?: providers.environmentVariable("MWDAT_PROPERTIES_FILE").orNull
val mwdatPropertiesFile = file(mwdatPropertiesPath ?: "mwdat.properties")
check(mwdatPropertiesPath == null || mwdatPropertiesFile.isFile) {
    "MWDAT configuration file does not exist. Check mwdatProperties / MWDAT_PROPERTIES_FILE."
}
val mwdatProperties = java.util.Properties().apply {
    if (mwdatPropertiesFile.isFile) mwdatPropertiesFile.inputStream().use { load(it) }
}
fun mwdatSetting(environmentName: String, propertyName: String): String =
    sequenceOf(providers.environmentVariable(environmentName).orNull,
        mwdatProperties.getProperty(propertyName), mwdatLocalProperties.getProperty(propertyName))
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

val mwdatGithubToken = mwdatSetting("GITHUB_TOKEN", "github_token")
val mwdatEnabled = providers.gradleProperty("includeMwdat").orNull?.let {
    it.toBooleanStrictOrNull() ?: error("includeMwdat must be true or false")
} ?: mwdatGithubToken.isNotBlank()
val mwdatApplicationId = mwdatSetting("MWDAT_APPLICATION_ID", "mwdat_application_id")
val mwdatClientToken = mwdatSetting("MWDAT_CLIENT_TOKEN", "mwdat_client_token")
// Only the SDK's app-attestation values belong in the installed app. Never
// forward the GitHub token to :app; GitHub alone validates its format/scope.
gradle.extra["mwdat.enabled"] = mwdatEnabled
gradle.extra["mwdat.hasGithubToken"] = mwdatGithubToken.isNotBlank()
gradle.extra["mwdat.applicationId"] = mwdatApplicationId.ifBlank { "0" }
gradle.extra["mwdat.clientToken"] = mwdatClientToken.ifBlank { "0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Meta requires a GitHub classic PAT with read:packages for its Maven
        // repository. Do not substitute an app runtime API key or a mock SDK.
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = ""
                password = mwdatGithubToken
            }
            content { includeGroup("com.meta.wearable") }
        }
    }
}

rootProject.name = "Meowmba"
include(":app")
