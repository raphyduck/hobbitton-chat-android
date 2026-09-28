# feature:auth

The app's only way in (D-077): `PortalSignInScreen`, composed by `EngineNavHost` while signed out.

## Screen

- `PortalSignInScreen` (`screen/PortalLogin.kt`) — the three addresses of the platform (engine,
  scheduler, portal: `EngineSettingsStore`, validated by `validateEngineAddresses`), then the
  portal's round trip in the app's own web view (`PortalWebView`, `:core:ui`), full screen over
  the form. Calls `onSignedIn` once the portal's tokens are held.

## ViewModel

- `PortalLoginViewModel` — fills the form from the stored addresses, saves them, then runs
  `PortalTasksSignIn` (PAR, PKCE, `state`, the code relayed by the scheduler, the exchange).
  Every main-frame navigation of the web view goes through `onNavigation`
  (`classifyPortalNavigation`): `http(s)` loads, the app scheme goes to the callback mailbox, other
  schemes are refused. Problems are typed (`PortalLoginProblem`: not ready, unreachable, refused,
  interrupted).
- Its two engine dependencies are resolved with `getOrNull` (`authModule`); without them the form
  is not offered (`PortalLoginUiState.available`) and the screen says so.

## Localization

Strings live in `src/commonMain/composeResources/values*/strings.xml` (nine locales).
