package com.github.nrfr.manager

// 单 APK：Shizuku 直连 CarrierConfig，受限时委托 shell 权限回退；不用 am instrument shell（会 force-stop 闪退）。

import android.content.Context
import android.os.PersistableBundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.CarrierConfigManager as AndroidCarrierConfigManager
import com.github.nrfr.model.SimCardInfo
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.io.InputStream

object CarrierConfigManager {
    private val PHONE_ID_PATTERN = Regex("""^Phone Id = (\d+)""")

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    fun getSimCards(context: Context): List<SimCardInfo> {
        val isubSnapshot = getIsubSnapshotByDumpsys()
        val configsByPhoneId = getCurrentConfigsByDumpsys()
        val simCards = mutableListOf<SimCardInfo>()

        for (slotIndex in 0..1) {
            val slot = slotIndex + 1
            val subId = isubSnapshot?.slotToSubId?.get(slotIndex)
                ?: getSubIdForSlotViaApi(context, slotIndex)
                ?: continue
            val config = configsByPhoneId[slotIndex]
                ?: getCurrentConfigByLoader(context, subId)
                ?: getCurrentConfigByPublicApi(context, subId)
            val carrierName = getCarrierNameBySubId(
                context,
                subId,
                slotIndex,
                config,
                isubSnapshot
            )
            simCards.add(SimCardInfo(slot, subId, carrierName, config))
        }

        return simCards
    }

    private data class IsubSnapshot(
        val slotToSubId: Map<Int, Int>,
        val subIdToCarrierName: Map<Int, String>
    )

    private val LOGICAL_SLOT_PATTERN = Regex("""Logical SIM slot (\d+): subId=(\d+)""")

    fun setCarrierConfig(
        context: Context,
        subId: Int,
        countryCode: String?,
        carrierName: String? = null
    ): CarrierConfigOperationResult {
        val bundle = PersistableBundle()

        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                AndroidCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(AndroidCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(AndroidCarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
            // 与 AOSP CTS 一致，提高 SPN 覆盖成功率（尤其副卡）
            bundle.putBoolean(AndroidCarrierConfigManager.KEY_FORCE_HOME_NETWORK_BOOL, true)
        }

        overrideCarrierConfig(context, subId, bundle)
        return CarrierConfigOperationResult()
    }

    fun resetCarrierConfig(context: Context, subId: Int): CarrierConfigOperationResult {
        overrideCarrierConfig(context, subId, null)
        return CarrierConfigOperationResult()
    }

    /**
     * @return true 表示走了异步 instrumentation 回退（调用方会在返回前等待配置生效）
     */
    private fun overrideCarrierConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?
    ): Boolean {
        var usedInstrumentation = false
        try {
            TelephonyHiddenApi.overrideConfig(subId, bundle, persistent = true)
            syncSubscriptionAfterOverride(context, subId, bundle)
            waitUntilConfigMatches(context, subId, bundle)
            return false
        } catch (error: SecurityException) {
            if (!error.message.orEmpty().contains("cannot be invoked by shell", ignoreCase = true)) {
                throw error.unwrapCarrierConfigCause()
            }
        } catch (error: Throwable) {
            val message = error.unwrapCarrierConfigCause().message.orEmpty()
            if (!message.contains("cannot be invoked by shell", ignoreCase = true)) {
                throw error.unwrapCarrierConfigCause()
            }
        }

        val delegatedError = runCatching {
            DelegatedCarrierConfigOverride.overrideConfigWithFallback(
                context,
                subId,
                bundle,
                persistent = true
            )
        }
        if (delegatedError.isSuccess) {
            syncSubscriptionAfterOverride(context, subId, bundle)
            waitUntilConfigMatches(context, subId, bundle)
            return false
        }

        if (!PrivilegedCarrierConfigRunner.overrideConfig(context, subId, bundle)) {
            throw IllegalStateException("无法启动后台服务应用配置")
        }
        usedInstrumentation = true
        // instrumentation 进程会立即退出，主进程延迟再同步一次 SPN
        if (bundle == null) {
            SubscriptionHiddenApi.syncAfterRestore(context, subId)
        } else {
            SubscriptionHiddenApi.syncAfterOverride(context, subId, bundle)
        }
        if (!waitUntilConfigMatches(context, subId, bundle, timeoutMs = 20_000L)) {
            throw IllegalStateException("配置未及时生效，请稍后下拉刷新")
        }
        return usedInstrumentation
    }

