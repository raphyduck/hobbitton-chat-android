# Butler

The Android client of the hobbitton platform (formerly « hobbitton chat »): chats and tasks on the OpenCode engine, behind the
platform's scheduler and its Authelia portal.

It began as a fork of [Switchboard](https://github.com/garfiec/Librechat-Mobile), a native client
for LibreChat. LibreChat has since left the platform, and its client code has left this app: what
remains is the engine's shell. The installed application is `at.hobbitton.chat`.

## What it does

- **One sign-in** — the three addresses of the platform (engine, scheduler, portal), then the
  portal's sign-in (PKCE) in the app's own web view.
- **Chat** — a session on the engine's `chat` agent, with the models of the `hobbitton-chat`
  provider and the connectors the scheduler opens to chats; answers stream live.
- **Tasks** — scheduled and one-off missions, their runs and their conversations, and the
  platform's consumption and model prices.
- **Global instructions** — sent as `system` on every chat and task turn, edited from Settings.
- **Dictation and audio files** — transcribed by the scheduler (`POST /transcription`).
- **Themes** — accent colour, light / dark / system, Material You on Android 12+.

## Install

Signed APKs are built by the release workflow (`.github/workflows/release.yml`) and attached to
the repository's GitHub Releases, each with a `.sha256` and a SLSA build-provenance attestation.
See [docs/RELEASING.md](docs/RELEASING.md) for how a release is cut and verified.

## Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ (CI uses 21) |
| Android Studio or IntelliJ IDEA | Latest stable |
| Gradle | 9.5.1 (via wrapper) |
| Kotlin | 2.4.10 |

## Building from Source

```bash
./gradlew :app:assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`. Tests and lint:

```bash
./gradlew test
./gradlew detekt :app:lint
```

## Tech Stack

- Kotlin Multiplatform modules (Android target), Jetpack Compose / Compose Multiplatform
- Navigation 3, Koin, Ktor Client (OkHttp), Kotlinx Serialization
- DataStore (preferences), EncryptedSharedPreferences (the portal's tokens)
- compileSdk 36, minSdk 26

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style, and PR guidelines.

## License

This project is licensed under the [MIT License](LICENSE).
