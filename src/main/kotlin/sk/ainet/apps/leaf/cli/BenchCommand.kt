@file:OptIn(ExperimentalCli::class)

package sk.ainet.apps.leaf.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sk.ainet.apps.leaf.embedding.ModelResolver
import sk.ainet.llm.api.EmbeddingModel
import sk.ainet.llm.providers.BertEmbeddingModel
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.measureTime

/**
 * Micro-benchmark for the embedding pipeline: model load time plus embedding
 * latency/throughput over three text-size buckets. Results print as a table
 * and optionally land in a JSON file, so runs against different SKaiNET
 * versions can be diffed (`bench --output bench-<version>.json`).
 */
internal class BenchCommand : Subcommand("bench", "Benchmark model load and embedding throughput") {
    val modelDir by option(ArgType.String, shortName = "m", fullName = "model-dir", description = "LEAF model directory or Hugging Face repo id (default: ${ModelResolver.DEFAULT_REPO})")
    val warmup by option(ArgType.Int, fullName = "warmup", description = "Warmup embeds per bucket (excluded from stats)").default(5)
    val iterations by option(ArgType.Int, fullName = "iterations", description = "Measured embeds per bucket").default(30)
    val output by option(ArgType.String, shortName = "o", fullName = "output", description = "Write results as JSON to this path")
    val label by option(ArgType.String, fullName = "label", description = "Free-form label stored in the JSON results (e.g. the SKaiNET version)")

    override fun execute() {
        val modelSpec = ModelResolver.resolve(modelDir)
        print("Loading model ($modelSpec)... ")
        val embedder: EmbeddingModel
        val loadTime = measureTime { embedder = ModelResolver.open(modelSpec) }
        println("done (${loadTime}) — dimensions=${embedder.dimensions}")

        val buckets = benchTexts()
        val results = embedder.use { model ->
            buckets.map { (name, text) ->
                // First embeds pay for JIT + any lazy init; keep them out of the stats.
                repeat(warmup) { model.embed(text) }
                val samplesMs = DoubleArray(iterations) {
                    val t0 = System.nanoTime()
                    model.embed(text)
                    (System.nanoTime() - t0) / 1e6
                }
                BucketResult.of(name, text.length, samplesMs).also { println(it.render()) }
            }
        }

        val report = BenchReport(
            label = label ?: detectedVersion() ?: "unknown",
            modelSpec = modelSpec,
            dimensions = embedder.dimensions,
            jvm = System.getProperty("java.version"),
            warmup = warmup,
            iterations = iterations,
            loadMs = loadTime.inWholeMilliseconds,
            buckets = results,
        )
        println("\nmodel load: ${report.loadMs} ms   (label: ${report.label})")

        output?.let {
            Path.of(it).writeText(Json { prettyPrint = true }.encodeToString(report))
            println("Results written to $it")
        }
    }

    /** SKaiNET version from the providers jar manifest, if available. */
    private fun detectedVersion(): String? =
        BertEmbeddingModel::class.java.`package`?.implementationVersion

    /**
     * Deterministic English-ish markdown so tokenization cost is realistic and
     * identical across runs/versions. Sizes mirror real usage: a heading-sized
     * snippet, a default 600-char chunk, and an oversized chunk.
     */
    private fun benchTexts(): List<Pair<String, String>> {
        val sentence = "Semantic search over markdown notes retrieves the most relevant chunks by cosine similarity of dense embeddings. "
        fun ofLength(target: Int) = buildString { while (length < target) append(sentence) }.take(target)
        return listOf(
            "short-60" to ofLength(60),
            "chunk-600" to ofLength(600),
            "long-2400" to ofLength(2400),
        )
    }
}

@Serializable
internal data class BucketResult(
    val name: String,
    val chars: Int,
    val minMs: Double,
    val meanMs: Double,
    val medianMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
    val embedsPerSec: Double,
    val charsPerSec: Double,
) {
    fun render(): String =
        "%-10s min %8.2f ms | median %8.2f ms | mean %8.2f ms | p95 %8.2f ms | %6.2f embeds/s | %9.0f chars/s"
            .format(name, minMs, medianMs, meanMs, p95Ms, embedsPerSec, charsPerSec)

    companion object {
        fun of(name: String, chars: Int, samplesMs: DoubleArray): BucketResult {
            val sorted = samplesMs.sorted()
            val mean = samplesMs.average()
            val median = sorted[sorted.size / 2]
            val p95 = sorted[((sorted.size - 1) * 95) / 100]
            return BucketResult(
                name = name,
                chars = chars,
                minMs = sorted.first(),
                meanMs = mean,
                medianMs = median,
                p95Ms = p95,
                maxMs = sorted.last(),
                embedsPerSec = 1000.0 / mean,
                charsPerSec = chars * 1000.0 / mean,
            )
        }
    }
}

@Serializable
internal data class BenchReport(
    val label: String,
    val modelSpec: String,
    val dimensions: Int,
    val jvm: String,
    val warmup: Int,
    val iterations: Int,
    val loadMs: Long,
    val buckets: List<BucketResult>,
)
