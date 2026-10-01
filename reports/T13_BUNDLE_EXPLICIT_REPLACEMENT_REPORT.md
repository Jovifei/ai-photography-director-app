# T13 explicit Bundle guidance replacement

Date: 2026-10-02 (Asia/Shanghai)
Base: accepted T12 PR #17 head `6685dfe5792b45c00081249f16d83e7782927900`
Branch: `codex/t13-bundle-explicit-replacement-20261002`
Remote plan/refinements: `c2c_2537`, original Ai相机 Project/chat.

## Result and behavior

`BUNDLE_RELEASE_REPLACEMENT_MACHINE_VALIDATED — PIPELINE_PRODUCER_COMPATIBILITY_PENDING — HUMAN_ACCEPTANCE_NOT_RUN`

The existing PKB1 importer now has an explicit replacement mode. The user binds every incoming item, views the frozen old and incoming provenance, then confirms replacement. Cancel and mapping/mode/document changes invalidate the preview without writing. Direct initial-import apply cannot bypass replacement confirmation. Activity recreation retains the ViewModel preview; applying blocks leave/rebind/cancel; unknown commit outcome uses the existing inspect-project path without claiming rollback or offering a blind retry.

Replacement requires a current Bundle READY row and complete provenance, with no complete or partial provider metadata. A derived read-only metadata-presence flag keeps mapping/preview consistent with the raw Room transaction guard; no column or migration was added. A historical analysis-attempt field on terminal READY is allowed and cleared after replacement. Active analysis states are ineligible.

Every mapped target must preserve bundleId, producerId, origin and producerReferenceId. Releases are opaque identities: different release IDs are not described as newer; same release with different SHA is rejected; same release with identical SHA is already applied and leaves the import timestamp unchanged. Confirmation re-reads every mapped row and compares its complete old provenance, including import timestamp, inside the existing all-target-prevalidated Room transaction. One stale, deleted, provider or mixed initial-import target rejects the whole batch. SQL failures roll back; acknowledgement failure remains OutcomeUnknown and is verified by reading committed state.

The transaction scope is exactly the incoming explicitly confirmed mapping set. Same-bundle rows omitted by an incoming release remain unchanged, including all old provenance. No membership synchronization, inferred mapping, hidden deletion or release-order inference is introduced. Reference IDs, private image files and capture associations are retained. Initial import, Room schema v6 and PKB1 contracts remain unchanged.

## Verification

- Final Android JVM results: 147 tests, 0 failures/errors.
- Final Debug/AndroidTest build PASS. Debug and static Release lint: 0 errors, 17 warnings each. Static Release lint used `-x :app:verifyReleaseSigning`; signed Release qualification NOT_RUN.
- Dedicated `T3_API35_20260927`, `emulator-5580`: final combined instrumentation 73/73 PASS. This comprises 18 T13 tests (9 isolated Room, 5 injected ViewModel, 4 real-screen/in-memory Room) and 55 existing initial-import, lifecycle, mapping, T12 navigation/invalidation, T3 camera and P25U capture/export regressions.
- T13 tests prove explicit preview/cancel/confirm; full provenance; provider and partial-provider refusal; identity mismatch/conflict/duplicate; stale/deleted target atomic refusal; SQLite rollback; uncertain acknowledgement; historical READY attempt handling; recreation; and 3-old/2-replaced/1-unchanged membership scope.
- PKB1 consumer contract: 12/12 PASS; Phase 1.5 contract checks PASS. T11 preflight remains `BLOCKED_LEGAL_REVIEW`, all acquisition/network/runtime/App/Pipeline authority flags false and tracked authority files unchanged.
- Independent final read-only review: PASS_NO_ACTIONABLE_FINDINGS. Privacy audit and staged whitespace checks are required before each push.

The first compile failed because the new Compose dialog lacked the `dp` import; corrected and all final builds passed. The first 55-test batch passed 48 tests and rejected all 7 T3 tests at their required `t3DedicatedEmulator` safety assertion because the runner omitted that argument. The unchanged assertion was preserved; final runs used the required argument after verifying the exact AVD. An intermediate combined 72/72 run passed before the remote-requested omitted-member test and explicit-scope wording were added; the final 73/73 run qualifies those additions. All unsuccessful outputs remain in external evidence.

Final Debug APK SHA-256: `E6173A4FCA53FF9C039B5D0F64204985D998DA6F0262F80C73042BE1B600066E`.
Final AndroidTest APK SHA-256: `1C0F5F5D739F9E71B58B43F1800038A23C1C2C40D15ED403127A70E0C559DCFB`.

Raw logs and APK metadata remain outside Git in Codex visualizations `t13-bundle-replacement-20261002`. T13 replacement tests use isolated in-memory Room and synthetic guidance; existing camera/export regressions use generated emulator media. No physical phone or real user photo was used. Real document-picker producer compatibility, production nightly output, physical-device photo usefulness, signing, merge and human acceptance are not claimed.

## Delivery and continuation

Delivery uses a Draft PR stacked on T12, with exact refs bound in C2C execution records. Remote review checks this implementation against its plan and identity/set-boundary decisions before selecting the next machine-executable stage. The separate nightly producer integrates through PKB1 when stable golden vectors are available; independent Android work can continue meanwhile.
