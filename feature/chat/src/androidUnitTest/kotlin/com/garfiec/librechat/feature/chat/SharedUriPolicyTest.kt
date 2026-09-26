package com.garfiec.librechat.feature.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which streams another app may hand this app (review C6). Pure string checks — the Android
 * `Uri` wrapper is one line over them and needs Robolectric to build.
 */
class SharedUriPolicyTest {

    private val own = "com.example.app.fileprovider"

    @Test
    fun `a content uri from another provider is accepted`() {
        assertThat(isAcceptableSharedUri("content", "com.android.providers.media.documents", own)).isTrue()
        assertThat(isAcceptableSharedUri("CONTENT", "media", own)).isTrue()
    }

    @Test
    fun `a file uri is refused whatever it points at`() {
        assertThat(isAcceptableSharedUri("file", null, own)).isFalse()
        assertThat(isAcceptableSharedUri("file", "", own)).isFalse()
    }

    @Test
    fun `other schemes are refused`() {
        listOf("http", "https", "android.resource", "data", null).forEach { scheme ->
            assertThat(isAcceptableSharedUri(scheme, "host", own)).isFalse()
        }
    }

    @Test
    fun `this app's own file provider is not a share source`() {
        assertThat(isAcceptableSharedUri("content", own, own)).isFalse()
        assertThat(isAcceptableSharedUri("content", own.uppercase(), own)).isFalse()
    }

    @Test
    fun `a content uri without an authority is refused`() {
        assertThat(isAcceptableSharedUri("content", null, own)).isFalse()
        assertThat(isAcceptableSharedUri("content", "  ", own)).isFalse()
    }
}
