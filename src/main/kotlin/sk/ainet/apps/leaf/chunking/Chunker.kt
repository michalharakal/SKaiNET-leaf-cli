package sk.ainet.apps.leaf.chunking

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

data class DocumentChunk(
    val content: String,
    val source: String,
    val chunkIndex: Int,
)

interface Chunker {
    fun chunk(text: String, source: String): List<DocumentChunk>
}

fun chunkDirectory(chunker: Chunker, dir: Path, extension: String = "md"): List<DocumentChunk> {
    val chunks = mutableListOf<DocumentChunk>()
    dir.toFile().walk()
        .filter { it.isFile && it.extension == extension }
        .sorted()
        .forEach { file ->
            val source = file.toPath().relativeTo(dir).toString()
            chunks += chunker.chunk(file.toPath().readText(), source)
        }
    return chunks
}
