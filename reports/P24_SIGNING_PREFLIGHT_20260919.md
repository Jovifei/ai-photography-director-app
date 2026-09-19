# P24 Release Signing 只读预检

状态：`P24_BLOCKED_OWNER_SIGNING_DECISION`

## 绑定

- 验证基线：`main@6a42feced043297aac9eabe677bb4ba23b9154fd`
- 预检 worktree：`codex/p24-signing-preflight-20260919`
- 本轮只读范围：Release signing guard、公开 identity metadata、Owner 环境变量存在性。

## 结果

- `android/release-signing-identity.properties` 存在；package、role、keyAlias 存在性、certificate SHA-256 格式和 version 字段均通过格式检查。文件中的值未写入报告。
- 以下四个环境变量均缺失：`PHOTOAI_RELEASE_STORE_FILE`、`PHOTOAI_RELEASE_STORE_PASSWORD`、`PHOTOAI_RELEASE_KEY_ALIAS`、`PHOTOAI_RELEASE_KEY_PASSWORD`。
- `android :app:verifyReleaseSigning` 真实退出码为 `1`，错误为 `P20_BLOCKED_RELEASE_SIGNING_INPUT: missing PHOTOAI_RELEASE_STORE_FILE`。
- 未生成新生产 keystore，未读取或上传密码，未生成 signed APK/AAB，未修改 signing guard。

## 下一步决策

Owner 需要在以下两项中明确选择一项：

1. 恢复并通过环境变量提供既有长期 signing identity；或
2. 明确授权创建新的长期 signing identity。

在 Owner 决策和材料安全提供前，不执行签名构建，不进入 P25 内容生产，也不启动 Qwen、LAN、Pipeline、真实照片、实体设备或发布流程。
