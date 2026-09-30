package app.sprintstacker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sprintstacker.core.Preset
import app.sprintstacker.core.Stability
import app.sprintstacker.game.GameUiState
import app.sprintstacker.game.GameViewModel
import app.sprintstacker.game.Phase
import app.sprintstacker.game.RewardKind
import app.sprintstacker.game.Tone
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.min

private val Card = RoundedCornerShape(12.dp)

@Composable
fun GameScreen(state: GameUiState, vm: GameViewModel, onOpenSettings: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            TopBar(enabled = state.sprint == null, onOpenSettings = onOpenSettings)
            Hud(state)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(18.dp)),
            ) {
                TowerCanvas(state, vm::onLanded, vm::onFallFinished, Modifier.fillMaxSize())
                BalanceGauge(state.stability, Modifier.align(Alignment.TopCenter).padding(8.dp))
                if (state.showWarning) {
                    LeanWarning(
                        onStabilize = { vm.requestReward(RewardKind.STABILIZE) },
                        onDismiss = vm::dismissWarning,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp, start = 8.dp, end = 8.dp),
                    )
                }
                NoticePill(state, vm::clearNotice, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
            Controls(state, vm, onClear = { confirmClear = true })
        }

        if (state.phase == Phase.Tipping && state.tipping != null) {
            ToppleSheet(state, vm)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the board?") },
            text = {
                Text(
                    "Your ${state.tower.size}-block tower is removed and the score resets to 0. " +
                        "Your best score of ${fmt(state.best)} stays.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearBoard() }) {
                    Text("Clear board", color = Palette.Hazard)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep my tower") } },
            containerColor = Palette.Panel,
        )
    }
}

@Composable
private fun TopBar(enabled: Boolean, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("SPRINT", fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
            Text("STACKER", fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 16.sp, letterSpacing = 1.sp, color = Palette.Tangerine)
        }
        IconButton(
            onClick = onOpenSettings,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Palette.Panel2),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun Hud(state: GameUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Stat("Score", fmt(state.score), Palette.Tangerine, Modifier.weight(1f))
        Stat("Best", fmt(state.best), Palette.Text, Modifier.weight(1f))
        Stat("Height", state.tower.size.toString(), Palette.Text, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(Palette.Panel, Card)
            .border(1.dp, Palette.Line, Card)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label.uppercase(), fontSize = 9.5.sp, letterSpacing = 1.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
    }
}

/** Needle shows the worst center-of-mass offset; zones mirror Stability thresholds. */
@Composable
private fun BalanceGauge(stability: Stability, modifier: Modifier) {
    val lean = min(stability.lean, 1f)
    val target = 0.5f + stability.leanDirection * lean * 0.5f
    val pos by animateFloatAsState(target, spring(dampingRatio = 0.55f), label = "needle")
    val (label, color) = when {
        stability.isToppling -> "Tipping" to Palette.Hazard
        stability.isUnstable -> "${(lean * 100).toInt()}% lean" to Palette.Hazard
        stability.isLeaning -> "${(lean * 100).toInt()}% lean" to Palette.Lemon
        lean > 0.05f -> "${(lean * 100).toInt()}% lean" to Palette.Aqua
        else -> "Steady" to Palette.Aqua
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Page.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .border(1.dp, if (stability.lean >= Stability.UNSTABLE_LEAN) Palette.Hazard else Palette.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BALANCE", fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(Modifier.weight(1f).height(18.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                    Brush.horizontalGradient(
                        0f to Palette.Hazard, 0.15f to Palette.Hazard, 0.15f to Palette.Lemon, 0.25f to Palette.Lemon,
                        0.25f to Palette.Aqua, 0.75f to Palette.Aqua, 0.75f to Palette.Lemon, 0.85f to Palette.Lemon,
                        0.85f to Palette.Hazard, 1f to Palette.Hazard,
                    ),
                ),
            )
            Box(
                Modifier
                    .offset(x = maxWidth * pos - 3.dp)
                    .size(6.dp, 18.dp)
                    .background(Color.White, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color,
            textAlign = TextAlign.End, modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun LeanWarning(onStabilize: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF2A1030), Card)
            .border(1.dp, Palette.Hazard, Card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Tower is leaning. One more off-center block could tip it over.", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStabilize) { Text("Stabilize tower · Ad") }
            OutlinedButton(onClick = onDismiss) { Text("Not now", color = Palette.Muted) }
        }
    }
}

