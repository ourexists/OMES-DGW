$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_image-packages.ps1")

$BuildDir = Split-Path -Parent $PSScriptRoot
$DockerRoot = Split-Path -Parent $BuildDir
$RepoRoot = Split-Path -Parent $DockerRoot
$artifacts = Get-CollectImageArtifacts

$DeployTemplate = Join-Path $DockerRoot "deploy"
$ReleaseDir = Join-Path $DockerRoot "release\omes-dgw-deploy"
$ImagesSrc = Join-Path $BuildDir "images"

foreach ($image in Get-CollectDeployImageList) {
    $tarPath = Join-Path $ImagesSrc $image.Tar
    if (-not (Test-Path $tarPath)) {
        $hint = switch ($image.Label) {
            "backend" { "Put $($artifacts.BackendJar) in build\packages, then run build-backend." }
            "frontend" { "Put dist in build\packages\dist, then run build-frontend." }
            "influxdb" { "Run build-middleware.ps1 (requires docker pull)." }
            default { "Run build-all.ps1." }
        }
        Write-Error "Missing $tarPath. $hint"
    }
}

New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

$legacyDb = Join-Path $ReleaseDir "db"
if (Test-Path $legacyDb) {
    Remove-Item -LiteralPath $legacyDb -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "[publish] Removed legacy db\ folder (DDL at package root)."
}

Write-Host "[publish] Copy deploy template -> $ReleaseDir (in-place, no full delete)"
Get-ChildItem -Path $DeployTemplate -Force | ForEach-Object {
    $destPath = Join-Path $ReleaseDir $_.Name
    Copy-Item -LiteralPath $_.FullName -Destination $destPath -Recurse -Force
}

$TemplatesDir = Join-Path $ReleaseDir "config-templates"
Write-Host "[publish] Sync config-templates from repo sources ..."
New-Item -ItemType Directory -Force -Path `
    (Join-Path $TemplatesDir "omes-dgw\config"), `
    (Join-Path $TemplatesDir "nginx"), `
    (Join-Path $TemplatesDir "omes-dgw\monitoring") | Out-Null

Copy-Item (Join-Path $RepoRoot "config\config-box.properties") `
    (Join-Path $TemplatesDir "omes-dgw\config\") -Force
Copy-Item (Join-Path $RepoRoot "config\config-center.properties") `
    (Join-Path $TemplatesDir "omes-dgw\config\") -Force
Copy-Item (Join-Path $RepoRoot "config\README.md") `
    (Join-Path $TemplatesDir "omes-dgw\config\") -Force
Copy-Item (Join-Path $RepoRoot "omes-dgw-web\nginx\default.conf") `
    (Join-Path $TemplatesDir "nginx\") -Force
$monitoringSrc = Join-Path $RepoRoot "monitoring"
if (Test-Path $monitoringSrc) {
    Copy-Item -Recurse -Force (Join-Path $monitoringSrc "*") `
        (Join-Path $TemplatesDir "omes-dgw\monitoring")
}

$deployEnv = Join-Path $DeployTemplate ".env"
$releaseEnv = Join-Path $ReleaseDir ".env"
if (Test-Path -LiteralPath $deployEnv) {
    Copy-Item -LiteralPath $deployEnv -Destination $releaseEnv -Force
    Write-Host "[publish] Included deploy/.env -> release/omes-dgw-deploy/.env"
}
elseif (-not (Test-Path -LiteralPath $releaseEnv)) {
    $envExample = Join-Path $DeployTemplate ".env.example"
    if (Test-Path -LiteralPath $envExample) {
        Copy-Item -LiteralPath $envExample -Destination $releaseEnv -Force
        Write-Host "[publish] No deploy/.env; created .env from .env.example (edit before field deploy)."
    }
}

foreach ($rel in @("init-influx.env", "influx-app-credentials.env")) {
    $p = Join-Path $ReleaseDir $rel
    if (Test-Path $p) {
        Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
        Write-Host "[publish] Removed local-only file: $rel"
    }
}

Get-ChildItem (Join-Path $ReleaseDir "images") -Filter "*.tar" -ErrorAction SilentlyContinue | Remove-Item -Force

Write-Host "[publish] Copy image tars ..."
foreach ($image in Get-CollectDeployImageList) {
    Copy-Item (Join-Path $ImagesSrc $image.Tar) (Join-Path $ReleaseDir "images\") -Force
}

Write-Host ""
Write-Host "[publish] Release package ready: $ReleaseDir"
Write-Host "[publish] Deliver this folder to field engineers (includes images/*.tar, .env, no source code)."
