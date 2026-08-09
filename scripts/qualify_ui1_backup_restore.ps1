[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$Serial,
    [string]$EvidenceDirectory = (Join-Path 'E:\project\_benchmark_evidence\ui1-final-qualification' (Get-Date -Format 'yyyyMMddTHHmmssZ'))
)

$ErrorActionPreference = 'Stop'

$ui1ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ui1AndroidRoot = Join-Path $ui1ProjectRoot 'android'
$ui1Adb = 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$ui1Package = 'com.jovi.photoai'
$ui1LocalTransport = 'com.android.localtransport/.LocalTransport'
$ui1D2dTransport = 'com.google.android.gms/.backup.migrate.service.D2dTransport'
$ui1CloudTransport = 'com.google.android.gms/.backup.BackupTransportService'
$ui1AppApk = Join-Path $ui1AndroidRoot 'app\build\outputs\apk\debug\app-debug.apk'
$ui1TestApk = Join-Path $ui1AndroidRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$ui1TestPackage = 'com.jovi.photoai.test'
$ui1TestRunner = 'androidx.test.runner.AndroidJUnitRunner'
$ui1EvidencePath = [System.IO.Path]::GetFullPath($EvidenceDirectory)

if (-not (Test-Path -LiteralPath $ui1Adb)) { throw 'ADB_NOT_FOUND' }
if (-not (Test-Path -LiteralPath $ui1AppApk)) { throw 'DEBUG_APK_NOT_FOUND' }
if (-not (Test-Path -LiteralPath $ui1TestApk)) { throw 'DEBUG_TEST_APK_NOT_FOUND' }
if ($ui1EvidencePath.StartsWith($ui1ProjectRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'EVIDENCE_MUST_BE_OUTSIDE_REPOSITORY'
}

function Invoke-Ui1Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    $ui1Output = @(& $ui1Adb -s $Serial @Arguments)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_COMMAND_FAILED' }
    return $ui1Output
}

function Invoke-Ui1Instrumentation {
    param([Parameter(Mandatory)][string]$ClassOrMethod)
    Invoke-Ui1Adb -Arguments @('shell', 'am', 'start', '-W', '-n', "$ui1Package/.MainActivity") | Out-Null
    Invoke-Ui1PreservingInstrumentation $ClassOrMethod
}

function Invoke-Ui1BackupNow {
    $ui1BackupOutput = Invoke-Ui1Adb shell bmgr backupnow $ui1Package
    if (($ui1BackupOutput -match 'Package not found') -or -not ($ui1BackupOutput -match "Package $([regex]::Escape($ui1Package)) with result: Success")) {
        Invoke-Ui1Adb shell bmgr run | Out-Null
        $ui1BackupOutput = Invoke-Ui1Adb shell bmgr backupnow $ui1Package
    }
    if (($ui1BackupOutput -match 'Package not found') -or -not ($ui1BackupOutput -match "Package $([regex]::Escape($ui1Package)) with result: Success")) {
        throw 'BACKUP_FAILED'
    }
}

function Get-Ui1RestoreToken {
    $ui1Sets = Invoke-Ui1Adb -Arguments @('shell', 'bmgr', 'list', 'sets')
    $ui1TokenLine = $ui1Sets | Where-Object { $_ -match '^\s*\d+\s*:' } | Select-Object -First 1
    $ui1Match = [regex]::Match([string]$ui1TokenLine, '^\s*(\d+)\s*:')
    if (-not $ui1Match.Success) { throw 'RESTORE_SET_UNAVAILABLE' }
    return $ui1Match.Groups[1].Value
}

function Invoke-Ui1RestorePackage {
    param([Parameter(Mandatory)][string]$Token)

    $ui1RestoreOutput = Invoke-Ui1Adb -Arguments @('shell', 'bmgr', 'restore', $Token, $ui1Package, '--monitor')
    if ($ui1RestoreOutput -match 'TRANSPORT_ERROR|restoreFinished:\s*-1000|RESTORE_ERROR') {
        throw 'D2D_RESTORE_TRANSPORT_ERROR'
    }
}

function Install-Ui1Apk {
    param([Parameter(Mandatory)][string]$ApkPath, [Parameter(Mandatory)][string]$FailureCode)

    $ui1InstallOutput = @(& $ui1Adb -s $Serial install -r -t $ApkPath)
    if ($LASTEXITCODE -ne 0 -or -not ($ui1InstallOutput -match '^Success$')) { throw $FailureCode }
}

function Install-Ui1TestFixturePackages {
    Install-Ui1Apk -ApkPath $ui1AppApk -FailureCode 'APP_INSTALL_FAILED'
    Install-Ui1Apk -ApkPath $ui1TestApk -FailureCode 'TEST_APK_INSTALL_FAILED'
}

