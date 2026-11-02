# XLite — 迅雷加速下载器

XLite 是一个基于 **迅雷下载 SDK** 的纯净下载器：没有捆绑软件、没有广告弹窗，使用迅雷 P2SP 加速下载 HTTP(S) 资源。共享代码使用 Kotlin + Compose Multiplatform，同一套界面运行在 Windows、Android 与 Linux 桌面。

下载最新版本：[GitHub Releases](https://github.com/Sh1rokoDev/XLite/releases/latest)。Windows 提供目录式便携包和 MSI 安装包，Android 提供 APK，Linux 提供 AppImage。

> XLite 与迅雷官方无隶属关系，不使用官方品牌与图标。P2SP 加速效果取决于资源热度、API 配额与控制台配置，不承诺与迅雷官方客户端完全一致。

## 功能

- HTTP/HTTPS 下载，迅雷 P2SP 加速；自动识别 `thunder://` 链接并转换为 HTTP(S)
- 单条或批量添加链接，逐行校验、去重，同名文件自动编号
- 任务列表：搜索、状态筛选、任务详情；按任务状态显示批量操作
- 暂停 / 继续 / 重试 / 删除（可选同时删除文件），支持多选批量操作
- 全局同时下载数（1–10）、下载限速、上传开关与上传限速、动态链接加速开关
- 下载来源详情：原始服务器 / P2P / P2S 流量与占比
- 任务持久化与重启恢复，并与 SDK 侧任务对账
- 下载完成系统通知；Windows 托盘、关闭到托盘、单实例
- 桌面目录选择、打开文件/文件夹；Android 前台下载服务、打开/分享文件
- API Key 仅保存在本机：Windows DPAPI、Linux 0600、Android Keystore 加密
- 跟随系统的浅色/深色主题，中文界面

## 平台支持

| 平台 | 状态 | 说明 |
|---|---|---|
| Windows 10/11 x64 | 已开发、已构建 | 目录式便携 ZIP + 支持升级的系统 MSI；均内置 Java，数据位于程序目录 |
| Android 8.0+ (API 26) | 已开发、已构建 | **仅 arm64-v8a**；未做真机测试 |
| Linux x64 桌面 | 已开发、已构建 | 单文件 AppImage；已做 WSL/Xvfb 窗口启动检查，未做实机测试 |

## 构建验证（2026-11-02）

- Windows 目录便携包与 MSI 系统安装包已构建；两者的设置、任务、日志、默认下载和运行时临时文件均位于程序目录。
- 便携包已在含中文、空格的路径启动，确认 JVM/JNA 临时目录位于包内。
- MSI 数据目录 ACL、固定升级标识、卸载/升级保留数据、安装路径记录通过产物检查；0.1.0 → 0.1.1 的升级元数据验证通过。未在本机实际安装或升级 MSI。
- 92 项 JVM 测试及 Android APK 构建通过；Linux AppImage 已重新封装并完成 WSL/Xvfb 窗口启动检查。

界面与来源统计更新见 [docs/UI-REFRESH.md](docs/UI-REFRESH.md)；打包和升级规则见 [docs/PACKAGING.md](docs/PACKAGING.md)。

## 重要限制

- 迅雷开放 SDK **只支持 HTTP/HTTPS**，不支持 BT/Torrent、磁力链、ed2k、FTP。
- **没有有效 API Key 就无法下载**，XLite 不内置第二套下载引擎。
- 客户端直接保存 API Key 属于迅雷官方 Demo/个人使用方式；生产分发应改为服务端签发登录 token。
- SDK 只提供全局限速，不支持每任务限速、自定义 HTTP Header、事件回调或批量创建。
- 完整限制见 [docs/LIMITATIONS.md](docs/LIMITATIONS.md)。

## 快速开始

### 1. 环境

- JDK 21（构建与打包）
- WiX 3（仅构建 Windows MSI；打包脚本按需下载 Compose 固定版本，亦可用 `-WixDir` 指定工具目录）
- Linux 打包需要 `curl`、`sha256sum`；脚本首次下载 AppImage 官方打包工具与静态 runtime，并校验固定 SHA-256
- Android SDK：platform 37、build-tools 37、platform-tools（仅 Android 构建需要）
- 在项目根目录创建 `local.properties`：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### 2. 获取迅雷 SDK

SDK 二进制不随仓库提交，需要从官方地址下载并生成本地 Maven 仓库：

```powershell
powershell -ExecutionPolicy Bypass -File tools/fetch-xunlei-sdk.ps1
```

```bash
./tools/fetch-xunlei-sdk.sh
```

脚本会校验 SHA-256 并生成 `third_party/xunlei/m2`。校验和见 [third_party/xunlei/SDK-SHA256.txt](third_party/xunlei/SDK-SHA256.txt)。

### 3. 运行与构建

```bash
# Windows 开发运行
gradlew.bat :desktopApp:run

# Windows 目录式便携 ZIP（内置 Java，整包解压后启动 XLite.exe）
powershell -ExecutionPolicy Bypass -File tools/package-portable.ps1

# Windows 系统安装包 MSI（后续递增版本后运行新版 MSI 原位升级）
powershell -ExecutionPolicy Bypass -File tools/package-installer.ps1

# 已有当前版本 app-image 和 WiX 时，跳过 Gradle 构建、重新封装
powershell -ExecutionPolicy Bypass -File tools/package-portable.ps1 -SkipBuild
powershell -ExecutionPolicy Bypass -File tools/package-installer.ps1 -SkipBuild

# Android debug APK
gradlew.bat :androidApp:assembleDebug

# Linux x64（在 Linux/WSL 中）
bash ./tools/package-linux.sh

# 单元测试
gradlew.bat :shared:desktopTest
```

分发产物位于 `dist/`：

- `XLite-0.1.2-windows-x64-portable.zip`：整包解压到可写目录，运行其中的 `XLite/XLite.exe`。`app/`、`runtime/` 为普通文件；设置、任务、日志、临时文件在 `data/`，默认下载在 `downloads/`。启动时不向用户缓存目录释放运行环境。
- `XLite-0.1.2-windows-x64-installer.msi`：系统级安装向导，默认安装到 Program Files 下的 XLite，支持选择目录、开始菜单和桌面快捷方式。数据同样保存在安装目录的 `data/`、`downloads/`；仅这些数据子目录允许普通用户写入。后续运行更高版本 MSI 会识别并替换旧版本，沿用安装路径，保持一个已安装程序条目。
- `XLite-0.1.2-linux-x64.AppImage`：内置 Java 的 Linux AppImage；首次下载后执行 `chmod +x` 或在属性中允许执行。无 FUSE 的环境可用 `--appimage-extract-and-run`。
- `XLite-0.1.2-debug.apk`：Android APK，保持原有安装方式。

`gradle.properties` 的 `xliteVersion` 是版本号来源。每次发布必须递增，Windows 始终保持 `upgradeUuid` 不变；Android 同时递增 `xliteAndroidVersionCode`。便携版升级时退出程序，用新版程序文件替换旧文件并保留 `data/` 和 `downloads/`。系统安装版升级和卸载均保留这些用户数据目录。详细命令、旧版数据迁移与验收边界见 [docs/PACKAGING.md](docs/PACKAGING.md)。

Linux 已有构建可用 `bash tools/package-linux.sh --skip-build` 重新封装；`XLITE_APP_DIR` 可指定 Linux app-image 目录。打包工具缓存于 `desktopApp/build/appimage-tools/`，固定版本并校验 SHA-256；产物复用该工具内嵌的静态 AppImage runtime，不依赖上游 continuous 下载。

构建要求使用 JDK 21。若系统默认 JDK 不是 21，设置 `JAVA_HOME` 后再执行 Gradle。

### 4. 配置 API Key

首次启动会显示配置引导：在应用内粘贴迅雷开放平台 API Key，点击“测试连接”，成功后保存。App ID 会从登录 token 的 JWT `aud` 自动解析；如自动识别失败，可在“高级选项”手动填写。

API Key 不会写入源码、Gradle 文件、日志或任务数据。日志中的 Key、JWT 与签名参数会被脱敏。

## 项目结构

```
shared/       Compose Multiplatform UI + 领域核心 + Android/桌面平台适配
androidApp/   Android 清单/资源壳（无 Kotlin 代码）
desktopApp/   Windows/Linux 桌面入口、托盘、打包配置
tools/        SDK 获取、Windows/Linux 打包脚本
docs/         限制、架构、Windows 测试与 SDK 实测记录
third_party/  迅雷 SDK 校验信息与本地 Maven 仓库（下载生成）
```

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 模块分层、任务生命周期、鉴权与持久化
- [docs/LIMITATIONS.md](docs/LIMITATIONS.md) — 协议、SDK 能力、平台和打包限制
- [docs/TESTING-WINDOWS.md](docs/TESTING-WINDOWS.md) — Windows 手动验收清单与结果
- [docs/SDK-NOTES.md](docs/SDK-NOTES.md) — 迅雷 SDK 实测结论
