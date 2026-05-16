# leaf-cli

A semantic search CLI built on pure JVM. Index your markdown documentation and query it with natural language — no database, no external vector engine, no Python.

Built as a companion project for the JavaLand Unconference session: **"Build Your Own Semantic Search Engine in Pure JVM (No Python, No DB, No Magic)"**.

## How it works

```mermaid
flowchart LR
    A[Markdown files]:::src --> B[Smart chunker<br/>paragraph / sentence / line aware]
    B --> C[LEAF embedder<br/>local CPU inference]
    C --> D[(In-memory<br/>vector store<br/>JSON on disk)]
    E[User question]:::src --> C
    C --> F{Cosine similarity}
    D --> F
    F --> G[Top-K chunks]:::out

    classDef src fill:#e8f0ff,stroke:#3060a0,color:#102040
    classDef out fill:#e7f7e1,stroke:#2f7c2c,color:#0e2c10
```

1. **Index** — reads `.md` files, splits them into overlapping chunks via `SmartChunker`, generates embeddings through the neutral `EmbeddingModel` SPI backed by the [MongoDB/mdbr-leaf-mt](https://huggingface.co/MongoDB/mdbr-leaf-mt) model, and persists the result through `JsonFileVectorRepository`.
2. **Ask** — loads the index, reads the model directory recorded inside it, embeds your question, runs cosine similarity over every stored chunk, and returns the top-K matches.

## Prerequisites

- Java 21+
- A local copy of the [MongoDB/mdbr-leaf-mt](https://huggingface.co/MongoDB/mdbr-leaf-mt) model (SafeTensors format)

### Model resolution

The CLI never asks where the model lives. `ModelResolver.resolveModelDir(...)` walks a fixed priority list and returns the first match:

```mermaid
flowchart TD
    A[ModelResolver.resolveModelDir] --> B{--model-dir flag?}
    B -- yes --> Z[Use explicit path]
    B -- no --> C{$LEAF_MODEL_DIR set?}
    C -- yes --> Z
    C -- no --> D{HF cache present?<br/>~/.cache/huggingface/hub/<br/>models--MongoDB--mdbr-leaf-ir/snapshots/}
    D -- yes --> E[Pick most recent snapshot]
    E --> Z
    D -- no --> F{Deliverance cache present?<br/>~/.deliverance/MongoDB_mdbr-leaf-ir/}
    F -- yes --> Z
    F -- no --> X[error: cannot locate model]

    classDef ok fill:#e7f7e1,stroke:#2f7c2c,color:#0e2c10
    classDef err fill:#fde2e2,stroke:#a13030,color:#3a0e0e
    class Z ok
    class X err
```

For `ask`, resolution is bypassed entirely — the absolute model path is written into the index file at `index` time and re-used on load, so queries always run against the exact model that produced the embeddings.

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

The code is split into four small packages — `cli`, `chunking`, `embedding`, `vector` — so each package owns one concern and depends only on stable abstractions (`Chunker`, `EmbeddingModel`, `VectorRepository`).

```mermaid
flowchart TB
    subgraph cli[sk.ainet.apps.leaf.cli]
        Main[Main.kt<br/>ArgParser bootstrap]
        Idx[IndexCommand]
        Ask[AskCommand]
    end

    subgraph chunking[sk.ainet.apps.leaf.chunking]
        ChI([Chunker])
        Smart[SmartChunker]
        Walk[chunkDirectory]
        Smart -. implements .-> ChI
    end

    subgraph embedding[sk.ainet.apps.leaf.embedding]
        Resolver[ModelResolver<br/>flag → env → HF cache → Deliverance]
        Factory[LeafEmbeddingModel<br/>fromSafeTensors factory]
    end

    subgraph vector[sk.ainet.apps.leaf.vector]
        Repo([VectorRepository])
        Json[JsonFileVectorRepository<br/>+ cosine similarity]
        Doc[VectorDocument]
        Json -. implements .-> Repo
    end

    subgraph SPI[Neutral embedding SPI]
        EM([EmbeddingModel])
    end

    subgraph Transformers[SKaiNET-transformers 0.23.5]
        Adapter[SkaiNetEmbeddingModel<br/>BERT → SPI adapter]
        Bert[BertRuntime<br/>+ HuggingFaceTokenizer]
    end

    subgraph Engine[SKaiNET engine 0.23.1<br/>aligned by transformers BOM]
        Ctx[DirectCpuExecutionContext]
        Safe[SafeTensorsParametersLoader]
    end

    Main --> Idx
    Main --> Ask
    Idx --> Walk
    Walk --> ChI
    Idx --> Resolver
    Ask --> Json
    Idx --> Factory
    Ask --> Factory
    Idx --> Repo
    Ask --> Repo
    Repo --> Doc
    Factory --> EM
    EM -. implements .-> Adapter
    Adapter --> Bert
    Bert --> Ctx
    Factory --> Safe
```

### Package layout

```
src/main/kotlin/sk/ainet/apps/leaf/
├── cli/
│   ├── Main.kt              # ArgParser bootstrap, wires subcommands
│   ├── IndexCommand.kt      # `index <folder>` — chunk → embed → persist
│   └── AskCommand.kt        # `ask <question>` — load → embed → search → render
├── chunking/
│   ├── Chunker.kt           # `Chunker` SPI + `DocumentChunk` + `chunkDirectory`
│   └── SmartChunker.kt      # paragraph / sentence / line-aware impl with overlap
├── embedding/
│   ├── ModelResolver.kt     # explicit → $LEAF_MODEL_DIR → HF cache → Deliverance
│   └── LeafEmbeddingModel.kt# `fromSafeTensors(...)` factory → neutral EmbeddingModel
└── vector/
    ├── VectorDocument.kt    # serializable id + content + source + chunkIndex + embedding
    ├── VectorRepository.kt  # narrow `add` / `count` / `search` SPI + `ScoredDocument`
    └── JsonFileVectorRepository.kt # in-memory list + JSON persistence + cosine similarity
```

Tests live under `src/test/kotlin/.../{chunking,vector}` and cover the chunker, the directory walker, and the JSON repository round-trip.

## Tech stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3.0 |
| JVM | Java 21 (with Vector API incubator) |
| Build | Gradle 8.13 (Kotlin DSL) |
| Embedding API | SKaiNET-transformers neutral `EmbeddingModel` SPI (0.23.5) |
| Model inference | SKaiNET BERT runtime on CPU (engine 0.23.1, aligned via transformers BOM) |
| Model format | SafeTensors (HuggingFace sentence-transformers layout, optional `2_Dense/` projection) |
| Embedding model | MongoDB/mdbr-leaf-mt (multilingual; dim = `projectionDim ?? hiddenSize`) |
| CLI parsing | kotlinx-cli |
| Serialization | kotlinx-serialization (JSON) |
| Tests | JUnit 5 + kotlin-test-junit5 |
| Packaging | ShadowJar (fat JAR) |

## Key design decisions

- **Package-per-concern** — `cli`, `chunking`, `embedding`, and `vector` are independent. The CLI is wiring; everything else is plain logic behind an interface (`Chunker`, `EmbeddingModel`, `VectorRepository`). Swapping the chunker, the embedder, or the storage backend is a one-line change in `IndexCommand` / `AskCommand`.
- **Neutral embedding API** — `LeafEmbeddingModel.fromSafeTensors(modelDir)` returns an `EmbeddingModel`, a small provider-neutral SPI shaped like the `embed(text)` / `embed(listOf(...))` / `call(EmbeddingRequest)` contract found across mainstream Java AI frameworks (Spring AI in particular). The factory mirrors `Gemma4ChatModel.fromSafeTensors(...)` upstream — singleton object, single one-call entry point that hides the runtime / weight-loader / tokenizer assembly behind the SPI. The CLI never touches BERT-specific types directly.
- **Zero-config model loading by default** — `ModelResolver` checks an explicit flag, then `LEAF_MODEL_DIR`, then the HuggingFace snapshot cache, then the Deliverance cache. Inside the model directory, `LeafEmbeddingModel` auto-detects the base SafeTensors file, the optional `2_Dense/` projection head, the `config.json`, and the `vocab.txt`. The user only specifies a path if the defaults miss.
- **Persisted model pointer** — `JsonFileVectorRepository.save(modelDir = ...)` writes the absolute model path into the index JSON. `ask` reads it back, guaranteeing the query model matches the indexed embeddings without the user passing `--model-dir` twice.
- **No database** — embeddings live in memory and persist as plain JSON. Vector databases are an optimization layer; this project teaches the fundamentals.
- **No external services** — model inference runs locally on CPU via SKaiNET's BERT runtime.
- **Smart chunking** — the chunker respects paragraph, sentence, and line boundaries rather than cutting mid-word. Chunks overlap by 100 characters for context continuity.
- **Brute-force search** — cosine similarity is computed against every stored embedding. Simple, correct, and sufficient for documentation-scale corpora.
- **Multilingual** — the LEAF model supports 50+ languages out of the box. Index English docs, query in German — it works.

## Version pinning

The app pins **`sk.ainet.transformers:*:0.23.5`** via `platform("sk.ainet.transformers:skainet-transformers-bom:0.23.5")` from public Maven Central. The transformers BOM transitively imports the engine BOM (`sk.ainet:skainet-bom:0.23.1` — the engine line tops out at 0.23.1; the `0.23.x` transformers releases are transformers-only patches on the same engine), so every `sk.ainet.core:*` artifact aligns to `0.23.1` automatically. The version-catalog entries for the engine artifacts list module coordinates without `version.ref`, leaving the BOM as the single source of truth. The version knob lives in one place: `gradle/libs.versions.toml` (`skainetTransformers = "0.23.5"`).

`LeafEmbeddingModel.kt` references engine types (`DirectCpuExecutionContext`, `SafeTensorsParametersLoader`, `FP32`, `JvmRandomAccessSource`) directly, so the four `sk.ainet.core:*` `implementation` lines in `build.gradle.kts` are required for the compile classpath — upstream declares them as Gradle `implementation` (runtime-only for consumers). Once the one-call `BertEmbeddingModel.load(...)` loader from the PRD lands, the engine types stop leaking into consumer code and those four lines go away.

The next simplification — replacing `LeafEmbeddingModel.kt`'s load path (multi-loader merge for `2_Dense/model.safetensors`, config auto-detect, vocab parsing) with a one-call `BertEmbeddingModel.fromSafeTensors(modelDir)` upstream — is tracked by [`PRD-skainet-transformers-bert-embeddings.md`](../PRD-skainet-transformers-bert-embeddings.md) at the workspace root and will land in a future transformers release.

## License

MIT
