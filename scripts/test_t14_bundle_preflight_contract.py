import unittest
from t14_bundle_intake_preflight_contract import preflight

class T14PreflightNegativeTest(unittest.TestCase):
    def assertReject(self, raw, manifest, checksums):
        result = preflight(raw, manifest, checksums)
        self.assertFalse(result.get('compatible_preflight', False))
        self.assertFalse(result.get('ready', True))
        self.assertFalse(result.get('db_import', True))
        self.assertFalse(result.get('t14_authority', True))

    def test_empty_files(self):
        self.assertReject(b'{}', {'files': []}, b'')

    def test_bom(self):
        self.assertReject(b'\xef\xbb\xbf{}', {'files': []}, b'')

    def test_unsafe_windows_path(self):
        self.assertReject(b'{}', {'files':[{'path':'C:\\outside\\x'}]}, b'')

    def test_unc_path(self):
        self.assertReject(b'{}', {'files':[{'path':'\\\\server\\x'}]}, b'')

    def test_duplicate_json_key_case(self):
        self.assertReject(b'{"a":1,"a":2}', {'files':[]}, b'')

if __name__ == '__main__':
    unittest.main()
