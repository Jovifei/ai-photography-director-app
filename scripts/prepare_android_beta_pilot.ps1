[CmdletBinding()]
param(
    [string]$ArtifactPath,
    [string]$OutputRoot,
    [string]$ReleaseSourceSha,
    [string]$ExpectedApkSha256,
    [string]$ExpectedCertificateSha256
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$packageName = 'com.jovi.photoai'
$versionName = '0.2.0-beta.1'
$versionCode = '2'
$packageApkName = 'photo-director-0.2.0-beta.1-pilot.apk'

function Stop-Pilot([string]$code) { throw $code }

function Normalize-Fingerprint([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $null }
    $normalized = ($value -replace ':', '').Trim().ToUpperInvariant()
    if ($normalized -notmatch '^[0-9A-F]{64}$') { return $null }
    return $normalized
}

function Test-InputShape {
    if ([string]::IsNullOrWhiteSpace($ArtifactPath) -or [string]::IsNullOrWhiteSpace($OutputRoot)) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_INVALID_INPUT'
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseSourceSha) -or $ReleaseSourceSha -notmatch '^[0-9a-fA-F]{40}$') {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_INVALID_INPUT'
    }
    if ([string]::IsNullOrWhiteSpace($ExpectedApkSha256) -or $ExpectedApkSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_INVALID_INPUT'
    }
    if ($null -eq (Normalize-Fingerprint $ExpectedCertificateSha256)) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_INVALID_INPUT'
    }
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

function Get-AndroidSdkRoot {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'build-tools') -PathType Container) {
            return (Resolve-Path $candidate).Path
        }
    }
    Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_ANDROID_SDK'
}

function Get-BuildTool([string]$sdkRoot, [string]$name) {
    $tool = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory |
        Sort-Object Name -Descending |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName $name) -PathType Leaf } |
        Select-Object -First 1
    if ($null -eq $tool) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_BUILD_TOOLS' }
    return (Join-Path $tool.FullName $name)
}

function Test-ReleaseSourceBoundary {
    $headResult = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $repo, 'rev-parse', 'HEAD')
    $head = ([string]($headResult.Output | Select-Object -First 1)).Trim()
    if ($headResult.ExitCode -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_GIT_HEAD' }
    $statusResult = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $repo, 'status', '--porcelain=v1', '--untracked-files=all')
    if ($statusResult.ExitCode -ne 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_GIT_STATUS' }
    if (@($statusResult.Output | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -ne 0) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_CANDIDATE_DIRTY'
    }
    $ancestry = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $repo, 'merge-base', '--is-ancestor', $ReleaseSourceSha, 'HEAD')
    if ($ancestry.ExitCode -ne 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_RELEASE_SOURCE' }
    $diffResult = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $repo, 'diff', '--name-only', "$ReleaseSourceSha..HEAD")
    if ($diffResult.ExitCode -ne 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_GIT_DIFF' }
    $changedPaths = @($diffResult.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    foreach ($changedPath in $changedPaths) {
        $allowed = $changedPath.StartsWith('android/app/src/androidTest/') -or
            $changedPath.StartsWith('scripts/') -or
            $changedPath.StartsWith('docs/') -or
            $changedPath.StartsWith('reports/') -or
            $changedPath.StartsWith('tasks/')
        if (-not $allowed) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_PRODUCTION_DRIFT' }
    }
    return [pscustomobject]@{ Head = $head; ChangedPathCount = $changedPaths.Count }
}

function Test-ExternalOutputRoot {
    if (-not (Test-Path -LiteralPath $OutputRoot -PathType Container)) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_ROOT' }
    $outputRootPath = (Resolve-Path $OutputRoot).Path
    if (-not [string]::IsNullOrWhiteSpace($env:GIT_DIR) -or -not [string]::IsNullOrWhiteSpace($env:GIT_WORK_TREE) -or -not [string]::IsNullOrWhiteSpace($env:GIT_COMMON_DIR)) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_GIT_PROBE'
    }
    $directoryPath = $outputRootPath.TrimEnd('\', '/')
    while (-not [string]::IsNullOrWhiteSpace($directoryPath)) {
        $gitMarker = Join-Path $directoryPath '.git'
        $looksBare = (Test-Path -LiteralPath (Join-Path $directoryPath 'HEAD') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $directoryPath 'objects') -PathType Container) -and
            (Test-Path -LiteralPath (Join-Path $directoryPath 'refs') -PathType Container)
        if ((Test-Path -LiteralPath $gitMarker) -or $looksBare) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_NOT_EXTERNAL' }
        $parent = [IO.Path]::GetDirectoryName($directoryPath)
        if ([string]::IsNullOrWhiteSpace($parent) -or [string]::Equals($parent, $directoryPath, [StringComparison]::OrdinalIgnoreCase)) { break }
        $directoryPath = $parent
    }
    $gitContext = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $outputRootPath, 'rev-parse', '--is-inside-work-tree')
    if ($gitContext.ExitCode -eq 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_NOT_EXTERNAL' }
    $probeText = (($gitContext.Output | ForEach-Object { [string]$_ }) -join "`n")
    if ($gitContext.ExitCode -ne 128 -or $probeText -notmatch '(?i)(not a git repository|不是一个\s*git\s*仓库)') {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_GIT_PROBE'
    }
    return $outputRootPath
}

