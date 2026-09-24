# P25R Owner 最终审核指南

状态：`READY_FOR_OWNER_HUMAN_REVIEW`。

这份指南把当前唯一未完成 Gate 集中到一处。它不包含审核决定，也不授权 Pipeline、App 导入或公开分发。

## 精确审核对象

- corpus：`docs/reference/p25_real_20_editorial_draft.v1.json`
- corpus ID：`P25-PORTRAIT-EDITORIAL-20-20260919`
- Git blob：`c6c4bae87c525f72678a658fc913bdf3ce4e8b97`
- Git-object SHA-256：`7be88f916da20aaa1801d8eff35f4dbb8de0d5b86f1168bfd813f4023ad4448c`
- 条目数：20
- AI 预审：20/20 `PRELIMINARY_PASS`，详见 `reports/P25R_AI_PRE_REVIEW_20260920.md`
- 真人审核：0/20，仍为 `PENDING`

只要 corpus 正文发生任何变化，以上 SHA 绑定和旧审核文件立即失效，必须重新生成模板并重新审核。

## Owner 要完成的决定

每条都要分别确认三项：

1. `content_review`：摄影建议准确、自然、可执行，且不存在危险站位或误导。
2. `rights_review`：文字属于项目可使用的原创编辑内容，没有复刻品牌、受限作品或未经许可来源。
3. `privacy_review`：不包含姓名、账号、地址、设备、EXIF、路径、媒体标识或其他个人信息。

总体还需填写真实的 `review_id`、`reviewer_id`、`rights_basis_evidence_id`；每条填写唯一的 `evidence_id`。这些 ID 是对真实审核记录的脱敏句柄，不能凭空编造。

## 20 条检查清单

| 条目 | 场景摘要 | 重点 |
|---|---|---|
| 001 | 室内窗边半身 | 混合色温、面部透视、自然手势 |
| 002 | 窗边侧脸 | 轮廓光、暗部细节、颈肩姿态 |
| 003 | 金色时刻逆光 | 眩光控制、曝光、安全站位 |
| 004 | 阴天开放阴影 | 肤色、背景分离、表情可执行性 |
| 005 | 门廊或门框 | 框景、通行避让、身体重心 |
| 006 | 咖啡馆桌边 | 场地许可、隐私、手部自然度 |
| 007 | 城市步行 | 仅人行道/封闭步行区，远离车流和出入口 |
| 008 | 楼梯纵深 | 台阶干燥稳固、远离边缘，不攀爬或站扶手 |
| 009 | 稳固长椅/平台坐姿 | 双脚接地，不使用墙沿或临空边缘 |
| 010 | 靠墙全身 | 不妨碍通行，避免危险或受限墙面 |
| 011 | 玻璃反射 | 不触碰、不倚靠玻璃，脚下稳定 |
| 012 | 前景遮挡半身 | 植物/建筑边缘不伤人、不侵入受限区域 |
| 013 | 极简留白 | 构图意图清楚，动作不过度僵硬 |
| 014 | 走廊/拱廊对称 | 通行避让、场地许可、透视稳定 |
| 015 | 低机位天空 | 摄影者和器材保持稳定，不躺入通行区域 |
| 016 | 高机位俯拍 | 相机仅在地面或稳固平台，人物不登高、不临边 |
| 017 | 蓝调城市人像 | 仅安全人行区域，远离车流、路缘和通行冲突 |
| 018 | 逆光剪影 | 地面稳定、轮廓可辨、不过度靠近危险边界 |
| 019 | 夜间橱窗/霓虹 | 安全人行区域，远离车流和路缘，注意场地隐私 |
| 020 | 两人互动 | 仅普通轻松话题，不涉及身份、联系方式或隐私 |

任一项不满足时，将该条标为拒绝并先修改 corpus；不要在正文修改后沿用旧审核。

## 操作步骤

需要逐条阅读完整九个摄影字段时，先在仓库外生成与当前 corpus 字节绑定的人审包：

```powershell
python scripts/p25_generate_human_review_packet.py `
  docs/reference/p25_real_20_editorial_draft.v1.json `
  E:\project_benchmark_evidence\p25r-owner-review\p25-human-review-packet.md
```

人审包只用于阅读和记录意见，不是审核回执，也不会写入任何 `APPROVED`。

然后生成新的 PENDING 模板：

```powershell
python scripts/p25_human_review_gate.py review-template `
  docs/reference/p25_real_20_editorial_draft.v1.json `
  E:\project_benchmark_evidence\p25r-owner-review\p25-review.json
```

由真人填写审核身份、总体权利证据句柄、20 个唯一 evidence ID 和每条三项决定。填写后运行：

```powershell
python scripts/p25_human_review_gate.py validate-review `
  docs/reference/p25_real_20_editorial_draft.v1.json `
  E:\project_benchmark_evidence\p25r-owner-review\p25-review.json
```

只有输出 `P25_REAL_20_CONTENT_REVIEWED` 才进入下一步。随后允许生成仓库外中间交接：

```powershell
python scripts/p25_human_review_gate.py export-pipeline-input `
  docs/reference/p25_real_20_editorial_draft.v1.json `
  E:\project_benchmark_evidence\p25r-owner-review\p25-review.json `
  E:\project_benchmark_evidence\p25r-owner-review\p25-curated-editorial-input.json
```

该输出必须保持 `HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE`、`app_import_authorized=false`、`public_distribution_authorized=false`。真实 Pipeline release 和 P26 trust 是之后的独立阶段。
