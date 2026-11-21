# Changelog

All notable changes to SoundMesh are documented in this file.

## [1.1.0] - 2026-11-21

### Changed
- 重构主界面为横屏双栏桌面布局（默认 960×620）：左侧集中控制卡片、音频来源与设置关于，右侧专注多设备输出列表与使用提示。
- 统一窗口、卡片、按钮、弹窗与图标容器圆角（`8dp`），与 Windows 11 原生圆角窗口视觉一致。

### Added
- 新增版本信息展示（标题栏版本标签 + 设置面板当前版本信息）。
- 新增内置更新日志（Changelog）弹窗。
- 新增 GitHub Releases 新版本在线检查功能（支持启动静默检查与手动检查，发现新版本可一键跳转下载）。

## [1.0.0] - 2026-11-21

### Added
- 首个正式版本发布。
- 基于 Windows WASAPI Loopback 捕获系统默认或指定输出设备的全部声音，镜像输出到多个音频设备。
- 支持每个设备独立音量调节（0–150%）、延迟补偿（0–1000 ms）、实时电平表与热插拔自动恢复。
- 采用 Compose Multiplatform + Miuix 桌面组件与 Mochi 风格弹簧按压动效，支持原生 Mica 背景与系统托盘驻留。
