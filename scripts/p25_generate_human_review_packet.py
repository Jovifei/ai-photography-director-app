"""Generate a PENDING-only human-readable review packet for the P25 corpus."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from p23c_bundle_contract import PackError
from p23c_prepare_review_pack import publish_new, read_selected
from p25_validate_editorial_corpus import CorpusError, MAX_CORPUS_BYTES, validate


FIELD_ORDER = (
    "scene",
    "background_story",
    "lighting",
    "composition",
    "subject_intent",
    "emotion",
    "pose_template",
    "camera_position",
    "director_prompt",
)


def render_packet(corpus_raw: bytes) -> bytes:
    result = validate(corpus_raw)
    corpus = json.loads(corpus_raw.decode("utf-8"))
    lines = [
        "# P25R 20 条真人审核包",
        "",
        f"状态：`{result['status']}`。",
        "",
        f"corpus_id：`{corpus['corpus_id']}`  ",
        f"corpus_sha256（所选文件精确字节）：`{hashlib.sha256(corpus_raw).hexdigest()}`  ",
        f"条目数：`{result['reference_count']}`  ",
        "本文件只供真人逐条阅读和记录意见，不是 review receipt，不会自动产生 APPROVED。",
        "",
        "## 审核规则",
        "",
        "- 每条分别判断 content / rights / privacy；任何一项都不能由另外两项代替。",
        "- 检查摄影建议准确性、动作可执行性、站位安全、原创权利和隐私信息。",
        "- 任一正文修改都会改变 corpus SHA；必须重新生成审核包和 PENDING review template。",
        "- 真实审核还需 review_id、reviewer_id、rights_basis_evidence_id 和每条唯一 evidence_id。",
        "",
    ]
    for entry in corpus["entries"]:
        lines.extend(
            [
                f"## {entry['reference_id']}",
                "",
                f"source_evidence_id：`{entry['source_evidence_id']}`（仅句柄，不是权利证明）  ",
                "content_review：`PENDING`　rights_review：`PENDING`　privacy_review：`PENDING`  ",
                "evidence_id：`null`",
                "",
            ]
        )
        for field in FIELD_ORDER:
            lines.extend((f"### {field}", "", entry["photography"][field], ""))
        lines.extend(
            (
                "真人备注：",
                "",
                "审核结论：content `PENDING` / rights `PENDING` / privacy `PENDING`",
                "",
                "---",
                "",
            )
        )
    return ("\n".join(lines).rstrip() + "\n").encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    args = list(argv or sys.argv[1:])
    if len(args) != 2:
        print(json.dumps({"status": "BLOCKED", "code": "USAGE"}, sort_keys=True))
        return 2
    try:
        corpus_raw = read_selected(Path(args[0]), ".json", MAX_CORPUS_BYTES)
        packet = render_packet(corpus_raw)
        publish_new(Path(args[1]), packet)
    except (OSError, CorpusError, PackError) as error:
        code = str(error) if isinstance(error, (CorpusError, PackError)) else "LOCAL_IO_ERROR"
        print(json.dumps({"status": "BLOCKED", "code": code}, sort_keys=True))
        return 2
    print(
        json.dumps(
            {
                "status": "PENDING_HUMAN_REVIEW_PACKET_WRITTEN",
                "packet_sha256": hashlib.sha256(packet).hexdigest(),
                "reference_count": 20,
                "approval_granted": False,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
