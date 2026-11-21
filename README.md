<div align="center">

# SoundMesh

**Windows 多设备同步音频输出工具 · Play Windows System Audio on Multiple Devices Simultaneously**

[![Release](https://img.shields.io/github/v/release/ackd7/SoundMesh?label=Release&color=3684FF)](https://github.com/ackd7/SoundMesh/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%20%2F%2011-0078D6)](https://github.com/ackd7/SoundMesh/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-30A46C)](LICENSE)

[简体中文](#-简体中文) · [English](#-english) · [下载安装 / Download](https://github.com/ackd7/SoundMesh/releases/latest) · [更新日志 / Changelog](CHANGELOG.md)

</div>

---

## 🇨🇳 简体中文

**SoundMesh** 是一款基于 **Windows WASAPI** 与 **Compose Multiplatform Desktop** 打造的多设备同步音频输出软件。  
无需安装任何虚拟声卡驱动，即可实时捕获系统正在播放的全部声音，并同步镜像输出到多个音频设备（蓝牙音箱、有线耳机、蓝牙耳机、USB 声卡、HDMI 显示器等）。

### 功能特性

- **系统全局音频捕获**：基于 Windows WASAPI Loopback（免虚拟声卡驱动），自动捕获系统默认设备或指定来源设备的全部声音。
- **多设备同时输出**：支持同时开启多个输出设备，自动重采样与格式转换（`AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM`），兼容不同采样率与声道数。
- **独立音量与延迟补偿**：
  - 每个设备可独立调节音量（`0% – 150%`）。
  - 每个设备支持 **`0 – 1000 ms`** 延迟补偿，轻松对齐有线耳机与蓝牙设备之间的传输延迟。
- **实时电平表与热插拔**：每个设备自带实时音量电平条；设备插拔、蓝牙断连重连自动恢复。
- **现代横屏桌面 UI**：
  - 采用横屏双栏布局，左侧控制中心与音频来源，右侧多设备列表与独立调音；卡片、按钮与弹窗统一使用 `18dp` 连续圆角。
  - 采用 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 组件库与原生 Mica / 自定义标题栏窗口（`miuix-window`）。
  - 结合 [feice11/mochi-compose](https://github.com/feice11/mochi-compose) 风格的弹簧按压动效，支持 **浅色 / 深色 / 跟随系统** 三种外观主题切换。
- **版本管理与在线更新**：内置当前版本信息、更新日志（[CHANGELOG.md](CHANGELOG.md)）弹窗与 GitHub Releases 新版本在线检查。
- **系统托盘驻留**：运行中关闭窗口自动最小化到托盘，支持记忆配置与启动时自动恢复输出。

### 安装与使用

1. 前往 [Releases](https://github.com/ackd7/SoundMesh/releases/latest) 下载：
   - **NSIS 安装版**：`SoundMesh-x.x.x-Setup.exe`（支持自定义路径与快捷方式，无需安装 Java）
   - **单 EXE 便携版**：`SoundMesh-x.x.x-Portable.exe`（单文件双击即用，免安装）
2. 在 **音频来源** 中选择要捕获声音的设备（默认跟随 Windows 系统默认输出设备）。
3. 在 **同步输出设备** 列表中，打开需要同时发声的设备开关（如蓝牙音箱、有线耳机、蓝牙耳机等）。
4. 点击 **开始同步输出**。
5. 若蓝牙设备比有线设备慢半拍，给较快的有线设备适当增加 **延迟补偿**（通常 `150–300 ms`）即可对齐。

> **提示**：来源设备本身是直接由系统发声的，无法被延迟。如果你希望所有设备都能自由调整延迟完全对齐，可以将一个闲置输出设备（如显示器自带扬声器）设为来源并将其音量调为 0，然后把所有需要听声音的设备都作为同步输出设备勾选。

### 本地构建与打包

环境要求：Windows 10 / 11 (x64)、JDK 21（如需 NSIS 安装包，额外安装 [NSIS](https://nsis.sourceforge.io/)）。

```powershell
# 直接运行
.\gradlew.bat run

# 运行单元测试与 WASAPI 冒烟测试
.\gradlew.bat check

# 打包 NSIS 安装程序（build\SoundMesh-<version>-Setup.exe）
powershell -ExecutionPolicy Bypass -File scripts\build-nsis.ps1
```

---

## 🇺🇸 English

**SoundMesh** is a Windows desktop utility built with **Windows WASAPI** and **Compose Multiplatform Desktop** that lets you play system audio on multiple output devices simultaneously.  
Without requiring any virtual audio cable or driver, it captures everything playing on your default (or chosen) output device and mirrors it in real time to any combination of Bluetooth speakers, wired headphones, Bluetooth earbuds, USB DACs, or HDMI monitors.

### Features

- **System-Wide Audio Capture**: Powered by Windows WASAPI Loopback (no virtual sound card required). Follows the Windows default playback device automatically or locks to a specific source device.
- **Multi-Device Simultaneous Output**: Mirror audio to any number of output devices at once with automatic resampling and format conversion (`AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM`).
- **Per-Device Volume & Delay Compensation**:
  - Independent volume gain (`0% – 150%`) per output device.
  - Per-device delay compensation (`0 – 1000 ms`) to keep wired headphones and higher-latency Bluetooth devices perfectly in sync.
- **Live Level Meters & Hot-Plug Recovery**: Real-time peak meters for source and outputs, with automatic device discovery and reconnection after unplugging or Bluetooth drops.
- **Modern Landscape Desktop UI**:
  - Two-column landscape layout with unified `18dp` continuous corners across cards, buttons, and dialogs.
  - Built with [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (`miuix-window` native Mica backdrop & custom title bar) and spring press motion inspired by [feice11/mochi-compose](https://github.com/feice11/mochi-compose).
  - Built-in **Light / Dark / Follow System** theme switcher.
- **Built-in Version Info, Changelog & Update Checker**: Displays current version, an in-app changelog dialog ([CHANGELOG.md](CHANGELOG.md)), and checks GitHub Releases for new versions.
- **System Tray Support**: Closing the window while routing minimizes to the system tray; remembers device settings and optionally auto-resumes on launch.

### Download & Usage

1. Download from [GitHub Releases](https://github.com/ackd7/SoundMesh/releases/latest) (bundled runtime — no Java installation required):
   - **NSIS Installer**: `SoundMesh-x.x.x-Setup.exe`
   - **Single-EXE Portable**: `SoundMesh-x.x.x-Portable.exe` (double-click to run, no installation)
2. Choose your **Audio Source** (defaults to following the Windows default playback device).
3. Toggle on all **Output Devices** you want to play audio simultaneously (Bluetooth speakers, wired headphones, Bluetooth headphones, etc.).
4. Click **Start Sync Output** (`开始同步输出`).
5. If a Bluetooth device lags behind a wired device, add **Delay Compensation** (typically `150–300 ms`) to the faster wired device until they align.

> **Tip**: Because the source device plays natively through Windows, it cannot be delayed. To freely delay *every* active device for exact synchronization, set an unused endpoint (such as a monitor speaker) as the muted source and enable all of your listening devices as mirrored outputs.

### Building from Source

Requirements: Windows 10 / 11 (x64), JDK 21 (and [NSIS](https://nsis.sourceforge.io/) if building the NSIS setup installer).

```powershell
# Run directly
.\gradlew.bat run

# Run unit & WASAPI smoke tests
.\gradlew.bat check

# Build NSIS installer (build\SoundMesh-<version>-Setup.exe)
powershell -ExecutionPolicy Bypass -File scripts\build-nsis.ps1
```

---

## 致谢与参考 / Credits

- UI Library: [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- Motion Reference: [feice11/mochi-compose](https://github.com/feice11/mochi-compose)

## License

[MIT](LICENSE)
