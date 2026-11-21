package io.github.soundmesh.audio

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Minimal, dependency-free WASAPI bindings built on top of JNA COM vtable calls.
 *
 * Only the handful of interfaces we need are covered:
 * IMMDeviceEnumerator, IMMDeviceCollection, IMMDevice, IPropertyStore,
 * IAudioClient, IAudioCaptureClient and IAudioRenderClient.
 */
internal object Wasapi {
    val CLSID_MMDeviceEnumerator = Guid.GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    val IID_IMMDeviceEnumerator = Guid.GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    val IID_IAudioClient = Guid.GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}")
    val IID_IAudioRenderClient = Guid.GUID("{F294ACFC-3146-4483-A7BF-ADDCA7C260E2}")
    val IID_IAudioCaptureClient = Guid.GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}")

    const val E_RENDER = 0
    const val E_CONSOLE = 0
    const val DEVICE_STATE_ACTIVE = 0x1
    const val STGM_READ = 0

    const val AUDCLNT_SHAREMODE_SHARED = 0
    const val AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
    const val AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000
    const val AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM = 0x80000000.toInt()
    const val AUDCLNT_BUFFERFLAGS_SILENT = 0x2

    const val AUDCLNT_E_DEVICE_INVALIDATED = 0x88890004.toInt()
    const val AUDCLNT_E_UNSUPPORTED_FORMAT = 0x88890008.toInt()
    const val AUDCLNT_E_DEVICE_IN_USE = 0x8889000A.toInt()
    const val AUDCLNT_E_SERVICE_NOT_RUNNING = 0x88890010.toInt()

    const val VT_LPWSTR: Short = 31
    const val VT_UI4: Short = 19

    // PKEY_Device_FriendlyName
    val PKEY_FRIENDLY_NAME = PropertyKey("{A45C254E-DF1C-4EFD-8020-67D146A850E0}", 14)
    // PKEY_Device_EnumeratorName (e.g. "BTHENUM", "USB", "HDAUDIO")
    val PKEY_ENUMERATOR_NAME = PropertyKey("{A45C254E-DF1C-4EFD-8020-67D146A850E0}", 24)
    // PKEY_AudioEndpoint_FormFactor
    val PKEY_FORM_FACTOR = PropertyKey("{1DA5D803-D492-4EDD-8C23-E0C0FFEE7F0E}", 0)

    // KSDATAFORMAT_SUBTYPE_IEEE_FLOAT
    private val SUBTYPE_IEEE_FLOAT = Guid.GUID("{00000003-0000-0010-8000-00AA00389B71}")

    private interface Ole32Ex : Library {
        fun PropVariantClear(pvar: Pointer): Int
    }

    private val ole32Ex: Ole32Ex by lazy { Native.load("ole32", Ole32Ex::class.java) }

    fun propVariantClear(p: Pointer) {
        ole32Ex.PropVariantClear(p)
    }

    /** Initialise COM (MTA) for the calling thread. Safe to call multiple times. */
    fun initThread() {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
    }

    fun uninitThread() {
        Ole32.INSTANCE.CoUninitialize()
    }

    /** Builds a WAVEFORMATEXTENSIBLE describing 32-bit float PCM. */
    fun floatFormat(channels: Int, sampleRate: Int): Memory {
        val m = Memory(40)
        m.clear()
        val blockAlign = channels * 4
        m.setShort(0, 0xFFFE.toShort()) // WAVE_FORMAT_EXTENSIBLE
        m.setShort(2, channels.toShort())
        m.setInt(4, sampleRate)
        m.setInt(8, sampleRate * blockAlign)
        m.setShort(12, blockAlign.toShort())
        m.setShort(14, 32)
        m.setShort(16, 22)
        m.setShort(18, 32)
        m.setInt(20, if (channels == 1) 0x4 else 0x3) // FRONT_CENTER or FRONT_LEFT|FRONT_RIGHT
        SUBTYPE_IEEE_FLOAT.write()
        m.write(24, SUBTYPE_IEEE_FLOAT.pointer.getByteArray(0, 16), 0, 16)
        return m
    }
}

