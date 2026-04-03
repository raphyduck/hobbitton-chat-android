package com.garfiec.librechat.feature.files.di

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
                com.garfiec.librechat.core.data.repository.FileRepository::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
                com.garfiec.librechat.feature.files.platform.FileReader::class,
            ),
        )
    }
}
