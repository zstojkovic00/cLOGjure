# cLOGjure

CLI application that enables semantic search over large application log files. It works by building an inverted index and timestamp index from the log file, then using statistical ranking algorithms (TF-IDF, BM25) to return the most relevant results for a query.

## Usage
```bash
# Build indexes (one-time)
clogjure index logs/app.log

# Search with ranking
clogjure search "memory leak error" --from 2024-01-01 --top 10
```

# How it works

1. **Indexing**: Parses the log file and creates two indexes
    - Inverted index: maps each word to byte offsets where it appears
    - Timestamp index: maps byte offsets to timestamps

2. **Searching**: Takes your query and finds matching log lines
    - Finds intersection of byte offsets for query terms
    - Filters by timestamp range if specified
    - Calculates TF-IDF/BM25 scores for each match
    - Returns top N results sorted by relevance