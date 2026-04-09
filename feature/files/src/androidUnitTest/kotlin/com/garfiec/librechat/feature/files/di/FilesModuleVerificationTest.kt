package com.garfiec.librechat.feature.files.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.feature.files.platform.FileReader
import org.junit.Test
import org.koin.test.verify.verify

class FilesModuleVerificationTest {
    @Test
    fun verifyFilesModule() {
        filesModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                FileRepository::class,
                ServerDataStore::class,
                FileReader::class,
            ),
        )
    }
}
