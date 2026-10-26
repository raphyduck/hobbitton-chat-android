# core:common

Kotlin utilities shared by all modules. The lowest layer — no other `:core:*` module is a
dependency.

## What This Module Provides

- **`ApiException`** (`result/`): a non-2xx answer with its status, read by the portal's token
  client to tell a refusal from a hiccup. The `Result` / `safeApiCall` layer around it went with
  LibreChat (26/10/2026, the review).
- **Dispatchers and scope** (`di/`): `KoinQualifiers.IO` / `Default` for dispatchers,
  `ApplicationScope` for the process-long coroutine scope, and the qualifiers of the engine's,
  scheduler's and portal's HTTP clients. Always inject dispatchers — never hardcode
  `Dispatchers.IO`. The platform `ioDispatcher` is read directly only where a repository hops a
  whole block off the caller's thread: Ktor resumes in the caller's context, so body decode and the
  token renewal a request drives would otherwise run wherever the call was launched from, the UI
  thread for anything on `viewModelScope`.
- **`Flow.batched`** (`flow/`): the items of a kind gathered for a few milliseconds and emitted as
  one list, the others at once, in order (the conversation's deltas, 26/10/2026).
- **ConnectivityObserver**: wraps Android's `ConnectivityManager.NetworkCallback`; drives the
  offline banner.
- **CleartextPolicy**: which `http://` hosts are private enough to talk to in the clear.
- **AppInfo**: version name/code and git SHA (baked into `BuildConfig`) for the startup header.

## Rules

- **No network or data dependencies.** This module must not depend on `:core:network`,
  `:core:data` or `:core:model`.
- Dependencies: `coroutines-core`, `coroutines-android`, Koin, Kermit.
- Convention plugins: `librechat.kmp.library` + `librechat.kmp.koin`.
