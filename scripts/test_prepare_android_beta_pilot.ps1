$ErrorActionPreference = 'Stop'
$tool = Join-Path $PSScriptRoot 'prepare_android_beta_pilot.ps1'
$tokens = $null
$errors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile($tool, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_PARSER' }

function Invoke-PilotTool([string[]]$arguments, [string]$GitDirOverride = $null) {
    $previousPreference = $ErrorActionPreference
    $previousGitDir = $env:GIT_DIR
    try {
        if ($null -ne $GitDirOverride) { $env:GIT_DIR = $GitDirOverride }
        $ErrorActionPreference = 'Continue'
        $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $tool @arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
        $env:GIT_DIR = $previousGitDir
    }
    return [pscustomobject]@{ Output = $output; ExitCode = $exitCode }
}

$temporary = Join-Path ([IO.Path]::GetTempPath()) ('photoai-pilot-package-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    $missingArtifact = Join-Path $temporary 'not-an-apk.apk'
    $result = Invoke-PilotTool @(
        '-ArtifactPath', $missingArtifact,
        '-OutputRoot', $temporary,
        '-ReleaseSourceSha', ('0' * 40),
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_INVALID_SOURCE' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_RELEASE_SOURCE') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_REDACTION' }
    if ($rendered.Contains($temporary) -or $rendered.Contains($missingArtifact)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_PATH_LEAK' }
    if (Test-Path -LiteralPath (Join-Path $temporary 'pilot-package')) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_OUTPUT_CREATED' }

    $head = ((& git -c core.excludesFile= -C (Split-Path -Parent $PSScriptRoot) rev-parse HEAD) | Select-Object -First 1).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT' }
    $tamperedArtifact = Join-Path $temporary 'tampered.apk'
    [IO.File]::WriteAllBytes($tamperedArtifact, [byte[]](1, 2, 3))
    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $temporary,
        '-ReleaseSourceSha', $head,
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_TAMPERED_INPUT' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_ARTIFACT_HASH') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_TAMPERED_REDACTION' }
    if ($rendered.Contains($temporary) -or $rendered.Contains($tamperedArtifact)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_TAMPERED_PATH_LEAK' }

    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $temporary,
        '-ReleaseSourceSha', $head,
        '-ExpectedApkSha256', 'not-a-valid-hash',
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_INVALID_PARAMETER' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_INVALID_INPUT') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_PARAMETER_REDACTION' }
    if ($rendered.Contains($temporary) -or $rendered.Contains($tamperedArtifact)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_PARAMETER_PATH_LEAK' }

    $gitMetadata = ((& git -c core.excludesFile= -C (Split-Path -Parent $PSScriptRoot) rev-parse --git-common-dir) | Select-Object -First 1).Trim()
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $gitMetadata -PathType Container)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_METADATA' }
    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $gitMetadata,
        '-ReleaseSourceSha', ('0' * 40),
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_METADATA' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_NOT_EXTERNAL') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_METADATA' }
    if ($rendered.Contains($gitMetadata)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_METADATA_PATH_LEAK' }

    $worktreeRoot = Split-Path -Parent $PSScriptRoot
    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $worktreeRoot,
        '-ReleaseSourceSha', ('0' * 40),
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_WORKTREE_ROOT' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_NOT_EXTERNAL') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_WORKTREE_ROOT' }
    if ($rendered.Contains($worktreeRoot)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_WORKTREE_PATH_LEAK' }

    $bareRepository = Join-Path $temporary 'bare-repository'
    & git init --bare $bareRepository | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_BARE_REPOSITORY_SETUP' }
    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $bareRepository,
        '-ReleaseSourceSha', ('0' * 40),
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    )
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_BARE_REPOSITORY' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_NOT_EXTERNAL') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_BARE_REPOSITORY' }
    if ($rendered.Contains($bareRepository)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_BARE_REPOSITORY_PATH_LEAK' }

    $result = Invoke-PilotTool @(
        '-ArtifactPath', $tamperedArtifact,
        '-OutputRoot', $temporary,
        '-ReleaseSourceSha', ('0' * 40),
        '-ExpectedApkSha256', ('0' * 64),
        '-ExpectedCertificateSha256', ('0' * 64)
    ) -GitDirOverride (Join-Path $temporary 'invalid-git-dir')
    if ($result.ExitCode -ne 2) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_PROBE' }
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_PACKAGE_BLOCKED_OUTPUT_GIT_PROBE') { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_PROBE' }
    if ($rendered.Contains($temporary)) { throw 'P20_PILOT_PACKAGE_TEST_FAILED_GIT_PROBE_PATH_LEAK' }
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
}
Write-Output 'P20_PILOT_PACKAGE_TEST_PASS'
