package com.garfiec.librechat.core.common.di

import kotlinx.coroutines.CoroutineDispatcher

/** The app's own IO threads, never `Dispatchers.IO` (27/10/2026, see [AppThreads]). */
actual val ioDispatcher: CoroutineDispatcher = AppThreads.io
