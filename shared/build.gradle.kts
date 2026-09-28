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
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3.kmp)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            implementation(libs.coil3.svg)
        }
    }
}

