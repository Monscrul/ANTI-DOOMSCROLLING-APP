plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // DateKey is part of this module's public API (DailySummary.dateKey, ScrollSession.dateKey).
    api(project(":core:common"))

    testImplementation(libs.junit)
}
