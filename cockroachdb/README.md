# CockroachDB (local development)

This repo targets **CockroachDB v25.2.x** (LTS). The pin is recorded in [`VERSION`](VERSION) and in [`docker-compose.yml`](docker-compose.yml).

## Option A: Docker Compose (recommended)

From this directory:

```powershell
docker compose up -d
```

- **SQL**: `localhost:26257`
- **DB Console**: http://localhost:8080
- **Data**: `./data` (gitignored)

Stop:

```powershell
docker compose down
```

To pick a newer patch release, edit `VERSION` and the `image:` line in `docker-compose.yml` to match (see [Cockroach releases](https://www.cockroachlabs.com/docs/releases/)).

## Option B: `docker run` (PowerShell)

```powershell
# from repo root
$tag = Get-Content .\cockroachdb\VERSION
docker run -d --name erp-cockroach `
  -p 26257:26257 -p 8080:8080 `
  -v "${PWD}\cockroachdb\data:/cockroach/cockroach-data" `
  "cockroachdb/cockroach:v$tag" `
  start-single-node --insecure `
  --listen-addr=0.0.0.0:26257 --http-addr=0.0.0.0:8080 `
  --advertise-addr=localhost:26257 --store=/cockroach/cockroach-data
```

## Option C: Windows binary

1. Download the **same major.minor** as [`VERSION`](VERSION) from [Install CockroachDB on Windows](https://www.cockroachlabs.com/docs/stable/install-cockroachdb-windows.html) or [Releases](https://www.cockroachlabs.com/docs/releases/).
2. Put `cockroach.exe` on your `PATH`, or copy it to `c:\project\ai\cockroachdb\bin\cockroach.exe` (see [`bin/README.md`](bin/README.md)). **If you still see v19.x**, the helper prefers `bin\cockroach.exe` — delete/rename the old exe and install the new one (direct zip link in `bin/README.md`).
3. Verify: `cockroach version` should report **v25.2.x** (same patch as [`VERSION`](VERSION)).

Start with the helper (stores data under `cockroachdb\data`):

```bat
REM from repo root — works when Get-ExecutionPolicy is Restricted
.\cockroachdb\local\start-single-node.cmd
```

Or in PowerShell (one-off bypass):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\cockroachdb\local\start-single-node.ps1
```

Stop:

```bat
.\cockroachdb\local\stop-single-node.cmd
```

If `Get-ExecutionPolicy` is **Restricted**, plain `.\*.ps1` will fail unless you use the **`.cmd`** shims above, `-ExecutionPolicy Bypass -File …`, or set e.g. `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once.

## Upgrading from very old binaries (e.g. v19.x)

On-disk store format is **not** compatible across that many major jumps. For **local dev**, stop Cockroach, delete the old store directory (`cockroachdb\data`, `cockroachdb\cockroach-data`, or the Docker volume path you used), then start again with the new version and re-run Flyway.

## Connect (SQL shell)

```powershell
cockroach sql --insecure --host=localhost --port=26257 --database=defaultdb


cockroach start-single-node --insecure `--listen-addr=localhost:26257 `--http-addr=localhost:8080 `--store=C:\project\ai\cockroachdb\cockroach-data


``

## IAM / entity-builder JDBC URL

Default in `application.yml`:

- `jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable`

Override:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable'
```

## “Connection refused” to `localhost:26257`

Nothing is listening on the SQL port yet (or Docker is not running).

1. **Docker Compose:** Start **Docker Desktop** (or the Docker engine), then from `cockroachdb\` run `docker compose up -d`. Check http://localhost:8080
2. **Native binary:** From repo root run `.\cockroachdb\local\start-single-node.cmd` (requires `cockroach.exe` on `PATH` or in `cockroachdb\bin\` — see Option C).

Quick check in PowerShell: `Test-NetConnection localhost -Port 26257` → `TcpTestSucceeded` should be **True** before starting IAM / entity-builder.

## Notes

- `--insecure` is for **local dev only**.
- Local data directories (`data/`, `cockroach-data/`) are gitignored.


taskkill /IM cockroach.exe /F

