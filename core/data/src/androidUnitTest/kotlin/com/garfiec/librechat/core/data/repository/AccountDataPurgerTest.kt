package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.data.db.dao.ArtifactShortcutDao
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.dao.DraftDao
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.db.dao.PrefetchWatermarkDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What a removed account leaves behind in Room: nothing of its own, and no artifact snapshot at
 * all (review C9) — the snapshots have no owner column and open without a session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDataPurgerTest {

    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val draftDao = mockk<DraftDao>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val prefetchWatermarkDao = mockk<PrefetchWatermarkDao>(relaxed = true)
    private val artifactShortcutDao = mockk<ArtifactShortcutDao>(relaxed = true)

    private val purger = AccountDataPurger(
        conversationDao = conversationDao,
        messageDao = messageDao,
        draftDao = draftDao,
        tagDao = tagDao,
        prefetchWatermarkDao = prefetchWatermarkDao,
        artifactShortcutDao = artifactShortcutDao,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `every tenant table is purged for the account and the artifact snapshots are emptied`() = runTest {
        purger.purge(AccountId("acct-1"))

        coVerify(exactly = 1) { messageDao.deleteAllForAccount("acct-1") }
        coVerify(exactly = 1) { draftDao.deleteAllForAccount("acct-1") }
        coVerify(exactly = 1) { tagDao.deleteAllForAccount("acct-1") }
        coVerify(exactly = 1) { conversationDao.deleteAllForAccount("acct-1") }
        coVerify(exactly = 1) { prefetchWatermarkDao.deleteAllForAccount("acct-1") }
        coVerify(exactly = 1) { artifactShortcutDao.deleteAll() }
    }
}
