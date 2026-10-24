# OpenSAICLink v0.1.0(候选版)

本目录是独立于 E6 的正式版候选工程。

- 包名：org.opensaiclink.app
- 应用名：OpenSAICLink
- 版本：0.1.0
- 内部 versionCode：101
- 最低 Android：14（API 34）
- E6：org.opensaiclink.trial 保持不变，正式版不读取其设置
- 构建：python release/app/build.py
- APK：release/app/dist/OpenSAICLink-0.1.0.apk
- 签名：本地开发测试签名；正式发布签名需在实车验收后决定
- 当前状态：启动闪退已修复；手机主页和设置页已在 vivo V2531A / Android 16 完成冷启动、后台恢复与交互检查；车机主页和实车验收待继续

## 产品界面

手机主页以三步状态卡为核心。设置、权限、应用位、蓝牙提示与诊断位于独立设置页。车机主页左侧为高德/百度真实导航入口，右侧为应用入口；不展示虚假的路线、里程或耗时。USB 音频仍未启用。

## 验证边界

VIVO 的本机 uiautomator 在部分系统浮层/翻译组件运行时会报告无法等待空闲；不以该错误冒充页面通过。页面截图和设置页层级已经保存，其他页面继续使用安全区、截图和仿真器/仪器测试交叉核验。

本轮闪退原因、修复和复测记录见 `CRASH-FIX-0.1.0.md`。
