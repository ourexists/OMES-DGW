$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_image-packages.ps1")

$layout = Get-BuildLayout
$DockerRoot = Split-Path -Parent $layout.BuildDir
$RepoRoot = Split-Path -Parent $DockerRoot
$artifacts = Get-CollectImageArtifacts

$distDir = Join-Path $layout.PackagesDir "dist"
if (-not (Test-Path $distDir)) {
    Write-Error "[build-frontend] Missing dist folder: $distDir`nRun npm run build and copy output to build\packages\dist\"
}

$indexHtml = Join-Path $distDir "index.html"
if (-not (Test-Path $indexHtml)) {
    Write-Error "[build-frontend] dist has no index.html: $distDir"
}

$nginxDir = Join-Path $layout.PackagesDir "nginx"
$nginxConf = Join-Path $RepoRoot "omes-dgw-web\nginx\default.conf"
if (-not (Test-Path $nginxConf)) {
    Write-Error "[build-frontend] Missing nginx config: $nginxConf"
}

New-Item -ItemType Directory -Force -Path $nginxDir | Out-Null
Copy-Item -Path $nginxConf -Destination (Join-Path $nginxDir "default.conf") -Force

New-Item -ItemType Directory -Force -Path $layout.ImagesDir | Out-Null
$tarPath = Join-Path $layout.ImagesDir $artifacts.FrontTar
$dockerfile = Join-Path $layout.BuildDir "Dockerfile.front"

Write-Host "[build-frontend] docker build $($artifacts.FrontTag) from packages\dist ..."
docker build -t $artifacts.FrontTag -f $dockerfile $layout.PackagesDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "[build-frontend] docker save -> $tarPath"
docker save -o $tarPath $artifacts.FrontTag
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "[build-frontend] Done. From docker folder, run the release-package bat."
