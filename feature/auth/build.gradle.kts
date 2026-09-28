plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.auth"
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
        }
    }
}
