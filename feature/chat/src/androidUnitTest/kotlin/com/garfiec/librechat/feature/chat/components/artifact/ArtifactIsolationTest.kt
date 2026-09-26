package com.garfiec.librechat.feature.chat.components.artifact

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the templates guarantee about the documents they build (review C1/C2/C3/C8):
 * untrusted text never reaches a script literal, the app's CSP comes first and once, the CDN
 * scripts are pinned and integrity-checked, and an artifact-authored document is sandboxed.
 */
class ArtifactIsolationTest {

    /** Text that would end a script element or a template literal if it were spliced in. */
    private val awkward = "a `b` \${'$'}{c} </script><script>d</script> \\ 'e' \"f\" é 🙂"

    private val base64Literal = Regex("""atob\('([A-Za-z0-9+/=]*)'\)""")

    private fun decodedLiterals(html: String): List<String> =
        base64Literal.findAll(html).map { String(Base64.getDecoder().decode(it.groupValues[1])) }.toList()

    private fun cspMetas(html: String): List<String> =
        Regex("""<meta http-equiv="Content-Security-Policy" content="([^"]*)">""").findAll(html)
            .map { it.groupValues[1] }
            .toList()

    private fun firstHeadChild(html: String): String {
        val head = html.substringAfter("<head>")
        return head.trim().substringBefore(">") + ">"
    }

    private fun scriptSources(html: String): List<String> =
        Regex("""<script[^>]*\ssrc="([^"]+)"[^>]*>""").findAll(html).map { it.value }.toList()

    @Test
    fun `markdown content travels as base64 and never as a literal`() {
        val html = MarkdownWebContent.buildHtml(awkward, isDarkTheme = false)

        assertEquals(listOf(awkward), decodedLiterals(html))
        assertFalse(html.contains("<script>d</script>"))
        // Exactly the template's own script elements — the content added none.
        assertEquals(html.split("<script").size, MarkdownWebContent.buildHtml("plain", isDarkTheme = false).split("<script").size)
    }

    @Test
    fun `mermaid content travels as base64 and never as a literal`() {
        val html = MermaidWebContent.buildHtml(awkward, isDarkTheme = true)

        assertEquals(listOf(awkward), decodedLiterals(html))
        assertFalse(html.contains("<script>d</script>"))
        assertTrue(html.contains("securityLevel: 'strict'"))
        assertFalse(html.contains("'loose'"))
    }

    @Test
    fun `markdown output is sanitised before it reaches the DOM`() {
        val html = MarkdownWebContent.buildHtml("# hi", isDarkTheme = false)
        assertTrue(html.contains("DOMPurify.sanitize(marked.parse(md)"))
        assertTrue(html.contains(CdnAssets.DOMPURIFY.url))
    }

    @Test
    fun `the app-authored templates declare one CSP, first in head, hardened`() {
        val documents = mapOf(
            "markdown" to MarkdownWebContent.buildHtml("x", isDarkTheme = false),
            "mermaid" to MermaidWebContent.buildHtml("graph TD; A-->B", isDarkTheme = false),
            "svg" to ArtifactWebContent.buildHtml("<svg/>", "image/svg+xml", isDarkTheme = false),
            "code" to ArtifactWebContent.buildHtml("x", "application/vnd.code-html", isDarkTheme = false),
            "html host" to ArtifactWebContent.buildHtml("<p>x</p>", "text/html", isDarkTheme = false),
            "react host" to ArtifactWebContent.buildHtml("export default () => null", "application/vnd.react", isDarkTheme = false),
        )
        documents.forEach { (name, html) ->
            val metas = cspMetas(html)
            assertEquals(1, metas.size, "$name: one CSP")
            assertTrue(firstHeadChild(html).startsWith("<meta http-equiv=\"Content-Security-Policy\""), "$name: CSP first")
            val csp = metas.single()
            assertTrue(csp.startsWith("default-src 'none'"), "$name: default-src")
            assertTrue(csp.contains("form-action 'none'"), "$name: form-action")
            assertTrue(csp.contains("base-uri 'none'"), "$name: base-uri")
            assertTrue(csp.contains("object-src 'none'"), "$name: object-src")
            assertTrue(csp.contains("frame-src "), "$name: frame-src")
        }
    }

    @Test
    fun `templates that frame nothing say so, sandbox hosts allow only about frames`() {
        assertTrue(cspMetas(MarkdownWebContent.buildHtml("x", isDarkTheme = false)).single().contains("frame-src 'none'"))
        val host = ArtifactWebContent.buildHtml("<p>x</p>", "text/html", isDarkTheme = false)
        assertTrue(cspMetas(host).single().contains("frame-src about:"))
    }

