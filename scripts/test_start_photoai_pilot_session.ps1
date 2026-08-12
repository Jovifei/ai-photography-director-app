$ErrorActionPreference = 'Stop'
$tool = Join-Path $PSScriptRoot 'start_photoai_pilot_session.ps1'
$tokens = $null
$errors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile($tool, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'P20_PILOT_SESSION_TEST_FAILED_PARSER' }
$source = Get-Content -LiteralPath $tool -Raw -Encoding utf8
foreach ($requiredCleanupFragment in @(
    'Remove-NetFirewallRule -Name $ruleName -ErrorAction Stop',
    "Get-NetFirewallRule -Group 'PhotoAI Pilot Temporary' -ErrorAction Stop",
    'Remove-Item -LiteralPath $SessionDirectory -Recurse -Force -ErrorAction Stop',
    "P20_PILOT_SESSION_BLOCKED_CLEANUP_REQUIRED"
)) {
    if (-not $source.Contains($requiredCleanupFragment)) { throw 'P20_PILOT_SESSION_TEST_FAILED_CLEANUP_CONTRACT' }
}

function Invoke-SessionTool([string[]]$arguments, [string]$GitDirOverride = $null) {
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

$temporary = Join-Path ([IO.Path]::GetTempPath()) ('photoai-pilot-session-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    $python = (Get-Command powershell.exe -ErrorAction Stop).Source
    $model = Join-Path $temporary 'model'
    New-Item -ItemType Directory -Path $model | Out-Null
    foreach ($file in @('config.json', 'preprocessor_config.json', 'photoai-model-manifest.json', 'model.safetensors')) {
        Set-Content -LiteralPath (Join-Path $model $file) -Value 'fixture' -Encoding ASCII
    }
    $common = @(
        '-Mode', 'Validate',
        '-LanIp', '192.168.20.10',
        '-SessionDirectory', (Join-Path $temporary 'session'),
        '-PythonPath', $python,
        '-ModelDirectory', $model
    )
    $result = Invoke-SessionTool $common
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($result.ExitCode -ne 0 -or $rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_SESSION_VALIDATION_PASS') { throw 'P20_PILOT_SESSION_TEST_FAILED_VALIDATE' }
    if (Test-Path -LiteralPath (Join-Path $temporary 'session')) { throw 'P20_PILOT_SESSION_TEST_FAILED_UNEXPECTED_OUTPUT' }

    foreach ($invalidIp in @('127.0.0.1', '8.8.8.8', '172.32.0.1', '169.254.1.1')) {
        $result = Invoke-SessionTool @('-Mode', 'Validate', '-LanIp', $invalidIp, '-SessionDirectory', (Join-Path $temporary ('session-' + $invalidIp.Replace('.', '_'))), '-PythonPath', $python, '-ModelDirectory', $model)
        $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
        if ($result.ExitCode -ne 2 -or $rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_SESSION_BLOCKED_INVALID_INPUT') { throw 'P20_PILOT_SESSION_TEST_FAILED_INVALID_IP' }
    }

    $existing = Join-Path $temporary 'existing-session'
    New-Item -ItemType Directory -Path $existing | Out-Null
    $result = Invoke-SessionTool @('-Mode', 'Validate', '-LanIp', '192.168.20.10', '-SessionDirectory', $existing, '-PythonPath', $python, '-ModelDirectory', $model)
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($result.ExitCode -ne 2 -or $rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_SESSION_BLOCKED_SESSION_EXISTS') { throw 'P20_PILOT_SESSION_TEST_FAILED_EXISTING_SESSION' }

    $worktreeRoot = Split-Path -Parent $PSScriptRoot
    $result = Invoke-SessionTool @('-Mode', 'Validate', '-LanIp', '192.168.20.10', '-SessionDirectory', (Join-Path $worktreeRoot 'pilot-session'), '-PythonPath', $python, '-ModelDirectory', $model)
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($result.ExitCode -ne 2 -or $rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_SESSION_BLOCKED_SESSION_NOT_EXTERNAL') { throw 'P20_PILOT_SESSION_TEST_FAILED_WORKTREE' }

    $result = Invoke-SessionTool $common -GitDirOverride (Join-Path $temporary 'invalid-git-dir')
    $rendered = @($result.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -ne '' })
    if ($result.ExitCode -ne 2 -or $rendered.Count -ne 1 -or $rendered[0] -ne 'P20_PILOT_SESSION_BLOCKED_GIT_PROBE') { throw 'P20_PILOT_SESSION_TEST_FAILED_GIT_PROBE' }
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
}
Write-Output 'P20_PILOT_SESSION_TEST_PASS'
