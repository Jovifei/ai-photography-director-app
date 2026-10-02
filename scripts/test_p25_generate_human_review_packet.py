from __future__ import annotations

import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import p25_generate_human_review_packet as packet
from p23c_bundle_contract import PackError


FIXTURE = Path(__file__).parents[1] / "docs" / "reference" / "p25_real_20_editorial_draft.v1.json"


class P25HumanReviewPacketTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.corpus_raw = FIXTURE.read_bytes()

    def test_renders_current_corpus_without_approval(self):
        rendered = packet.render_packet(self.corpus_raw).decode("utf-8")
        self.assertIn(hashlib.sha256(self.corpus_raw).hexdigest(), rendered)
        self.assertEqual(20, rendered.count("content_review：`PENDING`"))
        self.assertEqual(20, rendered.count("evidence_id：`null`"))
        self.assertNotIn("`APPROVED`", rendered)
        self.assertIn("远离车流和车辆出入口", rendered)
        self.assertIn("不涉及个人隐私、身份或联系方式", rendered)

    def test_cli_writes_once_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "review-packet.md"
            self.assertEqual(0, packet.main([str(FIXTURE), str(output)]))
            self.assertTrue(output.is_file())
            self.assertEqual(2, packet.main([str(FIXTURE), str(output)]))

    def test_cli_maps_safe_io_error_to_blocked(self):
        with mock.patch.object(packet, "read_selected", side_effect=PackError("INPUT_CHANGED")):
            with mock.patch("builtins.print") as output:
                result = packet.main(["input.json", "output.md"])
        self.assertEqual(2, result)
        self.assertIn("INPUT_CHANGED", output.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
