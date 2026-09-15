[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$IdentityRoot,
    [Parameter(Mandatory = $true)]
    [ValidateSet('Verify', 'Build', 'Qualify')]
    [string]$Action,
    [string]$EvidenceDir,
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

function Stop-Identity([string]$Code) { throw $Code }
function Get-FullPath([string]$Path) { return [IO.Path]::GetFullPath($Path) }

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\') + '\'
$identity = Get-FullPath $IdentityRoot
if ($identity.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
    Stop-Identity 'P20_BLOCKED_RELEASE_SIGNING_INPUT: identity root must be outside repository'
}

$publicPath = Join-Path $identity 'public-identity.json'
$credentialsPath = Join-Path $identity 'credentials.dpapi.json'
$storePath = Join-Path $identity 'photo-director-release-v1.p12'
foreach ($path in @($publicPath, $credentialsPath, $storePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Stop-Identity 'P20_BLOCKED_RELEASE_SIGNING_INPUT: durable identity artifact is unavailable'
    }
}

Add-Type -AssemblyName System.Security
$public = Get-Content -LiteralPath $publicPath -Raw -Encoding UTF8 | ConvertFrom-Json
$credentials = Get-Content -LiteralPath $credentialsPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($public.packageName -ne 'com.jovi.photoai' -or $public.keyAlias -ne 'photo-director-release-v1') {
    Stop-Identity 'P20_BLOCKED_SIGNING_IDENTITY_CONFLICT: public identity does not match package contract'
}
if ($credentials.scope -ne 'CurrentUser' -or $credentials.algorithm -ne 'Windows DPAPI') {
    Stop-Identity 'P20_BLOCKED_RELEASE_SIGNING_INPUT: unsupported credential protection'
}

$cipher = [Convert]::FromBase64String([string]$credentials.ciphertextBase64)
$plainBytes = $null
$password = $null
$gradle = Join-Path $repo 'android\gradlew.bat'
$android = Join-Path $repo 'android'

try {
    $plainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $cipher,
        $null,
        [Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    $password = [Text.Encoding]::UTF8.GetString($plainBytes)
    $env:PHOTOAI_RELEASE_STORE_FILE = $storePath
    $env:PHOTOAI_RELEASE_STORE_PASSWORD = $password
    $env:PHOTOAI_RELEASE_KEY_ALIAS = [string]$public.keyAlias
    $env:PHOTOAI_RELEASE_KEY_PASSWORD = $password

    if ($Action -eq 'Verify') {
        Push-Location $android
        try { & $gradle 'verifyReleaseSigning'; $code = $LASTEXITCODE } finally { Pop-Location }
        if ($code -ne 0) { Stop-Identity 'P20_BLOCKED_RELEASE_SIGNING_INPUT: verifyReleaseSigning failed' }
        Write-Output 'P20_RELEASE_SIGNING_IDENTITY_VERIFY_PASS'
    } elseif ($Action -eq 'Build') {
        Push-Location $android
        try { & $gradle 'verifyReleaseSigning' 'assembleRelease' 'bundleRelease' 'lintRelease' '--rerun-tasks'; $code = $LASTEXITCODE } finally { Pop-Location }
        if ($code -ne 0) { Stop-Identity 'P20_BETA_QUALITY_GATE_FAILED: release build failed' }
        Write-Output 'P20_BETA_SIGNED_BUILD_PASS'
    } else {
        if ([string]::IsNullOrWhiteSpace($EvidenceDir) -or [string]::IsNullOrWhiteSpace($Serial)) {
            Stop-Identity 'P20_BETA_RUNTIME_GATE_BLOCKED: Qualify requires EvidenceDir and emulator Serial'
        }
        $qualifier = Join-Path $repo 'scripts\qualify_android_closed_beta.ps1'
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $qualifier -Serial $Serial -EvidenceDir $EvidenceDir
        if ($LASTEXITCODE -ne 0) { Stop-Identity 'P20_BETA_QUALITY_GATE_FAILED: qualification failed' }
        Write-Output 'P20_BETA_SIGNED_QUALIFICATION_PASS'
    }
} finally {
    foreach ($name in @('PHOTOAI_RELEASE_STORE_FILE', 'PHOTOAI_RELEASE_STORE_PASSWORD', 'PHOTOAI_RELEASE_KEY_ALIAS', 'PHOTOAI_RELEASE_KEY_PASSWORD')) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
    if ($null -ne $plainBytes) { [Array]::Clear($plainBytes, 0, $plainBytes.Length) }
    $password = $null
}
