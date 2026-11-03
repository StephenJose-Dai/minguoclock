package app.minguo.calendar

import java.net.HttpURLConnection
import java.net.URL

object Net {
    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 10_000): String =
        String(bytes(url, headers, timeoutMs), Charsets.UTF_8)

    fun bytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 10_000): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs * 2
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MinguoClock/3.1")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            // 不手动设置 Accept-Encoding，系统会自动处理 gzip
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
