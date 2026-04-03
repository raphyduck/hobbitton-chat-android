plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

dependencies {
    subprojects.forEach { subproject ->
        if (subproject.buildFile.exists()) {
            kover(subproject)
        }
    }
}
