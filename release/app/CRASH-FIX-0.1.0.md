# OpenSAICLink 0.1.0 闪退修复与验证

## 现象

VIVO 首次安装正式版候选包后，应用在首页创建阶段闪退。

设备记录：

- 设备：VIVO V2531A
- 系统：Android 16
- 时间：2026-10-24 19:12:43、19:12:45、19:13:12
- 异常：`WindowInsetsController` 所依赖的 `DecorView` 尚未建立，`PhoneWindow.getInsetsController()` 发生空指针

## 修复

首页、设置页和横屏车载页均改为从已创建的 `DecorView` 获取 `WindowInsetsController`。首页只有在 `setContentView()` 之后才设置系统栏外观。

版本信息：

- 版本名：0.1.0
- versionCode：101
- 包名：org.opensaiclink.app
- APK：dist/OpenSAICLink-0.1.0.apk
- SHA-256：efa1ad3e296f5599e613cc2bf40820609be4a8d5bd150fb14c77236a35a60ed4

## 验证

2026-10-24 在 VIVO V2531A / Android 16 上完成：

- 覆盖安装成功，设备显示 versionCode 101。
- 强制停止后确认进程消失，再由系统完整冷启动，LaunchState=COLD，新进程正常进入 MainActivity，耗时约 769 ms。
- 首页、设置页、车载主页和返回路径连续操作后，应用进程保持运行。
- 新日志未出现本应用的 `FATAL EXCEPTION` 或 ANR。
- Dropbox 最后一条本应用启动崩溃记录仍为 19:13:12；修复包安装后的操作没有产生新记录。
- 首页控件可见、尺寸非零，无同级点击控件重叠。

本机 uiautomator 在 VIVO 外部浮层或翻译组件存在时可能报告 `could not get idle state`，该提示不属于应用崩溃。本轮以截图、控件边界、Activity 状态、进程 PID 与 Logcat/Dropbox 交叉验证。

## 尚待实车验收

- 修复包尚未在 A7 车机上重新测试。
- USB 连接、持续投屏、车机触控、双指缩放、蓝牙声音和延迟仍需实车确认。
- 在实车验收和最终签名决定之前，不发布 v0.1.0 Release。