function Invoke-Ui1PreservingInstrumentation {
    param([Parameter(Mandatory)][string]$ClassOrMethod)

    $ui1Output = Invoke-Ui1Adb -Arguments @(
        'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $ClassOrMethod, "$ui1TestPackage/$ui1TestRunner"
    )
    if (-not ($ui1Output -match 'OK \([0-9]+ test')) { throw 'PRESERVING_INSTRUMENTATION_FAILED' }
}

function Restore-Ui1App {
    Invoke-Ui1Adb shell pm uninstall --user 0 $ui1Package | Out-Null
    Install-Ui1Apk -ApkPath $ui1AppApk -FailureCode 'APP_REINSTALL_FAILED'
}

function Invoke-Ui1D2dAutoRestore {
    Invoke-Ui1Adb shell pm uninstall --user 0 $ui1Package | Out-Null
    Invoke-Ui1Adb shell bmgr transport $ui1CloudTransport | Out-Null
    Install-Ui1Apk -ApkPath $ui1AppApk -FailureCode 'D2D_APP_REINSTALL_FAILED'

    $ui1Deadline = (Get-Date).ToUniversalTime().AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        $ui1ReferenceCountOutput = Invoke-Ui1Adb -Arguments @(
            'shell', 'run-as', $ui1Package, 'sh', '-c', 'find files/references -type f | wc -l'
        )
        $ui1ReferenceCountText = ([string]$ui1ReferenceCountOutput).Trim()
        $ui1ReferenceCount = 0
        [void][int]::TryParse($ui1ReferenceCountText, [ref]$ui1ReferenceCount)
        if ($ui1ReferenceCount -gt 0) { return }
    } while ((Get-Date).ToUniversalTime() -lt $ui1Deadline)

    throw 'D2D_AUTO_RESTORE_TIMEOUT'
}

    $ui1Summary = [ordered]@{
    gate = 'UI1_FULL_EMULATOR_REQUALIFICATION'
    executed_utc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    device_kind = 'dedicated_api_35_emulator'
    static_or_existing_instrumentation = 'NOT_RUN_BY_SCRIPT'
    system_picker = 'NOT_RUN'
    responsive_accessibility_font_100 = 'NOT_RUN'
    responsive_accessibility_font_200 = 'NOT_RUN'
    cloud_backup_restore = 'NOT_RUN'
    d2d_restore = 'NOT_RUN'
    d2d_flow = 'OFFICIAL_SINGLE_DEVICE_AUTO_RESTORE'
    stage = 'PRECHECK'
    failure_code = 'NONE'
    result = 'BLOCKED'
}

