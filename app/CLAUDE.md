# App Module

Single Activity architecture. `MainActivity` is the sole entry point, annotated `@AndroidEntryPoint`.

## Navigation

`LibreChatNavHost` is the root composable. It uses adaptive layout based on `WindowSizeClass`:

- **Phone**: `ModalNavigationDrawer` as primary navigation (sidebar-first pattern matching the web frontend). No bottom navigation bar. Drawer opens via hamburger button in chat header or swipe gesture.
- **Tablet** (600dp+ width): Persistent side panel with full conversation list. No `NavigationRail` or bottom bar.

## Sidebar / Drawer Content

`DrawerContent` is the rich sidebar matching the web's left panel:
- "New Chat" button at top
- Search bar with debounce filtering
- Conversation items grouped by date (Today, Yesterday, Previous 7 Days, etc.)
- Each item shows: endpoint icon, title, model name, relative time
- Active conversation highlighted with left border indicator and tinted background
- Footer: links to Agents, Files, Settings
- Sign out button at bottom

## Top-Level Destinations

Defined in `TopLevelDestination` enum: Chat, Conversations, Agents, Files, Settings.
Conversations are integrated into the drawer body. Agents, Files, and Settings are accessible via drawer footer links. All routes remain registered in the NavHost.

## Active Conversation Tracking

`NavHostViewModel.activeConversationId` tracks the currently viewed conversation. Updated automatically from the navigation back stack when the chat route changes. Used by `DrawerContent` to highlight the active conversation.

## Auth & Session

- `NavHostViewModel` observes auth state via `isLoggedIn` flow
- Start destination is `CHAT_GRAPH_ROUTE` if logged in, `AUTH_GRAPH_ROUTE` otherwise
- `sessionExpired` flow triggers nav to auth graph with full backstack clear
- Drawer gestures are hidden during auth flow

## Deep Linking

- Scheme: `librechat://conversation/{conversationId}`
- Handled in `MainActivity.handleDeepLink()` and forwarded to `LibreChatNavHost`
- `onNewIntent` handles deep links when app is already running

## Connectivity

- `ConnectivityObserver` injected into `MainActivity`
- Offline banner animates in/out at top of screen when connection is lost

## Dependencies

This module depends on all `:core:*` and all `:feature:*` modules.
It applies convention plugins: `librechat.android.application`, `librechat.android.compose`, `librechat.android.hilt`.

### Server-Synced Favorites
- `NavHostViewModel` exposes `favorites: StateFlow<Set<String>>` and `toggleFavorite()`
- Currently backed by `SettingsDataStore` (local) — server sync via `UserApi.getFavorites()/updateFavorites()` available but needs wiring
- `DrawerContent` shows star icon per conversation (filled = bookmarked)
- Favorites state passed through `LibreChatNavHost` and `TabletLayout`

### Server Banners
- `NavHostViewModel` fetches banners from `BannerRepository` on init, filters expired via `displayFrom`/`displayTo` with `Instant.parse()`
- Dismissed banner IDs tracked in-memory via `dismissedBannerIds: StateFlow<Set<String>>` (session-scoped, not persisted)
- `BannerDisplay` composable shown at top of content in both `LibreChatNavHost` (phone) and `TabletLayout`
- Banner types: "info" (blue), "warning" (amber), "error" (red) — defaults to "info" if type is null
- **Gotcha**: `displayFrom`/`displayTo` are ISO 8601 strings parsed with `Instant.parse()` — wrap in `runCatching` since the format from the server is not guaranteed

### Preset Navigation
- `PRESET_MANAGER_ROUTE` registered in `SettingsNavigation.kt`
- `PresetManagerScreen` accessible from Settings → Chat section
- Navigation wired in both `LibreChatNavHost` and `TabletLayout`
