package com.github.nrfr.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

/** 不绘制任何按压/涟漪背景 */
object NoIndication : Indication {
    @Deprecated("Super method is deprecated")
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        return NoIndicationInstance
    }

    private object NoIndicationInstance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() = Unit
    }
}

/** 可点击但不出现灰色/涟漪背景 */
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
