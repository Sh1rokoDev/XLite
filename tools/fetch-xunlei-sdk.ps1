param(
    [string]$Version = "1.0.2"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "third_party/xunlei/dist"
$m2 = Join-Path $root "third_party/xunlei/m2"

$desktopUrl = "https://down.sandai.net/opensdk/download/xunlei-dlsdk-java-desktop.zip"
$androidUrl = "https://down.sandai.net/opensdk/download/xunlei-dlsdk-java-android.aar"
$desktopZipHash = "566b8c17043ff1fffe6f0d863edcd7f4359ccbfb358de350a90771b44d8b4d5a"
$androidAarHash = "22a1728c59e359c3e77732c0c516b406151916a72125c5cd8686a8e907044e6f"

function Assert-Sha256([string]$Path, [string]$Expected) {
    $actual = (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "SHA-256 mismatch for $Path`n  expected: $Expected`n  actual:   $actual"
    }
}

function Write-Pom([string]$ArtifactId, [string]$Packaging) {
    $dir = Join-Path $m2 "com/xunlei/open/$ArtifactId/$Version"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $pom = @"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.xunlei.open</groupId>
  <artifactId>$ArtifactId</artifactId>
  <version>$Version</version>
  <packaging>$Packaging</packaging>
</project>
"@
    Set-Content -Path (Join-Path $dir "$ArtifactId-$Version.pom") -Value $pom -Encoding UTF8
}

New-Item -ItemType Directory -Force -Path $dist, $m2 | Out-Null

$desktopZip = Join-Path $dist "xunlei-dlsdk-java-desktop-$Version.zip"
$androidAar = Join-Path $dist "xunlei-dlsdk-java-android-$Version.aar"

if (-not (Test-Path $desktopZip)) {
    Write-Host "Downloading desktop SDK..."
    Invoke-WebRequest -Uri $desktopUrl -OutFile $desktopZip -UseBasicParsing
}
Assert-Sha256 $desktopZip $desktopZipHash

if (-not (Test-Path $androidAar)) {
    Write-Host "Downloading Android SDK..."
    Invoke-WebRequest -Uri $androidUrl -OutFile $androidAar -UseBasicParsing
}
Assert-Sha256 $androidAar $androidAarHash

$temp = Join-Path $dist "extract-$Version"
if (Test-Path $temp) { Remove-Item -Recurse -Force $temp }
Expand-Archive -Path $desktopZip -DestinationPath $temp

$libs = Join-Path $temp "xunlei-dlsdk-java-desktop/libs"
$artifacts = @(
    @{ Source = "xl-dl-desktop-$Version.jar"; Target = "dlsdk-desktop"; Packaging = "jar" },
    @{ Source = "xl-dl-native-windows-x64-$Version.jar"; Target = "dlsdk-native-windows-x64"; Packaging = "jar" },
    @{ Source = "xl-dl-native-linux-x64-$Version.jar"; Target = "dlsdk-native-linux-x64"; Packaging = "jar" },
    @{ Source = $androidAar; Target = "dlsdk-android"; Packaging = "aar"; FullSource = $true }
)

foreach ($item in $artifacts) {
    $dir = Join-Path $m2 "com/xunlei/open/$($item.Target)/$Version"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $source = if ($item.FullSource) { $item.Source } else { Join-Path $libs $item.Source }
    $extension = $item.Packaging
    Copy-Item -Force $source (Join-Path $dir "$($item.Target)-$Version.$extension")
    Write-Pom -ArtifactId $item.Target -Packaging $item.Packaging
}

Remove-Item -Recurse -Force $temp

$checks = @{
    "dlsdk-desktop" = "9f13d08ed7edbbb256ca1513981992b4bde475d2fda109c74e0f658d8ac15615"
    "dlsdk-native-windows-x64" = "edbbc1deafc572e78fbe06e7e6ddab200fe9b5d81067c587cdabee7467a19a8e"
    "dlsdk-native-linux-x64" = "a11e27d77cbd5513c82bb60ee9a67856a51096c181ca19b251c8aae1132e15af"
    "dlsdk-android" = $androidAarHash
}
foreach ($name in $checks.Keys) {
    $ext = if ($name -eq "dlsdk-android") { "aar" } else { "jar" }
    Assert-Sha256 (Join-Path $m2 "com/xunlei/open/$name/$Version/$name-$Version.$ext") $checks[$name]
}

Write-Host "Xunlei SDK $Version is ready in $m2"
