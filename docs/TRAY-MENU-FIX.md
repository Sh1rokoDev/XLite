# Windows 托盘菜单方框排查记录

日期：2026-11-01。

## 现象与定位

截图中的四行方框对应“显示主界面、暂停全部、继续全部、退出”。蓝色托盘图标本身正常，问题位于菜单文字绘制。

1. `TrayController.kt` 中的中文字符串完整；菜单没有读取外部文本，也没有字节编码转换。
2. 原实现已经给 AWT `PopupMenu` 和每个 `MenuItem` 设置了中文物理字体，并调用 `canDisplayUpTo` 检查覆盖范围。
3. 在本机 JDK 21 上运行独立 AWT 弹出菜单对照程序：`Microsoft YaHei UI` 的 `canDisplayUpTo("显示主界面暂停全部继续退出")` 返回 `-1`，但实际弹出的菜单仍显示方框，复现用户截图。
4. Java2D 字形检查与 AWT Windows 原生菜单不是同一绘制路径。OpenJDK 的 `PlatformFont` 会把物理字体名映射到逻辑字体配置，`AwtFont` 再根据原生字体描述绘制。因此，单独指定微软雅黑并检查字形并不能验证原生菜单一定能够显示中文。

上游源码参考：

- [PlatformFont.java（字体族映射）](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/share/classes/sun/awt/PlatformFont.java)
- [awt_Font.cpp（Windows 原生字体绘制）](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/windows/native/libawt/windows/awt_Font.cpp)

结论：本机故障定位到 AWT 原生菜单字体路径；不是源文件中文损坏。未进一步修改 JDK 或 Windows 字体配置。

## 修复

- 保留 `SystemTray`、托盘图标、通知和双击显示主界面的行为。
- 用 Swing `JPopupMenu` / `JMenuItem` 绘制四项菜单，使用 Java2D 能实际使用的中文字体。
- 同时监听鼠标按下/释放中的 `isPopupTrigger`，适配平台触发时机。
- 使用可获取焦点的无边框弹窗，处理失焦和菜单取消；回调执行前隐藏菜单。
- 按鼠标所在屏幕的可用区域约束位置，避开任务栏及右下角溢出。
- 安装、卸载在 Swing EDT 上完成；重复安装不添加多个托盘图标，卸载释放菜单窗口。

## 验证结果与边界

- 本机可视验证：加载修复后的应用 JAR，通过诊断窗口转发右键事件到真实 `TrayIcon` 监听器，四项中文均正常显示。
- `:desktopApp:test`：2 项测试，0 失败、0 跳过。验证四项回调各执行一次、回调前菜单关闭、取消后重开、右下角工作区边界和窗口释放。
- `:shared:desktopTest`：通过 Gradle 检查，65 项既有测试结果复用（UP-TO-DATE），0 失败。
- `:desktopApp:createDistributable`：通过。
- `tools/package-portable.ps1 -SkipBuild`：通过；Go 启动器测试通过（缓存结果），生成约 98.8 MiB 的单文件 EXE。
- 未完成 Explorer 系统托盘右键到真实下载任务暂停/继续的端到端鼠标验收；本次验证用回调替身避免影响用户任务。多屏混合 DPI、Linux 实机和真实 Esc 按键仍需单独验收。

本次构建遇到 JDK 的 Unix-domain loopback 临时路径错误，命令级设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\XLD\desktopApp\build\tmp` 后构建通过；未修改系统环境变量。

## 使用新版

完全退出旧版进程，再启动 `dist/XLite-0.1.0-windows-x64.exe`。便携启动器按产物哈希使用独立运行缓存，无须清理设置、下载任务或旧缓存。
