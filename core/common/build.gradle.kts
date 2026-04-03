plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.koin")
}

android {
    namespace = "com.garfiec.librechat.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
