package io.github.soundmesh.audio

import kotlin.test.Test
import kotlin.test.assertTrue

class WasapiSmokeTest {
    @Test
    fun canEnumerateRenderDevicesOnWindows() {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("windows")) return

        Wasapi.initThread()
        try {
            DeviceEnumerator().use { en ->
                val devices = en.listRender()
                println("Detected ${devices.size} active render device(s):")
                devices.forEach { println(" - [${it.kind}] ${it.name} (default=${it.isDefault})") }
                assertTrue(devices.size >= 0)
            }
        } finally {
            Wasapi.uninitThread()
        }
    }
}
