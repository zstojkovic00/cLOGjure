# cLOGjure

Lightweight in-memory full-text search engine for large log files. Builds an inverted index and uses TF-IDF ranking to return the most relevant results.

## How it works

1. **Indexing** - Parses log file, creates inverted index (word → byte offsets) and timestamp index
2. **Search** - O(1) lookup for exact match, O(log n + k) for prefix ; seeks to byte offset to read lines
3. **Ranking** - TF-IDF scoring (Term Frequency × Inverse Document Frequency)

## Usage

```bash
lein midje ## run tests
lein run
```

### Commands
```
clogjure> index logs/app.log    # Create index from log file
clogjure> ls                    # List available indexes
clogjure> use app-inverted.idx  # Load existing index
clogjure> status                # Show current index info
clogjure> search error          # Search for word (AND logic is default)
clogjure> clear                 # Clear screen
clogjure> exit                  # Exit
```

### Search options

```
search <words> [options]

Options:
  --any              Match lines containing ANY of the given words (OR)
  --all              Match lines containing ALL of the given words (AND, default)
  --prefix           Match by word prefix
  --from DATE        Filter matching lines starting from DATE
  --to DATE          Filter matching lines ending at DATE
```

Examples:
```
clogjure> search error                     # Lines containing error
clogjure> search error memory              # Lines containing error AND memory
clogjure> search error warning --any       # Lines containing error OR warning
clogjure> search err --prefix              # Lines with words starting with err
clogjure> search error --from 2026-01-19T10:00:00 --to 2026-01-19T12:00:00
```