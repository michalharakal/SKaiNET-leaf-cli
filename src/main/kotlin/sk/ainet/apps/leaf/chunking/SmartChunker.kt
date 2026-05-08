package sk.ainet.apps.leaf.chunking

class SmartChunker(
    private val chunkSize: Int = 600,
    private val overlap: Int = 100,
) : Chunker {

    override fun chunk(text: String, source: String): List<DocumentChunk> =
        chunkText(text).mapIndexed { index, content ->
            DocumentChunk(content = content, source = source, chunkIndex = index)
        }

    private fun chunkText(text: String): List<String> {
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

        val paraBreak = window.lastIndexOf("\n\n")
        if (paraBreak > window.length / 2) return start + paraBreak + 2

        val sentenceBreak = window.lastIndexOf(". ")
        if (sentenceBreak > window.length / 2) return start + sentenceBreak + 2

        val lineBreak = window.lastIndexOf('\n')
        if (lineBreak > window.length / 2) return start + lineBreak + 1

        return end
    }
}
