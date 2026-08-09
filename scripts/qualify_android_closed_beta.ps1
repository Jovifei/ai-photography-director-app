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

foreach ($name in @('PHOTOAI_RELEASE_STORE_FILE', 'PHOTOAI_RELEASE_STORE_PASSWORD', 'PHOTOAI_RELEASE_KEY_ALIAS', 'PHOTOAI_RELEASE_KEY_PASSWORD')) {
    Require-Env $name
}

$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
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

$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending | Where-Object { Test-Path (Join-Path $_.FullName 'apksigner.bat') } | Select-Object -First 1
if ($null -eq $buildTools) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: apksigner unavailable' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$apkVerification = @(& $apksigner verify --verbose --print-certs $apk 2>&1)
if ($LASTEXITCODE -ne 0) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: apksigner verification failed' }
$jarsigner = Join-Path $env:JAVA_HOME 'bin\jarsigner.exe'
if (-not (Test-Path -LiteralPath $jarsigner)) { $jarsigner = 'jarsigner.exe' }
$aabVerification = @(& $jarsigner -verify -strict $aab 2>&1)
if ($LASTEXITCODE -ne 0) { Stop-Beta 'P20_BLOCKED_RELEASE_SIGNATURE: jarsigner verification failed' }

$fingerprint = $apkVerification | Where-Object { $_ -match 'certificate SHA-256 digest' } | ForEach-Object { ($_ -split ': ', 2)[-1].Trim() } | Select-Object -First 1
$summary = [ordered]@{
    status = 'PASS'
    source_sha = $sourceSha
    package = 'com.jovi.photoai'
    version_name = '0.2.0-beta.1'
    version_code = 2
    emulator = 'API35 emulator validated; serial intentionally omitted'
    apk = [ordered]@{ name = [IO.Path]::GetFileName($apk); size_bytes = (Get-Item $apk).Length; sha256 = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant() }
    aab = [ordered]@{ name = [IO.Path]::GetFileName($aab); size_bytes = (Get-Item $aab).Length; sha256 = (Get-FileHash $aab -Algorithm SHA256).Hash.ToLowerInvariant() }
    signing_certificate_sha256 = $fingerprint
    scope = 'synthetic-only smoke and release artifact verification; no user media or device identifiers'
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'qualification-summary.json') -Encoding UTF8
Write-Output 'P20_BETA_ARTIFACT_QUALIFICATION_PASS'
