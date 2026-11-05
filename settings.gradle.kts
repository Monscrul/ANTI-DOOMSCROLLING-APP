import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
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

rootProject.name = "Undertow"

include(
    ":app",

    // Pure-Kotlin domain and counting logic (no Android dependencies).
    ":core:model",
    ":core:common",

    // Storage
    ":core:database",
    ":core:datastore",

    // Shared UI
    ":core:designsystem",

    // Tracking pipeline
    ":tracking:api",
    ":tracking:engine",
    ":tracking:usagestats",
    ":tracking:accessibility",

    // Repositories / orchestration
    ":core:data",

    // Features
    ":feature:onboarding",
    ":feature:home",
    ":feature:stats",
    ":feature:limits",
    ":feature:settings",
)
