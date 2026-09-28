package com.garfiec.librechat.core.network.di

import org.junit.Test
import org.koin.test.verify.verify

class NetworkModuleVerificationTest {
    @Test
    fun verifyNetworkModule() {
        networkModule.verify()
    }
}
