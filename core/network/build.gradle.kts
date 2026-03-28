plugins {
    id("librechat.android.library")
    id("librechat.android.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.librechat.android.core.network"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
}
