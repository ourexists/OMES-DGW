$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_image-packages.ps1")

Export-MiddlewareImages

Write-Host "[build-middleware] InfluxDB image saved to build\images\"
