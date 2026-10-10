# core:model

`@Serializable` models shared by network, data and feature modules. Pure Kotlin.

## What This Module Provides

- **`engine/`** — the OpenCode engine's wire models: sessions (`EngineSession`,
  `CreateEngineSessionRequest`, `EngineSessionPatch`, `EnginePermissionRule`), messages and parts
  (`EngineMessage`, `EnginePart`, `EngineTokens`), prompts (`EnginePromptRequest`,
  `EnginePromptPart`, `EngineModelRef`), the provider catalogue (`EngineProviderCatalogue`,
  `EngineSelectableModel`), the live feed (`EngineStreamEvent`, `engineHistoryEvents`), a
  mission's verdict (`MissionState`, `judgeMission`) and `EngineFailureKind`.
- **`scheduler/`** — the scheduler's models: scheduled missions and runs, the connector catalogue
  and session scopes, consumption, model prices, provider health.
- **`chat/GlobalProfile`** — the global instructions sent as `system` on every chat and task turn.

## Rules

- **Pure Kotlin only.** No Android framework dependencies. Only dependency:
  `kotlinx-serialization-json`.
- Nullable fields with defaults where the server may omit them; `@SerialName` when the JSON key
  differs from Kotlin naming. The app's `Json` ignores unknown keys — never assume a response is
  exhaustive.
- Convention plugins: `librechat.kmp.library` + `librechat.kotlin.serialization`.
