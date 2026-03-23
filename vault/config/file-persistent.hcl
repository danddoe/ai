# Local persistent Vault (file storage). Not for production.
#
# Start from repository root (see vault/linux/dev-server.sh and vault/windows/dev-server.ps1)
# so storage path resolves to ./vault/file-data (gitignored).
#
# First time after install:
#   export VAULT_ADDR=http://127.0.0.1:8200   # PowerShell: $env:VAULT_ADDR = 'http://127.0.0.1:8200'
#   vault operator init -key-shares=1 -key-threshold=1
#   vault operator unseal <unseal-key>
#   vault login <root-token>
#   vault secrets enable -version=2 -path=secret kv
#
# After each Vault restart: unseal again (same key), then vault login or set VAULT_TOKEN.

ui            = true
disable_mlock = true

api_addr     = "http://127.0.0.1:8200"
cluster_addr = "http://127.0.0.1:8201"

listener "tcp" {
  address     = "127.0.0.1:8200"
  tls_disable = true
}

storage "file" {
  path = "vault/file-data"
}
