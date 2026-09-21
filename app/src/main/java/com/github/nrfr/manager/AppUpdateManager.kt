package com.github.nrfr.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val downloadUrl: String,
    val fileName: String
)

object AppUpdateManager {
    private const val API_URL = "https://api.github.com/repos/lmh-codes/Nrfr/releases/latest"
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_APK_BYTES = 100L * 1024 * 1024

    fun fetchLatestRelease(): AppRelease {
        val json = requestText(API_URL, MAX_METADATA_BYTES)
        val release = JSONObject(json)
        val version = release.optString("tag_name").removePrefix("v").trim()
        require(version.isNotEmpty()) { "仓库没有有效版本号" }

        val assets = release.optJSONArray("assets") ?: error("Release 没有 APK 资产")
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                return AppRelease(version, url, name)
            }
        }
        error("Release 没有可下载的 APK")
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    fun downloadApk(context: Context, release: AppRelease): File {
        val target = File(context.cacheDir, "update-${release.version}-${release.fileName}")
        downloadFile(release.downloadUrl, target)
        return target
    }

    fun installApk(context: Context, apk: File) {
        require(apk.isFile) { "更新文件不存在" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun requestText(url: String, maxBytes: Int): String {
        val connection = openConnection(url)
        return try {
            connection.inputStream.use { input ->
                input.readLimited(maxBytes).toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File) {
        val connection = openConnection(url)
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            temporary.delete()
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK 文件过大" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(temporary.length() > 0) { "下载文件为空" }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "无法保存更新文件" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Nrfr-Android")
            instanceFollowRedirects = true
            connect()
            check(responseCode in 200..299) { "服务器返回 HTTP $responseCode" }
        }
    }

    private fun versionParts(version: String): List<Int> {
        return version.removePrefix("v")
            .split(".")
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }

    private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "服务器响应过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
