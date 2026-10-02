[CmdletBinding()]
param(
    [ValidateSet('emulator-5580')][string]$Serial = 'emulator-5580',
    [string]$EvidenceDirectory,
    [ValidateSet('success','deleted')][string[]]$Mode = @('success','deleted'),
    [switch]$PolicyOnly
)
$ErrorActionPreference = 'Stop'

function Get-T17CleanupDecision([hashtable]$Receipt) {
    if ($Receipt.projectId -notmatch '^[a-f0-9-]{36}$' -or $Receipt.referenceId -notmatch '^[a-f0-9-]{36}$') { return 'PREFERENCE_ONLY_RETAIN' }
    foreach ($prefix in @('c1','c2')) {
        $attempted = $Receipt["${prefix}Attempted"]
        if ($attempted -notin @('true','false')) { return 'PREFERENCE_ONLY_RETAIN' }
        $id = $Receipt["${prefix}Id"]
        if ($attempted -eq 'true' -and $id -notmatch '^[a-f0-9]{32}$') { return 'PREFERENCE_ONLY_RETAIN' }
        if ($attempted -eq 'false' -and $id) { return 'PREFERENCE_ONLY_RETAIN' }
    }
    if ($Receipt.c1Id -and $Receipt.c1Id -eq $Receipt.c2Id) { return 'PREFERENCE_ONLY_RETAIN' }
    return 'EXACT_CLEANUP'
}
function Protect-T17LogLine([string]$Line,[hashtable]$Receipt = @{}) {
    $Line = $Line -replace '(T17_RECEIPT_(?:prefPresent|prefBase64|c1TupleBase64)=).*','$1<PRIVATE_REDACTED>'
    foreach ($key in @('prefBase64','c1TupleBase64')) {
        $privateValue = $Receipt[$key]
        if ($privateValue -and $privateValue -notin @('NONE','EMPTY')) { $Line = $Line.Replace([string]$privateValue,'<PRIVATE_REDACTED>') }
    }
    if ($Receipt.prefBase64 -and $Receipt.prefBase64 -notin @('NONE','EMPTY')) {
        try {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Receipt.prefBase64))
            if ($decoded) { $Line = $Line.Replace($decoded,'<PRIVATE_REDACTED>') }
        } catch { } # malformed private receipt is kept private and rejected by Android restoration.
    }
    return $Line
}
function Update-T17Receipt([hashtable]$Receipt,[string]$Line) {
    if ($Line -notmatch '^INSTRUMENTATION_STATUS: T17_RECEIPT_([A-Za-z][A-Za-z0-9]*)=(.*)$') { return $false }
    $key = $Matches[1]; $value = $Matches[2]
    if ($Receipt.ContainsKey($key) -and $Receipt[$key] -ne $value) {
        if ($key -notin @('c1Attempted','c2Attempted') -or $Receipt[$key] -ne 'false' -or $value -ne 'true') {
            throw "RECEIPT_IDENTITY_DRIFT_$key"
        }
    }
    $Receipt[$key] = $value
    return $true
}
if ($PolicyOnly) { return }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) { throw 'EVIDENCE_DIRECTORY_REQUIRED' }
$evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
if ($evidenceRoot.Equals($repoRoot,[StringComparison]::OrdinalIgnoreCase) -or
    $evidenceRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY' }
if (!(Test-Path -LiteralPath $evidenceRoot -PathType Container)) { throw 'EVIDENCE_DIRECTORY_MUST_EXIST' }
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adbPath)) { throw 'ADB_NOT_FOUND' }
$targetPackage = 'com.jovi.photoai'; $testPackage = 'com.jovi.photoai.test'
$testClass = 'com.jovi.photoai.t17.T17OfflineGuidedContinuityAndroidTest'
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
function Adb([string[]]$Arguments) {
    $result = @(& $adbPath -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_COMMAND_FAILED' }
    return ($result -join "`n").Trim()
}
function Assert-T17Identity {
    if ((Adb @('get-state')) -ne 'device' -or (Adb @('shell','getprop','sys.boot_completed')) -ne '1' -or
        (Adb @('shell','getprop','ro.build.version.sdk')) -ne '35' -or (Adb @('shell','getprop','ro.kernel.qemu')) -ne '1' -or
        (Adb @('shell','getprop','ro.boot.qemu.avd_name')) -ne 'T3_API35_20260927') { throw 'EXACT_DEDICATED_API35_EMULATOR_REQUIRED' }
}
function Permission-State {
    $dump = Adb @('shell','dumpsys','package',$targetPackage)
    $found = [regex]::Matches($dump,'(?m)^\s*android\.permission\.CAMERA: granted=(true|false), flags=\[([^\]]*)\]')
    if ($found.Count -ne 1) { throw 'CAMERA_PERMISSION_STATE_AMBIGUOUS' }
    return @{ granted = $found[0].Groups[1].Value; flags = @($found[0].Groups[2].Value -split '[|,]' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object) }
}
function Package-Uid([string]$Package) {
    $found = [regex]::Match((Adb @('shell','cmd','package','list','packages','-U',$Package)),"(?m)^package:$([regex]::Escape($Package)) uid:([0-9]+)\r?$")
    if (!$found.Success) { throw 'PACKAGE_UID_UNPROVEN' }
    return $found.Groups[1].Value
}
function Save-PrivateMarker {
    $temporary = "$markerPath.tmp"
    [IO.File]::WriteAllText($temporary,($receipt | ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporary,$markerPath,$true)
}
function Phase-Arguments {
    $arguments = @('-e','t17DedicatedEmulator','true','-e','t17Run',$runId,'-e','t17Mode',$currentMode)
    $mapping = @{ projectId='t17ProjectId'; referenceId='t17ReferenceId'; c1Attempted='t17C1Attempted'; c1Id='t17C1Id';
        c2Attempted='t17C2Attempted'; c2Id='t17C2Id'; prefPresent='t17OriginalPrefPresent'; prefBase64='t17OriginalPrefBase64';
        c1Sha256='t17C1Sha256'; referenceSha256='t17ReferenceSha256'; c1TupleBase64='t17C1TupleBase64';
        processEpoch='t17ProcessEpoch'; processId='t17ProcessId'; processUid='t17ProcessUid'; processName='t17ProcessName';
        targetPackage='t17TargetPackage'; targetUid='t17TargetUid'; testPackage='t17TestPackage'; testUid='t17TestUid' }
    foreach ($key in $mapping.Keys) { if ($receipt.ContainsKey($key)) { $arguments += @('-e',$mapping[$key],[string]$receipt[$key]) } }
    return $arguments
}
function Invoke-T17Phase([string]$Method,[string]$Name) {
    Assert-T17Identity
    if ($targetUid -and ((Package-Uid $targetPackage) -ne $targetUid -or (Package-Uid $testPackage) -ne $testUid)) { throw 'PACKAGE_UID_CHANGED_REFUSE_PHASE' }
    $start = [Diagnostics.ProcessStartInfo]::new($adbPath)
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    foreach ($argument in (@('-s',$Serial,'shell','am','instrument','-w','-r','-e','class',"$testClass#$Method") + (Phase-Arguments) + @($runner))) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    $started = $false
    $lines = [Collections.Generic.List[string]]::new()
    $log = [IO.StreamWriter]::new((Join-Path $runRoot "$Name.log"),$false,[Text.UTF8Encoding]::new($false))
    try {
        [void]$process.Start()
        $started = $true
        $stderr = $process.StandardError.ReadToEndAsync()
        $clock = [Diagnostics.Stopwatch]::StartNew()
        while ($true) {
            if ($clock.Elapsed.TotalSeconds -ge 180) { throw 'INSTRUMENTATION_TIMEOUT' }
            $read = $process.StandardOutput.ReadLineAsync()
            while (!$read.IsCompleted) {
                if ($clock.Elapsed.TotalSeconds -ge 180) { throw 'INSTRUMENTATION_TIMEOUT' }
                [void]$read.Wait(200)
            }
            $line = $read.GetAwaiter().GetResult()
            if ($null -eq $line) { break }
            # Capture the receipt before redaction; persist even if a later test assertion fails.
            if (Update-T17Receipt $receipt $line) { Save-PrivateMarker }
            $safe = Protect-T17LogLine $line $receipt
            $lines.Add($safe); $log.WriteLine($safe); $log.Flush()
        }
        $remainingMilliseconds = [int][Math]::Max(0, 180000 - $clock.Elapsed.TotalMilliseconds)
        if ($remainingMilliseconds -le 0 -or !$process.WaitForExit($remainingMilliseconds)) { throw 'INSTRUMENTATION_TIMEOUT' }
        # stderr may remain open after stdout EOF; share the same phase deadline.
        $remainingMilliseconds = [int][Math]::Max(0, 180000 - $clock.Elapsed.TotalMilliseconds)
        if (!$stderr.IsCompleted -and ($remainingMilliseconds -le 0 -or !$stderr.Wait($remainingMilliseconds))) { throw 'INSTRUMENTATION_TIMEOUT' }
        foreach ($line in ($stderr.GetAwaiter().GetResult() -split "`r?`n")) { if ($line) { $safe = Protect-T17LogLine $line $receipt; $lines.Add($safe); $log.WriteLine($safe) } }
        $output = $lines -join "`n"
        if ($process.ExitCode -ne 0 -or $output -notmatch '(?m)^OK \(1 tests?\)\r?$' -or
            $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|There was [0-9]+ failure') { throw "${Name}_INSTRUMENTATION_FAILED" }
    } catch {
        $phaseFailure = $_.Exception.Message
        # Killing the host adb client alone does not stop its device instrumentation.
        if ($started -and !$process.HasExited) { $process.Kill(); $process.WaitForExit() }
        try { Stop-FailedT17Phase $Name }
        catch {
            $script:t17FailureQuiescent = $false
            $summary.failed_phase_quiescence = 'BLOCKED_NO_CLEANUP_OR_PREFERENCE_RACE'
            throw "FAILED_PHASE_NOT_QUIESCENT_$Name"
        }
        throw $phaseFailure
    } finally {
        if ($started -and !$process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $log.Dispose(); $process.Dispose()
    }
}
function Get-Pids([string]$Name) {
    $values = @(& $adbPath -s $Serial shell pidof $Name 2>$null)
    if ($LASTEXITCODE -ne 0) { return @() }
    return @((($values -join ' ').Trim() -split '\s+') | Where-Object { $_ })
}
function Stop-FailedT17Phase([string]$Name) {
    Assert-T17Identity
    if ((Package-Uid $targetPackage) -ne $targetUid -or (Package-Uid $testPackage) -ne $testUid) { throw 'PACKAGE_UID_CHANGED_REFUSE_FAILED_PHASE_STOP' }
    # Target UID was measured before PREPARE. This remains usable when PREPARE
    # fails before publishing its process-name receipt (possibly named .test).
    $observed = @(Get-TargetUidPids)
    if ($observed.Count -gt 0) {
        Adb @('shell','am','force-stop',$targetPackage) | Out-Null
        $boundary = 'FAILED_PHASE_TARGET_PACKAGE_FORCE_STOP'
    } else { $boundary = 'FAILED_PHASE_PROCESS_ALREADY_EXITED' }
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $remaining = @(Get-TargetUidPids)
        if ($remaining.Count -eq 0) { break }
        Start-Sleep -Milliseconds 200
    }
    if ($remaining.Count -ne 0) { throw 'FAILED_PHASE_TARGET_PROCESSES_STILL_ALIVE' }
    $summary.failed_phase_boundaries = @($summary.failed_phase_boundaries) + "$Name=$boundary"
    $summary.failed_phase_quiescence = 'PASS_MEASURED_TARGET_UID_PROCESSES_ABSENT'
}
function Get-TargetUidPids {
    $table = Adb @('shell','ps','-A','-o','UID,PID,NAME')
    if ($table -notmatch '(?m)^\s*UID\s+PID\s+NAME\s*\r?$') { throw 'TARGET_UID_PROCESS_TABLE_UNPROVEN' }
    $ownedPids = @()
    foreach ($line in ($table -split "`r?`n")) {
        if ($line -match '^\s*UID\s+PID\s+NAME\s*$') { continue }
        if ($line -notmatch '^\s*([0-9]+)\s+([0-9]+)\s+(\S+)\s*$') { throw 'TARGET_UID_PROCESS_ROW_UNPROVEN' }
        if ($Matches[1] -eq $targetUid) { $ownedPids += $Matches[2] }
    }
    return $ownedPids
}

$allPassed = $true
foreach ($currentMode in $Mode) {
    $runId = [guid]::NewGuid().ToString('N')
    $runRoot = Join-Path $evidenceRoot "t17-$currentMode-$runId"
    if (Test-Path -LiteralPath $runRoot) { throw 'RUN_EVIDENCE_COLLISION' }
    New-Item -ItemType Directory -Path $runRoot | Out-Null
    $markerPath = Join-Path $runRoot 'private-recovery-marker.json'
    $receipt = @{ runId=$runId; mode=$currentMode }
    Save-PrivateMarker # precedes PREPARE and all fixture mutation.
    $summary = [ordered]@{ gate='T17_OFFLINE_GUIDED_CONTINUITY'; run_id=$runId; mode=$currentMode; prepare='NOT_RUN';
        verify='NOT_RUN'; fixture_cleanup='NOT_RUN'; preference_restoration='NOT_RUN'; marker='RETAINED'; result='BLOCKED' }
    $prepareAttempted = $false; $failure = $null; $permissionBefore = $null; $targetUid = $null; $testUid = $null
    $script:t17FailureQuiescent = $true
    try {
        Assert-T17Identity
        $permissionBefore = Permission-State
        if ($permissionBefore.granted -ne 'true') { throw 'EXISTING_CAMERA_GRANT_REQUIRED_NO_PERMISSION_MUTATION' }
        $targetUid = Package-Uid $targetPackage; $testUid = Package-Uid $testPackage
        $prepareAttempted = $true
        Invoke-T17Phase 'prepareRootGuidedCaptureAndCurrentBundle' 'prepare'
        foreach ($key in @('processEpoch','processId','processName','processUid','targetPackage','targetUid','testPackage','testUid')) {
            if (!$receipt[$key]) { throw "PREPARE_IDENTITY_MISSING_$key" }
        }
        if ($receipt.runId -ne $runId -or $receipt.mode -ne $currentMode -or $receipt.targetPackage -ne $targetPackage -or
            $receipt.testPackage -ne $testPackage -or $receipt.processUid -ne $targetUid -or $receipt.targetUid -ne $targetUid -or
            $receipt.testUid -ne $testUid -or $receipt.processUid -eq $testUid -or $receipt.processName -notin @($targetPackage,$testPackage) -or
            $receipt.processId -notmatch '^[0-9]+$') { throw 'PREPARE_PROCESS_BOUNDARY_MISMATCH' }
        $summary.prepare = 'PASS'
        Assert-T17Identity
        $oldPid = $receipt.processId
        $alive = $oldPid -in @((Get-Pids $receipt.processName) + (Get-Pids $targetPackage))
        if ($alive) {
            Adb @('shell','am','force-stop',$targetPackage) | Out-Null
            $summary.process_boundary = 'TARGET_PACKAGE_FORCE_STOP'
        } else { $summary.process_boundary = 'INSTRUMENTATION_PROCESS_EXIT_NO_FORCE_STOP_NEEDED' }
        for ($attempt=0; $attempt -lt 60 -and $oldPid -in @((Get-Pids $receipt.processName)+(Get-Pids $targetPackage)); $attempt++) { Start-Sleep -Milliseconds 200 }
        if ($oldPid -in @((Get-Pids $receipt.processName)+(Get-Pids $targetPackage))) { throw 'PREPARE_PID_STILL_ALIVE' }
        Invoke-T17Phase 'verifyFreshProcessRetakeUsesCurrentGuidance' 'verify'
        if (!$receipt.verifyProcessEpoch -or !$receipt.verifyProcessId -or $receipt.verifyProcessEpoch -eq $receipt.processEpoch -or
            $receipt.verifyProcessId -eq $oldPid -or $receipt.verifyProcessUid -ne $targetUid) { throw 'FRESH_VERIFY_PROCESS_NOT_PROVEN' }
        $summary.verify = 'PASS'
    } catch { $failure = $_.Exception.Message }
    finally {
        if ($prepareAttempted -and $script:t17FailureQuiescent) {
            $decision = Get-T17CleanupDecision $receipt
            if ($decision -eq 'EXACT_CLEANUP') {
                try {
                    Invoke-T17Phase 'cleanupExactRecordedFixtureAndRestorePreference' 'cleanup'
                    if ($receipt.cleanupFixtureAbsent -ne 'true' -or $receipt.prefRestored -ne 'true') { throw 'EXACT_CLEANUP_NOT_PROVEN' }
                    $summary.fixture_cleanup = 'PASS_EXACT_RECORDED_IDS_ABSENT'
                    $summary.preference_restoration = 'PASS_GUARDED_EXACT_KEY'
                } catch { $summary.cleanup_failure = $_.Exception.Message }
            } else { $summary.fixture_cleanup = 'BLOCKED_INCOMPLETE_OWNERSHIP_RETAINED' }
            # Even failed or incomplete capture receipts must attempt the separate non-destructive key restore.
            if ($summary.preference_restoration -ne 'PASS_GUARDED_EXACT_KEY' -and $script:t17FailureQuiescent) {
                try {
                    Invoke-T17Phase 'restoreOriginalPreferenceGuardedIndependentOfFixture' 'restore-preference'
                    if ($receipt.prefRestored -ne 'true') { throw 'PREFERENCE_RESTORATION_NOT_PROVEN' }
                    $summary.preference_restoration = 'PASS_GUARDED_EXACT_KEY'
                } catch { $summary.preference_restoration='BLOCKED'; $summary.preference_failure=$_.Exception.Message }
            }
        } elseif ($prepareAttempted) {
            $summary.fixture_cleanup = 'BLOCKED_ONGOING_FAILED_PHASE_RETAINED'
            $summary.preference_restoration = 'BLOCKED_ONGOING_FAILED_PHASE_NO_WRITE'
        }
        if ($permissionBefore) {
            try {
                Assert-T17Identity
                $after = Permission-State
                if ($after.granted -ne $permissionBefore.granted -or ($after.flags -join '|') -ne ($permissionBefore.flags -join '|')) { throw 'CAMERA_GRANT_OR_FLAGS_CHANGED' }
                $summary.permission_unchanged = 'PASS_FULL_FLAGS_EQUAL_NO_GRANT_REVOKE'
            } catch { $failure = $_.Exception.Message }
        }
        if (!$failure -and $summary.verify -eq 'PASS' -and $summary.fixture_cleanup -eq 'PASS_EXACT_RECORDED_IDS_ABSENT' -and
            $summary.preference_restoration -eq 'PASS_GUARDED_EXACT_KEY') { $summary.result = 'PASS' }
        if ($failure) { $summary.failure = $failure }
        if ($summary.fixture_cleanup -eq 'PASS_EXACT_RECORDED_IDS_ABSENT' -and $summary.preference_restoration -eq 'PASS_GUARDED_EXACT_KEY') {
            try {
                Remove-Item -LiteralPath $markerPath -ErrorAction Stop
                $summary.marker = 'REMOVED_AFTER_EXACT_CLEANUP_AND_PREFERENCE_RESTORATION'
            } catch { $summary.marker='RETAINED_REMOVAL_FAILED'; $summary.result='BLOCKED'; $summary.marker_failure='HOST_MARKER_REMOVAL_FAILED' }
        }
        if ($summary.result -ne 'PASS') { $allPassed=$false }
        $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runRoot 'summary.json') -Encoding UTF8
        $summary | ConvertTo-Json -Depth 5
    }
    if (!$allPassed) { break } # retained fixture blocks new fixture creation.
}
if (!$allPassed) { exit 1 }
