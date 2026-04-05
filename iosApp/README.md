# LibreChat iOS App

Compose Multiplatform iOS client for LibreChat. The iOS app is a thin SwiftUI wrapper around the full Compose Multiplatform UI — all screens, navigation, and business logic are shared with Android via KMP.

## Architecture

The entire UI is rendered by Compose Multiplatform. SwiftUI is only used as a hosting layer:

```
iOSApp.swift          — App entry point: initializes Koin DI, renders LibreChatComposeView
ComposeView.swift     — UIViewControllerRepresentable wrapping MainViewController() (CMP root)
KoinHelper.swift      — Swift-side Koin dependency resolver (for debugging / Swift-native code)
SharedFrameworkTest.swift — Compile-time smoke test for KMP + SKIE bridging
```

The `:shared` Gradle module exports `core:common`, `core:model`, `core:network`, and `core:data` as a single `Shared.framework`. All feature modules and `core:ui` are included for Compose Multiplatform screen sharing.

### Key components

- **SKIE**: Enhances Kotlin-Swift interop automatically — sealed classes become Swift enums (`onEnum(of:)`), `Flow<T>` becomes `AsyncSequence`, `suspend fun` becomes `async throws`
- **DI**: Koin is initialized in `iOSApp.init()` via `IosKoinHelperKt.startIosKoin()`
- **Crash Reporting**: `installCrashReporting()` sets up a Kotlin/Native unhandled exception hook that logs via Kermit + NSLog and raises as NSException
- **Platform Impls**:
  - `IosTokenDataStore` (`core/data/src/iosMain/`) — Keychain-backed token storage via Security.framework
  - `IosConnectivityObserver` (`core/common/src/iosMain/`) — NWPathMonitor via `nw_path_monitor_*` APIs
  - `IosSharedModule` (`shared/src/iosMain/`) — Koin module wiring Darwin Ktor engine + all platform deps

## Building

### Prerequisites
- Apple Silicon Mac (Intel Macs are not supported — no `iosSimulatorX64` target)
- Xcode 15.0+ with iOS 16.0+ SDK (for toolchain and simulators — IDE not needed)
- JDK 17+ (for Gradle/Kotlin compilation)
- Android Studio or IntelliJ IDEA (recommended IDE for all code editing)

### Build and Run

```bash
# Build the app (Xcode build phases handle the shared framework automatically)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath iosApp/build build

# Boot simulator, install, and launch
xcrun simctl boot "iPhone 16"
xcrun simctl install booted iosApp/build/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.garfiec.librechat.ios
```

> **Note:** The first build takes several minutes while the Kotlin/Native toolchain downloads.

To rebuild only the shared KMP framework (e.g., after changing shared Kotlin code):

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Info.plist Permissions

The following keys are configured in `Info.plist`:
- `NSMicrophoneUsageDescription` — Microphone for voice input
- `NSSpeechRecognitionUsageDescription` — Speech-to-text
- `NSCameraUsageDescription` — Camera for photo capture
- `NSPhotoLibraryUsageDescription` — Photo library access
- `CADisableMinimumFrameDurationOnPhone` — 120Hz ProMotion support

## URL Scheme

The app registers the `librechat://` URL scheme for deep linking (conversations, OAuth callbacks), matching the Android app's behavior.
