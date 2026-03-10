plugins {
    id("librechat.android.feature")
}

android {
    namespace = "com.librechat.android.feature.agents"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    implementation(libs.timber)
}
