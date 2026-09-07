$ErrorActionPreference = "Stop"

$InfluxDir = $PSScriptRoot

function Get-InitInfluxEnvValue {
    param([hashtable]$Vars, [string]$Name, [string]$Default = "")
    if ($Vars.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Vars[$Name])) {
        return $Vars[$Name].Trim()
    }
    return $Default
}

function Read-InitInfluxEnvFile {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path $Path)) { return $map }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        if ($line -match '^\s*([^=]+)=(.*)$') {
            $map[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $map
}

function Read-InfluxValueInteractive {
    param(
        [string]$Label,
        [string]$Default = ""
    )

    $prompt = if ([string]::IsNullOrWhiteSpace($Default)) { $Label } else { "$Label [$Default]" }
    $value = Read-Host $prompt
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $Default
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Error "[init-influx] $Label cannot be empty."
    }
    return $value.Trim()
}

function Read-InfluxTokenInteractive {
    param([string]$Label = "InfluxDB admin token")

    $secure = Read-Host $Label -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }

    if ([string]::IsNullOrWhiteSpace($token)) {
        Write-Error "[init-influx] Admin token cannot be empty."
    }
    return $token.Trim()
}

function Convert-RetentionToSeconds {
    param([string]$Retention)

    $text = $Retention.Trim().ToLower()
    if ($text -in @("", "0", "infinite", "inf", "none")) {
        return 0
    }
    if ($text -match '^(\d+)d$') {
        return [int]$matches[1] * 86400
    }
    if ($text -match '^(\d+)h$') {
        return [int]$matches[1] * 3600
    }
    if ($text -match '^(\d+)w$') {
        return [int]$matches[1] * 7 * 86400
    }
    if ($text -match '^\d+$') {
        return [int]$text
    }
    Write-Error "[init-influx] Invalid retention '$Retention'. Use 30d, 7d, 720h, or 0 (infinite)."
}

function Normalize-InfluxUrl {
    param([string]$Url)

    $normalized = $Url.Trim().TrimEnd("/")
    if ($normalized -notmatch '^https?://') {
        Write-Error "[init-influx] INFLUX_URL must start with http:// or https://"
    }
    return $normalized
}

function Invoke-InfluxApi {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [object]$Body = $null
    )

    $headers = @{
        Authorization = "Token $Token"
        Accept        = "application/json"
    }
    $params = @{
        Method  = $Method
        Uri     = $Url
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }

    try {
        return Invoke-RestMethod @params
    }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        throw "Influx API $Method $Url failed: $detail"
    }
}

function Test-InfluxHealth {
    param([string]$BaseUrl)

    $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/health" -Headers @{ Accept = "application/json" }
    if ($health.status -ne "pass") {
        throw "InfluxDB health check failed: $($health.message)"
    }
}

function Get-InfluxOrgs {
    param(
        [string]$BaseUrl,
        [string]$Token
    )

    $resp = Invoke-InfluxApi -Method Get -Url "$BaseUrl/api/v2/orgs" -Token $Token
    if ($resp.orgs) { return @($resp.orgs) }
    return @()
}

function Get-OrCreateInfluxOrg {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$OrgName
    )

    $existing = Get-InfluxOrgs -BaseUrl $BaseUrl -Token $Token | Where-Object { $_.name -eq $OrgName } | Select-Object -First 1
    if ($existing) {
        Write-Host "[init-influx] Org already exists: $OrgName"
        return $existing
    }

    Write-Host "[init-influx] Creating org: $OrgName"
    return Invoke-InfluxApi -Method Post -Url "$BaseUrl/api/v2/orgs" -Token $Token -Body @{ name = $OrgName }
}

function Get-InfluxBuckets {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$OrgName
    )

    $encodedOrg = [Uri]::EscapeDataString($OrgName)
    $resp = Invoke-InfluxApi -Method Get -Url "$BaseUrl/api/v2/buckets?org=$encodedOrg" -Token $Token
    if ($resp.buckets) { return @($resp.buckets) }
    return @()
}

