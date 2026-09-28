package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.GlobalProfileEditor
import com.garfiec.librechat.core.data.datastore.GlobalProfileSource
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.engine.EngineSessionKinds
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.pricing.ModelPriceCache
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The engine's local records in `:core:data`, included by `dataModule`: reading positions, session
 * kinds, the global profile's two faces and the price cache. Platform-neutral on purpose: what
 * needs the engine's clients stays in `engineModule`, which this module does not touch.
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
    // taking a DataStore with it. The store itself is bound in `dataModule`. One definition, two
    // faces: the same store is
    // also the editor of the engine shell's instructions screen (D-077).
    single<GlobalProfileSource> { get<GlobalProfileStore>() } binds arrayOf(GlobalProfileEditor::class)

    /**
     * The gateway's price table, shared by the chat's model picker and the tasks tab's.
     *
     * `getOrNull` and not `get`: its source is bound by `engineModule`. Without it the cache
     * answers an empty table without ever reaching the network — a picker with no prices, rather
     * than a graph that fails to build.
     */
    single { ModelPriceCache(source = getOrNull()) }
}
