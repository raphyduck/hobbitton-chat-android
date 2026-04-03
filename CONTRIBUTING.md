# Contributing to LibreChat Native KMP

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 17+ |
| Android Studio | Latest stable |
| Xcode | 15+ (iOS development) |
| Gradle | 8.11.1 (via wrapper) |
| Kotlin | 2.1.20 |

## Getting Started

```bash
# Clone with submodules (upstream LibreChat server reference)
git clone --recursive https://github.com/anthropics/LibreChat-Android.git
cd LibreChat-Android
```

### Android

```bash
./gradlew assembleDebug
```

Open the project in Android Studio and run on an emulator or device. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

### iOS

```bash
# Build the shared KMP framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Open `iosApp/iosApp.xcodeproj` in Xcode and run on an iOS simulator. The shared framework must be rebuilt whenever shared code changes.

## Project Structure

The project is a Kotlin Multiplatform (KMP) app with shared business logic and platform-specific UI layers.

### Modules

```
app/                   -> Android entry point, adaptive navigation (phone/tablet)
shared/                -> KMP umbrella module (commonMain, androidMain, iosMain)
core/common/           -> Result type, dispatcher DI, coroutine scopes, extensions
core/model/            -> @Serializable data classes (pure Kotlin)
core/network/          -> Ktor client, API services, SSE client, auth interceptor
core/data/             -> Room DB, DataStore, repositories
core/ui/               -> Material 3 theme, shared composables
feature/auth/          -> Login, Register, 2FA, OAuth, Forgot Password
feature/chat/          -> Real-time chat with SSE streaming, message rendering
feature/conversations/ -> Paginated list, CRUD, tags, search, export/import
feature/settings/      -> Account, appearance, about
feature/agents/        -> Agent marketplace with search and categories
feature/files/         -> File upload, management, image viewer
```

Source sets: `commonMain` = shared code, `androidMain` / `iosMain` = platform-specific implementations.

### Convention Plugins

`build-logic/convention/` contains 13 convention plugins (8 Android + 5 KMP) that standardize module configuration. See `CLAUDE.md` for details.

## Adding a New Feature Module

1. Create the module directory under `feature/`
2. Apply the `librechat.kmp.feature` convention plugin in `build.gradle.kts`
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Register the module in the application startup Koin configuration
5. Use `koinViewModel()` in screen composables to inject ViewModels

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- ViewModels live in `commonMain` only — no platform-specific ViewModels
- Handle platform differences via `expect`/`actual` declarations or delegate factories
- Use [Kermit](https://github.com/touchlab/Kermit) for logging (`co.touchlab.kermit.Logger`), not `println`, `Log.d`, or `NSLog`
- Feature modules depend on `:core:*` only, never on each other

## Testing

- Unit tests go in `commonTest` or `androidUnitTest`
- Maestro flows for E2E testing (8 existing flows in `.maestro/`)
- Run tests before submitting:

```bash
./gradlew test
```

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other
- Unidirectional data flow: UI -> ViewModel -> Repository -> API/Room
- Room is a read-through cache; server is source of truth
- Custom SSE parser over raw `ByteReadChannel` (not Ktor SSE plugin)

See `CLAUDE.md` for the full architecture overview and backend quirks.
