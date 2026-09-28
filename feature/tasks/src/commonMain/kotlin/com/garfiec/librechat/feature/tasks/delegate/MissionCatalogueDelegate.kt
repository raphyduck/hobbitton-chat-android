package com.garfiec.librechat.feature.tasks.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineModelChoice
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** What one catalogue fetch brought back: the value, or why there is none. */
internal sealed interface CatalogueFetch<out T> {
    data class Loaded<T>(val value: T) : CatalogueFetch<T>

    data class Failed(val kind: EngineFailureKind) : CatalogueFetch<Nothing>
}

/**
 * What a picker offers — the engine's models, the gateway's prices, the scheduler's connectors —
 * fetched the same way for the two screens that show one: the New-mission sheet (`TasksViewModel`)
 * and a mission's conversation (`MissionChatViewModel`).
 *
 * The two carried their own copy of these fetches until D-076. What they share lives here: the hop
 * to [context], the cancellation rethrown rather than swallowed, the failure classified and logged.
 * What each does with the answer stays its own — the sheet keeps the preselection and shrugs off a
 * missing model list, the conversation names each failure on its own chip.
 *
 * Three calls, never one: the models come from the engine, the catalogue and the prices from the
 * scheduler, and one host being down must not take the other's picker with it. Folding them into a
 * single `try` hid a working model picker behind a scheduler that was merely not redeployed yet
 * (30/08/2026).
 */
internal class MissionCatalogueDelegate(
    private val fetchModels: suspend () -> EngineModelChoice,
    private val fetchPrices: suspend () -> ModelPrices,
    private val fetchConnectors: suspend () -> ConnectorCatalogue,
    /** Where the fetches run: the IO dispatcher from a ViewModel that hops, nothing from one that does not. */
    private val context: CoroutineContext = EmptyCoroutineContext,
) {

    /** The models a mission may run on, and the one the engine would pick itself. */
    suspend fun models(): CatalogueFetch<EngineModelChoice> = fetch("models") { fetchModels() }

    /**
     * What each model costs. Asked for after the models and never instead of them: a price is
     * decoration on a list that works without it.
     *
     * Not wrapped like the other two because it does not fail — `ModelPriceCache` keeps its last
     * answer when the scheduler does not reply, and answers an empty table when there is none.
     */
    suspend fun prices(): ModelPrices = withContext(context) { fetchPrices() }

    /** The connectors this deployment offers — the scheduler's own table, never a local copy. */
    suspend fun connectors(): CatalogueFetch<ConnectorCatalogue> = fetch("connectors") { fetchConnectors() }

    private suspend fun <T> fetch(what: String, block: suspend () -> T): CatalogueFetch<T> =
        try {
            CatalogueFetch.Loaded(withContext(context) { block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, tag = LOG_TAG) { "Could not list the engine's $what" }
            CatalogueFetch.Failed(e.engineFailureKind())
        }

    private companion object {
        const val LOG_TAG = "Tasks"
    }
}
