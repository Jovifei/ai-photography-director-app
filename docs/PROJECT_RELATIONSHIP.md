# Project Relationship

本仓库是 Photo Intelligence Bundle 的 consumer 和 Android 首发现场执行端。兄弟仓库 `nightly-photo-intelligence-pipeline` 是 producer。两者不共享数据库或业务源码，只通过 `shared-contract/` 的 Schema、Fixture、Manifest 和 Hash 通信。未来 iOS 仍在本 App 仓库中以独立 `ios/` 目录实现，但只有 Android Gate 后才能创建。
