package sk.ainet.apps.leaf.cli

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Resolves the LEAF model directory from an explicit path, the
 * `LEAF_MODEL_DIR` env var, or the standard HuggingFace / Deliverance caches.
 *
 * Config + safetensors-layout detection live next to the loader that uses
 * them — see [LeafEmbeddingModel].
 */
object ModelResolver {

    fun resolveModelDir(explicit: String? = null): Path {
        // 1. Explicit path
        if (explicit != null) {
            val p = Path.of(explicit)
            if (p.exists() && p.isDirectory()) return p
            error("Model directory not found: $explicit")
        }

        // 2. LEAF_MODEL_DIR env var
        val envDir = System.getenv("LEAF_MODEL_DIR")
        if (envDir != null) {
            val p = Path.of(envDir)
            if (p.exists() && p.isDirectory()) return p
        }

        // 3. HuggingFace cache
        val home = System.getProperty("user.home")
        val hfCache = Path.of(home, ".cache", "huggingface", "hub", "models--MongoDB--mdbr-leaf-ir", "snapshots")
        if (hfCache.exists() && hfCache.isDirectory()) {
            val snapshot = hfCache.toFile().listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
            if (snapshot != null) return snapshot.toPath()
        }

        // 4. Deliverance cache
        val deliveranceCache = Path.of(home, ".deliverance", "MongoDB_mdbr-leaf-ir")
        if (deliveranceCache.exists() && deliveranceCache.isDirectory()) return deliveranceCache

        error(
            "Could not find LEAF model. Provide --model-dir or set LEAF_MODEL_DIR, " +
                "or place model in ~/.cache/huggingface/hub/models--MongoDB--mdbr-leaf-ir/ " +
                "or ~/.deliverance/MongoDB_mdbr-leaf-ir/"
        )
    }
}
