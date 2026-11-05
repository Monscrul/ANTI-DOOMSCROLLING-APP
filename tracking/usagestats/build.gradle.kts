plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.undertow.tracking.usagestats"
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
    implementation(project(":tracking:api"))
}
