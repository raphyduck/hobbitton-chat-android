# Build Logic

Convention plugins that apply consistent Gradle configuration across all modules.

## Convention Plugins (7 total)

| Plugin ID | Class | What it does |
|-----------|-------|-------------|
| `librechat.android.application` | `AndroidApplicationConventionPlugin` | AGP application plugin, compileSdk 35, minSdk 26, JDK 17 |
| `librechat.android.library` | `AndroidLibraryConventionPlugin` | AGP library plugin, same SDK/JDK config |
| `librechat.android.compose` | `AndroidComposeConventionPlugin` | Compose compiler, Compose BOM, Material 3 |
| `librechat.android.koin` | `AndroidKoinConventionPlugin` | Koin core + Android dependencies |
| `librechat.android.feature` | `AndroidFeatureConventionPlugin` | Auto-applies: library + compose + koin + serialization |
| `librechat.android.room` | `AndroidRoomConventionPlugin` | Room + KSP, schema export config |
| `librechat.kotlin.serialization` | `KotlinSerializationConventionPlugin` | Kotlinx Serialization plugin + JSON dependency |

## Critical: apply() Signature

Convention plugin `apply()` MUST use:
```kotlin
override fun apply(target: Project) {
    with(target) { ... }
}
```
Do NOT use `override fun apply(target: Project) = with(target) { ... }` -- that returns
the `with` result instead of `Unit`, which causes a Gradle error.

## Version Catalog

All dependency versions live in `gradle/libs.versions.toml`. Never hardcode versions in
build.gradle.kts files. Use `libs.` accessors (e.g., `libs.koin.android`, `libs.ktor.client.core`).

## Feature Module Convention

Feature modules use `id("librechat.android.feature")` which auto-applies library, compose,
koin, and serialization. They only need to add feature-specific dependencies.
Feature modules depend on `:core:*` only, never on other feature modules.
