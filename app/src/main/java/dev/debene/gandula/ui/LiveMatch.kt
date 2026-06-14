package dev.debene.gandula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import dev.debene.gandula.domain.MatchEvent
import dev.debene.gandula.domain.MatchEventKind
import dev.debene.gandula.domain.Side
import dev.debene.ui.theme.GandulaMono
import dev.debene.ui.theme.GradientEnd
import dev.debene.ui.theme.GradientStart
import dev.debene.ui.theme.NeonCyan
import kotlinx.coroutines.delay

/**
 * Plays a slice of a match minute-by-minute: a clock ticks from [startMinute] to
 * the last event's minute (~[minuteMillis] per minute), revealing each event as
 * the clock reaches it and counting goals into a live scoreboard. "Pular" jumps
 * to the end. [onDone] fires exactly once, when the slice finishes (played or
 * skipped) — the caller uses it to advance the flow (half-time card, next round).
 */
@Composable
fun MatchBroadcast(
    homeName: String,
    homeId: Int,
    awayName: String,
    awayId: Int,
    events: List<MatchEvent>,
    startMinute: Int,
    baselineHome: Int,
    baselineAway: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    minuteMillis: Long = 340L,
) {
    val lastMinute = events.lastOrNull()?.minute ?: startMinute
    // Anything already at/under the opening minute is shown immediately.
    val opening = events.indexOfFirst { it.minute > startMinute }.let { if (it < 0) events.size else it }
    var clock by remember(events) { mutableIntStateOf(startMinute) }
    var shown by remember(events) { mutableIntStateOf(opening) }
    var skipped by remember(events) { mutableStateOf(false) }
    var finished by remember(events) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(events, skipped) {
        if (finished) return@LaunchedEffect
        if (skipped) {
            shown = events.size; clock = lastMinute
            finished = true; onDone(); return@LaunchedEffect
        }
        while (clock < lastMinute) {
            delay(minuteMillis)
            clock++
            var hitGoal = false
            while (shown < events.size && events[shown].minute <= clock) {
                if (events[shown].kind is MatchEventKind.Goal) hitGoal = true
                shown++
            }
            if (hitGoal) delay(minuteMillis * 2) // let a goal breathe
        }
        shown = events.size
        finished = true
        onDone()
    }

    val revealed = events.take(shown)
    val homeScore = baselineHome + revealed.count { it.kind is MatchEventKind.Goal && it.side == Side.Home }
    val awayScore = baselineAway + revealed.count { it.kind is MatchEventKind.Goal && it.side == Side.Away }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveScoreboard(homeName, homeId, homeScore, awayName, awayId, awayScore, clock)

        val listState = rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(shown) {
            if (revealed.isNotEmpty()) listState.animateScrollToItem(revealed.lastIndex)
        }
        Card(shape = RoundedCornerShape(12.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(revealed) { FeedLine(it) }
            }
        }
        if (!finished) {
            OutlinedButton(onClick = { skipped = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text("Pular ⏩", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveScoreboard(
    homeName: String, homeId: Int, homeScore: Int,
    awayName: String, awayId: Int, awayScore: Int,
    clock: Int,
) {
    val gradient = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.border(2.dp, NeonCyan.copy(alpha = pulse), RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.fillMaxWidth().background(gradient).padding(16.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LiveCrest(homeName, homeId, Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "$homeScore : $awayScore",
                            fontFamily = GandulaMono,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 32.sp,
                            color = Color.White,
                        )
                    }
                    LiveCrest(awayName, awayId, Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier.padding(top = 10.dp)
                        .background(NeonCyan.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${clock.coerceAtMost(90)}'" + if (clock > 90) " +${clock - 90}" else "",
                        fontFamily = GandulaMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCrest(name: String, id: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ClubCrest(name, id, size = 36.dp)
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** One commentary line — shared by the live broadcast and the static feed. */
@Composable
internal fun FeedLine(event: MatchEvent) {
    val isGoal = event.kind is MatchEventKind.Goal
    val isWhistle = event.kind is MatchEventKind.HalfTime || event.kind is MatchEventKind.FullTime
    val color = when {
        isGoal -> MaterialTheme.colorScheme.primary
        event.kind is MatchEventKind.RedCard -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bg = if (isGoal) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isGoal) {
            Box(
                modifier = Modifier.padding(end = 8.dp)
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("GOL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(
            text = event.text,
            color = color,
            fontFamily = GandulaMono,
            fontSize = 14.sp,
            fontWeight = if (isGoal || isWhistle) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Split a match's events at the half-time whistle (inclusive in the first half). */
fun splitAtHalfTime(events: List<MatchEvent>): Pair<List<MatchEvent>, List<MatchEvent>> {
    val idx = events.indexOfFirst { it.kind is MatchEventKind.HalfTime }
    if (idx < 0) return events to emptyList()
    return events.subList(0, idx + 1) to events.subList(idx + 1, events.size)
}
