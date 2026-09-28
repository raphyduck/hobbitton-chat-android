# Switchboard

Native mobile client for LibreChat (Android & iOS). Connects to existing LibreChat backend servers (no backend changes). Users specify server URL during onboarding.

## Tech Stack

- **UI**: Jetpack Compose + Navigation Compose 3 (Nav 3)
- **DI**: Koin (KMP-ready)
- **Network**: Ktor Client (OkHttp engine)
- **Serialization**: Kotlinx Serialization
- **Local Storage**: Room (cache), DataStore (prefs), EncryptedSharedPreferences (tokens)
- **Build**: Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.10, compileSdk 36, minSdk 26

## Module Layout

```
app/                  → Single Activity, adaptive navigation (phone/tablet)
shared/               → KMP shared framework (iOS entry point, shared navigation)
build-logic/          → 13 convention plugins for consistent Gradle config
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

## hobbitton: one engine (D-077)

LibreChat is gone server-side. On Android the app runs entirely on the OpenCode engine, and the
LibreChat shell is no longer composed:

- **One login**: the Authelia portal (PKCE), from `PortalSignInScreen` (`:feature:auth`). Onboarding
  asks for the engine, scheduler and portal addresses (`EngineSettingsStore`), never a LibreChat
  server URL. Signed in = addresses set **and** portal tokens held (`isPortalSignedIn`).
- **Root = `EngineNavHost`** (`shared/.../engine/`): the chat under a drawer (new chat, recent chats,
  Tasks, Settings). `MainActivity` composes it; nothing of LibreChat is reachable from it, so none of
  LibreChat's startup calls run (config, banners, version check, token refresh, session tasks).
- **Chat and tasks are engine profiles** (`EngineProfile`, `:core:data`): a chat is a session on agent
  `chat` whose model always comes from provider `hobbitton-chat`, with every connector the scheduler
  marks `chat: true` as its perimeter (registered through the scheduler exactly like a mission's);
  a task keeps agent `mission` and the gateway. The UI is the mission chat (`MissionChatScreen`) with
  `EngineProfile.CHAT`.
- **Telling chats from tasks** (`classifySession`): the kind the app **recorded locally when it
  created the session** (`SessionKindStore`) wins; then the scheduler's title shape (a task); then
  the **agent written on the session's messages** (`chat` = chat); then the answering provider.
  Verdicts learned from a transcript are recorded, so each foreign session is read once.
- **Dictation and audio files** (chat and task composer) go to the scheduler's `POST /transcription`
  (`SchedulerApi.transcribe` → `SchedulerTranscriber`), never to LibreChat's speech route. A
  dictation lands in the composer and is never sent on its own; an audio file leaves with the
  message as a quoted transcription.
- **Global instructions** (`GlobalProfile`, sent as `system` on every chat and task turn) are edited
  from the shell's Settings → Instructions (`EngineInstructionsScreen`). Without a LibreChat account
  the profile is stored per device (`GlobalProfileStore`).
- The upstream modules (agents, files, conversations, settings, LibreChat chat, the legacy shell)
  are **still in the tree and still compile** — iOS starts from the legacy shell, the engine graph
  being Android-only (D-034) — but Android no longer reaches them. A later lot deletes them.

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other
- Single Activity with Nav 3 (`NavDisplay` + `NavBackStack<NavKey>` + `entryProvider`)
- Unidirectional data flow: UI → ViewModel → Repository → API/Room
- Room is a read-through cache; server is source of truth
- Custom SSE parser over raw ByteReadChannel (not Ktor SSE plugin)

## Adding a New Feature Module

1. Create the module directory under `feature/`
2. Apply the convention plugin in `build.gradle.kts` — this auto-applies Koin + Compose deps:
   - `librechat.kmp.feature` — for KMP modules with shared iOS + Android code (most features)
   - `librechat.mobile.feature` — for Android-only modules
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Add the module to `sharedKoinModules` (`shared/src/commonMain/.../di/SharedKoinModules.kt`) — the single list both Android (`LibreChatApplication`) and iOS (`IosSharedModule`) start from, so a module registered once is wired on both platforms
5. Use `koinViewModel()` in screen composables to inject ViewModels

## Backend Quirks

- Mutation endpoints wrap body in `arg` field: `{ "arg": { ... } }`
- Two-phase SSE: POST → `{ streamId }`, then GET stream. `streamId === conversationId`
- `GET /api/config` drives feature availability — never hardcode
- ua-parser-js middleware rejects non-browser User-Agents with 403 (workaround: Chrome UA string)
- Refresh token sent via request body (not HTTP-only cookies)
- iOS SSE streaming uses a custom `NWConnection`-based HTTP/1.1 transport (`core/network/src/iosMain/.../sse/SseHttpTransport.ios.kt`) to bypass NSURLSession's undocumented `text/*` content-type buffering. See `core/network/CLAUDE.md` SSE section for the two-layer buffering story.

## Upstream Sync

- **`upstream/`** — Git submodule of the [official LibreChat repo](https://github.com/danny-avila/LibreChat). Read-only reference for API and web app parity. Do not modify.
- **`UPSTREAM_VERSION`** — Tracks which official tag/commit this mobile build is based on. Updated by the `/sync-upstream` skill.
- **`backendTargetVersion`** (root `version.properties`) — single source of truth for the targeted backend; must match the tag in `UPSTREAM_VERSION` (without `v` prefix). A core/common Gradle task code-generates `BackendVersion.SUPPORTED_BACKEND_VERSION` from it, and `release.yml` reads it for release notes. Edit the property, not the constant.
- **`/sync-upstream`** — Claude Code skill to diff upstream releases, identify gaps, propose changes, and implement them with user approval. Uses Agent Teams (investigator, android-expert, implementer, verifier).
- **`scripts/mirrors.json`** — registry of upstream constants the client copies by hand, because the server never serves them (which providers take documents natively, which MIME types the parser extracts, which feedback reasons the write route accepts). These drift **silently**: nothing fails to decode and nothing errors, so a sync's ordinary diff sweep reads them as inert constant edits. `scripts/check-mirrors.py` diffs each watched region between two upstream revisions and names the Kotlin file to reconcile; `/sync-upstream` runs it at Phase 0. **Adding a hardcoded mirror means adding a registry entry in the same PR** — a mirror nobody registered is one nobody will notice going stale.
