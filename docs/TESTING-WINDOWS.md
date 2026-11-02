# Windows 测试记录

日期：2026-11-01
环境：Windows 10.0.26300 x64，JDK 21（Temurin），Gradle 9.7.0 wrapper，迅雷 Java Desktop SDK 1.0.2。

托盘菜单方框问题已追加专项修复与验证，见 [TRAY-MENU-FIX.md](TRAY-MENU-FIX.md)。

## 自动化测试

`gradlew :shared:desktopTest`：

- 65 个单元测试，0 失败。
- 覆盖：JWT 解析与 `aud` → App ID、AuthManager 初始化/刷新/清除、HTTP(S)/thunder URL 校验、文件名推导与唯一化、SDK 状态映射、错误码映射、设置归一化、任务/设置持久化与 JSON 损坏回退、添加/去重/批内重复、暂停/继续/重试/删除、启动对账导入、缺失任务标记失败、完成通知只发一次。
- 日志脱敏：Key/JWT/签名参数的 Redactor 测试通过。

## SDK 实测（摘要）

- 正确 Key：`getLoginToken` → JWT `aud` → `init`=0、`login`=0。
- App ID 必须传 JWT `aud` 的 Base64 原串；传解码后的 `xl-...` 会得到 `9116`。
- Cloudflare 1 MiB 与 64 KiB 测试文件下载完成，`stateCode=8`，落盘大小与 SDK 报告一致。
- `setDownloadSpeedLimit(-1)` 不限速；传 `0` 会被 SDK 当作 32 KB/s 下限。
- 同一 `cfgPath` 下 finished 列表跨进程保留，taskId 稳定；`getTaskInfo` 返回 JSON 字符串。
- 具体结论见 [SDK-NOTES.md](SDK-NOTES.md)。

## 手动 UI 验收

已执行并通过：

| 项目 | 结果 |
|---|---|
| 设置页并发数 3 → 4 → 3 | 立即生效，提示“设置已保存并生效” |
| 下载限速 128 → 0 并应用 | 保存成功；0 表示不限速 |
| 动态链接加速关闭/重新开启 | 开启时弹出“极低概率文件不一致”确认，确认后生效 |
| 批量添加（有效/重复/FTP/磁力/非法） | 逐条创建与汇总，非法协议显示中文原因 |
| 同名文件批量添加 | 自动生成 `名称 (1).ext`，不再触发 SDK 9403 |
| 多选 3 个任务并批量删除 | 确认后提示“已删除 3 个任务”，列表进入空态 |
| 关闭主窗口 | 窗口隐藏，进程与下载引擎继续运行（关闭到托盘） |
| 任务完成 | 1 MiB / 64 KiB 文件完成并写入下载目录 |
| 空态、统计卡、搜索框、筛选 chips | 布局正常，无截断 |
| 打包版启动 | `XLite.exe` 启动成功，自动读取本机 Key 并登录 |
| 打包版下载 | 64 KiB 文件完成；SHA-256 与 `curl` 直接下载完全一致 |
| 打包版重启 | 已完成任务保留、自动登录 |
| 单实例 | 第二次启动弹出“XLite 已在运行”对话框，确认后第二实例退出 |
| 深色主题 | `XLITE_THEME=dark` 截图正常；系统主题注册表轮询已实现 |
| 多选复选框 | 修复后直接点击复选框可选中，批量删除生效 |

尚未执行或需要发布前复核：

- 托盘菜单“显示主界面/暂停全部/继续全部/退出”的鼠标点击测试（AWT 托盘菜单不暴露在 UI Automation 树中）。
- 通知中心里点击完成通知打开文件的端到端测试。
- 下载中强制退出再启动：活动任务按 `desired=RUNNING` 自动恢复。
- 限速 32/128 KB/s 的实际速率观测（SDK 侧 32 KB/s 下限已实测）。
- 只读目录、磁盘满、超长文件名、断网等错误路径。
- 干净用户环境启动便携包（验证 native DLL 与 VC++ 运行库）。
- MSI 安装/卸载（需 WiX，当前网络下未执行）。

## 构建验证

- `:desktopApp:createDistributable` 生成 `desktopApp/build/compose/binaries/main/app/XLite`（214 MB 目录）。
- 单文件便携包：`tools/package-portable.ps1` → `dist/XLite-0.1.0-windows-x64.exe`（约 99 MiB），内嵌完整 app-image 与 Java 运行环境，保留 XLite 图标；Windows PowerShell 5 和 PowerShell 7 均完成打包。
- Go 启动器自动化测试通过：并发释放、中文和空格路径、缓存复用、不同产物隔离、损坏归档/越界路径拒绝、失败不残留半成品。
- 单文件 EXE 实际启动后，进程从 `%LOCALAPPDATA%/XLite/runtime/<哈希>/XLite.exe` 运行，检测到响应正常的 XLite 主窗口。
- `app/` 中只有 `dlsdk-desktop` 与 `dlsdk-native-windows-x64`，没有 Linux native JAR；native JAR 内含 `dk.dll`、`jni_dk.dll`。
- 打包版启动、登录和下载均成功，说明 NativeLoader 从应用 classpath 解压并加载 DLL 正常。
- MSI 默认不构建：WiX 仅在 `-PwithMsi=true -Pcompose.desktop.application.downloadWix=true` 或本机 `WIX_PATH` 可用时启用。
