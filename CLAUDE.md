# LibreChat Android

Native Android client for LibreChat. Connects to existing LibreChat backend servers (no backend changes). Users specify server URL during onboarding.

## Tech Stack

- **UI**: Jetpack Compose + Compose Navigation
- **DI**: Koin (KMP-ready)
- **Network**: Ktor Client (OkHttp engine)
- **Serialization**: Kotlinx Serialization
- **Local Storage**: Room (cache), DataStore (prefs), EncryptedSharedPreferences (tokens)
- **Build**: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, compileSdk 35, minSdk 26

## Module Layout

```
app/                  → Single Activity, adaptive navigation (phone/tablet)
build-logic/          → 7 convention plugins for consistent Gradle config
core/common/          → Result type, dispatcher DI, coroutine scopes, extensions
core/model/           → @Serializable data classes (pure Kotlin, no Android deps)
core/network/         → Ktor client, 16 API services, SSE client, auth interceptor
core/data/            → Room DB, DataStore, EncryptedSharedPrefs, repository impls
core/ui/              → Material 3 theme, shared composables
feature/auth/         → Server URL, Login, Register, 2FA, OAuth, Forgot Password
feature/chat/         → Real-time chat with SSE streaming, message rendering
feature/conversations/→ Paginated list, CRUD, tags, search, export/import
feature/settings/     → Account, appearance, about, danger zone
feature/agents/       → Agent marketplace with search and categories
feature/files/        → File upload, management, image viewer
```

Each module has its own `CLAUDE.md` with specific guidance.

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other
- Single Activity with Compose Navigation
- Unidirectional data flow: UI → ViewModel → Repository → API/Room
- Room is a read-through cache; server is source of truth
- Custom SSE parser over raw ByteReadChannel (not Ktor SSE plugin)

## Adding a New Feature Module

1. Create the module directory under `feature/`
2. Apply the `librechat.android.feature` convention plugin in `build.gradle.kts` — this auto-applies Koin + Compose deps
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Register the module in `LibreChatApplication.kt`'s `startKoin { modules(...) }` list
5. Use `koinViewModel()` in screen composables to inject ViewModels

## Backend Quirks

- Mutation endpoints wrap body in `arg` field: `{ "arg": { ... } }`
- Two-phase SSE: POST → `{ streamId }`, then GET stream. `streamId === conversationId`
- `GET /api/config` drives feature availability — never hardcode
- ua-parser-js middleware rejects non-browser User-Agents with 403 (workaround: Chrome UA string)
- Refresh token sent via request body (not HTTP-only cookies)

## Upstream Sync

- **`upstream/`** — Git submodule of the [official LibreChat repo](https://github.com/danny-avila/LibreChat). Read-only reference for API and web app parity. Do not modify.
- **`UPSTREAM_VERSION`** — Tracks which official tag/commit this Android build is based on. Updated by the `/sync-upstream` skill.
- **`BackendVersion.kt`** (`core/common/`) — `SUPPORTED_BACKEND_VERSION` constant must match the tag in `UPSTREAM_VERSION` (without `v` prefix).
- **`/sync-upstream`** — Claude Code skill to diff upstream releases, identify gaps, propose changes, and implement them with user approval. Uses Agent Teams (investigator, android-expert, implementer, verifier).
