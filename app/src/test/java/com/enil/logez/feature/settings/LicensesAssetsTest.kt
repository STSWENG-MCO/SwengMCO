package com.enil.logez.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The licences screen reads assets/licenses/notices.json at runtime, so a typo there fails
 * silently on a user's phone. This makes it fail here instead.
 */
class LicensesAssetsTest {
    private val dir = File("src/main/assets/${OssNotices.DIR}")
    private val notices = OssNotices.parse(File("src/main/assets/${OssNotices.INDEX}").readText())

    @Test
    fun `every notice points at a licence file that ships`() {
        notices.forEach { notice ->
            val file = notice.file ?: return@forEach
            assertTrue("${notice.name} -> missing $file", File(dir, file).isFile)
        }
    }

    @Test
    fun `names are unique, since the screen keys rows by name`() {
        assertEquals(notices.size, notices.map { it.name }.toSet().size)
    }

    @Test
    fun `the notices the audit named are present`() {
        val names = notices.joinToString("\n") { it.name }
        listOf("MapLibre Native", "vue-human-muscle-anatomy", "Chakra Petch", "IBM Plex Sans", "IBM Plex Mono", "Public Suffix List")
            .forEach { assertTrue("missing notice for $it", names.contains(it)) }
    }

    @Test
    fun `only a linked-only licence may have no text file`() {
        notices.filter { it.file == null }.forEach { assertTrue("${it.name} has neither a file nor a link", !it.url.isNullOrBlank()) }
    }
}
