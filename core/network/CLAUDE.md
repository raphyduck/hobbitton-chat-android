# core:network

Ktor HttpClient, API service classes, SSE streaming client, auth interceptor. All HTTP communication lives here.

## What This Module Provides

- **Ktor HttpClient factory** (`di/NetworkModule.kt`): Provides singleton `HttpClient(OkHttp)` with ContentNegotiation, Logging, HttpTimeout, HttpRequestRetry, and AuthInterceptorPlugin via Koin module.
- **AuthInterceptorPlugin** (`client/AuthInterceptor.kt`): Custom Ktor plugin that injects `Authorization: Bearer` on outgoing requests and retries on 401 after refreshing tokens. Skips auth endpoints (`auth/login`, `auth/register`, `auth/refresh`, etc.).
- **TokenManager interface** (`client/TokenManager.kt`): `getAccessToken()`, `setTokens()`, `refreshAccessToken()` (Mutex-guarded), `clearTokens()`, `sessionExpiredFlow`. Implemented in `:core:data`.
- **ServerUrlProvider interface** (`client/ServerUrlProvider.kt`): Resolves the user-configured base URL. Implemented in `:core:data`.
- **API services** (`api/`): One class per domain -- `AuthApi`, `ConversationsApi`, `MessagesApi`, `ChatStreamApi`, `FilesApi`, `AgentsApi`, `PresetsApi`, `PromptsApi`, `TagsApi`, `ShareApi`, `ConfigApi`, `EndpointsApi`, `BalanceApi`, `UserApi`, `SearchApi`. Each takes `HttpClient` as a constructor parameter, wired via Koin.
- **SSE client** (`sse/`): `SseClient`, `SseEvent`, `SseEventParser`, `SseConnectionManager`.
- **DTO mappers** (`mapper/`): Convert network DTOs to domain models from `:core:model`.

## SSE Streaming Architecture

**Do NOT use Ktor's SSE plugin.** Use the custom line parser over raw `ByteReadChannel`.

Two-phase protocol:
1. `POST /api/agents/chat` with full message payload -> returns `{ streamId }` (streamId === conversationId)
2. `GET /api/agents/chat/stream/:streamId` opens the SSE event stream

Legacy single-phase path (OpenAI Assistants): POST body is the SSE stream itself. Both paths need the custom parser.

`SseConnectionManager` handles lifecycle: start, reconnect with exponential backoff (1s/2s/4s/8s, max 5 retries), abort via `POST /api/agents/chat/abort`, and exposes `StateFlow<StreamingState>`.

On reconnection, append `?resume=true` to get a `sync` event with `runSteps[]` + `aggregatedContent[]`.

## Key Configuration

- `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false; explicitNulls = false; coerceInputValues = true }`
- `socketTimeoutMillis = 120_000` (SSE streams override to `Long.MAX_VALUE`)
- Browser-like `User-Agent` header required -- backend `ua-parser-js` middleware may reject non-browser UAs.

## Error Handling

- `HttpResponseValidator` in the client converts non-2xx to exceptions.
- API services throw on error. Repositories in `:core:data` catch via `safeApiCall` from `:core:common`.
- Respect `429 Too Many Requests` and parse `Retry-After` header on auth endpoints.

## Rules

- Dependencies: `:core:model`, `:core:common`, Ktor bundles, kotlinx-serialization, Timber, Koin.
- Convention plugins: `librechat.android.library` + `librechat.android.koin` + `librechat.kotlin.serialization`.
- API services must not contain business logic -- they are thin HTTP wrappers.
- All `arg`-wrapped endpoints must match the backend pattern: `setBody(mapOf("arg" to mapOf(...)))`.
