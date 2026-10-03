# core:data

The engine's and the portal's data layer, and the app's preferences.

## What This Module Provides

- **`engine/`** — `EngineMissionRepository` (chats and tasks on the engine: sessions, turns, the
  live feed, recent chats and missions), `EngineProfile`, the addresses (`EngineSettingsStore`,
  `EngineAddressField`), the sign-in round trip (`EngineSignIn`, the callback mailbox,
  `EngineSignInCoordinator`), `EngineSecureStore` (the portal's tokens, EncryptedSharedPreferences),
  `SessionKindStore`, speech to text (`AudioTranscriber`).
- **`portal/`** — `PortalSession`, `PortalTasksSignIn`, `PortalSignOut`, navigation classification.
- **`scheduler/SchedulerRepository`**, **`pricing/ModelPriceCache`** (the gateway's price table,
  `ModelPriceSource` bound to the scheduler).
- **`datastore/`** — `ThemeDataStore`, `SettingsDataStore` (language and text size, read-only; the attention switch),
  `GlobalProfileStore`, `MissionReadingPositions`. One DataStore file (`librechat_settings`, name
  kept so an upgrade keeps its preferences).
- **`legacy/`** — `LegacyLibreChatCleanup`: removes, once, what LibreChat left on an upgraded
  device (below).
- **`di/`** — `dataModule` (+ `dataPlatformModule`, `hobbittonDataModule`) and `engineModule`
  (androidMain: the engine's, scheduler's and portal's clients and everything above that needs
  them).

## The portal (`portal/`, D-076)

One Authelia identity for chats and tasks. Everything here is bound by `engineModule`
(androidMain); the shell and the sign-in resolve it with `getOrNull`.

- **`PortalSession`** — the one holder of the portal's tokens (ex-`EngineSessionManager`). The
  engine's and the scheduler's clients read their bearer here (`PortalBearerSource`); both sign-in
  paths write here.
- **`PortalTasksSignIn`** — the tasks half of the single login, as a web view host sees it. It
  delegates to the existing round trip (`EngineSignInLauncher`: PAR, PKCE, `state`, code exchange)
  and only changes where the page opens; `offer(url)` catches the scheduler page's hop to
  `at.hobbitton.chat://` and drops it in the callback mailbox.
- **`classifyPortalNavigation`** — what the sign-in's web view does with a navigation: `http(s)`
  loads, the app scheme goes to the mailbox, other schemes are refused.
- **`isPortalSignedIn`** — the app's one signed-in state since D-077: addresses set and tokens held.
- **`PortalSignOut`** — the shell's sign-out forgets the portal's tokens and expires the web
  view's cookies on the portal, engine and scheduler origins.
- **No engine password.** `EngineSecureStore` holds the portal tokens only and deletes, on first
  open, the Basic password builds before D-076 stored. `EngineSettingsStore` holds three addresses.

## Engine profiles: chat and task (`engine/`, D-077)

- **`EngineProfile`** — `CHAT` (agent `chat`, provider `hobbitton-chat`) and `TASK` (agent
  `mission`, the gateway). `offersProvider` narrows each model picker; `forChat()` moves a model
  onto the chat provider (same model ids on both).
- **`chatPerimeter()`** — every connector the scheduler's catalogue marks `chat: true`, and only
  those. `EngineMissionRepository.startChat` builds the session's rules from it with the same
  `permissionsFor`, records it with the scheduler before the first prompt (as `launch` does), and
  records the session's kind locally before anything can fail.
- **Every turn names its agent** — a turn without one runs on the engine's default agent, `build`,
  with none of the session's rules (29/09/2026: a task's follow-up ran there). A chat turn names
  agent `chat` and a `hobbitton-chat` model (`sendMessage(profile = CHAT)`). A task turn names the
  session's own agent (`taskAgentOf`): known when `launch` created the session (`mission`), read
  otherwise off the first user message of the transcript (`agentWrittenOn`, the reading
  `classifySession` uses; `history` records it for free), kept in memory per session; `mission`
  when nothing is readable, and in place of `build` or `plan` (`taskAgent`). Its model stays the
  session's unless one is picked.
- **`classifySession` + `SessionKindStore` (`EngineSessionKinds`)** — recorded kind, then the
  scheduler's title shape, then the agent on the messages, then the provider. `recentChats` (the
  drawer) reads at most ten unknown transcripts per refresh and records each verdict;
  `recentMissions` (the Tasks tab) drops chats. Cleared at sign-out with the reading positions.
- **`AudioTranscriber` (`SchedulerTranscriber`)** — the composer's speech to text on the scheduler's
  `POST /transcription`. Answers `TranscriptionOutcome` (the words, or a `TranscriptionFailure` plus
  the server's `erreur`), never throws past `:core:data`. The language hint is the device's when it
  is a bare ISO 639-1 code (`isoLanguageOrNull`), nothing otherwise. Bound in `engineModule`.
- **Attention (`Attention.kt`, 03/10/2026)**: `AttentionSignals` decides when a question or a
  finished reply rings (chime when its conversation is on screen, notification otherwise, nothing
  when `SettingsDataStore.attentionSound` is off, each question once per process);
  `EngineAttentionWatcher` folds the global feed for questions and reads the pending list at start;
  `ConversationRequests` holds the conversation a tapped notification opens. `AttentionNotifier` is
  the platform's, bound by `:app`. `AttentionSignals` and `ConversationRequests` are bound in
  `engineModule`, the watcher in `tasksModule`.
- **`GlobalProfileStore`** — the device's (`device:` keys). `GlobalProfileEditor` is the editor's
  view of it; `GlobalProfileSource` the send path's.

## LibreChat's leftovers (`legacy/`, D-077)

`LegacyLibreChatCleanup.launchOnce()` runs from `LibreChatApplication` after Koin starts, in the
application scope on the IO dispatcher, and at most once (flag `legacy_librechat_purged` written in
the same edit as the purge). It deletes `librechat.db`, WorkManager's database and pending job
(`JobScheduler.cancelAll()`), the `librechat_tokens` and `prefetch_schedule` preferences files and
LibreChat's cache directories; then, in the shared DataStore, it moves a LibreChat account's global
profile under the device keys when the device has none (`adoptAccountProfile`) and removes
LibreChat's entries **by name** plus every `acct:` / `srv:` key (`purgeLibreChatEntries`). The
engine's preferences, the theme, the language, the text size and the device's global profile are
never touched — add a name to `LIBRECHAT_KEYS` only if it was LibreChat's.

## Rules

- Stores take a `DataStore<Preferences>` and dispatchers by injection; tests use
  `PreferenceDataStoreFactory` on a temporary file.
- Convention plugins: `librechat.kmp.library` + `librechat.kmp.koin` + `librechat.kotlin.serialization`.
