package com.github.nrfr.manager

import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.telephony.CarrierConfigManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

private const val ARG_SUB_ID = "sub_id"
private const val ARG_CONFIG = "config"
private const val ARG_RESET = "reset"

class PrivilegedCarrierConfigInstrumentation : Instrumentation() {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)

        val subId = readIntArg(arguments, ARG_SUB_ID, "subId", default = -1)
        val bundle = buildOverrideBundle(arguments)
        if (subId < 0) {
            finish(1, Bundle().apply { putString("error", "无效的 subId") })
            return
        }

        Thread {
            var lastError: Throwable? = null
            try {
                waitForTargetContext()
                waitForShizukuBinder()
                withShellPermissionIdentity {
                    try {
                        invokeOverrideConfig(subId, bundle, persistent = true)
                    } catch (error: Throwable) {
                        lastError = error
                        invokeOverrideConfig(subId, bundle, persistent = false)
                        lastError = null
                    }
                }
            } catch (error: Throwable) {
                lastError = error
            } finally {
                val result = Bundle().apply {
                    lastError?.unwrapCarrierConfigCause()?.let { cause ->
                        putString("error", cause.message ?: cause.javaClass.simpleName)
                    }
                }
                finish(if (lastError == null) 0 else 1, result)
            }
        }.start()
    }

    private fun withShellPermissionIdentity(block: () -> Unit) {
        val activityBinder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.ACTIVITY_SERVICE) as IBinder
        val activityManager = Class.forName("android.app.IActivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(activityBinder))

        try {
            try {
                activityManager.javaClass
                    .getMethod(
                        "startDelegateShellPermissionIdentity",
                        Int::class.javaPrimitiveType,
                        Array<String>::class.java
                    )
                    .invoke(activityManager, Process.myUid(), null)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
            block()
        } finally {
            runCatching {
                activityManager.javaClass
                    .getMethod("stopDelegateShellPermissionIdentity")
                    .invoke(activityManager)
            }
        }
    }

    private fun waitForShizukuBinder(timeoutMs: Long = 8000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (Shizuku.pingBinder()) return
            Thread.sleep(50)
        }
        throw IllegalStateException("Shizuku 未就绪")
    }

    private fun waitForTargetContext(timeoutMs: Long = 8000L): Context {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            targetContext?.let { return it }
            Thread.sleep(50)
        }
        throw IllegalStateException("targetContext 等待超时")
    }

    private fun buildOverrideBundle(arguments: Bundle): PersistableBundle? {
        if (readBooleanArg(arguments, ARG_RESET, default = false)) {
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments.getParcelable(ARG_CONFIG, PersistableBundle::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            arguments.getParcelable<PersistableBundle>(ARG_CONFIG)?.let { return it }
        }

        return PersistableBundle().apply {
            arguments.getString("countryCode")?.takeIf { it.length == 2 }?.let {
                putString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, it.lowercase())
            }
            arguments.getString("carrierName")?.takeIf { it.isNotBlank() }?.let {
                putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
                putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, it)
            }
        }.takeIf { !it.isEmpty }
    }

    private fun invokeOverrideConfig(
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        val appContext = targetContext ?: throw IllegalStateException("targetContext 未就绪")
        val manager = appContext.getSystemService(CarrierConfigManager::class.java)
            ?: throw IllegalStateException("无法获取 CarrierConfigManager")
        val method = manager.javaClass.getMethod(
            "overrideConfig",
            Int::class.javaPrimitiveType,
            PersistableBundle::class.java,
            Boolean::class.javaPrimitiveType
        )
        try {
            method.invoke(manager, subId, bundle, persistent)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
        if (bundle == null) {
            SubscriptionHiddenApi.syncAfterRestoreBlocking(appContext, subId)
        } else {
            SubscriptionHiddenApi.syncAfterOverrideBlocking(appContext, subId, bundle)
        }
    }
}

private fun readIntArg(arguments: Bundle, vararg keys: String, default: Int): Int {
    keys.forEach { key ->
        if (!arguments.containsKey(key)) return@forEach
        arguments.getString(key)?.toIntOrNull()?.let { return it }
        runCatching { return arguments.getInt(key) }
    }
    return default
}

private fun readBooleanArg(arguments: Bundle, key: String, default: Boolean): Boolean {
    if (!arguments.containsKey(key)) return default
    arguments.getString(key)?.toBooleanStrictOrNull()?.let { return it }
    runCatching { return arguments.getBoolean(key) }
    return default
}
