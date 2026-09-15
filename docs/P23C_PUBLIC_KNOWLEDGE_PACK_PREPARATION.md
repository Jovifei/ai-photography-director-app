# P23C：公共知识包的离线交付准备

状态：`P23C_PREPARATION_IMPLEMENTED_LOCAL_VALIDATION_PENDING`。
本阶段实现 App 消费合同对应的生产端兼容检查与审核候选打包；不运行 Pipeline，
不产生真实公共知识、不修改 Android 生产逻辑，不构成内容导入或发布授权。

## 输入 → 输出

```text
已经生成的 v1 Bundle JSON（不是原图、数据库或模型原始输出）
  → inspect：字段、UTF-8、PKB1 摘要校验
  → review-template：生成逐条 PENDING 审核表
  → 人工在外部完成内容/权利/隐私审查并记录证据
  → prepare：验证审核表与原始文件、payload、用途、条目完全绑定
  → 未签名审核候选 ZIP
  → verify-candidate：离线重验，不解压、不导入 App
```

生产输入仍需 Owner 明确授权及独立批准的公共语料/生产证据；当前本地接力只能使用合成数据。
命令行没有“自动批准”“修复摘要”“自动补字段”“扫描目录”或“调用模型”选项。

## 命令行

依赖 Python 3.10+ 标准库；Windows 需要支持硬链接的本地文件系统。本轮 Linux 实测，Windows 待复验。
所有输出必须在仓库外、已经存在的 Owner 受控目录中。不会创建父目录或覆盖已有文件。
下面 `$Evidence` 指向一个新的、已创建的仓库外目录；这些命令不生成 APPROVED 回执。

```powershell
$Bundle = 'android/app/src/androidTest/assets/p23c/roundtrip.bundle.json'
python scripts/p23c_prepare_review_pack.py inspect --bundle $Bundle
python scripts/p23c_prepare_review_pack.py review-template --bundle $Bundle --purpose SYNTHETIC_TEST --output "$Evidence/pending-review.json"
# 未完成回执应 BLOCKED，退出码 2，不能留下 candidate.zip：
python scripts/p23c_prepare_review_pack.py prepare --bundle $Bundle --receipt "$Evidence/pending-review.json" --purpose SYNTHETIC_TEST --output "$Evidence/candidate.zip"
```

合成成功路径由 unittest 在临时目录中构造模拟回执并验证：

```powershell
python -m unittest discover -s scripts -p "test_p23c*.py" -v
```

真实审核完成且另获授权后，`prepare` 使用真实回执；随后：

```powershell
python scripts/p23c_prepare_review_pack.py verify-candidate --archive "$Evidence/candidate.zip" --purpose PUBLIC_CANDIDATE
```

`PUBLIC_CANDIDATE` 要求恰好 20 条及 `source.origin=PIPELINE`。
这是“至少20条公共条目”与现有消费者“单包最多20条”的首包交集，不是修改 App 的 1–20 条通用合同。
`SYNTHETIC_TEST` 允许 1–20 条。两种用途的回执不能直接交叉使用。

## 审核回执 v1（独立侧文件，不增加 Bundle 字段）

根字段严格为：

| 字段 | 校验 |
|---|---|
| receipt_version | 精确 `1.0` |
| purpose | `SYNTHETIC_TEST` 或 `PUBLIC_CANDIDATE`，与命令参数一致 |
| bundle_id | 与已校验 Bundle 一致 |
| payload_sha256 | PKB1 小写摘要 |
| bundle_file_sha256 | 所选 Bundle **原始字节** SHA-256 |
| review_id / reviewer_id | 非空 opaque token |
| source_evidence_id / producer_qualification_evidence_id | 非空 opaque token，不包含文件地址或人员隐私 |
| entries | 精确覆盖所有 reference_id，一条一次，无缺漏、重复或额外条目 |

