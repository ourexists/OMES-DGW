$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_image-packages.ps1")

Export-BackendImage
Write-Host "[build-backend] Done. From docker folder, run the release-package bat."
