# Load deploy/.env into the current process (used by 03-up.ps1)

function Ensure-DeployEnvFile {
    param([string]$DeployDir)

    $envFile = Join-Path $DeployDir ".env"
    if (Test-Path -LiteralPath $envFile) {
        return $envFile
    }

    $example = Join-Path $DeployDir ".env.example"
    if (-not (Test-Path -LiteralPath $example)) {
        Write-Error "[deploy] Missing .env.example in $DeployDir"
    }

    Copy-Item -LiteralPath $example -Destination $envFile
    Write-Host "[deploy] Created .env from .env.example. Edit MOUNT_PATH, SQLITE_PATH, Influx, etc., then run 03-启动.bat."
    exit 1
}

function Import-DeployEnv {
    param([string]$EnvFile)
    if (-not (Test-Path $EnvFile)) { return }
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        if ($line -match '^\s*([^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim().Trim('"').Trim("'")
            Set-Item -Path "env:$name" -Value $value
        }
    }
}

function Test-DeployEnvBool {
    param([string]$Name, [bool]$Default = $false)
    $v = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($v)) { return $Default }
    return $v -eq "1" -or $v -eq "true" -or $v -eq "yes" -or $v -eq "on"
}

function Get-ComposeProfileArgs {
    $profileArgs = @()
    if (Test-DeployEnvBool "USE_BUNDLED_INFLUX" $false) {
        $profileArgs += @("--profile", "bundled-influx")
    }
    return $profileArgs
}

function Get-DeployConfigEnvValue {
    param([string]$Key)

    if ($Key -eq "INFLUX_TOKEN") {
        $adminToken = [Environment]::GetEnvironmentVariable("INFLUX_ADMIN_TOKEN")
        if (-not [string]::IsNullOrWhiteSpace($adminToken)) {
            return $adminToken.Trim()
        }
    }
    $value = [Environment]::GetEnvironmentVariable($Key)
    if ([string]::IsNullOrWhiteSpace($value)) { return $null }
    return $value.Trim()
}

function Sync-DeployConfigProperties {
    param([string]$ConfigPath)

    if (-not (Test-Path -LiteralPath $ConfigPath)) { return }

    $syncKeys = @(
        "SQLITE_PATH", "FLYWAY_ENABLED",
        "COLLECT_MONITOR_TYPE", "INFLUX_URL", "INFLUX_TOKEN", "INFLUX_ORG", "INFLUX_BUCKET"
    )

    $lines = Get-Content -LiteralPath $ConfigPath -Encoding UTF8
    $out = foreach ($line in $lines) {
        if ($line -match '^\s*([^#=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = Get-DeployConfigEnvValue $key
            if ($syncKeys -contains $key -and $null -ne $value) {
                "$key=$value"
            }
            else {
                $line
            }
        }
        else {
            $line
        }
    }
    Set-Content -LiteralPath $ConfigPath -Value $out -Encoding UTF8
    Write-Host "[up] Synced portal config from .env: $ConfigPath"
}
