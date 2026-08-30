plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.tasks"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(libs.kermit)
            // Compose-native markdown for mission prose. Same renderer the chat uses
            // (com.mikepenz), pulled in directly so this module doesn't depend on
            // :feature:chat (features depend on :core:* only) — the :feature:skills precedent.
            implementation(libs.markdown.renderer.m3)
        }
    }
}
