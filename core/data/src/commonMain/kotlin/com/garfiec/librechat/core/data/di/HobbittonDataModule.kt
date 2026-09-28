package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.GlobalProfileSource
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.engine.EngineSessionKinds
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.pricing.ModelPriceCache
import org.koin.dsl.module

/**
 * The hobbitton overlay's own bindings in `:core:data`, out of the upstream `dataModule`.
 *
 * `dataModule` includes this in one line (D-076). Before, each of these sat among the upstream
 * definitions, so every sync with the upstream client had to step around them one hunk at a time;
 * now the patch there is that line. Platform-neutral on purpose: what is Android-only (the engine,
 * the scheduler) stays in `engineModule`, which this module does not touch.
 */
val hobbittonDataModule = module {
    /** Where each mission transcript was left, so the conversation reopens there. */
    single {
        MissionReadingPositions(
            dataStore = get(),
            json = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }

    /**
     * Which engine session is a chat and which a task (D-077). Platform-neutral like the reading
     * positions: plain preferences, no secret — the engine graph that reads it stays Android-only.
     */
    single<SessionKindStore> {
        EngineSessionKinds(
            dataStore = get(),
            json = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }

    // The store IS the source; the binding exists so a send path can ask for the value without
    // taking a DataStore with it. The store itself stays in `dataModule`, where the upstream
    // chat-profile store it replaced was bound.
    single<GlobalProfileSource> { get<GlobalProfileStore>() }

    /**
     * The gateway's price table, shared by the chat's model picker and the tasks tab's.
     *
     * `getOrNull` and not `get`: its source is bound by `engineModule`, which is Android-only
     * (D-034). On iOS the cache resolves with no source and answers an empty table without ever
     * reaching the network — a picker with no prices, rather than a graph that fails to build.
     */
    single { ModelPriceCache(source = getOrNull()) }
}
