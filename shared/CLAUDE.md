# Shared Module

The engine shell, and the Koin module list the application starts from. KMP library
(`commonMain` + `androidMain`), Android target only.

## `engine/`

- `EngineNavHost` — the app's root (D-077): the portal sign-in (`PortalSignInScreen`) when signed
  out, the chat under a drawer when signed in. Re-checks the signed-in state on every return to the
  foreground. Provides `SafeUriHandler` (http(s) and mailto only) to everything below it.
- `EngineShellViewModel` — signed-in state (`isPortalSignedIn`), the drawer's recent conversations
  (chats, and the tasks a person started — not the scheduler's runs), the theme, and sign-out: `PortalSignOut` (the portal's tokens and web-view cookies), then this device's
  session kinds and reading positions. Every engine dependency is nullable (`getOrNull`).
- `EngineNavigator` — the back stack: the chat (or a task opened from the drawer) as root, a new
  chat's blank entry replaced in place once the engine has the session; Tasks, mission runs, usage,
  settings and instructions pushed on top. « New task » pushes a blank `MissionChat` whose composer
  starts the task, replaced in place the same way (`taskStarted`).
- `EngineDrawer` (Claude's layout since lot 3, 10/10/2026) — a search over the titles (local
  filter), two short entries (new chat, Tasks), the recent conversations as bare titles under day
  headers (Today / Yesterday / Previous 7 days / Older, in the phone's time zone: `groupRecent` in
  `RecentGrouping.kt`, the offset from `localUtcOffsetMillis`, actual in `androidMain`), a small
  « Tâche » tag on a task, a dot of the accent while one answers, a short age; placeholder rows on
  a first load. At the foot, the account (`DrawerAccount`: the portal's name from
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
  Notifications (the switch), Assistant (instructions, usage), Plateforme (the addresses, read
  only), and sign-out alone at the foot. Test tags `settings_attention_sound`,
  `settings_instructions`, `settings_sign_out`, `settings_sign_out_confirm` are kept.
  `EngineInstructionsScreen` + `EngineInstructionsViewModel` — the global instructions' editor over
  `GlobalProfileEditor` (Enregistrer / Annuler; the MCP servers an earlier build stored are kept as
  they are, never edited here).

## `di/`

- `SharedKoinModules.kt` — `sharedKoinModules`: common, logging, network, data, auth and this
  module's `sharedAppModule`. The engine graph (`engineModule`, `tasksModule`) is added next to it
  by `LibreChatApplication`. Verified by `:app`'s `KoinGraphVerificationTest`.
- `AppModule.kt` — `sharedAppModule`: the shell's and the instructions' view models.
