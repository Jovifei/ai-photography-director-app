# 摄影导演 Android Beta：手机配置与首次使用说明

版本：`0.2.0-beta.1`
包名：`com.jovi.photoai`

这份说明针对第一次在真实 Android 手机上使用 Beta 的用户。完整链路是：

`安装 APK → 电脑和手机连接同一私有 Wi‑Fi → 启动本机 Qwen 服务 → 手机一次性配对 → 导入参考图 → 逐张分析 → READY-only 项目汇总 → 选择主参考 → Camera Director → 拍摄 → 系统另存为`

## 无 Qwen 服务时的离线使用

如果本机 Qwen 服务、Private Wi‑Fi 或配对条件尚未准备好，不要开始第 2–5 节的 AI 配置。你仍可安装 App、创建项目、私有导入最多 20 张参考图、选择或删除主参考、进入“无 AI 指导直接拍摄”、通过系统“另存为”保存照片，以及删除项目。

离线模式不会把示例内容当成当前照片分析：没有真实 `READY` 结果时，项目不会生成 AI 汇总，主参考也不会进入 AI Camera Director。等待服务条件准备好后，再按下面步骤启用真实分析。

如果你已经从获批流程取得 Photo Knowledge Bundle v1 JSON，可在项目看板选择“导入离线知识包”。系统文件选择器只授予本次读取权限；App 会验证格式和摘要，再要求你逐条选择对应的项目照片。该入口不需要 Qwen、Wi-Fi 或配对，但它不会自动扫描电脑、夜间管线或手机目录，也不会生成项目级语义汇总。没有获批知识包时可以忽略此入口。

## 0. 先确认当前边界

- 本 App 的 AI 分析运行在你自己的 Windows 电脑上，不在手机上下载模型。
- 电脑和手机必须在同一个私有局域网；不要使用公共 Wi‑Fi、访客 Wi‑Fi、VPN 或移动热点隔离网络。
- 服务使用 HTTPS、一次性配对码和证书 pin。配对码只显示一次，服务退出后失效。
- 本说明中的命令会显示局域网地址、配对码或证书 pin。它们只应在你自己的电脑和手机之间使用，不要上传到聊天、截图群或 Git。
- 当前 Beta 仍是封闭测试版本；不要把照片、原始 AI 输出、URI、文件名、设备序列号或配对材料发给开发者。

## 1. 安装手机 App

1. 在手机上允许本次 APK 安装来源（Android 会按系统显示“允许此来源”）。测试结束后可以关闭该开关。
2. 打开 `photo-director-0.2.0-beta.1-*.apk` 完成安装。
3. 启动应用，确认名称是“摄影导演”。
4. 第一次进入拍摄时，允许相机权限。App 不申请 `READ_MEDIA_IMAGES`、`READ_EXTERNAL_STORAGE` 或 `WRITE_EXTERNAL_STORAGE`。

如果手机上已经有旧 Beta，使用更新安装即可；不要为了排查问题先清除 App 数据，因为这会删除本机项目状态。

## 2. 准备 Windows 电脑

### 2.1 确认模型已在本机

本项目使用现有离线 `Qwen/Qwen3-VL-2B-Instruct`，不下载模型、不从 Hugging Face 联网。默认目录是：

```text
E:\AI_Tools\Other\LocalLLM\Qwen3-VL-2B-Instruct\89644892e4d85e24eaac8bacfd4f463576704203
```

目录中应有 `config.json`、`preprocessor_config.json`、`photoai-model-manifest.json` 和权重文件。若目录缺失，先不要启动服务；模型安装/核验不在手机端完成。

### 2.2 确认 Python 环境

推荐使用项目准备好的 Python：

```text
E:\AI_Tools\Other\LocalLLM\photoai-service-venv\Scripts\python.exe
```

若该路径不存在，请使用已经安装 `torch`、`transformers`、`fastapi`、`uvicorn`、`cryptography` 的 Python 环境，并把下面命令中的 `$python` 改成它的绝对路径。不要在本轮临时下载模型或自动升级 Transformers。

### 2.3 把 Windows 网络设为 Private

