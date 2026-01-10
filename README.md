# cLOGjure

Lightweight in-memory full-text search engine for large log files. Builds an inverted index and uses TF-IDF ranking to return the most relevant results.

## Usage

```bash
lein run
```

```
clogjure> index logs/app.log    # Create index from log file
clogjure> ls                    # List available indexes
clogjure> use app-inverted.idx  # Load existing index
clogjure> search error          # Search for keyword/s
clogjure> clear                 # Clear screen
clogjure> exit                  # Exit
```

## How it works

- **Indexing** - Parses log file, creates inverted index (word → byte offsets)
- **Search** - O(1) lookup in memory, seeks to byte offset in original file