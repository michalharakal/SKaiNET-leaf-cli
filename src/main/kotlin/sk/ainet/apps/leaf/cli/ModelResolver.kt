package sk.ainet.apps.leaf.cli

import sk.ainet.apps.bert.BertModelConfig
import sk.ainet.apps.bert.MDBR_LEAF_IR_CONFIG
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

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

    fun detectConfig(modelDir: Path): BertModelConfig {
        val configPath = modelDir.resolve("config.json")
        if (!configPath.exists()) {
            println("No config.json found, using MDBR_LEAF_IR_CONFIG defaults")
            return MDBR_LEAF_IR_CONFIG
        }
        val json = configPath.readText()
        val denseConfigPath = modelDir.resolve("2_Dense/config.json")
        val denseJson = if (denseConfigPath.exists()) denseConfigPath.readText() else null
        return parseConfigJson(json, denseJson)
    }

    private fun parseConfigJson(json: String, denseJson: String? = null): BertModelConfig {
        fun extractInt(source: String, key: String, default: Int): Int {
            val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
            return pattern.find(source)?.groupValues?.get(1)?.toIntOrNull() ?: default
        }
        fun extractDouble(source: String, key: String, default: Double): Double {
            val pattern = Regex("\"$key\"\\s*:\\s*([\\d.eE\\-+]+)")
            return pattern.find(source)?.groupValues?.get(1)?.toDoubleOrNull() ?: default
        }

        val projDim = if (denseJson != null) {
            extractInt(denseJson, "out_features", 0).let { if (it > 0) it else null }
        } else {
            null
        }

        return BertModelConfig(
            vocabSize = extractInt(json, "vocab_size", 30522),
            hiddenSize = extractInt(json, "hidden_size", 384),
            numHiddenLayers = extractInt(json, "num_hidden_layers", 6),
            numAttentionHeads = extractInt(json, "num_attention_heads", 12),
            intermediateSize = extractInt(json, "intermediate_size", 1536),
            maxPositionEmbeddings = extractInt(json, "max_position_embeddings", 512),
            typeVocabSize = extractInt(json, "type_vocab_size", 2),
            layerNormEps = extractDouble(json, "layer_norm_eps", 1e-12),
            projectionDim = projDim
        )
    }
}