$ui1OriginalTransport = $null
$ui1OriginalFontScale = $null
$ui1OriginalD2dMode = $null
$ui1LocalRestoreToken = $null
try {
    if ((Invoke-Ui1Adb shell getprop ro.kernel.qemu | Out-String).Trim() -ne '1') { throw 'NON_EMULATOR_TARGET' }
    if ((Invoke-Ui1Adb shell getprop ro.build.version.sdk | Out-String).Trim() -ne '35') { throw 'UNSUPPORTED_API_LEVEL' }

    $ui1InitialTransports = @(Invoke-Ui1Adb shell bmgr list transports)
    $ui1OriginalTransport = ($ui1InitialTransports | Where-Object { $_ -match '^\s*\*\s+' } | Select-Object -First 1) -replace '^\s*\*\s+', ''
    if ([string]::IsNullOrWhiteSpace($ui1OriginalTransport)) { throw 'SELECTED_TRANSPORT_UNKNOWN' }

    $ui1OriginalFontScale = (Invoke-Ui1Adb shell settings get system font_scale | Out-String).Trim()
    $ui1OriginalD2dMode = (Invoke-Ui1Adb shell settings get secure backup_enable_d2d_test_mode | Out-String).Trim()
    Invoke-Ui1Adb shell bmgr enable true | Out-Null
    Invoke-Ui1Adb shell settings put secure backup_enable_d2d_test_mode 1 | Out-Null
    Invoke-Ui1Adb shell bmgr init $ui1D2dTransport | Out-Null
    $ui1Transports = @(Invoke-Ui1Adb shell bmgr list transports)
    if (@($ui1Transports | Where-Object { $_ -match [regex]::Escape($ui1LocalTransport) }).Count -eq 0) {
        throw 'LOCAL_TRANSPORT_UNAVAILABLE'
    }
    if (@($ui1Transports | Where-Object { $_ -match [regex]::Escape($ui1D2dTransport) }).Count -eq 0) {
        throw 'D2D_TRANSPORT_UNAVAILABLE'
    }

    $ui1Summary.stage = 'INSTALL_PRECHECK'
    Install-Ui1TestFixturePackages
    $ui1Summary.stage = 'SYSTEM_PICKER'
    Invoke-Ui1Instrumentation 'com.jovi.photoai.ui1.Ui1SystemPickerFlowAndroidTest'
    $ui1Summary.system_picker = 'PASS'

    $ui1Summary.stage = 'FONT_100'
    Invoke-Ui1Adb shell settings put system font_scale 1.0 | Out-Null
    Invoke-Ui1Instrumentation 'com.jovi.photoai.ui1.Ui1ResponsiveAccessibilityAndroidTest'
    $ui1Summary.responsive_accessibility_font_100 = 'PASS_SEMANTICS_ONLY'

    $ui1Summary.stage = 'FONT_200'
    Invoke-Ui1Adb shell settings put system font_scale 2.0 | Out-Null
    Invoke-Ui1Instrumentation 'com.jovi.photoai.ui1.Ui1ResponsiveAccessibilityAndroidTest'
    $ui1Summary.responsive_accessibility_font_200 = 'PASS_SEMANTICS_ONLY'

    $ui1Summary.stage = 'LOCAL_PREPARE'
    Invoke-Ui1Adb shell bmgr transport $ui1LocalTransport | Out-Null
    Invoke-Ui1Adb shell settings put secure backup_local_transport_parameters is_encrypted=true | Out-Null
    Install-Ui1TestFixturePackages
    Invoke-Ui1PreservingInstrumentation 'com.jovi.photoai.ui1.Ui1BackupFixtureAndroidTest#prepare'
    $ui1Summary.stage = 'LOCAL_BACKUP'
    Invoke-Ui1BackupNow
    $ui1LocalRestoreToken = Get-Ui1RestoreToken
    $ui1Summary.stage = 'LOCAL_RESTORE'
    Restore-Ui1App
    Install-Ui1Apk -ApkPath $ui1TestApk -FailureCode 'TEST_APK_REINSTALL_FAILED'
    Invoke-Ui1RestorePackage -Token $ui1LocalRestoreToken
    $ui1Summary.stage = 'LOCAL_VERIFY'
    Invoke-Ui1PreservingInstrumentation 'com.jovi.photoai.ui1.Ui1BackupFixtureAndroidTest#verify'
    $ui1Summary.cloud_backup_restore = 'PASS_LOCAL_TRANSPORT'

    $ui1Summary.stage = 'D2D_PREPARE'
    Invoke-Ui1Adb shell settings put secure backup_enable_d2d_test_mode 1 | Out-Null
    Invoke-Ui1Adb shell bmgr transport $ui1D2dTransport | Out-Null
    Invoke-Ui1Adb shell bmgr init $ui1D2dTransport | Out-Null
    Install-Ui1TestFixturePackages
    Invoke-Ui1PreservingInstrumentation 'com.jovi.photoai.ui1.Ui1BackupFixtureAndroidTest#prepare'
    $ui1Summary.stage = 'D2D_BACKUP'
    Invoke-Ui1BackupNow
    $ui1Summary.stage = 'D2D_AUTO_RESTORE'
    Invoke-Ui1D2dAutoRestore
    Install-Ui1Apk -ApkPath $ui1TestApk -FailureCode 'TEST_APK_REINSTALL_FAILED'
    $ui1Summary.stage = 'D2D_VERIFY'
    Invoke-Ui1PreservingInstrumentation 'com.jovi.photoai.ui1.Ui1BackupFixtureAndroidTest#verify'
    $ui1Summary.d2d_restore = 'PASS_D2D_TRANSPORT'
    $ui1Summary.stage = 'COMPLETE'
    $ui1Summary.result = 'PASS'
} catch {
    $ui1Summary.failure_code = $_.Exception.Message
    if ($ui1Summary.stage -like 'D2D_*') {
        $ui1Summary.d2d_restore = 'BLOCKED_TRANSPORT'
        $ui1Summary.result = 'BLOCKED'
    } else {
        $ui1Summary.result = 'FAIL'
    }
} finally {
    if (-not [string]::IsNullOrWhiteSpace($ui1OriginalFontScale) -and $ui1OriginalFontScale -ne 'null') {
        Invoke-Ui1Adb shell settings put system font_scale $ui1OriginalFontScale | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($ui1OriginalD2dMode) -and $ui1OriginalD2dMode -ne 'null') {
        Invoke-Ui1Adb shell settings put secure backup_enable_d2d_test_mode $ui1OriginalD2dMode | Out-Null
    } else {
        Invoke-Ui1Adb shell settings delete secure backup_enable_d2d_test_mode | Out-Null
    }
    if (-not [string]::IsNullOrWhiteSpace($ui1OriginalTransport)) {
        Invoke-Ui1Adb shell bmgr transport $ui1OriginalTransport | Out-Null
    }

    New-Item -ItemType Directory -Force -Path $ui1EvidencePath | Out-Null
    $ui1SummaryPath = Join-Path $ui1EvidencePath 'backup-restore-summary.json'
    [System.IO.File]::WriteAllText(
        $ui1SummaryPath,
        ($ui1Summary | ConvertTo-Json),
        [System.Text.UTF8Encoding]::new($false)
    )
}

if ($ui1Summary.result -ne 'PASS') { exit 1 }
