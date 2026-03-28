plugins {
    id("librechat.android.library")
    id("librechat.android.koin")
}

android {
    namespace = "com.librechat.android.core.common"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
