package sk.ainet.apps.leaf.vector

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.sqrt

@Serializable
private data class VectorIndex(
    val modelDir: String,
    val documents: List<VectorDocument>,
)

class JsonFileVectorRepository(private val path: Path) : VectorRepository {

    private val documents = mutableListOf<VectorDocument>()

    override fun add(doc: VectorDocument) {
        documents += doc
    }

    override fun count(): Int = documents.size

    override fun search(queryEmbedding: FloatArray, topK: Int): List<ScoredDocument> =
        documents
            .map { ScoredDocument(it, cosineSimilarity(queryEmbedding, it.embedding)) }
            .sortedByDescending { it.score }
            .take(topK)

    fun save(modelDir: String) {
        val index = VectorIndex(modelDir = modelDir, documents = documents.toList())
        val json = Json { prettyPrint = true }
        path.writeText(json.encodeToString(VectorIndex.serializer(), index))
    }

    companion object {
        fun loadFrom(path: Path): Pair<JsonFileVectorRepository, String> {
            val json = Json { ignoreUnknownKeys = true }
            val index = json.decodeFromString(VectorIndex.serializer(), path.readText())
            val repo = JsonFileVectorRepository(path)
            index.documents.forEach { repo.add(it) }
            return repo to index.modelDir
        }
    }
}

private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vectors must have same dimension" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom > 0f) dot / denom else 0f
}
