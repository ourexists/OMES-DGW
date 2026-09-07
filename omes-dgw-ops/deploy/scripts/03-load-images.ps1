$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_image-versions.ps1")

$DeployDir = Split-Path -Parent $PSScriptRoot
$imagesDir = Join-Path $DeployDir "images"

function Test-DockerImageLoaded {
    param([string]$Tag)

    # docker 在镜像不存在时写 stderr，须避免 $ErrorActionPreference=Stop 将其当作脚本错误
    $prev = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $null = docker image inspect $Tag 2>&1
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

foreach ($image in Get-CollectDeployImageList) {
    $tarPath = Join-Path $imagesDir $image.Tar
    if (-not (Test-Path $tarPath)) {
        Write-Error "[load-images] Missing $tarPath"
    }

    if (Test-DockerImageLoaded $image.Tag) {
        Write-Host "[load-images] $($image.Tag) already loaded, skip."
        continue
    }

    Write-Host "[load-images] Loading $($image.Tag) from $($image.Tar) ..."
    docker load -i $tarPath
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "[load-images] Done."
