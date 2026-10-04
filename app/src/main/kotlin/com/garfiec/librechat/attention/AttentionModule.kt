package com.garfiec.librechat.attention

import com.garfiec.librechat.core.data.engine.AttentionNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The platform's sound and notifications, bound here because they show the app's own strings and
 * open its activity. What decides when to ring is common: `AttentionSignals`, in `engineModule`.
 */
val attentionModule = module {
    single<AttentionNotifier> { AndroidAttentionNotifier(androidContext()) }
}
