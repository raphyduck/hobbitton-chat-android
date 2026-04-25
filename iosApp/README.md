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

#### Simulator

```bash
# 1. Build the shared KMP framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Build the Xcode project
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath iosApp/build build

# 3. Boot simulator, install, and launch
xcrun simctl boot "iPhone 16"
xcrun simctl install booted iosApp/build/Build/Products/Debug-iphonesimulator/iosApp.app
xcrun simctl launch booted com.garfiec.librechat.ios
```

#### Physical Device

```bash
# 1. Build the shared KMP framework for device
./gradlew :shared:linkDebugFrameworkIosArm64
```

Then open the project in Xcode:

```bash
open iosApp/iosApp.xcodeproj
```

In Xcode:
1. Select your iPhone from the device picker (top toolbar)
2. Go to **Signing & Capabilities** → set your **Team** (required for device signing)
3. Press **⌘R** to build and install

> **Note:** The first build takes several minutes while the Kotlin/Native toolchain downloads. You need an Apple Developer account (free tier works) for device signing.

### Known Xcode Behaviour

**Red dot on `Shared.framework` in the navigator** — You may see a red indicator on `Shared.framework` in the Xcode file navigator (left sidebar). This is cosmetic and does not affect builds. It happens because the Xcode project file keeps a static reference path to the framework for display purposes, and that path may not exist if you haven't built that particular target yet. The linker always resolves the framework via `FRAMEWORK_SEARCH_PATHS`, which is set correctly per target — the red dot can be safely ignored. Building the Gradle framework for your active target clears it:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # clears it for simulator
./gradlew :shared:linkDebugFrameworkIosArm64           # clears it for device
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
