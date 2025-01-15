
## Android Api 24
- exceptions do not occur on modern versions

`SQLiteException: non-deterministic functions prohibited in index expressions (code 1): , while compiling: CREATE UNIQUE INDEX IF NOT EXISTS account_handle_unique_lower_idx ON account (lower(handle)) WHERE deleted_at IS NULL`
- lower considered nondeterministic in this version of SQLite
- use `lower()` during insert, not on index
- alt: `COLLATE NOCASE`

`SQLiteException: no such column: TRUE (code 1): , while compiling: CREATE UNIQUE INDEX IF NOT EXISTS installation_self_unique_idx ON installation (self) WHERE self = TRUE`
- use 1/0
