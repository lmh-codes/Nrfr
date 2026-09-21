package com.github.nrfr

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.nrfr.manager.OperationState
import com.github.nrfr.manager.ShizukuHelper
import com.github.nrfr.ui.screens.AboutScreen
import com.github.nrfr.ui.screens.MainScreen
import com.github.nrfr.ui.screens.ShizukuBlockReason
import com.github.nrfr.ui.screens.ShizukuNotReadyScreen
import com.github.nrfr.ui.screens.resolveShizukuBlockReason
import com.github.nrfr.ui.theme.NrfrTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var isShizukuReady by mutableStateOf(false)
    private var shizukuBlockReason by mutableStateOf(ShizukuBlockReason.SERVICE_NOT_RUNNING)
    private var showAbout by mutableStateOf(false)
    private var autoPermissionRequestAttempted = false

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread {
                updateShizukuStatus()
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "未授予 Shizuku 权限", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            updateShizukuStatus()
            requestShizukuPermissionAutomatically()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { updateShizukuStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OperationState.reset()

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListener(binderListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }

        updateShizukuStatus()
        requestShizukuPermissionAutomatically()

        setContent {
            NrfrTheme {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else if (isShizukuReady) {
                    MainScreen(onShowAbout = { showAbout = true })
                } else {
                    ShizukuNotReadyScreen(
                        reason = shizukuBlockReason,
                        onRequestPermission = { requestShizukuPermission() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun requestShizukuPermission() {
        if (!ShizukuHelper.isBinderAvailable()) {
            Toast.makeText(this, "请先启动 Shizuku 服务", Toast.LENGTH_LONG).show()
            updateShizukuStatus()
            return
        }
        ShizukuHelper.requestPermissionIfNeeded()
        updateShizukuStatus()
    }

    private fun requestShizukuPermissionAutomatically() {
        if (autoPermissionRequestAttempted || ShizukuHelper.hasPermission()) return
        if (ShizukuHelper.isBinderAvailable()) {
            autoPermissionRequestAttempted = true
            ShizukuHelper.requestPermissionIfNeeded()
        }
    }

    private fun updateShizukuStatus() {
        isShizukuReady = ShizukuHelper.hasPermission()
        if (!isShizukuReady) {
            shizukuBlockReason = resolveShizukuBlockReason()
        }
    }

    override fun onDestroy() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
        super.onDestroy()
    }
}
