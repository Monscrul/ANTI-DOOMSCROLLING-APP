plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:model"))
    implementation(project(":tracking:api"))
}
