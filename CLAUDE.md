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
core/common/          → dispatchers and scopes, connectivity, cleartext policy, batched flows
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
  The server's `chat` profile carries what the tasks' did (shell, local files, pages). The
  scheduler decides what a session reaches through the annuaire from WHO speaks to it (D-083,
  22/10/2026, Raphaël's rule), and every session this app starts stands at the titulaire's level
  (`perimetre(niveau="titulaire")`, recorded before the first prompt by
  `EngineMissionRepository.startChat`). The composer offers the model and a connectors sheet
  (24/10/2026, Raphaël's rule) limited to the engine's **direct** connectors (memory, workspace, web,
  shell, annuaire, scheduler, alerts: `directOffered`, their ticks becoming the session's rules
  through `directPerimeter`); what the annuaire serves (mail, messaging, browser, bank, SSH…) is
  never listed nor declared to the engine (D-073). The shell stays the one connector the scheduler
  leaves on demand. One
  screen (`MissionChatScreen`), one route (`EngineChat`) for every session: a chat, a task of an
  earlier version, a scheduled run, whose follow-ups run on `chat` too. A session the scheduler
  recorded at another level (a run stands at `mission`, where `ssh-distant` is forbidden) is
  lifted to the titulaire's level before the person's first message in it (24/10/2026, Raphaël's
  « Go ssh » in a run was refused by the annuaire; `sessionScope` reads the level, `send` lifts, and
  so does an answer to a question since 27/10/2026: a conversation the scheduler opens itself, a
  decision or a reply to follow, stands at the mission's level until the titulaire says a word,
  the kit's review; `liftToTitularIfNeeded`). The
  lift rewrites the rules only from connectors the screen read; unread, it records the level alone
  (`liftToTitular`) and the chip offers a retry (26/10/2026: an empty list stripped every tool).
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
  `retirer_question`, read back through `conversations_lues`; since 27/10/2026 evening also a
  conversation read aloud by phone, and the device tells the scheduler its own mark at every
  `markRead`, `SchedulerApi.markRead` → `marquer_lue`, so the voice reading starts at the first
  message the screen never showed: Raphaël's rule, « uniquement les messages non lus, message
  par message »); for a run, also « had
  something to say »: the scheduler's `historique(json_brut=True)` carries `a_signaler`, false when
  the run answered « RIEN À SIGNALER » (`runHasNews`). A run played inside a conversation (a
  reminder, a relance's decision; the scheduler's `conversation` flag, 26/10/2026) never makes that
  conversation a run: it keeps its title and is unread whenever it moved.
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
  dropped after 10 s and asked again, three times, then the screen says so (23/10/2026). It reads
  the last 40 messages only (`HISTORY_TAIL`, the engine's `limit`; 26/10/2026, Raphaël: switching
  conversations took too long, the heaviest weighed 2.7 MB), « Messages précédents » at the top
  reading the whole transcript (`loadEarlierHistory`, folded under what is on screen: since 26/10/2026 the reducer puts a message it
  has not seen at its place in the order of the ids, which grow with time, instead of appending it). A
  text delta on a part the screen was never told of (opened while the session thought, a feed that
  reconnected) draws nothing and has the transcript read again (`reconcileHistory`, 3 s apart at
  most): its kind is unknown, and drawn as prose a model's reasoning filled the screen then folded
  away at the step's end, the conversation jumping on every step (26/10/2026, Raphaël's recording).
  Reasoning deltas grow their part inside its fold. A message typed while the session
  works goes out at once (23/10/2026): the engine records it and the running turn reads it at its
  next step, never interrupted; send shows over stop as long as something is staged. An image a
  message names in markdown, `![description](https://…)`, is drawn in the message (23/10/2026,
  `MarkdownImages`, the app's Coil loader). A photo staged for a model that does not see images
  (DeepSeek, 24/10/2026) gets a line above the box: the platform's privacy gateway describes it in
  words for that model, a tap opens the model picker (`imageStagedForBlindModel`). Who is blind is
  the gateway's word, relayed by the scheduler's `tarifs` (`ModelPrice.seesImages`, `voit_images`),
  not the engine's: the engine is told such a model takes images so that it forwards them instead
  of refusing them. The top bar carries a **context ring** (24/10/2026, Raphaël: as in Claude's
  app): the share of the model's window the session fills, error colour past 80 %, a tap opening
  a sheet with the count (« 290,8 k sur 1 M jetons »). The count is the engine's, the tokens of the
  last model call (`message.updated` → `MessageStarted.tokens` → `MissionChatState.contextTokens`);
  the window is the gateway's, `max_input_tokens` relayed by the scheduler's `tarifs`
  (`ModelPrice.contextWindow`, `contexte`), for the model of the next message (`contextUsage`).
  **Compaction** (25/10/2026, Raphaël: as on Claude.ai) is the engine's: it resumes the
  conversation when its context nears the window of the model the next message goes to (kit #136
  declares each window), before that message's first call and mid-turn. The app draws it: the
  user message carrying the engine's `compaction` part becomes one line in small print
  (`CompactionNotice`: « Conversation compactée · 894 k jetons économisés », « Compactage de la
  conversation… » while the summary is written); the summary message (`summary: true`,
  `ChatTurn.Assistant.summary`) is never drawn, and the context restarts from its size. The same
  `summary` key is an object of diffs on a user message once its turn ended: only a boolean is
  the flag (`SummaryFlagSerializer`; 25/10/2026, read as a boolean it refused every finished
  conversation's history).
- **Voice mode** (27/10/2026, Raphaël's voice mode, step 3; a drawer entry, `EngineVoice`,
  `EngineVoiceScreen` in `shared/.../engine/voice/`): the phone joins a LiveKit room with the
  platform's voice agent, over data, no phone call and no code to read. The scheduler's `POST /vocal`
  (`SchedulerApi.voiceRoom`, behind the portal) signs the room's token and records the room at the
  titulaire's level; the agent, dispatched to every new room, asks the scheduler and finds the
  titulaire there. The screen shows the agent's state (`lk.agent.state`), what it understood and
  its last sentence (the `lk.transcription` text streams), and a red button hangs up; it joins as
  it opens (microphone asked first, `rememberMicrophoneGate`) and leaves as it closes. The room is
  LiveKit's Android SDK in `:app` (`LiveKitVoiceLink`, behind the shell's `VoiceLink`; the room is
  a call for Android, `CallAudioType`, since 30/10/2026: in communication mode the phone's echo
  canceller removes the agent's own voice from the microphone, where the media type of the first
  version let it come back and interrupt the agent on the speaker; the speakerphone stays the
  preferred output, after a headset); a scheduler without LiveKit settings answers 503 and the screen
  says so. What the agent can do is the server's affair (step 2's tools, Raphaël's rule of sober
  code: the agent reads the conversations and does the rest itself). Since 28/10/2026 the room
  lives on when the app leaves the foreground or the phone locks (Raphaël: « implémente le mode
  arrière-plan pour le mode vocal »): `VoiceCallService` (`:app`), a foreground service of type
  `microphone` that `LiveKitVoiceLink` starts as it joins (from the screen, as Android 14 requires)
  and stops as it leaves; its notification opens the app and hangs up, and swiping the app away
  hangs up too. Leaving the voice screen inside the app still hangs up.
- **Hands-free** (28/10/2026, voice mode step 4: the phone's ears and mouth, the server's brain; a
  button in the conversation's top bar, `handsFree`, shown when the dictation is the phone's). The
  phone's dictation sends each sentence it hears as a message (`sendSpoken`), the reply that lands is
  read aloud by Android's own speech synthesis (`TextToSpeech`, the phone's engine and language,
  `MissionSpeaker.android.kt`), then the mic listens again (`HandsFreeLoop` in the composer: the mic
  is open whenever nobody is talking, closed while Eva answers or reads; a dictation the person
  started is left alone). What is read is the answer's prose without markdown (`speechText`: code
  dropped, an image its description, a link its text, a table cell by cell; `lastAnswerSpeech`,
  `MissionChatUiState.toSpeak`). It ends with the screen (ON_PAUSE) and is refused when the phone
  can only record for the scheduler (no recognition of its own). No wake word: Android gives an
  app none without a third-party model listening all day.
