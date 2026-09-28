# Shared Module

The engine shell, and the Koin module list the application starts from. KMP library
(`commonMain` + `androidMain`), Android target only.

## `engine/`

- `EngineNavHost` — the app's root (D-077): the portal sign-in (`PortalSignInScreen`) when signed
  out, the chat under a drawer when signed in. Re-checks the signed-in state on every return to the
  foreground. Provides `SafeUriHandler` (http(s) and mailto only) to everything below it.
- `EngineShellViewModel` — signed-in state (`isPortalSignedIn`), the drawer's recent chats, the
  theme, and sign-out: `PortalSignOut` (the portal's tokens and web-view cookies), then this device's
  session kinds and reading positions. Every engine dependency is nullable (`getOrNull`).
- `EngineNavigator` — the back stack: the chat as root, a new chat's blank entry replaced in place
  once the engine has the session; Tasks, mission runs, usage, settings and instructions pushed on
  top.
- `EngineDrawer`, `EngineSettingsScreen` (theme, platform addresses read-only, sign-out),
  `EngineInstructionsScreen` + `EngineInstructionsViewModel` — the global instructions' editor over
  `GlobalProfileEditor` (Enregistrer / Annuler; the MCP servers an earlier build stored are kept as
  they are, never edited here).

## `di/`

- `SharedKoinModules.kt` — `sharedKoinModules`: common, logging, network, data, auth and this
  module's `sharedAppModule`. The engine graph (`engineModule`, `tasksModule`) is added next to it
  by `LibreChatApplication`. Verified by `:app`'s `KoinGraphVerificationTest`.
- `AppModule.kt` — `sharedAppModule`: the shell's and the instructions' view models.
