$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_env.ps1")

$DeployDir = Split-Path -Parent $PSScriptRoot
Set-Location $DeployDir

$envFile = Join-Path $DeployDir ".env"
if (Test-Path -LiteralPath $envFile) {
    Import-DeployEnv $envFile
}

$composeArgs = @("compose") + (Get-ComposeProfileArgs) + @("down")
Write-Host "[down] docker $($composeArgs -join ' ') ..."
& docker @composeArgs @args
