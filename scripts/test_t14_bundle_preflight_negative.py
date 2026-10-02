import json
import unittest
from scripts.t14_bundle_intake_preflight_contract import preflight


class T14PreflightNegativeTest(unittest.TestCase):
    def result(self, raw, manifest, checks=b''):
        return preflight(raw, manifest, checks)

    def test_bom_rejected(self):
        r = self.result(b'\xef\xbb\xbf{}', b'{}')
        self.assertEqual(r['error'], 'BOM')
        self.assertFalse(r['ready'])

    def test_duplicate_json_key_rejected(self):
        raw = b'{"a":1,"a":2}'
        r = self.result(raw, b'{}')
        self.assertEqual(r['error'], 'DUPLICATE_KEY')

    def test_manifest_hash_mismatch_rejected(self):
        raw = b'{"bundle":true}'
        manifest = json.dumps({'files':[{'path':'bundle.json','sha256':'0'*64}]}).encode()
        r = self.result(raw, manifest, b'bundle.json 0' )
        self.assertEqual(r['error'], 'CHECKSUM_MISMATCH')

    def test_ready_is_never_t14_authority(self):
        r = self.result(b'{}', json.dumps({'files':[]}).encode(), b'')
        self.assertFalse(r.get('ready', False))
        self.assertFalse(r.get('t14_authority', False))


if __name__ == '__main__':
    unittest.main()
