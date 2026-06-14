package dev.debene.gandula.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.career.lineCounts
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.ui.theme.GandulaMono
import dev.debene.ui.theme.IndigoLight
import dev.debene.ui.theme.StatusGold
import kotlin.math.roundToInt

private val TOKEN = 46.dp
private val BENCH_TOKEN = 42.dp

/**
 * A pitch-view starting-XI editor: the eleven laid out by position over a drawn
 * green pitch, a scrollable bench below, and **drag-to-swap** — drag a reserve
 * onto a starter (or vice-versa) to swap them. Always keeps 11 starters, so the
 * result is fieldable; [onChange] fires the new XI after each swap.
 */
@Composable
fun PitchLineupEditor(
    squad: List<Player>,
    xi: List<Int>,
    formation: Formation,
    onChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = squad.associateBy { it.id }
    val xiSet = xi.toSet()
    val starters = xi.mapNotNull { byId[it] }
    val reserves = squad.filter { it.id !in xiSet }
        .sortedWith(compareBy({ it.position.ordinal }, { -TransferMarket.playerOverall(it) }))

    // Each token's home centre in window coords, for drop hit-testing across the
    // pitch and the (scrollable) bench.
    val centers = remember { mutableStateMapOf<Int, Offset>() }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val dropRadiusPx = with(density) { TOKEN.toPx() }

    fun swap(a: Int, b: Int) {
        val aIn = a in xiSet
        val bIn = b in xiSet
        if (aIn == bIn) return // both starters or both reserves → no change
        // Same-position swaps only, so the formation's composition (and its single
        // keeper) stays intact — you change the *shape* via the formation cycler.
        if (byId[a]?.position != byId[b]?.position) return
        val inXi = if (aIn) a else b
        val inBench = if (aIn) b else a
        onChange(xi.map { if (it == inXi) inBench else it })
    }

    fun dropOn(id: Int) {
        val dragPos = dragStart + dragDelta
        val target = centers.entries
            .filter { it.key != id }
            .minByOrNull { (it.value - dragPos).getDistance() }
        if (target != null && (target.value - dragPos).getDistance() < dropRadiusPx) swap(id, target.key)
    }

    fun Modifier.dragToken(id: Int): Modifier = this
        .onGloballyPositioned { c -> if (draggingId != id) centers[id] = c.boundsInWindow().center }
        .then(if (draggingId == id) Modifier.zIndex(2f).offset { IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt()) } else Modifier)
        .pointerInput(id, xi) {
            detectDragGestures(
                onDragStart = { draggingId = id; dragStart = centers[id] ?: Offset.Zero; dragDelta = Offset.Zero },
                onDrag = { change, amount -> change.consume(); dragDelta += amount },
                onDragEnd = { dropOn(id); draggingId = null; dragDelta = Offset.Zero },
                onDragCancel = { draggingId = null; dragDelta = Offset.Zero },
            )
        }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ── Pitch ──────────────────────────────────────────────────────────
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.80f)) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            Canvas(Modifier.matchParentSize()) { drawPitch() }
            val positions = formationLayout(starters, formation, w, h)
            val halfPx = with(density) { TOKEN.toPx() } / 2f
            starters.forEach { p ->
                val home = positions[p.id] ?: Offset.Zero
                TokenChip(
                    player = p,
                    diameter = TOKEN,
                    dragging = draggingId == p.id,
                    modifier = Modifier
                        .offset { IntOffset((home.x - halfPx).roundToInt(), (home.y - halfPx).roundToInt()) }
                        .dragToken(p.id),
                )
            }
        }
        PositionLegend(Modifier.padding(top = 6.dp))
        Text(
            "Banco · arraste para o campo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        // ── Bench ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            reserves.forEach { p ->
                TokenChip(player = p, diameter = BENCH_TOKEN, dragging = draggingId == p.id, modifier = Modifier.dragToken(p.id))
            }
        }
    }
}

/** Token fill by position — so a glance reads the role: GK amber, DEF blue,
 *  MID green, FWD rose. */
