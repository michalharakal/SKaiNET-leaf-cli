# leaf-cli

A semantic search CLI built on pure JVM. Index your markdown documentation and query it with natural language — no database, no external vector engine, no Python.

Built as a companion project for the JavaLand Unconference session: **"Build Your Own Semantic Search Engine in Pure Java (No DB, No Magic)"**.

## How it works

```
Markdown Files → Chunking → LEAF Embeddings (local inference) → In-Memory Vector Store → Cosine Similarity Search → CLI Output
```

1. **Index** — reads `.md` files, splits them into overlapping chunks, generates embeddings using the [MongoDB/mdbr-leaf-mt](https://huggingface.co/MongoDB/mdbr-leaf-mt) model, and saves the index to a JSON file.
2. **Ask** — loads the index, embeds your question, computes cosine similarity against all stored chunks, and returns the most relevant results.

## Prerequisites

- Java 21+
- A local copy of the [MongoDB/mdbr-leaf-mt](https://huggingface.co/MongoDB/mdbr-leaf-mt) model (SafeTensors format)

The model is auto-detected from these locations (in order):

1. `--model-dir` CLI argument
2. `LEAF_MODEL_DIR` environment variable
3. HuggingFace cache: `~/.cache/huggingface/hub/models--MongoDB--mdbr-leaf-ir/snapshots/`
4. Deliverance cache: `~/.deliverance/MongoDB_mdbr-leaf-ir/`

## Build

```bash
./gradlew shadowJar
```

Produces `build/libs/leaf-cli-all.jar`.

## Usage

### Index a folder of markdown files

```bash
java --enable-preview --add-modules jdk.incubator.vector \
  -jar build/libs/leaf-cli-all.jar index ./docs
```

Options:

| Flag | Default | Description |
|------|---------|-------------|
| `-m`, `--model-dir` | auto-detected | Path to LEAF model directory |
| `-o`, `--output` | `leaf-index.json` | Output index file path |
| `--chunk-size` | `600` | Target chunk size in characters |

### Ask a question

```bash
java --enable-preview --add-modules jdk.incubator.vector \
  -jar build/libs/leaf-cli-all.jar ask "How do I reset a password?"
```

Options:

| Flag | Default | Description |
|------|---------|-------------|
| `-i`, `--index` | `leaf-index.json` | Path to index file |
| `-k`, `--top-k` | `3` | Number of results to return |

### Example output

```
Loading index... done (142 documents)
Loading model... done (1.2s)
Searching... done (45ms)

Question: "How do I reset a password?"
Top 3 results:
────────────────────────────────────────────────────────────

  #1  Score: 0.8234  Source: docs/auth/password-reset.md
  ──────────────────────────────────────────────────────────
  To reset a password, navigate to the settings page and click...

  #2  Score: 0.7891  Source: docs/onboarding/accounts.md
  ──────────────────────────────────────────────────────────
  New users receive a temporary password that must be changed...

  #3  Score: 0.6543  Source: docs/security/credentials.md
  ──────────────────────────────────────────────────────────
  Credential rotation policies require password changes every...
```

## Architecture

```
src/main/kotlin/sk/ainet/apps/leaf/cli/
├── Main.kt              # CLI entry point (index & ask subcommands)
├── ModelResolver.kt     # Model discovery and config detection
├── EmbeddingService.kt  # BERT model loading and text-to-vector inference
├── DocumentChunker.kt   # Smart markdown chunking with overlap
├── VectorDocument.kt    # Serializable document + embedding container
└── VectorStore.kt       # In-memory vector storage and cosine similarity search
```

## Tech stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3.0 |
| JVM | Java 21 (with Vector API incubator) |
| Build | Gradle 8.13 (Kotlin DSL) |
| Model inference | [SKaiNET](https://github.com/nickolay-kondratyev/SKaiNET) BERT runtime (CPU) |
| Model format | SafeTensors |
| Embedding model | MongoDB/mdbr-leaf-mt (multilingual, 768-dim output) |
| CLI parsing | kotlinx-cli |
| Serialization | kotlinx-serialization (JSON) |
| Packaging | ShadowJar (fat JAR) |

## Key design decisions

- **No database** — embeddings live in memory and persist as plain JSON. Vector databases are an optimization layer; this project teaches the fundamentals.
- **No external services** — model inference runs locally on CPU via SKaiNET's BERT runtime.
- **Smart chunking** — the chunker respects paragraph, sentence, and line boundaries rather than cutting mid-word. Chunks overlap by 100 characters for context continuity.
- **Brute-force search** — cosine similarity is computed against every stored embedding. Simple, correct, and sufficient for documentation-scale corpora.
- **Multilingual** — the LEAF model supports 50+ languages out of the box. Index English docs, query in German — it works.

## License

MIT
