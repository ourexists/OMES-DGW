# Image names for deploy package (must match docker-compose.yml and images/*.tar)

$script:CollectBackendVersion = "1.0-SNAPSHOT"
$script:CollectFrontVersion = "1.0.0"
$script:CollectInfluxTag = "influxdb:2.7-alpine"

function Get-CollectImageArtifacts {
    $bv = $script:CollectBackendVersion
    $fv = $script:CollectFrontVersion
    [pscustomobject]@{
        BackendVersion = $bv
        FrontVersion   = $fv
        BackendTag     = "omes-dgw-server:$bv"
        FrontTag       = "omes-dgw-web:$fv"
        # 实施包默认打中控门户；边缘盒子改用 omes-dgw-portal-box-*.jar 并改 JAR_FILE
        BackendJar     = "omes-dgw-portal-center-$bv.jar"
        BackendTar     = "omes-dgw-server-$bv.tar"
        FrontTar       = "omes-dgw-web-$fv.tar"
        InfluxTag      = $script:CollectInfluxTag
        InfluxTar      = "influxdb-2.7-alpine.tar"
    }
}

function Get-CollectDeployImageList {
    $a = Get-CollectImageArtifacts
    @(
        [pscustomobject]@{ Tag = $a.BackendTag; Tar = $a.BackendTar; Label = "backend" }
        [pscustomobject]@{ Tag = $a.FrontTag; Tar = $a.FrontTar; Label = "frontend" }
        [pscustomobject]@{ Tag = $a.InfluxTag; Tar = $a.InfluxTar; Label = "influxdb" }
    )
}
