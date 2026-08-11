# Android Closed Beta：5 人测试准备方案

本文件只准备测试，不代表测试已经执行或 APK 已发送。

## 环境与边界

- 测试者只使用 `T01`、`T02`、`T03`、`T04`、`T05` 编号。
- 全部在同一私有 Wi-Fi 和可达的用户本机 Qwen 服务中进行；不使用公共云。
- 每人使用 5 张自己的参考图，完成导入、逐张分析、READY-only 汇总、主参考、拍摄、系统另存为和删除。
- 记录字段仅为任务 PASS/FAIL、完成时间、1–5 分评分和脱敏问题描述。
- 禁止记录照片、姓名、账号、设备 ID、GPS、URI、文件名、LAN 地址或主机名、一次性配对码、证书 pin、token/凭据、绝对路径、原始日志、原始 AI 输出或截图中的私人内容。

## 测试卡片

`T01`～`T05` 只代表五位测试者；每位测试者都必须完成以下完整流程。

| 编号 | 流程 | 通过条件 |
|---|---|---|
| S01 | 安装并启动 | 显示“摄影导演”，可进入空项目状态 |
| S02 | 创建项目并导入 5 张 | 显示 5/20，单张状态独立且无媒体权限提示 |
| S03 | 配对与逐张分析 | 仅使用一次性配对；每张结果可区分，失败项可单独重试 |
| S04 | READY-only 汇总与主参考 | 汇总不包含 FAILED 文本；推荐主参考可由用户覆盖 |
| S05 | Camera Director 拍摄 | 有参考与无参考入口正确，拍摄完成后出现“保存照片” |
| S06 | 系统另存为成功 | 选择位置后文件可打开 |
| S07 | 取消与重试 | 取消或写入失败后缓存保留，允许再次保存 |
| S08 | 删除与清空 | 删除单项不影响其余项目；清空需要确认 |
| S09 | 重启与恢复 | 重启后不显示永久 RUNNING/QUEUED，内容先 reconciliation 后展示 |

## 结果模板

每位测试者单独填写：

```text
tester: T0X
tasks: S01 PASS/FAIL; S02 PASS/FAIL; S03 PASS/FAIL; S04 PASS/FAIL; S05 PASS/FAIL; S06 PASS/FAIL; S07 PASS/FAIL; S08 PASS/FAIL; S09 PASS/FAIL
completion_time_minutes: <number>
rating_1_to_5: <number>
redacted_issue: <short description or NONE>
```
