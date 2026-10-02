# Progress checkpoint contract

## Purpose

Protect the milestone ledger from silent evidence loss.

## Required identity

`docs/project_progress.json` is the only progress source.

`source_revision` must be an exact 40 lowercase hexadecimal Git object SHA.

## Frozen checkpoint identity

The accepted phase contract is:

- M1: 4 checkpoints
- M2: 6 checkpoints
- M3: 5 checkpoints
- M4: 7 checkpoints
- M5: 6 checkpoints
- M6: 6 checkpoints

Total: 34 checkpoints.

Checkpoint names are identities. Removing, renaming, duplicating, or silently replacing checkpoints requires an explicit migration decision, not a normal progress edit.

## Approval rule

`planning_status=APPROVED` requires the M1 roadmap approval checkpoint to be `done` with evidence.

Technical approval does not imply product, legal, device, human, producer, or release approval.

## Local commands

```bash
python scripts/validate_progress_contract.py
python scripts/render_project_progress.py --check
python scripts/prepush_privacy_audit.py
```
