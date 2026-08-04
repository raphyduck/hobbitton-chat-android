# GitHub CI/CD

## Workflow: App Store Upload

File: `.github/workflows/appstore.yml`

- **Manual only** (`workflow_dispatch`) with a required `tag` input (e.g. `v2026.07.3`) —
  cutting a GitHub release never uploads to Apple.
- Single `upload` job: `macos-latest`, `environment: release` (reviewer-gated secrets).
- Xcode **cloud signing** via an App Store Connect API key — no certificates or profiles
  stored anywhere; `-allowProvisioningUpdates` manages them in Apple's cloud.
- Archives the tagged commit, then `xcodebuild -exportArchive` with
  `method: app-store-connect`, `destination: upload` uploads during export (no altool/fastlane).
- Passes `IOS_BUILD_NUMBER_SUFFIX` (run_number×100 + run_attempt) so the "Stamp Version"
  build phase emits a per-upload-unique `CFBundleVersion` (`YYYYMMPP.N`).
- Secrets (in the `release` environment): `ASC_API_KEY_P8_BASE64`, `ASC_API_KEY_ID`,
  `ASC_API_ISSUER_ID`, `APPLE_TEAM_ID`. Setup runbook: `docs/RELEASING.md`.

## Workflow: CI

File: `.github/workflows/ci.yml`

### Triggers

- Push to `develop`
- Pull requests targeting `develop`

### Jobs

#### `lint`
- `./gradlew detekt detektMetadataCommonMain :app:lint --continue`
- Uploads merged detekt SARIF to GitHub Code Scanning + lint HTML report

#### `test`
- `./gradlew test`
- Uploads test results XML

#### `android` (Build Android App)
- `./gradlew :app:assembleDebug`

#### `ios` (Build iOS App)
- Selects latest stable Xcode
- Caches `~/.konan`
- Runs `xcodebuild` for the iOS simulator (arm64-only, no signing). The Kotlin framework is built by the Xcode "Compile Kotlin Framework" build phase (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`) — no separate link step.

### Environment

- `lint` + `test` + `android` jobs: `ubuntu-latest`, JDK 21
- `ios` job: `macos-latest` (Apple Silicon — required since there's no `iosSimulatorX64` target), JDK 21
- Concurrency: cancels in-progress runs for same branch

### Notes

- No release signing configured yet
- No instrumented/UI tests in CI (only unit tests)
- APK / iOS app are built but NOT uploaded as artifacts
- Detekt SARIF is uploaded to GitHub Code Scanning (requires GitHub Advanced Security for private repos)
