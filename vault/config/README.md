# Vault server configuration

| File | Purpose |
|------|---------|
| [`file-persistent.hcl`](file-persistent.hcl) | Single-node **file** backend; data under repo `vault/file-data/` (gitignored). Used by [`../linux/dev-server.sh`](../linux/dev-server.sh) and [`../windows/dev-server.ps1`](../windows/dev-server.ps1). |

Paths in the HCL are relative to the **process working directory** (the ERP repo root when using those scripts).

For a throwaway in-memory server (no persistence), run manually: `vault server -dev` (not recommended for keeping secrets).
