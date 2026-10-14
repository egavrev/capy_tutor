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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CapyTutor"

include(":core", ":ai", ":testbench")
project(":testbench").projectDir = File(settingsDir, "tools/testbench")

// The Android modules need the Android SDK. CI always has it. On a Mac without it (no Android
// Studio), only the plain-Kotlin modules are built: enough to run the test bench with just Java 17.
val localProperties = java.util.Properties().apply {
    File(settingsDir, "local.properties").takeIf { it.isFile }?.reader()?.use { load(it) }
}
val androidSdk = listOfNotNull(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
    localProperties.getProperty("sdk.dir"),
).map { File(it) }.firstOrNull { it.isDirectory }

if (androidSdk != null) {
    include(":app", ":data")
} else {
    org.gradle.api.logging.Logging.getLogger("capy").lifecycle(
        "Android SDK not found: building core, ai and testbench only (the app itself is built on GitHub Actions).",
    )
}
