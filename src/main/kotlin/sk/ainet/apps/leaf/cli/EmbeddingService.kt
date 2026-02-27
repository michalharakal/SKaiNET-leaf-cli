package sk.ainet.apps.leaf.cli

import sk.ainet.apps.bert.BertModelConfig
import sk.ainet.apps.bert.BertRuntime
import sk.ainet.apps.bert.HuggingFaceTokenizer
import sk.ainet.apps.bert.loadBertWeights
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.safetensors.SafeTensorsParametersLoader
import sk.ainet.lang.tensor.Tensor
import sk.ainet.lang.tensor.data.FloatArrayTensorData
import sk.ainet.lang.types.DType
import sk.ainet.lang.types.FP32
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class EmbeddingService private constructor(
    private val runtime: BertRuntime<FP32>,
    private val tokenizer: HuggingFaceTokenizer
) {

    fun embed(text: String): FloatArray {
        val tok = tokenizer.encodeWithMetadata(text)
        val tensor = runtime.encode(tok.inputIds, tok.attentionMask, tok.tokenTypeIds)
        return tensor.toFloatArray()
    }

    companion object {
        suspend fun load(modelDir: Path, config: BertModelConfig): EmbeddingService {
            val vocabPath = modelDir.resolve("vocab.txt")
            if (!vocabPath.exists()) error("vocab.txt not found in $modelDir")

            val tokenizer = HuggingFaceTokenizer.fromVocabTxt(vocabPath.readText())

            val ctx = DirectCpuExecutionContext()
            val loaders = buildList {
                val mainFile = resolveModelFile(modelDir)
                add(SafeTensorsParametersLoader(
                    sourceProvider = { JvmRandomAccessSource.open(mainFile.toString()) },
                    onProgress = { _, _, _ -> }
                ))
                val denseFile = modelDir.resolve("2_Dense/model.safetensors")
                if (denseFile.exists()) {
                    add(SafeTensorsParametersLoader(
                        sourceProvider = { JvmRandomAccessSource.open(denseFile.toString()) },
                        onProgress = { _, _, _ -> }
                    ))
                }
            }

            val weights = loadBertWeights(loaders, ctx, FP32::class, config)
            val runtime = BertRuntime(ctx, weights, FP32::class)

            return EmbeddingService(runtime, tokenizer)
        }

        private fun resolveModelFile(modelDir: Path): Path {
            val candidates = listOf("model.safetensors", "pytorch_model.safetensors")
            for (name in candidates) {
                val p = modelDir.resolve(name)
                if (p.exists()) return p
            }
            val dir = modelDir.toFile()
            val found = dir.listFiles()?.firstOrNull { it.extension == "safetensors" }
            if (found != null) return found.toPath()
            error("No .safetensors file found in $modelDir")
        }

        private fun <T : DType> Tensor<T, Float>.toFloatArray(): FloatArray {
            val data = this.data
            if (data is FloatArrayTensorData<*>) return data.buffer.copyOf()
            return data.copyToFloatArray()
        }
    }
}
