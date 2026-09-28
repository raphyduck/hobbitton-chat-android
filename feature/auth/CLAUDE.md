# feature:auth

## Screens
- **ServerUrlScreen** -- first-run server URL entry with validation and QR scan option
- **LoginScreen** -- email/password + social OAuth buttons + LDAP username mode
- **RegisterScreen** -- name, username, email, password, confirm password
- **ForgotPasswordScreen** -- email entry for password reset link
- **TwoFactorScreen** -- 6-digit OTP input with backup code fallback

## Navigation
- Sealed interface: `AuthRoute : NavKey` with typed route classes
- Routes: `ServerUrl`, `Login`, `Register`, `ForgotPassword`, `TwoFactor(tempToken)`, `VerifyEmail(email)`, `ResetPassword(userId, token)`, `Terms` (all `@Serializable`)
- Feature entries registered via `EntryProviderScope<NavKey>.authEntries()`
- Flow: `ServerUrl` → `Login` → (2FA if `tempToken` returned) → `onAuthComplete`
- Register and ForgotPassword are lateral routes from Login
- `TwoFactor(val tempToken: String)` data class carries the nav argument directly

## OAuth Flow
- LibreChat mounts its OAuth routes at **`/oauth/{provider}`** (v0.8.7), not `/api/oauth/…`:
  `oauthEntryUrl()` builds it for every launcher.
- **The portal — the only sign-in (hobbitton, D-076, then D-077).** `PortalSignInScreen` +
  `PortalLoginViewModel`: the three addresses (engine, scheduler, portal — `EngineSettingsStore`,
  validated by `validateEngineAddresses`, scheduler required), then the portal's round trip
  (`PortalTasksSignIn` → `EngineSignInLauncher`: PAR, PKCE, `state`, code relayed by the scheduler)
  hosted in the app's own `PortalWebView`. The scheduler page's hop to `at.hobbitton.chat://oauth`
  is caught by the web view (`classifyPortalNavigation`) and dropped in the callback mailbox.
  **Since D-077 there is no LibreChat step**: no `/oauth/openid`, no `refreshToken` cookie read, no
  chat session. The view model belongs to the activity — `consumeSignedIn()` lowers its flag once
  the screen has handed over, or a sign-in after a sign-out would go unheard. On iOS (no engine
  graph) the screen says the sign-in is not available.
- `LoginScreen` is back to its upstream form (email/password + social providers); only the legacy
  shell (iOS) still shows it.
- Other providers (and iOS): Custom Tab / `ASWebAuthenticationSession`, then `extractTokenFromCookies`
  on resume. **Known, inherited limitation:** a browser tab has its own cookie jar, so that read cannot
  see the cookie the server set there. Only `openid` is configured on the servers this fork targets.
- Cookie is cleared after extraction to prevent stale reads.

## Token Storage
- Tokens stored in `EncryptedSharedPreferences` via `TokenDataStore` in `:core:data`
- Refresh token sent as Cookie header (backend reads `cookies.parse(req.headers.cookie)`)
- Access token sent as Bearer header
- Token refresh is an explicit POST, not automatic cookie-based

## ViewModels
- One ViewModel per screen: `ServerUrlViewModel`, `LoginViewModel`, `RegisterViewModel`, `ForgotPasswordViewModel`, `TwoFactorViewModel`
- All use `AuthRepository` from `:core:data`

## Key Implementation Notes
- If server URL is already stored, skip ServerUrl screen on launch
- Third-party social logins use Custom Tabs, not a WebView (RFC 8252). The deployment's own portal
  (`openid`, D-076) is the exception: it needs the app's cookie jar, and it is not a third party.
- `openidAutoRedirect` from server config triggers automatic redirect instead of showing login form
- LDAP mode: show "Username" field instead of "Email" (check server config)

### Terms Screen
- `TermsScreen` + `TermsViewModel` — displays server terms, "I Accept" button
- Route: `Terms` data object (part of `AuthRoute` sealed interface) in `AuthNavigation.kt`
- Loads terms text via `UserRepository`, posts acceptance on confirm
- `TermsViewModel.consumeAccepted()` resets navigation trigger
- **Gotcha**: Terms check should happen after login if `startupConfig.requireTerms` is true
- **Note**: `VerifyEmailScreen` already existed pre-Round 2

### Localization
- `strings.xml` created for all 8 modules (app, core/ui, feature/auth, chat, conversations, settings, agents, files)
- Contains key toolbar titles, button labels, section headers — NOT exhaustive extraction
- Full string extraction is a future pass
