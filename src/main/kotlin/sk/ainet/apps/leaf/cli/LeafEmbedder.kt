@file:Suppress("DEPRECATION")

package sk.ainet.apps.leaf.cli

import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.safetensors.SafeTensorsParametersLoader
import sk.ainet.lang.types.FP32
import sk.ainet.llm.api.EmbeddingModel
import sk.ainet.llm.providers.SkaiNetEmbeddingModel
import sk.ainet.models.bert.BertModelConfig
import sk.ainet.models.bert.BertRuntime
import sk.ainet.models.bert.HuggingFaceTokenizer
import sk.ainet.models.bert.loadBertWeights
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

object LeafEmbedder {

    suspend fun load(modelDir: Path, config: BertModelConfig): EmbeddingModel {
        val vocabPath = modelDir.resolve("vocab.txt")
        if (!vocabPath.exists()) error("vocab.txt not found in $modelDir")
        val tokenizer = HuggingFaceTokenizer.fromVocabTxt(vocabPath.readText())

        val ctx = DirectCpuExecutionContext()
        val loaders = buildList {
            add(safetensorsLoader(resolveModelFile(modelDir)))
            val denseFile = modelDir.resolve("2_Dense/model.safetensors")
            if (denseFile.exists()) add(safetensorsLoader(denseFile))
        }
        val weights = loadBertWeights(loaders, ctx, FP32::class, config)
        val runtime = BertRuntime(ctx, weights, FP32::class)

        val dim = config.projectionDim ?: config.hiddenSize
        return SkaiNetEmbeddingModel(
            runtime = runtime,
            tokenizer = tokenizer,
            dimensions = dim,
            modelId = modelDir.fileName.toString(),
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
}
