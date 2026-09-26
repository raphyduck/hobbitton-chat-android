package com.garfiec.librechat.feature.chat.components.artifact

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MermaidBridgeReceiverTest {

    @Test
    fun `onSvg writes to cache under bound key`() {
        val cache = MermaidRenderCache()
        val receiver = MermaidBridgeReceiver(cache, key = "k1")
        receiver.onSvg("<svg id='one'/>")

        assertThat(cache["k1"]).isEqualTo("<svg id='one'/>")
    }

    @Test
    fun `two receivers with different keys write to distinct entries`() {
        val cache = MermaidRenderCache()
        val r1 = MermaidBridgeReceiver(cache, key = "a")
        val r2 = MermaidBridgeReceiver(cache, key = "b")

        r1.onSvg("<svg id='a'/>")
        r2.onSvg("<svg id='b'/>")

        assertThat(cache["a"]).isEqualTo("<svg id='a'/>")
        assertThat(cache["b"]).isEqualTo("<svg id='b'/>")
    }

    @Test
    fun `repeated onSvg overwrites the cache entry for the bound key`() {
        val cache = MermaidRenderCache()
        val receiver = MermaidBridgeReceiver(cache, key = "k")
        receiver.onSvg("<svg id='v1'/>")
        receiver.onSvg("<svg id='v2'/>")

        assertThat(cache["k"]).isEqualTo("<svg id='v2'/>")
    }

    // What the page reports is checked before it is cached (review C3).

    @Test
    fun `a rendered diagram with style and text is accepted`() {
        val svg = "  <svg xmlns=\"http://www.w3.org/2000/svg\"><style>.node{fill:#fff}</style><text>on time</text></svg>"
        assertThat(isAcceptableRenderedSvg(svg)).isTrue()
    }

    @Test
    fun `anything that is not an svg document is refused`() {
        val cache = MermaidRenderCache()
        MermaidBridgeReceiver(cache, key = "k").onSvg("<div>not a diagram</div>")
        MermaidBridgeReceiver(cache, key = "k").onSvg("")
        MermaidBridgeReceiver(cache, key = "k").onSvg("plain text")

        assertThat(cache["k"]).isNull()
    }

    @Test
    fun `an svg carrying a script element is refused`() {
        assertThat(isAcceptableRenderedSvg("<svg><script>x</script></svg>")).isFalse()
        assertThat(isAcceptableRenderedSvg("<svg><SCRIPT>x</SCRIPT></svg>")).isFalse()
    }

    @Test
    fun `an svg carrying an event handler attribute is refused`() {
        assertThat(isAcceptableRenderedSvg("<svg onload=\"x\"></svg>")).isFalse()
        assertThat(isAcceptableRenderedSvg("<svg><g ONCLICK = \"x\"/></svg>")).isFalse()
    }

    @Test
    fun `an svg carrying a javascript reference is refused`() {
        assertThat(isAcceptableRenderedSvg("<svg><a href=\"javascript:x\"/></svg>")).isFalse()
    }

    @Test
    fun `an oversized svg is refused`() {
        val huge = "<svg>" + "x".repeat(MAX_RENDERED_SVG_CHARS) + "</svg>"
        assertThat(isAcceptableRenderedSvg(huge)).isFalse()
    }
}
