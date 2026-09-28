# core:network

HTTP layer of the engine, the scheduler and the portal. Ktor Client on OkHttp.

## What This Module Provides

- **`api/`** — `AgentEngineApi` (the OpenCode engine: sessions, messages, providers, the event
  feed) and `SchedulerApi` (missions, runs, connectors, consumption, prices, health,
  `POST /transcription` → `TranscriptionRefused` on refusal).
- **`engine/`** — the clients' configuration (`portalService`, `PortalService.ENGINE` /
  `SCHEDULER`, `EngineAuthPlugin`), the addresses (`EngineAccess`), the live feed
  (`EngineEventTransport`, `KtorEngineEventTransport`, `EngineStreamClient`, `EngineEventParser`),
  `EngineHttpException`, and `auth/`: the portal's OAuth client (`EngineTokenClient`, discovery,
  PAR, PKCE, the form-post callback relayed by the scheduler, `EngineGrantRefused`).
- **`client/`** — `CleartextGuardPlugin` (no bearer in the clear to a public host) and the host
  scoping helpers (`isSameServerAuthority`).
- **`sse/`** — `SseLineParser` (the custom event-stream parser over the raw byte stream — not
  Ktor's SSE plugin), `SseEvent`, `SseStreamException`, `SseHttpStatusException`.
- **`di/`** — `networkModule`: the app's `Json` (`librechatJson`) and, per platform, the HTTP
  engine factory (OkHttp). The clients themselves are built in `:core:data`'s `engineModule`.

## The portal's bearer: the only credential for the engine and the scheduler (D-076)

- `EngineAuthPlugin` puts `Proxy-Authorization: Bearer <portal token>` on requests to its client's own
  service, and nothing else. **It never sets `Authorization`**: the edge (nginx) validates the bearer
  through Authelia and then presents the engine's Basic itself. The app kept that password until
  D-076 — a shared service secret on every phone — and the review found it riding the scheduler's
  client too. Don't add a credential here; add it at the edge.
- The engine's and the scheduler's clients are built by one function, `portalService(PortalService.X)`,
  which scopes the bearer to that service's address. `PortalServiceClientTest` (the function) and
  `:core:data`'s `PortalServiceWiringTest` (the Koin module) pin that neither client ever hands the
  bearer to the other's host, nor to a redirect target off its authority.
- One client id, `PORTAL_CLIENT_ID`, used by the PAR, the token calls and the authorization URL alike.

`SchedulerApi.transcribe` is the scheduler's one plain-HTTP route, `POST /transcription`
(multipart: file part `audio` with its real name and `audio/*` type, optional text `langue`; 25 MB
at most; 200 `{"texte"}`, 400/502 `{"erreur"}`, 403). Same client and bearer as the MCP calls, a
longer per-request timeout; every refusal raises `TranscriptionRefused(status, reason)`.

`AgentEngineApi.sendMessage` takes an optional `agent` (absent by default): a chat names `chat` on
every turn (D-077) so a follow-up never falls back to the engine's default agent.

## The Agent engine has two disjoint API worlds

`AgentEngineApi` talks to OpenCode, and OpenCode exposes the *same* session twice: a **classic**
surface under `/session/…` and a **v2** surface under `/api/…` with a durable, resumable event feed.
They look interchangeable. They are not, and picking the wrong one produces no error at all — which
is exactly how the Tasks chat shipped on 29/08/2026 showing an empty transcript and swallowing every
message sent to it.

Measured against the live engine on 29/08/2026:

| | v2 (`/api/…`) | classic (`/session/…`) |
|---|---|---|
| Missions are launched here | no | **yes** (`prompt_async`) |
| Transcript readable | empty, or the request times out before headers | **13 messages, complete** |
| A prompt starts a turn | only if the session has *never* used the classic surface | **always** |

A session that has run `prompt_async` **never executes a v2 prompt**. It answers 200, emits
`prompt.admitted` then `prompted`, and stops — with `delivery: steer` and with `delivery: queue`
alike. Nothing reports a failure; the turn simply never happens.

So everything a mission session needs goes through the classic routes:

- **history** — `GET /session/{id}/message`
- **send** — `POST /session/{id}/message`, synchronous, returning the finished assistant message
  (~2,9 s measured)
- **live tokens** — `GET /event`, which is **global**: one feed carrying every session's frames
  (`message.updated`, `message.part.updated`, `message.part.delta`, `session.idle`), each naming its
  session in `properties.sessionID`. `EngineStreamClient` subscribes once and drops what is not the
  session on screen. There is no per-session classic feed and no resume cursor — a reconnect
  re-reads the transcript instead.

`engineHistoryEvents` replays a fetched transcript as the same `EngineStreamEvent`s a live turn
emits, so the past and the present fold through one reducer and cannot drift apart.

## Key Configuration

- `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false; explicitNulls = false; coerceInputValues = true }`
- Engine and scheduler clients: 10 s to connect, 30 s per request (`portalService`). The event feed
  lifts the request timeout per request (a long poll); a stalled feed is caught by `SseLineParser`'s
  line-read timeout.

## Rules

- API services are thin HTTP wrappers — no business logic.
- Dependencies: `:core:model`, `:core:common`, `:core:logging`, Ktor, kotlinx-serialization, okio
  (PKCE), Kermit, Koin.
- Convention plugins: `librechat.kmp.library` + `librechat.kmp.koin` + `librechat.kotlin.serialization`.
