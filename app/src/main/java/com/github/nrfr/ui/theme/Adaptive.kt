package com.github.nrfr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 随日/夜间切换的表面与强调色，避免硬编码浅色在暗色下发灰难读 */
object NrfrAdaptive {
    @Composable
    fun card(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun wash(): Color = MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun link(): Color = if (isSystemInDarkTheme()) DarkLink else NrfrLink

    /** 实心链接色块上的前景（图标/白字） */
    @Composable
    fun onLink(): Color = if (isSystemInDarkTheme()) Color(0xFF0B1220) else Color.White

    @Composable
    fun linkSoft(): Color = if (isSystemInDarkTheme()) Color(0xFF1A2F4D) else NrfrLinkSoft

    @Composable
    fun okBg(): Color = if (isSystemInDarkTheme()) Color(0xFF1A2F4D) else NrfrOkBg

    @Composable
    fun codeAccent(): Color = if (isSystemInDarkTheme()) Color(0xFFC4B5FD) else NrfrCodeAccent

    @Composable
    fun error(): Color = MaterialTheme.colorScheme.error

    @Composable
    fun errorContainer(): Color = MaterialTheme.colorScheme.errorContainer
}
