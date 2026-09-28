# App Module

Single Activity. `MainActivity` is the sole entry point; `LibreChatApplication` starts Koin.

## MainActivity

- Composes `EngineNavHost` (`:shared`) under `LibreChatTheme`, once the persisted theme
  (`ThemeDataStore`) and language (`SettingsDataStore`) have been read, so the first frame never
  shows the wrong theme or locale.
- Offline banner: `ConnectivityObserver` drives a strip above the shell when the network is lost.
- **One link**: the portal's return, `at.hobbitton.chat://oauth` (see the manifest's comment for
  why the code comes back through the scheduler and a deep link). `handleIntent` hands it to
  `EngineCallbackDelivery`; anything else is logged and ignored. The launch intent is processed on a
  fresh start only; `onNewIntent` handles the rest.
- Clears the dynamic home-screen shortcuts earlier builds published (they deep-linked into
  LibreChat's chat).

## LibreChatApplication

- Logcat through `RedactingLogWriter` (floored and scrubbed in release), installed before Koin.
- `startKoin`: `sharedKoinModules + engineModule + tasksModule`, `allowOverride(false)`.
- Starts `LegacyLibreChatCleanup` once: removes what LibreChat left on an upgraded device.
- Diagnostics after Koin: the persistent log writer, the crash record, the startup header, the
  main-thread watchdog. Best-effort — a failure there never blocks launch.
- Coil's singleton image loader, on its own bare Ktor client (cleartext guard only, no identity).

## Tests

- `KoinGraphVerificationTest` — verifies `sharedKoinModules`, whitelisting the types bound by the
  engine graph and the other modules.
- `EngineNavigatorTest` — the shell's back stack.
- `DataExtractionRulesTest` — backup and device-transfer exclusions (`res/xml/data_extraction_rules.xml`).

## Dependencies

`:shared`, all `:core:*`, `:feature:auth`, `:feature:tasks`. Convention plugins:
`librechat.mobile.application`, `librechat.mobile.compose`, `librechat.mobile.koin`.
The Kotlin namespace stays `com.garfiec.librechat`; the installed identity is `at.hobbitton.chat`.
