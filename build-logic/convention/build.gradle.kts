plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
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
        register("androidDetekt") {
            id = "librechat.android.detekt"
            implementationClass = "AndroidDetektConventionPlugin"
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
        register("kmpRoom") {
            id = "librechat.kmp.room"
            implementationClass = "KmpRoomConventionPlugin"
        }
        register("kmpFeature") {
            id = "librechat.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
