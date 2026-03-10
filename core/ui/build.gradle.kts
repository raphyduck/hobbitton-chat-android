plugins {
    id("librechat.android.library")
    id("librechat.android.compose")
}

android {
    namespace = "com.librechat.android.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coil.compose)
}
