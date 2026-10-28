package com.garfiec.librechat.shared.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.tasks.navigation.EngineChat

/**
 * The view models of the shell's screens, one store per back-stack entry (28/10/2026, Raphaël:
 * « ça recommence », a conversation grey for good).
 *
 * In place of Navigation 3's own decorator (lifecycle-viewmodel-navigation3 2.11.0-beta01), which
 * marks a popped entry's store for disposal and drops it the moment nothing holds it: the journal
 * of 28/10 showed conversations opened two to five times within half a second, each view model
 * gone before its first read, the screen left on one of them. The rule here is plain: a store
 * lives while its entry is on the back stack **or** still drawn (a screen fading out), and is
 * cleared only once it is neither, looked up in the back stack as it is at that moment. A
 * conversation pushed again is a new entry ([com.garfiec.librechat.feature.tasks.navigation.EngineChat.instance]),
 * so it never inherits a store on its way out.
 *
 * The stores are held by a view model of the activity: they survive a configuration change and
 * are cleared with it when the activity finishes.
 */
internal class EntryViewModelStores : ViewModel() {
    private val stores = mutableMapOf<Any, ViewModelStore>()
    private val drawn = mutableMapOf<Any, Int>()

    fun storeOf(key: Any): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun drawn(key: Any) {
        drawn[key] = (drawn[key] ?: 0) + 1
    }

    fun undrawn(key: Any, onStack: Set<Any>) {
        val left = (drawn[key] ?: 1) - 1
        if (left > 0) drawn[key] = left else drawn.remove(key)
        sweep(onStack)
    }

    /** Clears the stores of entries neither on the back stack nor drawn. */
    fun sweep(onStack: Set<Any>) {
        stores.keys.filter { it !in onStack && it !in drawn }.forEach { stores.remove(it)?.clear() }
    }

    /** Every store cleared: the shell left (signed out), or the activity finishes. */
    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
        drawn.clear()
    }

    override fun onCleared() = clearAll()
}

/**
 * The decorator that gives each entry its store from [EntryViewModelStores]. [contentKeyOf] must
 * be the content key the entries are registered with, so that the back stack and the drawn
 * entries speak of the same keys.
 */
@Composable
internal fun rememberEntryViewModelStoreDecorator(
    backStack: List<NavKey>,
    contentKeyOf: (NavKey) -> Any,
    holder: EntryViewModelStores = viewModel { EntryViewModelStores() },
): NavEntryDecorator<NavKey> {
    val currentStack by rememberUpdatedState(backStack)
    val currentKeyOf by rememberUpdatedState(contentKeyOf)
    val onStack = { currentStack.mapTo(HashSet<Any>()) { currentKeyOf(it) } }
    // Every change of the stack: an entry popped while nothing drew it (under a pushed screen,
    // unwound in one go) has no disposal of its own to sweep it.
    LaunchedEffect(holder) { snapshotFlow(onStack).collect(holder::sweep) }
    // The shell leaving (sign-out) takes its screens' view models with it; a configuration change
    // that recreates the activity keeps them for the new one.
    val keep = rememberStoresKeptOnDispose()
    DisposableEffect(holder) { onDispose { if (!keep()) holder.clearAll() } }
    return remember(holder) {
        NavEntryDecorator(decorate = { entry ->
            val key = entry.contentKey
            val store = holder.storeOf(key)
            val owner = remember(store) {
                object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore = store
                }
            }
            DisposableEffect(key) {
                holder.drawn(key)
                onDispose { holder.undrawn(key, onStack()) }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
        })
    }
}

/**
 * The content key the shell registers each route under: a conversation's push, else the route as
 * Navigation 3 names it by default (its `toString`).
 */
internal fun shellContentKey(key: NavKey): Any = (key as? EngineChat)?.entryKey ?: key.toString()

/** Whether stores outlive the shell's composition now: true while the activity is being recreated. */
@Composable
internal expect fun rememberStoresKeptOnDispose(): () -> Boolean
