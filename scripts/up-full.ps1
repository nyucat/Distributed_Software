$ErrorActionPreference = "Stop"

Set-Location -Path (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "[INFO] .env not found, created from .env.example"
}

Write-Host "[INFO] Starting full profile..."
docker compose --profile full up -d --build
docker compose ps
