# Phase 1.5 Contract Test Environment

The offline contract checks were verified with Python `3.14.2`, `jsonschema==4.26.0`, and `referencing==0.37.0`. The exact direct dependencies are locked in `requirements-phase1_5-contracts.txt`.

Run from the repository root:

```powershell
python scripts/test_phase1_5_contracts.py
```

The script resolves repository files from its own location, so it also supports execution from `scripts/` and an arbitrary current working directory with an absolute script path. It only reads local JSON, Markdown, and Python contract files. It does not install packages, access the network, load a model, read an image, or access Android media.

If either locked dependency is unavailable or a different incompatible version is installed, imports or schema validation fail non-zero. Operators must provision the locked environment through approved offline dependency management; the script must never call `pip`, invoke a package manager, or download a dependency.
