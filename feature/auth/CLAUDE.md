# feature:auth

The app's only way in (D-077): `PortalSignInScreen`, composed by `EngineNavHost` while signed out.

## Screen

- `PortalSignInScreen` (`screen/PortalLogin.kt`) — since lot 4 (10/10/2026) the bow tie and
  « Butler » in the serif, one « Se connecter » button, and the three addresses of the platform
  (engine, scheduler, portal: `EngineSettingsStore`, validated by `validateEngineAddresses`) folded
  away when the build names a platform (`PlatformDefaults`, from `platform.properties`), shown
  from the start otherwise or on « Autre plateforme ». Then the portal's round trip in the app's
  own web view (`PortalWebView`, `:core:ui`), full screen over the form. Calls `onSignedIn` once
  the portal's tokens are held. `CaptureSignInTest` renders it (`-Pcaptures`, `build/captures/`).

## ViewModel

- `PortalLoginViewModel` — fills the form from the stored addresses, saves them, then runs
  `PortalTasksSignIn` (PAR, PKCE, `state`, the code relayed by the scheduler, the exchange).
  Every main-frame navigation of the web view goes through `onNavigation`
  (`classifyPortalNavigation`): `http(s)` loads, the app scheme goes to the callback mailbox, other
  schemes are refused. Problems are typed (`PortalLoginProblem`: not ready, unreachable, refused,
  interrupted).
- Its two engine dependencies are resolved with `getOrNull` (`authModule`); without them the form
  is not offered (`PortalLoginUiState.available`) and the screen says so. The build's platform
  (`PlatformDefaults`, bound by `:app`) is a third `getOrNull`: complete, it prefills the form
  (`prefilled`, `addressesShown`); the addresses a previous sign-in stored still win over it, and
  another platform than the build's unfolds the fields.

## Localization

Strings live in `src/commonMain/composeResources/values*/strings.xml` (nine locales).
