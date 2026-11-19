package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.feature.tasks.components.isForeignContentUri
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** S1 (19/11/2026): a share into Eva is read only from another app's content, never from the app's own files. */
class SharedUriFilterTest {

    private val own = "at.hobbitton.chat"

    @Test
    fun `the app's own files and every other scheme are refused`() {
        listOf(
            "file:///data/data/at.hobbitton.chat/shared_prefs/engine_secrets.xml",
            "content://at.hobbitton.chat.files/attachments/eva-diagnostic.jsonl",
            "content://at.hobbitton.chat/x",
            "content://0@at.hobbitton.chat/attachments/x",
            "content://other.example/x",
            "content://at.hobbitton.chat.androidx-startup/x",
            "CONTENT://AT.HOBBITTON.CHAT.FILES/x",
            "http://example.com/x",
            "content:///x",
        ).forEach { assertThat(isForeignContentUri(it, own)).isFalse() }
    }

    @Test
    fun `the debug build refuses its own providers too`() {
        assertThat(isForeignContentUri("content://at.hobbitton.chat.debug.files/x", "at.hobbitton.chat.debug")).isFalse()
    }

    @Test
    fun `userinfo cannot hide the app's own provider`() {
        assertThat(isForeignContentUri("content://0@at.hobbitton.chat.files/x", own)).isFalse()
    }

    @Test
    fun `other apps' content is accepted`() {
        listOf(
            "content://com.android.providers.media.documents/document/image%3A12",
            "content://media/external/images/media/12",
            "content://com.whatsapp.provider.media/item/1",
        ).forEach { assertThat(isForeignContentUri(it, own)).isTrue() }
    }
}
