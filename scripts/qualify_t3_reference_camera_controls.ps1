[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^emulator-\d+$')][string]$Serial,
    [Parameter(Mandatory = $true)][string]$EvidenceDir
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$android = Join-Path $repo 'android'
$evidenceParent = (Resolve-Path -LiteralPath $EvidenceDir).Path
if ($evidenceParent.Equals($repo, [StringComparison]::OrdinalIgnoreCase) -or
    $evidenceParent.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'T3_EVIDENCE_ROOT_INSIDE_REPOSITORY'
}
$binding = Get-Content -Raw (Join-Path $repo 'PROJECT_BINDING.json') | ConvertFrom-Json
$origin = ((git -C $repo remote get-url origin) -join '').Trim()
if ($origin -ne $binding.remote_url) { throw 'T3_REMOTE_MISMATCH' }
if (@(git -C $repo status --porcelain).Count -ne 0) { throw 'T3_SOURCE_WORKTREE_NOT_CLEAN' }
$sourceSha = ((git -C $repo rev-parse HEAD) -join '').Trim()
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'T3_ADB_NOT_FOUND' }
$boot = ((& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -join '').Trim()
$sdk = ((& $adb -s $Serial shell getprop ro.build.version.sdk 2>$null) -join '').Trim()
$qemu = ((& $adb -s $Serial shell getprop ro.kernel.qemu 2>$null) -join '').Trim()
if ($boot -ne '1' -or $sdk -ne '35' -or $qemu -ne '1') { throw 'T3_API35_EMULATOR_REQUIRED' }

$runId = [guid]::NewGuid().ToString('N')
$runRoot = (New-Item -ItemType Directory -Path (Join-Path $evidenceParent $runId)).FullName
$runner = 'com.jovi.photoai.test/androidx.test.runner.AndroidJUnitRunner'

function Invoke-Test([string]$label, [string]$class, [int]$expected, [string[]]$extra = @()) {
    & $adb -s $Serial shell am force-stop com.jovi.photoai.test | Out-Null
    Start-Sleep -Seconds 2
    $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $class) + $extra + @($runner)
    $lines = @(& $adb -s $Serial @arguments 2>&1 | ForEach-Object { [string]$_ })
    $exit = $LASTEXITCODE
    $log = Join-Path $runRoot "$label.log"
    $lines | Set-Content -LiteralPath $log -Encoding utf8
    $passed = @($lines | Where-Object { $_ -eq 'INSTRUMENTATION_STATUS_CODE: 0' }).Count
    $failed = @($lines | Where-Object { $_ -match '^INSTRUMENTATION_STATUS_CODE: (-2|-4)$' }).Count
    $complete = @($lines | Where-Object { $_ -match "^OK \($expected tests?\)$" }).Count
    if ($exit -ne 0 -or $passed -ne $expected -or $failed -ne 0 -or $complete -ne 1) {
        throw "T3_INSTRUMENTATION_FAILED: $label ($passed/$expected); log=$log"
    }
    Write-Output "PASS $label $passed/$expected"
}

Write-Output "START T3 source=$sourceSha device=$Serial evidence=$runRoot"
$gradle = Join-Path $android 'gradlew.bat'
Push-Location $android
try {
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    & $gradle ':app:testDebugUnitTest' ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':app:lintDebug' '--rerun-tasks' *> (Join-Path $runRoot 'build.log')
    if ($LASTEXITCODE -ne 0) { throw 'T3_BUILD_OR_LINT_FAILED' }
} finally { Pop-Location }

$apk = Join-Path $android 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $android 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($package in @($apk, $testApk)) {
    & $adb -s $Serial install -r $package | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'T3_EMULATOR_INSTALL_FAILED' }
}

Invoke-Test 't3-ui' 'com.jovi.photoai.t3.T3CameraReferenceAndroidTest' 7 @('-e', 't3DedicatedEmulator', 'true')
Invoke-Test 'director-root' 'com.jovi.photoai.phase1.ReferenceDirectorFlowAndroidTest' 2
Invoke-Test 'p25u-camera' 'com.jovi.photoai.p25u.P25UCameraCaptureAndroidTest' 1 @('-e', 'p25uDedicatedEmulator', 'true')
Invoke-Test 'p25u-export' 'com.jovi.photoai.p25u.P25UCaptureExportAndroidTest' 3

$recoveryArgs = @('-e', 'p25uRun', $runId, '-e', 'p25uDedicatedEmulator', 'true')
$recoveryFailure = $null
try {
    Invoke-Test 'recovery-prepare' 'com.jovi.photoai.p25u.P25UDefaultAppCaptureRecoveryAndroidTest#prepare' 1 ($recoveryArgs + @('-e', 'p25uPhase', 'prepare'))
    $marker = "/data/user/0/com.jovi.photoai/no_backup/p25u-default-recovery-$runId.txt"
    $captureId = ((& $adb -s $Serial shell run-as com.jovi.photoai cat $marker 2>$null) -join '').Trim()
    if ($captureId -notmatch '^[a-f0-9]{32}$') { throw 'T3_RECOVERY_MARKER_INVALID' }
    & $adb -s $Serial shell am start -W -n com.jovi.photoai/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'T3_APP_START_FAILED' }
    Start-Sleep -Seconds 2
    $pidBefore = ((& $adb -s $Serial shell pidof com.jovi.photoai 2>$null) -join ' ').Trim()
    & $adb -s $Serial shell am force-stop com.jovi.photoai
    if ($LASTEXITCODE -ne 0) { throw 'T3_FORCE_STOP_FAILED' }
    Start-Sleep -Seconds 2
    $pidAfter = ((& $adb -s $Serial shell pidof com.jovi.photoai 2>$null) -join ' ').Trim()
    $recovery = [ordered]@{ runId = $runId; captureId = $captureId; pidBefore = $pidBefore; pidAfter = $pidAfter }
    $recovery | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runRoot 'recovery.json') -Encoding utf8
    if ([string]::IsNullOrWhiteSpace($pidBefore) -or -not [string]::IsNullOrWhiteSpace($pidAfter)) { throw 'T3_FORCE_STOP_NOT_PROVEN' }
    Invoke-Test 'recovery-verify' 'com.jovi.photoai.p25u.P25UDefaultAppCaptureVerifyAndroidTest#verifyAfterExternalForceStop' 1 ($recoveryArgs + @('-e', 'p25uPhase', 'verify'))
} catch { $recoveryFailure = $_ } finally {
    try {
        Invoke-Test 'recovery-cleanup' 'com.jovi.photoai.p25u.P25UDefaultAppCaptureRecoveryAndroidTest#cleanup' 1 ($recoveryArgs + @('-e', 'p25uPhase', 'cleanup'))
    } catch {
        if ($null -eq $recoveryFailure) { $recoveryFailure = $_ }
        else { Write-Warning "T3_RECOVERY_CLEANUP_FAILED: $_" }
    }
}
if ($null -ne $recoveryFailure) { throw $recoveryFailure }

$artifactHashes = @(Get-ChildItem -LiteralPath $runRoot -File | ForEach-Object {
    [ordered]@{ name = $_.Name; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant() }
})
[ordered]@{
    status = 'T3_EMULATOR_VALIDATED_AWAITING_INDEPENDENT_REVIEW'
    sourceCommit = $sourceSha
    device = [ordered]@{ serial = $Serial; sdk = $sdk; qemu = $qemu }
    debugApkSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
    recovery = $recovery
    artifacts = $artifactHashes
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runRoot 'qualification.json') -Encoding utf8
Write-Output "PASS T3_EMULATOR_VALIDATED source=$sourceSha evidence=$runRoot"
