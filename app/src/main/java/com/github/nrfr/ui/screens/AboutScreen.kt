package com.github.nrfr.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.nrfr.R

private const val PROJECT_REPO = "https://github.com/lmh-codes/Nrfr"
private const val UPSTREAM_REPO = "https://github.com/Ackites/Nrfr"
private const val UPSTREAM_AUTHOR = "https://x.com/intent/follow?screen_name=actkites"
private const val UPSTREAM_LICENSE = "https://github.com/Ackites/Nrfr/blob/master/LICENSE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            modifier = Modifier.size(48.dp),
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("关于")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .weight(1f, fill = false),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "功能介绍",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "• Android 16+ 适配版 SIM 运营商配置覆盖工具\n" +
                                "• 单 APK 安装，开箱即用\n" +
                                "• 基于 Shizuku 直连系统 Telephony 服务，无需 Root\n" +
                                "• 支持查看 SIM1 / SIM2 当前配置和覆盖状态\n" +
                                "• 支持双卡设备，可分别配置国家码与运营商名称\n" +
                                "• 配置可保存，也可一键还原当前覆盖",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "开源信息",
                    style = MaterialTheme.typography.titleMedium
                )
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "本项目基于 Apache-2.0 许可证发布。",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "项目仓库: lmh-codes/Nrfr",
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(PROJECT_REPO) }
                        )
                        Text(
                            "上游项目: Ackites/Nrfr",
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(UPSTREAM_REPO) }
                        )
                        Text(
                            "原作者: Ackites (@actkites)",
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(UPSTREAM_AUTHOR) }
                        )
                        Text(
                            "开源许可证 (Apache-2.0)",
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(UPSTREAM_LICENSE) }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "© 2026 lmh-codes · Ackites/Nrfr (Apache-2.0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
