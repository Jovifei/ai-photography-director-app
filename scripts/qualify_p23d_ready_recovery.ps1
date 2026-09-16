[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$Serial,
    [Parameter(Mandatory)]
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$androidRoot = Join-Path $projectRoot 'android'
$package = 'com.jovi.photoai'
$testPackage = 'com.jovi.photoai.test'
$runner = 'androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.jovi.photoai.p23d.P23DReadyStateProcessDeathAndroidTest'
$appApk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $androidRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$evidence = [System.IO.Path]::GetFullPath($EvidenceDirectory)

$adbCandidates = @()
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $adbCandidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')
}
if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $adbCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
}
$adb = $adbCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($adb)) { throw 'ADB_NOT_FOUND' }
if (-not (Test-Path -LiteralPath $appApk)) { throw 'DEBUG_APK_NOT_FOUND' }
if (-not (Test-Path -LiteralPath $testApk)) { throw 'DEBUG_TEST_APK_NOT_FOUND' }
if ($evidence.StartsWith($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY'
}
if (Test-Path -LiteralPath $evidence) { throw 'EVIDENCE_DIRECTORY_EXISTS' }
New-Item -ItemType Directory -Path $evidence | Out-Null

function Invoke-P23dAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = @(& $adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "ADB_COMMAND_FAILED: $($Arguments -join ' ')" }
    return $output
}

function Install-P23dApk {
    param([Parameter(Mandatory)][string]$Path)
    $output = @(& $adb -s $Serial install -r -t $Path 2>&1)
    if ($LASTEXITCODE -ne 0 -or -not ($output -match '^Success$')) { throw 'APK_INSTALL_FAILED' }
}

function Invoke-P23dTest {
    param([Parameter(Mandatory)][string]$Method, [Parameter(Mandatory)][string]$LogName)
    $target = "$testClass#$Method"
    $output = Invoke-P23dAdb -Arguments @(
        'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $target, "$testPackage/$runner"
    )
    $text = [string]::Join("`n", @($output))
    Set-Content -LiteralPath (Join-Path $evidence $LogName) -Value $text -Encoding UTF8
    if ($text -match 'FAILURES!!!|There was [0-9]+ failure') { throw "INSTRUMENTATION_FAILED: $Method" }
    if ($text -notmatch 'OK \(1 test\)') { throw "INSTRUMENTATION_RESULT_UNPROVEN: $Method" }
}

function Wait-P23dPid {
    param([bool]$ShouldExist)
    $deadline = (Get-Date).ToUniversalTime().AddSeconds(15)
    do {
        $pidText = ([string](Invoke-P23dAdb -Arguments @('shell', 'pidof', $package))).Trim()
        if ($ShouldExist -and -not [string]::IsNullOrWhiteSpace($pidText)) { return }
        if (-not $ShouldExist -and [string]::IsNullOrWhiteSpace($pidText)) { return }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    if ($ShouldExist) { throw 'APP_PROCESS_NOT_RUNNING' }
    throw 'APP_PROCESS_STILL_RUNNING_AFTER_FORCE_STOP'
}

$summary = [ordered]@{
    gate = 'P23D_READY_PROVIDER_SUMMARY_OS_PROCESS_RECOVERY'
    executed_utc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    device_kind = 'dedicated_api_35_emulator'
    prepare = 'NOT_RUN'
    app_process_started = 'NOT_RUN'
    force_stop = 'NOT_RUN'
    verify_after_restart = 'NOT_RUN'
    cleanup = 'NOT_RUN'
    result = 'BLOCKED'
    failure_code = 'NONE'
}

try {
    if ((Invoke-P23dAdb -Arguments @('shell', 'getprop', 'ro.kernel.qemu') | Out-String).Trim() -ne '1') {
        throw 'NON_EMULATOR_TARGET'
    }
    if ((Invoke-P23dAdb -Arguments @('shell', 'getprop', 'ro.build.version.sdk') | Out-String).Trim() -ne '35') {
        throw 'UNSUPPORTED_API_LEVEL'
    }
    Install-P23dApk $appApk
    Install-P23dApk $testApk

    Invoke-P23dTest -Method 'cleanupDurableReadyFixture' -LogName '00-cleanup-before.log'
    Invoke-P23dTest -Method 'prepareProviderReadySummaryState_forExternalForceStop' -LogName '01-prepare.log'
    $summary.prepare = 'PASS'

    Invoke-P23dAdb -Arguments @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity") | Out-Null
    Wait-P23dPid -ShouldExist $true
    $summary.app_process_started = 'PASS'

    Invoke-P23dAdb -Arguments @('shell', 'am', 'force-stop', $package) | Out-Null
    Wait-P23dPid -ShouldExist $false
    $summary.force_stop = 'PASS_OS_PROCESS_TERMINATED'

    Invoke-P23dTest -Method 'verifyProviderReadySummaryState_afterExternalOsProcessRestart' -LogName '02-verify.log'
    $summary.verify_after_restart = 'PASS'
    $summary.result = 'PASS'
} catch {
    $summary.failure_code = $_.Exception.Message
    throw
} finally {
    try {
        Invoke-P23dTest -Method 'cleanupDurableReadyFixture' -LogName '99-cleanup-after.log'
        $summary.cleanup = 'PASS'
    } catch {
        $summary.cleanup = 'FAILED'
        if ($summary.failure_code -eq 'NONE') { $summary.failure_code = $_.Exception.Message }
        $summary.result = 'BLOCKED'
    }
    ($summary | ConvertTo-Json -Depth 5) | Set-Content -LiteralPath (Join-Path $evidence 'p23d-ready-recovery-summary.json') -Encoding UTF8
}

Write-Output ($summary | ConvertTo-Json -Compress)
if ($summary.result -ne 'PASS') { exit 2 }
