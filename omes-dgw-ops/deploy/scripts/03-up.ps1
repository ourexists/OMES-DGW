$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_env.ps1")

$DeployDir = Split-Path -Parent $PSScriptRoot
$TemplatesDir = Join-Path $DeployDir "config-templates"
Set-Location $DeployDir

$envFile = Ensure-DeployEnvFile $DeployDir
Import-DeployEnv $envFile

$mountPath = $env:MOUNT_PATH
if ([string]::IsNullOrWhiteSpace($mountPath)) {
    Write-Error "[up] Set MOUNT_PATH in .env"
}

$useBundledInflux = Test-DeployEnvBool "USE_BUNDLED_INFLUX" $false

$dirs = @(
    (Join-Path $mountPath "nginx\logs"),
    (Join-Path $mountPath "omes-dgw\config"),
    (Join-Path $mountPath "omes-dgw\monitoring"),
    (Join-Path $mountPath "omes-dgw\logs"),
    (Join-Path $mountPath "omes-dgw\data")
)
if ($useBundledInflux) {
    $dirs += Join-Path $mountPath "influxdb2"
    $dirs += Join-Path $mountPath "influxdb2\etc"
}
New-Item -ItemType Directory -Force -Path $dirs | Out-Null

$nginxSynced = $false
$nginxTpl = Join-Path $TemplatesDir "nginx\default.conf"
$nginxDst = Join-Path $mountPath "nginx\default.conf"
if (Test-Path $nginxTpl) {
    Copy-Item $nginxTpl $nginxDst -Force
    $nginxSynced = $true
}

$configTpl = Join-Path $TemplatesDir "omes-dgw\config\config.properties"
$configDst = Join-Path $mountPath "omes-dgw\config\config.properties"
if (-not (Test-Path $configDst) -and (Test-Path $configTpl)) {
    Copy-Item $configTpl $configDst
}
if (Test-Path $configDst) {
    Sync-DeployConfigProperties $configDst
}

$alertDst = Join-Path $mountPath "omes-dgw\monitoring\prometheus\alerts\omes-dgw-server.yml"
if (-not (Test-Path $alertDst)) {
    $alertTpl = Join-Path $TemplatesDir "omes-dgw\monitoring\prometheus\alerts\omes-dgw-server.yml"
    if (Test-Path $alertTpl) {
        Copy-Item -Recurse -Force (Join-Path $TemplatesDir "omes-dgw\monitoring\*") (Join-Path $mountPath "omes-dgw\monitoring")
    }
}

& (Join-Path $PSScriptRoot "03-load-images.ps1")

$profileArgs = Get-ComposeProfileArgs

$influxMode = if ($useBundledInflux) { "bundled (influxdb service)" } else { "external $($env:INFLUX_URL)" }
Write-Host "[up] Influx: $influxMode"

$middleware = @()
if ($useBundledInflux) { $middleware += "influxdb" }
if ($middleware.Count -gt 0) {
    $mwArgs = @("compose") + $profileArgs + @("up", "-d", "--wait") + $middleware
    Write-Host "[up] Wait for middleware healthy: $($middleware -join ', ') ..."
    Write-Host "[up] docker $($mwArgs -join ' ') ..."
    & docker @mwArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$composeArgs = @("compose") + $profileArgs + @("up", "-d", "--wait", "omes-dgw-server", "omes-dgw-web")
Write-Host "[up] Starting application services ..."
Write-Host "[up] docker $($composeArgs -join ' ') ..."
& docker @composeArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($nginxSynced) {
    $restartArgs = @("compose") + $profileArgs + @("restart", "omes-dgw-web")
    Write-Host "[up] Restart omes-dgw-web to apply nginx config ..."
    & docker @restartArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$appPort = if ($env:APP_PUBLISH_PORT) { $env:APP_PUBLISH_PORT } else { "12010" }
$frontPort = if ($env:FRONT_PUBLISH_PORT) { $env:FRONT_PUBLISH_PORT } else { "12011" }

Write-Host ""
Write-Host "URLs:"
Write-Host "  Front: http://localhost:$frontPort"
Write-Host "  API:   http://localhost:$appPort"
Write-Host "Stop:    run 04-停止.bat in this folder"
