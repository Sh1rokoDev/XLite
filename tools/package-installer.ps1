param(
    [switch]$SkipBuild,
    [string]$Version,
    [string]$WixDir
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-packaging.ps1')
$Version = Get-XLiteVersion $Version
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $SkipBuild) {
    Invoke-XLiteGradle @(':desktopApp:createDistributable', ':unzipWix',
        '-Pcompose.desktop.application.downloadWix=true', "-PxliteVersion=$Version")
}
$appImage = Join-Path $root 'desktopApp/build/compose/binaries/main/app/XLite'
if ((Get-XLiteImageVersion $appImage) -ne $Version) { throw 'App image version differs. Rebuild without -SkipBuild.' }
Assert-XLiteLocalPaths $appImage
if (-not $WixDir) { $WixDir = Join-Path $root 'build/wix311' }
foreach ($tool in @('candle.exe', 'light.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $WixDir $tool))) { throw "WiX tool missing: $tool. Supply -WixDir or rebuild without -SkipBuild." }
}
$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/jpackage.exe' } else { (Get-Command jpackage.exe -ErrorAction Stop).Source }
$workDir = Join-Path $root ('desktopApp/build/installer/' + [guid]::NewGuid().ToString('N'))
$stage = Join-Path $workDir 'XLite'
$msiDir = Join-Path $workDir 'output'
$resources = Join-Path $workDir 'resources'
New-Item -ItemType Directory -Force -Path $stage, $msiDir, $resources | Out-Null
# Extend this build JDK's own MSI template, retaining its standard upgrade logic.
# jpackage only copies recognized resource names, so inline our Product children.
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$jdkRoot = Split-Path -Parent (Split-Path -Parent $jpackage)
$jmodBytes = [IO.File]::ReadAllBytes((Join-Path $jdkRoot 'jmods/jdk.jpackage.jmod'))
# JMOD has a four-byte JM header before its ZIP payload.
$jmodStream = [IO.MemoryStream]::new()
$jmodStream.Write($jmodBytes, 4, $jmodBytes.Length - 4)
$jmodStream.Position = 0
$jmod = [IO.Compression.ZipArchive]::new($jmodStream, [IO.Compression.ZipArchiveMode]::Read)
try {
    $reader = [IO.StreamReader]::new($jmod.GetEntry('classes/jdk/jpackage/internal/resources/main.wxs').Open())
    try { $template = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $jmod.Dispose(); $jmodStream.Dispose() }
$product = [xml](Get-Content -LiteralPath (Join-Path $PSScriptRoot 'windows-installer/product.wxi') -Raw)
$feature = $product.DocumentElement.SelectSingleNode("*[local-name()='FeatureRef']")
$componentReferences = $feature.InnerXml
[void]$product.DocumentElement.RemoveChild($feature)
$featureMarker = '<ComponentGroupRef Id="Files"/>'
if (-not $template.Contains($featureMarker)) { throw 'Unsupported JDK MSI template: default Feature insertion point is missing.' }
$template = $template.Replace($featureMarker, ($featureMarker + $componentReferences))
$marker = '<Media Id="1"'
if (-not $template.Contains($marker)) { throw 'Unsupported JDK MSI template: Media insertion point is missing.' }
$template = $template.Replace($marker, ($product.DocumentElement.InnerXml + "`n    " + $marker))
[IO.File]::WriteAllText((Join-Path $resources 'main.wxs'), $template, [Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows-installer/overrides.wxi') -Destination $resources
# User directories are created and permissioned by MSI, never bundled from a running copy.
Get-ChildItem -LiteralPath $appImage -Force | Where-Object { $_.Name -notin @('data', 'downloads', 'portable.marker') } |
    Copy-Item -Destination $stage -Recurse
$jpackageArguments = @('--type', 'msi', '--app-image', $stage, '--name', 'XLite',
    '--app-version', $Version, '--vendor', 'XLite', '--description', 'XLite - Xunlei accelerated downloader',
    '--dest', $msiDir, '--temp', (Join-Path $workDir 'msi-work'),
    '--resource-dir', $resources, '--verbose',
    '--icon', (Join-Path $root 'desktopApp/src/main/resources/xlite.ico'),
    '--win-upgrade-uuid', '6f0f2c9e-6b6a-4f6c-9d2c-1f4a5b7c8d90',
    '--install-dir', 'XLite', '--win-dir-chooser', '--win-shortcut', '--win-menu', '--win-menu-group', 'XLite')
$savedBuildPath = $env:PATH
try {
    $env:PATH = "$WixDir;$savedBuildPath"
    # Windows PowerShell 5 treats native stderr (including JAVA_TOOL_OPTIONS)
    # as ErrorRecords. Use the exit code rather than terminating on log output.
    $savedBuildErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $jpackage @jpackageArguments *> (Join-Path $workDir 'jpackage.log')
        $jpackageExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedBuildErrorPreference }
    if ($jpackageExitCode -ne 0) {
        Get-Content -LiteralPath (Join-Path $workDir 'jpackage.log') -Tail 60 | Write-Host
        throw "jpackage failed with exit code $jpackageExitCode"
    }
} finally { $env:PATH = $savedBuildPath }
$source = Join-Path $msiDir "XLite-$Version.msi"
if (-not (Test-Path -LiteralPath $source)) { throw "MSI is missing: $source" }
& (Join-Path $PSScriptRoot 'test-windows-packages.ps1') -Installer $source
$outDir = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$output = Join-Path $outDir "XLite-$Version-windows-x64-installer.msi"
Copy-Item -LiteralPath $source -Destination $output -Force
Write-Host "System installer: $output"
Write-Host 'New releases must increase xliteVersion and retain the fixed upgradeUuid.'
