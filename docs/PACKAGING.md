# 分发与升级

当前分发形式：Windows 目录式便携 ZIP、Windows MSI 系统安装包、Android APK、Linux AppImage。Windows 不再使用自解压单文件启动器，构建不再需要 Go。

## Windows 数据位置

两种 Windows 包的程序、Java 与依赖都位于程序目录内。设置、任务、密钥、日志与 SDK 状态位于 `<程序目录>/data/`；JVM/JNA/SDK 临时文件位于 `data/tmp/`；默认下载目录为 `<程序目录>/downloads/`。用户仍可自行选择下载目的地。

便携版整包解压到可写目录，运行 `XLite/XLite.exe`，保持 `app/` 和 `runtime/` 同在该目录。SDK DLL 在打包时展开到 `app/native/`，运行时不向用户缓存目录释放程序。整个目录可以移动；Windows DPAPI 密钥仍受原 Windows 账号和机器保护，换机器后需重新输入 API Key。

MSI 默认系统级安装至 Program Files 下的 XLite，可选择安装目录。安装器创建 `data/`、`data/tmp/` 和 `downloads/`，以不依赖系统语言的 SID ACL 为普通用户提供数据目录的修改权限。程序和运行环境目录不放宽写入权限。升级、修复和卸载不删除用户数据目录。

旧单文件版的数据不会自动迁移或删除。如需保留旧设置，先完全退出 XLite，再将旧 `%LOCALAPPDATA%/XLite/` 下的 `settings.json`、`tasks.json`、`secret.bin` 和 `xunlei/` 复制到新程序的 `data/` 中。不要复制旧 `runtime/` 缓存；不迁移密钥也可重新登录。

## 构建

在 Windows 使用 JDK 21，设置 `JAVA_HOME`。最终用户无需安装 Java。

```powershell
./tools/package-portable.ps1
./tools/package-installer.ps1

# 已有同版本 app-image 时跳过 Gradle 构建；MSI 仍会重新封装
./tools/package-portable.ps1 -SkipBuild
./tools/package-installer.ps1 -SkipBuild

# 自备 WiX 3 工具目录
./tools/package-installer.ps1 -WixDir C:/tools/wix3
```

安装脚本会调用 Compose 的固定 WiX 下载任务，然后使用 JDK `jpackage` 和项目的 WiX 扩展生成 MSI。它从当前构建 JDK 的模板添加数据目录、权限和安装路径记录，保留标准升级逻辑并禁止降级。发布脚本会检查 MSI 结构后才将产物复制到 `dist/`。

```text
dist/XLite-<版本>-windows-x64-portable.zip
dist/XLite-<版本>-windows-x64-installer.msi
dist/XLite-<版本>-debug.apk
dist/XLite-<版本>-linux-x64.AppImage
```

便携包每次从干净 app-image 新建 staging，避免把运行过的配置或密钥打进 ZIP。`-SkipBuild` 会检查 app-image 版本和本地数据路径选项，拒绝使用不匹配的中间产物。

Android 继续执行 `./gradlew.bat :androidApp:assembleDebug`，APK 位于 `androidApp/build/outputs/apk/debug/`。Linux 继续在 Linux/WSL 中执行 `bash tools/package-linux.sh`；已有 Linux app-image 可使用 `--skip-build`，`XLITE_APP_DIR` 可指定其目录。

## 后续升级

1. 修改 `gradle.properties` 中的 `xliteVersion`，例如 `0.1.0` → `0.1.1`。Windows 使用三段数值版本，最大为 `255.255.65535`。Android 同时递增 `xliteAndroidVersionCode`。
2. Windows 固定保持名称 `XLite`、系统级安装范围和 `upgradeUuid=6f0f2c9e-6b6a-4f6c-9d2c-1f4a5b7c8d90`。更改 UUID 会被 Windows 当成另一款程序。
3. 运行两种打包脚本。也可临时传入 `-Version 0.1.1` 验证其他版本，但正式发布应提交统一版本号。
4. 用户完全退出旧版后运行新版 MSI。安装器检测旧版、移除旧程序、沿用安装目录并保留数据，系统只保留一个 XLite 安装条目。同版本是维护/修复，不替代递增版本发布；降级被阻止。
5. 便携版升级时替换程序文件，保留原 `data/`、`downloads/`，不要删除整个旧目录后再解压。

这里的升级指用户执行新版 MSI，尚未实现应用内自动下载更新。

## 验证

```powershell
./gradlew.bat :shared:desktopTest :desktopApp:test :androidApp:assembleDebug
./tools/test-windows-packages.ps1 `
  -PortableZip dist/XLite-0.1.0-windows-x64-portable.zip `
  -Installer dist/XLite-0.1.1-windows-x64-installer.msi `
  -PreviousInstaller dist/XLite-0.1.0-windows-x64-installer.msi
```

检查内容包括固定升级标识、递增产品版本及变化的产品标识、旧产品移除规则、系统级安装、目录 ACL、安装路径读取、保留数据组件，以及便携包真实文件结构、程序内临时目录和无用户数据。

2026-11-02 已构建 0.1.0 和用于验证的 0.1.1 MSI，升级元数据检查通过；便携包在含中文/空格的路径实际启动，JVM 属性、日志和 JNA 临时文件均位于该包内。未在本机实际安装或升级 MSI，仍需在干净 Windows 虚拟机验证首次安装、自定义路径、普通用户写入、升级后单一安装条目和数据保留。

打包参数依据 [Compose 原生分发文档](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) 与 [JDK 21 jpackage 文档](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)。

本轮 Linux AppImage 使用已有 Linux Java/原生运行环境，替换本轮编译的应用与共享 JAR 后重新封装；已在 WSL/Xvfb 下启动并检测到 1100×720 的 XLite 窗口，未进行 Linux 实机下载验收。
