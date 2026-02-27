@file:OptIn(ExperimentalCli::class)

package sk.ainet.apps.leaf.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.time.measureTime

fun main(args: Array<String>) {
    val parser = ArgParser("leaf-cli")
    parser.subcommands(IndexCommand(), AskCommand())
    parser.parse(args)
}

private class IndexCommand : Subcommand("index", "Index markdown files for semantic search") {
    val folder by argument(ArgType.String, description = "Directory containing .md files")
    val modelDir by option(ArgType.String, shortName = "m", fullName = "model-dir", description = "Path to LEAF model directory")
    val output by option(ArgType.String, shortName = "o", fullName = "output", description = "Output index file path").default("leaf-index.json")
    val chunkSize by option(ArgType.Int, fullName = "chunk-size", description = "Target chunk size in characters").default(600)

    override fun execute() = runBlocking {
        val folderPath = Path.of(folder)
        if (!folderPath.toFile().isDirectory) error("Not a directory: $folder")

        // Chunk documents
        print("Chunking documents in $folder... ")
        val chunks = DocumentChunker.chunkDirectory(folderPath, chunkSize)
        println("${chunks.size} chunks from ${chunks.map { it.source }.distinct().size} files")

        // Resolve model
        val resolvedModelDir = ModelResolver.resolveModelDir(modelDir)
        val config = ModelResolver.detectConfig(resolvedModelDir)
        println("Model: ${resolvedModelDir.fileName} (hidden=${config.hiddenSize}, projection=${config.projectionDim ?: "none"})")

        // Load model
        print("Loading model... ")
        val service: EmbeddingService
        val loadTime = measureTime { service = EmbeddingService.load(resolvedModelDir, config) }
        println("done ($loadTime)")

        // Generate embeddings
        val store = VectorStore()
        println("Generating embeddings...")
        val embedTime = measureTime {
            chunks.forEachIndexed { i, chunk ->
                val embedding = service.embed(chunk.content)
                store.add(
                    VectorDocument(
                        id = "${chunk.source}#${chunk.chunkIndex}",
                        content = chunk.content,
                        source = chunk.source,
                        chunkIndex = chunk.chunkIndex,
                        embedding = embedding
                    )
                )
                print("\r  ${i + 1}/${chunks.size} chunks embedded")
            }
        }
        println("\r  ${chunks.size}/${chunks.size} chunks embedded ($embedTime)")

        // Save index
        val outputPath = Path.of(output)
        store.saveTo(outputPath, resolvedModelDir.toAbsolutePath().toString())
        println("Index saved to $outputPath (${store.size()} documents)")
    }
}

private class AskCommand : Subcommand("ask", "Ask a question against an indexed document set") {
    val question by argument(ArgType.String, description = "Question to search for")
    val indexFile by option(ArgType.String, shortName = "i", fullName = "index", description = "Path to index file").default("leaf-index.json")
    val topK by option(ArgType.Int, shortName = "k", fullName = "top-k", description = "Number of results to return").default(3)

    override fun execute() = runBlocking {
        val indexPath = Path.of(indexFile)
        if (!indexPath.toFile().exists()) error("Index file not found: $indexFile")

        // Load index
        print("Loading index... ")
        val (store, modelDirStr) = VectorStore.loadFrom(indexPath)
        println("done (${store.size()} documents)")

        // Load model
        val modelDir = Path.of(modelDirStr)
        val config = ModelResolver.detectConfig(modelDir)
        print("Loading model... ")
        val service: EmbeddingService
        val loadTime = measureTime { service = EmbeddingService.load(modelDir, config) }
        println("done ($loadTime)")

        // Embed question and search
        print("Searching... ")
        val queryEmbedding: FloatArray
        val searchTime = measureTime {
            queryEmbedding = service.embed(question)
        }
        val results = store.search(queryEmbedding, topK)
        println("done ($searchTime)")

        // Display results
        println()
        println("Question: \"$question\"")
        println("Top $topK results:")
        println("─".repeat(60))
        results.forEachIndexed { i, (doc, score) ->
            println()
            println("  #${i + 1}  Score: ${"%.4f".format(score)}  Source: ${doc.source}")
            println("  " + "─".repeat(56))
            val preview = doc.content.take(300).replace('\n', ' ')
            println("  $preview")
        }
    }
}
