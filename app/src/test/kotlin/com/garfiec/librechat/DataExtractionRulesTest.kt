package com.garfiec.librechat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What a cloud backup and a device-to-device transfer must leave behind (finding M5, 26/09/2026).
 *
 * Read off the resource files rather than the running app: Robolectric is not on this module's test
 * classpath, and the rules are declarative anyway — the property to pin is that the manifest points
 * at the file and that the file names every store that carries a credential or a conversation.
 */
class DataExtractionRulesTest {

    private val resDir = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }
    private val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map(::File).first { it.isFile }

    /** (domain, path) pairs every section must exclude — one entry per store named in the review. */
    private val required = setOf(
        "database" to "librechat.db",
        "database" to "librechat.db-wal",
        "database" to "librechat.db-shm",
        "sharedpref" to "librechat_tokens.xml",
        "sharedpref" to "engine_secrets.xml",
        "file" to "datastore/",
        "file" to "diag_logs/",
    )

    private fun excludesOf(section: String): Set<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/data_extraction_rules.xml"))
        val sections = document.getElementsByTagName(section)
        assertThat(sections.length).isEqualTo(1)
        val excludes = (sections.item(0) as Element).getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .map { it.getAttribute("domain") to it.getAttribute("path") }
            .toSet()
    }

    @Test
    fun `the manifest declares the rules next to allowBackup=false`() {
        val text = manifest.readText()

        assertThat(text).contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        assertThat(text).contains("android:allowBackup=\"false\"")
    }

    @Test
    fun `cloud backup excludes every credential and cache store`() {
        assertThat(excludesOf("cloud-backup")).containsAtLeastElementsIn(required)
    }

    @Test
    fun `device transfer excludes the same stores`() {
        assertThat(excludesOf("device-transfer")).containsAtLeastElementsIn(required)
        assertThat(excludesOf("device-transfer")).isEqualTo(excludesOf("cloud-backup"))
    }

    @Test
    fun `nothing is explicitly included, so a new store is not silently opted in`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/data_extraction_rules.xml"))

        assertThat(document.getElementsByTagName("include").length).isEqualTo(0)
    }
}
