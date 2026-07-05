package com.github.nrfr.manager

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    fun isBinderAvailable(): Boolean {
        return runCatching { Shizuku.getBinder() != null }.getOrDefault(false)
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
