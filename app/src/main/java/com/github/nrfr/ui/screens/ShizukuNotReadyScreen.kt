package com.github.nrfr.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.nrfr.manager.ShizukuHelper
import com.github.nrfr.ui.theme.NrfrAdaptive
import com.github.nrfr.ui.theme.NrfrPill

private const val SHIZUKU_RELEASES = "https://github.com/lmh-codes/shizuku/releases"

enum class ShizukuBlockReason {
    NOT_INSTALLED,
    SERVICE_NOT_RUNNING,
    PERMISSION_NOT_GRANTED
}

@Composable
fun ShizukuNotReadyScreen(
    reason: ShizukuBlockReason,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val title = when (reason) {
        ShizukuBlockReason.NOT_INSTALLED -> "未安装 Shizuku"
        ShizukuBlockReason.SERVICE_NOT_RUNNING -> "Shizuku 未运行"
        ShizukuBlockReason.PERMISSION_NOT_GRANTED -> "需要 Shizuku 授权"
    }
    val detail = when (reason) {
        ShizukuBlockReason.NOT_INSTALLED -> "本应用依赖 Shizuku。请先安装后再返回。"
        ShizukuBlockReason.SERVICE_NOT_RUNNING -> "已检测到本机 Shizuku，请先启动或完成配对，再返回本应用。"
        ShizukuBlockReason.PERMISSION_NOT_GRANTED -> "服务已运行。点击下方按钮，在弹窗中允许本应用即可。"
    }
    val actionLabel = when (reason) {
        ShizukuBlockReason.NOT_INSTALLED -> "去下载"
        ShizukuBlockReason.SERVICE_NOT_RUNNING -> "打开 Shizuku"
        ShizukuBlockReason.PERMISSION_NOT_GRANTED -> "请求授权"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    when (reason) {
                        ShizukuBlockReason.NOT_INSTALLED -> {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_RELEASES))
                            )
                        }
                        ShizukuBlockReason.SERVICE_NOT_RUNNING -> {
                            if (!ShizukuHelper.openApp(context)) {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_RELEASES))
                                )
                            }
                        }
                        ShizukuBlockReason.PERMISSION_NOT_GRANTED -> onRequestPermission()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = NrfrPill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NrfrAdaptive.link(),
                    contentColor = NrfrAdaptive.onLink()
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun resolveShizukuBlockReason(context: Context): ShizukuBlockReason {
    return when {
        ShizukuHelper.isBinderAvailable() -> ShizukuBlockReason.PERMISSION_NOT_GRANTED
        ShizukuHelper.isInstalled(context) -> ShizukuBlockReason.SERVICE_NOT_RUNNING
        else -> ShizukuBlockReason.NOT_INSTALLED
    }
}
