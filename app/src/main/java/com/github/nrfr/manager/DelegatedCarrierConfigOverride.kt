package com.github.nrfr.manager

import android.content.Context
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.telephony.CarrierConfigManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

/**
 * 通过 Shizuku 委托 shell 权限后，在主进程内调用 [CarrierConfigManager.overrideConfig]。
 * 避免自指向 instrumentation 导致系统 force-stop 应用（闪退）。
 */
object DelegatedCarrierConfigOverride {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    fun overrideConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        ensureShizukuReady()
        withShellPermissionIdentity {
            invokeOverrideConfig(context, subId, bundle, persistent)
        }
    }

    internal fun runWithShellPermission(block: () -> Unit) {
        ensureShizukuReady()
        withShellPermissionIdentity(block)
    }

    fun overrideConfigWithFallback(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        // Android 16 / Oppo 上 persistent=false 成功率更高
        try {
            overrideConfig(context, subId, bundle, persistent = false)
        } catch (sessionError: Throwable) {
            if (!persistent) {
                throw sessionError
            }
            overrideConfig(context, subId, bundle, persistent = true)
        }
    }

    private fun ensureShizukuReady() {
        if (!ShizukuHelper.hasPermission()) {
            throw IllegalStateException("Shizuku 未启动或未授权")
        }
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

    private fun invokeOverrideConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
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
    }
}
