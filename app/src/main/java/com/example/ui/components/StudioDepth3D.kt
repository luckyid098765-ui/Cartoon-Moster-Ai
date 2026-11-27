package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricViolet

@Composable
fun Tactile3DCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    accentGlow: Color = ElectricViolet,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "3d_card_scale"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = accentGlow.copy(alpha = 0.35f),
                spotColor = CyanSpark.copy(alpha = 0.28f)
            ),
        shape = shape,
        color = surfaceColor,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    accentGlow.copy(alpha = 0.22f),
                    Color.Black.copy(alpha = 0.28f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}
