package com.librechat.android.core.data.repository

import com.librechat.android.core.data.db.dao.DraftDao
import com.librechat.android.core.data.db.entity.DraftEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DraftRepositoryImpl(
    private val draftDao: DraftDao,
    private val ioDispatcher: CoroutineDispatcher,
) : DraftRepository {

    private val cache = ConcurrentHashMap<String, String>()
    private val debounceJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override suspend fun getDraft(conversationId: String): String? {
        // Check cache first for fast reads
        cache[conversationId]?.let { return it }
        // Fallback to Room
        val entity = draftDao.getDraft(conversationId)
        val text = entity?.text
        if (text != null) {
            cache[conversationId] = text
        }
        return text
    }

    override suspend fun saveDraft(conversationId: String, text: String) {
        if (text.isBlank()) {
            deleteDraft(conversationId)
            return
        }
        // Update cache immediately for fast reads
        cache[conversationId] = text
        // Schedule debounced write to Room
        debounceJobs[conversationId]?.cancel()
        debounceJobs[conversationId] = scope.launch {
            delay(DEBOUNCE_MS)
            draftDao.upsertDraft(
                DraftEntity(
                    conversationId = conversationId,
                    text = text,
                ),
            )
            debounceJobs.remove(conversationId)
        }
    }

    override suspend fun deleteDraft(conversationId: String) {
        cache.remove(conversationId)
        debounceJobs.remove(conversationId)?.cancel()
        draftDao.deleteDraft(conversationId)
    }

    override fun observeAllDrafts(): Flow<Map<String, String>> =
        draftDao.observeAllDrafts().map { entities ->
            entities.associate { it.conversationId to it.text }
        }

    companion object {
        private const val DEBOUNCE_MS = 500L
    }
}
