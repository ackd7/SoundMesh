package io.github.soundmesh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.soundmesh.audio.AudioDevice
import io.github.soundmesh.audio.AudioEngine
import io.github.soundmesh.audio.DeviceMonitor
import io.github.soundmesh.audio.EngineSnapshot
import io.github.soundmesh.audio.StreamStatus
import io.github.soundmesh.audio.TargetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UI-facing state holder. All mutations happen on the Compose (Swing) thread. */
class AppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engineLock = Mutex()
    private val settings = Settings()
    private val engine = AudioEngine()
    private val monitor = DeviceMonitor()

    val devices: StateFlow<List<AudioDevice>> = monitor.devices
    val deviceError: StateFlow<String?> = monitor.error

    var running by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** `null` = follow Windows default output. */
    var sourceId by mutableStateOf(settings.sourceId)
        private set

    var resumeOnLaunch by mutableStateOf(settings.resumeOnLaunch)
        private set

    val enabled = mutableStateMapOf<String, Boolean>()
    val volumes = mutableStateMapOf<String, Float>()
    val delays = mutableStateMapOf<String, Int>()

    var snapshot by mutableStateOf(
        EngineSnapshot(false, StreamStatus.Idle, null, 48_000, 0f, emptyMap()),
    )
        private set

    init {
        engine.sourceId = sourceId
        monitor.start()
        scope.launch {
            var lastSource: String? = null
            while (isActive) {
                val snap = engine.snapshot()
                snapshot = snap
                if (snap.activeSourceId != lastSource) {
                    lastSource = snap.activeSourceId
                    pushConfigs()
                }
                delay(50)
            }
        }
        scope.launch {
            devices.collect { list ->
                list.forEach { d ->
                    if (d.id !in enabled) enabled[d.id] = settings.isEnabled(d.id)
                    if (d.id !in volumes) volumes[d.id] = settings.volume(d.id)
                    if (d.id !in delays) delays[d.id] = settings.delay(d.id)
                }
                pushConfigs()
                if (list.isNotEmpty() && !running && !busy && resumeOnLaunch && settings.wasRunning && !autoResumed) {
                    autoResumed = true
                    start()
                }
            }
        }
    }

    private var autoResumed = false

    /** The device whose output is being captured (it keeps playing natively). */
    fun effectiveSourceId(list: List<AudioDevice>): String? =
        snapshot.activeSourceId.takeIf { running } ?: sourceId ?: list.firstOrNull { it.isDefault }?.id

    private fun currentConfigs(): Map<String, TargetConfig> {
        val src = effectiveSourceId(devices.value)
        val present = devices.value.map { it.id }.toSet()
        return enabled.filter { (id, on) -> on && id != src && id in present }
            .mapValues { (id, _) -> TargetConfig(volumes[id] ?: 1f, delays[id] ?: 0) }
    }

    private fun pushConfigs() {
        if (!running) return
        val cfg = currentConfigs()
        scope.launch(Dispatchers.IO) { engineLock.withLock { engine.applyTargets(cfg) } }
    }

    fun toggle() = if (running) stop() else start()

    fun start() {
        if (running || busy) return
        busy = true
        val cfg = currentConfigs()
        scope.launch {
            launch(Dispatchers.IO) { engineLock.withLock { engine.start(cfg) } }.join()
            running = true
            busy = false
            settings.wasRunning = true
            settings.save()
        }
    }

    fun stop() {
        if (!running || busy) return
        busy = true
        scope.launch {
            launch(Dispatchers.IO) { engineLock.withLock { engine.stop() } }.join()
            running = false
            busy = false
            settings.wasRunning = false
            settings.save()
        }
    }

    fun setSource(id: String?) {
        sourceId = id
        engine.sourceId = id
        settings.sourceId = id
        settings.save()
        if (id != null) enabled[id] = false
        pushConfigs()
    }

    fun setEnabled(id: String, on: Boolean) {
        enabled[id] = on
        settings.setEnabled(id, on)
        settings.save()
        pushConfigs()
    }

    fun setVolume(id: String, v: Float) {
        volumes[id] = v
        pushConfigs()
    }

    fun setDelay(id: String, ms: Int) {
        delays[id] = ms
        pushConfigs()
    }

    /** Called when a slider drag ends, to persist. */
    fun persist(id: String) {
        settings.setVolume(id, volumes[id] ?: 1f)
        settings.setDelay(id, delays[id] ?: 0)
        settings.save()
    }

    fun updateResumeOnLaunch(on: Boolean) {
        resumeOnLaunch = on
        settings.resumeOnLaunch = on
        settings.save()
    }

    fun refreshDevices() = monitor.refresh()

    fun shutdown() {
        monitor.stop()
        engine.stop()
        settings.save()
    }
}
