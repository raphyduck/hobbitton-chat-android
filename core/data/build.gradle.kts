plugins {
    id("librechat.android.library")
    id("librechat.android.koin")
    id("librechat.android.room")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.librechat.android.core.data"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.bundles.room)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
}
