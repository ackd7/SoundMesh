package io.github.soundmesh.audio

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

sealed interface StreamStatus {
    data object Idle : StreamStatus
    data object Starting : StreamStatus
    data object Running : StreamStatus
    data class Error(val message: String) : StreamStatus
}

data class TargetConfig(val volume: Float, val delayMs: Int)

data class TargetSnapshot(val status: StreamStatus, val level: Float)

data class EngineSnapshot(
    val running: Boolean,
    val sourceStatus: StreamStatus,
    val activeSourceId: String?,
    val sampleRate: Int,
    val sourceLevel: Float,
    val targets: Map<String, TargetSnapshot>,
)

private fun Throwable.toUserMessage(): String = when (this) {
    is WasapiException -> when {
        deviceGone -> "设备已断开，等待重新连接…"
        hr == Wasapi.AUDCLNT_E_DEVICE_IN_USE -> "设备正被其他程序独占"
        hr == Wasapi.AUDCLNT_E_UNSUPPORTED_FORMAT -> "设备不支持当前音频格式"
        hr == Wasapi.AUDCLNT_E_SERVICE_NOT_RUNNING -> "Windows 音频服务未运行"
        else -> message ?: "未知错误"
    }

    else -> message ?: javaClass.simpleName
}

/**
 * Captures everything that is played on the source endpoint (WASAPI loopback)
 * and converts it to interleaved stereo float samples.
 */
private class LoopbackCapture(
    private val engine: AudioEngine,
) {
    @Volatile private var running = true
    @Volatile var status: StreamStatus = StreamStatus.Starting
    @Volatile var activeSourceId: String? = null
    @Volatile var level = 0f

    private val thread = Thread(::run, "SoundMesh-capture").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
    }

    private var stereo = FloatArray(4096)
    private var rawF = FloatArray(4096)
    private var rawS = ShortArray(4096)
    private var rawI = IntArray(4096)
    private var rawB = ByteArray(4096)

    fun start() = thread.start()

    fun stop() {
        running = false
        thread.join(2000)
    }

    private fun run() {
        Wasapi.initThread()
        try {
            while (running) {
                try {
                    session()
                } catch (t: Throwable) {
                    status = StreamStatus.Error(t.toUserMessage())
                    level = 0f
                    sleepQuietly(1000)
                }
            }
        } finally {
            Wasapi.uninitThread()
        }
    }

    private fun session() {
        val wanted = engine.sourceId
        DeviceEnumerator().use { en ->
            val dev = if (wanted == null) en.openDefaultRender() else en.open(wanted)
            dev.use {
                val currentId = DeviceEnumerator.idOf(dev)
                activeSourceId = currentId
                DeviceEnumerator.activateClient(dev).use { client ->
                    val mixPtr = AudioClient.mixFormat(client)
                    val fmt: WaveFormat
                    try {
                        fmt = WaveFormat.parse(mixPtr)
                        AudioClient.initialize(client, Wasapi.AUDCLNT_STREAMFLAGS_LOOPBACK, 2_000_000L, mixPtr)
                    } finally {
                        Ole32.INSTANCE.CoTaskMemFree(mixPtr)
                    }
                    AudioClient.service(client, Wasapi.IID_IAudioCaptureClient).use { capture ->
                        engine.onSourceFormat(fmt.sampleRate)
                        AudioClient.start(client)
                        status = StreamStatus.Running
                        try {
                            var lastCheck = System.currentTimeMillis()
                            val pData = PointerByReference()
                            val frames = IntByReference()
                            val flags = IntByReference()
                            val next = IntByReference()
                            var lastPacket = System.currentTimeMillis()
                            while (running && engine.sourceId == wanted) {
                                while (true) {
                                    capture.call(5, next).check("GetNextPacketSize")
                                    if (next.value == 0) break
                                    capture.call(3, pData, frames, flags, null, null).check("Capture.GetBuffer")
                                    val n = frames.value
                                    val samples = n * 2
                                    if (stereo.size < samples) stereo = FloatArray(samples * 2)
                                    if (flags.value and Wasapi.AUDCLNT_BUFFERFLAGS_SILENT != 0) {
                                        stereo.fill(0f, 0, samples)
                                    } else {
                                        convert(pData.value, n, fmt)
                                    }
                                    capture.call(4, n).check("Capture.ReleaseBuffer")
                                    level = peakDecay(level, stereo, samples, 1f)
                                    engine.dispatch(stereo, samples)
                                    lastPacket = System.currentTimeMillis()
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastPacket > 100) level *= 0.8f
                                if (wanted == null && now - lastCheck > 1000) {
                                    lastCheck = now
                                    if (en.defaultRenderId() != currentId) break
                                }
                                sleepQuietly(3)
                            }
                        } finally {
                            AudioClient.stop(client)
                        }
                    }
                }
            }
        }
    }

    private fun convert(p: Pointer, frames: Int, fmt: WaveFormat) {
        val ch = fmt.channels
        val total = frames * ch
        when {
            fmt.isFloat && fmt.bits == 32 -> {
                if (rawF.size < total) rawF = FloatArray(total * 2)
                p.read(0, rawF, 0, total)
                for (i in 0 until frames) {
                    val l = rawF[i * ch]
                    stereo[i * 2] = l
                    stereo[i * 2 + 1] = if (ch > 1) rawF[i * ch + 1] else l
                }
            }

            !fmt.isFloat && fmt.bits == 16 -> {
                if (rawS.size < total) rawS = ShortArray(total * 2)
                p.read(0, rawS, 0, total)
                for (i in 0 until frames) {
                    val l = rawS[i * ch] / 32768f
                    stereo[i * 2] = l
                    stereo[i * 2 + 1] = if (ch > 1) rawS[i * ch + 1] / 32768f else l
                }
            }

            !fmt.isFloat && fmt.bits == 32 -> {
                if (rawI.size < total) rawI = IntArray(total * 2)
                p.read(0, rawI, 0, total)
                for (i in 0 until frames) {
                    val l = rawI[i * ch] / 2147483648f
                    stereo[i * 2] = l
                    stereo[i * 2 + 1] = if (ch > 1) rawI[i * ch + 1] / 2147483648f else l
                }
            }

            !fmt.isFloat && fmt.bits == 24 -> {
                val bytes = total * 3
                if (rawB.size < bytes) rawB = ByteArray(bytes * 2)
                p.read(0, rawB, 0, bytes)
                fun s24(idx: Int): Float {
                    val o = idx * 3
                    val v = (rawB[o].toInt() and 0xFF) or
                        ((rawB[o + 1].toInt() and 0xFF) shl 8) or
                        (rawB[o + 2].toInt() shl 16)
                    return v / 8388608f
                }
                for (i in 0 until frames) {
                    val l = s24(i * ch)
                    stereo[i * 2] = l
                    stereo[i * 2 + 1] = if (ch > 1) s24(i * ch + 1) else l
                }
            }

            else -> stereo.fill(0f, 0, frames * 2)
        }
    }
}

