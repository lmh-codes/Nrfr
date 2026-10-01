package com.github.nrfr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 大圆角：选择框 / 卡片 / pill 按钮 */
val NrfrPill = RoundedCornerShape(50)
val NrfrSelectShape = RoundedCornerShape(24.dp)
val NrfrCardShape = RoundedCornerShape(18.dp)
val NrfrChipShape = RoundedCornerShape(12.dp)

val NrfrShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
