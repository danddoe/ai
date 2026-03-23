# Sets E2E_USER_PASSWORD from Vault for Playwright.
# Requires: VAULT_ADDR, VAULT_TOKEN, vault.exe on PATH; KV path secret/erp/dev/e2e key user_password
$ErrorActionPreference = 'Stop'
$e2ePath = if ($env:VAULT_E2E_PATH) { $env:VAULT_E2E_PATH } else { 'secret/erp/dev/e2e' }
$field = if ($env:VAULT_E2E_FIELD) { $env:VAULT_E2E_FIELD } else { 'user_password' }
$raw = vault kv get -field=$field $e2ePath
Set-Item -Path Env:E2E_USER_PASSWORD -Value $raw
Write-Host "E2E_USER_PASSWORD loaded from Vault ($e2ePath#$field) (value not printed)."
