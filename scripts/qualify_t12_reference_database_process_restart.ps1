[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
if ($evidenceRoot.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) { throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY' }
if (!(Test-Path -LiteralPath $evidenceRoot -PathType Container)) { throw 'EVIDENCE_DIRECTORY_MUST_EXIST' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'ADB_NOT_FOUND' }

$targetPackage = 'com.jovi.photoai'
$testPackage = 'com.jovi.photoai.test'
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$testClass = 'com.jovi.photoai.t12.T12ReferenceDatabaseProcessRestartAndroidTest'
$runId = [guid]::NewGuid().ToString('N')
$evidence = Join-Path $evidenceRoot $runId
if (Test-Path -LiteralPath $evidence) { throw 'RUN_EVIDENCE_DIRECTORY_ALREADY_EXISTS' }
New-Item -ItemType Directory -Path $evidence | Out-Null
$markerPath = Join-Path $evidence "$runId.synthetic-process-marker.json"
$marker = @{ runId = $runId }
$prepareAttempted = $false
$summary = [ordered]@{
    gate = 'T12_REFERENCE_DATABASE_SYNTHETIC_PROCESS_RESTART'
    run_id = $runId
    target_package = $targetPackage
    test_package = $testPackage
    run_evidence_directory = $evidence
    marker_location = 'host_evidence_only'
    target_app_synthetic_fixture_write = 'NOT_RUN'
    target_app_marker_written = $false
    test_package_marker_written = $false
    main_activity_launched = $false
    prepare = 'NOT_RUN'
    singleton_owner_uid_verified = 'NOT_RUN'
    force_stop = 'NOT_RUN'
    force_stop_boundary = 'NOT_RUN'
    new_process_epoch = 'NOT_RUN'
    persisted_project_reference_reopen = 'NOT_RUN'
    exact_project_cleanup = 'NOT_RUN'
    host_marker_cleanup = 'NOT_RUN'
    clear_all_used = $false
    failure = 'NONE'
    result = 'BLOCKED'
}
$marker | ConvertTo-Json -Compress | Set-Content -LiteralPath $markerPath -Encoding UTF8

function Invoke-T12Phase([string]$Method, [string]$Phase, [string[]]$ExtraArguments = @()) {
    $logPath = Join-Path $evidence "$Phase.log"
    $arguments = @('shell','am','instrument','-w','-r','-e','class',"$testClass#$Method",'-e','t12RestartRun',$runId) + $ExtraArguments + @($runner)
    $output = @(& $adb -s $Serial @arguments 2>&1)
    $exitCode = $LASTEXITCODE
    Set-Content -LiteralPath $logPath -Value $output -Encoding UTF8
    $lines = @(Get-Content -LiteralPath $logPath)
    if ($exitCode -ne 0 -or ($lines -match '^OK \(1 tests?\)$').Count -ne 1 -or
        ($lines -match 'FAILURES!!!|There was [0-9]+ failure').Count -ne 0) {
        throw "T12_RESTART_$($Phase.ToUpperInvariant())_INSTRUMENTATION_FAILED"
    }
    return $lines
}

function Get-UniquePhaseIdentity([string[]]$Lines,[string]$Phase) {
    $prefix = 'T12_RESTART_' + $Phase + '_'
    $values = @{}
    foreach ($line in $Lines) {
        if ($line -match '^INSTRUMENTATION_STATUS: (T12_RESTART_[A-Z]+_\w+)=(.*)$') {
            $key = $Matches[1]
            if ($key.StartsWith($prefix,[StringComparison]::Ordinal)) { $values[$key.Substring($prefix.Length)] = $Matches[2] }
        }
    }
    $required = if ($Phase -eq 'PREPARE') {
        @('runId','projectId','referenceId','processEpoch','processId','processName','processUid','targetPackage','targetUid','testPackage','testUid')
    } else {
        @('runId','projectId','referenceId','processEpoch','processId','processName','processUid')
    }
    foreach ($key in $required) { if (!$values.ContainsKey($key)) { throw ('T12_RESTART_' + $Phase + '_IDENTITY_MISSING_' + $key) } }
    return $values
}

function Get-T12Pid([string]$ProcessName) {
    $output = @(& $adb -s $Serial shell pidof $ProcessName 2>$null)
    if ($LASTEXITCODE -ne 0) { return @() }
    return @((([string]::Join(' ',$output)).Trim() -split '\s+') | Where-Object { $_ })
}

try {
    if (((& $adb -s $Serial get-state 2>$null) -join '').Trim() -ne 'device') { throw 'EMULATOR_OFFLINE' }
    if ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -join '' -ne '1' -or
        (& $adb -s $Serial shell getprop ro.build.version.sdk 2>$null) -join '' -ne '35' -or
        (& $adb -s $Serial shell getprop ro.kernel.qemu 2>$null) -join '' -ne '1') { throw 'DEDICATED_API35_EMULATOR_REQUIRED' }
    if ((Get-T12Pid $targetPackage).Count -ne 0) { throw 'TARGET_APP_PROCESS_ALREADY_RUNNING' }
    if ((Get-T12Pid $testPackage).Count -ne 0) { throw 'ANDROID_TEST_PROCESS_ALREADY_RUNNING' }

    $prepareAttempted = $true
    $summary.target_app_synthetic_fixture_write = 'ATTEMPTED_OUTCOME_UNKNOWN'
    $prepareLines = Invoke-T12Phase 'prepareUniqueProjectAndReferenceForProcessStop' 'prepare'
    $prepared = Get-UniquePhaseIdentity $prepareLines 'PREPARE'
    if ($prepared.runId -ne $runId -or $prepared.targetPackage -ne $targetPackage -or $prepared.testPackage -ne $testPackage -or
        $prepared.processUid -ne $prepared.targetUid -or $prepared.processUid -eq $prepared.testUid -or
        $prepared.processName -notin @($targetPackage, $testPackage)) { throw 'PREPARE_UID_OR_PACKAGE_BOUNDARY_MISMATCH' }
    if ($prepared.projectId -notmatch '^[a-f0-9-]{36}$' -or $prepared.referenceId -notmatch '^[a-f0-9-]{36}$' -or
        $prepared.processId -notmatch '^[0-9]+$') { throw 'PREPARE_SYNTHETIC_IDENTITY_INVALID' }
    $marker = $prepared
    $marker | ConvertTo-Json -Compress | Set-Content -LiteralPath $markerPath -Encoding UTF8
    $summary.prepare = 'PASS'
    $summary.target_app_synthetic_fixture_write = 'PASS_RUN_ID_SCOPED_PROJECT_REFERENCE_AND_JPEG'
    $summary.process_identity = "processName=$($marker.processName); UID=$($marker.processUid); PID=$($marker.processId)"
    $summary.singleton_owner_uid_verified = 'PASS_TARGET_UID'

    $pidByName = @(Get-T12Pid $marker.processName)
    $pidByTargetPackage = @(Get-T12Pid $marker.targetPackage)
    $processAlive = $marker.processId -in $pidByName -or $marker.processId -in $pidByTargetPackage
    & $adb -s $Serial shell am force-stop $targetPackage *> (Join-Path $evidence 'force-stop.log')
    if ($LASTEXITCODE -ne 0) { throw 'FORCE_STOP_TARGET_PACKAGE_FAILED' }
    $oldProcessGone = !$processAlive
    for ($attempt = 0; $processAlive -and $attempt -lt 60; $attempt++) {
        $stillByName = @(Get-T12Pid $marker.processName)
        $stillTarget = @(Get-T12Pid $marker.targetPackage)
        $processAlive = $marker.processId -in $stillByName -or $marker.processId -in $stillTarget
        $oldProcessGone = !$processAlive
        if (!$oldProcessGone) { Start-Sleep -Milliseconds 250 }
    }
    if (!$oldProcessGone) { throw 'PREPARE_SINGLETON_OWNER_PROCESS_STILL_RUNNING' }
    $summary.force_stop = 'PASS'
    $summary.force_stop_boundary = if (($pidByName + $pidByTargetPackage) -contains $marker.processId) { 'TARGET_PACKAGE_FORCE_STOP' } else { 'INSTRUMENTATION_PROCESS_EXIT_FORCE_STOP_IDEMPOTENT' }

    $verifyArguments = @(
        '-e','t12RestartProjectId',$marker.projectId,
        '-e','t12RestartReferenceId',$marker.referenceId,
        '-e','t12RestartProcessEpoch',$marker.processEpoch,
        '-e','t12RestartProcessId',$marker.processId,
        '-e','t12RestartProcessName',$marker.processName,
        '-e','t12RestartProcessUid',$marker.processUid,
        '-e','t12RestartTargetPackage',$marker.targetPackage,
        '-e','t12RestartTargetUid',$marker.targetUid,
        '-e','t12RestartTestPackage',$marker.testPackage,
        '-e','t12RestartTestUid',$marker.testUid
    )
    $verifyLines = Invoke-T12Phase 'verifyPersistedProjectAndReferenceInNewProcess' 'verify' $verifyArguments
    $verify = Get-UniquePhaseIdentity $verifyLines 'VERIFY'
    if ($verify.runId -ne $runId -or $verify.projectId -ne $marker.projectId -or $verify.referenceId -ne $marker.referenceId -or
        $verify.processEpoch -eq $marker.processEpoch -or $verify.processId -eq $marker.processId) { throw 'VERIFY_NEW_PROCESS_OR_PERSISTED_IDENTITY_UNPROVEN' }
    $summary.new_process_epoch = 'PASS'
    $summary.persisted_project_reference_reopen = 'PASS'
    $summary.verify_pid = $verify.processId
    $summary.result = 'PASS'
} catch {
    $summary.failure = $_.Exception.Message
    $summary.result = 'FAIL'
} finally {
    if ($prepareAttempted) {
        $cleanupArguments = @()
        if ($marker.projectId) { $cleanupArguments += @('-e','t12RestartProjectId',$marker.projectId) }
        try {
            [void](Invoke-T12Phase 'cleanupDeletesOnlyUniqueSyntheticProject' 'cleanup' (@('-e','t12RestartRun',$runId) + $cleanupArguments))
            $summary.exact_project_cleanup = 'PASS'
        } catch {
            $summary.exact_project_cleanup = 'FAIL'
            $summary.cleanup_failure = $_.Exception.Message
            $summary.result = 'BLOCKED'
        }
    }
    if ($markerPath -and (Test-Path -LiteralPath $markerPath)) {
        if ($summary.exact_project_cleanup -eq 'PASS') {
            try { Remove-Item -LiteralPath $markerPath -ErrorAction Stop; $summary.host_marker_cleanup = 'PASS' }
            catch { $summary.host_marker_cleanup = 'FAIL'; $summary.result = 'BLOCKED' }
        } else {
            $summary.host_marker_cleanup = 'RETAINED_FOR_EXACT_RECOVERY'
            $summary.result = 'BLOCKED'
        }
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'process-restart-summary.json') -Encoding UTF8
    Write-Output ($summary | ConvertTo-Json -Compress -Depth 5)
}
if ($summary.result -ne 'PASS') { exit 1 }
