$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'qualify_t17_offline_guided_continuity.ps1') -PolicyOnly
function Assert-Equal($Expected,$Actual,[string]$Name) { if ($Expected -cne $Actual) { throw "POLICY_FAILED_$Name" } }
function Fixture {
    return @{ runId='a'*32; mode='success'; projectId='11111111-1111-1111-1111-111111111111';
        referenceId='22222222-2222-2222-2222-222222222222'; c1Attempted='true'; c1Id='b'*32; c2Attempted='false' }
}
$cases=0
$known = Fixture
Assert-Equal 'EXACT_CLEANUP' (Get-T17CleanupDecision $known) 'known_c1_before_c2'; $cases++
$known.c2Attempted='true'; $known.c2Id='c'*32
Assert-Equal 'EXACT_CLEANUP' (Get-T17CleanupDecision $known) 'two_recorded_captures'; $cases++
foreach ($key in @('projectId','referenceId','c1Id','c1Attempted','c2Attempted')) {
    $incomplete=Fixture; $incomplete.Remove($key)
    Assert-Equal 'PREFERENCE_ONLY_RETAIN' (Get-T17CleanupDecision $incomplete) "missing_$key"; $cases++
}
$unknown=Fixture; $unknown.c2Attempted='true'
Assert-Equal 'PREFERENCE_ONLY_RETAIN' (Get-T17CleanupDecision $unknown) 'attempted_c2_lost_id'; $cases++
$mismatch=Fixture; $mismatch.c2Id='c'*32
Assert-Equal 'PREFERENCE_ONLY_RETAIN' (Get-T17CleanupDecision $mismatch) 'id_without_attempt'; $cases++
$duplicate=Fixture; $duplicate.c2Attempted='true'; $duplicate.c2Id=$duplicate.c1Id
Assert-Equal 'PREFERENCE_ONLY_RETAIN' (Get-T17CleanupDecision $duplicate) 'duplicate_capture_id'; $cases++
$malformed=Fixture; $malformed.projectId='../foreign'; $malformed.c1Id='../foreign'
Assert-Equal 'PREFERENCE_ONLY_RETAIN' (Get-T17CleanupDecision $malformed) 'malformed_ownership'; $cases++
$receipt=@{}
Assert-Equal $true (Update-T17Receipt $receipt 'INSTRUMENTATION_STATUS: T17_RECEIPT_c1Attempted=false') 'initial_attempt'; $cases++
Assert-Equal $true (Update-T17Receipt $receipt 'INSTRUMENTATION_STATUS: T17_RECEIPT_c1Attempted=true') 'monotonic_attempt'; $cases++
$rejected=$false
try { [void](Update-T17Receipt $receipt 'INSTRUMENTATION_STATUS: T17_RECEIPT_c1Attempted=false') } catch { $rejected=$true }
Assert-Equal $true $rejected 'attempt_cannot_reset'; $cases++
[void](Update-T17Receipt $receipt 'INSTRUMENTATION_STATUS: T17_RECEIPT_projectId=owned')
$rejected=$false
try { [void](Update-T17Receipt $receipt 'INSTRUMENTATION_STATUS: T17_RECEIPT_projectId=foreign') } catch { $rejected=$true }
Assert-Equal $true $rejected 'identity_cannot_change'; $cases++
foreach ($key in @('prefPresent','prefBase64','c1TupleBase64')) {
    Assert-Equal "INSTRUMENTATION_STATUS: T17_RECEIPT_$key=<PRIVATE_REDACTED>" (Protect-T17LogLine "INSTRUMENTATION_STATUS: T17_RECEIPT_$key=secret") "masked_$key"; $cases++
}
$private=@{prefBase64=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('private-original-reference'))}
Assert-Equal 'expected <PRIVATE_REDACTED>' (Protect-T17LogLine 'expected private-original-reference' $private) 'failed_assertion_redacted'; $cases++
Write-Output "PASS_T17_CLEANUP_RECEIPT_POLICY cases=$cases NO_ADB_NO_DEVICE_NO_APP_DATA"
