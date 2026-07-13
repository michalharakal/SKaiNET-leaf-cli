package sk.ainet.apps.leaf.embedding

import sk.ainet.llm.api.EmbeddingModel
import sk.ainet.llm.providers.BertEmbeddingModel
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Resolves and opens the LEAF embedding model from a *model spec*: a local
 * snapshot directory or a Hugging Face repo id (`owner/name`). With no spec,
 * falls back to the `LEAF_MODEL_DIR` env var, an already-downloaded HF hub
 * snapshot, and finally a Hub download of [DEFAULT_REPO] (cached by the
 * engine's data source under `~/.cache/skainet/models/`, offline-safe after
 * the first run).
 */
object ModelResolver {

    const val DEFAULT_REPO = "MongoDB/mdbr-leaf-ir"

    private val repoIdPattern = Regex("""[\w.-]+/[\w.-]+""")

    /**
     * Canonicalize a model spec. Returns either an absolute directory path or
     * an HF repo id — this exact string is stored in the index file, so `ask`
     * can reopen the same model with [open].
     */
    fun resolve(spec: String? = null): String {
        if (spec != null) {
            val p = Path.of(spec)
            if (p.exists() && p.isDirectory()) return p.toAbsolutePath().toString()
            if (repoIdPattern.matches(spec)) return spec
            error("Model spec is neither an existing directory nor a Hugging Face repo id: $spec")
        }

        val envDir = System.getenv("LEAF_MODEL_DIR")
        if (envDir != null) {
            val p = Path.of(envDir)
            if (p.exists() && p.isDirectory()) return p.toAbsolutePath().toString()
        }

        // Already-downloaded HF hub snapshot (huggingface-cli / Python layout)
        val home = System.getProperty("user.home")
        val hfCache = Path.of(home, ".cache", "huggingface", "hub", "models--MongoDB--mdbr-leaf-ir", "snapshots")
        if (hfCache.exists() && hfCache.isDirectory()) {
            val snapshot = hfCache.toFile().listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
            if (snapshot != null) return snapshot.toPath().toAbsolutePath().toString()
        }

        return DEFAULT_REPO
    }

    /**
     * Open a canonical spec from [resolve] (or one read back from an index
     * file): local directory → [BertEmbeddingModel.fromSafeTensors], repo id →
     * [BertEmbeddingModel.fromHuggingFace] (downloads on first use).
     */
    fun open(spec: String): EmbeddingModel {
        val p = Path.of(spec)
        return if (p.exists() && p.isDirectory()) {
            BertEmbeddingModel.fromSafeTensors(p)
        } else {
            BertEmbeddingModel.fromHuggingFace(spec)
        }
    }
}
