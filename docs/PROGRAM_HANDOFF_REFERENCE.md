# Program Handoff Reference

本仓库（`ai-photography-director-app`）是项目群级交接包派生的**项目级**仓库。项目群 Source of Truth **不属于本仓库**，本文件仅记录所使用的交接包版本与外部引用，便于审计与可追溯。

## 交接包引用

| 字段 | 值 |
| --- | --- |
| 交接包名称 | AI Photography Program Agent Handoff |
| 交接包版本 | 1.2.1（Android First 修正版） |
| 交接包日期 | 2026-07-13 |
| 交付形态 | 解压目录（未以 ZIP 形态落盘） |
| 外部路径（Source of Truth） | `E:\project\_agent-handoff\AI_Photography_Program_Agent_Handoff_v1.2.1_Android_First` |

## 完整性引用

交接包未以单一 ZIP 文件交付，因此不存在单一 ZIP SHA-256。完整性以交接包内 `manifest.sha256`（逐文件 SHA-256 清单，共 164 行）为准：

| 字段 | 值 |
| --- | --- |
| 完整性清单文件 | `manifest.sha256`（位于交接包根） |
| `manifest.sha256` 自身 SHA-256 | `7fbc45650f0d97b88560b1e3af6e163c9be0e454a6b4747518de05bc789575bf` |

> 如需校验，可在外部交接包目录运行 `sha256sum -c manifest.sha256`。

## shared-contract 快照

本仓库 `shared-contract/` 是项目群 Bundle Contract 的**仓库本地快照**（consumer 侧）。合同内容与交接包 `shared-contract/` 完全一致（逐文件 SHA-256 已校验）。

| 字段 | 值 |
| --- | --- |
| 合同名称 | photo-intelligence-bundle |
| contract_version | 1.0.0 |
| schema 状态 | draft-for-G0-freeze |
| 本仓库 Bundle 角色 | consumer |
| 兄弟仓库（producer） | `nightly-photo-intelligence-pipeline` |

跨仓库的合同变更必须先创建 ADR、在两个仓库同步 fixtures/schemas，并获得负责人批准。不得在本仓库单独修改合同内容。

## 边界声明

- 项目群交接包（`project-templates/` 本身、总包级 `tasks/`、`validation`、`PROGRAM_START_HERE`、另一工程模板、ZIP/SHA256/项目群 Manifest、项目群级智能体入口文件）**不进入本仓库**。
- 本仓库只包含 `project-templates/ai-photography-director-app/` 下的项目级内容。
- Pipeline 源码、iOS 工程、CameraX 功能实现、Pose 模型、大型第三方仓库源码、私人图片、模型文件、运行数据库**不进入本仓库**。
- 当前阶段：G0（仓库整理与首次安全推送）。下一阶段：AH0（Android 环境与 CameraX）。
