package sk.ainet.apps.leaf.cli

import kotlinx.serialization.Serializable

@Serializable
data class VectorDocument(
    val id: String,
    val content: String,
    val source: String,
    val chunkIndex: Int,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorDocument) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
