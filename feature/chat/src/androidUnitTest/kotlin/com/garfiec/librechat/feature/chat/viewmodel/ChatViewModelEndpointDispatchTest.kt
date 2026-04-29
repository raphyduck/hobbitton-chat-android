package com.garfiec.librechat.feature.chat.viewmodel

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Static-source regression guard for the issue #60 fix.
 *
 * Enforces the project convention that every `chatRepository.startChat(` call
 * site binds `val dispatch = classifyCurrentEndpoint(...)` and forwards the
 * dispatch fields via the literal named arguments `endpointType = dispatch.endpointType`,
 * `key = dispatch.key`, and `modelDisplayLabel = dispatch.modelDisplayLabel`.
 *
 * The greps are intentionally textual: a refactor that renames the local
 * (e.g. `d.endpointType`) or inlines the classifier call will fail this test
 * and force the author to update the convention deliberately rather than
 * silently regressing the wire format back to `endpoint: "agents"`.
 */
class ChatViewModelEndpointDispatchTest {

    /**
     * Locates ChatViewModel.kt by walking up from the unit test working directory
     * to the module root. Tests run from `feature/chat/`, so the source file sits at
     * a known relative path.
     */
    private val source: String by lazy {
        val candidates = listOf(
            File("src/commonMain/kotlin/com/garfiec/librechat/feature/chat/viewmodel/ChatViewModel.kt"),
            File("../../src/commonMain/kotlin/com/garfiec/librechat/feature/chat/viewmodel/ChatViewModel.kt"),
            File("feature/chat/src/commonMain/kotlin/com/garfiec/librechat/feature/chat/viewmodel/ChatViewModel.kt"),
        )
        val found = candidates.firstOrNull { it.exists() }
            ?: error("ChatViewModel.kt not found from ${File(".").absolutePath}")
        found.readText()
    }

    @Test
    fun everyStartChatCallPassesEndpointTypeKeyAndModelDisplayLabel() {
        val callSites = extractStartChatCallSites(source)
        assertThat(callSites).isNotEmpty()

        callSites.forEachIndexed { index, callBlock ->
            assertWithMessage("Site $index").that(callBlock).contains("endpointType = dispatch.endpointType")
            assertWithMessage("Site $index").that(callBlock).contains("key = dispatch.key")
            assertWithMessage("Site $index").that(callBlock).contains("modelDisplayLabel = dispatch.modelDisplayLabel")
        }
    }

    @Test
    fun classifyHelperExistsAndUsesEndpointConfigsFromUiState() {
        // The helper centralizes the classifier wiring; if someone removes it, every
        // call site silently breaks. Guard the wiring.
        assertThat(source).contains("private fun classifyCurrentEndpoint(name: String)")
        assertThat(source).contains("EndpointClassifier.classify(name, _uiState.value.endpointConfigs)")
    }

    private fun extractStartChatCallSites(text: String): List<String> {
        // Strip block + line comments first so doc-comment mentions of
        // `chatRepository.startChat(...)` aren't counted as real call sites.
        val cleaned = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")
        val token = "chatRepository.startChat("
        val results = mutableListOf<String>()
        var idx = 0
        while (true) {
            val start = cleaned.indexOf(token, idx)
            if (start < 0) break
            // Walk until matching ')'
            var depth = 0
            var i = start + token.length - 1 // position of '('
            while (i < cleaned.length) {
                when (cleaned[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            results.add(cleaned.substring(start, i + 1))
                            idx = i + 1
                            break
                        }
                    }
                }
                i++
            }
            if (depth != 0) error("Unbalanced parens at offset $start")
        }
        return results
    }
}
