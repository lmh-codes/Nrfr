package com.github.nrfr.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(
    val version: String,
    val downloadUrl: String,
    val fileName: String,
    val sizeBytes: Long = -1L,
    /** 可选 SHA-256；空表示仅做魔数/体积校验 */
    val sha256: String = ""
)

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    /** 0..100；总大小未知时为 -1 */
    val percent: Int
) {
    fun detailText(): String {
        val readMb = bytesRead / (1024.0 * 1024.0)
        return if (totalBytes > 0) {
            val totalMb = totalBytes / (1024.0 * 1024.0)
            String.format("%.1f / %.1f MB · %d%%", readMb, totalMb, percent.coerceIn(0, 100))
        } else {
            String.format("%.1f MB · 下载中…", readMb)
        }
    }
}

/**
 * 在线更新：单线走本仓库 GitHub Releases。
 * 检查：Releases API；下载：官方 browser_download_url。
 */
object AppUpdateManager {
    private const val OFFICIAL_API =
        "https://api.github.com/repos/lmh-codes/Nrfr/releases/latest"
    private const val PREFS = "nrfr_update"
    private const val KEY_PENDING_APK = "pending_apk"
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val MIN_APK_BYTES = 64L * 1024

    fun fetchLatestRelease(context: Context, currentVersion: String = ""): AppRelease {
        @Suppress("UNUSED_PARAMETER")
        val ignored = context to currentVersion
        val json = requestText(OFFICIAL_API, MAX_METADATA_BYTES, accept = "application/vnd.github+json")
        return parseRelease(json)
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

    fun downloadApk(
        context: Context,
        release: AppRelease,
        onProgress: (DownloadProgress) -> Unit = {}
    ): File {
        val target = File(context.cacheDir, "update-${release.version}-${release.fileName}")
        if (target.isFile) {
            try {
                assertValidApk(target, release.sizeBytes, release.sha256)
                val size = target.length()
                onProgress(DownloadProgress(size, size, 100))
                return target
            } catch (_: Exception) {
                target.delete()
            }
        }
        try {
            downloadFile(release.downloadUrl, target, release.sizeBytes, onProgress)
            assertValidApk(target, release.sizeBytes, release.sha256)
            return target
        } catch (error: Exception) {
            target.delete()
            File(target.parentFile, "${target.name}.part").delete()
            throw error
        }
    }

    /** 安装结果：STARTED=已拉起系统安装；NEED_PERMISSION=已跳转授权页，APK 已缓存待续装 */
    enum class InstallOutcome {
        STARTED,
        NEED_PERMISSION
    }

    fun installApk(context: Context, apk: File): InstallOutcome {
        require(apk.isFile) { "更新文件不存在" }
        assertInstallable(context, apk)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            savePendingApk(context, apk)
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return InstallOutcome.NEED_PERMISSION
        }

        clearPendingApk(context)
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
        return InstallOutcome.STARTED
    }

