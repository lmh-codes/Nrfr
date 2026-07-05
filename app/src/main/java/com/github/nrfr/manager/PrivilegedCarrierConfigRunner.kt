package com.github.nrfr.manager

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

private const val ARG_SUB_ID = "sub_id"
private const val ARG_CONFIG = "config"
private const val ARG_RESET = "reset"

object PrivilegedCarrierConfigRunner {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    /** 启动 instrumentation 并在返回前由调用方等待配置生效。 */
    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?): Boolean {
        return runCatching {
            startInstrumentationInternal(context, subId, bundle)
            true
        }.getOrDefault(false)
    }

    private fun startInstrumentationInternal(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?
    ) {
        val args = Bundle().apply {
            putInt(ARG_SUB_ID, subId)
            putBoolean(ARG_RESET, bundle == null)
            if (bundle != null) {
                putParcelable(ARG_CONFIG, bundle)
            }
        }

        val activityBinder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.ACTIVITY_SERVICE) as IBinder

        val activityManager = Class.forName("android.app.IActivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(activityBinder))

        val component = ComponentName(context, PrivilegedCarrierConfigInstrumentation::class.java)
        val flags = ActivityManager::class.java.getField("INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS").getInt(null) or
            ActivityManager::class.java.getField("INSTR_FLAG_NO_RESTART").getInt(null)

        val watcherClass = Class.forName("android.app.IInstrumentationWatcher")
        val connectionClass = Class.forName("android.app.IUiAutomationConnection")
        val uiAutomationConnection = Class.forName("android.app.UiAutomationConnection")
            .getConstructor()
            .newInstance()

        val method = activityManager.javaClass.getMethod(
            "startInstrumentation",
            ComponentName::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Bundle::class.java,
            watcherClass,
            connectionClass,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        try {
            method.invoke(
                activityManager,
                component,
                null,
                flags,
                args,
                null,
                uiAutomationConnection,
                0,
                null
            )
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
