# Butler — hobbitton chat (Android)

Native Android client of the hobbitton platform, named **Butler** on the launcher since 03/10/2026
(`app_name`; release asset `butler-v<version>.apk`): chats and tasks run on the **OpenCode engine**,
behind the **scheduler** and the **Authelia portal**. The app started as a fork of Switchboard, a
LibreChat client; LibreChat is gone from the server (D-077) and from this tree. iOS was dropped
with it: the modules stay Kotlin Multiplatform (`commonMain` / `androidMain`) but Android is the
only target.

## Tech Stack

- **UI**: Jetpack Compose (Compose Multiplatform in the KMP modules) + Navigation 3
- **DI**: Koin
- **Network**: Ktor Client (OkHttp engine)
- **Serialization**: Kotlinx Serialization
- **Local Storage**: DataStore (preferences), EncryptedSharedPreferences (the portal's tokens)
- **Build**: Gradle 9.5.1, AGP 9.2.1, Kotlin 2.4.10, compileSdk 36, minSdk 26

## Module Layout

```
app/                  → MainActivity + LibreChatApplication (Koin start, image loader, diagnostics)
shared/               → the engine shell: EngineNavHost, drawer, settings, global instructions
build-logic/          → convention plugins
core/common/          → Result / safeApiCall, dispatchers and scopes, connectivity, cleartext policy
core/logging/         → persistent redacted diagnostic log, startup header, main-thread watchdog
core/model/           → @Serializable models of the engine and the scheduler, GlobalProfile
core/network/         → engine and scheduler APIs, portal clients (OAuth/PKCE), event stream
core/data/            → engine profiles and repository, portal session, stores, LibreChat cleanup
core/ui/              → Material 3 theme, markdown, composer look, portal web view
feature/auth/         → PortalSignInScreen: the three addresses, then the portal sign-in
feature/tasks/        → the conversation (MissionChatScreen, chat and task), Tasks tab, usage
```

Most modules have their own `CLAUDE.md` with specific guidance.

## One engine (D-077)

- **One login**: the Authelia portal (PKCE), from `PortalSignInScreen` (`:feature:auth`). It asks
  for the engine, scheduler and portal addresses (`EngineSettingsStore`). Signed in = addresses set
  **and** portal tokens held (`isPortalSignedIn`).
- **Root = `EngineNavHost`** (`shared/.../engine/`): the chat under a drawer (new chat, recent
  conversations — chats and started tasks —, Tasks, Settings). `MainActivity` composes it; the
  only link it acts on is the portal's return (`at.hobbitton.chat://oauth`).
- **Chat and tasks are engine profiles** (`EngineProfile`, `:core:data`): a chat is a session on
  agent `chat` whose model always comes from provider `hobbitton-chat`, with every connector the
  scheduler marks `chat: true` as its perimeter (registered through the scheduler exactly like a
  mission's); a task keeps agent `mission` and the gateway. The UI is the mission chat
  (`MissionChatScreen`) with `EngineProfile.CHAT`. A new task opens the same screen, blank, on
  `EngineProfile.TASK`: its first message (files and ticked connectors included) creates it.
- **Telling chats from tasks** (`classifySession`): the kind the app **recorded locally when it
  created the session** (`SessionKindStore`) wins; then the scheduler's title shape (a task); then
  the **agent written on the session's messages** (`chat` = chat); then the answering provider.
  Verdicts learned from a transcript are recorded, so each foreign session is read once.
- **A new chat greets** (lot 2, 10/10/2026): « Bonjour, <prénom>. » in the serif, the first name
  read from the scheduler's `GET /identite` (the portal's `Remote-Name`, via `SchedulerRepository.identity`),
  without a name when it gives none. The composer is Claude's: a raised box, bare field, plain
  labels for the model and the connectors, send only once there is something to send
  (`:core:ui` `input/`). Messages settle in with `animateItem`, « Réflexion… » shimmers before the
  first token, a round button brings the tail back when one has scrolled up, send and stop click
  under the thumb.
- **Dictation and audio files** (chat and task composer) go to the scheduler's `POST /transcription`
  (`SchedulerApi.transcribe` → `SchedulerTranscriber`). A dictation lands in the composer and is
  never sent on its own; an audio file leaves with the message as a quoted transcription.
- **Questions from the agent** (OpenCode's `question` tool, 03/10/2026): the engine announces one on
  the feed (`question.asked`) and blocks the turn until `POST /question/{id}/reply` or `/reject`.
  The conversation shows it as a form in place of the composer (`MissionQuestionForm`, options,
  multiple choice, a free answer), and reads `GET /question` at opening so a question asked before
  the screen opened is not missed. The server opens the tool to the `chat` and `mission` profiles;
  the app grants it on every session it builds rules for (`permissionsFor`), the scheduler's
  autonomous missions never get it.
- **Sound and notifications** (Settings → Notifications, on by default): a question chimes when its
  conversation is on screen and is notified otherwise (`EngineAttentionWatcher`, run by the shell
  over the global feed, every session); a reply the screen saw start is notified when it ends out
  of sight. The decision is common (`AttentionSignals`), the sound and channels are `:app`'s
  (`AndroidAttentionNotifier`); a tap opens the conversation (`ConversationRequests`).
- **Global instructions** (`GlobalProfile`, sent as `system` on every chat and task turn) are edited
  from Settings → Instructions (`EngineInstructionsScreen`) and stored on the device
  (`GlobalProfileStore`, `device:` keys).
- **LibreChat's leftovers** on a device that ran an earlier build (its Room database, tokens,
  WorkManager job, caches and preferences) are removed once at start by `LegacyLibreChatCleanup`;
  a LibreChat account's global profile is moved under the device keys first.

## Koin graph

`LibreChatApplication` starts `sharedKoinModules` (`commonModule`, `loggingModule`,
`networkModule`, `dataModule`, `authModule`, `sharedAppModule`) plus `engineModule` (`:core:data`
androidMain: the engine, scheduler and portal clients, the portal session, the stores) and
`tasksModule`. The shell and the sign-in resolve the engine graph with `getOrNull`.
`KoinGraphVerificationTest` (`:app`) verifies `sharedKoinModules`.

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other
- Single Activity with Nav 3 (`NavDisplay` + `NavBackStack<NavKey>` + `entryProvider`)
- Unidirectional data flow: UI → ViewModel → Repository → API
- The engine is the source of truth; the app keeps only preferences, the portal's tokens and small
  local records (session kinds, reading positions)

## Adding a New Feature Module

1. Create the module directory under `feature/` and add it to `settings.gradle.kts`
2. Apply `librechat.kmp.feature` in its `build.gradle.kts` — this auto-applies Koin, Compose,
   serialization and Nav 3 dependencies, and the `:core:*` modules
3. Create `di/<Feature>Module.kt` with a Koin `module { }` containing `viewModelOf(::YourViewModel)` definitions
4. Add the module to `sharedKoinModules` (`shared/src/commonMain/.../di/SharedKoinModules.kt`), or
   next to `engineModule` in `LibreChatApplication` if it needs the engine graph
5. Use `koinViewModel()` in screen composables to inject ViewModels

## Server Quirks

- The engine exposes each session twice (classic `/session/…` and v2 `/api/…`); missions and chats
  use the classic routes only — see `core/network/CLAUDE.md`.
- The engine's and the scheduler's clients carry the portal's bearer as `Proxy-Authorization`,
  scoped to their own host; the edge presents the engine's own credential.
- Custom SSE parser (`SseLineParser`) over the raw byte stream, not Ktor's SSE plugin.
