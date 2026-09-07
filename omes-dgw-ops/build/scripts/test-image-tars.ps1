$ErrorActionPreference = "Stop"

$BuildDir = Split-Path -Parent $PSScriptRoot
$DockerRoot = Split-Path -Parent $BuildDir
. (Join-Path $DockerRoot "deploy\scripts\_image-versions.ps1")

$imagesDir = Join-Path $BuildDir "images"
$missing = @()

foreach ($image in Get-CollectDeployImageList) {
    $tarPath = Join-Path $imagesDir $image.Tar
    if (-not (Test-Path $tarPath)) {
        $missing += $image.Tar
    }
}

if ($missing.Count -eq 0) {
    exit 0
}

Write-Host "[test-image-tars] Missing: $($missing -join ', ')"
exit 1
