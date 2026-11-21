package io.github.soundmesh

import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(
        val latestVersion: String,
        val url: String,
        val notes: String,
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}

object UpdateChecker {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    fun checkLatest(currentVersion: String = AppInfo.VERSION): UpdateState {
        return try {
            val request = HttpRequest.newBuilder(URI.create(AppInfo.LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "${AppInfo.NAME}/$currentVersion")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 404) {
                return UpdateState.UpToDate(currentVersion)
            }
            if (response.statusCode() !in 200..299) {
                return UpdateState.Error("HTTP ${response.statusCode()}")
            }
            val body = response.body()
            val tag = extractJsonString(body, "tag_name")?.trim().orEmpty()
            if (tag.isEmpty()) {
                return UpdateState.Error("无法解析版本信息")
            }
            val latest = tag.removePrefix("v").removePrefix("V")
            val htmlUrl = extractJsonString(body, "html_url") ?: AppInfo.RELEASES_URL
            val notes = extractJsonString(body, "body")?.trim().orEmpty()
            if (isNewerVersion(latest, currentVersion)) {
                UpdateState.Available(latestVersion = latest, url = htmlUrl, notes = notes)
            } else {
                UpdateState.UpToDate(currentVersion)
            }
        } catch (t: Throwable) {
            UpdateState.Error(t.message?.take(60) ?: "网络连接失败")
        }
    }

    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val a = parseSemver(latest)
        val b = parseSemver(current)
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseSemver(v: String): List<Int> =
        v.removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.toIntOrNull() }

    internal fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\""
        var idx = json.indexOf(pattern)
        while (idx >= 0) {
            var pos = idx + pattern.length
            while (pos < json.length && json[pos].isWhitespace()) pos++
            if (pos < json.length && json[pos] == ':') {
                pos++
                while (pos < json.length && json[pos].isWhitespace()) pos++
                if (pos < json.length && json[pos] == '"') {
                    return readJsonString(json, pos + 1)
                }
            }
            idx = json.indexOf(pattern, idx + pattern.length)
        }
        return null
    }

    private fun readJsonString(s: String, start: Int): String {
        val sb = StringBuilder()
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == '"') return sb.toString()
            if (c == '\\' && i + 1 < s.length) {
                i++
                when (val esc = s[i]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> if (i + 4 < s.length) {
                        val hex = s.substring(i + 1, i + 5)
                        hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        i += 4
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    fun openInBrowser(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
