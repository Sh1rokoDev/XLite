param(
    [switch]$SkipBuild,
    [string]$Version
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-packaging.ps1')
$Version = Get-XLiteVersion $Version
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$appImage = Join-Path $root 'desktopApp/build/compose/binaries/main/app/XLite'
$outDir = Join-Path $root 'dist'
$output = Join-Path $outDir "XLite-$Version-windows-x64-portable.zip"

if (-not $SkipBuild) {
    Invoke-XLiteGradle @(':desktopApp:createDistributable', "-PxliteVersion=$Version")
}
if (-not (Test-Path -LiteralPath (Join-Path $appImage 'XLite.exe'))) {
    throw 'Windows app image is missing. Run createDistributable first or omit -SkipBuild.'
}
if ((Get-XLiteImageVersion $appImage) -ne $Version) {
    throw 'App image version differs from the requested version. Rebuild without -SkipBuild.'
}
Assert-XLiteLocalPaths $appImage

# A fresh directory prevents existing portable user data from entering the ZIP.
$workDir = Join-Path $root ("desktopApp/build/portable/" + [guid]::NewGuid().ToString('N'))
$stage = Join-Path $workDir 'XLite'
New-Item -ItemType Directory -Force -Path $workDir, $outDir | Out-Null
Copy-Item -LiteralPath $appImage -Destination $stage -Recurse
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'data/tmp'), (Join-Path $stage 'downloads') | Out-Null
Set-Content -LiteralPath (Join-Path $stage 'portable.marker') -Value 'XLite portable: keep this folder together; user data is stored in data/.' -Encoding utf8

# Ship the SDK DLLs as ordinary files instead of extracting them at startup.
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$nativeJar = @(Get-ChildItem -LiteralPath (Join-Path $stage 'app') -Filter 'dlsdk-native-windows-x64-*.jar')
if ($nativeJar.Count -ne 1) { throw 'Expected exactly one Windows x64 SDK native JAR.' }
$nativeDir = Join-Path $stage 'app/native'
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
$nativeZip = [IO.Compression.ZipFile]::OpenRead($nativeJar[0].FullName)
try {
    foreach ($name in @('dk.dll', 'jni_dk.dll')) {
        $entry = @($nativeZip.Entries | Where-Object { $_.Name -eq $name })
        if ($entry.Count -ne 1) { throw "Expected exactly one $name in the SDK native JAR." }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry[0], (Join-Path $nativeDir $name), $true)
    }
} finally { $nativeZip.Dispose() }

# jpackage expands $APPDIR at launch, so moving the whole folder remains safe.
$cfgPath = Join-Path $stage 'app/XLite.cfg'
$cfg = [IO.File]::ReadAllText($cfgPath)
$cfg += @'

java-options=-Dcom.xunlei.open.dlsdk.native.dir=$APPDIR/native
'@
[IO.File]::WriteAllText($cfgPath, $cfg, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $stage 'README-portable.txt'), @"
XLite $Version - Windows x64 portable

Extract the entire ZIP to a writable folder, then run XLite.exe.
Keep app/ and runtime/ beside XLite.exe. No Java installation is required.
Configuration, tasks, logs and temporary files stay in data/.
The default download folder is downloads/; you may choose another destination.
To update: exit XLite completely, replace program files with a newer portable
release, and KEEP your data/ and downloads/ folders. Do not combine this folder
with a system installation. DPAPI-protected API keys belong to the Windows user
and machine that saved them; re-enter the key after moving to another machine.
"@, [Text.UTF8Encoding]::new($false))
$archive = Join-Path $workDir 'portable.zip'
$zip = [IO.Compression.ZipFile]::Open($archive, [IO.Compression.ZipArchiveMode]::Create)
try {
    # .NET Framework's CreateFromDirectory uses backslashes on some PowerShell 5
    # hosts. Emit standard ZIP separators explicitly on every supported host.
    foreach ($item in Get-ChildItem -LiteralPath $stage -Recurse -Force) {
        $relative = $item.FullName.Substring($stage.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
        $entryName = 'XLite/' + $relative
        if ($item.PSIsContainer) {
            [void]$zip.CreateEntry($entryName + '/')
        } else {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $item.FullName, $entryName, [IO.Compression.CompressionLevel]::Optimal)
        }
    }
} finally { $zip.Dispose() }
Move-Item -LiteralPath $archive -Destination $output -Force
Write-Host "Portable ZIP: $output"
Write-Host "Unpacked portable folder: $stage"
