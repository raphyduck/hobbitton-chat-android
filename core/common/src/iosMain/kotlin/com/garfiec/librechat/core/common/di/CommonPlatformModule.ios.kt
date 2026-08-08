package com.garfiec.librechat.core.common.di

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.IosAppInfo
import com.garfiec.librechat.core.common.lifecycle.DeferredWorkWindow
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.network.IosConnectivityObserver
import com.garfiec.librechat.core.common.network.IosNetworkConditionObserver
import com.garfiec.librechat.core.common.network.NetworkConditionObserver
import com.garfiec.librechat.core.common.power.IosPowerStateObserver
import com.garfiec.librechat.core.common.power.PowerStateObserver
import org.koin.core.module.Module
import org.koin.dsl.module

actual val commonPlatformModule: Module = module {
    single<ConnectivityObserver> { IosConnectivityObserver() }
    single<NetworkConditionObserver> { IosNetworkConditionObserver() }
    single<PowerStateObserver> { IosPowerStateObserver() }
    single<AppInfo> { IosAppInfo() }
    // False until the BGTaskScheduler work lands: iOS suspends a backgrounded process, so a latched
    // window would start a pass and then freeze it mid-request with nothing to finish or cancel it.
    // Falling back to the foreground signal keeps today's behaviour exactly.
    single { DeferredWorkWindow(foregroundSignal = get(), backgroundRunsSupported = false) }
}
