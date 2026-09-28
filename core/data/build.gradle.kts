plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.garfiec.librechat.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:logging"))
            implementation(libs.datastore.preferences)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.security.crypto)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
