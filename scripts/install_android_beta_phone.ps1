[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [Parameter(Mandatory = $true)]
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'

if ($Serial -match '^emulator-') {
    throw 'P20_PHONE_INSTALL_BLOCKED_EMULATOR_TARGET'
}
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw 'P20_PHONE_INSTALL_BLOCKED_ADB_UNAVAILABLE'
}
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    throw 'P20_PHONE_INSTALL_BLOCKED_APK_UNAVAILABLE'
}

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$deviceLines = @(& $adb devices)
if ($LASTEXITCODE -ne 0) { throw 'P20_PHONE_INSTALL_BLOCKED_DEVICE_QUERY' }
$matching = @($deviceLines | Where-Object {
    $_ -match ('^' + [regex]::Escape($Serial) + '\s+(?<state>device|offline|unauthorized)$')
})
if ($matching.Count -ne 1) { throw 'P20_PHONE_INSTALL_BLOCKED_DEVICE_NOT_UNIQUE' }
$state = [regex]::Match($matching[0], '\s(?<state>device|offline|unauthorized)$').Groups['state'].Value
if ($state -ne 'device') { throw 'P20_PHONE_INSTALL_BLOCKED_DEVICE_NOT_READY' }

$installOutput = @(& $adb -s $Serial install -r -t $resolvedApk)
if ($LASTEXITCODE -ne 0 -or -not ([string]::Join("`n", $installOutput) -match '(?m)^Success$')) {
    throw 'P20_PHONE_INSTALL_BLOCKED_SAME_SIGNATURE_OR_DEVICE_POLICY'
}

Write-Output 'P20_PHONE_INSTALL_PASS'
