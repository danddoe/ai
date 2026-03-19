# CockroachDB (local development)

This repo uses **CockroachDB** as the database (PostgreSQL wire protocol). For local development you can run a **single-node** cluster.

## Option A: Install CockroachDB binary (Windows)

1. Download and install CockroachDB for Windows (official docs):
   - `https://www.cockroachlabs.com/docs/stable/install-cockroachdb-windows.html`

2. Put `cockroach.exe` on your PATH, or copy it into this repo at:
   - `c:\project\ai\cockroachdb\bin\cockroach.exe`

3. Verify:

```powershell
cockroach version

cockroach start-single-node --insecure --listen-addr=localhost:26257 --http-addr=localhost:8181


cockroach start --insecure --listen-addr=localhost:26257 --http-addr=localhost:8181
```

## Option B: Run with Docker (recommended if you already have Docker)

```powershell
docker run --rm --name crdb \
  -p 26257:26257 -p 8080:8080 \
  -v "${PWD}\\cockroachdb\\data:/cockroach/cockroach-data" \
  cockroachdb/cockroach:latest \
  start-single-node --insecure --store=/cockroach/cockroach-data
```

- **SQL port**: `26257`
- **DB Console (Admin UI)**: `http://localhost:8080`

## Start a local single node (binary)

If you installed the binary, you can run the helper script:

```powershell
# from repo root
.\\cockroachdb\\local\\start-single-node.ps1
```

Stop it with:

```powershell
.\\cockroachdb\\local\\stop-single-node.ps1
```

## Connect (SQL shell)

```powershell
cockroach sql --insecure --host=localhost --port=26257 --database=defaultdb
```

## IAM module connection string

The IAM module default JDBC URL is:

- `jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable`

You can override it with:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable'
```

## Notes

- `--insecure` is for **local dev only**.
- Data directory for local runs: `cockroachdb/data/` (ignored by git).
