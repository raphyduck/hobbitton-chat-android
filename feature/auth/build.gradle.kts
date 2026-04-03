plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.librechat.android.feature.auth"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
        }
        androidMain.dependencies {
            implementation(libs.browser)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.test)
        }
    }
}
