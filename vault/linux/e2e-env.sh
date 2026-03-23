#!/usr/bin/env bash
# Source this file to export E2E_USER_PASSWORD from Vault for Playwright.
# Prerequisites: VAULT_ADDR, VAULT_TOKEN, vault CLI; KV path secret/erp/dev/e2e key user_password
set -euo pipefail
E2E_PATH="${VAULT_E2E_PATH:-secret/erp/dev/e2e}"
E2E_FIELD="${VAULT_E2E_FIELD:-user_password}"
E2E_USER_PASSWORD="$(vault kv get -field="$E2E_FIELD" "$E2E_PATH")"
export E2E_USER_PASSWORD
echo "E2E_USER_PASSWORD loaded from Vault ($E2E_PATH#$E2E_FIELD) (value not printed)."
