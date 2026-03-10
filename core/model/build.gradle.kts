plugins {
    id("librechat.android.library")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.librechat.android.core.model"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    // compose-runtime is needed for @Immutable annotation on model classes,
    // enabling proper skip logic in the Compose compiler.
    implementation("androidx.compose.runtime:runtime:1.7.6")
}
