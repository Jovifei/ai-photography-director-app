# P23C 本地离线交付准备复验

状态：`P23C_BLOCKED_WITH_EXACT_GATES`

## 绑定

- main：`f2d6a87f85ba3b2ddd9f83544a6d912890019e7c`
- P23C 远端起点：`20f05d570f643770881e1752f505d14757c53164`
- 本地 test-only source commit：`7edb307314598c526eabab457f71d152462b8cdf`
- 最终 docs commit：以本地验证分支实际 HEAD 为准。
- 逻辑 evidence ID：`p23c-20260916`

本轮只增加 P23C 测试 asset、test-only Room/重启 harness 和验收文档；没有修改 App 生产代码、Room、Provider、签名配置、shared-contract、旧 manifest 或 Owner tasks。

## Windows 工具

- unittest：`55/55` executed PASS，`0` failure/error，`1` skip；skip 是系统不允许创建 symlink 的权限限制，未计为 PASS。
- compileall：PASS。
- Bundle inspect：PASS。
- review-template：身份字段 null、条目状态 PENDING。
- PENDING prepare：exit `2`，未留下 ZIP。
- 合成 1 条、20 条 prepare/verify：PASS，输出均为 `UNSIGNED_NOT_FOR_DISTRIBUTION`，三个授权字段均 false。
- 1 条候选重复生成到不同路径：SHA-256 相同。
- junction/reparse、硬链接输入、网络路径、已有输出、跨仓库输出：均按预期 exit `2`。
- fsync/中断、归档篡改、回执缺项/重复/用途错误：由 Windows unittest 覆盖。

## 源码清单与范围

- P23C manifest：`8/8` Git object/blob/字节/SHA-256 匹配。
- source commit：`7edb307314598c526eabab457f71d152462b8cdf`。
- `f2d6a87..P23C` 仅包含 P23C 脚本、test-only asset/test 和文档；无生产 Android、签名、tasks 或 shared-contract 差异。

## Android

- 标准 Gradle：`BLOCKED`，缺少 `PHOTOAI_RELEASE_STORE_FILE`；未生成密钥。
- 排除 signing guard：Debug/Test APK、JVM、Debug/Release lint PASS。
- P23C Python compatibility test：`4/4`，真实 Android Parser。
- Python 生成 20 条 Bundle → 真实 Parser → 20 张程序生成合成参考图 → 真实 Repository/Room 显式绑定：PASS。
- 外部 `am force-stop` App 后重启，再读取20条 READY、PIPELINE provenance、无 provider provenance、无项目 summary：PASS。
- R1 fresh regression：`28/28`；既有 Android：`38/38`。
- Bundle contract：`12/12`；Phase 1.5：`75/75`；service：`5/5`；compileall、privacy、diff：PASS。

## NOT_RUN / BLOCKED

- Release signing/APK/AAB：`BLOCKED`，外部 signing input 缺失。
- READY/provider/source/summary 既有状态的 OS process death：`NOT_RUN`；本轮仅完成 Bundle READY/PIPELINE provenance 的合成持久化重启 harness。
- 真实公共语料、人工内容/权利审核、真实生产候选、知识包签名/信任根、Qwen、真实照片、LAN、Pipeline、Cloud、iOS、实体设备、公开发布：`NOT_RUN`。

本阶段不合并 P23C 到 main。下一步是对最终本地验证 SHA 做独立 Reviewer 审查；Owner 仍需单独授权真实公共内容和发布流程。
