# Shared Module

The engine shell, and the Koin module list the application starts from. KMP library
(`commonMain` + `androidMain`), Android target only.

## `engine/`

- `EngineNavHost` — the app's root (D-077): the portal sign-in (`PortalSignInScreen`) when signed
  out, the conversation under a drawer when signed in. Re-checks the signed-in state on every return to the
  foreground. Provides `SafeUriHandler` (http(s) and mailto only) to everything below it.
- `EngineShellViewModel` — signed-in state (`isPortalSignedIn`), the Conversations page's sessions
  (`allChats`, every session, unread first) and the drawer's (`chats`: the same read, by latest
  activity; the same sessions in both lists since 19/10/2026), `markRead` (called by `EngineMainLayout` when a conversation opens, is left, or the
  app goes to the background), the theme, and sign-out: `PortalSignOut` (the portal's tokens and web-view cookies), then this device's
  session kinds and read marks. Every engine dependency is nullable (`getOrNull`).
- `EngineNavigator`: the back stack: the conversation as root (`EngineChat`, any session since
  D-082), a new one's blank entry replaced in place once the engine has the session; Programmées,
  usage, settings and instructions pushed on top. Back from a session alone on the stack lands on
  the Conversations page, which takes its place with the menu in place of its back arrow; back from
  there closes the app (Raphaël's rule, 20/10/2026: back closed the app; `backToConversations`, run
  by a `PlatformBackHandler` since Nav 3 hands a one-entry stack's back to the system).
- `EntryViewModelStores` (28/10/2026): the view model store of each back-stack entry, in place of
  Navigation 3's decorator, which cleared a store an equal entry pushed again had taken (a screen
  left grey for good). A store lives while its entry is on the stack or drawn; `shellContentKey`
  names the entries as `NavDisplay` does (a conversation by its push, `EngineChat.entryKey`).
  Cleared when the shell leaves (sign-out), kept across an activity recreation.
- `EngineConversationsScreen` (16/10/2026): every session, the unread under « Non lues », then the
  rest, each by latest activity; pushed like Programmées (`EngineConversations`), pull to refresh.
  Its rows are the drawer's (`RecentChatItem`, an unread title in bold). Both lists read 200
  sessions at a time; « Plus anciennes » at their foot reads 200 more (`loadOlder`,
  `EngineChatsState.more`, 21/10/2026: the lists had stopped at 200, a few days of runs).
- `EngineDrawer` (Claude's layout since lot 3, 10/10/2026) — a search over the titles of every
  session the engine holds, accents folded (21/10/2026: `EngineShellViewModel.search`, a pause of
  300 ms, then `recentChats(query = …)`; the rows already drawn are filtered meanwhile), three short entries (new conversation, Conversations, Programmées), every session (the same as the Conversations page, a small clock on a run) as bare titles, the unread
  first under « Non lues » (20/10/2026), the rest under day headers (Today / Yesterday / Previous 7 days / Older, in the phone's time zone: `groupRecent` in
  `RecentGrouping.kt`, the offset from `localUtcOffsetMillis`, actual in `androidMain`), an unread title in bold,
  a dot of the accent while one answers, a short age; placeholder rows on
  a first load; pulled down, the list re-reads (11/10/2026). At the foot, the account (`DrawerAccount`: the portal's name from
  `SchedulerRepository.identity`, the engine's host), which opens the settings.
- `EngineNavHost` also sets the Nav 3 transitions: pushed screens slide in over a fade, a
  predictive back shrinks the leaving screen; the chat entry's own metadata cross-fades between
  two chats (`TasksRoute.kt`).
- `EngineShellViewModel` also runs the question watch (`EngineAttentionWatcher`) while signed in,
  and exposes the Settings switch for sound and notifications and the conversation a tapped
  notification asks to open (`ConversationRequests`), which `EngineMainLayout` opens as the root.
- `EngineSettingsScreen` (Claude's layout since lot 4, 10/10/2026): the account at the head
  (`DrawerAccount`), then groups of rows on raised cards (`SectionGroup`, `:core:ui`): Apparence
  (theme and text size as segmented controls, the size written to `SettingsDataStore`),
  Notifications (the switch), Assistant (instructions, usage, spending ceilings), Plateforme (the addresses, read
  only, and « Partager le journal de diagnostic », 20/10/2026: `EngineShellViewModel.shareDiagnostics`
  over `DiagnosticLogShare`, bound by `:app`, a no-op without it), and sign-out alone at the foot.
  Test tags `settings_attention_sound`, `settings_instructions`, `settings_diagnostics`,
  `settings_sign_out`, `settings_sign_out_confirm` are kept.
  `EngineInstructionsScreen` + `EngineInstructionsViewModel` — the global instructions' editor over
  `GlobalProfileEditor` (Enregistrer / Annuler; the MCP servers an earlier build stored are kept as
  they are, never edited here).
- `EngineBudgetScreen` + `EngineBudgetViewModel` (12/10/2026), from Settings → Assistant →
  Plafonds de dépense: the conversations' key ceilings per day, per week and over 30 days, in
  dollars, over `ChatBudgetSource` (the scheduler's `/plafonds`). Enregistrer / Annuler; the
  amounts are checked on the phone first (positive, day ≤ week ≤ 30 days), the scheduler's own
  refusal is shown in its words; a scheduler without the route reads « pas encore ».
- `lock/` (28/10/2026): the app's lock. `AppLockGate` over the signed-in shell (create the code,
  unlock with the code or the fingerprint, sign out after the last wrong code), `CodeEntry` (six
  dots, the app's own keypad), `ChangeCodeDialog`, `AppLockViewModel` over `AppLock` (`:core:data`),
  `Biometrics` (`expect`; Android: `BiometricPrompt` with a crypto object, a strong biometric,
  a key voided when fingerprints change). Settings → Verrouillage (`LockSection`,
  `LockSectionContent` for the captures): change the code, the fingerprint, the delay.

## `di/`

- `SharedKoinModules.kt` — `sharedKoinModules`: common, logging, network, data, auth and this
  module's `sharedAppModule`. The engine graph (`engineModule`, `tasksModule`) is added next to it
  by `LibreChatApplication`. Verified by `:app`'s `KoinGraphVerificationTest`.
- `AppModule.kt`: `sharedAppModule`, the shell's, the instructions' and the ceilings' view models.
