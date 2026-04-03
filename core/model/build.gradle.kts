plugins {
    id("librechat.kmp.library")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.librechat.android.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
