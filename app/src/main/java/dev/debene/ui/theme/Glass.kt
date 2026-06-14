package dev.debene.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's frosted-glass backdrop: a deep-slate fill with two soft radial glows
 * (indigo top-left, purple bottom-right) bleeding behind the content. Place once
 * at the screen root; glass cards sit on top of it.
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(SlateBg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(GlowIndigo, Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.06f),
                        radius = size.minDimension * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(GlowPurple, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.92f),
                        radius = size.minDimension * 1.05f,
                    ),
                )
            },
        content = content,
    )
}

/**
 * A translucent "glass" card: a faint white body over the glow, with a soft
 * glowing border and rounded 20dp corners. The frosted-glass building block that
 * replaces flat Material `Card`s for hero surfaces.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    borderColor: Color = GlassBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(if (active) GlassBodyActive else GlassBody, RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
        content = content,
    )
}