/**
 * Plays the shared stream on one output endpoint. Uses WASAPI shared mode with
 * AUTOCONVERTPCM so Windows handles resampling / channel mapping per device.
 */
private class RenderTarget(val deviceId: String, initialRate: Int) {
    @Volatile var volume = 1f
    @Volatile var delayMs = 0
        set(value) {
            if (field != value) {
                field = value
                resync = true
            }
        }
    @Volatile var sampleRate = initialRate
    @Volatile var status: StreamStatus = StreamStatus.Starting
    @Volatile var level = 0f

    @Volatile private var running = true
    @Volatile private var resync = false
    private var priming = true

    private val ring = FloatRing(192_000 * 2 * 3)
    private var out = FloatArray(8192)

    private val thread = Thread(::run, "SoundMesh-render").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
    }

    fun start() = thread.start()

    fun push(data: FloatArray, len: Int) {
        if (status == StreamStatus.Running) ring.write(data, len)
    }

    fun stop() {
        running = false
    }

    fun join() = thread.join(2000)

    private fun run() {
        Wasapi.initThread()
        try {
            while (running) {
                try {
                    session()
                } catch (t: Throwable) {
                    status = StreamStatus.Error(t.toUserMessage())
                    level = 0f
                    sleepQuietly(2000)
                }
            }
        } finally {
            Wasapi.uninitThread()
        }
    }

    private fun session() {
        val rate = sampleRate
        DeviceEnumerator().use { en ->
            en.open(deviceId).use { dev ->
                DeviceEnumerator.activateClient(dev).use { client ->
                    val fmt = Wasapi.floatFormat(2, rate)
                    AudioClient.initialize(
                        client,
                        Wasapi.AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM or Wasapi.AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
                        2_000_000L,
                        fmt,
                    )
                    val bufferFrames = AudioClient.bufferSize(client)
                    val wantFill = minOf(bufferFrames, rate * DEVICE_FILL_MS / 1000)
                    AudioClient.service(client, Wasapi.IID_IAudioRenderClient).use { render ->
                        ring.clear()
                        priming = true
                        AudioClient.start(client)
                        status = StreamStatus.Running
                        val pp = PointerByReference()
                        try {
                            while (running && rate == sampleRate) {
                                val pad = AudioClient.padding(client)
                                val toWrite = wantFill - pad
                                if (toWrite > 0) {
                                    render.call(3, toWrite, pp).check("Render.GetBuffer")
                                    fill(toWrite, rate)
                                    pp.value.write(0, out, 0, toWrite * 2)
                                    render.call(4, toWrite, 0).check("Render.ReleaseBuffer")
                                }
                                sleepQuietly(3)
                            }
                        } finally {
                            AudioClient.stop(client)
                            status = StreamStatus.Starting
                        }
                    }
                }
            }
        }
    }

    private fun fill(frames: Int, rate: Int) {
        val need = frames * 2
        if (out.size < need) out = FloatArray(need * 2)
        val target = ((BASE_LATENCY_MS + delayMs) * rate / 1000) * 2
        val tolerance = (DRIFT_TOLERANCE_MS * rate / 1000) * 2
        var avail = ring.available()

        if (resync) {
            resync = false
            if (avail < target) priming = true else if (avail > target) ring.skip(avail - target)
            avail = ring.available()
        }
        if (priming) {
            if (avail >= target) {
                priming = false
            } else {
                out.fill(0f, 0, need)
                level *= 0.85f
                return
            }
        }
        if (avail > target + tolerance) {
            ring.skip(avail - target)
        }
        val got = ring.read(out, need)
        if (got < need) {
            out.fill(0f, got, need)
            priming = true
        }
        val v = volume
        if (v != 1f) {
            for (i in 0 until got) out[i] = (out[i] * v).coerceIn(-1f, 1f)
        }
        level = peakDecay(level, out, got, 1f)
    }
}

