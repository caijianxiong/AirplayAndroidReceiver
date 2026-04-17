# AirplayAndroidReceiver

Java 实现的 AirPlay 安卓端接收程序。模拟 Apple TV 行为，支持屏幕镜像与音频投送。

## 1. 项目核心功能
- **服务发现 (Discovery)**：基于 JmDNS 实现 Bonjour 协议，向局域网广播 AirPlay 服务。
- **镜像投屏 (Mirroring)**：支持 H.264 视频流接收、硬解码与自适应渲染。
- **音频投送 (Audio)**：支持 AAC_ELD 编码格式的音频解密、解码与同步播放。
- **UI 自适应**：支持根据投屏内容（横/竖屏）自动调整显示比例。

## 2. 技术栈与核心库
| 库名称 | 作用 |
| :--- | :--- |
| **Netty** | 负责 RTSP 控制信道 (TCP) 与音频数据信道 (UDP) 的网络通信。 |
| **JmDNS** | 实现 mDNS/Bonjour 服务广播，使设备可被发现。 |
| **dd-plist** | 解析 Apple 私有的二进制 PList 格式数据。 |
| **MediaCodec** | 调用安卓硬件解码器处理 H.264 视频与 AAC_ELD 音频。 |
| **EdDSA / Curve25519** | 处理 AirPlay 配对与验证过程中的加密安全。 |

## 3. 核心流程实现

### 3.1 音频处理逻辑 (Audio Pipeline)
- **抗抖动缓冲**：使用环形缓冲区 (`AudioPacket[]`) 解决 UDP 乱序问题。
- **丢包恢复**：实现了灵敏的序列号同步机制。当检测到序列号跳跃（Gap > 64）时，自动跳过缺失包，防止静音死锁。
- **解密与解码**：对接收到的加密 AAC_ELD 数据进行 AES 解密，并配置 `MediaCodec` (需 `csd-0` 参数) 进行解码。

### 3.2 视频镜像逻辑 (Video Pipeline)
- **动态尺寸监听**：解析 `MirroringHeader` 中的 PayloadType 1，实时获取手机端的宽高变化。
- **SPS/PPS 注入**：从视频流中提取配置帧，动态初始化解码器。
- **比例缩放**：`PlaySurfaceView` 通过 `onMeasure` 实现 Fit Center 效果，确保竖屏画面不拉伸，横屏画面铺满。

## 4. 优化记录 (Hotfix)
- **RTSP 稳定性**：修复了 `BinaryPropertyListParser` 解析空请求体导致的 `ArrayIndexOutOfBoundsException` 崩溃。
- **音频断流优化**：将强制同步阈值从 512 优化至 64，并调大系统 UDP 接收缓冲区 (`SO_RCVBUF`) 至 2MB，大幅减少丢包。
- **界面体验**：移除了 Activity 标题栏，实现了真正的全屏投屏体验。

## 5. 开发环境
- **Android Studio**: 最新稳定版
- **最小 SDK**: API 21 (Android 5.0)
- **权限**: 
  - `INTERNET`
  - `CHANGE_WIFI_MULTICAST_STATE` (用于 JmDNS 发现)

---
*本项目持续优化中，旨在提供高性能、低延迟的 AirPlay 安卓端实现。*
