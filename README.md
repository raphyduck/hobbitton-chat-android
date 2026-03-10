# LibreChat Android

Native Android client for [LibreChat](https://www.librechat.ai/). Connects to any self-hosted LibreChat server — no backend modifications required.

## Features

- **Chat** — Real-time streaming (SSE), message branching & sibling navigation, stop/regenerate/continue, markdown with syntax highlighting, LaTeX math rendering, code blocks with copy, image display, file attachments, tool call progress cards
- **Model Selection** — Searchable bottom sheet grouped by endpoint, model comparison mode
- **Agents** — Marketplace with search and categories, MCP server configuration
- **Conversations** — Paginated list with date grouping, tags, search, rename, archive, delete, share, fork, duplicate, export/import
- **Presets & Prompts** — Save/load chat presets, prompts library with @mention insertion
- **Authentication** — Login, registration, forgot password, two-factor (TOTP + backup codes), OAuth (Google, GitHub, Discord, Facebook, Apple, OpenID)
- **Files** — Upload, list, delete, inline image rendering with pinch-to-zoom
- **Voice** — Speech-to-text input, text-to-speech playback (device and server engines)
- **Settings** — Theme (system/light/dark), account management, data controls
- **Tablet** — Adaptive dual-pane layout (600dp+) with persistent sidebar
- **Accessibility** — Semantic headings, content descriptions, 48dp touch targets, live regions

## Server Setup

The app works with any standard LibreChat server. During onboarding, you'll enter your server URL (e.g., `https://chat.example.com` or `http://192.168.1.100:3080`).

### Required Configuration

Add the following to your LibreChat server's `.env` file:

```env
# Safety net for native app clients.
# The app sends a browser User-Agent to pass the uaParser middleware,
# but if it ever fails to parse, this prevents ban point accumulation.
NON_BROWSER_VIOLATION_SCORE=0
```

Without this setting, the server's violation system may accumulate ban points against the Android client if the User-Agent check fails, eventually locking the account out.

### Notes

- **Registration** — The app respects your server's registration settings. If registration is disabled server-side, only the login form is shown.

## Building from Source

**Requirements:** Android Studio, JDK 17+

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build:

```bash
./gradlew assembleRelease
```

### Tech Stack

- Jetpack Compose + Compose Navigation
- Hilt (dependency injection)
- Ktor Client with OkHttp engine
- Kotlinx Serialization
- Room (cache), DataStore (preferences), EncryptedSharedPreferences (tokens)
- Kotlin 2.1.0, compileSdk 35, minSdk 26

## License

This project is licensed under the [MIT License](LICENSE).
