plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.b1tsagar.liquiwin.core.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// Test helpers shared by other modules' tests. Only ever a testImplementation dependency.
dependencies {
    api(project(":core:tiles"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.turbine)
}
