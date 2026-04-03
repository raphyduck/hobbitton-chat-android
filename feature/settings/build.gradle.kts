plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.librechat.android.feature.settings"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
