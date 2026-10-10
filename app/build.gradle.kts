plugins {
    id("librechat.mobile.application")
    id("librechat.mobile.compose")
    id("librechat.mobile.koin")
    id("librechat.kotlin.serialization")
}

android {
    // Namespace stays upstream's: it names Kotlin classes (BuildConfig, R, and the
    // MainActivity), not the installed app.
    // The installed identity is applicationId below.
    namespace = "com.garfiec.librechat"

    defaultConfig {
        // Install identity of this deployment. Changing it on a fleet that already
        // has the app publishes a *different* application: existing installs don't
        // update, they coexist. Fixed from here on.
        applicationId = "at.hobbitton.chat"
    }

    buildTypes {
        debug {
            // Release must keep the bare id — Obtainium tracks updates by package name.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:tasks"))

    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.navigation3.ui.kmp)
    implementation(libs.bundles.lifecycle)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor)
    implementation(libs.coil3.svg)
    implementation(libs.kermit)

    debugImplementation(libs.leakcanary)
}
