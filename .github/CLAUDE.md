# GitHub CI/CD

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
- `./gradlew :feature:chat:assembleDebugAndroidTest` — compile-only gate for the chat instrumented
  suite, which *runs* on a local emulator (`connectedDebugAndroidTest`), never in CI. Scoped to the
  one module deliberately: a repo-wide `assembleDebugAndroidTest` would also compile `:app`'s stale
  `androidTest` sources.
- Uploads the debug APK as an artifact (90-day retention) and posts its download link to the PR

### Environment

- `lint` + `test` + `android` jobs: `ubuntu-latest`, JDK 21
- Concurrency: cancels in-progress runs for same branch

### Notes

- No release signing configured yet
- Instrumented/UI tests are never *executed* in CI (no emulator) — `:feature:chat`'s suite is only
  compiled, as a gate. Executed test coverage in CI is unit tests.
- The debug APK is uploaded as an artifact
- Detekt SARIF is uploaded to GitHub Code Scanning (requires GitHub Advanced Security for private repos)
