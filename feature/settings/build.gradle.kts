plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.settings"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.coil.compose)
    implementation(libs.timber)
}
