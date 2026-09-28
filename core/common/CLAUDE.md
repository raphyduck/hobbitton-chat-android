# core:common

Kotlin utilities shared by all modules. The lowest layer — no other `:core:*` module is a
dependency.

## What This Module Provides

- **Result** (`result/Result.kt`): `Success<T>`, `Error(exception, message)`, `Loading`;
  `safeApiCall` / `onApiDispatcher` (below); `ApiException` and the failure classification that
  screens server text before it can reach the UI (`FailureMessages.kt`).
- **Dispatchers and scope** (`di/`): `KoinQualifiers.IO` / `Default` / `Main` for dispatchers,
  `ApplicationScope` for the process-long coroutine scope, and the qualifiers of the engine's,
  scheduler's and portal's HTTP clients. Always inject dispatchers — never hardcode
  `Dispatchers.IO`; the one exception is `safeApiCall` / `onApiDispatcher`, which read the platform
  `ioDispatcher` directly.
- **ConnectivityObserver**: wraps Android's `ConnectivityManager.NetworkCallback`; drives the
  offline banner.
- **CleartextPolicy**: which `http://` hosts are private enough to talk to in the clear.
- **AppInfo**: version name/code and git SHA (baked into `BuildConfig`) for the startup header.

## safeApiCall / onApiDispatcher

`safeApiCall { }` runs the block on the IO dispatcher and turns a failure into a displayable
`Result.Error`. `onApiDispatcher { }` is the same hop **without** the error mapping, for calls that
classify their own failures. The hop lives here rather than at the call sites because Ktor resumes
in the caller's context: body deserialization and the token renewal a request drives would
otherwise run wherever the call was launched from — the UI thread, from `viewModelScope`.

## Rules

- **No network or data dependencies.** This module must not depend on `:core:network`,
  `:core:data` or `:core:model`.
- Dependencies: `coroutines-core`, `coroutines-android`, Koin, Kermit.
- Convention plugins: `librechat.kmp.library` + `librechat.kmp.koin`.
