[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$IdentityRoot,
    [Parameter(Mandatory = $true)]
    [string]$RecoveryRoot,
    [string]$PackageName = 'com.jovi.photoai',
    [string]$Alias = 'photo-director-release-v1'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$expectedPackage = 'com.jovi.photoai'
$expectedAlias = 'photo-director-release-v1'
$role = 'direct-distribution-app-signing'
$validityDays = 10950
$subject = 'CN=Photography Director Android Release,O=Jovi PhotoAI,C=CN'

function Stop-Bootstrap([string]$Code) {
    throw $Code
}

function Get-FullPath([string]$Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Assert-OutsideRepository([string]$Path, [string]$Name) {
    $repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\') + '\'
    $full = Get-FullPath $Path
    if ($full.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Bootstrap "P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: $Name must be outside repository"
    }
    return $full
}

function Get-Keytool {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: keytool unavailable'
}

function Invoke-Keytool([string]$Tool, [string[]]$Arguments, [string]$FailureCode) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Tool @Arguments 2>$null | Out-Null
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { Stop-Bootstrap $FailureCode }
}

function Get-Sha256Hex([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Bytes)) -replace '-', '').ToUpperInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Protect-Password([string]$Password) {
    Add-Type -AssemblyName System.Security
    $plain = [Text.Encoding]::UTF8.GetBytes($Password)
    try {
        $cipher = [Security.Cryptography.ProtectedData]::Protect(
            $plain,
            $null,
            [Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        return [Convert]::ToBase64String($cipher)
    } finally {
        [Array]::Clear($plain, 0, $plain.Length)
    }
}

function Set-PrivateAcl([string]$Path) {
    $identity = "$env:USERDOMAIN\$env:USERNAME"
    & icacls.exe $Path '/inheritance:r' '/grant:r' "${identity}:(OI)(CI)(F)" 'SYSTEM:(OI)(CI)(F)' | Out-Null
    if ($LASTEXITCODE -ne 0) { Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: ACL setup failed' }
}

if ($PackageName -ne $expectedPackage -or $Alias -ne $expectedAlias) {
    Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: frozen package or alias mismatch'
}

$identity = Assert-OutsideRepository $IdentityRoot 'identity root'
$recovery = Assert-OutsideRepository $RecoveryRoot 'recovery root'
$identityParent = Split-Path -Parent $identity
$recoveryParent = Split-Path -Parent $recovery
New-Item -ItemType Directory -Force -Path $identityParent, $recoveryParent | Out-Null

if (Test-Path -LiteralPath $identity) {
    Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_CONFLICT: identity root already exists'
}
if (Test-Path -LiteralPath $recovery) {
    Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_CONFLICT: recovery root already exists'
}

$tool = Get-Keytool
$nonce = [Guid]::NewGuid().ToString('N')
$tempIdentity = Join-Path $identityParent "identity-bootstrap-$nonce"
$tempRecovery = Join-Path $recoveryParent "recovery-bootstrap-$nonce"
$quarantine = Join-Path $identityParent "quarantine\$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'))-$nonce"
$password = $null
$passwordBytes = $null
$cert = $null

try {
    New-Item -ItemType Directory -Force -Path $tempIdentity, $tempRecovery | Out-Null
    Set-PrivateAcl $tempIdentity
    Set-PrivateAcl $tempRecovery

    $random = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($random) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($random)
    [Array]::Clear($random, 0, $random.Length)

    $store = Join-Path $tempIdentity 'photo-director-release-v1.p12'
    $certFile = Join-Path $tempIdentity 'certificate.der'
    $env:PHOTOAI_BOOTSTRAP_STORE_PASSWORD = $password
    $env:PHOTOAI_BOOTSTRAP_KEY_PASSWORD = $password

    Invoke-Keytool $tool @(
        '-genkeypair', '-keystore', $store, '-storetype', 'PKCS12', '-alias', $Alias,
        '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA',
        '-validity', $validityDays.ToString(), '-dname', $subject,
        '-storepass:env', 'PHOTOAI_BOOTSTRAP_STORE_PASSWORD',
        '-keypass:env', 'PHOTOAI_BOOTSTRAP_KEY_PASSWORD',
        '-J-Duser.language=en', '-J-Duser.country=US'
    ) 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: key generation failed'

    Invoke-Keytool $tool @(
        '-list', '-keystore', $store, '-storetype', 'PKCS12', '-alias', $Alias,
        '-storepass:env', 'PHOTOAI_BOOTSTRAP_STORE_PASSWORD',
        '-J-Duser.language=en', '-J-Duser.country=US'
    ) 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: key verification failed'

    Invoke-Keytool $tool @(
        '-exportcert', '-keystore', $store, '-storetype', 'PKCS12', '-alias', $Alias,
        '-file', $certFile, '-storepass:env', 'PHOTOAI_BOOTSTRAP_STORE_PASSWORD',
        '-J-Duser.language=en', '-J-Duser.country=US'
    ) 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: certificate export failed'

    $cert = New-Object Security.Cryptography.X509Certificates.X509Certificate2($certFile)
    $nowUtc = (Get-Date).ToUniversalTime()
    if ($cert.NotBefore.ToUniversalTime() -gt $nowUtc -or $cert.NotAfter.ToUniversalTime() -lt $nowUtc) {
        Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: certificate is not currently valid'
    }
    if ($cert.PublicKey.Key.KeySize -lt 4096) {
        Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: RSA key size is below 4096'
    }
    $certificateSha256 = Get-Sha256Hex $cert.RawData
    $keystoreSha256 = (Get-FileHash -LiteralPath $store -Algorithm SHA256).Hash.ToUpperInvariant()
    $encryptedPassword = Protect-Password $password
    $passwordBytes = [Text.Encoding]::UTF8.GetBytes($password)

    $public = [ordered]@{
        version = 1
        packageName = $PackageName
        keyAlias = $Alias
        role = $role
        storeType = 'PKCS12'
        keyAlgorithm = 'RSA'
        keySize = 4096
        signatureAlgorithm = 'SHA256withRSA'
        validityDays = $validityDays
        certificateSha256 = $certificateSha256
        keystoreSha256 = $keystoreSha256
        createdUtc = (Get-Date).ToUniversalTime().ToString('o')
    }
    $credentials = [ordered]@{
        version = 1
        scope = 'CurrentUser'
        algorithm = 'Windows DPAPI'
        ciphertextBase64 = $encryptedPassword
    }
    $public | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $tempIdentity 'public-identity.json') -Encoding UTF8
    $credentials | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $tempIdentity 'credentials.dpapi.json') -Encoding UTF8

    Copy-Item -LiteralPath $store -Destination (Join-Path $tempRecovery 'photo-director-release-v1.p12')
    Copy-Item -LiteralPath (Join-Path $tempIdentity 'public-identity.json') -Destination (Join-Path $tempRecovery 'public-identity.json')
    Copy-Item -LiteralPath (Join-Path $tempIdentity 'credentials.dpapi.json') -Destination (Join-Path $tempRecovery 'credentials.dpapi.json')
    Set-PrivateAcl $tempRecovery

    foreach ($name in @('photo-director-release-v1.p12', 'public-identity.json', 'credentials.dpapi.json')) {
        $left = (Get-FileHash -LiteralPath (Join-Path $tempIdentity $name) -Algorithm SHA256).Hash
        $right = (Get-FileHash -LiteralPath (Join-Path $tempRecovery $name) -Algorithm SHA256).Hash
        if ($left -ne $right) { Stop-Bootstrap 'P20_BLOCKED_SIGNING_IDENTITY_BOOTSTRAP: recovery hash mismatch' }
    }

    Remove-Item -LiteralPath $certFile -Force
    Move-Item -LiteralPath $tempIdentity -Destination $identity
    Move-Item -LiteralPath $tempRecovery -Destination $recovery
    Write-Output "P20_RELEASE_SIGNING_IDENTITY_READY certificate_sha256=$certificateSha256"
} catch {
    if (Test-Path -LiteralPath $tempIdentity) {
        New-Item -ItemType Directory -Force -Path $quarantine | Out-Null
        Move-Item -LiteralPath $tempIdentity -Destination (Join-Path $quarantine 'identity') -Force
    }
    if (Test-Path -LiteralPath $tempRecovery) {
        New-Item -ItemType Directory -Force -Path $quarantine | Out-Null
        Move-Item -LiteralPath $tempRecovery -Destination (Join-Path $quarantine 'recovery') -Force
    }
    throw
} finally {
    foreach ($name in @('PHOTOAI_BOOTSTRAP_STORE_PASSWORD', 'PHOTOAI_BOOTSTRAP_KEY_PASSWORD')) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
    if ($null -ne $passwordBytes) { [Array]::Clear($passwordBytes, 0, $passwordBytes.Length) }
    $password = $null
    $cert = $null
}
