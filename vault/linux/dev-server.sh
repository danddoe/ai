#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"
CONFIG="$REPO_ROOT/vault/config/file-persistent.hcl"
if [[ ! -f "$CONFIG" ]]; then
  echo "Missing config: $CONFIG" >&2
  exit 1
fi

cat <<EOF
Starting Vault with file storage (persistent).
  Repo root : $REPO_ROOT
  Config    : $CONFIG
  Data dir  : $REPO_ROOT/vault/file-data (gitignored)

First time:
  export VAULT_ADDR='http://127.0.0.1:8200'
  vault operator init -key-shares=1 -key-threshold=1
  vault operator unseal <unseal-key>
  vault login <root-token>
  vault secrets enable -version=2 -path=secret kv

After each restart: vault operator unseal, then login or set VAULT_TOKEN.

Ephemeral only (no disk): run manually: vault server -dev
EOF

exec vault server -config="$CONFIG"
