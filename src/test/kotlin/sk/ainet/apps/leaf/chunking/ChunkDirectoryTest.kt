package sk.ainet.apps.leaf.chunking

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkDirectoryTest {

    private val chunker = SmartChunker(chunkSize = 100)

    @Test
    fun `walks directory and ignores non-markdown files`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("a.md"), "Hello A.")
        Files.writeString(dir.resolve("b.md"), "Hello B.")
        Files.writeString(dir.resolve("c.txt"), "Hello C.")

        val sources = chunkDirectory(chunker, dir).map { it.source }
        assertEquals(listOf("a.md", "b.md"), sources)
    }

    @Test
    fun `walks nested directories with relative source paths`(@TempDir dir: Path) {
        Files.createDirectory(dir.resolve("sub"))
        Files.writeString(dir.resolve("top.md"), "top")
        Files.writeString(dir.resolve("sub/nested.md"), "nested")

        val sources = chunkDirectory(chunker, dir).map { it.source }.sorted()
        assertEquals(listOf("sub/nested.md", "top.md"), sources)
    }

    @Test
    fun `returns empty list when no markdown files present`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("a.txt"), "ignored")
        Files.writeString(dir.resolve("b.json"), "{}")

        assertTrue(chunkDirectory(chunker, dir).isEmpty())
    }

    @Test
    fun `walks files in deterministic alphabetical order`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("c.md"), "C")
        Files.writeString(dir.resolve("a.md"), "A")
        Files.writeString(dir.resolve("b.md"), "B")

        val sources = chunkDirectory(chunker, dir).map { it.source }
        assertEquals(listOf("a.md", "b.md", "c.md"), sources)
    }
}
