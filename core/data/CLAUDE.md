# core:data

The engine's and the portal's data layer, and the app's preferences.

## What This Module Provides

- **`engine/`**: `EngineMissionRepository` (the conversations on the engine: sessions, turns, the
  live feed, the drawer's list), `CHAT_AGENT` / `CHAT_PROVIDER` / `forChat`, the addresses (`EngineSettingsStore`,
  `EngineAddressField`; `PlatformDefaults`, the three a build was made for, bound by `:app` from
  `platform.properties` and read by the sign-in), the sign-in round trip (`EngineSignIn`, the callback mailbox,
  `EngineSignInCoordinator`), `EngineSecureStore` (the portal's tokens, EncryptedSharedPreferences),
  `SessionKindStore`, speech to text (`AudioTranscriber`).
- **`portal/`** — `PortalSession`, `PortalTasksSignIn`, `PortalSignOut`, navigation classification.
- **`scheduler/SchedulerRepository`** (also the `ChatBudgetSource` of Settings' spending ceilings,
  `/plafonds`), **`pricing/ModelPriceCache`** (the gateway's price table,
  `ModelPriceSource` bound to the scheduler).
- **`datastore/`** — `ThemeDataStore`, `SettingsDataStore` (language, read-only; the text size, the attention and dictation switches and the default model, written from Settings),
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

## One kind of conversation (`engine/`, D-077, D-082)

Since 12/10/2026 chats and tasks are one conversation, as on Claude.ai.

- **Agent and provider**: every session the app starts and every turn it sends names agent
  `chat` (`CHAT_AGENT`) and a `hobbitton-chat` model (`CHAT_PROVIDER`; `forChat()` moves a model
  named on the gateway, same ids on both). A turn without an agent would run on the engine's
  default (`build` on 29/09/2026, a tool-less `repli` profile on the server since D-082). Old task
  sessions and scheduled runs answered from the phone go through `chat` too; their own rules still
  decide what they may do. `models()` offers the chat provider only.
- **`startChat(text, connectors, model, files)`**: the session stands at the titulaire's trust level
  (D-083, 22/10/2026, Raphaël: the scheduler decides what a session reaches from WHO speaks to it,
  never from the caller's ticks). `perimetre(session, niveau="titulaire")` is recorded before the
  first prompt (D-071); of `connectors`, only the shell counts (`SHELL_CONNECTOR`, the one thing that
  level leaves on demand). The direct connectors of `titularPerimeter` become the session's rules
  (`permissionsFor`); the session's kind is recorded locally before anything can fail.
  `setConnectors` re-grants a live session the same way, shell on or off.
- **`recentChats(scope)`**: `ChatScope.MANUAL` (the drawer) leaves the scheduler's runs out
  before any transcript is read; `ChatScope.ALL` (the Conversations page) keeps them, named after
  their mission (`scheduledMissionName`) and marked `scheduled`, sorted unread first (`inboxOrder`).
  `unread` comes from `ConversationReads` (`EngineConversationReads`: when each session was last
  shown here, and a floor so a first launch marks nothing; cleared at sign-out), merged with the
  scheduler's own read marks (`SchedulerApi.serverReads`, its `conversations_lues` tool, 22/10/2026:
  a conversation whose question the unread mission withdrew because a reply made it moot; the
  later mark wins, `ReadSnapshot.withServerReads`) and, for a run, from
  the scheduler's `runs()` (`runHasNews`: news, failed or stopped short). A discreet mission's
  runs are left out (`runHidden`, 19/10/2026), failed or not since 20/10 (its failure shows on its row
  under Programmées); a session the
  scheduler lists as a run counts as one even when its title is not recognised. `classifySession` +
  `SessionKindStore` (`EngineSessionKinds`) leave out a session that has said nothing yet; at most
  ten unknown transcripts are read per refresh and each verdict recorded (forty for a search).
  `query` (21/10/2026) keeps only the sessions whose title matches, over every session the engine
  lists, accents folded (`foldForSearch` / `matchesSearch`, `SearchFold.kt`); `limit` is the page. Cleared at sign-out with
  the reading positions.
- **`models()`**: the conversations' provider only; `preselected` is the person's default
  (`ModelPreference`, Settings' `SettingsDataStore.defaultChatModel`) while the list still has it,
  the engine's otherwise (`engineDefault`, kept for Settings to name). A turn sent without a model
  goes there too (`chatModel`).
- **`AudioTranscriber` (`SchedulerTranscriber`)** — the composer's speech to text on the scheduler's
  `POST /transcription`. Answers `TranscriptionOutcome` (the words, or a `TranscriptionFailure` plus
  the server's `erreur`), never throws past `:core:data`. The language hint is the device's when it
  is a bare ISO 639-1 code (`isoLanguageOrNull`), nothing otherwise. Bound in `engineModule`.
- **`events(sessionId)` / `allEvents()`** are the live feed, read and parsed on `ioDispatcher`
  whatever scope collects them (20/10/2026): the watcher collects from the shell's main scope, and
  the global feed repeats each tool's whole output on every frame that grows it; parsed on the main
  thread, a scheduled run streaming its tools froze the app.
- **Attention (`Attention.kt`, 03/10/2026)**: `AttentionSignals` decides when a question or a
  finished reply rings (chime when its conversation is on screen, notification otherwise, nothing
  when `SettingsDataStore.attentionSound` is off, each question once per process);
  `EngineAttentionWatcher` folds the global feed for questions and reads the pending list at start,
  and notifies the end of a reminder's turn (a user message opening with `REMINDER_PREFIX`);
  `ConversationRequests` holds the conversation a tapped notification opens. `Push.kt`
  (19/10/2026): `parsePushLine` (one line of the server's ntfy stream: a decision or a question,
  its conversation from the click link), `shouldNotifyPush`; `attentionWatcherRunning` says whether
  the watcher is following the engine in this process. `SchedulerRepository.pushChannel()` reads
  `GET /notifications`. `AttentionNotifier` is
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
