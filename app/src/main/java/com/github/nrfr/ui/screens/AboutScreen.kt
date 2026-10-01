package com.github.nrfr.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nrfr.R
import com.github.nrfr.manager.AppUpdateManager
import com.github.nrfr.ui.theme.NrfrAdaptive
import com.github.nrfr.ui.theme.NrfrCardShape
import com.github.nrfr.ui.theme.NrfrPill
import com.github.nrfr.ui.theme.clickableNoIndication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROJECT_REPO = "https://github.com/lmh-codes/Nrfr"
private const val SHIZUKU_REPO = "https://github.com/lmh-codes/shizuku"
private const val UPSTREAM_REPO = "https://github.com/Ackites/Nrfr"
private const val UPSTREAM_LICENSE = "https://github.com/Ackites/Nrfr/blob/master/LICENSE"

/** 在线更新弹窗：轻量状态卡，与关于页同色系 */


private enum class UpdateTone { IDLE, PROGRESS, SUCCESS, ERROR }

private data class UpdateUiState(
    val tone: UpdateTone = UpdateTone.IDLE,
    /** 状态正文 */
    val detail: String = "",
    val currentVersion: String = "",
    val latestVersion: String = "检查中",
    val progressLabel: String = "正在下载",
    /** 0..100；仅下载时有意义 */
    val progressPercent: Int = 0,
    /** 仅下载过程显示进度条 */
    val showProgress: Boolean = false,
    val actionLabel: String = "重新检查",
    /** 为 true 时主按钮触发重新检查；否则关闭 */
    val actionIsRetry: Boolean = true,
    /** 为 true 时主按钮只续装已下载 APK，不再重新下载 */
    val actionIsInstall: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateUi by remember { mutableStateOf<UpdateUiState?>(null) }

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun markInstallStarted(currentLabel: String, latestLabel: String) {
        updateUi = UpdateUiState(
            tone = UpdateTone.SUCCESS,
            detail = "下载完成，请在系统弹窗中确认安装。",
            currentVersion = currentLabel,
            latestVersion = latestLabel,
            showProgress = false,
            actionLabel = "知道了",
            actionIsRetry = false,
            actionIsInstall = false
        )
    }

    fun markNeedInstallPermission(currentLabel: String, latestLabel: String) {
        updateUi = UpdateUiState(
            tone = UpdateTone.IDLE,
            detail = "请允许「安装未知应用」，返回后点「继续安装」（已下载，无需重下）。",
            currentVersion = currentLabel,
            latestVersion = latestLabel,
            showProgress = false,
            actionLabel = "继续安装",
            actionIsRetry = false,
            actionIsInstall = true
        )
    }

    fun startUpdateCheck() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        val currentLabel = if (currentVersion.isNotBlank()) "v$currentVersion" else "—"
        updateUi = UpdateUiState(
            tone = UpdateTone.PROGRESS,
            detail = "正在读取更新清单（版本 / 校验 / 镜像）…",
            currentVersion = currentLabel,
            latestVersion = "检查中",
            showProgress = false,
            actionLabel = "检查中…",
            actionIsRetry = false,
            actionIsInstall = false
        )
        coroutineScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    AppUpdateManager.fetchLatestRelease(context, currentVersion)
                }
                val latestLabel = "v${release.version}"
                if (!AppUpdateManager.isNewerVersion(release.version, currentVersion)) {
                    updateUi = UpdateUiState(
                        tone = UpdateTone.SUCCESS,
                        detail = "已是最新版本，无需下载。",
                        currentVersion = currentLabel,
                        latestVersion = latestLabel,
                        showProgress = false,
                        actionLabel = "知道了",
                        actionIsRetry = false,
                        actionIsInstall = false
                    )
                } else {
                    updateUi = UpdateUiState(
                        tone = UpdateTone.PROGRESS,
                        detail = "发现新版本，准备下载 ${release.fileName}…",
                        currentVersion = currentLabel,
                        latestVersion = latestLabel,
                        progressLabel = "准备下载",
                        progressPercent = 0,
                        showProgress = true,
                        actionLabel = "下载中…",
                        actionIsRetry = false,
                        actionIsInstall = false
                    )
                    val mainHandler = Handler(Looper.getMainLooper())
                    val apk = withContext(Dispatchers.IO) {
                        AppUpdateManager.downloadApk(context, release) { progress ->
                            val percent = if (progress.percent >= 0) {
                                progress.percent.coerceIn(0, 100)
                            } else {
                                0
                            }
                            val detail = progress.detailText()
                            mainHandler.post {
                                updateUi = UpdateUiState(
                                    tone = UpdateTone.PROGRESS,
                                    detail = detail,
                                    currentVersion = currentLabel,
                                    latestVersion = latestLabel,
                                    progressLabel = "正在下载",
                                    progressPercent = percent,
                                    showProgress = true,
                                    actionLabel = "下载中…",
                                    actionIsRetry = false,
                                    actionIsInstall = false
                                )
                            }
                        }
                    }
                    when (AppUpdateManager.installApk(context, apk)) {
                        AppUpdateManager.InstallOutcome.STARTED ->
                            markInstallStarted(currentLabel, latestLabel)
                        AppUpdateManager.InstallOutcome.NEED_PERMISSION ->
                            markNeedInstallPermission(currentLabel, latestLabel)
                    }
                }
            } catch (error: Exception) {
                updateUi = UpdateUiState(
                    tone = UpdateTone.ERROR,
                    detail = buildString {
                        append((error.message ?: "网络或文件异常").take(160))
                        append("\n可检查网络后重试，或手动打开 Releases。")
                    },
                    currentVersion = currentLabel,
                    latestVersion = "失败",
                    showProgress = false,
                    actionLabel = "重新检查",
                    actionIsRetry = true,
                    actionIsInstall = false
                )
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun resumePendingInstall() {
        if (isCheckingUpdate) return
        val currentLabel = if (currentVersion.isNotBlank()) "v$currentVersion" else "—"
        val latestLabel = updateUi?.latestVersion?.takeIf { it.startsWith("v") } ?: "—"
        val pending = AppUpdateManager.pendingApkFile(context)
        if (pending == null) {
            startUpdateCheck()
            return
        }
        isCheckingUpdate = true
        try {
            when (AppUpdateManager.installApk(context, pending)) {
                AppUpdateManager.InstallOutcome.STARTED ->
                    markInstallStarted(currentLabel, latestLabel)
                AppUpdateManager.InstallOutcome.NEED_PERMISSION ->
                    markNeedInstallPermission(currentLabel, latestLabel)
            }
        } catch (error: Exception) {
            updateUi = UpdateUiState(
                tone = UpdateTone.ERROR,
                detail = buildString {
                    append((error.message ?: "安装失败").take(160))
                    append("\n可检查网络后重试，或手动打开 Releases。")
                },
                currentVersion = currentLabel,
                latestVersion = "失败",
                showProgress = false,
                actionLabel = "重新检查",
                actionIsRetry = true,
                actionIsInstall = false
            )
        } finally {
            isCheckingUpdate = false
        }
    }

    if (updateUi != null) {
        UpdateResultDialog(
            state = updateUi!!,
            busy = isCheckingUpdate,
            onDismiss = {
                if (!isCheckingUpdate) updateUi = null
            },
            onPrimary = {
                val state = updateUi ?: return@UpdateResultDialog
                if (isCheckingUpdate) return@UpdateResultDialog
                when {
                    state.actionIsInstall -> resumePendingInstall()
                    state.actionIsRetry -> startUpdateCheck()
                    else -> updateUi = null
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(40.dp),
                    tint = Color(0xFF8300FD)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Nrfr",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                fontSize = 22.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentVersion.isNotBlank()) {
                            Text(
                                "v$currentVersion",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NrfrAdaptive.link()
                            )
                        }
                    }
                    Text(
                        "运营商覆盖",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AboutCard(title = "功能") {
                FeatureLine("单 APK 安装，无需 Root")
                FeatureLine("经 Shizuku 读写运营商配置")
                FeatureLine("双卡分别覆盖，可一键还原")
            }

            Spacer(modifier = Modifier.height(12.dp))

            AboutCard(title = "开源") {
                LinkRow("项目仓库", "lmh-codes/Nrfr") { openUrl(PROJECT_REPO) }
                LinkRow("Shizuku", "lmh-codes/shizuku") { openUrl(SHIZUKU_REPO) }
                LinkRow("上游", "Ackites/Nrfr") { openUrl(UPSTREAM_REPO) }
                LinkRow("许可证", "Apache-2.0") { openUrl(UPSTREAM_LICENSE) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { startUpdateCheck() },
                enabled = !isCheckingUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = NrfrPill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.outline,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Text(
                    if (isCheckingUpdate) "检查中…" else "检查更新",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                "© 2026 lmh-codes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun UpdateResultDialog(
    state: UpdateUiState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPrimary: () -> Unit
) {
    val canDismiss = !busy && state.tone != UpdateTone.PROGRESS
    val title = when {
        state.actionIsInstall -> "等待安装授权"
        state.tone == UpdateTone.ERROR -> "更新失败"
        state.tone == UpdateTone.SUCCESS -> if (state.detail.contains("最新")) "已是最新" else "下载完成"
        state.tone == UpdateTone.PROGRESS -> if (state.showProgress) "正在下载" else "正在检查"
        else -> "软件更新"
    }
    val accent = when {
        state.actionIsInstall -> NrfrAdaptive.link()
        state.tone == UpdateTone.ERROR -> NrfrAdaptive.error()
        state.tone == UpdateTone.SUCCESS -> NrfrAdaptive.link()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceVariant

    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = NrfrAdaptive.card(),
            border = androidx.compose.foundation.BorderStroke(1.dp, outline),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = onVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (canDismiss) {
                        Text(
                            "关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = onVariant,
                            modifier = Modifier.clickableNoIndication(onClick = onDismiss)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        state.currentVersion.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = onSurface
                    )
                    Text(
                        "→",
                        color = onVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        state.latestVersion.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (state.tone == UpdateTone.ERROR) onSurface else NrfrAdaptive.link()
                    )
                }

                Text(
                    state.detail.ifBlank { "请稍候…" },
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = onVariant
                )

                if (state.showProgress) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                state.progressLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Text(
                                "${state.progressPercent.coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = accent
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(NrfrPill)
                                .background(track)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(state.progressPercent.coerceIn(0, 100) / 100f)
                                    .clip(NrfrPill)
                                    .background(NrfrAdaptive.link())
                            )
                        }
                    }
                }

                Button(
                    onClick = onPrimary,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = NrfrPill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = outline,
                        disabledContentColor = onVariant
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        state.actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NrfrCardShape,
        color = NrfrAdaptive.card(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            content()
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(NrfrAdaptive.link())
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoIndication(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = NrfrAdaptive.link()
            )
        )
    }
}
