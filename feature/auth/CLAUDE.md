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
- **`openid` on Android — the single sign-in (hobbitton, D-076).** `PortalLoginViewModel` runs it in
  an embedded web view (`PortalWebView`, `:core:ui`) over the login screen:
  1. load `{server}/oauth/openid`; the edge sends it through the Authelia portal (password + 2FA);
  2. the navigation back out of `/oauth` on the chat origin (`classifyPortalNavigation`, `:core:data`)
     is stopped — never loaded: the web client would spend the refresh token itself — and the
     `refreshToken` cookie is read from `CookieManager` (retried briefly), cleared, and handed to
     `AuthRepository.loginWithOAuthToken`, the path every sign-in ends on;
  3. in the **same** web view (same jar, so the portal session is there), the tasks' portal round trip
     runs (`PortalTasksSignIn` → `EngineSignInLauncher`): only the consent click is left. The
     scheduler page's hop to `at.hobbitton.chat://oauth` is caught by the web view and dropped in the
     callback mailbox.
  The screen is left once both steps are over; closing the view during step 3 keeps the chat and skips
  the tasks. The email/password form stays below as a fallback while the server accepts it.
- Other providers (and iOS): Custom Tab / `ASWebAuthenticationSession`, then `extractTokenFromCookies`
  on resume. **Known, inherited limitation:** a browser tab has its own cookie jar, so that read cannot
  see the cookie the server set there. Only `openid` is configured on the servers this fork targets.
- Cookie is cleared after extraction to prevent stale reads; `checkOAuthResult` is skipped while the
  portal web view is open (it owns that cookie).

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
