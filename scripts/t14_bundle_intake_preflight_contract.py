"""Compatibility-only synthetic bundle preflight.
Never imports DB and never returns T14 READY authority.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import PurePosixPath

MAX_BYTES = 512 * 1024

def preflight(raw: bytes, manifest: dict):
    if raw.startswith(b'\xef\xbb\xbf'):
        return {'ready': False, 'db_import': False, 't14_authority': False, 'error': 'BOM'}
    if len(raw) > MAX_BYTES:
        return {'ready': False, 'db_import': False, 't14_authority': False, 'error': 'SIZE'}
    try:
        json.loads(raw.decode('utf-8'))
    except Exception:
        return {'ready': False, 'db_import': False, 't14_authority': False, 'error': 'UTF8_JSON'}
    if not isinstance(manifest, dict) or not isinstance(manifest.get('files'), list):
        return {'ready': False, 'db_import': False, 't14_authority': False, 'error': 'MANIFEST'}
    for item in manifest['files']:
        path = item.get('path')
        if not isinstance(path, str) or PurePosixPath(path).is_absolute() or '..' in PurePosixPath(path).parts:
            return {'ready': False, 'db_import': False, 't14_authority': False, 'error': 'PATH'}
    return {'ready': False, 'db_import': False, 't14_authority': False, 'compatible_preflight': True}
