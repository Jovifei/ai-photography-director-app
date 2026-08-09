# Android Closed Beta：5 人测试准备方案

本文件只准备测试，不代表测试已经执行或 APK 已发送。

## 环境与边界

- 测试者只使用 `T01`、`T02`、`T03`、`T04`、`T05` 编号。
- 全部在同一私有 Wi-Fi 和可达的用户本机 Qwen 服务中进行；不使用公共云。
- 每人使用 5 张自己的参考图，完成导入、逐张分析、READY-only 汇总、主参考、拍摄、系统另存为和删除。
- 记录字段仅为任务 PASS/FAIL、完成时间、1–5 分评分和脱敏问题描述。
- 禁止记录照片、姓名、账号、设备 ID、GPS、URI、文件名、原始 AI 输出或截图中的私人内容。

## 测试卡片

| 编号 | 流程 | 通过条件 |
|---|---|---|
| T01 | 创建项目并导入 5 张 | 显示 5/20，单张状态独立且无媒体权限提示 |
| T02 | 配对、逐张分析、汇总 | 每张结果可区分；汇总不包含 FAILED 文本；推荐主参考可覆盖 |
| T03 | 进入 Camera Director 并拍摄 | 参考/无参考入口正确，拍摄完成后出现“保存照片” |
| T04 | 系统另存为 | 选择位置后文件可打开；取消和写入失败保留缓存，可重试 |
| T05 | 删除与恢复 | 删除单项/清空需确认；重启后不显示永久 RUNNING/QUEUED |

## 结果模板

每位测试者单独填写：

```text
tester: T0X
tasks: T01 PASS/FAIL; T02 PASS/FAIL; T03 PASS/FAIL; T04 PASS/FAIL; T05 PASS/FAIL
completion_time_minutes: <number>
rating_1_to_5: <number>
redacted_issue: <short description or NONE>
```
