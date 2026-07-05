package com.github.nrfr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.nrfr.manager.ShizukuHelper

enum class ShizukuBlockReason {
    SERVICE_NOT_RUNNING,
    PERMISSION_NOT_GRANTED
}

@Composable
fun ShizukuNotReadyScreen(
    reason: ShizukuBlockReason,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (reason) {
            ShizukuBlockReason.SERVICE_NOT_RUNNING -> {
                Text(
                    text = "Shizuku 未运行",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先打开 Shizuku 应用并启动服务，然后返回本应用",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            ShizukuBlockReason.PERMISSION_NOT_GRANTED -> {
                Text(
                    text = "需要授权 Nrfr 使用 Shizuku",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shizuku 已运行，但尚未授权本应用。点击下方按钮，在弹窗中允许。",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRequestPermission) {
                    Text("请求 Shizuku 授权")
                }
            }
        }
    }
}

fun resolveShizukuBlockReason(): ShizukuBlockReason {
    return if (!ShizukuHelper.isBinderAvailable()) {
        ShizukuBlockReason.SERVICE_NOT_RUNNING
    } else {
        ShizukuBlockReason.PERMISSION_NOT_GRANTED
    }
}
