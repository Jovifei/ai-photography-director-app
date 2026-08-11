[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-')]
    [string]$Serial,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDir
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$android = Join-Path $repo 'android'
$evidence = (New-Item -ItemType Directory -Force -Path $EvidenceDir).FullName
$artifacts = (New-Item -ItemType Directory -Force -Path (Join-Path $evidence 'artifacts')).FullName

function Stop-Beta([string]$code) { throw $code }
function Require-Env([string]$name) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        Stop-Beta "P20_BLOCKED_RELEASE_SIGNING_INPUT: missing $name"
    }
}
function Invoke-Checked([string]$label, [string]$file, [string[]]$arguments, [string]$workingDirectory) {
    Write-Output "START $label"
    Push-Location $workingDirectory
    try { & $file @arguments; $code = $LASTEXITCODE } finally { Pop-Location }
    if ($code -ne 0) { Stop-Beta "P20_BETA_QUALITY_GATE_FAILED: $label exit $code" }
    Write-Output "PASS $label"
}

function Get-AndroidSdkRoot {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Container) { return (Resolve-Path $candidate).Path }
    }
    Stop-Beta 'P20_BETA_RUNTIME_GATE_BLOCKED: Android SDK unavailable'
}

function Invoke-Captured([string]$file, [string[]]$arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $file @arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ Output = $output; ExitCode = $exitCode }
}

function Normalize-Fingerprint([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $null }
    $normalized = ($value -replace ':', '').Trim().ToUpperInvariant()
    if ($normalized -notmatch '^[0-9A-F]{64}$') { return $null }
    return $normalized
}

$identityFile = Join-Path $android 'release-signing-identity.properties'
if (-not (Test-Path -LiteralPath $identityFile -PathType Leaf)) {
    Stop-Beta 'P20_BLOCKED_SIGNING_IDENTITY_CONFLICT: public identity file unavailable'
}
$identity = ConvertFrom-StringData (Get-Content -LiteralPath $identityFile -Raw -Encoding UTF8)
$pinnedFingerprint = Normalize-Fingerprint $identity.certificateSha256
if ($null -eq $pinnedFingerprint -or $identity.packageName -ne 'com.jovi.photoai' -or $identity.role -ne 'direct-distribution-app-signing') {
    Stop-Beta 'P20_BLOCKED_SIGNING_IDENTITY_CONFLICT: public identity metadata invalid'
}

foreach ($name in @('PHOTOAI_RELEASE_STORE_FILE', 'PHOTOAI_RELEASE_STORE_PASSWORD', 'PHOTOAI_RELEASE_KEY_ALIAS', 'PHOTOAI_RELEASE_KEY_PASSWORD')) {
    Require-Env $name
}

$sdkRoot = Get-AndroidSdkRoot
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { Stop-Beta 'P20_BETA_RUNTIME_GATE_BLOCKED: adb unavailable' }
if ((& $adb -s $Serial get-state 2>$null) -ne 'device') { Stop-Beta 'P20_BETA_RUNTIME_GATE_BLOCKED: emulator is not ready' }
$sdk = ((& $adb -s $Serial shell getprop ro.build.version.sdk) -join '').Trim()
$qemu = ((& $adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim()
if ($sdk -ne '35' -or $qemu -ne '1') { Stop-Beta 'P20_BETA_RUNTIME_GATE_BLOCKED: API 35 emulator required' }

Invoke-Checked 'assembleRelease' (Join-Path $android 'gradlew.bat') @('assembleRelease') $android
Invoke-Checked 'bundleRelease' (Join-Path $android 'gradlew.bat') @('bundleRelease') $android

$sourceSha = ((git -C $repo rev-parse HEAD) -join '').Trim()
$shortSha = $sourceSha.Substring(0, 8)
$apkSource = Join-Path $android 'app\build\outputs\apk\release\app-release.apk'
$aabSource = Join-Path $android 'app\build\outputs\bundle\release\app-release.aab'
if (-not (Test-Path -LiteralPath $apkSource) -or -not (Test-Path -LiteralPath $aabSource)) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: release artifacts missing' }
$apk = Join-Path $artifacts "photo-director-0.2.0-beta.1-$shortSha.apk"
$aab = Join-Path $artifacts "photo-director-0.2.0-beta.1-$shortSha.aab"
Copy-Item -LiteralPath $apkSource -Destination $apk
Copy-Item -LiteralPath $aabSource -Destination $aab

$buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory | Sort-Object Name -Descending | Where-Object { Test-Path (Join-Path $_.FullName 'apksigner.bat') } | Select-Object -First 1
if ($null -eq $buildTools) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: apksigner unavailable' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$apkVerificationResult = Invoke-Captured $apksigner @('verify', '--verbose', '--print-certs', $apk)
if ($apkVerificationResult.ExitCode -ne 0) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: apksigner verification failed' }
$apkVerification = $apkVerificationResult.Output
$jarsigner = Join-Path $env:JAVA_HOME 'bin\jarsigner.exe'
if (-not (Test-Path -LiteralPath $jarsigner)) { $jarsigner = 'jarsigner.exe' }
$aabVerificationResult = Invoke-Captured $jarsigner @('-verify', '-verbose', '-certs', $aab)
if ($aabVerificationResult.ExitCode -ne 0) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: jarsigner verification failed' }
$aabVerification = $aabVerificationResult.Output
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { $keytool = 'keytool.exe' }
$aabCertificateResult = Invoke-Captured $keytool @('-printcert', '-jarfile', $aab, '-J-Duser.language=en', '-J-Duser.country=US')
if ($aabCertificateResult.ExitCode -ne 0) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: AAB certificate extraction failed' }

$apkFingerprint = $null
foreach ($line in $apkVerification) {
    $match = [regex]::Match([string]$line, 'certificate SHA-256 digest:\s*([0-9A-F:]+)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($match.Success) { $apkFingerprint = Normalize-Fingerprint $match.Groups[1].Value; break }
}
$aabFingerprint = $null
foreach ($line in $aabCertificateResult.Output) {
    $match = [regex]::Match([string]$line, '^\s*SHA256:\s*([0-9A-F:]+)\s*$', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($match.Success) { $aabFingerprint = Normalize-Fingerprint $match.Groups[1].Value; break }
}
if ($null -eq $apkFingerprint -or $null -eq $aabFingerprint) {
    Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: certificate fingerprint parsing returned empty'
}
if ($apkFingerprint -ne $pinnedFingerprint -or $aabFingerprint -ne $pinnedFingerprint) {
    Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: APK/AAB certificate fingerprint mismatch'
}
$summary = [ordered]@{
    status = 'PASS'
    source_sha = $sourceSha
    package = 'com.jovi.photoai'
    version_name = '0.2.0-beta.1'
    version_code = 2
    emulator = 'API35 emulator validated; serial intentionally omitted'
    apk = [ordered]@{ name = [IO.Path]::GetFileName($apk); size_bytes = (Get-Item $apk).Length; sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() }
    aab = [ordered]@{ name = [IO.Path]::GetFileName($aab); size_bytes = (Get-Item $aab).Length; sha256 = (Get-FileHash $aab -Algorithm SHA256).Hash.ToLowerInvariant() }
    signing_certificate_sha256 = $pinnedFingerprint
    apk_signing_certificate_sha256 = $apkFingerprint
    aab_signing_certificate_sha256 = $aabFingerprint
    android_build_tools = $buildTools.Name
    scope = 'synthetic-only smoke and release artifact verification; no user media or device identifiers'
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'qualification-summary.json') -Encoding UTF8
Write-Output 'P20_BETA_ARTIFACT_QUALIFICATION_PASS'
