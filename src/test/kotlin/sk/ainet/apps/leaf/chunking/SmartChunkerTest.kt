package sk.ainet.apps.leaf.chunking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartChunkerTest {

    @Test
    fun `short text fits in a single chunk`() {
        val out = SmartChunker(chunkSize = 100).chunk("Hello world.", source = "a.md")
        assertEquals(1, out.size)
        assertEquals("Hello world.", out[0].content)
        assertEquals("a.md", out[0].source)
        assertEquals(0, out[0].chunkIndex)
    }

    @Test
    fun `empty text yields no chunks`() {
        assertTrue(SmartChunker(chunkSize = 100).chunk("", source = "a.md").isEmpty())
    }

    @Test
    fun `whitespace-only text yields no chunks`() {
        assertTrue(SmartChunker(chunkSize = 100).chunk("   \n  \t  ", source = "a.md").isEmpty())
    }

    @Test
    fun `prefers paragraph boundary in upper half of window`() {
        // 12-char run, paragraph break at window[12]/20 (above midpoint), 12-char run.
        val text = "AAAAAAAAAAAA\n\nBBBBBBBBBBBB"
        val out = SmartChunker(chunkSize = 20, overlap = 2).chunk(text, "x.md")
        assertEquals("AAAAAAAAAAAA", out[0].content)
    }

    @Test
    fun `falls back to sentence boundary when no paragraph break in window`() {
        // No "\n\n"; multiple ". " — the last one in upper half wins.
        val text = "AAAA. BBBB. CCCC. DDDD."
        val out = SmartChunker(chunkSize = 20, overlap = 2).chunk(text, "x.md")
        assertEquals("AAAA. BBBB. CCCC.", out[0].content)
    }

    @Test
    fun `falls back to line break when no paragraph or sentence break`() {
        // No "\n\n", no ". " — the lone "\n" in upper half is the chosen split.
        val text = "AAAAA\nBB"
        val out = SmartChunker(chunkSize = 7, overlap = 1).chunk(text, "x.md")
        assertEquals("AAAAA", out[0].content)
    }

    @Test
    fun `chunk indices are sequential starting from zero and sources propagate`() {
        val text = "A".repeat(100)
        val out = SmartChunker(chunkSize = 20, overlap = 5).chunk(text, "x.md")
        assertTrue(out.size > 1, "expected multiple chunks; got ${out.size}")
        out.forEachIndexed { i, c -> assertEquals(i, c.chunkIndex) }
        assertTrue(out.all { it.source == "x.md" })
    }
}
