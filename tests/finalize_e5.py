from pathlib import Path
import datetime,json,hashlib,zipfile
root=Path.cwd();now=datetime.datetime.now().strftime('%Y-%m-%d %H:%M')
meta=json.loads((root/'dist/build-info.json').read_text(encoding='utf-8-sig'))

def write(path,text):
 path=Path(path);path.write_text(text,encoding='utf-8-sig');assert path.read_bytes().startswith(b'\xef\xbb\xbf');assert path.read_text(encoding='utf-8-sig')==text

def append(path,text):
 path=Path(path);write(path,path.read_text(encoding='utf-8-sig')+text)

write('E5安装与驻车验证.md',f'''# E5 安装与驻车验证

更新时间：{now}。安装包：[OpenSAICLink-E5-0.5.0.apk](dist/OpenSAICLink-E5-0.5.0.apk)。

可直接覆盖 E4，包名和签名保持一致。E5 已完成 Android 16 模拟器验证，K80 与真实 A7 车机尚待本轮验收。

## 三个新增开关

在“试验设置”中可找到，默认开启：

| 开关 | 作用 | 生效时间 |
| --- | --- | --- |
| 允许双指缩放 | 将车机的两根手指传到手机，支持地图捏合；最多两指 | 立即，切换时先释放旧手势 |
| 尝试让手机桌面横屏 | 投屏时通过现有无障碍权限请求横屏，停止后移除 | 立即；K80 桌面兼容性待测 |
| 横屏画面铺满车机 | 去除镜像左右/上下黑边，再填满车机画面；同步调整触点 | 下次投屏 |

横屏铺满会适度改变宽高比例，K80 3200×1440 到 1280×480 的画面约横向拉宽 20%。内容保持完整；若更重视地图比例，关闭此项恢复 E4 的等比例显示。竖屏画面不会强行拉宽。只处理镜像本身的黑边，应用内部自行生成的留白仍可能存在。

辅助横屏不会把手机默认桌面替换成我们的主页。手机 HOME 键仍打开原桌面；通知中的“返回车载主页”打开六个大按钮页面。若 K80 原桌面仍竖屏或布局不合适，可以单独关闭辅助横屏并使用车载主页。

## 本次只需验证这些

1. 覆盖安装，确认设置页“触控权限”为是；若更新后服务未连接，在无障碍设置中重新开启“SAICLink 车机触控”。原有自动横屏权限也保留。
2. 驻车接线、开始投屏，观察车载主页是否占满车机、各按钮是否完整；点击左右边缘按钮检查是否点得准。
3. 按手机 HOME 键，查看原系统桌面是否横屏、图标是否正常；再从投屏通知返回车载主页。
4. 打开地图，双指张开/捏合，分别先抬左手和右手，检查缩放是否连续，随后单指拖动是否仍正常。当前只支持两指。
5. 用动态测试画面再拍手机与车机同框；若感觉延迟增加，停止投屏，关闭“横屏画面铺满车机”，重新投屏比较。
6. 停止后确认手机方向恢复，再导出诊断。诊断为累计 ZIP，等待“诊断包已保存”再取走文件。

声音继续沿用已验证的蓝牙路径：先打开车机音乐，再播放手机音乐。E5 未加入 USB 音频。

## 本地验证依据

双指放大约 1.88 倍、缩小约 0.57 倍，先抬任一手指、暂停释放和单指恢复均通过；模拟器原桌面和强制竖屏测试页可横屏。铺满与直通模式均正常录屏、停止和恢复。以上不等于 K80/车机新版本已通过，详细证据见 [E5 验证报告](E5验证报告.md)。
''')
write('E5验证报告.md',f'''# E5 验证报告

更新时间：{now}。本轮完成 E4 实车诊断复盘，以及 E5 双指、系统桌面横屏和车机显示适配。

## 已交付文件

- 安装包：[OpenSAICLink-E5-0.5.0.apk](dist/OpenSAICLink-E5-0.5.0.apk)
- 版本 0.5.0-e5 / versionCode 5；包名 org.opensaiclink.trial；大小 {meta['bytes']:,} 字节。
- SHA-256：`{meta['sha256']}`。
- 与 E4 相同本地签名，可覆盖更新。E1–E4 原安装包哈希均核对保持一致。
- [安装与驻车验证](E5安装与驻车验证.md)、[E4 实车复盘](analysis/e4-car-20261024/E4实车复盘与E5设计.md)。

## E4 返回资料的结论

两份累计 ZIP 校验通过。四段投屏约 69、237、574、190 秒；最长 9 分 34 秒。没有记录编码错误或超过 3 秒的写入停滞；第三段队列累计丢弃 5 帧，不能据此保证每一时刻都无丢帧。四份已保存 H.264 片段合计 6376 帧可以解码，尺寸全部 1280×480。录屏有约 10 MiB 上限，这些片段并未覆盖长会话的全部画面。

三张照片的同图读数差为 **116、175、50 ms**，均值约 **114 ms**。它们支持百毫秒量级的样本表现；刷新、曝光和滚动快门有误差，不能解释为全程恒定 150 ms。

真实触控数据包含 2698 个事件，指针 0 为 2540 个、指针 1 为 158 个；9 次两指并存。E4 过滤第二触点，因而只能单指；E5 才开始转发两指。没有证据表明该车机已验证三指或更多。

## E5 实现

- TouchFrames 按触点编号维护生命周期，按 comm 成帧；只合并移动，不吞掉按下/抬起。RemoteTouchService 同时续接两条 Android 手势，暂停、停止、锁屏和超时释放。模拟器发现续接时新指针不能在首个采样时刻加入，故延后 1 ms；不动的重复坐标跳过，避免 Android 将空事件序列取消。
- RotationOverlay 使用透明、不可触摸、不可聚焦的方向窗口，在已获授权的无障碍服务内工作。停止、暂停、锁屏、开关关闭或服务销毁后移除，不读取页面内容。
- DisplayRenderer 通过 GPU 将镜像的横屏有效区域铺满原视频尺寸，TouchInput 使用对应逆变换。仅对横屏且宽高比差适度的内容启用；竖屏保持等比例。无 CPU 图片读回，不额外排队积累帧；新增当前/最大渲染耗时日志。
- 铺满关闭时使用原直接录屏输入路径；GPU 初始化失败会记录 DISPLAY_FALLBACK 并回到直通，运行中错误会停止本次录屏，提示关闭铺满后重试。
- 继续设置 15 fps、1.2 Mbps、无 B 帧及原低延迟队列；视频协议分包不变。USB 音频继续暂停。

## 验证结果

| 范围 | 结果 |
| --- | --- |
| 主机检查 | 原 5129 项 + 新 2716 项全部通过；完整回放 2698 条真实触摸，保留 9 次双指并存 |
| Android 16 实际手势 | 13 项通过；真实 MotionEvent 两指流与 ScaleGestureDetector 放大约 1.88 倍、缩小约 0.57 倍 |
| 抬起与恢复 | 第一指先抬、第二指先抬、快速成组移动、暂停释放全部触点、恢复单指、停止阻断通过 |
| 坐标 | 原黑边位置启用铺满后可点到正确的手机坐标，主机几何映射与实际事件交叉核对 |
| 方向 | 强制竖屏测试页与模拟器系统桌面能够横屏；撤销窗口后测试页恢复竖屏 |
| UI 与录屏 | 车载主页、测试画面、切换原系统桌面、GPU/直通切换、停止与方向恢复、累计 ZIP 导出通过 |
| 新视频 | GPU 744 帧、直通 640 帧，全部可解码为 1280×480 |
| A7 原 ARM 回放 | 1570 个实际 Java WL 包产生 1384 个图像回调、93 个关键帧，重组字节全部一致 |
| 安装包 | 实际安装的 APK 哈希与交付文件一致；权限列表与 E4 相同，无原版 Native 库或测试入口类 |

证据：[设备检查](evidence/e5/device-test-result.txt)、[UI 检查](evidence/e5/emulator-smoke.json)、[视频检查](evidence/e5/capture-validation.json)、[安装包检查](evidence/e5/apk-inspection.json)、[录屏日志](evidence/e5/capture-log.txt)。源码快照：releases/E5-0.5.0/。

UI 自动化分段完成：读取 UI 树的自动化工具会临时抑制其他无障碍服务，导致方向窗口暂时撤销；因此桌面方向改用只读窗口状态验证。累计 ZIP 初次读取时尚未写完，等待完整后重新读取且 CRC 通过。两项均记录为测试流程修正，没有把中间失败隐去。

## 实际录出的车机比例画面

下图是模拟器录屏 H.264 解码帧，**并非新实车照片**。Settings 是模拟器选择的常用应用。

![E5 铺满后的车载主页](evidence/e5/gpu-decoded-home.png)

原等比例测试帧：

![原等比例](evidence/e5/direct-decoded-pattern.png)

启用铺满后，同样完整内容占用更宽的区域：

![E5 铺满](evidence/e5/gpu-decoded-pattern.png)

## 尚需实车确认

- E4 的正常功能由用户实测；E5 本轮只在隔离 Android 16 模拟器运行，没有连接用户 K80 或车机。ARM 回放不包含 USB 硬件、车机硬件解码或实际显示。
- K80/HyperOS 是否允许原桌面横屏、横屏后图标如何排布，需实测。辅助方向窗口不能保证所有应用都响应，也不重排第三方应用内部布局。
- GPU 铺满增加一道显示处理。模拟器日志动态阶段当前渲染常见约 1–2 ms，启动最大值 102 ms；软件渲染器和手机 GPU 不同，这些值不能证明实车端到端延迟不增加。新包需开关对照照片和诊断。
- 画面铺满会改变横纵比例，K80 例约横向放大 20%；可关闭恢复原模式。触控仍受 Android 无障碍手势和具体地图应用响应影响。
- 历史协议审查中的非零扩展头、长 USB 写入恢复、辅助 NAL 等条件性缺口未在本轮宣称解决；现有真实主路径与新录屏解析回归通过。

参考：[Android 多笔画手势 API](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder)、[续接手势](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription)、[AOSP 注入实现](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java)。主记录：[Task.md](../Task.md)。
''')
f=root/'README.md';s=f.read_text(encoding='utf-8-sig');end=s.index('## 架构与验收目标')
s='''# OpenSAICLink 地图投屏试验 E5

## 当前版本：E5（2026-10-24）

用户确认 E4 实车功能正常。最新诊断发现车机真实回传两指，三张照片显示差约 50–175 ms。E5 增加双指缩放、实验性系统桌面横屏、可关闭的横屏铺满与匹配触控坐标；USB 音频继续暂停。E5 已通过 Android 16 模拟器验证，K80/车机待验收。

- [E5 安装包](dist/OpenSAICLink-E5-0.5.0.apk)
- [E5 安装与驻车验证](E5安装与驻车验证.md)、[E5 验证报告](E5验证报告.md)
- [E4 最新实车诊断与 E5 设计](analysis/e4-car-20261024/E4实车复盘与E5设计.md)
- [E4 原安装包](dist/OpenSAICLink-E4-0.4.0.apk)、[E4 使用说明](E4安装与使用.md)、[E4 验证报告](E4验证报告.md)
- [E3 安装包](dist/OpenSAICLink-E3-0.3.0.apk)、[E3 验证报告](E3验证报告.md)
- [USB 音频研究留档](USB音频协议研究留档.md)

'''+s[end:]
s=s.replace('E3 通过用户授权的无障碍服务执行单指手势','E5 通过用户授权的无障碍服务执行最多双指手势，并提供临时方向窗口')
s=s.replace('E4 使用 tests/run_device_test_e4.py、tests/emulator_smoke_e4.py 和 tests/verify_e4_capture.py','E5 使用 tests/run_device_test_e5.py、tests/emulator_smoke_e5.py 和 tests/verify_e5_capture.py').replace('E2/E3 脚本保留为历史流程','E2/E3/E4 脚本保留为历史流程');write(f,s)
append('E4验证报告.md',f'''\n## {now} 后续实车确认\n\n用户已反馈 E4 功能正常，并带回两份累计诊断和三张延迟照片。四段投屏最长约 9 分 34 秒；照片同图读数差为 116、175、50 ms。诊断发现真实两指数据，E4 手机端只处理第一指。此前“E4 主页尚待实车”的段落保留为交付时的历史状态，最新依据见 [E4 实车复盘](analysis/e4-car-20261024/E4实车复盘与E5设计.md)。E4 安装包不变，显示适配和双指功能在 E5 独立版本实现。\n''')
append('analysis/e4-car-20261024/E4实车复盘与E5设计.md',f'''\n## {now} 实现与新增验证\n\nE5 已完成：13 项 Android 设备检查通过，含真实双指放大/缩小、任一手指先抬、暂停释放、方向窗口恢复和铺满坐标。GPU/直通两段视频合计 1384 帧，通过解码和 A7 原解析重组；四份 E4 录屏片段合计 6376 帧可解码。视频片段有约 10 MiB 记录上限，不代表长会话全程。详见 [E5 验证报告](../../E5验证报告.md)；K80 和车机新版本等待实测。\n''')
append('../Task.md',f'''\n## {now} E5 双指与显示适配完成\n\n- E5 0.5.0-e5 已交付，{meta['bytes']:,} 字节，SHA-256 {meta['sha256']}。新增最多双指、可撤销的系统桌面横屏请求、可关闭的车机铺满和逆向触点映射；USB 音频继续暂停。\n- 7845 项主机检查、13 项实际 Android 手势/方向检查通过；GPU/直通两段共 1384 帧解码和 A7 原解析重组一致。E4 四个已保存片段共 6376 帧可解码；原诊断和 E1–E4 APK 保留。\n- 关键产物：OpenSAICLink-Trial/dist/OpenSAICLink-E5-0.5.0.apk；OpenSAICLink-Trial/E5安装与驻车验证.md；OpenSAICLink-Trial/E5验证报告.md；OpenSAICLink-Trial/evidence/e5/；OpenSAICLink-Trial/releases/E5-0.5.0/。\n- 状态：本地制作与验证完成；K80 系统桌面横屏、车机实际双指及新增渲染的端到端延迟等待用户实车验收。本轮只使用隔离模拟器，未连接用户手机。\n''')
append('变更记录.md',f'''\n## {now} E5 交付归档\n\n- 新增 E5安装与驻车验证.md、E5验证报告.md，更新 README、E4 验证报告后续确认及 Task.md。分别记录实车 E4、模拟器 E5、原 ARM 解析回放的证据边界。\n- 安装包 {meta['bytes']:,} 字节，SHA-256 {meta['sha256']}；实际安装文件哈希相同，原版本哈希保留。源码和测试归档到 releases/E5-0.5.0/。主记录：[Task.md](../Task.md)。\n''')
append(r'F:\Documents\Agents\20261023-0753-CODEX-OpenSAICLink试验包.md',f'''\n## {now} E5 隔离模拟器复用\n\n- 位置：C:\\Users\\Zxx\\AppData\\Local\\Temp\\AndroidEmulator\\emu-crash-36.4.9.db；已有模拟器运行时自动生成或更新的崩溃诊断库。\n- 用途：E5 双指、方向窗口、GPU/直通录屏、停止恢复与诊断导出验证。\n- 来源：E:\\Soft\\Tools\\AndroidSDK\\emulator\\emulator.exe；原项目 F:\\Documents\\Code\\2026\\Misc\\2026-10-21\\OpenSAICLink-Trial。\n- 主动产物、AVD 写入层与测试 APK 均在原项目目录，未添加全局依赖；仅操作 emulator-5580，未安装到用户 K80。\n- 保留要求：临时文件，项目完成后可删除；删除前仍须用户确认。\n''')
# Normalize the ASCII build output captured by Windows PowerShell as UTF-16.
f=root/'evidence/e5/build-log.txt';data=f.read_bytes()
if data.startswith(b'\xff\xfe'):write(f,data.decode('utf-16'))
release=root/'releases/E5-0.5.0';release.mkdir(parents=True,exist_ok=True)
files=sorted((root/'src').rglob('*'))+sorted((root/'tests').glob('*'))+[root/'build.py',root/'README.md',root/'E5安装与驻车验证.md',root/'E5验证报告.md',root/'变更记录.md']
files=[f for f in files if f.is_file() and f.suffix!='.pyc'];manifest=[]
with zipfile.ZipFile(release/'source-and-tests.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in files:
  data=f.read_bytes();name=f.relative_to(root).as_posix();z.writestr(name,data);manifest.append({'path':name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
write(release/'source-hashes.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
with zipfile.ZipFile(release/'source-and-tests.zip') as z:assert z.testzip() is None
print(json.dumps({'docsWritten':True,'sourceFiles':len(manifest),'apkSha256':meta['sha256']}))
