plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.auth"

    // Screen captures (Robolectric + Roborazzi, test-only): the sign-in rendered without a device.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // `-Pcaptures` records the PNGs under build/captures (the Roborazzi library alone, no plugin).
        unitTests.all { it.systemProperty("roborazzi.test.record", if (project.hasProperty("captures")) "true" else "false") }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            // The portal sign-in (D-076) logs its outcome; declared, not borrowed transitively.
            implementation(libs.kermit)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
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
