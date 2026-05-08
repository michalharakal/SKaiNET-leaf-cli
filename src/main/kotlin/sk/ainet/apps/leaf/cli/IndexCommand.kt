@file:OptIn(ExperimentalCli::class)

package sk.ainet.apps.leaf.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import sk.ainet.apps.leaf.chunking.Chunker
import sk.ainet.apps.leaf.chunking.SmartChunker
import sk.ainet.apps.leaf.chunking.chunkDirectory
import sk.ainet.apps.leaf.embedding.LeafEmbeddingModel
import sk.ainet.apps.leaf.embedding.ModelResolver
import sk.ainet.apps.leaf.vector.JsonFileVectorRepository
import sk.ainet.apps.leaf.vector.VectorDocument
import sk.ainet.llm.api.EmbeddingModel
import java.nio.file.Path
import kotlin.time.measureTime

internal class IndexCommand : Subcommand("index", "Index markdown files for semantic search") {
    val folder by argument(ArgType.String, description = "Directory containing .md files")
    val modelDir by option(ArgType.String, shortName = "m", fullName = "model-dir", description = "Path to LEAF model directory")
    val output by option(ArgType.String, shortName = "o", fullName = "output", description = "Output index file path").default("leaf-index.json")
    val chunkSize by option(ArgType.Int, fullName = "chunk-size", description = "Target chunk size in characters").default(600)

    override fun execute() {
        val folderPath = Path.of(folder)
        if (!folderPath.toFile().isDirectory) error("Not a directory: $folder")

        print("Chunking documents in $folder... ")
        val chunker: Chunker = SmartChunker(chunkSize = chunkSize)
        val chunks = chunkDirectory(chunker, folderPath)
        println("${chunks.size} chunks from ${chunks.map { it.source }.distinct().size} files")

        val resolvedModelDir = ModelResolver.resolveModelDir(modelDir)
        print("Loading model... ")
        val embedder: EmbeddingModel
        val loadTime = measureTime { embedder = LeafEmbeddingModel.fromSafeTensors(resolvedModelDir) }
        println("done ($loadTime) — model=${resolvedModelDir.fileName}, dimensions=${embedder.dimensions}")

        val repo = JsonFileVectorRepository(Path.of(output))
        println("Generating embeddings...")
        val embedTime = measureTime {
            embedder.use {
                chunks.forEachIndexed { i, chunk ->
                    val embedding = it.embed(chunk.content)
                    repo.add(
                        VectorDocument(
                            id = "${chunk.source}#${chunk.chunkIndex}",
                            content = chunk.content,
                            source = chunk.source,
                            chunkIndex = chunk.chunkIndex,
                            embedding = embedding,
                        )
                    )
                    print("\r  ${i + 1}/${chunks.size} chunks embedded")
                }
            }
        }
        println("\r  ${chunks.size}/${chunks.size} chunks embedded ($embedTime)")

        repo.save(modelDir = resolvedModelDir.toAbsolutePath().toString())
        println("Index saved to $output (${repo.count()} documents)")
    }
}
