# P25U 本地 Codex 接力｜2026-09-24

## 接力目标

唯一 Gate：P25U_LOCAL_ANDROID_CAPTURE_QUALIFICATION_AND_REMEDIATION。
本地候选已完成 Android 资格验证和独立审查；Owner landing 决策待定。

## 当前候选

- 分支：codex/p25u-local-android-qualification-20260924
- 源码提交：d26f3cf81c57936da7d144dd17c5258f2d70d66a
- 独立审查通过的 delivery HEAD：753163efddfd8e28a6b267ac70af95cdd86c282e
- 交付目标：codex/p25s-internal-handoff-validation-20260920
- 源码提交父项/当前目标分支：832a87af933157741bb01ce77a5c54ef8940369c
- main：1b776ba9932a7fdc96112c8cb85c7258f3f7d6af，未修改

## 已完成

- Git-object manifest 24/24 精确匹配。
- Debug/JVM/AndroidTest/lintDebug：137/137 JVM，0 lint errors。
- P25/P23C Python、compileall、P25U 7+3+1 Android、磁盘三阶段通过。
- 默认 App 真实 CameraX 成片、外部 force-stop、独立 ActivityScenario 重开画廊、cleanup 通过。
- DocumentsUI 真实另存成功和取消各 1/1 通过。
- 导出 token 改为 rememberSaveable，Activity 重建后 Activity Result 仍绑定原 capture token；真实重建/取消/重试与 DocumentsUI 成功/取消共 3/3。
- Recovery verify 读取本轮 prepare marker，核对同一 capture ID、AVAILABLE 状态和原片 digest。
- 最终恢复 run `2e704f18fd914180b27ee96f1d5c3ab1`：PID `5994 → 空`，prepare/verify/cleanup 各 1/1。

## 阻断与边界

- P25U host core 因没有现有 kotlinc 为 NOT_RUN；未安装工具链。
- Release signing 为 P20_BLOCKED_RELEASE_SIGNING_INPUT；未生成新密钥。
- 首次独立 Reviewer 的 P1/P2 与证据 hash 问题已整改；最终 Reviewer 对 `753163ef` 返回 PASS、FINDINGS=NONE、OWNER_LANDING=ALLOWED。
- main landing、真实照片/Qwen/LAN/Pipeline、实体设备和发布仍不在本地 Android Gate 范围内。
- 物理设备不得作为测试目标。

## 下一步

Owner 可依据 Reviewer PASS 决定是否 landing 到 main；本任务没有执行合并。

外部逻辑证据 ID：p25u-local-android-qualification-20260924。
