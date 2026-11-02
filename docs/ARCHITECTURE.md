# 架构说明

## 1. 模块划分

```
androidApp    Android 应用壳：applicationId、Manifest、图标、主题、FileProvider XML
desktopApp    JVM 桌面入口：窗口、托盘、单实例、jpackage 配置
shared        KMP 共享模块
  commonMain    领域模型、引擎、仓库、鉴权、Compose UI
  androidMain   AndroidXunleiSdkPort、Keystore、前台服务、通知、FileProvider 操作
  desktopMain   DesktopXunleiSdkPort、DPAPI、路径、JFileChooser/Desktop 操作
```

`commonMain` 不引用平台 API。界面、任务状态机、持久化、错误映射与重试逻辑只实现一次。
Android 与桌面使用同一包名的迅雷 SDK（JAR / AAR），因此不能放进同一编译单元；两端各有一层窄适配器 `XunleiSdkPort`，只做原始类型转换。

## 2. 核心组件

| 组件 | 职责 |
|---|---|
| `AppContainer` | 手工依赖注入；单线程 SDK dispatcher、IO dispatcher、生命周期 |
| `AuthManager` | 获取 token、解析 JWT、初始化 SDK、登录、刷新、凭据清除 |
| `XunleiEngine` | 添加/暂停/继续/重试/删除、轮询、重启对账、设置下发 |
| `XunleiSdkPort` | SDK 的阻塞式平台适配接口（仅基础类型/DTO） |
| `TaskRepository` | 任务列表状态流、原子 JSON 持久化、500ms 防抖 |
| `SettingsRepository` | 设置校验、持久化 |
| `SecretStore` | API Key 加密存储（DPAPI / Keystore / 0600） |
| `PlatformActions` | 打开文件/目录、分享、系统通知、目录选择 |
| `Redactor` / `AppLog` | 日志脱敏与 2MB×3 轮转 |

## 3. 鉴权流程

1. 用户输入 API Key，调用 `getLoginToken(key, 86400, ["xtask.p2sp","xindex.query"])`。
2. 本地解析 JWT：
   - `exp` 为过期时间；
   - `aud` 本身是一段 Base64，解码后是“可打印 App ID + NUL + 二进制数据”。
3. **SDK `init()` 接收的 App ID 是 JWT `aud` 原字符串**，不是解码后的前缀；传解码值会得到 `9116 APPNAME_APPKEY_ERROR`。
4. `init(appId, version, cfgDir, saveTasks=true)` → `login(token)`；`9101 ALREADY_INIT` 视为成功。
5. token 只保存在内存；开始 24 小时有效，剩余不足 30 分钟时后台刷新并重新登录。
6. SDK 返回认证类错误（9602–9605、9116）时刷新 token 并重试一次；仍失败则进入“登录过期”门禁。
7. 更换 Key 会停止活动任务、`uninit()`、使用新凭据重新初始化并恢复原先 `desired=RUNNING` 的任务。

## 4. 任务状态与生命周期

本地模型 `DownloadTask` 同时保存“用户意图”和“最近状态”：

- `desired`: `RUNNING` / `PAUSED` / `NONE`
- `status`: 由 SDK `stateCode` 归一化（3 等待、4 启动、5 下载、6 暂停中、7 已暂停、8 完成、9 失败）

命令都在单线程 SDK dispatcher 上串行执行，避免静态 SDK 的线程安全与重入问题：

- 添加：校验 URL → 检查重复 → 计算唯一文件名 → `createP2spTask` → 本地落库 → `startTask`
- 暂停：`stopTask` → `desired=PAUSED`，等待状态变为 7
- 继续：`startTask` → `desired=RUNNING`
- 重试：先 `startTask`；若 SDK 报告任务不存在/已完成/状态错误，则用相同 URL、目录、文件名重建任务
- 删除：活动任务先停止，再 `deleteTask`，成功后移除本地记录

所有任务操作期间 UI 显示 pending，避免重复点击。

## 5. 轮询与对账

- 有活动/过渡任务时每 1 秒轮询，空闲时每 3 秒。
- 每 10 个 tick 调用一次 `getUnfinishedTasks` / `getFinishedTasks` 对账。
- 启动时：
  - SDK 有、本地无 → `getTaskInfo` 导入；
  - 本地有、SDK 无 → 未完成任务标记失败；已完成历史保留；
  - `desired=RUNNING` 但 SDK 已暂停 → 补发 `startTask`；
  - 已完成任务只通知一次。
- `getTaskInfo` 返回 JSON 字符串（如 `{"save_name":"x.zip"}`），导入时先解析再取值。

## 6. 持久化

Windows：`%LOCALAPPDATA%\XLite\`；Linux：`${XDG_DATA_HOME:-~/.local/share}/XLite/`；Android：应用 `filesDir`。

| 文件 | 内容 |
|---|---|
| `settings.json` | 设置，原子写 + 校验归一化 |
| `tasks.json` | 任务列表，防抖原子写，退出强制 flush |
| `secret.bin` | 加密后的 API Key |
| `xunlei/` | 迅雷 SDK 配置与 SDK 任务持久化 |
| `logs/xlite.log` | 轮转日志（2MB×3，脱敏） |

下载目录：Windows `%USERPROFILE%\Downloads\XLite`、Linux `~/Downloads/XLite`、Android `getExternalFilesDir("downloads")`。

## 7. UI 与平台

- `App(container, platform, themeOverride)` 由两端入口复用，依据可用宽度切换完整侧边栏、窄导航栏或顶部导航与添加按钮；文件、分享和托盘操作仍由平台能力决定。
- 无 Key/过期时显示鉴权门禁；有缓存任务时显示顶部横幅并禁用操作。
- 主题跟随系统：Android 用 `isSystemInDarkTheme`；Windows 轮询注册表 `AppsUseLightTheme`；Linux 失败回退浅色。
- Windows 托盘使用 AWT `SystemTray`；关闭窗口按设置隐藏到托盘；单实例通过 `%LOCALAPPDATA%\XLite\instance.lock` 文件锁实现。
- Android 下载期间运行 `dataSync` 前台服务，持有 partial wakelock，空闲后停止；FileProvider 提供打开/分享。
