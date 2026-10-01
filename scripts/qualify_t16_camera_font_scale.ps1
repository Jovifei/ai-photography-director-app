[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('emulator-5580')][string]$Serial,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
if ($evidenceRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $evidenceRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY'
}
if (!(Test-Path -LiteralPath $evidenceRoot -PathType Container)) { throw 'EVIDENCE_DIRECTORY_MUST_EXIST' }
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adbPath)) { throw 'ADB_NOT_FOUND' }
$runRoot = Join-Path $evidenceRoot ('t16-font-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runRoot | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $result = @(& $adbPath -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB_FAILED: $($Arguments -join ' ')" }
    return ($result -join "`n").Trim()
}
function Assert-Identity {
    if ((Invoke-Adb @('get-state')) -ne 'device' -or
        (Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1' -or
        (Invoke-Adb @('shell','getprop','ro.build.version.sdk')) -ne '35' -or
        (Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1' -or
        (Invoke-Adb @('shell','getprop','ro.boot.qemu.avd_name')) -ne 'T3_API35_20260927') {
        throw 'EXACT_DEDICATED_API35_EMULATOR_REQUIRED'
    }
}
Assert-Identity
$original = Invoke-Adb @('shell','settings','get','system','font_scale')
if ($original -ne 'null' -and $original -notmatch '^\d+(\.\d+)?$') { throw 'INVALID_ORIGINAL_FONT_SCALE' }
$summary = [ordered]@{ original_font_scale = $original; points = @(); restoration = 'NOT_RUN'; result = 'BLOCKED'; evidence_directory = $runRoot }
$mutated = $false
$failure = $null
try {
    foreach ($scale in @('1.0','2.0')) {
        Assert-Identity
        $mutated = $true
        Invoke-Adb @('shell','settings','put','system','font_scale',$scale) | Out-Null
        if ((Invoke-Adb @('shell','settings','get','system','font_scale')) -ne $scale) { throw 'FONT_SCALE_WRITE_UNCONFIRMED' }
        foreach ($orientation in @('portrait','landscape')) {
            $phaseArguments = @('shell','am','instrument','-w','-r','-e','t16DedicatedEmulator','true',
                '-e','t16ExpectedFontScale',$scale,'-e','t16Orientation',$orientation,
                '-e','class','com.jovi.photoai.t16.T16CameraAccessibilityAndroidTest#hostFontAndOrientationKeepCapabilitiesAndInstructionReadable',
                'com.jovi.photoai.test/androidx.test.runner.AndroidJUnitRunner')
            $lines = @(& $adbPath -s $Serial @phaseArguments 2>&1)
            $phaseExit = $LASTEXITCODE
            $output = $lines -join "`n"
            Set-Content -LiteralPath (Join-Path $runRoot "font-$scale-$orientation.log") -Value $output -Encoding UTF8
            if ($phaseExit -ne 0 -or $output -notmatch '(?m)^OK \(1 tests?\)\r?$' -or
                $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw "FONT_${scale}_${orientation}_FAILED" }
            $summary.points += "$scale/$orientation=PASS"
        }
    }
    $summary.result = 'PASS'
} catch {
    $failure = $_
    $summary.result = 'FAIL'
} finally {
    if ($mutated) {
        try {
            Assert-Identity
            if ($original -eq 'null') { Invoke-Adb @('shell','settings','delete','system','font_scale') | Out-Null }
            else { Invoke-Adb @('shell','settings','put','system','font_scale',$original) | Out-Null }
            $restored = Invoke-Adb @('shell','settings','get','system','font_scale')
            $summary.restored_font_scale = $restored
            if ($restored -ne $original) { throw 'FONT_SCALE_EXACT_RESTORE_FAILED' }
            $summary.restoration = 'PASS'
        } catch { $summary.restoration = 'FAIL'; $summary.result = 'FAIL'; $failure = $_ }
    }
    $summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runRoot 'summary.json') -Encoding UTF8
}
$summary | ConvertTo-Json -Depth 4
if ($failure) { throw $failure }