private fun positionColor(p: Position): Color = when (p) {
    Position.GK -> Color(0xFFFBBF24)  // amber
    Position.DEF -> Color(0xFF60A5FA) // blue
    Position.MID -> Color(0xFF34D399) // emerald
    Position.FWD -> Color(0xFFFB7185) // rose
}

@Composable
private fun TokenChip(player: Player, diameter: Dp, dragging: Boolean, modifier: Modifier = Modifier) {
    val lowStamina = player.attributes.stamina < 40
    val fill = positionColor(player.position)
    val onFill = when (player.position) {
        Position.GK -> Color(0xFF0B1222) // dark text on light amber
        else -> Color.White
    }
    val ring = when {
        dragging -> Color.White
        lowStamina -> StatusGold
        else -> Color.White.copy(alpha = 0.3f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(diameter)
                .background(fill, CircleShape)
                .border(if (dragging || lowStamina) 2.5.dp else 2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${TransferMarket.playerOverall(player)}", color = onFill, fontFamily = GandulaMono, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Text(
            player.name.substringAfterLast(' '),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PositionLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Position.GK to "GOL", Position.DEF to "DEF", Position.MID to "MEI", Position.FWD to "ATA").forEach { (pos, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(positionColor(pos), CircleShape))
                Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Place the eleven into the formation's slots: the keeper at the back, then the
 *  DEF/MID/FWD lines filled by best position fit (overflow spills to the next
 *  line), each line spread evenly across the width. Re-runs when the formation
 *  changes, so cycling 4-3-3 → 4-5-2 visibly reshapes the pitch. */
private fun formationLayout(starters: List<Player>, formation: Formation, w: Float, h: Float): Map<Int, Offset> {
    val (defC, midC, fwdC) = formation.lineCounts()
    val gk = starters.firstOrNull { it.position == Position.GK } ?: starters.lastOrNull()
    val remaining = starters.filter { it.id != gk?.id }.toMutableList()

    fun fill(role: Position, count: Int): List<Player> {
        val picked = ArrayList<Player>(count)
        for (p in remaining.filter { it.position == role }) { if (picked.size >= count) break; picked.add(p) }
        remaining.removeAll(picked.toSet())
        while (picked.size < count && remaining.isNotEmpty()) picked.add(remaining.removeAt(0))
        return picked
    }
    val defLine = fill(Position.DEF, defC)
    val midLine = fill(Position.MID, midC)
    val fwdLine = fill(Position.FWD, fwdC)

    val out = HashMap<Int, Offset>()
    gk?.let { out[it.id] = Offset(w * 0.5f, h * 0.90f) }
    fun place(line: List<Player>, yFrac: Float) {
        line.forEachIndexed { i, p -> out[p.id] = Offset(w * (i + 1) / (line.size + 1), h * yFrac) }
    }
    place(defLine, 0.70f)
    place(midLine, 0.46f)
    place(fwdLine, 0.20f)
    return out
}

private fun DrawScope.drawPitch() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E6B3E), Color(0xFF245834))))
    val line = Color.White.copy(alpha = 0.5f)
    val sw = size.minDimension * 0.006f
    val m = size.minDimension * 0.04f
    drawRect(line, topLeft = Offset(m, m), size = Size(size.width - 2 * m, size.height - 2 * m), style = Stroke(sw))
    drawLine(line, Offset(m, size.height / 2), Offset(size.width - m, size.height / 2), strokeWidth = sw)
    drawCircle(line, radius = size.minDimension * 0.12f, center = Offset(size.width / 2, size.height / 2), style = Stroke(sw))
    val boxW = size.width * 0.46f
    val boxH = size.height * 0.14f
    val bx = (size.width - boxW) / 2
    drawRect(line, topLeft = Offset(bx, m), size = Size(boxW, boxH), style = Stroke(sw))
    drawRect(line, topLeft = Offset(bx, size.height - m - boxH), size = Size(boxW, boxH), style = Stroke(sw))
}
