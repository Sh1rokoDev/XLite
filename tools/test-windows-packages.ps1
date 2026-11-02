param(
    [string]$PortableZip,
    [Parameter(Mandatory=$true)][string]$Installer,
    [string]$PreviousInstaller
)
$ErrorActionPreference = 'Stop'

function Read-MsiRows([string]$Path, [string]$Sql, [string[]]$Columns) {
    $engine = New-Object -ComObject WindowsInstaller.Installer
    $db = $engine.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $engine, @([IO.Path]::GetFullPath($Path), 0))
    $view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db, @($Sql))
    try {
        $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
        while ($record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)) {
            $row = [ordered]@{}
            for ($i = 0; $i -lt $Columns.Count; $i++) {
                $row[$Columns[$i]] = $record.GetType().InvokeMember('StringData', 'GetProperty', $null, $record, @($i + 1))
            }
            [pscustomobject]$row
        }
    } finally {
        $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
        foreach ($obj in @($view, $db, $engine)) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($obj) }
    }
}

function Read-MsiIdentity([string]$Path) {
    $result = @{}
    Read-MsiRows $Path 'SELECT `Property`, `Value` FROM `Property`' @('Name', 'Value') | ForEach-Object { $result[$_.Name] = $_.Value }
    return $result
}

$identity = Read-MsiIdentity $Installer
$family = '{6F0F2C9E-6B6A-4F6C-9D2C-1F4A5B7C8D90}'
if ($identity.UpgradeCode -ne $family -or $identity.ALLUSERS -ne '1') { throw 'MSI product family or installation scope changed.' }
$upgrades = @(Read-MsiRows $Installer 'SELECT `UpgradeCode`, `VersionMin`, `VersionMax`, `Attributes`, `ActionProperty` FROM `Upgrade`' @('Family', 'Min', 'Max', 'Attributes', 'Action'))
if (-not ($upgrades | Where-Object { $_.Family -eq $family -and $_.Max -eq $identity.ProductVersion -and ([int]$_.Attributes -band 2) -eq 0 })) {
    throw 'MSI does not replace older releases of the same product family.'
}
$sequence = @(Read-MsiRows $Installer 'SELECT `Action`, `Sequence` FROM `InstallExecuteSequence`' @('Action', 'Sequence'))
if (-not ($sequence | Where-Object { $_.Action -eq 'RemoveExistingProducts' })) { throw 'MSI does not schedule old-product removal.' }
$permissions = @(Read-MsiRows $Installer 'SELECT `LockObject`, `SDDLText` FROM `MsiLockPermissionsEx`' @('Directory', 'Sddl'))
foreach ($directory in @('XLiteDataDir', 'XLiteDownloadsDir')) {
    if (-not ($permissions | Where-Object { $_.Directory -eq $directory -and $_.Sddl.Contains('(A;OICI;0x1301bf;;;BU)') })) {
        throw "Missing writable data-directory ACL: $directory"
    }
}
$locations = @(Read-MsiRows $Installer 'SELECT `Root`, `Key`, `Name` FROM `RegLocator`' @('Root', 'Key', 'Name'))
if (-not ($locations | Where-Object { $_.Root -eq '2' -and $_.Key -eq 'Software\XLite' -and $_.Name -eq 'InstallDir' })) {
    throw 'MSI does not reuse the saved installation path.'
}
$components = @(Read-MsiRows $Installer 'SELECT `Component`, `Attributes` FROM `Component`' @('Name', 'Attributes'))
foreach ($name in @('XLiteDataFolder', 'XLiteTempFolder', 'XLiteDownloadsFolder')) {
    if (-not ($components | Where-Object { $_.Name -eq $name -and ([int]$_.Attributes -band 16) -ne 0 })) {
        throw "MSI would remove persistent user directories: $name"
    }
}
if ($PreviousInstaller) {
    $previous = Read-MsiIdentity $PreviousInstaller
    if ($previous.UpgradeCode -ne $identity.UpgradeCode -or $previous.ProductCode -eq $identity.ProductCode -or
        [version]$previous.ProductVersion -ge [version]$identity.ProductVersion) {
        throw 'Upgrade must keep UpgradeCode, change ProductCode, and increase ProductVersion.'
    }
    Write-Host "Upgrade metadata verified: $($previous.ProductVersion) -> $($identity.ProductVersion)"
}
Write-Host "MSI verified: $($identity.ProductVersion), per-machine, fixed upgrade family, local writable data, saved installation path."

if ($PortableZip) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $zip = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($PortableZip))
    try {
        foreach ($name in @('XLite/XLite.exe', 'XLite/runtime/bin/server/jvm.dll', 'XLite/app/native/dk.dll', 'XLite/app/native/jni_dk.dll')) {
            if (-not $zip.GetEntry($name)) { throw "Portable file missing: $name" }
        }
        $reader = [IO.StreamReader]::new($zip.GetEntry('XLite/app/XLite.cfg').Open())
        try { $cfg = $reader.ReadToEnd() } finally { $reader.Dispose() }
        foreach ($option in @('-Dxlite.home=$APPDIR/..', '-Djava.io.tmpdir=$APPDIR/../data/tmp',
            '-Djna.tmpdir=$APPDIR/../data/tmp', '-Dcom.xunlei.open.dlsdk.native.dir=$APPDIR/native')) {
            if (-not $cfg.Contains($option)) { throw "Portable launch option missing: $option" }
        }
        if ($zip.Entries | Where-Object { $_.FullName -match '(secret\.bin|settings\.json|tasks\.json|payload\.zip)$' }) {
            throw 'Portable package contains user data or an obsolete extraction payload.'
        }
    } finally { $zip.Dispose() }
    Write-Host 'Portable ZIP verified: ordinary app/runtime files, in-folder data/temp paths, no user data.'
}
