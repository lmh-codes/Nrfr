package com.github.nrfr.manager

import android.os.IBinder
import android.os.PersistableBundle
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

internal object TelephonyHiddenApi {
    private const val LOADER_STUB = "com.android.internal.telephony.ICarrierConfigLoader\$Stub"

    fun getConfigForSubId(callingPackage: String, subId: Int): PersistableBundle? {
        val loader = getCarrierConfigLoader() ?: return null
        val method = loader.javaClass.getMethod(
            "getConfigForSubId",
            Int::class.javaPrimitiveType,
            String::class.java
        )
        return method.invoke(loader, subId, callingPackage) as? PersistableBundle
    }

    fun overrideConfig(subId: Int, bundle: PersistableBundle?, persistent: Boolean) {
        val loader = getCarrierConfigLoader()
            ?: throw IllegalStateException("无法获取 CarrierConfigLoader 服务")
        val method = loader.javaClass.getMethod(
            "overrideConfig",
            Int::class.javaPrimitiveType,
            PersistableBundle::class.java,
            Boolean::class.javaPrimitiveType
        )
        try {
            method.invoke(loader, subId, bundle, persistent)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun getCarrierConfigLoader(): Any? {
        return getCarrierConfigLoaderOrNull()
    }

    internal fun getCarrierConfigLoaderOrNull(): Any? {
        return runCatching {
            val initializer = Class.forName("android.telephony.TelephonyFrameworkInitializer")
            val serviceManager = initializer.getMethod("getTelephonyServiceManager").invoke(null)
            val registerer = serviceManager.javaClass
                .getMethod("getCarrierConfigServiceRegisterer")
                .invoke(serviceManager)
            val binder = registerer.javaClass.getMethod("get").invoke(registerer) as IBinder
            val stubClass = Class.forName(LOADER_STUB)
            stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
        }.getOrNull()
    }
}

internal fun Throwable.unwrapCarrierConfigCause(): Throwable {
    var current: Throwable = this
    while (current is InvocationTargetException && current.cause != null) {
        current = current.cause!!
    }
    return current
}