每条仅含 `reference_id`、`evidence_id`、`content_review`、`rights_review`、`privacy_review`。
三个检查全部为 `APPROVED` 才能准备候选。模板全部为 `PENDING`，身份和证据字段为 null。
证据正文在 Owner 控制的外部材料中，不能为通过测试编造真实人工审查或权利结论。

**此校验只证明回执结构、声明与字节绑定，不认证填表人，不核查证据正文，不自行判断版权许可。**
攻击者重新编造一份完整回执并计算摘要仍可能通过结构检查。因此所有输出明确保留
`publisher_authenticated=false`、`app_import_authorized=false`、`release_authorized=false`。
生产签名/信任根和批准流程属于后续阶段，不得用该工具 PASS 替代。

## 摘要域与不可变性

PKB1 与现有 `PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md` 相同：字段顺序、数组顺序、UTF-8 字节长度、
不裁剪、不进行 Unicode 规范化。JSON 外部格式或 CRLF 改变可能不影响 PKB1，
但会改变 `bundle_file_sha256`，因此已有回执须重新绑定，不能静默归一化后继续使用旧审批。

输入字段白名单和大小上限沿用 App：512 KiB、1–20 条、800 code points，导演提示1200。
预检拒绝重复键、无效 UTF-8/BOM、孤立代理字符、非标准 JSON、深度超过32、传输/路径类文本。
额外的生产侧保守限制：拒绝 Unicode U+2028/U+2029 行/段分隔符；不放宽 Android 校验。

## 候选归档

固定成员：`bundle.json`、`review-receipt.json`、`candidate-manifest.json`。
原 Bundle 与回执字节原样保留；固定时间/顺序/无压缩 ZIP，避免压缩库版本改变产物字节。
验包读取各成员前校验数量、名称、类型和上限，禁止路径穿越、额外/重复成员、压缩成员及尾随内容。
不调用解压，不把归档中的任何路径当作文件系统目标。

状态始终为 `UNSIGNED_NOT_FOR_DISTRIBUTION`；不存在任何已签名/已认证的含义。
**ZIP 不是 Android OpenDocument 的新输入格式。** 在另获批准的导入试验中只交付其中原样的 `bundle.json`；
App 继续逐项显式映射已有参考照片，不能按回执顺序猜对应关系。

当前 v1 Bundle 不含参考图片、签名、模型身份或项目级语义汇总；本阶段没有解决公共参考图
授权交付、发布者签名验证或生产流水线执行。APK keystore 也不等于知识包签名身份。

## 文件系统边界

只打开用户显式指定、扩展名符合预期的普通本地文件，限定读取量。
拒绝网络路径、路径遍历、符号链接、Windows reparse/junction 和多硬链接输入；读取前后检查文件身份。
输出路径不得位于当前源码仓库或任一带 `.git` 标记的工作树内。
文件先完整写入同目录临时文件并 fsync，再以硬链接不替换发布；不支持硬链接就 BLOCKED，
不能降级成可能覆盖目标的 rename/replace。命令错误只输出固定错误码，不回显正文/路径。

边界：目录须 Owner 受控且没有并发重命名攻击；这不是恶意本地文件系统沙箱。
不承诺断电后的目录项持久性；被系统强杀可能留下临时文件，需要 Owner 外部证据目录清理，
不能把文件名出现当成审核或发布通过。Windows 特性和杀进程行为仍需实际平台验证。

## 验证入口

Python：56 个 unittest 方法（22 合同 +34 回执/归档/CLI/文件安全），本轮 Linux 全部执行通过。
Android：`com.jovi.photoai.p23c.P23CPythonBundleCompatibilityAndroidTest`，4 个方法，**本轮未编译/运行**。
它使用 test APK 内的合成 Unicode JSON，而非生产 assets；对照真实 Android Parser 与 PKB1。

参考：RFC 8259（https://www.rfc-editor.org/rfc/rfc8259）、Python JSON
（https://docs.python.org/3/library/json.html）、Python os.link（https://docs.python.org/3/library/os.html#os.link）。
本轮自行实现，无第三方源码复制、无新增运行依赖；未将外部文档当作已锁定源码依赖。
