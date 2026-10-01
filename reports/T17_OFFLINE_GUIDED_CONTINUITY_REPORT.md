# T17 offline guided capture continuity

## Scope and target

Remote task `c2c_6b17`; accepted T16 documentation base `acfc1a515c8b5ec99ca56d01b4d9c637cc27c94e`; isolated branch `codex/t17-offline-guided-continuity-20261002`.

Target: `OFFLINE_GUIDED_CAPTURE_CONTINUITY_MACHINE_VALIDATED — PRODUCER_AND_HUMAN_ACCEPTANCE_PENDING`.

This stage adds production-root integration qualification and a bounded recovery runner. Production source, Room/capture schemas, replacement policy, Bundle contract, provider/Qwen/T11 authority and sibling repository remain unchanged. It reuses T12 identity/current-guidance policy and the T13-qualified explicit repository replacement API; it does not claim new replacement-picker/confirmation UI proof.

## Actual product journey

Success mode uses actual MainActivity/PhotographyDirectorApp, production ReferenceRepository/CaptureRepository, generated synthetic reference JPEG, formally digested A/B knowledge Bundles and actual emulator CameraX. No setContent host substitution or GrantPermissionRule.

PREPARE traverses Home → exact project → READY reference → Analysis Detail → Director Card → Camera Director and captures C1. It records the exact reservation ID/association before outcome wait, then verifies AVAILABLE, capture row tuple/JPEG hash and reference JPEG hash. Existing explicit replacement with a fresh-read complete A provenance fence applies same-association B with distinct composition/directorPrompt and an opaque release ID. C1 row/JPEG and reference image remain unchanged.

VERIFY runs under a new process epoch/PID with measured target UID. It reopens persisted P/R/B/C1, checks current B complete logical provenance/canonical digest, READY state and guidance, positive imported timestamp, exact C1 tuple/JPEG/reference image. Production capture-library C1 detail displays current B source and the existing nonhistorical-reference notice. Production guided-retake callback resolves current B, excludes A guidance, opens actual Camera Director and creates independent C2 with the same P/R. C1 is never relabeled as historically captured with B.

Deleted mode is a separate unique run: after actual guided C1, only its owned reference is deleted. A fresh root reopens preserved C1; reference/open/retake actions fail closed with no alternate reference guessed. Pure mismatch/non-READY T12 policy cases remain baseline.

## Ownership, privacy and recovery

Host marker precedes PREPARE; incremental receipts are atomically persisted even if instrumentation fails. Capture-attempt flags cannot regress; identities cannot drift. A missing attempted ID, extra capture, duplicate ID or wrong P/R blocks destructive cleanup. Android cleanup calls the same exact-set/association guard exercised by eight policy tests, validates every identity before deleting exact captures/files, then exact reference/project, and proves those IDs absent. Whole library/database emptiness is never asserted.

Only last_active_reference_id key presence/raw value is privately snapshotted. Original and owned-reference values are compared exactly; foreign changes, including unexpected absence for a present original, cause refusal without writing. Deleted mode restores original before deleting owned R; stage finally and independent host restoration protect failures. No other preferences are rewritten. Raw snapshot/tuple and decoded original value are redacted from logs/console/review output; private marker is removed only after exact fixture and preference proofs.

Runner requires exact emulator-5580 / T3_API35_20260927 / API35/qemu, measured package/target/test UIDs and CAMERA already GRANTED. It never grant/revoke/clear-data. It records natural instrumentation process exit versus actual target force-stop honestly. Every phase has a shared180s deadline, including continuous output, stdout EOF, process and stderr waits. Failure stops only proven target-UID processes through exact target-package force-stop and verifies quiescence before any subsequent cleanup/restorer. Unknown ownership retains the fixture/marker.

## Qualification

- Final success run `b5006d4ec8c3492686f06a3a9336c532`: PREPARE/VERIFY/CLEANUP PASS; fresh process, C1 unchanged/current-B retake/C2 association, original preference restored, exact fixture absent, marker removed, full camera flags unchanged.
- Final deleted run `727823ece07746d89bc520a4330133c7`: PREPARE/VERIFY/CLEANUP PASS; C1 preserved/reference actions unavailable after restart, exact fixture absent and original preference restored, marker removed, full camera flags unchanged.
- Both boundaries were actual `INSTRUMENTATION_PROCESS_EXIT_NO_FORCE_STOP_NEEDED`; no force-stop causality is invented. Actor process epoch/UID/PID receipts are available in redacted phase logs outside Git.
- Eight shared Android ownership/preference policy tests PASS;19 host receipt/privacy policy cases PASS. These are refusal-policy proof, not eight successful data cleanup operations.
- JVM159/159 and Debug/AndroidTest builds/static debug+Release lint PASS,0 errors/17 warnings each. Signing verification excluded. Final affected96/96 PASS,0 failure/skip, including existing T3/T4/P25U/T12/T13/T15/T16 and the eight T17 policy cases.
- PKB1 consumer12/12, Phase1.5 contracts PASS; T11 preflight remains BLOCKED_LEGAL_REVIEW with authority flags false. Privacy/diff checks required before push.

Debug APK SHA-256: `56C77D533DCFF5DD66E0971A9AFBE80EED43A64684CA9F60A334DAF0015390EF` (source-identical accepted T16 App).
Final96-test AndroidTest APK SHA-256: `2F0E9F91B75A724F0D9AB4B54E4558061D8CCFDDE92A4554AF757C4FB06B6077`.
The strengthened host-mode runs used AndroidTest APK `3B3EABEDE06EED003327BE598C158C6C5B458CFFD9BE8FE4E050A9AA79E1A002`; the only subsequent harness change was synchronization in the unrelated legacy T4 test, not T17 phase code or production App. Both artifact receipts are retained.

## Review refinements and retained evidence

Initial success/deleted runs passed, but independent review required bounded EOF/continuous-output waits, failed-phase quiescence, complete B persistence/current-source assertions and shared refusal-policy checks. Those were added and both final modes rerun on the strengthened harness. No fabricated negative runtime failure is claimed; first-pass evidence is supplemental, not final-source qualification. Prior T16 failures remain their own evidence.

Initial affected96 matrix passed95 and exposed a legacy T4 test race: it clicked the next action immediately after an asynchronous primary-reference commit, before the merged CTA updated. The test now waits for the expected merged enabled action, retaining all no-old-guidance/safe-return assertions. Focused1/1 and final96/96 passed. Production code was not changed for this race. Independent final source review PASS_NO_ACTIONABLE_FINDINGS.

## Producer sidecar and boundaries

`docs/PKB1_PRODUCER_GOLDEN_VECTOR_HANDOFF_REQUEST.md` specifies single/multi-reference, explicit A/B pair, negative schema/digest, exact raw-document bytes/SHA, canonical PKB1 digest and binding metadata needed from the producer. Consumer-generated fixtures never qualify T14. A fresh read of published producer main tree `ffc4130823c1308f089b835c766e341ec2173e82` found no PKB1/PhotoKnowledgeBundle/golden-Bundle/consumer-handoff named paths; this is a narrow published-tree observation, not a claim about active producer branches or all ongoing work. Sibling checkout was not edited or fetched.

Producer compatibility PENDING_EXTERNAL_GOLDEN_VECTORS. Physical-device variance, human photography/TalkBack usefulness, provider/Pipeline runtime, signing/release/merge NOT_RUN/NOT_EVALUATED. Local machine qualification PASS; delivery and remote exact-source verdict PENDING. Logs and generated data remain outside Git; no private images, DBs, weights, keys or runtime output are committed.
