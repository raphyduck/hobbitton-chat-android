# LibreChat iOS App

Compose Multiplatform iOS client for LibreChat, sharing business logic and UI with the Android app via Kotlin Multiplatform.

## Project Structure

```
iosApp/
  iosApp/
    iOSApp.swift               — SwiftUI App entry point (initializes Koin DI)
    RootView.swift              — Auth state router (login vs chat)
    ComposeView.swift           — CMP UIViewController wrapper
    LoginView.swift             — Server URL + email/password login screen
    RegisterView.swift          — Account registration screen
    ForgotPasswordView.swift    — Password reset screen
    FilesView.swift             — File management screen
    ChatStreamView.swift        — SSE streaming display with stream controls
    KoinHelper.swift            — Swift-side Koin dependency resolver
    ContentView.swift           — Placeholder view (for Previews)
    SharedFrameworkTest.swift   — Compile-time smoke test for KMP + SKIE bridging
    Info.plist                  — Bundle config (ID: com.garfiec.librechat.ios, URL scheme: librechat)
```

## Architecture

- **Shared Framework**: The `:shared` Gradle module exports `core:common`, `core:model`, `core:network`, and `core:data` as a single `Shared.framework` for iOS. All feature modules and `core:ui` are included for Compose Multiplatform screen sharing.
- **SKIE**: Enhances Kotlin→Swift interop automatically:
  - `sealed interface StreamEvent` → Swift exhaustive enum via `onEnum(of:)`
  - `Flow<T>` → `AsyncSequence` (e.g., SSE streaming, connectivity observer)
  - `suspend fun` → `async throws` (e.g., `sdk.login()`)
- **DI**: Koin is initialized in `iOSApp.init()` via `IosKoinHelperKt.startIosKoin()`
- **Crash Reporting**: `installCrashReporting()` sets up a Kotlin/Native unhandled exception hook that logs via Kermit + NSLog and raises as NSException for readable iOS crash logs.
- **Platform Impls**:
  - `IosTokenDataStore` (`core/data/src/iosMain/`) — Keychain-backed token storage via Security.framework
  - `ServerDataStore` (`core/data/src/commonMain/`) — DataStore-backed server URL (shared with Android)
  - `IosConnectivityObserver` (`core/common/src/iosMain/`) — NWPathMonitor via C-level `nw_path_monitor_*` APIs
  - `IosSharedModule` (`shared/src/iosMain/`) — Koin module wiring Darwin Ktor engine + all platform deps

## Building

### Prerequisites
- Xcode 15.0+ with iOS 16.0+ SDK
- JDK 17 (for Gradle/Kotlin compilation)

### Build the Shared Framework
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Open in Xcode
```bash
open iosApp/iosApp.xcodeproj
```

Build with scheme `iosApp` targeting an iOS Simulator. The Gradle build phase in the Xcode project automatically builds the shared framework.

## Info.plist Permissions

The following keys are configured in `Info.plist`:
- `NSMicrophoneUsageDescription` — Microphone for voice input
- `NSSpeechRecognitionUsageDescription` — Speech-to-text
- `NSCameraUsageDescription` — Camera for photo capture
- `NSPhotoLibraryUsageDescription` — Photo library access
- `CADisableMinimumFrameDurationOnPhone` — 120Hz ProMotion support

## CI

iOS builds run on `macos-14` (Apple Silicon) via `.github/workflows/ios.yml`:
- Builds the shared KMP framework
- Builds the iOS app via `xcodebuild` (scheme: `iosApp`, no signing)
- Runs KMP unit tests on the iOS simulator target

## URL Scheme

The app registers the `librechat://` URL scheme for deep linking (conversations, OAuth callbacks), matching the Android app's behavior.
