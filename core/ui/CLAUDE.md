# core:ui

Material 3 theme and the Compose pieces two features share. Purely presentational — no business
logic, no ViewModels, no repositories.

## What This Module Provides

### Theme (`theme/`)
- `Theme.kt`: `LibreChatTheme` wrapping Material 3 `MaterialTheme` with Butler's one skin
  (10/10/2026): `Color.kt` (`ButlerLightColors` / `ButlerDarkColors`, fixed warm neutrals and a
  brick accent, nothing generated from a seed or the wallpaper), `Type.kt` (`butlerTypography`:
  Source Serif 4 from `composeResources/font/` for display, headline and `titleLarge`, the system
  sans below; licence in `LICENSE-SourceSerif4.md`), `Shape.kt`.
- `LocalAppLocale.kt` / `AppLocale`: applies the stored app language at the root.

### Markdown (`markdown/`)

`MarkdownParsing.kt` — the block-level parser (`parseMarkdownSegments`, `MarkdownSegment`,
`InlineSegment`, tables, LaTeX detection). `MarkdownTheme.kt` — how rendered markdown *looks*:
`chatMarkdownColors`, `chatMarkdownTypography` and `TextStyle.scaleFontSize`. `StreamingCursor.kt`
— the cursor drawn at the end of a streaming answer. **Not a renderer**: `feature/tasks` renders
with the `com.mikepenz` multiplatform-markdown-renderer itself (`MissionMarkdown`). This module
declares the library as `api` because `chatMarkdownColors`/`chatMarkdownTypography` return its
types.

### Composer look (`input/`)

`ChatInputBox` (the raised box: lightest surface, hairline, a shadow on the light page only),
`ChatInputField` (a bare `BasicTextField` with the box's own margins), `ChatInputPill` (a plain
label with a chevron, no fill), `ChatInputDefaults` (shape, fill, border, shadow, keyboard options,
control size) and `ComposerSendButton` (hidden until there is something to send; brick to send, dark
to stop) — the message composer's look, used by the mission
conversation's composer.

### Components (`components/`)
- `SectionGroup` / `SectionDivider` / `SectionLabel` — a group of rows on a raised card (the
  composer's surface and hairline), as the settings and the Tasks tab lay them out since lot 4.
- `ShimmerText` — a line whose text colour shimmers while the agent works (« Réflexion… »).
- `ModelPriceTag` / `modelPriceLabel` — a model's price beside its name in every model picker
  (« price unknown » is never rendered as a zero).
- `PlatformBackHandler` — predictive back, multiplatform.

### Portal web view (`web/`, D-076)
- `PortalWebView` — the web view the sign-in runs in (Authelia portal, the tasks' consent page).
  Here because two features host it (`feature/auth`'s sign-in, `feature/tasks`' re-sign-in) and one
  web view means one cookie jar, which is the point. It knows no scheme or host: every main-frame
  navigation is offered to the caller first, only `http(s)` is ever loaded, TLS errors cancel.
  JavaScript and DOM storage are on because the portal is a single-page app.

### Utilities (`util/`)
- `SafeUriHandler` — the one link gate: http(s) and mailto only.
- `copyToClipboard` (`PlatformClipboard`).

## Rules

- **No business logic.** No ViewModels, no repository calls, no use cases.
- All components must be stateless or hoist state to the caller.
- Dependencies: `:core:model`, the mikepenz markdown renderer (`api`), Compose,
  Kermit. **Not `:core:data`** — the font-size multiplier is passed in as a `Float`.
- Convention plugins: `librechat.kmp.library` + `librechat.kmp.compose`. Resources are public
  (`publicResClass = true`) so features can read this module's strings.
- No DI in this module. **One exception**, and it is a lookup rather than an injection:
  `util/PlatformClipboard.android.kt` reaches the application `Context` through `GlobalContext` to
  touch the system clipboard.

## Compose Performance Rules

These rules apply to all composables across the app, not just core:ui.

### UI State Architecture

1. **Single UI state data class per screen.** Each ViewModel exposes ONE `StateFlow<XyzUiState>` — never 5+ individual StateFlows that each trigger recomposition independently.

2. **UI state contains only display-ready data.** The ViewModel maps domain models to minimal display data classes. Composables should never receive full domain models.

3. **Mark UI state classes `@Immutable`.** This lets the Compose compiler skip recomposition when the reference hasn't changed. All fields must be `val` with stable types.

4. **Collect state at the narrowest scope.** If only the drawer uses drawer state, collect inside the drawer — not in the parent that also owns the NavDisplay. State changes should only recompose the composable that reads them.

5. **Use `SharingStarted.Eagerly`** for UI state that should be ready before the composable subscribes (avoids empty→populated two-phase render jank on first frame).

### Stability

6. **All types passed to composables must be stable.** Primitives, `String`, `@Immutable` data classes, and enums are stable. Standard `List`/`Set`/`Map` are NOT stable by default — they are declared stable in `compose-stability.conf` at the project root.

7. **Never pass lambdas that capture unstable references.** Prefer method references (`viewModel::doThing`) or `remember`'d lambdas over inline lambdas that capture changing state.

### LazyColumn / LazyList

8. **Always provide `key`** on `items()` calls. Keys must be unique, stable identifiers (e.g., a session id).

9. **Always provide `contentType`** when a LazyColumn has mixed item types (headers, content rows, loading indicators). This enables Compose to reuse compositions across items of the same type.

10. **One element per `item {}` block.** Multiple composables in one block prevents independent recycling.

11. **No nested same-direction scrollables.** Never put `LazyColumn` inside `verticalScroll` — combine into one `LazyColumn` using `item {}` blocks.

### Avoiding Unnecessary Work

12. **Move data transformations to the ViewModel.** Sorting, filtering, grouping, and model mapping happen in the ViewModel — not in `remember` blocks in composables.

13. **Cache expensive objects as top-level constants.** `RoundedCornerShape`, `Regex`, `DateTimeFormatter` — allocating these per-item per-frame is wasteful.

14. **Use `background(color, shape)` instead of `clip(shape).background(color)`.** The `clip` modifier creates a persistent clip layer per composable; `background` with a shape parameter draws the clipped background without the layer overhead.

15. **Avoid `copy(alpha = ...)` on colors.** Alpha-blended colors force GPU compositing. Use opaque colors from the theme where possible.

16. **Defer state reads to later phases.** Use lambda-based modifiers (`Modifier.offset { }`, `Modifier.drawBehind { }`) instead of value-based ones (`Modifier.offset(x, y)`, `Modifier.background(animatedColor)`) for scroll/animation state.
