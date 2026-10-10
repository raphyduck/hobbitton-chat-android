plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.compose")
    id("librechat.kmp.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.garfiec.librechat.shared"

    // Screen captures (Robolectric + Roborazzi, test-only): the screens rendered without a device.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // `-Pcaptures` records the PNGs under build/captures (the Roborazzi library alone, no plugin).
        unitTests.all { it.systemProperty("roborazzi.test.record", if (project.hasProperty("captures")) "true" else "false") }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:network"))
            api(project(":core:data"))
            api(project(":core:ui"))
            implementation(project(":core:logging"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:tasks"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3.kmp)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.compose.ui.test.junit4.versioned)
        }
    }
}

dependencies {
    // The test activity the compose rule launches, visible to Robolectric's merged manifest.
    debugImplementation(libs.compose.ui.test.manifest)
}
