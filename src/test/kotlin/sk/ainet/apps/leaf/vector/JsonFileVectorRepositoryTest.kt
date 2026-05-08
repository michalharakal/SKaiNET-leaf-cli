package sk.ainet.apps.leaf.vector

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonFileVectorRepositoryTest {

    @Test
    fun `add increments count`(@TempDir dir: Path) {
        val repo = JsonFileVectorRepository(dir.resolve("idx.json"))
        assertEquals(0, repo.count())
        repo.add(doc("a", floatArrayOf(1f, 0f)))
        assertEquals(1, repo.count())
        repo.add(doc("b", floatArrayOf(0f, 1f)))
        assertEquals(2, repo.count())
    }

    @Test
    fun `search ranks by cosine similarity`(@TempDir dir: Path) {
        // Hand-checkable scores against query [1, 0]:
        //   x=[1,0]      → 1.0
        //   z=[1,1]      → 1/sqrt(2) ≈ 0.7071068
        //   y=[0,1]      → 0.0
        val repo = JsonFileVectorRepository(dir.resolve("idx.json")).apply {
            add(doc("x", floatArrayOf(1f, 0f)))
            add(doc("y", floatArrayOf(0f, 1f)))
            add(doc("z", floatArrayOf(1f, 1f)))
        }

        val results = repo.search(floatArrayOf(1f, 0f), topK = 3)

        assertEquals(listOf("x", "z", "y"), results.map { it.document.id })
        assertEquals(1.0f, results[0].score, 1e-5f)
        assertEquals(0.7071068f, results[1].score, 1e-5f)
        assertEquals(0.0f, results[2].score, 1e-5f)
    }

    @Test
    fun `search truncates to topK`(@TempDir dir: Path) {
        val repo = JsonFileVectorRepository(dir.resolve("idx.json"))
        repeat(5) { i -> repo.add(doc("d$i", floatArrayOf(i.toFloat() + 1f, 1f))) }
        assertEquals(2, repo.search(floatArrayOf(1f, 0f), topK = 2).size)
    }

    @Test
    fun `search on empty repository returns empty list`(@TempDir dir: Path) {
        val repo = JsonFileVectorRepository(dir.resolve("idx.json"))
        assertTrue(repo.search(floatArrayOf(1f, 0f), topK = 5).isEmpty())
    }

    @Test
    fun `save and loadFrom roundtrips documents and modelDir`(@TempDir dir: Path) {
        val path = dir.resolve("idx.json")
        JsonFileVectorRepository(path).apply {
            add(doc("a", floatArrayOf(0.1f, 0.2f, 0.3f), source = "sa.md"))
            add(doc("b", floatArrayOf(0.4f, 0.5f, 0.6f), source = "sb.md", chunkIndex = 1))
            save(modelDir = "/path/to/model")
        }

        val (loaded, modelDir) = JsonFileVectorRepository.loadFrom(path)

        assertEquals("/path/to/model", modelDir)
        assertEquals(2, loaded.count())
        val top = loaded.search(floatArrayOf(0.1f, 0.2f, 0.3f), topK = 1)
        assertEquals("a", top[0].document.id)
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), top[0].document.embedding)
    }

    private fun doc(
        id: String,
        embedding: FloatArray,
        source: String = "src",
        chunkIndex: Int = 0,
    ) = VectorDocument(
        id = id,
        content = "content-$id",
        source = source,
        chunkIndex = chunkIndex,
        embedding = embedding,
    )
}
