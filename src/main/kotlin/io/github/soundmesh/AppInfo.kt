package io.github.soundmesh

data class ChangelogEntry(
    val version: String,
    val date: String,
    val highlights: List<String>,
)

object AppInfo {
    const val NAME = "SoundMesh"
    const val VERSION = "1.1.0"
    const val REPO_OWNER = "ackd7"
    const val REPO_NAME = "SoundMesh"
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    val changelog: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.1.0",
            date = "2026-11-21",
            highlights = listOf(
                "重构为横屏双栏桌面布局：左侧集中控制与音频来源，右侧专注多设备列表与调音",
                "统一窗口、卡片、按钮、弹窗与图标容器圆角（8dp），与 Windows 11 原生窗口风格一致",
                "新增版本信息展示、内置更新日志（Changelog）弹窗与 GitHub 新版本在线检查",
            ),
        ),
        ChangelogEntry(
            version = "1.0.0",
            date = "2026-11-21",
            highlights = listOf(
                "首个正式版本发布，支持 Windows WASAPI Loopback 免驱动全局音频捕获",
                "支持同时输出到蓝牙音箱、有线耳机、蓝牙耳机等多个音频设备",
                "提供独立音量调节（0–150%）、延迟补偿（0–1000 ms）、实时电平表与热插拔重连",
                "基于 miuix 与 mochi-compose 风格动效打造，支持 Mica 背景与系统托盘驻留",
            ),
        ),
    )
}
