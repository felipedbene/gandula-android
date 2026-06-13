package dev.debene.gandula.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.debene.ui.theme.ElectricBlue
import kotlin.math.cos
import kotlin.math.sin

/** The app's soccer-ball mark — a blue ring with a central pentagon + spokes. */
@Composable
fun BallCrest(size: Dp, color: Color = ElectricBlue) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = color, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.16f))
        // central pentagon
        val pr = r * 0.42f
        val pts = (0 until 5).map { i ->
            val a = Math.toRadians((-90 + i * 72).toDouble())
            Offset(c.x + (pr * cos(a)).toFloat(), c.y + (pr * sin(a)).toFloat())
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, color)
        // spokes from pentagon corners outward
        pts.forEach { p ->
            val dir = Offset(p.x - c.x, p.y - c.y)
            val len = kotlin.math.hypot(dir.x, dir.y)
            val end = Offset(c.x + dir.x / len * r * 0.86f, c.y + dir.y / len * r * 0.86f)
            drawLine(color, p, end, strokeWidth = r * 0.1f)
        }
    }
}

/** A deterministic generated club crest: a colored rounded tile with the club's
 *  initials. Color derives from the team id, so each club is visually stable. */
@Composable
fun ClubCrest(name: String, seedId: Int, size: Dp = 22.dp) {
    val hue = ((seedId * 47) % 360 + 360) % 360
    val bg = Color.hsl(hue.toFloat(), 0.45f, 0.40f)
    val initials = name.split(' ', '-')
        .filter { it.isNotBlank() && it[0].isUpperCase() }
        .take(2).map { it.first() }.joinToString("")
        .ifEmpty { name.take(2) }.uppercase()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(percent = 28)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The GANDULA wordmark — bold, letter-spaced, electric blue. */
@Composable
fun Wordmark() {
    Text(
        "GANDULA",
        color = ElectricBlue,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        letterSpacing = 4.sp,
        style = MaterialTheme.typography.titleLarge,
    )
}
