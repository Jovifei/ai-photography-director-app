# T14 Intake Preflight Handoff

Status: COMPATIBILITY_PREPARATION_ONLY

The App side validates Bundle structure only.

It may check:

- bundle manifest presence
- checksum presence
- schema version visibility
- path/hash preparation

It must not:

- import Pipeline DB data
- create READY state
- claim producer compatibility
- replace producer golden vector evidence

T14 approval requires independent producer evidence.
