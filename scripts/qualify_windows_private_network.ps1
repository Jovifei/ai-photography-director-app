[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [int]$InterfaceIndex,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

function Test-Rfc1918([string]$Address) {
    $parsed = $null
    if (-not [Net.IPAddress]::TryParse($Address, [ref]$parsed) -or
        $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }
    $octets = $parsed.GetAddressBytes()
    return ($octets[0] -eq 10) -or
        ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
        ($octets[0] -eq 192 -and $octets[1] -eq 168)
}

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

$adapters = @(Get-NetAdapter -ErrorAction Stop | Where-Object {
    $_.Status -eq 'Up' -and $_.HardwareInterface
})
$profiles = @(Get-NetConnectionProfile -ErrorAction Stop)
$candidates = @()
foreach ($adapter in $adapters) {
    $addresses = @(Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {
        $_.AddressState -eq 'Preferred' -and (Test-Rfc1918 $_.IPAddress)
    })
    $profile = $profiles | Where-Object InterfaceIndex -eq $adapter.ifIndex | Select-Object -First 1
    foreach ($address in $addresses) {
        $candidates += [pscustomobject]@{
            interface_index = [int]$adapter.ifIndex
            network_category = if ($null -eq $profile) { 'UNKNOWN' } else { [string]$profile.NetworkCategory }
            address_scope = 'RFC1918'
            hardware = $true
            state = [string]$adapter.Status
        }
    }
}

if ($candidates.Count -eq 0) {
    Write-Output 'P20_NETWORK_BLOCKED_NO_UP_RFC1918_HARDWARE_INTERFACE'
    exit 2
}

if (-not $PSBoundParameters.ContainsKey('InterfaceIndex')) {
    $candidates | Sort-Object interface_index | Format-Table -AutoSize
    if (@($candidates | Where-Object network_category -eq 'Private').Count -gt 0) {
        Write-Output 'P20_NETWORK_PRIVATE_INTERFACE_AVAILABLE'
        exit 0
    }
    Write-Output 'P20_NETWORK_BLOCKED_PRIVATE_PROFILE_REQUIRED'
    exit 2
}

$selected = $candidates | Where-Object interface_index -eq $InterfaceIndex | Select-Object -First 1
if ($null -eq $selected) {
    Write-Output 'P20_NETWORK_BLOCKED_INTERFACE_NOT_ELIGIBLE'
    exit 2
}
if (-not $Apply) {
    Write-Output 'P20_NETWORK_TARGET_VALID_READONLY'
    $selected | Format-Table -AutoSize
    exit 0
}
if (-not (Test-Administrator)) {
    Write-Output 'P20_NETWORK_BLOCKED_ADMIN_REQUIRED'
    exit 2
}
if ($selected.network_category -ne 'Private') {
    if ($PSCmdlet.ShouldProcess("interface $InterfaceIndex", 'Set Windows network category to Private')) {
        Set-NetConnectionProfile -InterfaceIndex $InterfaceIndex -NetworkCategory Private
    }
}
$updated = Get-NetConnectionProfile -InterfaceIndex $InterfaceIndex -ErrorAction Stop
if ($updated.NetworkCategory -ne 'Private') {
    Write-Output 'P20_NETWORK_BLOCKED_PRIVATE_PROFILE_NOT_APPLIED'
    exit 2
}
Write-Output 'P20_NETWORK_PRIVATE_PROFILE_PASS'
