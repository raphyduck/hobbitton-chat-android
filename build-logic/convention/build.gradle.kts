plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "librechat.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "librechat.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "librechat.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "librechat.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "librechat.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidKoin") {
            id = "librechat.android.koin"
            implementationClass = "AndroidKoinConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "librechat.kotlin.serialization"
            implementationClass = "KotlinSerializationConventionPlugin"
        }
    }
}
