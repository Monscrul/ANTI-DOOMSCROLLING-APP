// AGP 9 supplies Kotlin via its built-in Kotlin support: the
// `org.jetbrains.kotlin.android` plugin must NOT be applied anywhere.
// Pure-JVM modules use `org.jetbrains.kotlin.jvm`, pinned to the Kotlin
// version bundled with AGP (see gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
