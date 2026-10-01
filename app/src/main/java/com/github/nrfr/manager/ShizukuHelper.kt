package com.github.nrfr.manager

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    const val PACKAGE_NAME = "moe.shizuku.privileged.api"

    fun isBinderAvailable(): Boolean {
        return runCatching { Shizuku.getBinder() != null }.getOrDefault(false)
    }

    fun isInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
    }

    fun openApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) ?: return false
        context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun hasPermission(): Boolean {
        return runCatching {
            isBinderAvailable() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermissionIfNeeded(requestCode: Int = 0): Boolean {
        return runCatching {
            if (!isBinderAvailable()) {
                return false
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
            true
        }.getOrDefault(false)
    }
}