- **Dictation and audio files** (the composer). The mic is heard by the phone itself by default
  (Settings → Dictée, « Dictée sur le téléphone », key `dictation_on_phone`; Raphaël, 26/10/2026:
  local is the default he wants) and listens **without a break until the tap on stop** (same day:
  « il faut absolument qu'il écoute sans s'arrêter jusqu'à ce que j'appuie sur STOP »). Android's
  recognizer stops at a pause and, holding the mic itself, lost the words spoken while it
  restarted; so the app holds the mic (`DictationMic`, an `AudioRecord`, `VOICE_RECOGNITION`
  source, 16 kHz) and the on-device recognizer reads it through a pipe (`EXTRA_AUDIO_SOURCE`,
  Android 13, a segmented session: `onSegmentResults` per stretch of speech, the session ends when
  the pipe closes at stop). A recognizer that still ends at a pause is started again on a new pipe
  fed first with what the mic heard since its `onEndOfSpeech` (`DictationTake`, the last 30 s,
  replayed from 500 ms before): nothing falls in the gap, and silence never ends the dictation
  (`PhoneDictation`, `MissionDictation.android.kt`). Never the network recognizer, which would send
  the voice to Google. With the switch off, before Android 13, or while the phone downloads its
  French, the mic records until stop (MediaRecorder, OGG/Opus 16 kHz 32 kb/s) and the take goes to
  the scheduler's `POST /transcription` (`SchedulerApi.transcribe` → `SchedulerTranscriber`:
  Whisper large-v3 then Cohere Transcribe on the platform's GPU), and so do audio files. A
  dictation lands in the composer and is never sent on its own; an audio file leaves with the
  message as a quoted transcription.
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
  another for one conversation. **Auto** (25/10/2026, Raphaël's rule: the model necessary and
  sufficient, the cheapest): a choice of Settings and of the picker (`EngineSelectableModel.AUTO`,
  id `auto`, never sent to the engine). The first message of a new conversation goes to the
  scheduler's trieur (`POST /palier`, `SchedulerRepository.triage`), which names the tier and its
  model on the chat's provider; the conversation starts on it and keeps it (`resolveModel`), the chip
  showing what Auto chose; a trieur that does not answer leaves it on the ladder's lowest tier
  (`ModelTiers.lowest`, 27/10/2026: sent without a model it ran on the engine's default, Sonnet, which
  Auto never picks), and only with no ladder either on the engine's default. A
  conversation that already ran keeps its model. **A conversation's model is what this device last
  sent it with** (26/10/2026, Raphaël: « le choix du modèle change en cours de route »):
  `ConversationModelStore` (`EngineConversationModels`, device preferences, cleared at sign-out),
  recorded at every send and read ahead of the model the last turn ran on
  (`MissionChatUiState.rememberedModel`, `effectiveModel`), and a last turn that ran on the engine's
  own default nobody chose (not the person's default either) does not hold the conversation: the chip
  and the next message follow the person's default (`chosenRunModel`, 27/10/2026, Raphaël: « je n'ai
  jamais choisi Sonnet »). The engine keeps no model per session,
  and the last turn's model is whoever wrote it: a reminder played back by the scheduler, a relance
  posing its decision in its own conversation, ran on their own model (the palier's Sonnet until
  kit #149), and the chip and the next message followed them. Under the last answer, « Refaire plus fort »
  (`redoStronger`, `MissionChatUiState.canRedoStronger`) sends the last request again, prefixed
  (`REDO_STRONGER_PREFIX`), on the tier above the one that answered, read off the scheduler's ladder
  (`GET /palier` → `ModelTiers.strongerThan`); at the top of the ladder the button is not shown.
- **What the phone keeps to itself** (26/10/2026, the review). An image a message names draws on
  its own from any `https:` host or as a `data:` URL (27/10/2026, Raphaël: « il faut pouvoir
  afficher des images dans les sessions »; the day before only the platform's hosts drew, and a
  search's product photos all showed as links); a cleartext or odd-scheme image becomes a link
  (`insecureImagesAsLinks`, `:core:ui`). Accepted with his rule of openness: a trapped content
  could make the model write an image URL that carries the context out. No SVG decoder. Notifications keep their text off the lock screen (`VISIBILITY_PRIVATE` with a
  public version that only says something waits). An intent's extras name a conversation only by
  a valid engine session id (`isEngineSessionId`). Signing out also revokes the refresh token at
  the portal (`revokeAndForget`, RFC 7009), deletes the opened attachments, the image cache and the
  diagnostic log (`DeviceSession`, `AndroidDeviceSession`). The OAuth mailbox is armed with the
  tour's `state` and ignores a link with another. A copy to the clipboard is marked sensitive. The
  push stream refuses cleartext to a public host and follows no redirection. **Out of sight, the
  phone keeps one connection**, the push stream (26/10/2026 evening, lot 2 of the review): the
  engine's feed closes when the app leaves the foreground (`EngineShellViewModel.pauseAttention`
  at the shell's ON_STOP, `recheck` at ON_START resumes the watch, which re-reads the pending
  questions), and so does the main-thread watchdog (`ForegroundWatchdog`, an activity lifecycle
  counter in `:app`); before, the push service kept the process alive and the phone followed every
  frame of every scheduled run all night. `PushService` starts at sign-in and stops at sign-out
  (`DeviceSession`, `AndroidDeviceSession`), cuts its stream the moment the Settings switch turns
  off, waits for a network instead of retrying blind, posts its notice once, and gives up by
  itself on a signed-out phone. A dictation is closed as a tap on stop when the app leaves
  (ON_PAUSE), and the activity handles its own configuration changes so a rotation keeps the take.
  The persistent journal keeps Info and above. Deliberately not done:
  `FLAG_SECURE` (screen recordings are how Raphaël reports), and encrypting the transcript cache
  (plain gzip under the cache directory, cleared at sign-out).
- **The app's lock** (28/10/2026, Raphaël: « au vu de l'accès que donne l'application Eva, il
  faudrait sécuriser par un mot de passe et optionnellement de la biométrie, comme une application
  bancaire »). A six-digit code, created right after the sign-in (an installed app asks for one at
  its next start), drawn on the app's own keypad (no system keyboard learns it), asked at every
  start and on every return after the delay of Settings → Verrouillage (at once, 1, 5 or 15
  minutes; 1 by default); no switch turns it off. The code is never kept, only a PBKDF2 hash with
  its salt in its own encrypted file (`AppLock`, `AppLockStore`, `:core:data`;
  `AndroidAppLockStore`), the delay measured on the device's uptime. Too simple a code is refused
  (`isTooSimple`: one digit six times, a run up or down). Five wrong codes in a row sign out (the
  count survives a restart; the app stays locked until the sign-out ends, then the portal, its
  password and its second factor are the way back). The fingerprint is optional, offered after the
  code and in Settings: a strong biometric only, through `BiometricPrompt` over a keystore key
  that needs it at each use and that Android voids when a fingerprint is added
  (`Biometrics`, `Biometrics.android.kt`): the code is then asked and the fingerprint turned off.
  `AppLockGate` (`:shared`, `engine/lock/`) stands over the signed-in shell: the app below is
  composed once opened, then kept under the lock (and hidden from accessibility services) so a
  return finds it as it was; a notification's conversation opens once unlocked. `MainActivity` is a
  `FragmentActivity` (the prompt needs one) and keeps conversations out of the recent apps'
  pictures (`setRecentsScreenshotEnabled(false)`, Android 13+; not `FLAG_SECURE`). Signing out
  forgets the lock.
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
- Each client's OkHttp dispatcher allows 32 requests in flight per host (`OkHttpWithRoom`,
  `NetworkPlatformModule.android.kt`), not OkHttp's 5: the two event feeds held two places for
  good, and opening a conversation queued its transcript past the screen's 10 s (25/10/2026).