手机和电脑连接同一个私有 Wi‑Fi 后，在 PowerShell 查看本机 IPv4 地址和网络类别：

```powershell
Get-NetConnectionProfile | Select-Object InterfaceAlias,NetworkCategory
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object {$_.AddressState -eq 'Preferred' -and $_.IPAddress -notlike '127.*'} |
  Select-Object IPAddress,InterfaceIndex
```

必须满足：

- 使用 `10.x.x.x`、`172.16.x.x`–`172.31.x.x` 或 `192.168.x.x` 的地址；
- 对应网络类别是 `Private`；
- 手机能访问这台电脑所在的同一 Wi‑Fi；
- 不使用 `127.0.0.1`、`169.254.x.x`、公共地址或 VPN 虚拟接口。

如果类别是 `Public`，请在 Windows“设置 → 网络和 Internet → Wi‑Fi → 当前网络属性”把网络配置文件改为“专用”。公司网络、访客网络或安全软件可能禁止修改；这种情况下不要绕过策略，换用你控制的私有 Wi‑Fi。

把下面的 `$lanIp` 替换为电脑在该 Private Wi‑Fi 上的 IPv4 地址；不要把地址写入仓库或反馈记录。

## 3. 启动本机 Qwen HTTPS 服务

所有服务会话都在仓库外创建临时 TLS 目录；服务退出后脚本会清理证书、私钥和临时防火墙规则。

在 PowerShell 中执行：

```powershell
$repo = 'E:\project\_worktrees\ai-photography-director-app-android-beta-release-candidate'
$python = 'E:\AI_Tools\Other\LocalLLM\photoai-service-venv\Scripts\python.exe'
$model = 'E:\AI_Tools\Other\LocalLLM\Qwen3-VL-2B-Instruct\89644892e4d85e24eaac8bacfd4f463576704203'
$lanIp = '192.168.x.x' # 改成上一步确认的 Private Wi‑Fi IPv4
$session = 'E:\project\_benchmark_evidence\android-closed-beta-pilot\phone-session-<UTC>'

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  (Join-Path $repo 'scripts\start_photoai_pilot_session.ps1') `
  -Mode Run `
  -LanIp $lanIp `
  -SessionDirectory $session `
  -PythonPath $python `
  -ModelDirectory $model `
  -Port 8443
```

脚本会先检查模型、Python、Private 网络和 `8443` 端口；检查失败时不会启动服务，也不会创建防火墙规则。成功启动后，服务窗口会打印一行：

```text
PAIRING_CODE=一次性代码
```

保持这个 PowerShell 窗口运行，不要关闭。服务只接受当前会话的一个配对请求。

### 3.1 找到证书 pin

服务启动后，在另一个 PowerShell 窗口读取临时 TLS 摘要：

```powershell
Get-Content -Raw -Encoding UTF8 (Join-Path $session 'tls\tls-summary.json')
```

复制其中的 `spki_pin` 字符串（它以 `sha256/` 开头）。只复制值，不复制 JSON 标记。例如输入框需要的是：

```text
sha256/……
```

不要把 `key.pem` 内容发给任何人；它是服务私钥。若找不到摘要或摘要中没有 `spki_pin`，停止配对并关闭服务，不要猜测 pin。

## 4. 在手机中配对

1. 打开“摄影导演”，创建一个拍摄项目。
2. 在项目页选择“连接本机分析电脑”。
3. 填写：

   - **HTTPS 地址**：`https://<电脑 Private IPv4>:8443`，例如 `https://192.168.x.x:8443`；不要填写 `localhost` 或 `127.0.0.1`。
   - **一次性配对码**：服务窗口打印的 `PAIRING_CODE` 值。
   - **证书 pin**：`tls-summary.json` 中的 `spki_pin` 值，包含 `sha256/` 前缀。

4. 点击“配对并继续”。配对成功后，代码立即失效，Android 只在当前进程内保留访问令牌；服务端不把令牌写入文件。

如果配对失败：

