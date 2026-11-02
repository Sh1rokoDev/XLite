param(
    [Parameter(Mandatory=$true)][string]$Executable,
    [Parameter(Mandatory=$true)][string]$Icon
)
$ErrorActionPreference = 'Stop'
if (-not ('XLite.Resources' -as [type])) {
    Add-Type @'
using System;
using System.Runtime.InteropServices;
namespace XLite {
    public static class Resources {
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
        public static extern IntPtr BeginUpdateResource(string path, bool deleteExisting);
        [DllImport("kernel32.dll", SetLastError=true)]
        public static extern bool UpdateResource(IntPtr handle, IntPtr type, IntPtr name,
            ushort language, byte[] data, uint size);
        [DllImport("kernel32.dll", SetLastError=true)]
        public static extern bool EndUpdateResource(IntPtr handle, bool discard);
    }
}
'@
}
$bytes = [IO.File]::ReadAllBytes($Icon)
$count = [BitConverter]::ToUInt16($bytes, 4)
if ([BitConverter]::ToUInt16($bytes, 2) -ne 1 -or $count -eq 0) { throw 'Invalid ICO file' }
$handle = [XLite.Resources]::BeginUpdateResource($Executable, $false)
if ($handle -eq [IntPtr]::Zero) { throw 'Cannot open executable resources' }
$committed = $false
try {
    $group = New-Object byte[] (6 + 14 * $count)
    [Array]::Copy($bytes, 0, $group, 0, 6)
    for ($i = 0; $i -lt $count; $i++) {
        $entry = 6 + 16 * $i
        $size = [BitConverter]::ToUInt32($bytes, $entry + 8)
        $offset = [BitConverter]::ToUInt32($bytes, $entry + 12)
        $image = New-Object byte[] $size
        [Array]::Copy($bytes, $offset, $image, 0, $size)
        if (-not [XLite.Resources]::UpdateResource($handle, [IntPtr]3, [IntPtr]($i + 1), 0, $image, $size)) {
            throw 'Cannot write executable icon'
        }
        [Array]::Copy($bytes, $entry, $group, (6 + 14 * $i), 12)
        [Array]::Copy([BitConverter]::GetBytes([uint16]($i + 1)), 0, $group, (18 + 14 * $i), 2)
    }
    if (-not [XLite.Resources]::UpdateResource($handle, [IntPtr]14, [IntPtr]1, 0, $group, $group.Length)) {
        throw 'Cannot write executable icon group'
    }
    $committed = $true
} finally {
    if (-not [XLite.Resources]::EndUpdateResource($handle, -not $committed)) {
        throw 'Cannot finish executable resources'
    }
}
