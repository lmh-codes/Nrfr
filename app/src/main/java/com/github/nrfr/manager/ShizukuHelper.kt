package com.github.nrfr.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    private val SHIZUKU_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager"
    )

    fun isBinderAvailable(): Boolean {
        return runCatching { Shizuku.getBinder() != null }.getOrDefault(false)
    }

    fun hasPermission(): Boolean {
        return runCatching {
            isBinderAvailable() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun isInstalled(context: Context): Boolean {
        return SHIZUKU_PACKAGES.any { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }

    fun openApp(context: Context): Boolean {
        for (pkg in SHIZUKU_PACKAGES) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching {
                context.startActivity(launch)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return false
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
