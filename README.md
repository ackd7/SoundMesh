# SoundMesh

一款基于 Windows WASAPI 与 Compose Multiplatform Desktop 打造的多设备同步音频输出工具。  
捕获系统任意软件正在播放的声音，实时镜像输出到多个音频设备（如蓝牙音箱、有线耳机、蓝牙耳机、USB 声卡、HDMI 显示器等）。

## 功能特性

- **系统全局音频捕获**：基于 Windows WASAPI Loopback（无需虚拟声卡驱动），自动捕获系统默认设备或指定来源设备的全部声音。
- **多设备同时输出**：支持同时勾选多个输出设备，自动重采样与格式转换（`AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM`），兼容不同采样率与声道数。
- **独立音量与延迟补偿**：
  - 每个设备可独立调节音量（0% – 150%）。
  - 每个设备支持 **0 – 1000 ms** 延迟补偿，轻松对齐有线耳机与蓝牙设备之间的传输延迟。
- **实时电平表与热插拔**：每个设备自带实时音量电平条；设备插拔、蓝牙断连重连自动恢复。
- **现代桌面 UI**：
  - 采用 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 组件库与原生 Mica / 自定义标题栏窗口（`miuix-window`）。
  - 结合 [feice11/mochi-compose](https://github.com/feice11/mochi-compose) 风格的弹簧按压动效，支持深色 / 浅色模式自动跟随系统。
- **系统托盘驻留**：运行中关闭窗口自动最小化到托盘，支持记忆配置与启动时自动恢复输出。

## 使用说明

1. 启动 SoundMesh，在 **音频来源** 中选择要捕获声音的设备（默认跟随 Windows 系统默认输出设备）。
2. 在 **同步输出到** 列表中，打开需要同时发声的设备开关（如蓝牙音箱、有线耳机、蓝牙耳机等）。
3. 点击 **开始同步输出**。
4. 若蓝牙设备比有线设备慢半拍，给较快的有线设备适当增加 **延迟补偿**（通常 150–300 ms）即可对齐。

> **提示**：来源设备本身是直接由系统发声的，无法被延迟。如果你希望所有设备都能自由调整延迟完全对齐，可以将一个闲置输出设备（如显示器自带扬声器）设为来源并将其音量调为 0，然后把所有需要听声音的设备都作为同步输出设备勾选。

## 本地构建与运行

环境要求：
- Windows 10 / 11 (x64)
- JDK 21

```powershell
# 直接运行
.\gradlew.bat run

# 打包 Windows 安装包 (MSI / EXE)
.\gradlew.bat packageDistributionForCurrentOS
```

## 致谢与参考

- UI 组件库：[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- 动效参考：[feice11/mochi-compose](https://github.com/feice11/mochi-compose)

## License

MIT
