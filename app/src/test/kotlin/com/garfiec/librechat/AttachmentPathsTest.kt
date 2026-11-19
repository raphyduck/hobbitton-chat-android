package com.garfiec.librechat

import com.garfiec.librechat.core.common.AppCache
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The FileProvider serves what `AppCache.ATTACHMENTS_DIR` names (O4, 19/11/2026). The XML cannot
 * read a constant, so this test keeps the two in step: a directory renamed in one place only fails here.
 *
 * Read off the resource file, like [DataExtractionRulesTest]: Robolectric is not on this module's classpath.
 */
class AttachmentPathsTest {

    private val resDir = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    @Test
    fun `the file provider serves exactly the attachments directory`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "xml/attachment_paths.xml"))
        val paths = document.getElementsByTagName("cache-path")
        assertThat(paths.length).isEqualTo(1)
        assertThat((paths.item(0) as Element).getAttribute("path")).isEqualTo("${AppCache.ATTACHMENTS_DIR}/")
    }
}
