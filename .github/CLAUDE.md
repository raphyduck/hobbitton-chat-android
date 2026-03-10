# GitHub CI/CD

## Workflow: Android CI

File: `.github/workflows/android.yml`

### Triggers

- Push to `main` or `develop`
- Pull requests targeting `main`

### Pipeline Steps

1. Checkout code
2. Set up JDK 17 (Temurin)
3. Setup Gradle with caching (`~/.gradle/caches` and `~/.gradle/wrapper`)
4. `./gradlew lint` -- Android lint checks
5. `./gradlew test` -- unit tests across all modules
6. `./gradlew assembleDebug` -- build debug APK
7. Upload `app/build/outputs/apk/debug/app-debug.apk` as artifact (named `debug-apk`)

### Environment

- Runs on `ubuntu-latest`
- Gradle cache key based on `**/*.gradle*` and `gradle-wrapper.properties`

### Notes

- No release signing configured yet (debug builds only)
- No instrumented/UI tests in CI (only unit tests)
- APK artifact is available for download from the Actions run summary