    @Test
    fun `an html artifact is the srcdoc of a frame that may only run scripts`() {
        val content = "<html><head><title>t</title></head><body><form action=\"https://example.org\"></form></body></html>"
        val host = ArtifactWebContent.buildHtml(content, "text/html", isDarkTheme = false)

        assertTrue(host.contains("""<iframe sandbox="allow-scripts" srcdoc="""))
        assertFalse(host.contains("allow-same-origin"))
        assertFalse(host.contains("allow-forms"))
        assertFalse(host.contains("allow-top-navigation"))
        assertFalse(host.contains("allow-popups"))
        // The artifact's markup is attribute-escaped in the host and intact inside the frame.
        assertFalse(host.contains("<form"))
        val inner = SandboxHostSupport.sandboxedDocument(host)
        assertTrue(inner.contains("<form action=\"https://example.org\">"))
        assertTrue(inner.contains("<title>t</title>"))
        assertTrue(inner.contains("https://cdn.tailwindcss.com/3.4.17"))
    }

    @Test
    fun `an office html preview is sandboxed the same way`() {
        val host = ArtifactWebContent.buildOfficePreviewHtml("<b>doc</b>", "html", isDarkTheme = false)
        assertTrue(host.contains("""<iframe sandbox="allow-scripts" srcdoc="""))
        // The preview MIME the office card hands over, not the document's own: only that one
        // takes the pass-through branch of buildHtml.
        val preview = ArtifactType.DEFAULT_OFFICE_PREVIEW_MIME
        assertEquals(host, ArtifactWebContent.buildHtml(host, preview, isDarkTheme = false))
    }

    @Test
    fun `every CDN script and stylesheet is pinned and integrity-checked`() {
        val react = SandboxHostSupport.sandboxedDocument(
            ArtifactWebContent.buildHtml("export default () => null", "application/vnd.react", isDarkTheme = false),
        )
        val documents = listOf(
            MarkdownWebContent.buildHtml("x", isDarkTheme = true),
            MermaidWebContent.buildHtml("graph TD; A-->B", isDarkTheme = false),
            react,
        )
        documents.forEach { html ->
            scriptSources(html).forEach { tag ->
                if (tag.contains("cdn.tailwindcss.com")) {
                    // No CORS headers from that host, so no SRI possible; the version is pinned instead.
                    assertTrue(tag.contains("https://cdn.tailwindcss.com/3.4.17"), tag)
                } else {
                    assertTrue(tag.contains("""integrity="sha384-"""), "no integrity on $tag")
                    assertTrue(tag.contains("""crossorigin="anonymous""""), "no crossorigin on $tag")
                    assertTrue(Regex("""@\d+\.\d+\.\d+/""").containsMatchIn(tag), "not pinned to an exact version: $tag")
                }
            }
            Regex("""<link rel="stylesheet"[^>]*>""").findAll(html).forEach { link ->
                assertTrue(link.value.contains("""integrity="sha384-"""), "no integrity on ${link.value}")
            }
        }
        assertFalse(react.contains("unpkg.com"))
        assertFalse(react.contains("@babel/standalone/babel.min.js\""))
    }

    @Test
    fun `no template names the CDN as its own origin`() {
        val html = MarkdownWebContent.buildHtml("x", isDarkTheme = false)
        assertFalse(html.contains("<base"))
    }

    @Test
    fun `resource policies match the templates`() {
        assertTrue(ArtifactWebContent.resourcePolicy("text/markdown").allows("https://example.org/a.png", isMainFrame = false))
        assertFalse(ArtifactWebContent.resourcePolicy("application/vnd.mermaid").allows("https://example.org/a.png", isMainFrame = false))
        assertTrue(ArtifactWebContent.resourcePolicy("application/vnd.mermaid").allows(CdnAssets.MERMAID.url, isMainFrame = false))
        assertTrue(ArtifactWebContent.resourcePolicy("application/vnd.react").allows("https://esm.sh/react@18.3.1", isMainFrame = false))
        assertFalse(ArtifactWebContent.resourcePolicy("image/svg+xml").allows("https://example.org/a.png", isMainFrame = false))
        assertFalse(ArtifactWebContent.resourcePolicy("text/html").allows("https://example.org/", isMainFrame = true))
    }

    @Test
    fun `escapeHtml covers the five characters an attribute or text node can be broken with`() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", ArtifactWebContent.escapeHtml("&<>\"'"))
        assertEquals("&amp;lt;", ArtifactWebContent.escapeHtml("&lt;"))
    }
}
