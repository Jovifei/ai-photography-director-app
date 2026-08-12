[CmdletBinding()]
param(
    [ValidateSet('Validate', 'Preflight', 'Run')]
    [string]$Mode = 'Validate',
    [string]$LanIp,
    [string]$SessionDirectory,
    [string]$PythonPath,
    [string]$ModelDirectory,
    [int]$Port = 8443
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tlsTool = Join-Path $PSScriptRoot 'prepare_photoai_pilot_tls.py'
$serviceModule = 'local_analysis_service.service'

function Stop-PilotSession([string]$code) { throw $code }

function Restore-Environment([hashtable]$values) {
    foreach ($name in $values.Keys) {
        if ($null -eq $values[$name]) {
            Remove-Item -Path ('Env:' + $name) -ErrorAction SilentlyContinue
        } else {
            Set-Item -Path ('Env:' + $name) -Value $values[$name]
        }
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

function Test-Rfc1918Ipv4([string]$value) {
    $address = $null
    if (-not [Net.IPAddress]::TryParse($value, [ref]$address) -or $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }
    $octets = $address.GetAddressBytes()
    return ($octets[0] -eq 10) -or
        ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
        ($octets[0] -eq 192 -and $octets[1] -eq 168)
}

function Test-InputShape {
    if ([string]::IsNullOrWhiteSpace($LanIp) -or -not (Test-Rfc1918Ipv4 $LanIp) -or
        [string]::IsNullOrWhiteSpace($SessionDirectory) -or
        [string]::IsNullOrWhiteSpace($PythonPath) -or
        [string]::IsNullOrWhiteSpace($ModelDirectory) -or
        $Port -ne 8443) {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_INVALID_INPUT'
    }
    if (Test-Path -LiteralPath $SessionDirectory) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_SESSION_EXISTS' }
    $parent = Split-Path -Parent $SessionDirectory
    if ([string]::IsNullOrWhiteSpace($parent) -or -not (Test-Path -LiteralPath $parent -PathType Container)) {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_SESSION_PARENT'
    }
    if (-not (Test-Path -LiteralPath $PythonPath -PathType Leaf) -or -not (Test-Path -LiteralPath $ModelDirectory -PathType Container) -or
        -not (Test-Path -LiteralPath $tlsTool -PathType Leaf)) {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_RUNTIME'
    }
}

function Test-ExternalSessionParent {
    $parentPath = (Resolve-Path (Split-Path -Parent $SessionDirectory)).Path
    if (-not [string]::IsNullOrWhiteSpace($env:GIT_DIR) -or -not [string]::IsNullOrWhiteSpace($env:GIT_WORK_TREE) -or -not [string]::IsNullOrWhiteSpace($env:GIT_COMMON_DIR)) {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_GIT_PROBE'
    }
    $directoryPath = $parentPath.TrimEnd('\', '/')
    while (-not [string]::IsNullOrWhiteSpace($directoryPath)) {
        $gitMarker = Join-Path $directoryPath '.git'
        $looksBare = (Test-Path -LiteralPath (Join-Path $directoryPath 'HEAD') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $directoryPath 'objects') -PathType Container) -and
            (Test-Path -LiteralPath (Join-Path $directoryPath 'refs') -PathType Container)
        if ((Test-Path -LiteralPath $gitMarker) -or $looksBare) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_SESSION_NOT_EXTERNAL' }
        $ancestor = [IO.Path]::GetDirectoryName($directoryPath)
        if ([string]::IsNullOrWhiteSpace($ancestor) -or [string]::Equals($ancestor, $directoryPath, [StringComparison]::OrdinalIgnoreCase)) { break }
        $directoryPath = $ancestor
    }
    $gitContext = Invoke-Captured 'git' @('-c', 'core.excludesFile=', '-C', $parentPath, 'rev-parse', '--is-inside-work-tree')
    if ($gitContext.ExitCode -eq 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_SESSION_NOT_EXTERNAL' }
    $probeText = (($gitContext.Output | ForEach-Object { [string]$_ }) -join "`n")
    if ($gitContext.ExitCode -ne 128 -or $probeText -notmatch '(?i)(not a git repository|不是一个\s*git\s*仓库)') {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_GIT_PROBE'
    }
    return $parentPath
}

function Test-LocalRuntime {
    foreach ($file in @('config.json', 'preprocessor_config.json', 'photoai-model-manifest.json')) {
        if (-not (Test-Path -LiteralPath (Join-Path $ModelDirectory $file) -PathType Leaf)) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_RUNTIME' }
    }
    $weights = @(Get-ChildItem -LiteralPath $ModelDirectory -File -Filter '*.safetensors') + @(Get-ChildItem -LiteralPath $ModelDirectory -File -Filter '*.bin')
    if ($weights.Count -eq 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_RUNTIME' }
    $previous = @{
        PHOTOAI_MODEL_DIR = $env:PHOTOAI_MODEL_DIR; HF_HUB_OFFLINE = $env:HF_HUB_OFFLINE
        TRANSFORMERS_OFFLINE = $env:TRANSFORMERS_OFFLINE; PYTHONPATH = $env:PYTHONPATH
    }
    try {
        $env:PHOTOAI_MODEL_DIR = $ModelDirectory
        $env:HF_HUB_OFFLINE = '1'
        $env:TRANSFORMERS_OFFLINE = '1'
        $env:PYTHONPATH = $repo
        $probe = Invoke-Captured $PythonPath @('-B', '-c', "import cryptography, fastapi, torch, transformers; import $serviceModule")
    } finally {
        Restore-Environment $previous
    }
    if ($probe.ExitCode -ne 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_RUNTIME' }
}

function Get-PrivateLanInterface {
    try {
        $addresses = @(Get-NetIPAddress -IPAddress $LanIp -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.AddressState -eq 'Preferred' })
        if ($addresses.Count -ne 1) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_NETWORK_INTERFACE' }
        $adapter = Get-NetAdapter -InterfaceIndex $addresses[0].InterfaceIndex -ErrorAction Stop
        if ($adapter.Status -ne 'Up' -or -not $adapter.HardwareInterface) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_NETWORK_INTERFACE' }
        $profile = Get-NetConnectionProfile -InterfaceIndex $addresses[0].InterfaceIndex -ErrorAction Stop
        if ($profile.NetworkCategory -ne 'Private') { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE' }
        return [int]$addresses[0].InterfaceIndex
    } catch {
        $message = [string]$_.Exception.Message
        if ($message -match '^P20_PILOT_SESSION_') { throw }
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE'
    }
}

function Test-PortAvailable {
    try {
        $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {
            $_.LocalPort -eq $Port -and ($_.LocalAddress -eq $LanIp -or $_.LocalAddress -eq '0.0.0.0')
        })
    } catch {
        Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_PORT_CHECK'
    }
    if ($listeners.Count -ne 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_PORT_IN_USE' }
}

function Invoke-Preflight {
    Test-LocalRuntime
    Get-PrivateLanInterface | Out-Null
    Test-PortAvailable
}

function Invoke-PilotService {
    $tlsPath = Join-Path $SessionDirectory 'tls'
    $ruleName = 'PhotoAI-Pilot-' + [Guid]::NewGuid().ToString('N')
    $ruleCreated = $false
    $sessionCreated = $false
    $previous = @{
        PHOTOAI_MODEL_DIR = $env:PHOTOAI_MODEL_DIR; PHOTOAI_TLS_CERTFILE = $env:PHOTOAI_TLS_CERTFILE
        PHOTOAI_TLS_KEYFILE = $env:PHOTOAI_TLS_KEYFILE; PHOTOAI_BIND_HOST = $env:PHOTOAI_BIND_HOST
        PHOTOAI_PORT = $env:PHOTOAI_PORT; HF_HUB_OFFLINE = $env:HF_HUB_OFFLINE
        TRANSFORMERS_OFFLINE = $env:TRANSFORMERS_OFFLINE; PYTHONPATH = $env:PYTHONPATH
    }
    try {
        New-Item -ItemType Directory -Path $SessionDirectory -ErrorAction Stop | Out-Null
        $sessionCreated = $true
        $tls = Invoke-Captured $PythonPath @($tlsTool, '--output-dir', $tlsPath, '--lan-ip', $LanIp)
        if ($tls.ExitCode -ne 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_TLS' }
        try {
            New-NetFirewallRule -Name $ruleName -DisplayName $ruleName -Group 'PhotoAI Pilot Temporary' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $Port -LocalAddress $LanIp -RemoteAddress LocalSubnet -Profile Private -EdgeTraversalPolicy Block -ErrorAction Stop | Out-Null
            $ruleCreated = $true
        } catch {
            Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_FIREWALL'
        }
        $env:PHOTOAI_MODEL_DIR = $ModelDirectory
        $env:PHOTOAI_TLS_CERTFILE = Join-Path $tlsPath 'cert.pem'
        $env:PHOTOAI_TLS_KEYFILE = Join-Path $tlsPath 'key.pem'
        $env:PHOTOAI_BIND_HOST = $LanIp
        $env:PHOTOAI_PORT = [string]$Port
        $env:HF_HUB_OFFLINE = '1'
        $env:TRANSFORMERS_OFFLINE = '1'
        $env:PYTHONPATH = $repo
        Write-Output 'P20_PILOT_SESSION_RUNNING'
        & $PythonPath -B -m $serviceModule
        if ($LASTEXITCODE -ne 0) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_SERVICE' }
    } finally {
        Restore-Environment $previous
        $cleanupFailed = $false
        if ($ruleCreated) {
            try {
                Remove-NetFirewallRule -Name $ruleName -ErrorAction Stop
                $remainingRules = @(Get-NetFirewallRule -Group 'PhotoAI Pilot Temporary' -ErrorAction Stop | Where-Object { $_.Name -eq $ruleName })
                if ($remainingRules.Count -ne 0) { throw 'RULE_REMAINS' }
            } catch {
                $cleanupFailed = $true
            }
        }
        if ($sessionCreated -and (Test-Path -LiteralPath $SessionDirectory)) {
            try {
                Remove-Item -LiteralPath $SessionDirectory -Recurse -Force -ErrorAction Stop
                if (Test-Path -LiteralPath $SessionDirectory) { throw 'SESSION_REMAINS' }
            } catch {
                $cleanupFailed = $true
            }
        }
        if ($cleanupFailed) { Stop-PilotSession 'P20_PILOT_SESSION_BLOCKED_CLEANUP_REQUIRED' }
    }
}

try {
    Test-InputShape
    Test-ExternalSessionParent | Out-Null
    if ($Mode -eq 'Validate') {
        Write-Output 'P20_PILOT_SESSION_VALIDATION_PASS'
        exit 0
    }
    Invoke-Preflight
    if ($Mode -eq 'Preflight') {
        Write-Output 'P20_PILOT_SESSION_PREFLIGHT_PASS'
        exit 0
    }
    Invoke-PilotService
    Write-Output 'P20_PILOT_SESSION_STOPPED'
    exit 0
} catch {
    $message = [string]$_.Exception.Message
    if ($message -notmatch '^P20_PILOT_SESSION_') { $message = 'P20_PILOT_SESSION_FAILED' }
    [Console]::Error.WriteLine($message)
    exit 2
}
