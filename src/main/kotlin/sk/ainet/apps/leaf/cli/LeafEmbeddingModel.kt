@file:Suppress("DEPRECATION")

package sk.ainet.apps.leaf.cli

import kotlinx.coroutines.runBlocking
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.context.ExecutionContext
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.safetensors.SafeTensorsParametersLoader
import sk.ainet.lang.types.FP32
import sk.ainet.llm.api.EmbeddingModel
import sk.ainet.llm.providers.SkaiNetEmbeddingModel
import sk.ainet.models.bert.BertModelConfig
import sk.ainet.models.bert.BertRuntime
import sk.ainet.models.bert.HuggingFaceTokenizer
import sk.ainet.models.bert.MDBR_LEAF_IR_CONFIG
import sk.ainet.models.bert.loadBertWeights
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Sample-app one-call factory for LEAF (mdbr-leaf-mt) sentence-transformers
 * BERT, returning the framework-neutral [EmbeddingModel] SPI from
 * `sk.ainet.transformers:skainet-transformers-api`.
 *
 * Mirrors the public-API factory shape used by `Gemma4ChatModel.fromSafeTensors`
 * in `sk.ainet.transformers:skainet-transformers-runtime-kgemma`: a singleton
 * object with a single `fromSafeTensors(...)` entry point that hides the
 * runtime / weight-loader / tokenizer assembly behind the neutral SPI.
 *
 * Once the public `BertEmbeddingModel.load(...)` factory described in
 * `PRD-skainet-transformers-bert-embeddings.md` lands upstream, this file
 * becomes a thin alias and eventually goes away.
 */
object LeafEmbeddingModel {

    /**
     * Load a HuggingFace sentence-transformers BERT directory and return it
     * behind the neutral [EmbeddingModel] SPI.
     *
     * Auto-detects:
     *   - base weights file: `model.safetensors`, `pytorch_model.safetensors`,
     *     or any `*.safetensors` in the directory
     *   - optional dense projection: `2_Dense/model.safetensors` (loaded as a
     *     second [SafeTensorsParametersLoader] and merged before tensor mapping)
     *   - model config: `config.json` + optional `2_Dense/config.json`; falls
     *     back to [MDBR_LEAF_IR_CONFIG] when neither is present
     *
     * Tokenizer is loaded from `vocab.txt` (the LEAF / MongoDB snapshot ships
     * one).
     *
     * @param modelDir directory containing the HF snapshot
     * @param ctx execution context; defaults to [DirectCpuExecutionContext]
     * @param modelId reported via [EmbeddingModel] response metadata; defaults
     *   to the model directory name
     */
    fun fromSafeTensors(
        modelDir: Path,
        ctx: ExecutionContext = DirectCpuExecutionContext(),
        modelId: String? = modelDir.fileName.toString(),
    ): EmbeddingModel {
        val vocabPath = modelDir.resolve("vocab.txt")
        if (!vocabPath.exists()) error("vocab.txt not found in $modelDir")
        val tokenizer = HuggingFaceTokenizer.fromVocabTxt(vocabPath.readText())

        val config = detectConfig(modelDir)
        val loaders = buildList {
            add(safetensorsLoader(resolveModelFile(modelDir)))
            val denseFile = modelDir.resolve("2_Dense/model.safetensors")
            if (denseFile.exists()) add(safetensorsLoader(denseFile))
        }
        val weights = runBlocking { loadBertWeights(loaders, ctx, FP32::class, config) }
        val runtime = BertRuntime(ctx, weights, FP32::class)

        val dim = config.projectionDim ?: config.hiddenSize
        return SkaiNetEmbeddingModel(
            runtime = runtime,
            tokenizer = tokenizer,
            dimensions = dim,
            modelId = modelId,
        )
    }

    private fun safetensorsLoader(file: Path) = SafeTensorsParametersLoader(
        sourceProvider = { JvmRandomAccessSource.open(file.toString()) },
        onProgress = { _, _, _ -> },
    )

    private fun resolveModelFile(modelDir: Path): Path {
        listOf("model.safetensors", "pytorch_model.safetensors").forEach { name ->
            val p = modelDir.resolve(name)
            if (p.exists()) return p
        }
        modelDir.toFile().listFiles()
            ?.firstOrNull { it.extension == "safetensors" }
            ?.let { return it.toPath() }
        error("No .safetensors file found in $modelDir")
    }

    private fun detectConfig(modelDir: Path): BertModelConfig {
        val configPath = modelDir.resolve("config.json")
        if (!configPath.exists()) return MDBR_LEAF_IR_CONFIG
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
            projectionDim = projDim,
        )
    }
}
