# USB 音频协议研究留档（暂停接入）

更新：2026-10-24。依据用户最新要求，E3 优先处理视频延迟、触控和横屏，停止 USB 音频开发与测试。本文件保存已有发现，方便以后续接。

## 当前决定

- 用户实车验证：先打开车机音乐，再播放手机音乐，车机扬声器能够出声。这条播放路径保留；导航播报是否正常还需单独确认。
- E2 没有发送 USB PCM 音频，用户报告的长时间投屏超过 1 秒延迟不能归因于该功能。
- PCM 和视频将共用同一连接与发送通道，会增加带宽和写入工作量，但没有实测证明会增加多少显示延迟。44100 Hz、单声道、16 位 PCM 为 88,200 字节/秒；按每 20 毫秒一包，含 24 字节内部头及 8 字节 WL 头约为 89,800 字节/秒，约 0.72 Mbps。仅为格式计算，不代表车机性能。
- 本轮不请求录音权限，不发送音频或媒体切源命令。未验证的封包草稿归档，排除在应用构建之外。

## 静态证据

来源为用户提供的 A7 固件与 SAICLink APK，不依赖来源未确定的外来 libwlchannel.so。

- A7 文件：analysis/car-20261023/firmware/SaicLink。
- SHA-256：835f2d3a6485c52111c5360c34ef665bcf2b1a26969cd7cea8747fc8f2275ced。
- 函数地址：wl_audio_protocol_analysis 0x19388；get_audio_param 0x1a054；wl_audio_ctrl 0x19f80；wl_audio_music_ctrl 0x196fc；wl_audio_input_data 0x1add4；wl_audio_send_data 0x1931c。地址仅适用于上述哈希。
- 原版 ByteUtil.getPackHeadInfo / getPlayPCMParamsByte / getPackageDataByParam 与 PCMManager.sendMusicPackageToCar 的封包关系已有核对；外层使用 WL type 17。
- 反汇编和当前 APK DEX 证据在 analysis/e3-input-audio-20261024/。尚未完成原 ARM 音频函数执行验证或真车 USB 播放验证。

## 封包结构

内部头使用小端序，放在外层 WL type 17 的载荷中。偏移从内部头起算。

| 偏移 | 长度 | 含义 |
| --- | --- | --- |
| 0 | 2 | 0x55AA，即 AA 55 |
| 2 | 2 | 命令 10 |
| 4 | 1 | 来源：1 音乐，0 导航 |
| 5 | 7 | 保留/时间字段，已有草稿置零 |
| 12 | 4 | 8 + PCM 数据字节数 |
| 16 | 1 | playMark，草稿为 0 |
| 17 | 1 | 阶段：1 开始、0 数据、2 结束 |
| 18 | 1 | 声道数 |
| 19 | 1 | 位深 |
| 20 | 4 | 采样率 |
| 24 | 可变 | PCM 数据 |

车机回执函数构造 12 字节内部包：AA 55，命令 40010（开始）或 40020（结束），来源位于偏移 4。回执外层通道仍需抓包核实，不把猜测的 type 13/17 当成已证实结论。

当前 APK 的 AP31 媒体状态为 WeLink / onMediaState，extData.mediaState=1（开始）或 2（停止），A7 有对应方法。不要换用其他机型的 onStartCarMedia，也不要通过未核实的命令强制切换音源或音量。

## 将来恢复研究时

1. 先完成视频延迟的稳定基线，对比无 USB 音频和有 USB 音频的长时间表现。
2. 先验证封包、回执、开始/结束清理和有界队列，再测试低音量生成音；写入成功不等于车机有声。
3. Android AudioPlaybackCapture 只能捕获符合条件且允许被捕获的应用播放声音。导航播报可能使用不符合条件的用途，不能保证通过这种方式获得；不采集麦克风。
4. 恢复研究后再通过用户明确授权增加采集权限，不直接启用历史草稿。

参考：[Android 播放声音采集限制](https://developer.android.com/media/platform/av-capture)。

主记录：[Task.md](../Task.md)；开发记录：[变更记录.md](变更记录.md)。
