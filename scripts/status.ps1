$ErrorActionPreference = "Stop"

Set-Location -Path (Join-Path $PSScriptRoot "..")

Write-Host "[INFO] Compose service status:"
docker compose ps
