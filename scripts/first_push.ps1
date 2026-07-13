param(
  [string]$CommitMessage = "chore: initialize repository from approved agent handoff"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$binding = Get-Content (Join-Path $root "PROJECT_BINDING.json") -Raw | ConvertFrom-Json

if (-not (Test-Path (Join-Path $root ".git"))) { git init | Out-Host }
$origin = git remote get-url origin 2>$null
if (-not $origin) {
  git remote add origin $binding.remote_url
} elseif ($origin.TrimEnd('/') -ne $binding.remote_url.TrimEnd('/')) {
  throw "origin mismatch. Expected $($binding.remote_url), found $origin"
}

git branch -M $binding.default_branch
python (Join-Path $root "scripts\prepush_privacy_audit.py")
if ($LASTEXITCODE -ne 0) { throw "Privacy audit failed" }

git add .
Write-Host "Staged files:"
git diff --cached --name-only | Out-Host
python (Join-Path $root "scripts\prepush_privacy_audit.py")
if ($LASTEXITCODE -ne 0) { throw "Privacy audit failed after staging" }

$hasCommit = git rev-parse --verify HEAD 2>$null
if (-not $hasCommit) {
  git commit -m $CommitMessage | Out-Host
} else {
  git diff --cached --quiet
  if ($LASTEXITCODE -ne 0) { git commit -m $CommitMessage | Out-Host }
}

git push -u origin $binding.default_branch | Out-Host

python (Join-Path $root "scripts\generate_g0_report.py")
git add reports/G0_repository_bootstrap_report.md
python (Join-Path $root "scripts\prepush_privacy_audit.py")
if ($LASTEXITCODE -ne 0) { throw "Privacy audit failed for G0 report" }
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
  git commit -m "docs: record G0 repository bootstrap evidence" | Out-Host
  git push origin $binding.default_branch | Out-Host
}

Write-Host "Pushed $($binding.repository_name) to $($binding.remote_url)"
Write-Host "STOP: wait for owner approval before feature work."
