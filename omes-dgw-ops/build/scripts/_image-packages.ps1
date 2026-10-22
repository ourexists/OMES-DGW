$ErrorActionPreference = "Stop"

$BuildScriptsDir = $PSScriptRoot
$DockerRoot = Split-Path (Split-Path $BuildScriptsDir -Parent) -Parent
. (Join-Path $DockerRoot "deploy\scripts\_image-versions.ps1")

function Get-BuildLayout {
    $buildDir = Split-Path -Parent $PSScriptRoot
    @{
        BuildDir    = $buildDir
        PackagesDir = Join-Path $buildDir "packages"
        ImagesDir   = Join-Path $buildDir "images"
    }
}

function Copy-ImageTar {
    param(
        [Parameter(Mandatory = $true)][string]$TarName,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $layout = Get-BuildLayout
    $src = Join-Path $layout.PackagesDir $TarName
    $dst = Join-Path $layout.ImagesDir $TarName

    if (-not (Test-Path $src)) {
        Write-Error "[copy] Missing $Label package: $src`nPlace exported tar in build\packages\ (see packages\README.md)."
    }

    New-Item -ItemType Directory -Force -Path $layout.ImagesDir | Out-Null
    Write-Host "[copy] $Label : packages\$TarName -> images\$TarName"
    Copy-Item -Path $src -Destination $dst -Force
}

function Export-BackendImage {
    $artifacts = Get-CollectImageArtifacts
    $layout = Get-BuildLayout
    $tarSrc = Join-Path $layout.PackagesDir $artifacts.BackendTar
    $jarSrc = Join-Path $layout.PackagesDir $artifacts.BackendJar
    $tarDst = Join-Path $layout.ImagesDir $artifacts.BackendTar

    if (Test-Path $tarSrc) {
        Copy-ImageTar -TarName $artifacts.BackendTar -Label "backend"
        return
    }

    $jarFile = $null
    if (Test-Path $jarSrc) {
        $jarFile = $artifacts.BackendJar
    }
    else {
        $bareName = "omes-dgw-portal-center-$($artifacts.BackendVersion)"
        $barePath = Join-Path $layout.PackagesDir "$bareName.jar"
        if (Test-Path $barePath -PathType Leaf) {
            Copy-Item -Path $barePath -Destination $jarSrc -Force
            $jarFile = $artifacts.BackendJar
        }
        else {
            $candidates = Get-ChildItem -Path $layout.PackagesDir -Filter "omes-dgw-portal-*.jar" -File -ErrorAction SilentlyContinue
            if ($candidates.Count -eq 1) {
                $jarFile = $candidates[0].Name
            }
        }
    }

    if (-not $jarFile) {
        Write-Error @"
[build-backend] Missing backend package in $($layout.PackagesDir)
  - Put jar: $($artifacts.BackendJar)  (mvn package), or
  - Put tar: $($artifacts.BackendTar)  (docker save)
See packages\README.md
"@
    }

    New-Item -ItemType Directory -Force -Path $layout.ImagesDir | Out-Null
    $dockerfile = Join-Path $layout.BuildDir "Dockerfile.backend"

    Write-Host "[build-backend] docker build $($artifacts.BackendTag) from packages\$jarFile ..."
    docker build -t $artifacts.BackendTag -f $dockerfile $layout.PackagesDir --build-arg "JAR_FILE=$jarFile"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "[build-backend] docker save -> $tarDst"
    docker save -o $tarDst $artifacts.BackendTag
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Export-MiddlewareImages {
    $artifacts = Get-CollectImageArtifacts
    $layout = Get-BuildLayout
    New-Item -ItemType Directory -Force -Path $layout.ImagesDir | Out-Null

    foreach ($item in @(
            @{ Tag = $artifacts.InfluxTag; Tar = $artifacts.InfluxTar; Label = "influxdb" }
        )) {
        $tarDst = Join-Path $layout.ImagesDir $item.Tar
        if (Test-Path $tarDst) {
            Write-Host "[build-middleware] $($item.Tar) already exists, skip."
            continue
        }

        Write-Host "[build-middleware] docker pull $($item.Tag) ..."
        docker pull $item.Tag
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

        Write-Host "[build-middleware] docker save -> $tarDst"
        docker save -o $tarDst $item.Tag
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
