package sk.ainet.apps.leaf.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class VectorIndex(
    val modelDir: String,
    val documents: List<VectorDocument>
)

class VectorStore {
    private val documents = mutableListOf<VectorDocument>()

    fun add(doc: VectorDocument) {
        documents.add(doc)
    }

    fun size(): Int = documents.size

    fun search(queryEmbedding: FloatArray, topK: Int): List<Pair<VectorDocument, Float>> {
        return documents
            .map { doc -> doc to cosineSimilarity(queryEmbedding, doc.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
    }

    fun saveTo(path: Path, modelDir: String) {
        val index = VectorIndex(modelDir = modelDir, documents = documents.toList())
        val json = Json { prettyPrint = true }
        path.writeText(json.encodeToString(VectorIndex.serializer(), index))
    }

    companion object {
        fun loadFrom(path: Path): Pair<VectorStore, String> {
            val json = Json { ignoreUnknownKeys = true }
            val index = json.decodeFromString(VectorIndex.serializer(), path.readText())
            val store = VectorStore()
            index.documents.forEach { store.add(it) }
            return store to index.modelDir
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            require(a.size == b.size) { "Vectors must have same dimension" }
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
            return if (denom > 0f) dot / denom else 0f
        }
    }
}