function Get-VerifiedApk([string]$sdkRoot) {
    if (-not (Test-Path -LiteralPath $ArtifactPath -PathType Leaf)) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_ARTIFACT_MISSING' }
    $expectedHash = $ExpectedApkSha256.ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $ArtifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_ARTIFACT_HASH' }

    $apksigner = Get-BuildTool $sdkRoot 'apksigner.bat'
    $verification = Invoke-Captured $apksigner @('verify', '--verbose', '--print-certs', $ArtifactPath)
    if ($verification.ExitCode -ne 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_APK_SIGNATURE' }
    $actualFingerprint = $null
    foreach ($line in $verification.Output) {
        $match = [regex]::Match([string]$line, 'certificate SHA-256 digest:\s*([0-9A-F:]+)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) { $actualFingerprint = Normalize-Fingerprint $match.Groups[1].Value; break }
    }
    $expectedFingerprint = Normalize-Fingerprint $ExpectedCertificateSha256
    if ($null -eq $actualFingerprint -or $null -eq $expectedFingerprint -or $actualFingerprint -ne $expectedFingerprint) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_APK_CERTIFICATE'
    }

    $aapt = Get-BuildTool $sdkRoot 'aapt.exe'
    $badging = Invoke-Captured $aapt @('dump', 'badging', $ArtifactPath)
    if ($badging.ExitCode -ne 0) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_APK_METADATA' }
    $packageLine = $badging.Output | Where-Object { [string]$_ -like 'package:*' } | Select-Object -First 1
    $packageMatch = [regex]::Match([string]$packageLine, "name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'")
    if (-not $packageMatch.Success -or $packageMatch.Groups[1].Value -ne $packageName -or $packageMatch.Groups[2].Value -ne $versionCode -or $packageMatch.Groups[3].Value -ne $versionName) {
        Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_APK_METADATA'
    }
    return [pscustomobject]@{
        Name = [IO.Path]::GetFileName($ArtifactPath)
        SizeBytes = (Get-Item -LiteralPath $ArtifactPath).Length
        Sha256 = $actualHash
        CertificateSha256 = $actualFingerprint
    }
}

function Publish-PilotPackage($outputRootPath, $sourceBoundary, $apk) {
    $published = Join-Path $outputRootPath 'pilot-package'
    if (Test-Path -LiteralPath $published) { Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_EXISTS' }
    $staging = Join-Path $outputRootPath ('.pilot-package-' + [Guid]::NewGuid().ToString('N'))
    $publishedSuccessfully = $false
    try {
        New-Item -ItemType Directory -Path $staging | Out-Null
        Copy-Item -LiteralPath $ArtifactPath -Destination (Join-Path $staging $packageApkName)
        Copy-Item -LiteralPath (Join-Path $repo 'docs\BETA_USER_GUIDE.md') -Destination (Join-Path $staging 'BETA_USER_GUIDE.md')
        Copy-Item -LiteralPath (Join-Path $repo 'docs\privacy-policy.md') -Destination (Join-Path $staging 'privacy-policy.md')
        Set-Content -LiteralPath (Join-Path $staging 'SHA256SUMS.txt') -Encoding ASCII -NoNewline -Value ($apk.Sha256 + '  ' + $packageApkName)
        $summary = [ordered]@{
            status = 'PASS'
            release_source_sha = $ReleaseSourceSha.ToLowerInvariant()
            candidate_head_sha = $sourceBoundary.Head
            allowed_delta_count = $sourceBoundary.ChangedPathCount
            package = $packageName
            version_name = $versionName
            version_code = [int]$versionCode
            apk = [ordered]@{ name = $packageApkName; size_bytes = $apk.SizeBytes; sha256 = $apk.Sha256 }
            signing_certificate_sha256 = $apk.CertificateSha256
            included_documents = @('BETA_USER_GUIDE.md', 'privacy-policy.md')
            scope = 'direct pilot package; no private media, device identifiers, LAN addresses, pairing material, or secrets'
        }
        $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $staging 'pilot-package-summary.json') -Encoding UTF8
        $expectedContents = @($packageApkName, 'SHA256SUMS.txt', 'BETA_USER_GUIDE.md', 'privacy-policy.md', 'pilot-package-summary.json') | Sort-Object
        $actualContents = @(Get-ChildItem -LiteralPath $staging -Force | ForEach-Object { $_.Name } | Sort-Object)
        if ((Compare-Object -ReferenceObject $expectedContents -DifferenceObject $actualContents).Count -ne 0) {
            Stop-Pilot 'P20_PILOT_PACKAGE_BLOCKED_PACKAGE_CONTENTS'
        }
        [IO.Directory]::Move($staging, $published)
        $publishedSuccessfully = $true
    } finally {
        if (-not $publishedSuccessfully -and (Test-Path -LiteralPath $staging)) {
            Remove-Item -LiteralPath $staging -Recurse -Force
        }
    }
}

try {
    Test-InputShape
    $outputRootPath = Test-ExternalOutputRoot
    $sourceBoundary = Test-ReleaseSourceBoundary
    $sdkRoot = Get-AndroidSdkRoot
    $apk = Get-VerifiedApk $sdkRoot
    Publish-PilotPackage $outputRootPath $sourceBoundary $apk
    Write-Output 'P20_PILOT_PACKAGE_PREPARATION_PASS'
    exit 0
} catch {
    $message = [string]$_.Exception.Message
    if ($message -notmatch '^P20_PILOT_PACKAGE_') { $message = 'P20_PILOT_PACKAGE_PREPARATION_FAILED' }
    [Console]::Error.WriteLine($message)
    exit 2
}
