package com.librechat.android.core.common.di

import org.junit.Test
import org.koin.test.verify.verify

class CommonModuleVerificationTest {
    @Test
    fun verifyCommonModule() {
        commonModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
            ),
        )
    }
}