- 确认手机和电脑仍在同一个 Private Wi‑Fi；
- 确认地址使用电脑的 Private IPv4，端口是 `8443`；
- 确认服务窗口仍在运行；
- 确认 pin 完整复制且保留 `sha256/`；
- 不要重复使用已经消费过的配对码；重启服务会生成新的配对码和新的临时证书。

## 5. 导入和逐张分析 20 张照片

1. 在项目页点击“导入参考图”。
2. 使用 Android 系统 Photo Picker 选择照片；最多 20 张。App 会逐张导入，并保存去除 EXIF 的应用私有 JPEG。
3. 点击项目中的“开始分析”或逐项分析入口。每张照片独立显示 `QUEUED → RUNNING → READY`，失败项显示 `FAILED/UNAVAILABLE`，可以单项重试。
4. 等待所有需要的照片完成。不要把 `示例指导 · 非图片分析` 当成真实 AI 结果；没有服务或真实分析失败时，App 不会用 Demo 冒充结果。
5. 打开“项目汇总”。汇总只读取 READY 项；FAILED、UNAVAILABLE、CANCELLED 的正文不会发送给汇总服务。
6. 检查共同场景、光线、构图、主体和情绪方向，确认推荐主参考；你可以覆盖模型推荐。

## 6. 进入 Camera Director、拍摄和保存

1. 在项目页选择主参考，进入“开始拍摄”；如果不想使用参考指导，也可以进入“无指导直接拍摄”。
2. 第一次拍摄允许相机权限，等待相机控件显示“拍摄”。
3. 点击“拍摄”，完成后点击“保存照片”。
4. Android 系统会打开“另存为”选择器；选择手机存储或你自己的文件位置并确认。
5. 取消选择器或保存失败不会删除应用缓存，可以修正位置后再次点击“保存照片”。缓存不是长期相册，重要照片请完成系统另存为。

## 7. 结束服务和清理

1. 完成测试后回到服务 PowerShell 窗口按 `Ctrl+C`。
2. 脚本会清理临时防火墙规则、TLS 证书和私钥目录；不要手工复制或保留 `key.pem`。
3. 关闭服务前不要强制删除会话目录。如果脚本报告清理失败，先停止使用 App，并记录错误码，不要把私钥或完整日志发出来。
4. App 内可删除单项或确认清空项目；不要使用系统“清除数据”代替产品删除流程。

## 8. 常见状态与反馈方式

| 现象 | 含义 | 正确处理 |
| --- | --- | --- |
| `P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE` | 网络不是 Private | 切换到你控制的 Private Wi‑Fi，不要放宽脚本检查 |
| `P20_PILOT_SESSION_BLOCKED_NETWORK_INTERFACE` | 地址不是可用硬件接口 | 重新选择 Private Wi‑Fi 的 Preferred IPv4 |
| `P20_PILOT_SESSION_BLOCKED_RUNTIME` | Python/模型/依赖不完整 | 先核验本地模型和 Python 路径，不下载新模型 |
| 配对失败 | 地址、配对码或 pin 不匹配 | 重启服务取得一组新的三项输入 |
| 单项 FAILED/UNAVAILABLE | 该照片的真实分析失败 | 记录状态和错误码，单项重试；不要把 Demo 当结果 |
| 保存照片失败 | 系统目标位置或输出流失败 | 保留缓存，换一个保存位置重试 |

反馈只写：测试编号、步骤、PASS/FAIL、耗时、1–5 分体验评分和脱敏问题描述。不要写照片内容、姓名、账号、GPS、URI、文件名、设备序列号、配对码、证书 pin、私钥或原始 AI 输出。

## 9. 退出前检查清单

- [ ] App 已安装并能启动“摄影导演”。
- [ ] Windows 和手机在同一 Private Wi‑Fi。
- [ ] 服务 Preflight PASS，模型保持离线。
- [ ] HTTPS 地址、一次性配对码、`sha256/…` pin 已正确填写。
- [ ] 参考图逐张达到预期状态，项目汇总只显示 READY 内容。
- [ ] 主参考、Camera Director、拍摄和系统“另存为”完成。
- [ ] 服务退出后临时 TLS/私钥/防火墙资源已清理。