    fun pendingApkFile(context: Context): File? {
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK, null)
            ?.trim()
            .orEmpty()
        if (path.isEmpty()) return null
        val file = File(path)
        return if (file.isFile) file else {
            clearPendingApk(context)
            null
        }
    }

    fun tryResumePendingInstall(context: Context): InstallOutcome? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return null
        }
        val apk = pendingApkFile(context) ?: return null
        return installApk(context, apk)
    }

    private fun savePendingApk(context: Context, apk: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_APK, apk.absolutePath)
            .apply()
    }

    private fun clearPendingApk(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_APK)
            .apply()
    }

    private fun assertInstallable(context: Context, apk: File) {
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: error("无法解析更新包")

        archive.applicationInfo?.sourceDir = apk.absolutePath
        archive.applicationInfo?.publicSourceDir = apk.absolutePath

        val packageName = archive.packageName
            ?: error("更新包缺少包名")
        require(packageName == context.packageName) {
            "更新包名不一致（$packageName）"
        }

        val installed = try {
            if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }

        val currentHashes = signingCertHashes(installed)
        val updateHashes = signingCertHashes(archive)
        if (currentHashes.isEmpty() || updateHashes.isEmpty()) return
        if (currentHashes.intersect(updateHashes).isEmpty()) {
            error(
                "安装失败：签名不一致。当前安装包与更新包不是同一签名" +
                    "（常见于 debug 测试包升级正式版）。请先卸载当前应用，再安装正式版 APK。"
            )
        }
    }

    private fun signingCertHashes(info: android.content.pm.PackageInfo): Set<String> {
        val digests = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            for (sig in signers) {
                digests += sha256Hex(sig.toByteArray())
            }
        } else {
            @Suppress("DEPRECATION")
            val signatures = info.signatures ?: return emptySet()
            for (sig in signatures) {
                digests += sha256Hex(sig.toByteArray())
            }
        }
        return digests
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseRelease(json: String): AppRelease {
        val release = JSONObject(json)
        val version = release.optString("tag_name").removePrefix("v").trim()
        require(version.isNotEmpty()) { "仓库没有有效版本号" }

        val assets = release.optJSONArray("assets") ?: error("Release 没有 APK 资产")
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            val size = asset.optLong("size", -1L)
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                return AppRelease(version, url, name, size)
            }
        }
        error("Release 没有可下载的 APK")
    }

    private fun requestText(url: String, maxBytes: Int, accept: String): String {
        val connection = openConnection(
            url = url,
            accept = accept,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 20_000
        )
        return try {
            val body = connection.inputStream.use { input ->
                input.readLimited(maxBytes).toString(Charsets.UTF_8)
            }
            val trimmed = body.trimStart()
            require(trimmed.startsWith("{") || trimmed.startsWith("[")) { "返回内容不是 JSON" }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(
        url: String,
        target: File,
        knownSize: Long,
        onProgress: (DownloadProgress) -> Unit
    ) {
        onProgress(DownloadProgress(0L, knownSize, if (knownSize > 0) 0 else -1))
        val connection = openConnection(
            url = url,
            accept = "*/*",
            connectTimeoutMs = 8_000,
            readTimeoutMs = 90_000
        )
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            temporary.delete()
            val headerSize = connection.contentLengthLong
            val totalBytes = when {
                headerSize > 0 -> headerSize
                knownSize > 0 -> knownSize
                else -> -1L
            }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    var total = 0L
                    var lastEmitAt = 0L
                    var lastEmitBytes = -1L
                    val buffer = ByteArray(64 * 1024)
                    onProgress(DownloadProgress(0L, totalBytes, if (totalBytes > 0) 0 else -1))
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK 文件过大" }
                        output.write(buffer, 0, count)

                        val now = System.currentTimeMillis()
                        val percent = if (totalBytes > 0) {
                            ((total * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        val shouldEmit = total == totalBytes ||
                            lastEmitBytes < 0 ||
                            total - lastEmitBytes >= 128 * 1024 ||
                            now - lastEmitAt >= 200
                        if (shouldEmit) {
                            lastEmitAt = now
                            lastEmitBytes = total
                            onProgress(DownloadProgress(total, totalBytes, percent))
                        }
                    }
                    onProgress(DownloadProgress(total, totalBytes, if (totalBytes > 0) 100 else -1))
                }
            }
            require(temporary.length() > 0) { "下载文件为空" }
            if (knownSize > 0 && temporary.length() != knownSize) {
                error("下载不完整（${temporary.length()} / $knownSize）")
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "无法保存更新文件" }
        } finally {
            connection.disconnect()
            if (temporary.exists() && !target.exists()) {
                temporary.delete()
            }
        }
    }

    private fun assertValidApk(file: File, knownSize: Long, expectedSha256: String = "") {
        require(file.isFile) { "更新文件不存在" }
        require(file.length() >= MIN_APK_BYTES) { "下载文件过小（${file.length()}）" }
        if (knownSize > 0) {
            require(file.length() == knownSize) {
                "下载体积不符（${file.length()} / $knownSize）"
            }
        }
        file.inputStream().use { input ->
            val b0 = input.read()
            val b1 = input.read()
            require(b0 == 'P'.code && b1 == 'K'.code) { "下载内容不是 APK（ZIP 魔数校验失败）" }
        }
        val expect = expectedSha256.trim().lowercase()
        if (expect.isNotEmpty()) {
            val actual = fileSha256(file)
            require(actual == expect) {
                "文件校验失败（SHA-256 不一致）"
            }
        }
    }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openConnection(
        url: String,
        accept: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "Nrfr-Android")
                setRequestProperty("Cache-Control", "no-cache")
                connect()
            }
            val code = connection.responseCode
            if (code in 200..299) return connection
            if (code in 300..399 && redirects < 8) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank()) { "服务器返回 HTTP $code（无跳转地址）" }
                current = if (location.startsWith("http")) {
                    location
                } else {
                    URL(URL(current), location).toString()
                }
                redirects += 1
                continue
            }
            connection.disconnect()
            error("服务器返回 HTTP $code")
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
