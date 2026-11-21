package io.github.soundmesh

import java.io.File
import java.util.Properties

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色模式"),
    Dark("深色模式"),
}

/** Persists user choices in %APPDATA%\SoundMesh\settings.properties. */
class Settings {
    private val file: File = run {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"))
        File(base, "SoundMesh").apply { mkdirs() }.resolve("settings.properties")
    }
    private val props = Properties()

    init {
        if (file.exists()) {
            runCatching { file.reader(Charsets.UTF_8).use { props.load(it) } }
        }
    }

    var sourceId: String?
        get() = props.getProperty("source")?.takeIf { it.isNotEmpty() }
        set(value) {
            props.setProperty("source", value ?: "")
        }

    var resumeOnLaunch: Boolean
        get() = props.getProperty("resumeOnLaunch", "false").toBoolean()
        set(value) {
            props.setProperty("resumeOnLaunch", value.toString())
        }

    var wasRunning: Boolean
        get() = props.getProperty("wasRunning", "false").toBoolean()
        set(value) {
            props.setProperty("wasRunning", value.toString())
        }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(props.getProperty("themeMode", ThemeMode.System.name)) }
            .getOrDefault(ThemeMode.System)
        set(value) {
            props.setProperty("themeMode", value.name)
        }

    fun isEnabled(id: String) = props.getProperty("on|$id", "false").toBoolean()
    fun setEnabled(id: String, on: Boolean) = props.setProperty("on|$id", on.toString())

    fun volume(id: String) = props.getProperty("vol|$id")?.toFloatOrNull() ?: 1f
    fun setVolume(id: String, v: Float) = props.setProperty("vol|$id", v.toString())

    fun delay(id: String) = props.getProperty("delay|$id")?.toIntOrNull() ?: 0
    fun setDelay(id: String, ms: Int) = props.setProperty("delay|$id", ms.toString())

    fun save() {
        runCatching {
            file.writer(Charsets.UTF_8).use { props.store(it, "SoundMesh settings") }
        }
    }
}
