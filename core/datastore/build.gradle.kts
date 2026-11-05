plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.undertow.core.datastore"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))

    // Exposed in the repository's public constructor so tests can inject a file-backed store.
    api(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
