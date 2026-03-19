Set-Location $PSScriptRoot
& (Join-Path (Split-Path $PSScriptRoot -Parent) "gradlew.bat") :api-gateway:bootRun