@Composable
private fun NoticePill(state: GameUiState, onTimeout: (Long) -> Unit, modifier: Modifier) {
    val notice = state.notice
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(2400)
            onTimeout(notice.id)
        }
    }
    AnimatedVisibility(notice != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        val n = notice ?: return@AnimatedVisibility
        val color = when (n.tone) {
            Tone.Good -> Palette.Aqua
            Tone.Bad -> Palette.Hazard
            Tone.Neutral -> Palette.Text
        }
        Text(
            n.text,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(Palette.Page.copy(alpha = 0.9f), RoundedCornerShape(50))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun Controls(state: GameUiState, vm: GameViewModel, onClear: () -> Unit) {
    val idle = state.phase == Phase.Idle && state.loaded
    val running = state.phase == Phase.Running
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            val secs = state.remainingSeconds
            Text(
                "%02d:%02d".format(secs / 60, secs % 60),
                fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            val label = when (state.phase) {
                Phase.Running -> {
                    val preset = state.sprint?.preset ?: state.preset
                    "Focus. ${(preset.seconds * state.progress).toInt()} of ${preset.seconds} pts"
                }
                Phase.Idle -> if (state.tower.size > 0) "Ready for the next block" else "Ready when you are"
                Phase.Tipping -> "Tower is tipping"
                else -> "Block incoming"
            }
            Text(label, fontSize = 12.sp, color = Palette.Muted, textAlign = TextAlign.End, modifier = Modifier.padding(bottom = 6.dp))
        }
        LinearProgressIndicator(
            progress = { if (running) state.progress else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Palette.Tangerine,
            trackColor = Palette.Panel2,
            drawStopIndicator = {},
        )
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Preset.entries.forEach { p ->
                PresetChip(p, selected = p == state.preset, enabled = idle, onClick = { vm.selectPreset(p) }, modifier = Modifier.weight(1f))
            }
        }
        ActionButton(state, idle, onStart = vm::startSprint, onGiveUp = vm::giveUp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClear, enabled = idle && state.tower.size > 0) {
                Text("Clear Board", color = if (idle) Palette.Muted else Palette.Faint)
            }
            Spacer(Modifier.weight(1f))
            Text("No ads during a sprint", fontSize = 11.sp, color = Palette.Faint)
        }
    }
}

private val sizeLetter = mapOf(Preset.FIVE_MIN to "L", Preset.THREE_MIN to "M", Preset.ONE_MIN to "S", Preset.TEST to "XS")

@Composable
private fun PresetChip(preset: Preset, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0xFF2D1F3A) else Palette.Panel, shape)
            .border(1.dp, if (selected) Palette.Tangerine else Palette.Line, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val alpha = if (enabled || selected) 1f else 0.45f
        Text(preset.label, fontWeight = FontWeight.Bold, color = (if (selected) Palette.Tangerine else Palette.Text).copy(alpha = alpha))
        Text("${preset.seconds} pts · ${sizeLetter[preset]}", fontSize = 9.sp, color = Palette.Muted.copy(alpha = alpha))
    }
}

@Composable
private fun ActionButton(state: GameUiState, idle: Boolean, onStart: () -> Unit, onGiveUp: () -> Unit) {
    val running = state.phase == Phase.Running
    val (title, subtitle) = when {
        running -> "STOP / GIVE UP" to "Drops a penalty block"
        idle -> "START SPRINT" to "${presetName(state.preset)} · up to ${state.preset.seconds} pts"
        else -> "SETTLING…" to "Wait for the tower"
    }
    Button(
        onClick = if (running) onGiveUp else onStart,
        enabled = running || idle,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) Palette.Hazard else Palette.Tangerine,
            contentColor = if (running) Color.White else Color(0xFF2A1300),
            disabledContainerColor = Palette.Panel2,
            disabledContentColor = Palette.Muted,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp)
            Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ToppleSheet(state: GameUiState, vm: GameViewModel) {
    val stability = state.tipping ?: return
    val atRisk = state.tower.atRisk(stability)
    val support = state.tower.blocks.getOrNull(stability.failIndex)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xA0060410))
            // Swallow taps so the game underneath stays untouchable.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Palette.Panel,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your tower is tipping!", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    "The blocks above the ${if (support?.kind == app.sprintstacker.core.BlockKind.PENALTY) "penalty block" else "block below them"} " +
                        "are past their balance point. Save them with a short video, or let them fall.",
                    color = Palette.Muted, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskStat("Blocks at risk", atRisk.size.toString(), Modifier.weight(1f))
                    RiskStat("Points at risk", fmt(atRisk.sumOf { it.points }), Modifier.weight(1f))
                }
                Button(onClick = { vm.requestReward(RewardKind.STABILIZE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stabilize tower · Watch ad")
                }
                Button(
                    onClick = { vm.requestReward(RewardKind.UNDO_PENALTY) },
                    enabled = state.tower.hasPenalty,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Sky, contentColor = Color(0xFF0A1A3A)),
                ) {
                    Text("Undo last penalty block · Watch ad")
                }
                OutlinedButton(onClick = vm::letFall, modifier = Modifier.fillMaxWidth()) {
                    Text("Let it fall", color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
private fun RiskStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Palette.Panel2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Palette.Hazard)
    }
}

private fun presetName(p: Preset) = when (p) {
    Preset.FIVE_MIN -> "5 minute focus"
    Preset.THREE_MIN -> "3 minute focus"
    Preset.ONE_MIN -> "1 minute focus"
    Preset.TEST -> "10 second test"
}

private fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)