internal class PropertyKey(guid: String, pid: Int) {
    val memory: Memory = Memory(20).also { m ->
        val g = Guid.GUID(guid)
        g.write()
        m.write(0, g.pointer.getByteArray(0, 16), 0, 16)
        m.setInt(16, pid)
    }
}

class WasapiException(val what: String, val hr: Int) :
    RuntimeException("$what failed: 0x%08X".format(hr)) {
    val deviceGone: Boolean
        get() = hr == Wasapi.AUDCLNT_E_DEVICE_INVALIDATED || hr == 0x80070490.toInt()
}

internal fun Int.check(what: String): Int {
    if (this < 0) throw WasapiException(what, this)
    return this
}

/** A thin COM pointer wrapper that can invoke arbitrary vtable slots. */
internal open class ComPtr(p: Pointer) : Unknown(p), AutoCloseable {
    fun call(index: Int, vararg args: Any?): Int {
        val all = arrayOfNulls<Any>(args.size + 1)
        all[0] = pointer
        System.arraycopy(args, 0, all, 1, args.size)
        return _invokeNativeInt(index, all)
    }

    override fun close() {
        try {
            Release()
        } catch (_: Throwable) {
        }
    }
}

internal data class WaveFormat(val channels: Int, val sampleRate: Int, val bits: Int, val isFloat: Boolean) {
    val blockAlign: Int get() = channels * bits / 8

    companion object {
        fun parse(p: Pointer): WaveFormat {
            val tag = p.getShort(0).toInt() and 0xFFFF
            val ch = p.getShort(2).toInt() and 0xFFFF
            val rate = p.getInt(4)
            val bits = p.getShort(14).toInt() and 0xFFFF
            val isFloat = when (tag) {
                3 -> true
                0xFFFE -> p.getInt(24) == 3
                else -> false
            }
            return WaveFormat(ch, rate, bits, isFloat)
        }
    }
}

enum class DeviceKind { Bluetooth, Headphones, Speakers, Usb, Hdmi, Other }

data class AudioDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val isDefault: Boolean,
)

/** Wraps IMMDeviceEnumerator. Must be used on a COM-initialised thread. */
internal class DeviceEnumerator : AutoCloseable {
    private val enumerator: ComPtr