function Get-OrCreateInfluxBucket {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [string]$OrgId,
        [string]$OrgName,
        [string]$BucketName,
        [int]$RetentionSeconds
    )

    $existing = Get-InfluxBuckets -BaseUrl $BaseUrl -Token $Token -OrgName $OrgName |
        Where-Object { $_.name -eq $BucketName } |
        Select-Object -First 1
    if ($existing) {
        Write-Host "[init-influx] Bucket already exists: $BucketName"
        return $existing
    }

    $body = @{
        orgID = $OrgId
        name  = $BucketName
    }
    if ($RetentionSeconds -gt 0) {
        $body.retentionRules = @(
            @{
                type                      = "expire"
                everySeconds              = $RetentionSeconds
                shardGroupDurationSeconds = 86400
            }
        )
    }

    Write-Host "[init-influx] Creating bucket: $BucketName (retention=$RetentionSeconds sec)"
    return Invoke-InfluxApi -Method Post -Url "$BaseUrl/api/v2/buckets" -Token $Token -Body $body
}

function New-InfluxAppToken {
    param(
        [string]$BaseUrl,
        [string]$AdminToken,
        [string]$OrgId,
        [string]$BucketName
    )

    $permissions = @(
        @{
            action   = "read"
            resource = @{
                type   = "buckets"
                orgID  = $OrgId
                name   = $BucketName
            }
        },
        @{
            action   = "write"
            resource = @{
                type   = "buckets"
                orgID  = $OrgId
                name   = $BucketName
            }
        }
    )

    $body = @{
        orgID       = $OrgId
        description = "collect-service app token"
        permissions = $permissions
    }

    Write-Host "[init-influx] Creating app token for bucket: $BucketName"
    $auth = Invoke-InfluxApi -Method Post -Url "$BaseUrl/api/v2/authorizations" -Token $AdminToken -Body $body
    if (-not $auth.token) {
        throw "Influx API did not return app token."
    }
    return $auth.token
}

$envFile = Join-Path $InfluxDir "init-influx.env"
$vars = Read-InitInfluxEnvFile $envFile

$defaultUrl = Get-InitInfluxEnvValue $vars "INFLUX_URL" "http://127.0.0.1:8086"
$defaultOrg = Get-InitInfluxEnvValue $vars "INFLUX_ORG" "omes"
$defaultBucket = Get-InitInfluxEnvValue $vars "INFLUX_BUCKET" "collect_metrics"
$defaultRetention = Get-InitInfluxEnvValue $vars "INFLUX_RETENTION" "30d"

Write-Host "[init-influx] Target InfluxDB 2.x (org + bucket + app token)"
Write-Host "[init-influx] Admin token must have permission to manage orgs/buckets/authorizations."

$influxUrl = Normalize-InfluxUrl (Read-InfluxValueInteractive -Label "InfluxDB URL" -Default $defaultUrl)
$adminToken = Read-InfluxTokenInteractive
$orgName = Read-InfluxValueInteractive -Label "InfluxDB org" -Default $defaultOrg
$bucketName = Read-InfluxValueInteractive -Label "InfluxDB bucket" -Default $defaultBucket
$retentionText = Read-InfluxValueInteractive -Label "Bucket retention (e.g. 30d, 0=infinite)" -Default $defaultRetention
$retentionSeconds = Convert-RetentionToSeconds $retentionText

Write-Host "[init-influx] Checking health: $influxUrl"
Test-InfluxHealth -BaseUrl $influxUrl

$org = Get-OrCreateInfluxOrg -BaseUrl $influxUrl -Token $adminToken -OrgName $orgName
$bucket = Get-OrCreateInfluxBucket `
    -BaseUrl $influxUrl `
    -Token $adminToken `
    -OrgId $org.id `
    -OrgName $orgName `
    -BucketName $bucketName `
    -RetentionSeconds $retentionSeconds

$appToken = New-InfluxAppToken `
    -BaseUrl $influxUrl `
    -AdminToken $adminToken `
    -OrgId $org.id `
    -BucketName $bucketName

$credFile = Join-Path $InfluxDir "influx-app-credentials.env"
@(
    "# Paste into omes-dgw-deploy/.env"
    "INFLUX_URL=$influxUrl"
    "INFLUX_ADMIN_TOKEN=$appToken"
    "INFLUX_ORG=$orgName"
    "INFLUX_BUCKET=$bucketName"
    "INFLUX_ENABLED=true"
) | Set-Content -LiteralPath $credFile -Encoding UTF8

Write-Host ""
Write-Host "[init-influx] Done."
Write-Host "[init-influx] Org: $orgName (id=$($org.id))"
Write-Host "[init-influx] Bucket: $bucketName (id=$($bucket.id))"
Write-Host "[init-influx] Measurements used by app: collect_protocol_metrics, device_realtime, device_point"
Write-Host "[init-influx] Credentials saved: $credFile"
