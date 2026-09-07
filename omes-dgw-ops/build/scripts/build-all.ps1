$ErrorActionPreference = "Stop"
$ScriptsDir = $PSScriptRoot

& (Join-Path $ScriptsDir "build-backend.ps1")
& (Join-Path $ScriptsDir "build-frontend.ps1")
& (Join-Path $ScriptsDir "build-middleware.ps1")

Write-Host "[build-all] All deploy images saved to build\images\"


