plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.conversations"
}

dependencies {
    implementation(libs.timber)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.kotlinx.serialization.json)
}