    init {
        val pp = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            Wasapi.CLSID_MMDeviceEnumerator,
            null,
            WTypes.CLSCTX_ALL,
            Wasapi.IID_IMMDeviceEnumerator,
            pp,
        )
        hr.toInt().check("CoCreateInstance(MMDeviceEnumerator)")
        enumerator = ComPtr(pp.value)
    }

    fun defaultRenderId(): String? {
        val pp = PointerByReference()
        val hr = enumerator.call(4, Wasapi.E_RENDER, Wasapi.E_CONSOLE, pp)
        if (hr < 0) return null
        return ComPtr(pp.value).use { idOf(it) }
    }

    fun openDefaultRender(): ComPtr {
        val pp = PointerByReference()
        enumerator.call(4, Wasapi.E_RENDER, Wasapi.E_CONSOLE, pp).check("GetDefaultAudioEndpoint")
        return ComPtr(pp.value)
    }

    fun open(id: String): ComPtr {
        val pp = PointerByReference()
        enumerator.call(5, WString(id), pp).check("GetDevice")
        return ComPtr(pp.value)
    }

    fun listRender(): List<AudioDevice> {
        val defaultId = defaultRenderId()
        val pp = PointerByReference()
        enumerator.call(3, Wasapi.E_RENDER, Wasapi.DEVICE_STATE_ACTIVE, pp).check("EnumAudioEndpoints")
        val result = ArrayList<AudioDevice>()
        ComPtr(pp.value).use { collection ->
            val count = IntByReference()
            collection.call(3, count).check("IMMDeviceCollection.GetCount")
            for (i in 0 until count.value) {
                val pd = PointerByReference()
                if (collection.call(4, i, pd) < 0) continue
                ComPtr(pd.value).use { dev ->
                    val id = idOf(dev) ?: return@use
                    val props = readProps(dev)
                    result += AudioDevice(
                        id = id,
                        name = props.name ?: "Unknown device",
                        kind = props.kind,
                        isDefault = id == defaultId,
                    )
                }
            }
        }
        return result
    }

    private class Props(val name: String?, val kind: DeviceKind)

    private fun readProps(dev: ComPtr): Props {
        val pp = PointerByReference()
        if (dev.call(4, Wasapi.STGM_READ, pp) < 0) return Props(null, DeviceKind.Other)
        return ComPtr(pp.value).use { store ->
            val name = readString(store, Wasapi.PKEY_FRIENDLY_NAME)
            val enumerator = readString(store, Wasapi.PKEY_ENUMERATOR_NAME)?.uppercase() ?: ""
            val formFactor = readUInt(store, Wasapi.PKEY_FORM_FACTOR) ?: -1
            val kind = when {
                enumerator.startsWith("BTH") -> DeviceKind.Bluetooth
                formFactor == 3 || formFactor == 4 -> DeviceKind.Headphones // Headphones / Headset
                formFactor == 8 -> DeviceKind.Hdmi // DigitalAudioDisplayDevice
                enumerator == "USB" -> DeviceKind.Usb
                formFactor == 1 -> DeviceKind.Speakers
                else -> DeviceKind.Other
            }
            Props(name, kind)
        }
    }

    private fun readString(store: ComPtr, key: PropertyKey): String? {
        val pv = Memory(24)
        pv.clear()
        if (store.call(5, key.memory, pv) < 0) return null
        return try {
            if (pv.getShort(0) == Wasapi.VT_LPWSTR) pv.getPointer(8)?.getWideString(0) else null
        } finally {
            Wasapi.propVariantClear(pv)
        }
    }

    private fun readUInt(store: ComPtr, key: PropertyKey): Int? {
        val pv = Memory(24)
        pv.clear()
        if (store.call(5, key.memory, pv) < 0) return null
        return try {
            if (pv.getShort(0) == Wasapi.VT_UI4) pv.getInt(8) else null
        } finally {
            Wasapi.propVariantClear(pv)
        }
    }

    override fun close() = enumerator.close()

    companion object {
        fun idOf(dev: ComPtr): String? {
            val pp = PointerByReference()
            if (dev.call(5, pp) < 0) return null
            val p = pp.value ?: return null
            return try {
                p.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(p)
            }
        }

        fun activateClient(dev: ComPtr): ComPtr {
            val pp = PointerByReference()
            dev.call(3, Wasapi.IID_IAudioClient, WTypes.CLSCTX_ALL, null, pp).check("IMMDevice.Activate")
            return ComPtr(pp.value)
        }
    }
}

/** Helpers for IAudioClient vtable slots. */
internal object AudioClient {
    fun initialize(client: ComPtr, flags: Int, bufferHns: Long, format: Pointer) =
        client.call(3, Wasapi.AUDCLNT_SHAREMODE_SHARED, flags, bufferHns, 0L, format, null)
            .check("IAudioClient.Initialize")

    fun bufferSize(client: ComPtr): Int {
        val r = IntByReference()
        client.call(4, r).check("IAudioClient.GetBufferSize")
        return r.value
    }

    fun padding(client: ComPtr): Int {
        val r = IntByReference()
        client.call(6, r).check("IAudioClient.GetCurrentPadding")
        return r.value
    }

    fun mixFormat(client: ComPtr): Pointer {
        val pp = PointerByReference()
        client.call(8, pp).check("IAudioClient.GetMixFormat")
        return pp.value
    }

    fun start(client: ComPtr) = client.call(10).check("IAudioClient.Start")
    fun stop(client: ComPtr) = client.call(11)

    fun service(client: ComPtr, iid: Guid.GUID): ComPtr {
        val pp = PointerByReference()
        client.call(14, iid, pp).check("IAudioClient.GetService")
        return ComPtr(pp.value)
    }
}
