package sk.ainet.apps.leaf.cli

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

data class DocumentChunk(
    val content: String,
    val source: String,
    val chunkIndex: Int
)

object DocumentChunker {

    fun chunkDirectory(directory: Path, chunkSize: Int = 600, overlap: Int = 100): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        val dir = directory.toFile()
        dir.walk()
            .filter { it.isFile && it.extension == "md" }
            .sorted()
            .forEach { file ->
                val relativePath = file.toPath().relativeTo(directory).toString()
                val text = file.toPath().readText()
                val fileChunks = chunkText(text, chunkSize, overlap)
                fileChunks.forEachIndexed { index, content ->
                    chunks.add(DocumentChunk(content = content, source = relativePath, chunkIndex = index))
                }
            }
        return chunks
    }

    internal fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
        if (text.length <= chunkSize) return listOf(text.trim()).filter { it.isNotEmpty() }

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            var end = (start + chunkSize).coerceAtMost(text.length)

            if (end < text.length) {
                end = findSmartBoundary(text, start, end)
            }

            val chunk = text.substring(start, end).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }

            val advance = end - overlap
            start = if (advance <= start) end else advance
        }

        return chunks
    }

    private fun findSmartBoundary(text: String, start: Int, end: Int): Int {
        val window = text.substring(start, end)

        // Prefer paragraph boundary
        val paraBreak = window.lastIndexOf("\n\n")
        if (paraBreak > window.length / 2) return start + paraBreak + 2

        // Then sentence boundary
        val sentenceBreak = window.lastIndexOf(". ")
        if (sentenceBreak > window.length / 2) return start + sentenceBreak + 2

        // Then newline
        val lineBreak = window.lastIndexOf('\n')
        if (lineBreak > window.length / 2) return start + lineBreak + 1

        return end
    }
}