- **Opening a conversation in a second or two** (26/10/2026, Raphaël: the placeholders stayed 30 s
  to a minute and a later tap worked; the engine itself answers a 40-message transcript in 3 to
  25 ms, the time is the phone's). Everything multiplexes on one HTTP/2 connection per host, which
  a sleep or a change of network leaves dead without a word, and OkHttp never evicts a multiplexed
  connection on a timeout: the connections PING every 20 s (`PING_INTERVAL_SECONDS`), a transcript
  read that hangs drops them all before its retry (8 s per try; the 3 s first try and the 10 s
  opening watchdog of v2026.10.47 and .48 are gone, 26/10/2026 evening: the journal showed 2 to
  5 s reads cut as « hung » and the watchdog watched the wrong thing; the opening logs at Info
  what it kept and how long the read took) (`ConnectionReset`, `OkHttpConnections`:
  the calls cancelled, the pool emptied; `repository.dropConnections()`), and so does a change of
  the DEFAULT network (`ConnectivityObserver.networkChanges`: `registerDefaultNetworkCallback`, the first
  callback skipped, a loss and a gain within 1,5 s folded into one by `LibreChatApplication`; the
  v2026.10.43 regression listened to every network, and the mobile one comes and goes every few
  seconds under Wi-Fi: each time the transcript being read was killed, two minutes of placeholders). The
  transcript goes first: the catalogue (models, connectors, level, prices) is read once it has
  landed (`catalogueRequested`). **The conversation screen collects its state with
  `collectAsState`, never with the lifecycle-bound collector** (26/10/2026 evening, Raphaël's
  recording and journal: the transcript had landed 1,4 s after the tap, the placeholders stayed
  11 s, and the screen's first ON_RESUME came only when the app was left and brought back).
  Navigation 3 gives each entry a lifecycle of its own, capped below RESUMED until it counts the
  entry as shown; a `collectAsStateWithLifecycle` on it then keeps the initial state whatever the
  ViewModel loaded. The screen logs at Warn an entry still not RESUMED 2 s after composition
  (`ENTRY_RESUME_GRACE_MS`): the next journal names the state. A reconciliation
  (`reconcileHistory`) never cancels a read in progress: while a session writes, the deltas come
  faster than a slow read lands. The feed is read ONCE per app (`EngineMissionRepository.feed`,
  `shareIn` on the application scope, 5 s of linger): the conversation and the attention watcher
  used to download the engine's whole traffic twice. The drawer tells a session's kind from its
  last two messages (`CLASSIFICATION_TAIL`), not its whole transcript (ten of them, megabytes, read
  while the person tapped). The kit's nginx now gzips the JSON (`hobbitton-proxy.inc`: 361 KB
  of transcript become a few tens), the SSE untouched. **A conversation opened before shows at
  once** (same day, Raphaël: « il ne redemande pas nécessairement le reste »): the tail the
  opening read is kept on the device (`TranscriptCache`, `FileTranscriptCache`: gzip files under
  the app's cache directory, 40 sessions and 12 MB at most, the oldest out first, a tail over 2 MB
  not kept, nothing in memory between openings, cleared at sign-out), seeded before anything is
  asked (`repository.cachedHistory`), and the engine is read again only if the session's
  `time.updated` changed or the session is busy (`historyUnlessUpdatedAt`: one small read of the
  session and the statuses). A pull to refresh or a retry reads the engine whatever was kept;
  a return to the foreground makes that small check only (`resume`, 26/10/2026 evening; before,
  the whole tail again). **Fewer round trips, off the main thread** (same evening, lot 3 of the
  review): the lists' five reads go together on IO and are kept 30 s for « Plus anciennes » and
  the search (`ListSnapshot`, `RECENT_LIST_REUSE_MS`), the unknown sessions' tails are read
  together; the engine's providers are kept ten minutes (`providerCatalogue`: every send asked
  them again to name the model), the scheduler's identity an hour and its ladder ten minutes,
  all forgotten at sign-out; the kept tail takes the clock the check just read instead of reading
  the session again; the deltas of a frame or so go into the state together (`batched`,
  `:core:common`, 80 ms), where each character redrew the conversation; a conversation left and
  the app left in the same instant mark it read once; a markdown image is decoded at 1 600 px a
  side at most and the image cache holds 64 MB.
