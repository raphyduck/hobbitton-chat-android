# Navigation Compose 3: Research Report

> Compiled 2026-04-03. Sources: Android Developer docs, JetBrains Kotlin docs, nav3-recipes repo, Android Developers Blog, community blog posts, and release notes.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Concepts & API](#2-core-concepts--api)
3. [Best Practices](#3-best-practices)
4. [Migration Guide (Nav 2 → Nav 3)](#4-migration-guide-nav-2--nav-3)
5. [Gotchas, Footguns & Complications](#5-gotchas-footguns--complications)
6. [KMP / Compose Multiplatform Considerations](#6-kmp--compose-multiplatform-considerations)
7. [Appendix: Version & Dependency Reference](#7-appendix-version--dependency-reference)

---

## 1. Overview

### What is Navigation 3?

Navigation 3 (Nav3) is a **ground-up rewrite** of Android's Jetpack Navigation, designed specifically for Jetpack Compose. Released as stable **1.0.0 on November 19, 2025**, it replaces the controller-based paradigm of Nav 2 with a **state-based, declarative approach** where the developer owns the back stack directly.

- **Stable release**: 1.0.1 (Feb 11, 2026)
- **Latest**: 1.1.0-rc01 (Mar 25, 2026)
- **Min SDK**: 23
- **Compile SDK**: 36+

### How It Differs from Nav 2

| Aspect | Nav 2 | Nav 3 |
|--------|-------|-------|
| **State ownership** | Library-owned (`NavController`) | Developer-owned (`SnapshotStateList`) |
| **Back stack model** | Hidden internal structure | Transparent mutable list |
| **Navigation API** | `navController.navigate()` | `backStack.add(key)` |
| **Host component** | `NavHost` | `NavDisplay` |
| **Route definition** | `composable<T> { }` in `NavHost` | `entry<T> { }` in `entryProvider` |
| **Layout flexibility** | Single pane only | Multi-pane via Scenes API |
| **Nested graphs** | `navigation<T> { }` | Not needed — flat entry list |
| **Design philosophy** | Opinionated framework | Flexible building blocks |
| **Compose alignment** | Bolted-on Compose support | Compose-first from scratch |

### Founding Principles

1. **You Own the Back Stack** — Navigation state is a `SnapshotStateList<T>` you control directly. Push = `add()`, pop = `removeLastOrNull()`.
2. **Get Out of Your Way** — The library provides open, extensible building blocks rather than a black box.
3. **Pick Your Building Blocks** — Composable components you combine for your specific needs, with recipes for complex scenarios.

### Data Flow

```
User Action → modify backStack (add/remove) → NavDisplay observes change → entryProvider resolves key → NavEntry.content rendered
```

This follows **Unidirectional Data Flow (UDF)**, fully aligned with Compose's declarative model.

---

## 2. Core Concepts & API

### 2.1 Keys (Routes)

Routes in Nav 3 are simple Kotlin data types that implement `NavKey`:

```kotlin
@Serializable
data object Home : NavKey

@Serializable
data class ProductDetail(val id: String) : NavKey

@Serializable
data object Settings : NavKey
```

Keys must be:
- `@Serializable` (for state persistence)
- Implement `NavKey` (marker interface for `rememberNavBackStack`)

### 2.2 Back Stack

The back stack is a `SnapshotStateList<T>` — you own it completely:

```kotlin
// Simple (no persistence across process death)
val backStack = remember { mutableStateListOf<Any>(Home) }

// Persistent (survives config changes + process death)
val backStack = rememberNavBackStack(Home)
```

Navigation is just list manipulation:
```kotlin
// Navigate forward
backStack.add(ProductDetail(id = "123"))

// Navigate back
backStack.removeLastOrNull()
```

### 2.3 NavEntry

`NavEntry` wraps a composable function with its key and optional metadata:

```kotlin
NavEntry(
    key = ProductDetail("123"),
    metadata = mapOf("type" to "detail")
) {
    ProductDetailScreen()
}
```

### 2.4 Entry Provider

Resolves keys to `NavEntry` objects. Two approaches:

**Lambda function:**
```kotlin
entryProvider = { key ->
    when (key) {
        is Home -> NavEntry(key) { HomeScreen() }
        is ProductDetail -> NavEntry(key) { ProductDetailScreen(key.id) }
        else -> NavEntry(Unit) { Text("Unknown") }
    }
}
```

**DSL (recommended):**
```kotlin
entryProvider = entryProvider {
    entry<Home> { HomeScreen() }
    entry<ProductDetail> { key -> ProductDetailScreen(key.id) }
}
```

### 2.5 NavDisplay

The primary UI component that observes the back stack and renders content:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    ),
    sceneStrategies = listOf(DialogSceneStrategy()),
    entryProvider = entryProvider {
        entry<Home> { HomeScreen() }
        entry<ProductDetail> { key -> ProductDetailScreen(key.id) }
    }
)
```

### 2.6 NavEntry Decorators

Decorators wrap each `NavEntry` with additional logic (state holders, ViewModel stores, logging):

| Decorator | Purpose |
|-----------|---------|
| `rememberSaveableStateHolderNavEntryDecorator()` | Persists `rememberSaveable` state across config changes and process death. **Should be first in the list.** |
| `rememberViewModelStoreNavEntryDecorator()` | Scopes `ViewModel`s to individual `NavEntry` lifecycle. Requires `androidx.lifecycle:lifecycle-viewmodel-navigation3` dependency. |
| `rememberSharedViewModelStoreNavEntryDecorator()` | Enables ViewModel sharing between parent/child entries. |

Custom decorator example:
```kotlin
class LoggingDecorator<T : Any> : NavEntryDecorator<T>(
    decorate = { entry ->
        Log.d("Nav", "Entered ${entry.contentKey}")
        entry.Content()
    },
    onPop = { key -> Log.d("Nav", "Popped $key") }
)
```

### 2.7 Scenes & SceneStrategy

**Scenes** render one or more `NavEntry` instances. **SceneStrategies** determine which Scene to use based on the current entries.

**Built-in strategies:**
- `SinglePaneSceneStrategy` — Default, shows topmost entry
- `DialogSceneStrategy` — Renders entries with dialog metadata as overlays
- `ListDetailSceneStrategy` (from `material3-adaptive-navigation3`) — Adaptive list-detail layout

**Custom strategy example (list-detail):**
```kotlin
class ListDetailSceneStrategy<T : Any>(
    val windowSizeClass: WindowSizeClass
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>
    ): Scene<T>? {
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND))
            return null
        val detailEntry = entries.lastOrNull()?.takeIf { it.metadata.contains(DetailKey) } ?: return null
        val listEntry = entries.findLast { it.metadata.contains(ListKey) } ?: return null
        return ListDetailScene(
            key = listEntry.contentKey,
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = detailEntry
        )
    }
}
```

### 2.8 Metadata

Arbitrary `Map<String, Any>` attached to `NavEntry` or `Scene` for communication between components:

```kotlin
// Type-safe metadata DSL (1.1.0-beta01+)
entry<Home>(
    metadata = metadata {
        put(NavDisplay.TransitionKey) { fadeIn() togetherWith fadeOut() }
        put(ListDetailSceneStrategy.ListKey, true)
    }
) { HomeScreen() }
```

### 2.9 Animations

NavDisplay supports built-in transition animations:

```kotlin
NavDisplay(
    backStack = backStack,
    // Global defaults
    transitionSpec = {
        slideInHorizontally(initialOffsetX = { it }) togetherWith
            slideOutHorizontally(targetOffsetX = { -it })
    },
    popTransitionSpec = {
        slideInHorizontally(initialOffsetX = { -it }) togetherWith
            slideOutHorizontally(targetOffsetX = { it })
    },
    predictivePopTransitionSpec = {
        slideInHorizontally(initialOffsetX = { -it }) togetherWith
            slideOutHorizontally(targetOffsetX = { it })
    },
    entryProvider = entryProvider {
        // Per-entry override via metadata
        entry<ScreenC>(
            metadata = metadata {
                put(NavDisplay.TransitionKey) {
                    slideInVertically(initialOffsetY = { it }) togetherWith
                        ExitTransition.KeepUntilTransitionsFinished
                }
            }
        ) { ScreenC() }
    }
)
```

Shared element transitions:
```kotlin
SharedTransitionLayout {
    NavDisplay(
        sharedTransitionScope = this,
        // ...
    )
}
```

### 2.10 Deep Linking

Nav 3 does **not** have built-in deep link support like Nav 2. Instead, you parse intents manually:

1. Define `DeepLinkPattern` objects mapping URIs to `NavKey` classes
2. Parse incoming `Intent.data` into a `DeepLinkRequest`
3. Match against patterns to produce a `NavKey`
4. Build a synthetic back stack for proper Up navigation

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val uri: Uri? = intent.data
    val key: NavKey = uri?.let { parseDeepLink(it) } ?: HomeKey
    setContent {
        val backStack = rememberNavBackStack(key)
        NavDisplay(backStack = backStack, ...)
    }
}
```

Recipes available: [Basic deep links](https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/deeplinks), [Advanced deep links](https://github.com/android/nav3-recipes/tree/main/advanceddeeplinkapp).

---

## 3. Best Practices

### 3.1 Architecture Pattern

**Recommended: Navigator + NavigationState pattern** (from official migration guide):

```kotlin
// NavigationState — holds all navigation state
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute
}

// Navigator — handles navigation events
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }
    fun goBack() { /* ... */ }
}
```

### 3.2 Multi-Module Navigation

Nav 3 is designed for modular navigation. Each feature module exposes:
- **`api` module**: Navigation keys (routes)
- **`impl` module**: Entry builders (content)

```kotlin
// feature/chat/impl
fun EntryProviderScope<NavKey>.chatEntryBuilder() {
    entry<ChatRoute> { key -> ChatScreen(key.conversationId) }
    entry<ChatSettings> { ChatSettingsScreen() }
}

// app module
NavDisplay(
    entryProvider = entryProvider {
        chatEntryBuilder()
        authEntryBuilder()
        settingsEntryBuilder()
    }
)
```

**With Koin DI** (from nav3-recipes):
```kotlin
// Each feature module provides its entry builder
val chatNavigationModule = module {
    factory<EntryProviderScope<NavKey>.() -> Unit>(named("chat")) {
        { chatEntryBuilder(get()) }
    }
}

// App module collects all builders
val entryBuilders: List<EntryProviderScope<NavKey>.() -> Unit> = getAll()
NavDisplay(
    entryProvider = entryProvider {
        entryBuilders.forEach { builder -> this.builder() }
    }
)
```

### 3.3 ViewModel Scoping

ViewModels are scoped to `NavEntry` lifecycle via decorators:

```kotlin
NavDisplay(
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),  // Always first
        rememberViewModelStoreNavEntryDecorator()
    ),
    // ...
)
```

With this setup:
- `viewModel()` calls inside NavEntry content are scoped to that entry
- ViewModels survive config changes
- ViewModels are cleared when the entry is popped

**With Koin:**
```kotlin
entry<ChatRoute> { key ->
    val viewModel = koinViewModel<ChatViewModel>()
    ChatScreen(viewModel)
}
```

### 3.4 Multiple Back Stacks (Bottom Navigation)

For apps with bottom navigation / top-level routes:

```kotlin
val topLevelRoutes = setOf(Home, Search, Profile)

// Each top-level route gets its own persistent back stack
val backStacks = topLevelRoutes.associateWith { rememberNavBackStack(it) }

// Switch between stacks (state is retained)
var currentTopLevel by remember { mutableStateOf(Home) }
```

Recipe: [Multiple back stacks](https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/multiplestacks)

### 3.5 Adaptive / Multi-Pane Layouts

Use Material 3 Adaptive for responsive list-detail:

```kotlin
val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

NavDisplay(
    backStack = backStack,
    sceneStrategies = listOf(listDetailStrategy),
    entryProvider = entryProvider {
        entry<ConversationList>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = { Text("Select a conversation") }
            )
        ) { ConversationListScreen() }

        entry<ConversationDetail>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key -> ConversationDetailScreen(key.id) }
    }
)
```

### 3.6 Testing Navigation

**Nav 3 has no dedicated testing library like Nav 2's `navigation-testing`.** However, since navigation state is just a list, testing is simpler:

```kotlin
@Test
fun testNavigation() {
    val backStack = mutableStateListOf<NavKey>(Home)
    
    // Simulate navigation
    backStack.add(ProductDetail("123"))
    assertEquals(ProductDetail("123"), backStack.last())
    
    // Simulate back
    backStack.removeLastOrNull()
    assertEquals(Home, backStack.last())
}
```

For UI testing, you can use standard Compose testing:
```kotlin
@Test
fun testNavigationUI() {
    composeTestRule.setContent {
        val backStack = remember { mutableStateListOf<NavKey>(Home) }
        NavDisplay(
            backStack = backStack,
            entryProvider = entryProvider { /* ... */ }
        )
    }
    // Assert on rendered content
}
```

The `SceneStrategyScope` has a no-arg public constructor specifically for testing (added in 1.0.0-beta01).

---

## 4. Migration Guide (Nav 2 → Nav 3)

### 4.1 Prerequisites

- `compileSdk` 36+
- `minSdk` 23+
- All destinations must be composable functions
- Routes must implement `NavKey`

### 4.2 Can Nav 2 and Nav 3 Coexist?

**Yes.** The official migration guide mentions **atomic migration** as the recommended approach, but community sources confirm **incremental migration** is possible:

- Nav 2 and Nav 3 can operate simultaneously during migration
- Features can be migrated one at a time
- Business logic and ViewModels don't need rewriting

However, the official migration guide assumes atomic migration. For incremental, you'd maintain a Nav 2 `NavHost` for unmigrated features and a Nav 3 `NavDisplay` for migrated ones, coordinating between them.

### 4.3 Step-by-Step Migration

#### Step 1: Add Dependencies

```toml
[versions]
nav3Core = "1.0.1"
lifecycleViewmodelNav3 = "2.11.0-alpha03"

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
```

#### Step 2: Update Routes to Implement NavKey

```kotlin
// Before
@Serializable data object Home
@Serializable data class Chat(val conversationId: String)

// After
@Serializable data object Home : NavKey
@Serializable data class Chat(val conversationId: String) : NavKey
```

#### Step 3: Create NavigationState & Navigator

See [Section 3.1](#31-architecture-pattern) for the full pattern.

#### Step 4: Replace NavController Calls

| Nav 2 | Nav 3 |
|-------|-------|
| `navController.navigate(route)` | `navigator.navigate(route)` or `backStack.add(route)` |
| `navController.popBackStack()` | `navigator.goBack()` or `backStack.removeLastOrNull()` |
| `navController.currentDestination` | `backStack.last()` |
| `currentBackStackEntryAsState()` | Direct state observation of `backStack` |
| `destination.isRouteInHierarchy()` | `key == navigationState.topLevelRoute` |

#### Step 5: Convert Destinations

| Nav 2 | Nav 3 |
|-------|-------|
| `composable<T> { }` | `entry<T> { }` |
| `dialog<T> { }` | `entry<T>(metadata = DialogSceneStrategy.dialog()) { }` |
| `navigation<T>(startDestination = ...) { }` | **Delete** — replace references with first child route |
| `NavGraphBuilder` extension | `EntryProviderScope<NavKey>` extension |
| `entry.toRoute<T>()` | `key` parameter: `entry<T> { key -> ... }` |

**Before:**
```kotlin
NavHost(navController = navController, startDestination = Home) {
    composable<Home> { HomeScreen() }
    composable<Chat> { entry ->
        val route = entry.toRoute<Chat>()
        ChatScreen(route.conversationId)
    }
    dialog<ConfirmDialog> { ConfirmDialogScreen() }
}
```

**After:**
```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategies = listOf(DialogSceneStrategy()),
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    ),
    entryProvider = entryProvider {
        entry<Home> { HomeScreen() }
        entry<Chat> { key -> ChatScreen(key.conversationId) }
        entry<ConfirmDialog>(
            metadata = DialogSceneStrategy.dialog()
        ) { ConfirmDialogScreen() }
    }
)
```

#### Step 6: Remove Nav 2 Dependencies

Remove all Nav 2 imports, `NavController`, `NavHost`, `NavGraphBuilder`, and gradle dependencies.

### 4.4 Key Differences to Watch

1. **No NavController** — State is a mutable list, not a controller object
2. **No nested graphs** — Delete `navigation<T>` blocks entirely
3. **No built-in deep links** — Must implement manually (see recipes)
4. **Route args via key param** — `entry<T> { key -> ... }` instead of `toRoute<T>()`
5. **Dialogs via metadata** — `DialogSceneStrategy.dialog()` metadata instead of `dialog<T>`
6. **Use `rememberSerializable`, not `rememberSaveable`** — For top-level route persistence
7. **Decorators are explicit** — Must add state/ViewModel decorators manually

---

## 5. Gotchas, Footguns & Complications

### 5.1 Known Issues (from release notes)

| Issue | Status | Versions |
|-------|--------|----------|
| z-order bug during animation interruptions ([b/459419800](https://issuetracker.google.com/issues/459419800)) | Fixed in 1.1.0-rc01 | |
| Lifecycle not capped at STARTED for entries under overlays ([b/483966071](https://issuetracker.google.com/issues/483966071)) | Fixed in 1.1.0-rc01 | |
| Crash with SharedTransitionLayout + OverlayScene ([b/478664101](https://issuetracker.google.com/issues/478664101)) | Fixed in 1.1.0-alpha04 | |
| IllegalStateException in Android Studio Previews ([b/477149762](https://issuetracker.google.com/issues/477149762)) | Fixed in 1.0.1 / NavigationEvent 1.0.2 | |
| Infinite scene recalculation ([b/418153031](https://issuetracker.google.com/issues/418153031)) | Fixed in 1.0.0-alpha07 | |
| Screen flicker when swapping backStack with animations ([b/450967248](https://issuetracker.google.com/issues/450967248)) | Fixed in 1.0.0-beta01 | |

### 5.2 Footguns

1. **Decorator order matters** — `rememberSaveableStateHolderNavEntryDecorator()` must be **first** in the decorators list. Otherwise, `rememberSaveable` won't work inside NavEntry content.

2. **Animations in adaptive layouts** — When using `ListDetailSceneStrategy` in two-pane mode (>=600dp), transition animations between top-level destinations may not work. The screen "jumps" without animation.

3. **No built-in deep link handling** — Unlike Nav 2's `deepLinks { }` DSL, you must parse intents and build synthetic back stacks yourself. See the recipes repo for patterns.

4. **`rememberNavBackStack` requires NavKey** — If you want persistence across process death, all keys must implement `NavKey` and be `@Serializable`. Simple `data object` without `NavKey` will work but won't survive process death.

5. **compileSdk 36 required** — This is a hard requirement. If your project is on an older compileSdk, you must upgrade.

6. **No nested navigation (>1 level)** — The migration guide explicitly states: only one level of nesting is supported. Shared destinations (screens moving between different back stacks) are also unsupported.

7. **`calculateScene` is no longer `@Composable` (since 1.0.0-alpha11)** — Custom SceneStrategies cannot call composable functions in `calculateScene`. Use `remember` in a factory function instead.

8. **NavBackStack serialization is Android-only** — The built-in `NavBackStackSerializer` is restricted to Android ([b/420443609](https://issuetracker.google.com/issues/420443609)). For KMP, you need polymorphic serialization (see Section 6).

9. **API churn in 1.1.x** — The 1.1 branch is still in RC. APIs like `SceneStrategy` receiving `List<SceneStrategy>` instead of chained strategies changed in alpha05. Pin to 1.0.1 for stability.

### 5.3 Things Harder in Nav 3

- **Deep linking**: Significantly more manual work required
- **Bottom sheet destinations**: No built-in support; recipe available but requires custom SceneStrategy
- **Fragment/View interop**: Must use Views-in-Compose wrappers; Nav 3 is Compose-only
- **Shared destinations between back stacks**: Not supported
- **Testing**: No dedicated testing artifact; testing is easier conceptually (just a list) but lacks the convenience APIs of `TestNavHostController`

### 5.4 Things Easier in Nav 3

- **Back stack inspection**: It's just a list — fully observable and debuggable
- **Multi-pane/adaptive layouts**: First-class support via Scenes
- **Custom navigation logic**: No fighting the NavController
- **State management**: Aligns perfectly with Compose state
- **ViewModel scoping**: Explicit, clear, and configurable via decorators

---

## 6. KMP / Compose Multiplatform Considerations

### 6.1 Is Nav 3 Available for Compose Multiplatform?

**Yes.** JetBrains added Nav 3 support in **Compose Multiplatform 1.10.0** (January 2026) for all platforms: Android, iOS, desktop, and web.

### 6.2 Dependencies (KMP)

```toml
[versions]
multiplatform-nav3-ui = "1.0.0-alpha05"

[libraries]
jetbrains-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "multiplatform-nav3-ui" }
```

Nav 3 ships as two artifacts:
- `navigation3-common` — Core (transitive dependency)
- `navigation3-ui` — NavDisplay and UI components (separate CMP implementation)

Additional KMP libraries:
```toml
# Material 3 Adaptive
jetbrains-material3-adaptiveNavigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "compose-multiplatform-adaptive" }

# ViewModel support
jetbrains-lifecycle-viewmodelNavigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "compose-multiplatform-lifecycle" }
```

### 6.3 Critical KMP Requirement: Polymorphic Serialization

**On Android**, Nav 3 uses reflection-based serialization for `NavKey` types. **This does NOT work on non-JVM platforms (iOS, web).** You must use polymorphic serialization.

`rememberNavBackStack()` has two overloads:
1. **Android-only**: `rememberNavBackStack(startKey)` — uses reflection
2. **Multiplatform**: `rememberNavBackStack(config, startKey)` — requires `SavedStateConfiguration`

**For KMP, you MUST use the second overload:**

```kotlin
@Serializable
data object Home : NavKey

@Serializable
data class Chat(val id: String) : NavKey

// Required for non-JVM platforms
private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(Chat::class, Chat.serializer())
        }
    }
}

@Composable
fun App() {
    val backStack = rememberNavBackStack(config, Home)
    NavDisplay(backStack = backStack, ...)
}
```

### 6.4 Multi-Module KMP Pattern

For our multi-module setup, use sealed interfaces per feature module:

```kotlin
// feature/auth/api
@Serializable
sealed interface AuthRoute : NavKey

@Serializable data object Login : AuthRoute
@Serializable data object Register : AuthRoute

// feature/chat/api
@Serializable
sealed interface ChatRoute : NavKey

@Serializable data class Conversation(val id: String) : ChatRoute

// app module — aggregate all feature routes
private val config = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<AuthRoute>()
            subclassesOfSealed<ChatRoute>()
        }
    }
}
```

Alternatively, with Koin DI, each module can expose a `SerializersModule` and the app module combines them:

```kotlin
// Each feature module
val chatSerializerModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Conversation::class, Conversation.serializer())
    }
}

// App module
val combinedConfig = SavedStateConfiguration {
    serializersModule = chatSerializerModule + authSerializerModule + settingsSerializerModule
}
```

### 6.5 Impact on Our Current Setup

We currently use `org.jetbrains.androidx.navigation:navigation-compose:2.9.2`. For Nav 3:

| Current (Nav 2 KMP) | Nav 3 KMP |
|---------------------|-----------|
| `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` | `org.jetbrains.androidx.navigation3:navigation3-ui:1.0.0-alpha05` |
| `NavHost` | `NavDisplay` |
| `NavController` | `SnapshotStateList` + custom Navigator |
| Reflection-based routing | **Must use** `SavedStateConfiguration` with polymorphic serialization |
| Direct route access | Key-based entry access |

**Key considerations:**
- The JetBrains Nav 3 artifact is at **1.0.0-alpha05** (not yet stable for KMP)
- Android-side Nav 3 is stable at 1.0.1
- We must implement polymorphic serialization for all routes
- Our Koin-based DI can provide serializer modules per feature
- Browser history navigation (for web target) is expected in Nav 3 KMP 1.1.0

### 6.6 CMP Nav 3 Recipes

JetBrains maintains a Compose Multiplatform fork of the nav3-recipes:
- [github.com/terrakok/nav3-recipes](https://github.com/terrakok/nav3-recipes)

---

## 7. Appendix: Version & Dependency Reference

### AndroidX Dependencies (Android-only)

```toml
[versions]
nav3Core = "1.0.1"                    # Stable
# nav3Core = "1.1.0-rc01"            # Latest RC
lifecycleViewmodelNav3 = "2.11.0-alpha03"
material3AdaptiveNav3 = "1.3.0-alpha09"
kotlinxSerializationCore = "1.9.0"

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
androidx-material3-adaptive-navigation3 = { module = "androidx.compose.material3.adaptive:adaptive-navigation3", version.ref = "material3AdaptiveNav3" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinxSerializationCore" }
```

### Compose Multiplatform Dependencies (KMP)

```toml
[versions]
multiplatform-nav3-ui = "1.0.0-alpha05"
compose-multiplatform-adaptive = "1.3.0-alpha02"
compose-multiplatform-lifecycle = "2.10.0-alpha05"

[libraries]
jetbrains-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "multiplatform-nav3-ui" }
jetbrains-material3-adaptiveNavigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "compose-multiplatform-adaptive" }
jetbrains-lifecycle-viewmodelNavigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "compose-multiplatform-lifecycle" }
```

### Nav 3 Recipes Repository

- Android: [github.com/android/nav3-recipes](https://github.com/android/nav3-recipes) (1.2k stars)
- CMP: [github.com/terrakok/nav3-recipes](https://github.com/terrakok/nav3-recipes)

Available recipes:
- Basic, Saveable back stack, Entry provider DSL
- Deep links (basic + advanced)
- Dialog, BottomSheet, List-Detail, Two-pane, Supporting Pane
- Animations
- Navigation UI, Multiple back stacks
- Conditional navigation
- Modularized navigation (Hilt + Koin)
- ViewModel patterns (basic, Hilt, Koin, shared)
- Retain values for back stack composables
- Returning results (events + state)
- Fragment/View interop

### Sources

- [Navigation 3 Overview](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 Basics](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Navigation 3 Migration Guide](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
- [Navigation 3 Save State](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 Metadata](https://developer.android.com/guide/navigation/navigation-3/metadata)
- [Navigation 3 Modularize](https://developer.android.com/guide/navigation/navigation-3/modularize)
- [Navigation 3 Scenes](https://developer.android.com/guide/navigation/navigation-3/scenes)
- [Navigation 3 Animations](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)
- [Navigation 3 Entry Decorators](https://developer.android.com/guide/navigation/navigation-3/naventrydecorators)
- [Navigation 3 Get Started](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Navigation 3 Release Notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Navigation 3 in Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [Compose Multiplatform 1.10.0 Blog Post](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/)
- [Jetpack Navigation 3 is Stable (Blog)](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html)
- [Announcing Jetpack Navigation 3 (Blog)](https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html)
- [nav3-recipes GitHub](https://github.com/android/nav3-recipes)
- [Deep Link Guide](https://github.com/android/nav3-recipes/blob/main/docs/deeplink-guide.md)
- [Shared ViewModel Recipe](https://developer.android.com/guide/navigation/navigation-3/recipes/sharedviewmodel)
