plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.tasks"

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
            implementation(project(":core:network"))
            implementation(libs.kermit)
            // Compose-native markdown for mission prose. Same renderer the chat uses
            // (com.mikepenz), pulled in directly so this module doesn't depend on
            // :feature:chat (features depend on :core:* only) — the :feature:skills precedent.
            implementation(libs.markdown.renderer.m3)
            // Renders a message's attached photos from their data URLs (Coil ships a DataUriFetcher).
            implementation(libs.coil3.compose)
        }
        androidMain.dependencies {
            // The photo picker's activity-result launchers.
            implementation(libs.activity.compose)
        }
        commonTest.dependencies {
            // The ViewModel delegates' tests drive their coroutines with runTest.
            implementation(libs.coroutines.test)
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
