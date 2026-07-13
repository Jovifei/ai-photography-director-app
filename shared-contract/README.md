# Photo Intelligence Bundle v1

该目录是 App 与 Nightly Pipeline 的唯一共享合同。两个仓库应复制 Schema 和 Fixture，并在 CI 中运行兼容测试；不要把两个仓库直接依赖到本目录的绝对路径。

- Pipeline：只导出通过人工审核的项目；
- App：验证 major version、Schema、relative paths、review_status 和 SHA-256；
- 任何破坏性变化先创建 ADR，再升级 major version。
