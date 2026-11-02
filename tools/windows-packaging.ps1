$ErrorActionPreference = 'Stop'

function Get-XLiteVersion([string]$RequestedVersion) {
    if (-not $RequestedVersion) {
        $properties = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../gradle.properties')
        $line = $properties | Where-Object { $_ -match '^xliteVersion=' } | Select-Object -First 1
        if (-not $line) { throw 'xliteVersion is missing from gradle.properties' }
        $RequestedVersion = ($line -split '=', 2)[1].Trim()
    }
    if ($RequestedVersion -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
        throw 'Version must be three numeric fields, for example 0.1.1.'
    }
    $fields = $RequestedVersion.Split('.')
    if ([long]$fields[0] -gt 255 -or [long]$fields[1] -gt 255 -or [long]$fields[2] -gt 65535) {
        throw 'Windows MSI version fields must be at most 255.255.65535.'
    }
    return $RequestedVersion
}

function Invoke-XLiteGradle([string[]]$BuildArguments) {
    $root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    Push-Location $root
    try {
        & (Join-Path $root 'gradlew.bat') @BuildArguments '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}

function Get-XLiteImageVersion([string]$AppImage) {
    $statePath = Join-Path $AppImage 'app/.jpackage.xml'
    if (-not (Test-Path -LiteralPath $statePath)) { throw "App image metadata is missing: $statePath" }
    $state = [xml](Get-Content -LiteralPath $statePath -Raw)
    return [string]$state.'jpackage-state'.'app-version'
}

function Assert-XLiteLocalPaths([string]$AppImage) {
    $cfg = [IO.File]::ReadAllText((Join-Path $AppImage 'app/XLite.cfg'))
    foreach ($option in @('-Dxlite.home=$APPDIR/..', '-Djava.io.tmpdir=$APPDIR/../data/tmp', '-Djna.tmpdir=$APPDIR/../data/tmp')) {
        if (-not $cfg.Contains($option)) { throw "App image lacks $option. Rebuild without -SkipBuild." }
    }
}
