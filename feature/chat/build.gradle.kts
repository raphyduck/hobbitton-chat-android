plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.chat"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)
    implementation(libs.fuzzywuzzy)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)
}
