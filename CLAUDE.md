# Eva — hobbitton chat (Android)

Native Android client of the hobbitton platform, named **Eva** on the launcher since 19/10/2026
(Butler from 03/10; `app_name`; release asset `eva-v<version>.apk`; the agent bears the name too,
in the server's `chat` prompt; its mark is a brick speech bubble holding a serif « e », `EvaMark`).
Its conversations run on the **OpenCode engine**, behind the **scheduler** and the **Authelia portal**. The app started as a fork of Switchboard, a
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
core/data/            → engine repository (conversations), portal session, stores, LibreChat cleanup
core/ui/              → Material 3 theme, markdown, composer look, portal web view
feature/auth/         → PortalSignInScreen: the platform's addresses (prefilled), then the portal sign-in
feature/tasks/        → the conversation (MissionChatScreen), Programmées (scheduled missions), usage
```

Most modules have their own `CLAUDE.md` with specific guidance.

## One engine (D-077)

- **One login**: the Authelia portal (PKCE), from `PortalSignInScreen` (`:feature:auth`). It starts
  on the engine, scheduler and portal addresses the build was made for (`PlatformDefaults`, from a
  git-ignored `platform.properties`; « Autre plateforme » unfolds the fields) and saves them in
  `EngineSettingsStore`. Signed in = addresses set **and** portal tokens held (`isPortalSignedIn`).
- **Root = `EngineNavHost`** (`shared/.../engine/`): the conversation under a drawer (new
  conversation, Conversations, Programmées, the recent sessions, Settings). `MainActivity` composes it; it acts on
  two links only: the portal's return (`at.hobbitton.chat://oauth`) and, since 19/10/2026, a
  conversation named by a server push notification (`at.hobbitton.chat://conversation/<session>`,
  ntfy: a decision to make, a question asked; `sessionOfConversationLink`).
- **One kind of conversation** (D-082, 12/10/2026, as on Claude.ai): chats and tasks are fused.
  Every conversation is a session on agent `chat` whose model always comes from provider
  `hobbitton-chat` (the conversations' budget; the scheduled missions spend on the gateway's key).
  The server's `chat` profile carries what the tasks' did (shell, local files, pages). No connector
  choice since 22/10/2026 (D-083, Raphaël's rule): the scheduler decides what a session reaches from
  WHO speaks to it, and every session this app starts stands at the titulaire's level
  (`perimetre(niveau="titulaire")`, recorded before the first prompt by
  `EngineMissionRepository.startChat`). The composer offers the model and one switch, the shell
  (« Sans shell » / « Shell activé »), the only thing that level leaves on demand. One
  screen (`MissionChatScreen`), one route (`EngineChat`) for every session: a chat, a task of an
  earlier version, a scheduled run, whose follow-ups run on `chat` too. A session the scheduler
  recorded at another level (a run stands at `mission`, where `ssh-distant` is forbidden) is
  lifted to the titulaire's level before the person's first message in it (24/10/2026, Raphaël's
  « Go ssh » in a run was refused by the annuaire; `sessionScope` reads the level, `send` lifts).
- **Programmées** (ex Tasks tab): the scheduler's entries only, recurring then one-shot, each row
  opening its actions. Their runs are not listed there: they are conversations, named after their
  mission with a small clock (`EngineChatSummary.scheduled`). `classifySession` still leaves out a
  session that has said nothing yet.
- **Drawer and Conversations** (Raphaël's rule, 19/10/2026: the two lists show the same sessions;
  from 16/10 the drawer had left the scheduler's runs out). The Conversations page
  (`EngineConversationsScreen`, an entry of the drawer) lists every session, unread first, then by
  latest activity (`inboxOrder`); the drawer shows the same read (`ChatScope.ALL`) by latest
  activity under day headers, a run with its small clock. Both read 200 sessions at a time,
  « Plus anciennes » reads more, and the drawer's search covers every session, accents folded
  (21/10/2026).
  Unread = moved since this device last showed it (`ConversationReads`), or since the scheduler
  marked it read (22/10/2026: its unread mission withdrew a question a reply had made moot,
  `retirer_question`, read back through `conversations_lues`); for a run, also « had
  something to say »: the scheduler's `historique(json_brut=True)` carries `a_signaler`, false when
  the run answered « RIEN À SIGNALER » (`runHasNews`).
  A **discreet** mission (Raphaël's rule, 19/10/2026: the guet's lots crowded both lists) keeps its
  runs out of both, failed or not (`runHidden`, the scheduler's `discrete` on each run; Raphaël,
  20/10/2026: the failing runs filled the drawer; a failure shows on its row under Programmées); its history stays under Programmées, whose actions sheet hides or shows it
  (`modifier(discrete=…)`).
- **A new chat greets** (lot 2, 10/10/2026): « Bonjour, <prénom>. » in the serif, the first name
  read from the scheduler's `GET /identite` (the portal's `Remote-Name`, via `SchedulerRepository.identity`),
  without a name when it gives none. The composer is Claude's: a raised box, bare field, plain
  labels for the model and the connectors, send only once there is something to send
  (`:core:ui` `input/`). Messages settle in with `animateItem`, « Réflexion… » shimmers before the
  first token, a round button brings the tail back when one has scrolled up, send and stop click
  under the thumb. A conversation opens at its very bottom, the end of its last message in view
  (Raphaël, 23/10/2026: the position saved per conversation from 31/08 kept reopening at the wrong
  place, then the start of the last message left a long one opening higher), once the history
  call has ended, not at the feed's first turn (`MissionTurns`); a history read that hangs is
  dropped after 10 s and asked again, three times, then the screen says so (23/10/2026). A message typed while the session
  works goes out at once (23/10/2026): the engine records it and the running turn reads it at its
  next step, never interrupted; send shows over stop as long as something is staged. An image a
  message names in markdown, `![description](https://…)`, is drawn in the message (23/10/2026,
  `MarkdownImages`, the app's Coil loader). A photo staged for a model that does not see images
  (DeepSeek, 24/10/2026) gets a line above the box: the platform's privacy gateway describes it in
  words for that model, a tap opens the model picker (`imageStagedForBlindModel`). Who is blind is
  the gateway's word, relayed by the scheduler's `tarifs` (`ModelPrice.seesImages`, `voit_images`),
  not the engine's: the engine is told such a model takes images so that it forwards them instead
  of refusing them.
- **Dictation and audio files** (the composer). The mic is heard by the phone itself (19/10/2026,
  Settings → Dictée, on by default): Android 12's on-device recognizer in French, its words shown
  as they come and dropped into the composer one stretch of speech at a time (`PhoneDictation`,
  `MissionDictation.android.kt`), never the network recognizer, which would send the voice to
  Google. Without one on the phone, with the switch off, or while the phone downloads its French,
  the take is recorded and goes to the scheduler's `POST /transcription` (`SchedulerApi.transcribe`
  → `SchedulerTranscriber`), and so do audio files. A dictation lands in the composer and is never
  sent on its own; an audio file leaves with the message as a quoted transcription.
- **Questions from the agent** (OpenCode's `question` tool, 03/10/2026): the engine announces one on
  the feed (`question.asked`) and blocks the turn until `POST /question/{id}/reply` or `/reject`.
  The conversation shows it as a form in place of the composer (`MissionQuestionForm`, options,
  multiple choice, a free answer), and reads `GET /question` at opening so a question asked before
  the screen opened is not missed. The server opens the tool to the `chat` profile (and `mission`);
  the app grants it on every session it builds rules for (`permissionsFor`), the scheduler's
  autonomous missions never get it.
- **Sound and notifications** (Settings → Notifications, on by default): a question chimes when its
  conversation is on screen and is notified otherwise (`EngineAttentionWatcher`, run by the shell
  over the global feed, every session); a reply the screen saw start is notified when it ends out
  of sight, and so is a reminder the scheduler plays back into the conversation that set it
  (`planifier(..., dans_cette_conversation=True)`, 19/10/2026; its turn opens with
  `REMINDER_PREFIX`). The decision is common (`AttentionSignals`), the sound and channels are `:app`'s
  (`AndroidAttentionNotifier`); a tap opens the conversation (`ConversationRequests`).
  **Push** (19/10/2026, Raphaël's rule: no third-party app, nothing to configure): the server
  publishes decisions and questions on its ntfy topic; `PushService` (`:app`, a foreground service of
  type `remoteMessaging`, restarted at boot by `PushBootReceiver`) asks the scheduler where
  (`GET /notifications` → `PushChannel`), keeps the topic's JSON stream open and resumes after the
  last message shown. `parsePushLine` reads a message, `shouldNotifyPush` keeps it quiet when the app
  is on screen or when the in-app watcher (`attentionWatcherRunning`) already rings that question.
  Off with the Settings switch. Its own notice is as discreet as Android allows (Raphaël,
  19/10/2026: keep the stream, no Firebase): lowest channel, no badge or sound, hidden on the lock
  screen, dismissable, and never posted again while the service runs.
- **Spending ceilings** (Settings → Plafonds de dépense, 12/10/2026): the conversations' key per
  day, per week and over 30 days, read and set through the scheduler's `/plafonds`
  (`EngineBudgetScreen`). The scheduled missions' key is not set from here.
- **Global instructions** (`GlobalProfile`, sent as `system` on every chat and task turn) are edited
  from Settings → Instructions (`EngineInstructionsScreen`) and stored on the device
  (`GlobalProfileStore`, `device:` keys).
- **Default model** (Settings → Assistant, 19/10/2026): the model a new conversation starts on,
  stored on the device (`SettingsDataStore.defaultChatModel`, a model id) and read by the
  repository's `models()` (`ModelPreference`) as `preselected`; « Celui du serveur » (null) follows
  the engine's default, and so does a model the deployment no longer lists. The composer still picks
  another for one conversation.
- **LibreChat's leftovers** on a device that ran an earlier build (its Room database, tokens,
  WorkManager job, caches and preferences) are removed once at start by `LegacyLibreChatCleanup`;
  a LibreChat account's global profile is moved under the device keys first.

## Koin graph

`LibreChatApplication` starts `sharedKoinModules` (`commonModule`, `loggingModule`,
`networkModule`, `dataModule`, `authModule`, `sharedAppModule`) plus `engineModule` (`:core:data`
androidMain: the engine, scheduler and portal clients, the portal session, the stores),
`tasksModule`, `attentionModule` and `platformModule` (the build's default addresses, and the share
sheet for the diagnostic log). The shell and the sign-in resolve the engine graph with `getOrNull`.
`KoinGraphVerificationTest` (`:app`) verifies `sharedKoinModules`.

## Architecture Rules

- Feature modules depend on `:core:*` only, never on each other
- Single Activity with Nav 3 (`NavDisplay` + `NavBackStack<NavKey>` + `entryProvider`)
- Unidirectional data flow: UI → ViewModel → Repository → API
- The engine is the source of truth; the app keeps only preferences, the portal's tokens and small
  local records (session kinds, read marks)

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
- The feed is global and heavy (a tool's whole output again on every frame that grows it):
  `EngineMissionRepository.events` / `allEvents` read and parse it on the IO dispatcher whoever
  collects (20/10/2026: parsed on the main thread by the attention watcher, a scheduled run's
  tools froze the app). The phone's own traces come out through Settings → Plateforme → « Partager
  le journal de diagnostic » (`DiagnosticLogShare`).
