package com.Hrshd1eux.filemanager.feature.viewer.unified

import com.Hrshd1eux.filemanager.feature.viewer.unified.loader.TextPreviewLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextPreviewLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `loads small text file completely without truncation`() = runBlocking {
        val file = tempFolder.newFile("sample.txt")
        val content = (1..50).joinToString("\n") { "Line $it: The quick brown fox jumps over the lazy dog." }
        file.writeText(content)

        val result = TextPreviewLoader.load(file).getOrThrow()
        assertEquals(50, result.lines.size)
        assertEquals(50, result.totalLinesCount)
        assertFalse(result.isTruncated)
        assertEquals("Line 1: The quick brown fox jumps over the lazy dog.", result.lines.first())
        assertEquals("Line 50: The quick brown fox jumps over the lazy dog.", result.lines.last())
    }

    @Test
    fun `loads empty text file successfully`() = runBlocking {
        val file = tempFolder.newFile("empty.txt")
        file.writeText("")

        val result = TextPreviewLoader.load(file).getOrThrow()
        assertTrue(result.lines.isEmpty())
        assertEquals(0, result.totalLinesCount)
        assertFalse(result.isTruncated)
    }

    @Test
    fun `truncates text file exceeding max lines limit safely`() = runBlocking {
        val file = tempFolder.newFile("large.log")
        // Write 6,000 lines
        file.bufferedWriter().use { writer ->
            for (i in 1..6000) {
                writer.write("Entry $i: Status 200 OK\n")
            }
        }

        val result = TextPreviewLoader.load(file).getOrThrow()
        assertTrue(result.isTruncated)
        assertEquals(TextPreviewLoader.MAX_PREVIEW_LINES, result.lines.size)
        assertEquals(6000, result.totalLinesCount)
    }

    @Test
    fun `handles UTF-8 and special unicode characters accurately`() = runBlocking {
        val file = tempFolder.newFile("unicode.txt")
        val unicodeContent = "Hello 世界 🌍\nПривет мир\nBonjour le monde"
        file.writeText(unicodeContent)

        val result = TextPreviewLoader.load(file).getOrThrow()
        assertEquals(3, result.lines.size)
        assertEquals("Hello 世界 🌍", result.lines[0])
        assertEquals("Привет мир", result.lines[1])
        assertEquals("Bonjour le monde", result.lines[2])
        assertFalse(result.isTruncated)
    }

    @Test
    fun `returns failure when file does not exist`() = runBlocking {
        val nonexistent = File(tempFolder.root, "does_not_exist.txt")
        val result = TextPreviewLoader.load(nonexistent)
        assertTrue(result.isFailure)
    }
}
