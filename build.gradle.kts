plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Not applied anywhere: AGP compiles Kotlin itself. Declared only to pin the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Robolectric's SDK 36 runtime sets raw file descriptors through jdk.internal.access, which JDK 17+ doesn't export.
subprojects {
    tasks.withType<Test>().configureEach { jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
}
