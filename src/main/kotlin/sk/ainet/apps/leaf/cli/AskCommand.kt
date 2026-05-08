@file:OptIn(ExperimentalCli::class)

package sk.ainet.apps.leaf.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import sk.ainet.apps.leaf.embedding.LeafEmbeddingModel
import sk.ainet.apps.leaf.vector.JsonFileVectorRepository
import sk.ainet.apps.leaf.vector.ScoredDocument
import sk.ainet.apps.leaf.vector.VectorRepository
import sk.ainet.llm.api.EmbeddingModel
import java.nio.file.Path
import kotlin.time.measureTime

internal class AskCommand : Subcommand("ask", "Ask a question against an indexed document set") {
    val question by argument(ArgType.String, description = "Question to search for")
    val indexFile by option(ArgType.String, shortName = "i", fullName = "index", description = "Path to index file").default("leaf-index.json")
    val topK by option(ArgType.Int, shortName = "k", fullName = "top-k", description = "Number of results to return").default(3)

    override fun execute() {
        val indexPath = Path.of(indexFile)
        if (!indexPath.toFile().exists()) error("Index file not found: $indexFile")

        print("Loading index... ")
        val (loaded, modelDirStr) = JsonFileVectorRepository.loadFrom(indexPath)
        val repo: VectorRepository = loaded
        println("done (${repo.count()} documents)")

        val modelDir = Path.of(modelDirStr)
        print("Loading model... ")
        val embedder: EmbeddingModel
        val loadTime = measureTime { embedder = LeafEmbeddingModel.fromSafeTensors(modelDir) }
        println("done ($loadTime)")

        print("Searching... ")
        val queryEmbedding: FloatArray
        val searchTime = measureTime {
            embedder.use { queryEmbedding = it.embed(question) }
        }
        val results = repo.search(queryEmbedding, topK)
        println("done ($searchTime)")

        printResults(question, topK, results)
    }
}

private fun printResults(question: String, topK: Int, results: List<ScoredDocument>) {
    println()
    println("Question: \"$question\"")
    println("Top $topK results:")
    println("─".repeat(60))
    results.forEachIndexed { i, scored ->
        println()
        println("  #${i + 1}  Score: ${"%.4f".format(scored.score)}  Source: ${scored.document.source}")
        println("  " + "─".repeat(56))
        val preview = scored.document.content.take(300).replace('\n', ' ')
        println("  $preview")
    }
}
