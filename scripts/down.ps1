$ErrorActionPreference = "Stop"

Set-Location -Path (Join-Path $PSScriptRoot "..")

Write-Host "[INFO] Stopping and removing all compose resources..."
docker compose down
