# RC3 体积优化记录

日期：2026-10-25。版本：1.0.0-rc3 / 102。交付包，GitHub 标签为 v1.0.0-rc3。

## 结果

| 项目 | RC2 | RC3 |
| --- | --- | --- |
| APK 字节数 | 2,242,134 | 165,504 |
| DEX 类数量 | 1235 | 156 |
| Kotlin 类 | 1047 | 0 |

体积减少 92.62%，RC3 约 161.6 KiB。签名 SHA-256 与 E6/RC1/RC2 相同，包名不变，可覆盖安装。

## 改动与依据

- gradle.properties 添加 kotlin.stdlib.default.dependency=false，关闭 Kotlin Gradle 自动引入标准库。应用共 29 个 Java 源文件，无 Kotlin 源码，也未引用 Kotlin API。
- 依据：[Kotlin 官方 Gradle 配置说明](https://kotlinlang.org/docs/gradle-configure-project.html#dependency-on-the-standard-library)。
- app/build.gradle 更新版本号；MainActivity 关于页更新版本文本；tests/package_release.py 同步身份核验。
- 未开启混淆、资源裁剪或算法优化。29 个 Java 源文件与已发布 RC2 对照，除关于页版本号之外完全一致；APK 中应用自身的全部类仍保留。

## 本地检查

- Gradle 发布构建成功；Lint 0 错误、66 条非阻断提示。
- APK 完整性、安装身份、签名、非调试属性、无测试入口、权限与 E6 一致等检查通过。
- 11 个 E6 核心源文件逐字匹配 v0.6.0-e6。
- DEX 中没有 Kotlin 类；应用类集合与 RC2 相同。
- 既有 11,554 项离线断言的业务逻辑源码未改，本轮没有重复运行该套测试。build-info.json 中 offlineChecks 引用现有检查结果。
- 未运行手机、ADB、模拟器或实车，实际覆盖安装与运行由用户验证。
- 第一次源码对照脚本使用未解析的相对父路径，发生 FileNotFoundError；修正为绝对路径后对照通过。未影响构建或 APK。

SHA-256：`c06bacdf96c1d7627ea6eabd184196e5690f32f215af3f96cf743a288a6afb41`。

导航：[开发记录](开发记录.md)；主任务记录 ../Task.md。
