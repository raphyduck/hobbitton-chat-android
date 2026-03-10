plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.auth"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.browser)
    testImplementation(libs.kotlinx.serialization.json)
}
