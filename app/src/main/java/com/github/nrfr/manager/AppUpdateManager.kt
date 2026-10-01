package com.github.nrfr.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
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
    /** 远程清单提供的 SHA-256；空表示仅做魔数/体积校验 */
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
 * 在线更新：优先读远程 update-manifest.json（版本 / 下载地址 / SHA-256 / 镜像），
 * 发版只需改远程文件与 APK，不必重编更新逻辑。清单失败时再回退 GitHub Releases API。
 */
object AppUpdateManager {
    private const val OFFICIAL_API =
        "https://api.github.com/repos/lmh-codes/Nrfr/releases/latest"
    private const val OFFICIAL_MANIFEST_JSON =
        "https://raw.githubusercontent.com/lmh-codes/Nrfr/main/update-manifest.json"
    private const val OFFICIAL_MIRRORS_JSON =
        "https://raw.githubusercontent.com/lmh-codes/Nrfr/main/update-mirrors.json"
    private const val SELFHOST_MANIFEST_JSON =
        "https://git.220324.xyz/update-manifest.json"
    private const val SELFHOST_MIRRORS_JSON =
        "https://git.220324.xyz/update-mirrors.json"
    private const val PREFS = "nrfr_update"
    private const val KEY_MIRRORS = "mirrors_json"
    private const val KEY_MIRRORS_AT = "mirrors_fetched_at"
    private const val KEY_PENDING_APK = "pending_apk"
    private const val MIRROR_TTL_MS = 6L * 60L * 60L * 1000L
    private const val MAX_METADATA_BYTES = 1024 * 1024
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private const val MIN_APK_BYTES = 64L * 1024

    /** 写死引导源：仅用于拉取远程清单；正式加速域名写在清单 mirrors 里 */
    private val BOOTSTRAP_MIRRORS = listOf(
        "https://gh.ddlc.top/",
        "https://git.220324.xyz/"
    )

