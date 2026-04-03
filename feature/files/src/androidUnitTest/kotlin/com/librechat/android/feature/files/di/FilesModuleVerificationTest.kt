package com.librechat.android.feature.files.di

import org.junit.Test
import org.koin.test.verify.verify

class FilesModuleVerificationTest {
    @Test
    fun verifyFilesModule() {
        filesModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.librechat.android.core.data.repository.FileRepository::class,
                com.librechat.android.core.data.datastore.ServerDataStore::class,
                com.librechat.android.feature.files.platform.FileReader::class,
            ),
        )
    }
}