    private fun waitUntilConfigMatches(
        context: Context,
        subId: Int,
        expectedBundle: PersistableBundle?,
        timeoutMs: Long = 5_000L
    ): Boolean {
        val phoneId = getPhoneIdForSubId(context, subId) ?: return true
        val expected = expectedBundle?.let { parseConfigBundle(it) } ?: emptyMap()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (configMatches(readOverrideConfigForPhoneId(context, phoneId), expected)) {
                return true
            }
            Thread.sleep(250)
        }
        return configMatches(readOverrideConfigForPhoneId(context, phoneId), expected)
    }

    private fun readOverrideConfigForPhoneId(context: Context, phoneId: Int): Map<String, String> {
        getCurrentConfigsByDumpsys()[phoneId]?.let { return it }

        val subId = getIsubSnapshotByDumpsys()?.slotToSubId?.get(phoneId)
            ?: getSubIdForSlotViaApi(context, phoneId)
            ?: return emptyMap()
        return getCurrentConfigByLoader(context, subId)
            ?: getCurrentConfigByPublicApi(context, subId)
    }

    private fun configMatches(current: Map<String, String>, expected: Map<String, String>): Boolean {
        if (expected.isEmpty()) {
            return current.isEmpty()
        }
        return expected.all { (key, value) -> current[key] == value }
    }

    private fun getPhoneIdForSubId(context: Context, subId: Int): Int? {
        getIsubSnapshotByDumpsys()?.slotToSubId?.entries?.firstOrNull { it.value == subId }?.key?.let {
            return it
        }
        for (slotIndex in 0..1) {
            if (getSubIdForSlotViaApi(context, slotIndex) == subId) {
                return slotIndex
            }
        }
        return null
    }

    private fun syncSubscriptionAfterOverride(context: Context, subId: Int, bundle: PersistableBundle?) {
        if (bundle == null) {
            SubscriptionHiddenApi.syncAfterRestore(context, subId)
        } else {
            SubscriptionHiddenApi.syncAfterOverride(context, subId, bundle)
        }
    }

    private fun ensureShizukuReady() {
        if (!ShizukuHelper.hasPermission()) {
            throw IllegalStateException("Shizuku 未启动或未授权")
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun getIsubSnapshotByDumpsys(): IsubSnapshot? {
        return try {
            if (!ShizukuHelper.hasPermission()) {
                return null
            }
            parseDumpsysIsub(runShellCommand("dumpsys isub"))
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDumpsysIsub(output: String): IsubSnapshot {
        val slotToSubId = mutableMapOf<Int, Int>()
        LOGICAL_SLOT_PATTERN.findAll(output).forEach { match ->
            val slot = match.groupValues[1].toIntOrNull() ?: return@forEach
            val subId = match.groupValues[2].toIntOrNull() ?: return@forEach
            slotToSubId[slot] = subId
        }

        val subIdToCarrierName = mutableMapOf<Int, String>()
        output.lineSequence().forEach { line ->
            if (!line.contains("SubscriptionInfoInternal: id=")) {
                return@forEach
            }
            val subId = Regex("""\bid=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@forEach
            val carrierName = Regex("""\bcarrierName=([^\s]+)""")
                .find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val displayName = Regex("""\bdisplayName=([^\s]+)""")
                .find(line)?.groupValues?.get(1)
                ?.replace(Regex("""\d+$"""), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val mcc = Regex("""\bmcc=(\d+)""").find(line)?.groupValues?.get(1)
            val mnc = Regex("""\bmnc=(\d+)""").find(line)?.groupValues?.get(1)
            val name = displayName
                ?: carrierName
                ?: resolveCarrierNameByMccMnc(
                    if (mcc != null && mnc != null) mcc + mnc.padStart(2, '0') else null
                )
            if (!name.isNullOrBlank()) {
                subIdToCarrierName[subId] = name
            }
            val slotIndex = Regex("""\bsimSlotIndex=(\d+)""")
                .find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (slotIndex != null && slotIndex >= 0) {
                slotToSubId.putIfAbsent(slotIndex, subId)
            }
        }

        return IsubSnapshot(slotToSubId, subIdToCarrierName)
    }

    private fun getSubIdForSlotViaApi(context: Context, slotIndex: Int): Int? {
        val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return null
        return runCatching {
            manager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)?.subscriptionId
                ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        }.getOrNull() ?: runCatching {
            val getSubId = SubscriptionManager::class.java.getDeclaredMethod(
                "getSubId",
                Int::class.javaPrimitiveType
            )
            val subIds = getSubId.invoke(null, slotIndex) as? IntArray ?: return null
            subIds.firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        }.getOrNull()
    }

    private fun getCurrentConfigByLoader(context: Context, subId: Int): Map<String, String>? {
        return try {
            val config = TelephonyHiddenApi.getConfigForSubId(context.packageName, subId)
                ?: return null
            parseConfigBundle(config)
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentConfigByPublicApi(context: Context, subId: Int): Map<String, String> {
        return try {
            val manager = context.getSystemService(AndroidCarrierConfigManager::class.java)
                ?: return emptyMap()
            val config = manager.getConfigForSubId(subId) ?: return emptyMap()
            parseConfigBundle(config)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun parseConfigBundle(config: PersistableBundle): Map<String, String> {
        val result = mutableMapOf<String, String>()

        config.getString(AndroidCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
            result["国家码"] = it
        }

        if (config.getBoolean(AndroidCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
            config.getString(AndroidCarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                result["运营商名称"] = it
            }
        }

        return result
    }

    private fun getCurrentConfigsByDumpsys(): Map<Int, Map<String, String>> {
        return try {
            if (!ShizukuHelper.hasPermission()) {
                return emptyMap()
            }
            parseDumpsysCarrierConfig(runShellCommand("dumpsys carrier_config"))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun parseDumpsysCarrierConfig(output: String): Map<Int, Map<String, String>> {
        val result = mutableMapOf<Int, Map<String, String>>()
        var currentPhoneId: Int? = null
        var readingOverrideConfig = false
        val rawValues = mutableMapOf<String, String>()

        fun flushCurrentConfig() {
            val phoneId = currentPhoneId ?: return
            val config = mutableMapOf<String, String>()
            rawValues["sim_country_iso_override_string"]?.takeIf { it.isNotBlank() }?.let {
                config["国家码"] = it
            }
            if (rawValues["carrier_name_override_bool"] == "true") {
                rawValues["carrier_name_string"]?.takeIf { it.isNotBlank() }?.let {
                    config["运营商名称"] = it
                }
            }
            result[phoneId] = config
            rawValues.clear()
        }

        output.lineSequence().forEach { line ->
            PHONE_ID_PATTERN.find(line)?.let { match ->
                flushCurrentConfig()
                currentPhoneId = match.groupValues[1].toIntOrNull()
                readingOverrideConfig = false
                return@forEach
            }

            val trimmed = line.trim()
            if (trimmed.startsWith("mOverrideConfigs")) {
                readingOverrideConfig = !trimmed.endsWith("null")
                if (!readingOverrideConfig) {
                    rawValues.clear()
                }
                return@forEach
            }

            if (!readingOverrideConfig) {
                return@forEach
            }

            if (trimmed.startsWith("m") && trimmed.endsWith(":")) {
                readingOverrideConfig = false
                return@forEach
            }

            val separatorIndex = trimmed.indexOf(" = ")
            if (separatorIndex > 0) {
                rawValues[trimmed.substring(0, separatorIndex)] =
                    trimmed.substring(separatorIndex + 3)
            }
        }

        flushCurrentConfig()
        return result
    }

    private fun getCarrierNameBySubId(
        context: Context,
        subId: Int,
        slotIndex: Int,
        currentConfig: Map<String, String>,
        isubSnapshot: IsubSnapshot?
    ): String {
        currentConfig["运营商名称"]?.takeIf { it.isNotBlank() }?.let { return it }

        isubSnapshot?.subIdToCarrierName?.get(subId)?.takeIf { it.isNotBlank() }?.let { return it }

        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        subscriptionManager?.let { manager ->
            val subscriptionInfo = runCatching {
                manager.getActiveSubscriptionInfo(subId)
            }.getOrNull() ?: runCatching {
                manager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
            }.getOrNull()
            subscriptionInfo?.let { info ->
                resolveCarrierNameByMccMnc(
                    String.format("%03d%02d", info.mcc, info.mnc)
                )?.let { return it }

                info.displayName?.toString()?.trim()
                    ?.replace(Regex("""\d+$"""), "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""

        return try {
            val subTelephony = telephonyManager.createForSubscriptionId(subId)
            subTelephony.simOperatorName.takeIf { it.isNotBlank() }
                ?: resolveCarrierNameByMccMnc(subTelephony.simOperator)
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun resolveCarrierNameByMccMnc(operatorNumeric: String?): String? {
        if (operatorNumeric.isNullOrBlank()) {
            return null
        }

        return when {
            operatorNumeric.startsWith("46015") -> "中国广电"
            operatorNumeric.startsWith("46003") ||
                operatorNumeric.startsWith("46005") ||
                operatorNumeric.startsWith("46011") -> "中国电信"
            operatorNumeric.startsWith("46001") ||
                operatorNumeric.startsWith("46006") ||
                operatorNumeric.startsWith("46009") -> "中国联通"
            operatorNumeric.startsWith("46000") ||
                operatorNumeric.startsWith("46002") ||
                operatorNumeric.startsWith("46007") ||
                operatorNumeric.startsWith("46008") -> "中国移动"
            else -> null
        }
    }

    private fun runShellCommand(command: String): String {
        val shellProcess = newShizukuProcess(arrayOf("sh", "-c", command))
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = readAsync(shellProcess.inputStream, stdout)
        val stderrThread = readAsync(shellProcess.errorStream, stderr)
        val exitCode = shellProcess.waitFor()
        stdoutThread.join()
        stderrThread.join()

        if (exitCode != 0) {
            throw IllegalStateException(
                listOf(stdout.toString(), stderr.toString())
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .ifBlank { "执行 shell 命令失败，退出码: $exitCode" }
            )
        }

        return stdout.toString()
    }

    private fun newShizukuProcess(command: Array<String>): Process {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcess.isAccessible = true
        return newProcess.invoke(null, command, null, null) as Process
    }

    private fun readAsync(inputStream: InputStream, output: StringBuilder): Thread {
        return Thread {
            inputStream.bufferedReader().use { reader ->
                output.append(reader.readText())
            }
        }.apply { start() }
    }

    data class CarrierConfigOperationResult(
        val warnings: List<String> = emptyList()
    )
}

internal fun Throwable.toSimCardsUserMessage(): String {
    var current: Throwable = this
    while (current.cause != null) {
        current = current.cause!!
    }
    val message = current.message.orEmpty()
    if (current is SecurityException) {
        return "权限不足，请检查 Shizuku 是否已授权"
    }
    return message.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
        ?: "读取 SIM 配置失败"
}
