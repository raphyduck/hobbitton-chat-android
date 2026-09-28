plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.compose")
    id("librechat.kmp.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.garfiec.librechat.shared"
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
    }
}

