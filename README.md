# OpenSAICLink

## 当前候选版本：1.0.0-rc2（2026-10-25）

新版仍处于试验阶段，以已验收 E6 核心为基础，提供手机连接/应用/设置三页、玻璃风格车载九宫格、八个自定义应用位和悬浮主页。RC1 已收到用户初步测试正常的反馈；RC2 增加首页使用说明，尚待用户验证。

**使用说明：本应用目前处于试验阶段。投屏时，部分图层不会在车机上显示；部分应用需要开启手机的“自动旋转屏幕”功能。**

- [下载 RC2 APK](https://github.com/ZxxXinI/OpenSAIClink-Trial/releases/download/v1.0.0-rc2/OpenSAICLink-1.0.0-rc2.apk)
- [RC2 发布页](https://github.com/ZxxXinI/OpenSAIClink-Trial/releases/tag/v1.0.0-rc2)
- [新版源码与功能说明](OpenSAICLink/README.md) · [本次变更与核验](OpenSAICLink/docs/RC2发布记录.md)
- [构建与签名](OpenSAICLink/docs/构建与签名.md) · [使用说明](OpenSAICLink/docs/使用说明与实车检查.md)

最低 Android 14，主要适配 REDMI K80 与 A7 车机。沿用 org.opensaiclink.trial 包名和 E6 签名，可覆盖 E6 / RC1。声音继续走蓝牙；本次未调整方向控制、通知中心或通信/触控核心。

新版源码位于 `OpenSAICLink/`。以下根目录工程与文档保留为 E6 历史记录，E6 标签与 Release 不变。

---

# OpenSAICLink 地图投屏试验 E6

## 当前版本：E6（2026-10-24）

E6 已由用户完成 K80 实车验收并确认功能正常。移除原桌面辅助横屏，增加无障碍投屏前置、第三行三个应用位，修正双指抬起坐标，提供悬浮主页返回；USB 音频继续暂停。

- [E6 安装包](dist/OpenSAICLink-E6-0.6.0.apk)、[E6 安装与使用](E6安装与使用.md)、[GitHub Release v0.6.0-e6](https://github.com/ZxxXinI/OpenSAIClink-Trial/releases/tag/v0.6.0-e6)
- 实车验收针对同一 E6 源码的首次签名构建；仓库中的 APK 是之后用相同源码和同一测试签名重建的归档文件，签名时间不同，因此文件哈希不同。
- [E5 实车问题复盘](analysis/e5-car-20261024/E5实车问题复盘与E6修正.md)
- [E5 验证报告](E5验证报告.md)、[E4 验证报告](E4验证报告.md)
- [USB 音频留档](USB音频协议研究留档.md)

## 架构与验收目标

- 独立包名 org.opensaiclink.trial，不重签或覆盖原版 SAICLink。电脑端制作可安装 APK，实车接受性需 K80 驻车验证。
- Android USB accessory 字节流 → WL 帧解析/握手 → MediaProjection 采集 → MediaCodec H.264 → type 6 USB 输出。
- 使用 Android 平台 API，无第三方运行依赖，无原版 32 位 Native 库。前台服务负责 USB 和录屏，切换地图时持续运行。
- E6 通过用户授权的无障碍服务执行最多双指手势，提供悬浮主页入口；声音沿用手机/现有车机播放路径，USB 音频暂停。
- 原版 APK、A7 固件、新提供的库和高德车机 APK 均作为只读输入。

## 资料判断

- 实车版本 AP31_MCE_10.25_MP5_21.12_210128_R 已在另一会话与 A7 包目标版本核对；主机为嵌入式 Linux/Qt。
- 新提供的 libwlchannel.so 是 AArch64 Android JNI 库，含 WeLink_SDK、onMuReady/responseWeLink 等实现痕迹，但来源、配套 Java 层和适配性未确认。它不是完整的协议说明；E2 不加载它。
- 高德车机 APK 为 com.autonavi.amapauto 5.3.0.600050，只有 armeabi-v7a 库，属于 Android 应用，不能直接装到 A7 Linux。E2 调用手机上现有高德 com.autonavi.minimap 或百度 com.baidu.BaiduMap。
- 资料哈希及原 APK 的 SDK 指令审计保留在本地工作副本，未将原始 APK、固件和大型录屏推送到源码仓库。

## 协议边界

- WL 固定头为 8 字节，目前验证的是 ext=0 路径。复审发现 A7 对非零 ext 的长度定义与 E2 接收器不同；此前泛化的扩展长度表述撤回，详见下方复审报告。
- onHuReady 回复 onMuReady；requestWeLink 独立回复 responseWeLink。收到 config 才记录会话配置完成，不把 type 104=1 当作成功。
- 默认 protocol 2 直接发送 type 6/9；不强制加入 type 107 隧道，不假设 type 106 已实际改变块大小。
- 遵循车机发来的暂停/切出状态，记录限制消息；应用退后台本身不触发停止。
- 屏幕共享由用户授权；无障碍手势服务必须连接后才能投屏；不读取无障碍页面内容，无麦克风、定位、联系人权限，地图自行处理定位。

## 历史状态（以下保留当时的结论）

2026-10-23 追加复审：基础握手和当前 K80 视频形态匹配，完整 2711 帧原 ARM 回放通过。USB 写入停滞缺少退出和恢复，另有非零扩展头、帧率重配置、辅助 NAL 的条件性缺口。E2 安装包保持原版本，见 [E2 与 A7 协议通信复审](analysis/e2-protocol-review-20261023/E2与A7协议通信复审.md)。

E1 已完成实车配置但出现黑屏。已在 A7 原 ARM 解析函数中复现 SPS/PPS/IDR 单包不兼容；E2 改为参数分包。5027 项主机断言、3 段真实首包回归、Android 16 模拟器录屏/后台/停止/导出通过；E2 尚未安装到 K80，实际车机显示待下一次驻车验收。

- [E2 安装与驻车验证](E2安装与驻车验证.md)
- [E2 验证报告](E2验证报告.md)
- [黑屏复盘与修复](analysis/car-20261023/黑屏复盘与E2修复.md)
- [工程模式照片分析与 CarPlay](analysis/car-20261023/工程模式照片分析.md)
- [E1 验证记录](验证报告.md)
- [变更记录](变更记录.md)

## 参考

- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/develop/connectivity/usb/accessory
- https://developer.android.com/studio/publish/app-signing
## 方案选择记录

| 方案 | 本轮判断 |
|---|---|
| 重签并恢复原版全屏路径 | 需处理原应用包身份、旧生命周期和多个业务模块，变更影响面较大 |
| 直接加载新提供的 libwlchannel.so | 缺配套 Java/API 文档，来源和适配性待确认 |
| 独立平台 API 试验客户端 | 选择此方案，分阶段验证 USB、录屏和视频；原版可作为对照，车机接受性仍待实测 |

## 本机复现

- 构建：准备 JDK 17、Android SDK 36（含 build-tools 36.0.0），设置 `JAVA_HOME` 和 `ANDROID_HOME`，运行 `python build.py`。仓库不保存 SDK、JDK、本地签名密钥或模拟器 AVD。
- `python build.py` 始终执行基础协议和触摸逻辑测试。需要大型实车录屏语料的回归只在本地 `analysis/` 数据存在时运行；缺失时构建信息明确记录 `SKIP`，不冒充通过。
- 设备测试脚本用于本地 K80/模拟器复现，含硬编码的 ADB 目标和本机工具路径，使用前需按实际环境修改。原始大体积诊断和证据 ZIP 未上传。
- 首次构建会创建本地测试签名。该签名只适合试验，不要作为正式生产签名来源。

## 最终验收

- 2026-10-24：用户在 K80 与目标车机上测试 E6，确认“测试没有问题”。本轮未提供新的诊断 ZIP 或照片，因此仓库不声称该反馈包含额外的量化延迟或硬件解码结果。
- 历史结论、静态协议审查、模拟器结果和用户实车反馈分别记录；前者不能替代硬件实测。

## 仓库资料边界

为避免把多 GB 的私密设备录屏和镜像纳入源码仓库，以下只保留在本机：`emulator/`、原始诊断和录屏、`evidence/`、`signing/`、解压依赖及大的分析二进制。仍提交源码、测试、精简的 JSON/日志/触控样本、说明文档及 E6 APK。用户提供的原版 SAICLink APK、车机固件和高德车机 APK 不重新分发。

## 支持

<img src="\README.assets\image-20261025204820819.png" alt="image-20261025204820819" style="zoom:33%;" />

