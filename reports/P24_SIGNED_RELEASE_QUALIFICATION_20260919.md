# P24 Signed Release 资格报告

状态：`P24_SIGNED_RELEASE_ARTIFACT_QUALIFIED`

## 绑定

- 源码提交：`e19e873db30a31c13169800b768374d2ac6559c3`
- 分支：`codex/p24-signing-preflight-20260919`
- 包名：`com.jovi.photoai`
- versionName/versionCode：`0.2.0-beta.1` / `2`
- 逻辑证据 ID：`p24-20260919/release-final`

## Signing identity

- 采用项目已有 `scripts/bootstrap_android_release_identity.ps1` 生成长期 identity。
- 算法：RSA-4096、PKCS12、SHA256withRSA。
- alias：`photo-director-release-v1`。
- certificate SHA-256：`C62A1EE1F293CB596571135D37C9D51E7523E1639FBFAA01A6AB34D2528B457B`。
- 密钥库、recovery copy 和 DPAPI credential 均在仓库外受保护目录；没有提交 Git，也没有把密码写入报告。
- identity/recovery 三个文件的 SHA-256 均逐项匹配。

## Build / artifact verification

- `verifyReleaseSigning`：PASS。
- `assembleRelease`：PASS。
- `bundleRelease`：PASS。
- `lintRelease`：PASS，20 warnings、0 errors。
- `lintDebug`：PASS，24 warnings、0 errors。
- `testDebugUnitTest`：130/130，0 failure/error/skip。
- APK：`debuggable=false`、`apksigner` PASS、package/version 匹配。
- APK certificate SHA-256：`C62A1EE1F293CB596571135D37C9D51E7523E1639FBFAA01A6AB34D2528B457B`。
- APK SHA-256：`32AEE64C391D5F8463C93F31BD28188AAE6E7BC00D7D95FB61EC90FD37259DD0`。
- AAB `jarsigner -verify`：exit `0`。
- AAB SHA-256：`9BD937D103757E52B4B34CA11D98FCC4245F65E35F879229BCFA42402973C8C2`。

APK/AAB 只保存在仓库外 evidence 目录，没有进入 Git。签名构建证明签名身份、构建和产物完整性，不等于真实手机、真实 Qwen/LAN、内容审核、Pilot 或公开发布完成。

## 未执行

- 未安装实体设备；未运行实体设备 instrumentation。
- 未启动 Qwen、LAN、Pipeline；未读取真实照片。
- 未进入 P25 内容生产、P26 trust、P27 LAN、P28 真机、P29 Pilot 或 P30 闭测。
