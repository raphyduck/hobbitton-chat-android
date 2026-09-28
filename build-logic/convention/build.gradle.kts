plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "librechat.mobile.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "librechat.mobile.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidKoin") {
            id = "librechat.mobile.koin"
            implementationClass = "AndroidKoinConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "librechat.kotlin.serialization"
            implementationClass = "KotlinSerializationConventionPlugin"
        }
        register("detekt") {
            id = "librechat.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("kmpLibrary") {
            id = "librechat.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "librechat.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpKoin") {
            id = "librechat.kmp.koin"
            implementationClass = "KmpKoinConventionPlugin"
        }
        register("kmpFeature") {
            id = "librechat.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
