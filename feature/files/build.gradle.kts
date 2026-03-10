plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.files"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.coil.compose)
    implementation(libs.timber)
}
