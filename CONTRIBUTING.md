# Contributing

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17+ | CI uses 21 |
| Android Studio or IntelliJ IDEA | Latest stable | |
| Gradle | 9.5.1 (via wrapper) | |
| Kotlin | 2.4.10 | |

## Getting Started

Build and install on a connected device or emulator:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or run directly via Android Studio's run configuration.

Debug builds carry a `.debug` applicationId suffix, so they install as `at.hobbitton.chat.debug`
under the launcher name **Butler Dev** (with a distinct icon backdrop) and coexist with a
released install instead of colliding with it. The two are separate apps to Android: each keeps its
own sign-in and preferences. Release builds keep the bare `at.hobbitton.chat` — update channels
track that package name, so it must not gain a suffix.

The code namespace is still `com.garfiec.librechat`: it names classes, not the installed app, so
`$PKG/.MainActivity` shorthand does not resolve — spell the activity out in full.

Both installs register the portal's return scheme (`at.hobbitton.chat://oauth`), so with both
installed the system asks which app should open it (see the manifest's comment).

## Project Structure

Kotlin Multiplatform modules with a single Android target: shared code in `commonMain`,
platform code in `androidMain`.

```
app/            -> MainActivity, LibreChatApplication
shared/         -> the engine shell (EngineNavHost), shared Koin module list
core/common/    -> Result / safeApiCall, dispatchers, connectivity
core/logging/   -> persistent redacted diagnostic log
core/model/     -> @Serializable models of the engine and the scheduler
core/network/   -> engine and scheduler APIs, portal clients, event stream
core/data/      -> engine repository and profiles, portal session, stores
core/ui/        -> Material 3 theme, markdown, composer look, portal web view
feature/auth/   -> the portal sign-in
feature/tasks/  -> the conversation (chat and task), Tasks tab, usage
```

`build-logic/convention/` contains the convention plugins that standardize module
configuration. See `CLAUDE.md` and the modules' own `CLAUDE.md` files for details.

## Adding a New Feature Module

1. Create the module directory under `feature/` and include it in `settings.gradle.kts`
2. Apply `librechat.kmp.feature` in its `build.gradle.kts`
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Register the module in `sharedKoinModules`, or next to `engineModule` in `LibreChatApplication`
5. Use `koinViewModel()` in screen composables to inject ViewModels

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html); detekt
  (`config/detekt/detekt.yml`) enforces the project's rules
- ViewModels live in `commonMain`
- Handle platform differences via `expect`/`actual` declarations
- Use [Kermit](https://github.com/touchlab/Kermit) for logging (`co.touchlab.kermit.Logger`), not `println` or `Log.d`
- Feature modules depend on `:core:*` only, never on each other

## Testing

- Unit tests go in `commonTest` or `androidUnitTest`
- Run tests and lint before submitting:

```bash
./gradlew test
./gradlew detekt :app:lint
```