- **The shell's view models live in its own stores** (28/10/2026, Raphaël: « ça recommence », a
  conversation grey for good; the journal showed one opening written two to five times in half a
  second, then nothing, not even the guard). Navigation 3's decorator (lifecycle-viewmodel-navigation3
  2.11.0-beta01) marks a popped entry's store for disposal and drops it once nothing holds it, even
  after an equal entry was pushed again and took it: « new conversation » tapped twice, a
  conversation reopened as it faded out, and the view model on screen died before its first read,
  `koinViewModel` handing the dead store a new one at each recomposition. `EntryViewModelStores`
  (`:shared`, `rememberEntryViewModelStoreDecorator`) keeps one store per entry while the entry is
  on the back stack or still drawn, and clears it once it is neither, read off the back stack as it
  is then; each push of a conversation is its own entry (`EngineChat.instance`, out of `equals`,
  `entryKey` its content key, `shellContentKey`). `EntryViewModelStoresTest` drives thousands of
  gestures at random paces through `NavDisplay` and finds Navigation 3's decorator killing a drawn
  view model; none with the shell's. The journal writes « closed » when a conversation's view model
  goes, « before its transcript landed » when that is so.
- **The app's own threads** (27/10/2026, Raphaël: a conversation stayed grey for good, and the
  journal had not one line for it, not even the device-cache look that opens every opening). The
  app's blocking work never queues on the shared `Dispatchers.IO` any more: `ioDispatcher` and
  `KoinQualifiers.IO` are `AppThreads.io` (`:core:common`), an elastic pool (`eva-io-N`, a thread
  per task in flight); Ktor's engines run on `AppThreads.net` (`eva-net-N`), where their blocking
  body reads (the feed's, for good) cost a thread and never another request. Every OkHttp socket is
  tracked (`TrackedSockets`): `dropAll` closes them for real, which ends the reads and writes a dead
  link holds, and its caller waits 1,5 s at most. The feed's socket timeout is 150 s (the engine
  beats every ~10 s; infinite, it took the write timeout off the connection every request shared).
  The opening logs « opening » before any hop, bounds the device's copy at 2 s, and its guard
  (`watchOpening`) reads again, connections dropped, when no step has moved for 10 s (twice at
  most), writing the step and `threadReport()` to the journal. In front, `startThreadHealthWatch`
  (`:app`) probes `Dispatchers.IO` every 15 s and reports a pool past 32 threads, with the same
  report: the next journal names what held the threads.
- The feed is global and heavy (a tool's whole output again on every frame that grows it):
  `EngineMissionRepository.events` / `allEvents` read and parse it on the IO dispatcher whoever
  collects (20/10/2026: parsed on the main thread by the attention watcher, a scheduled run's
  tools froze the app). The phone's own traces come out through Settings → Plateforme → « Partager
  le journal de diagnostic » (`DiagnosticLogShare`).
