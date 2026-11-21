package io.github.soundmesh

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.soundmesh.ui.AppIcons
import io.github.soundmesh.ui.AppMetrics
import io.github.soundmesh.ui.MainScreen
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.window.LocalMiuixWindowInfo
import top.yukonga.miuix.kmp.window.LocalWindowState
import top.yukonga.miuix.kmp.window.MiuixCaptionBar
import top.yukonga.miuix.kmp.window.MiuixWindow
import top.yukonga.miuix.kmp.window.MiuixWindowBackgroundType
import top.yukonga.miuix.kmp.window.MiuixWindowProperties
import top.yukonga.miuix.kmp.window.WindowDraggableArea

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val controller = remember { AppController() }
    var windowVisible by remember { mutableStateOf(true) }
    val state = rememberWindowState(
        size = DpSize(960.dp, 620.dp),
        position = WindowPosition.Aligned(Alignment.Center),
    )
    val quit = {
        controller.shutdown()
        exitApplication()
    }

    val dark = isSystemInDarkTheme()
    MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        val trayIcon = rememberVectorPainter(AppIcons.VolumeUp)
        Tray(
            icon = trayIcon,
            tooltip = if (controller.running) "${AppInfo.NAME} v${AppInfo.VERSION} · 正在同步输出" else "${AppInfo.NAME} v${AppInfo.VERSION}",
            onAction = { windowVisible = true },
            menu = {
                Item("显示主窗口", onClick = { windowVisible = true })
                Item(if (controller.running) "停止输出" else "开始同步输出", onClick = controller::toggle)
                Separator()
                Item("退出", onClick = quit)
            },
        )

        val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        MiuixWindow(
            state = state,
            visible = windowVisible,
            onCloseRequest = {
                if (controller.running) windowVisible = false else quit()
            },
            title = "${AppInfo.NAME} v${AppInfo.VERSION}",
            icon = trayIcon,
            properties = MiuixWindowProperties.default(
                minSize = DpSize(760.dp, 500.dp),
                captionButtonIsDarkTheme = isDark,
                backgroundIsDarkTheme = isDark,
                backgroundType = when {
                    MiuixWindowBackgroundType.Mica.isSupported() -> MiuixWindowBackgroundType.Mica
                    else -> MiuixWindowBackgroundType.None
                },
            ),
        ) {
            val placement = LocalWindowState.current.placement
            val isFullscreen = placement == WindowPlacement.Fullscreen
            val isFloating = placement == WindowPlacement.Floating
            val captionBarHeight = LocalMiuixWindowInfo.current.captionBarHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isFloating) Modifier.clip(RoundedCornerShape(AppMetrics.CornerRadius)) else Modifier)
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                if (!isFullscreen && captionBarHeight > 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(captionBarHeight)
                            .background(MiuixTheme.colorScheme.surface),
                    ) {
                        WindowDraggableArea()
                        MiuixCaptionBar {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.width(12.dp))
                                Image(
                                    painter = trayIcon,
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(AppInfo.NAME, style = MiuixTheme.textStyles.body2)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AppMetrics.CornerRadius))
                                        .background(MiuixTheme.colorScheme.secondary)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "v${AppInfo.VERSION}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MainScreen(controller)
                }
            }
        }
    }
}
