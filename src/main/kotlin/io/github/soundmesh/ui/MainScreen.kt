package io.github.soundmesh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.soundmesh.AppController
import io.github.soundmesh.AppInfo
import io.github.soundmesh.UpdateChecker
import io.github.soundmesh.UpdateState
import io.github.soundmesh.audio.AudioDevice
import io.github.soundmesh.audio.StreamStatus
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

private val CardBottomGap = Modifier.padding(bottom = 12.dp)

@Composable
fun MainScreen(controller: AppController) {
    val devices by controller.devices.collectAsState()
    val deviceError by controller.deviceError.collectAsState()
    val snapshot = controller.snapshot
    val sourceId = controller.effectiveSourceId(devices)
    val sourceDevice = devices.firstOrNull { it.id == sourceId }
    val outputs = devices.filter { it.id != sourceId }
    var showChangelog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "多设备同声输出",
                subtitle = "蓝牙音箱 · 有线耳机 · 蓝牙耳机 同时播放",
                actions = {
                    val update = controller.updateState
                    if (update is UpdateState.Available) {
                        TextButton(
                            text = "新版本 v${update.latestVersion}",
                            onClick = { showChangelog = true },
                            cornerRadius = AppMetrics.CornerRadius,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            minHeight = 32.dp,
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    IconButton(
                        onClick = { showChangelog = true },
                        cornerRadius = AppMetrics.CornerRadius,
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Icon(AppIcons.Changelog, contentDescription = "更新日志", tint = MiuixTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = controller::refreshDevices,
                        cornerRadius = AppMetrics.CornerRadius,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = "刷新设备", tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left column: Control Hero + Audio Source + Settings & Version/Updates
            LazyColumn(
                modifier = Modifier
                    .weight(0.43f)
                    .fillMaxHeight()
                    .overScrollVertical(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
            ) {
                item(key = "hero") {
                    HeroCard(controller, sourceDevice, outputs)
                }

                item(key = "source-title") { SmallTitle("音频来源", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item(key = "source") {
                    Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
                        val items = listOf("跟随系统默认输出设备") + devices.map { it.name }
                        val selected = controller.sourceId?.let { id -> devices.indexOfFirst { it.id == id } + 1 } ?: 0
                        WindowDropdownPreference(
                            items = items,
                            selectedIndex = selected.coerceAtLeast(0),
                            title = "捕获声音来源",
                            summary = "来源设备照常发声，其余选中设备同步镜像",
                            onSelectedIndexChange = { idx ->
                                controller.setSource(if (idx == 0) null else devices[idx - 1].id)
                            },
                        )
                        if (sourceDevice != null) {
                            SourceRow(sourceDevice, snapshot.sourceLevel, controller.running)
                        }
                    }
                }

                item(key = "settings-title") { SmallTitle("设置与版本", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item(key = "settings") {
                    SettingsAndAboutCard(
                        controller = controller,
                        onOpenChangelog = { showChangelog = true },
                    )
                }
            }

            // Right column: Output Devices + Tips
            LazyColumn(
                modifier = Modifier
                    .weight(0.57f)
                    .fillMaxHeight()
                    .overScrollVertical(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
            ) {
                item(key = "outputs-title") {
                    SmallTitle(
                        text = "同步输出设备（${outputs.size}）",
                        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
                if (outputs.isEmpty()) {
                    item(key = "empty") {
                        Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
                            BasicComponent(
                                title = if (deviceError != null) "无法读取设备列表" else "没有其他可用的输出设备",
                                summary = deviceError ?: "请先连接蓝牙音箱 / 耳机，或插入有线耳机。设备连接后会自动出现在这里。",
                            )
                        }
                    }
                }
                items(outputs, key = { it.id }) { device ->
                    DeviceCard(controller, device)
                }

                item(key = "tips-title") { SmallTitle("使用提示", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item(key = "tips") {
                    Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tip("蓝牙设备通常比有线设备慢 150–300 ms。若声音不同步，请给较快的设备调大「延迟补偿」。")
                            Tip("来源设备直接由系统发声，无法延迟。想让所有设备完全对齐，可将闲置设备（如显示器扬声器）设为来源并静音，再把要用的设备都加入输出。")
                            Tip("运行中关闭窗口会最小化到系统托盘，可从托盘图标恢复或退出。")
                        }
                    }
                }
            }
        }
    }

    ChangelogDialog(
        show = showChangelog,
        updateState = controller.updateState,
        onDismiss = { showChangelog = false },
        onCheckUpdate = { controller.checkForUpdates(silent = false) },
    )
}

@Composable
private fun SettingsAndAboutCard(
    controller: AppController,
    onOpenChangelog: () -> Unit,
) {
    val update = controller.updateState
    val versionSummary = when (update) {
        UpdateState.Idle -> "当前版本 v${AppInfo.VERSION} · 点击检查更新"
        UpdateState.Checking -> "当前版本 v${AppInfo.VERSION} · 正在检查新版本…"
        is UpdateState.UpToDate -> "当前版本 v${update.currentVersion} · 已是最新版本"
        is UpdateState.Available -> "当前 v${AppInfo.VERSION} → 发现新版本 v${update.latestVersion}"
        is UpdateState.Error -> "当前 v${AppInfo.VERSION} · 检查失败（${update.message}）"
    }

    Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
        SwitchPreference(
            checked = controller.resumeOnLaunch,
            onCheckedChange = controller::updateResumeOnLaunch,
            title = "启动时自动恢复输出",
            summary = "若上次退出时正在输出，下次打开时自动开始",
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BasicComponent(
            title = "版本信息与更新",
            summary = versionSummary,
            endActions = {
                if (update is UpdateState.Available) {
                    TextButton(
                        text = "前往下载",
                        onClick = { UpdateChecker.openInBrowser(update.url) },
                        cornerRadius = AppMetrics.CornerRadius,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        minHeight = 34.dp,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    )
                } else {
                    TextButton(
                        text = if (update is UpdateState.Checking) "检查中…" else "检查更新",
                        onClick = { controller.checkForUpdates(silent = false) },
                        enabled = update !is UpdateState.Checking,
                        cornerRadius = AppMetrics.CornerRadius,
                        minHeight = 34.dp,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BasicComponent(
            title = "更新日志（Changelog）",
            summary = "查看 v${AppInfo.VERSION} 及历史版本更新内容",
            onClick = onOpenChangelog,
            endActions = {
                TextButton(
                    text = "查看",
                    onClick = onOpenChangelog,
                    cornerRadius = AppMetrics.CornerRadius,
                    minHeight = 34.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BasicComponent(
            title = "GitHub 项目主页",
            summary = "${AppInfo.REPO_OWNER}/${AppInfo.REPO_NAME}",
            onClick = { UpdateChecker.openInBrowser(AppInfo.REPO_URL) },
        )
    }
}

@Composable
private fun ChangelogDialog(
    show: Boolean,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "版本信息与更新日志",
        summary = "${AppInfo.NAME} 当前版本 v${AppInfo.VERSION}",
        cornerRadius = AppMetrics.CornerRadius,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (updateState is UpdateState.Available) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(AppMetrics.CornerRadius))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "发现新版本 v${updateState.latestVersion}",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        if (updateState.notes.isNotBlank()) {
                            Text(
                                text = updateState.notes.take(260),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        TextButton(
                            text = "前往 GitHub 下载 v${updateState.latestVersion}",
                            onClick = { UpdateChecker.openInBrowser(updateState.url) },
                            cornerRadius = AppMetrics.CornerRadius,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            minHeight = 36.dp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppInfo.changelog.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "v${entry.version}",
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                            )
                            if (entry.version == AppInfo.VERSION) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AppMetrics.CornerRadius))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "当前",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = entry.date,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        entry.highlights.forEach { item ->
                            Tip(item)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = if (updateState is UpdateState.Checking) "检查中…" else "检查更新",
                    onClick = onCheckUpdate,
                    enabled = updateState !is UpdateState.Checking,
                    cornerRadius = AppMetrics.CornerRadius,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    cornerRadius = AppMetrics.CornerRadius,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row {
        Text("•  ", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(text, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun HeroCard(controller: AppController, source: AudioDevice?, outputs: List<AudioDevice>) {
    val snapshot = controller.snapshot
    val running = controller.running
    val activeCount = outputs.count { controller.enabled[it.id] == true }
    val title = if (running) "正在同步输出" else "未开始"
    val summary = buildString {
        append("来源：").append(source?.name ?: "未知")
        if (running) append(" · ").append(snapshot.sampleRate / 1000.0).append(" kHz")
        append("\n").append("已选择 ").append(activeCount).append(" 个同步输出设备")
        val err = snapshot.sourceStatus as? StreamStatus.Error
        if (running && err != null) append("\n⚠ ").append(err.message)
    }

    Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when {
                    !running -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    snapshot.sourceStatus is StreamStatus.Error -> Color(0xFFE5484D)
                    else -> Color(0xFF30A46C)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(14.dp))
            LevelMeter(if (running) snapshot.sourceLevel else 0f, height = 6.dp)
            Spacer(Modifier.height(14.dp))
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = controller::toggle,
                enabled = !controller.busy,
                cornerRadius = AppMetrics.CornerRadius,
                modifier = Modifier.fillMaxWidth().springPress(interaction),
                interactionSource = interaction,
                colors = if (running) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                minHeight = 46.dp,
            ) {
                Text(
                    text = if (running) "停止输出" else "开始同步输出",
                    color = if (running) MiuixTheme.colorScheme.onSecondaryVariant else MiuixTheme.colorScheme.onPrimary,
                    style = MiuixTheme.textStyles.button,
                )
            }
        }
    }
}

@Composable
private fun SourceRow(device: AudioDevice, level: Float, running: Boolean) {
    BasicComponent(
        title = device.name,
        summary = "${device.kind.label()} · 来源设备（原声播放）",
        startAction = { DeviceIcon(device, active = true) },
        bottomAction = if (running) {
            { LevelMeter(level, modifier = Modifier.padding(top = 10.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun DeviceIcon(device: AudioDevice, active: Boolean) {
    val bg = if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f) else MiuixTheme.colorScheme.secondary
    val tint = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(AppMetrics.CornerRadius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.forKind(device.kind), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DeviceCard(controller: AppController, device: AudioDevice) {
    val on = controller.enabled[device.id] == true
    val running = controller.running
    val volume = controller.volumes[device.id] ?: 1f
    val delayMs = controller.delays[device.id] ?: 0
    val target = controller.snapshot.targets[device.id]
    val status = when {
        !on -> device.kind.label()
        !running -> "${device.kind.label()} · 已选中，开始后输出"
        target == null -> "${device.kind.label()} · 等待中"
        else -> when (val s = target.status) {
            StreamStatus.Running -> "${device.kind.label()} · 输出中"
            StreamStatus.Starting, StreamStatus.Idle -> "${device.kind.label()} · 正在连接…"
            is StreamStatus.Error -> "⚠ ${s.message}"
        }
    }

    Card(modifier = CardBottomGap, cornerRadius = AppMetrics.CornerRadius) {
        SwitchPreference(
            checked = on,
            onCheckedChange = { controller.setEnabled(device.id, it) },
            title = device.name,
            summary = status,
            startAction = { DeviceIcon(device, active = on) },
        )
        AnimatedVisibility(
            visible = on,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                LevelMeter(if (running) target?.level ?: 0f else 0f)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        LabeledValue("音量", "${(volume * 100).toInt()}%")
                        Slider(
                            value = volume,
                            onValueChange = { controller.setVolume(device.id, it) },
                            onValueChangeFinished = { controller.persist(device.id) },
                            valueRange = 0f..1.5f,
                            showKeyPoints = true,
                            keyPoints = listOf(0f, 0.5f, 1f, 1.5f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        LabeledValue("延迟补偿", "$delayMs ms")
                        Slider(
                            value = delayMs.toFloat(),
                            onValueChange = { controller.setDelay(device.id, (it / 5f).toInt() * 5) },
                            onValueChangeFinished = { controller.persist(device.id) },
                            valueRange = 0f..1000f,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
        label = "level",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppMetrics.CornerRadius))
            .background(MiuixTheme.colorScheme.secondary),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(AppMetrics.CornerRadius))
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}
