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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.soundmesh.AppController
import io.github.soundmesh.audio.AudioDevice
import io.github.soundmesh.audio.StreamStatus
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val CardPadding = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)

@Composable
fun MainScreen(controller: AppController) {
    val devices by controller.devices.collectAsState()
    val deviceError by controller.deviceError.collectAsState()
    val snapshot = controller.snapshot
    val sourceId = controller.effectiveSourceId(devices)
    val sourceDevice = devices.firstOrNull { it.id == sourceId }
    val outputs = devices.filter { it.id != sourceId }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "SoundMesh",
                largeTitle = "多设备同声输出",
                subtitle = "蓝牙音箱 · 有线耳机 · 蓝牙耳机 同时播放",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = controller::refreshDevices, modifier = Modifier.padding(end = 12.dp)) {
                        Icon(AppIcons.Refresh, contentDescription = "刷新设备", tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = padding,
        ) {
            item(key = "hero") {
                HeroCard(controller, sourceDevice, outputs)
            }

            item(key = "source-title") { SmallTitle("音频来源") }
            item(key = "source") {
                Card(CardPadding) {
                    val items = listOf("跟随系统默认输出设备") + devices.map { it.name }
                    val selected = controller.sourceId?.let { id -> devices.indexOfFirst { it.id == id } + 1 } ?: 0
                    WindowDropdownPreference(
                        items = items,
                        selectedIndex = selected.coerceAtLeast(0),
                        title = "捕获哪个设备的声音",
                        summary = "该设备本身照常发声，其余选中的设备同步镜像它的声音",
                        onSelectedIndexChange = { idx ->
                            controller.setSource(if (idx == 0) null else devices[idx - 1].id)
                        },
                    )
                    if (sourceDevice != null) {
                        SourceRow(sourceDevice, snapshot.sourceLevel, controller.running)
                    }
                }
            }

            item(key = "outputs-title") { SmallTitle("同步输出到") }
            if (outputs.isEmpty()) {
                item(key = "empty") {
                    Card(CardPadding) {
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

            item(key = "settings-title") { SmallTitle("设置") }
            item(key = "settings") {
                Card(CardPadding) {
                    SwitchPreference(
                        checked = controller.resumeOnLaunch,
                        onCheckedChange = controller::updateResumeOnLaunch,
                        title = "启动时自动恢复输出",
                        summary = "若上次退出时正在输出，下次打开时自动开始",
                    )
                }
            }

            item(key = "tips-title") { SmallTitle("使用提示") }
            item(key = "tips") {
                Card(CardPadding) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tip("蓝牙设备通常比有线设备慢 150–300 ms。若声音不同步，请给较快的设备调大「延迟补偿」。")
                        Tip("来源设备是直接发声的，无法被延迟。想让所有设备完全对齐，可以把一个不用的设备（如显示器扬声器）设为来源并将其静音，再把要用的设备都加入输出。")
                        Tip("运行中关闭窗口会最小化到系统托盘，可从托盘图标恢复或退出。")
                    }
                }
            }
            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
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

    Card(CardPadding.padding(top = 4.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when {
                    !running -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    snapshot.sourceStatus is StreamStatus.Error -> Color(0xFFE5484D)
                    else -> Color(0xFF30A46C)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(16.dp))
            LevelMeter(if (running) snapshot.sourceLevel else 0f, height = 6.dp)
            Spacer(Modifier.height(16.dp))
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = controller::toggle,
                enabled = !controller.busy,
                modifier = Modifier.fillMaxWidth().springPress(interaction),
                interactionSource = interaction,
                colors = if (running) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                minHeight = 52.dp,
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
        modifier = Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg),
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

    Card(CardPadding) {
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
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(14.dp))
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

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 4.dp) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh),
        label = "level",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.secondary),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(50))
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}