    fun fetchLatestRelease(context: Context, currentVersion: String = ""): AppRelease {
        @Suppress("UNUSED_PARAMETER")
        val ignored = currentVersion
        val errors = mutableListOf<String>()

        // 1) 远程清单（文件校验更新）：改 JSON 即可发版，无需动 APK 内更新代码
        for (url in manifestCandidates()) {
            try {
                val json = requestText(url, MAX_METADATA_BYTES, accept = "application/json")
                val release = parseManifest(json)
                cacheMirrorsFromList(context, release.mirrorsHint)
                return release.release
            } catch (error: Exception) {
                errors += "${channelLabel(url)}: ${error.message ?: error.javaClass.simpleName}"
            }
        }

        // 2) 回退：GitHub Releases API + 镜像列表
        refreshMirrors(context)
        for (url in apiCandidates(context)) {
            try {
                val json = requestText(url, MAX_METADATA_BYTES, accept = "application/vnd.github+json")
                return parseRelease(json)
            } catch (error: Exception) {
                errors += "${channelLabel(url)}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        error("检查更新失败（清单与官方均不可用）：${errors.joinToString("；")}")
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
        refreshMirrors(context)
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
        val candidates = downloadCandidates(context, release.downloadUrl)
        val errors = mutableListOf<String>()
        for ((index, url) in candidates.withIndex()) {
            try {
                if (index > 0) {
                    onProgress(DownloadProgress(0L, release.sizeBytes, if (release.sizeBytes > 0) 0 else -1))
                }
                downloadFile(url, target, release.sizeBytes, onProgress)
                assertValidApk(target, release.sizeBytes, release.sha256)
                return target
            } catch (error: Exception) {
                target.delete()
                File(target.parentFile, "${target.name}.part").delete()
                errors += "${channelLabel(url)}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        error("下载失败（官方与镜像均不可用）：${errors.joinToString("；")}")
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

    private data class ManifestResult(
        val release: AppRelease,
        val mirrorsHint: List<String>
    )

    private fun manifestCandidates(): List<String> {
        // 先官方 raw，失败再用自建 / 镜像前缀
        val urls = linkedSetOf(
            OFFICIAL_MANIFEST_JSON,
            SELFHOST_MANIFEST_JSON
        )
        for (prefix in BOOTSTRAP_MIRRORS) {
            urls += prefix + OFFICIAL_MANIFEST_JSON
        }
        return urls.toList()
    }

    private fun parseManifest(raw: String): ManifestResult {
        val obj = JSONObject(raw.trim())
        val version = obj.optString("version").removePrefix("v").trim()
        require(version.isNotEmpty()) { "清单缺少 version" }
        val downloadUrl = obj.optString("downloadUrl").trim()
        require(downloadUrl.startsWith("https://")) { "清单 downloadUrl 无效" }
        val fileName = obj.optString("fileName").ifBlank {
            downloadUrl.substringAfterLast('/').ifBlank { "Nrfr-$version.apk" }
        }
        val sizeBytes = obj.optLong("sizeBytes", -1L)
        val sha256 = obj.optString("sha256").trim().lowercase()
        val mirrors = mutableListOf<String>()
        val arr = obj.optJSONArray("mirrors")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) mirrors += normalizePrefix(item)
            }
        }
        return ManifestResult(
            release = AppRelease(
                version = version,
                downloadUrl = downloadUrl,
                fileName = fileName,
                sizeBytes = sizeBytes,
                sha256 = sha256
            ),
            mirrorsHint = mirrors
        )
    }

    private fun cacheMirrorsFromList(context: Context, mirrors: List<String>) {
        if (mirrors.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MIRRORS, JSONArray(mirrors).toString())
            .putLong(KEY_MIRRORS_AT, System.currentTimeMillis())
            .apply()
    }

    /** 拉取并缓存远程镜像列表；失败时保留旧缓存 / 引导列表 */
    fun refreshMirrors(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetchedAt = prefs.getLong(KEY_MIRRORS_AT, 0L)
        if (!force && fetchedAt > 0L && System.currentTimeMillis() - fetchedAt < MIRROR_TTL_MS) {
            return
        }
        val errors = mutableListOf<String>()
        for (url in mirrorConfigCandidates()) {
            try {
                val body = requestText(url, MAX_METADATA_BYTES, accept = "application/json")
                // 清单里也带 mirrors，优先当镜像源
                val mirrors = if (body.contains("\"downloadUrl\"")) {
                    parseManifest(body).mirrorsHint
                } else {
                    parseMirrorsJson(body)
                }
                if (mirrors.isNotEmpty()) {
                    cacheMirrorsFromList(context, mirrors)
                    return
                }
            } catch (error: Exception) {
                errors += "${channelLabel(url)}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        if (errors.isNotEmpty() && prefs.getString(KEY_MIRRORS, null).isNullOrBlank()) {
            cacheMirrorsFromList(context, BOOTSTRAP_MIRRORS)
        }
    }

    private fun mirrorConfigCandidates(): List<String> {
        // 先官方，失败再自建 / 镜像前缀
        val urls = linkedSetOf(
            OFFICIAL_MANIFEST_JSON,
            OFFICIAL_MIRRORS_JSON,
            SELFHOST_MANIFEST_JSON,
            SELFHOST_MIRRORS_JSON
        )
        for (prefix in BOOTSTRAP_MIRRORS) {
            urls += prefix + OFFICIAL_MANIFEST_JSON
            urls += prefix + OFFICIAL_MIRRORS_JSON
        }
        return urls.toList()
    }

    private fun activeMirrors(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_MIRRORS, null)
        val fromCache = cached?.let { runCatching { parseMirrorsJson(it) }.getOrNull() }.orEmpty()
        val merged = linkedSetOf<String>()
        merged.addAll(fromCache)
        merged.addAll(BOOTSTRAP_MIRRORS)
        return merged.map { normalizePrefix(it) }.filter { it.startsWith("https://") }.distinct()
    }

    private fun apiCandidates(context: Context): List<String> {
        val urls = linkedSetOf(OFFICIAL_API)
        for (prefix in activeMirrors(context)) {
            urls += prefix + OFFICIAL_API
        }
        return urls.toList()
    }

    private fun downloadCandidates(context: Context, officialUrl: String): List<String> {
        require(officialUrl.startsWith("https://")) { "下载地址必须是 HTTPS" }
        // 始终先直连官方地址，失败后再拼镜像前缀
        val urls = linkedSetOf(officialUrl)
        if (isGitHubHosted(officialUrl)) {
            for (prefix in activeMirrors(context)) {
                val mirrored = prefix + officialUrl
                if (mirrored != officialUrl) urls += mirrored
            }
        }
        return urls.toList()
    }

    private fun parseMirrorsJson(raw: String): List<String> {
        val text = raw.trim()
        val mirrors = mutableListOf<String>()
        when {
            text.startsWith("{") -> {
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("mirrors") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) mirrors += normalizePrefix(item)
                }
            }
            text.startsWith("[") -> {
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) mirrors += normalizePrefix(item)
                }
            }
            else -> error("镜像配置不是 JSON")
        }
        return mirrors.distinct()
    }

    private fun normalizePrefix(prefix: String): String {
        val trimmed = prefix.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
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

    private fun isGitHubHosted(url: String): Boolean {
        return try {
            val host = URL(url).host.lowercase()
            host == "github.com" ||
                host.endsWith(".github.com") ||
                host.endsWith(".githubusercontent.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun channelLabel(url: String): String {
        return when {
            url.contains("update-manifest.json") -> "清单"
            url.contains("update-mirrors.json") -> "配置"
            url.contains("raw.githubusercontent.com") -> "配置"
            url.contains("api.github.com") ||
                (url.contains("github.com") && !url.contains("git.220324")) ||
                url.contains("githubusercontent.com") -> "官方"
            url.startsWith("https://") && !url.contains("github.com") -> "镜像"
            else -> "线路"
        }
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
