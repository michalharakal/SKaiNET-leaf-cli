package sk.ainet.apps.leaf.vector

data class ScoredDocument(val document: VectorDocument, val score: Float)

/**
 * Backend-agnostic surface for vector storage. Persistence is impl-specific:
 * a JSON-on-disk impl exposes save/load on the concrete type, a vector-DB
 * impl would write on every [add]. The interface stays narrow so future
 * backends don't inherit no-op methods.
 */
interface VectorRepository {
    fun add(doc: VectorDocument)
    fun count(): Int
    fun search(queryEmbedding: FloatArray, topK: Int): List<ScoredDocument>
}
