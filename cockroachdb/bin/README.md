# Local `cockroach.exe` (optional)

[`../local/start-single-node.ps1`](../local/start-single-node.ps1) uses **`cockroachdb\bin\cockroach.exe` first**, then your `PATH`. If this folder still has an old binary (e.g. v19.x), `cockroach version` will stay old until you **replace** it.

## Upgrade / install (match [`../VERSION`](../VERSION))

1. Stop any running node: from repo root run `.\cockroachdb\local\stop-single-node.cmd` (or stop the process manually).
2. **Remove or rename** the old exe, e.g.  
   `del cockroach.exe` or `ren cockroach.exe cockroach-v19-backup.exe`
3. Download the Windows zip for the pinned version (Intel/amd64), e.g. **v25.2.15**:  
   [cockroach-v25.2.15.windows-6.2-amd64.zip](https://binaries.cockroachdb.com/cockroach-v25.2.15.windows-6.2-amd64.zip)  
   (Adjust the version in the URL when you bump [`../VERSION`](../VERSION).)
4. Extract **`cockroach.exe`** from the zip into **this directory** (`cockroachdb\bin\`).
5. Confirm:

```bat
cd /d C:\project\ai\cockroachdb\bin
cockroach version
```

You should see **v25.2.15** (or whatever is in `VERSION`).

## v19 → v25 store format

If you used the old binary with `cockroachdb\data` or `cockroachdb\cockroach-data`, that store may not open on v25. For local dev, delete that data folder and run Flyway again after starting the new binary. See [`../README.md`](../README.md).

## Other versions

Full matrix: [CockroachDB releases (v25.2)](https://www.cockroachlabs.com/docs/releases/v25.2) — use the **Windows (Experimental)** row for the matching patch.
