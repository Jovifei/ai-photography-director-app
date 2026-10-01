[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('emulator-5580')][string]$Serial,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
if ($evidenceRoot.Equals($repo, [StringComparison]::OrdinalIgnoreCase) -or
    $evidenceRoot.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY'
}
if (!(Test-Path -LiteralPath $evidenceRoot -PathType Container)) { throw 'EVIDENCE_DIRECTORY_MUST_EXIST' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'ADB_NOT_FOUND' }
$runId = [guid]::NewGuid().ToString('N')
$evidence = Join-Path $evidenceRoot "t16-permission-$runId"
New-Item -ItemType Directory -Path $evidence | Out-Null
$package = 'com.jovi.photoai'
$permission = 'android.permission.CAMERA'
$runner = 'com.jovi.photoai.test/androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.jovi.photoai.t16.T16CameraPermissionAndroidTest'
$original = $null
$mutated = $false
$failure = $null
$summary = [ordered]@{ run_id = $runId; serial = $Serial; prepare = 'NOT_RUN'; denied = 'NOT_RUN';
    recovery = 'NOT_RUN'; restoration = 'NOT_RUN'; result = 'BLOCKED'; evidence_directory = $evidence }

function Adb([string[]]$Arguments) {
    $output = @(& $adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB_FAILED: $($Arguments -join ' '): $($output -join ' ')" }
    return ($output -join "`n").Trim()
}
function Assert-Identity {
    if ((Adb @('get-state')) -ne 'device' -or
        (Adb @('shell','getprop','sys.boot_completed')) -ne '1' -or
        (Adb @('shell','getprop','ro.build.version.sdk')) -ne '35' -or
        (Adb @('shell','getprop','ro.kernel.qemu')) -ne '1' -or
        (Adb @('shell','getprop','ro.boot.qemu.avd_name')) -ne 'T3_API35_20260927') {
        throw 'EXACT_DEDICATED_API35_EMULATOR_REQUIRED'
    }
}
function Get-PermissionState {
    $dump = Adb @('shell','dumpsys','package',$package)
    $matchesFound = [regex]::Matches($dump, '(?m)^\s*android\.permission\.CAMERA: granted=(true|false), flags=\[([^\]]*)\]')
    if ($matchesFound.Count -ne 1) { throw 'CAMERA_PERMISSION_STATE_AMBIGUOUS' }
    $flags = @($matchesFound[0].Groups[2].Value -split '[|,]' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object)
    return @{ granted = $matchesFound[0].Groups[1].Value -eq 'true'; flags = $flags }
}
function Phase([string]$Method,[string]$Name) {
    Assert-Identity
    $arguments = @('shell','am','instrument','-w','-r','-e','class',"$testClass#$Method",
        '-e','t16DedicatedEmulator','true','-e','t16PermissionRun',$runId,$runner)
    $lines = @(& $adb -s $Serial @arguments 2>&1)
    $phaseExit = $LASTEXITCODE
    $output = $lines -join "`n"
    Set-Content -LiteralPath (Join-Path $evidence "$Name.log") -Value $output -Encoding UTF8
    if ($phaseExit -ne 0 -or $output -notmatch '(?m)^OK \(1 tests?\)\r?$' -or
        $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|There was [0-9]+ failure') {
        throw "T16_PERMISSION_${Name}_INSTRUMENTATION_FAILED"
    }
    return $output
}
function Restore-OriginalPermission([string]$Name) {
    Assert-Identity
    $restorationErrors = [Collections.Generic.List[string]]::new()
    $operation = if ($original.granted) { 'grant' } else { 'revoke' }
    try { Adb @('shell','pm',$operation,$package,$permission) | Out-Null }
    catch { $restorationErrors.Add("grant-state: $($_.Exception.Message)") }
    foreach ($flag in @('user-set','user-fixed')) {
        $originalName = $flag.ToUpperInvariant().Replace('-','_')
        $operation = if ($original.flags -contains $originalName) { 'set-permission-flags' } else { 'clear-permission-flags' }
        try { Adb @('shell','pm',$operation,$package,$permission,$flag) | Out-Null }
        catch { $restorationErrors.Add("${flag}: $($_.Exception.Message)") }
    }
    # Read and compare all flags even when an independent restoration command failed.
    try {
        $restored = Get-PermissionState
        $restored | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $evidence "$Name-permission.json") -Encoding UTF8
        if ($restored.granted -ne $original.granted -or
            (($restored.flags -join '|') -ne ($original.flags -join '|'))) {
            $restorationErrors.Add('EXACT_CAMERA_PERMISSION_AND_FLAGS_RESTORATION_FAILED')
        }
    } catch { $restorationErrors.Add("verify: $($_.Exception.Message)") }
    if ($restorationErrors.Count -gt 0) { throw ($restorationErrors -join '; ') }
}
try {
    Assert-Identity
    $original = Get-PermissionState
    $original | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $evidence 'original-permission.json') -Encoding UTF8
    # Classification flags are never requested by this runner. All mutable accepted flags
    # have explicit restoration commands; refuse one-time/revocation/policy flags up front.
    $allowedFlags = @('USER_SET','USER_FIXED','USER_SENSITIVE_WHEN_GRANTED','USER_SENSITIVE_WHEN_DENIED')
    $unsupportedFlags = @($original.flags | Where-Object { $_ -notin $allowedFlags })
    if ($unsupportedFlags.Count -gt 0) { throw "UNSUPPORTED_ORIGINAL_PERMISSION_FLAGS: $($unsupportedFlags -join '|')" }
    $prepared = Phase 'prepareMeasuredOriginalPermissionWithoutActivityOrMutation' 'prepare'
    $measured = if ($original.granted) { 'GRANTED' } else { 'DENIED' }
    if ($prepared -notmatch "T16_PERMISSION_RUN_ID=$runId" -or
        $prepared -notmatch "T16_PERMISSION_ORIGINAL=$measured" -or
        $prepared -notmatch 'T16_PERMISSION_PREPARE_PID=[0-9]+') { throw 'PREPARE_IDENTITY_OR_PERMISSION_MISMATCH' }
    $summary.prepare = 'PASS'
    $preparePid = [regex]::Match($prepared, 'T16_PERMISSION_PREPARE_PID=([0-9]+)').Groups[1].Value
    $summary.prepare_pid = $preparePid
    Assert-Identity
    $mutated = $true # finally restores even if the shell mutation outcome is unknown.
    Adb @('shell','pm','revoke',$package,$permission) | Out-Null
    Adb @('shell','pm','set-permission-flags',$package,$permission,'user-fixed') | Out-Null
    if ((Get-PermissionState).granted) { throw 'REVOKE_NOT_VERIFIED' }
    $denied = Phase 'freshDeniedProcessShowsPermissionContentWithoutHardwareControls' 'denied'
    $deniedPid = [regex]::Match($denied, 'T16_PERMISSION_DENIED_PID=([0-9]+)').Groups[1].Value
    if (!$deniedPid -or $deniedPid -eq $preparePid) { throw 'DENIED_FRESH_PROCESS_NOT_PROVEN' }
    $summary.denied_pid = $deniedPid
    $summary.denied = 'PASS'
} catch { $failure = $_ }
finally {
    if ($mutated -and $null -ne $original) {
        try {
            Restore-OriginalPermission 'restored'
            $summary.restoration = 'PASS_INDEPENDENT_HOST_GRANTED_AND_ALL_FLAGS_EQUAL'
        } catch {
            $summary.restoration = 'FAILED'
            if ($null -eq $failure) { $failure = $_ } else { $summary.restoration_failure = $_.Exception.Message }
        }
    }
}
try {
    if ($null -eq $failure -and $original.granted) {
        $recovery = Phase 'freshGrantedProcessRecoversActualCameraPreview' 'recovery'
        $recoveryPid = [regex]::Match($recovery, 'T16_PERMISSION_RECOVERY_PID=([0-9]+)').Groups[1].Value
        if (!$recoveryPid -or $recoveryPid -eq $deniedPid -or $recoveryPid -eq $preparePid) { throw 'RECOVERY_FRESH_PROCESS_NOT_PROVEN' }
        $summary.recovery_pid = $recoveryPid
        $summary.recovery = 'PASS'
        $postRecovery = Get-PermissionState
        if ($postRecovery.granted -ne $original.granted -or ($postRecovery.flags -join '|') -ne ($original.flags -join '|')) {
            throw 'RECOVERY_CHANGED_PERMISSION_FLAGS'
        }
    } elseif ($null -eq $failure) { $summary.recovery = 'SKIPPED_ORIGINAL_PERMISSION_DENIED' }
} catch { $failure = $_ }
finally {
    if ($mutated -and $null -ne $original) {
        try {
            Restore-OriginalPermission 'final-restored'
            $summary.final_restoration = 'PASS_INDEPENDENT_HOST_GRANTED_AND_ALL_FLAGS_EQUAL'
        } catch {
            $summary.final_restoration = 'FAILED'
            if ($null -eq $failure) { $failure = $_ } else { $summary.final_restoration_failure = $_.Exception.Message }
        }
    }
}
if ($null -eq $failure) { $summary.result = 'PASS' } else { $summary.result = 'FAIL'; $summary.failure = $failure.Exception.Message }
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'summary.json') -Encoding UTF8
$summary | ConvertTo-Json -Depth 5
if ($null -ne $failure) { throw $failure }