private const val BASE_LATENCY_MS = 40
private const val DEVICE_FILL_MS = 40
private const val DRIFT_TOLERANCE_MS = 60

private fun peakDecay(prev: Float, data: FloatArray, len: Int, gain: Float): Float {
    var peak = 0f
    var i = 0
    while (i < len) {
        val a = abs(data[i])
        if (a > peak) peak = a
        i += 2
    }
    return max(peak * gain, prev * 0.9f).coerceIn(0f, 1f)
}

private fun sleepQuietly(ms: Long) {
    try {
        Thread.sleep(ms)
    } catch (_: InterruptedException) {
    }
}

/**
 * Coordinates one loopback capture with any number of render targets.
 * All public methods are safe to call from the UI thread.
 */
class AudioEngine {
    /** `null` means "follow the Windows default output device". */
    @Volatile var sourceId: String? = null

    @Volatile private var sampleRate = 48_000
    private var capture: LoopbackCapture? = null
    private val targets = ConcurrentHashMap<String, RenderTarget>()

    val isRunning: Boolean get() = capture != null

    @Synchronized
    fun start(configs: Map<String, TargetConfig>) {
        if (capture != null) return
        capture = LoopbackCapture(this).also { it.start() }
        applyTargets(configs)
    }

    @Synchronized
    fun stop() {
        capture?.stop()
        capture = null
        val all = targets.values.toList()
        targets.clear()
        all.forEach { it.stop() }
        all.forEach { it.join() }
    }

    /** Adds, removes or updates render targets to match [configs]. */
    @Synchronized
    fun applyTargets(configs: Map<String, TargetConfig>) {
        if (capture == null) return
        val removed = targets.keys - configs.keys
        removed.forEach { id -> targets.remove(id)?.stop() }
        configs.forEach { (id, cfg) ->
            val t = targets.getOrPut(id) { RenderTarget(id, sampleRate).also { it.start() } }
            t.volume = cfg.volume
            t.delayMs = cfg.delayMs
        }
    }

    internal fun onSourceFormat(rate: Int) {
        sampleRate = rate
        targets.values.forEach { it.sampleRate = rate }
    }

    internal fun dispatch(data: FloatArray, len: Int) {
        val src = capture?.activeSourceId
        for (t in targets.values) {
            // Never feed the device we are capturing from, it would create a feedback loop.
            if (t.deviceId != src) t.push(data, len)
        }
    }

    fun snapshot(): EngineSnapshot {
        val c = capture
        return EngineSnapshot(
            running = c != null,
            sourceStatus = c?.status ?: StreamStatus.Idle,
            activeSourceId = c?.activeSourceId,
            sampleRate = sampleRate,
            sourceLevel = c?.level ?: 0f,
            targets = targets.mapValues { (_, t) -> TargetSnapshot(t.status, t.level) },
        )
    }
}

/** Periodically enumerates output devices on a dedicated COM thread. */
class DeviceMonitor {
    private val _devices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val devices: StateFlow<List<AudioDevice>> = _devices.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var running = true
    @Volatile private var refreshRequested = false

    private val thread = Thread(::run, "SoundMesh-devices").apply { isDaemon = true }

    fun start() = thread.start()

    fun refresh() {
        refreshRequested = true
        thread.interrupt()
    }

    fun stop() {
        running = false
        thread.interrupt()
    }

    private fun run() {
        Wasapi.initThread()
        try {
            while (running) {
                try {
                    val list = DeviceEnumerator().use { it.listRender() }
                    if (list != _devices.value) _devices.value = list
                    _error.value = null
                } catch (t: Throwable) {
                    _error.value = t.toUserMessage()
                }
                refreshRequested = false
                try {
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                }
            }
        } finally {
            Wasapi.uninitThread()
        }
    }
}
