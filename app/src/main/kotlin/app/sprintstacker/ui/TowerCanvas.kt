package app.sprintstacker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.sprintstacker.core.Block
import app.sprintstacker.core.BlockKind
import app.sprintstacker.core.Physics
import app.sprintstacker.game.GameUiState
import app.sprintstacker.game.Phase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Tower units per screen width: the center column is 1 unit wide. */
private const val UNITS_ACROSS = 5f
private const val GRAVITY = 34f // units / s²
private val Gravity = Easing { t -> t * t }

private data class Body(
    val block: Block,
    val cx: Float,
    val cy: Float,
    val vx: Float,
    val vy: Float,
    val angle: Float, // degrees
    val spin: Float, // degrees / s
) {
    fun step(dt: Float) = copy(
        cx = cx + vx * dt,
        cy = cy + vy * dt,
        vy = vy - 22f * dt,
        angle = angle + spin * dt,
    )
}

/**
 * Draws the playfield and runs the drop, wobble and fall animations. The view
 * model owns game state; this only reports when an animation has finished.
 */
@Composable
fun TowerCanvas(
    state: GameUiState,
    onLanded: (Long) -> Unit,
    onFallFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val landed by rememberUpdatedState(onLanded)
    val fallFinished by rememberUpdatedState(onFallFinished)
    val vibrate by rememberUpdatedState(state.vibrate)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val unit = widthPx / UNITS_ACROSS
        val viewH = heightPx / unit
        val towerHeight = state.tower.height
        val camTarget = max(-0.6f, towerHeight + 1.2f - (viewH - 3.2f))
        val camY by animateFloatAsState(camTarget, tween(500), label = "camera")
        val camNow by rememberUpdatedState(camY)
        val spawnY = camY + viewH - 2.1f

        // Drop: a penalty first bends from the charging square into its real shape.
        val incoming = state.incoming
        val dropY = remember { Animatable(0f) }
        val morph = remember { Animatable(1f) }
        LaunchedEffect(incoming?.id) {
            val block = incoming ?: return@LaunchedEffect
            val start = camTarget + viewH - 2.1f
            dropY.snapTo(start)
            if (block.kind == BlockKind.PENALTY) {
                morph.snapTo(0f)
                morph.animateTo(1f, tween(320))
            } else {
                morph.snapTo(1f)
            }
            val distance = (start - towerHeight).coerceAtLeast(0.05f)
            val ms = (sqrt(2f * distance / GRAVITY) * 1000f).toInt().coerceIn(120, 1200)
            dropY.animateTo(towerHeight, tween(ms, easing = Gravity))
            if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            landed(block.id)
        }

        // Blocks sliding off after a topple or Clear Board.
        val bodies = remember { mutableStateListOf<Body>() }
        LaunchedEffect(state.fallKey) {
            if (state.falling.isEmpty()) return@LaunchedEffect
            val rnd = Random(state.fallKey)
            bodies.clear()
            state.falling.forEachIndexed { i, f ->
                val dir = if (f.direction != 0) f.direction.toFloat() else if (rnd.nextBoolean()) 1f else -1f
                val spread = if (f.direction != 0) 1.3f + i * 0.4f else 0.5f + rnd.nextFloat() * 2f
                bodies += Body(
                    block = f.block,
                    cx = f.block.x,
                    cy = f.baseY + f.block.height / 2f,
                    vx = dir * (spread + rnd.nextFloat() * 0.6f),
                    vy = 0.6f + rnd.nextFloat() * 1.2f,
                    angle = 0f,
                    spin = dir * (90f + rnd.nextFloat() * 140f),
                )
            }
            var last = withFrameNanos { it }
            while (bodies.isNotEmpty()) {
                val t = withFrameNanos { it }
                val dt = ((t - last) / 1e9f).coerceAtMost(0.033f)
                last = t
                for (i in bodies.indices) bodies[i] = bodies[i].step(dt)
                bodies.removeAll { it.cy < camNow - 3f || abs(it.cx) > UNITS_ACROSS }
            }
            fallFinished()
        }

        val tipping = state.tipping
        val wobble = if (state.phase == Phase.Tipping && tipping != null) {
            val transition = rememberInfiniteTransition(label = "tip")
            transition.animateFloat(
                -1f, 1f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "wobble",
            ).value
        } else 0f

        val charge by animateFloatAsState(state.progress, tween(220, easing = LinearEasing), label = "charge")
        val rulerStyle = TextStyle(
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        val bestStyle = rulerStyle.copy(color = Palette.Lemon)
        val pctStyle = rulerStyle.copy(fontSize = 11.sp)

        Canvas(Modifier.fillMaxSize()) {
            fun sx(x: Float) = size.width / 2f + x * unit
            fun sy(y: Float) = size.height - (y - camY) * unit

            drawRect(Brush.verticalGradient(listOf(Color(0xFF171238), Color(0xFF241C50))))

            // Grid: half-unit squares aligned to the column.
            val grid = Color.White.copy(alpha = 0.04f)
            for (k in -5..5) drawLine(grid, Offset(sx(k * 0.5f), 0f), Offset(sx(k * 0.5f), size.height))
            var gy = kotlin.math.floor(camY * 2f) / 2f
            while (gy < camY + viewH + 1f) {
                drawLine(grid, Offset(0f, sy(gy)), Offset(size.width, sy(gy)))
                gy += 0.5f
            }

            // Center drop column.
            drawRect(Palette.Tangerine.copy(alpha = 0.05f), Offset(sx(-0.5f), 0f), Size(unit, size.height))
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 14f))
            for (x in floatArrayOf(-0.5f, 0.5f)) {
                drawLine(Color.White.copy(alpha = 0.14f), Offset(sx(x), 0f), Offset(sx(x), size.height), pathEffect = dash)
            }

            // Height ruler.
            var ry = max(1, kotlin.math.ceil(camY).toInt())
            while (ry < camY + viewH) {
                val py = sy(ry.toFloat())
                if (py > 44.sp.toPx()) {
                    drawRect(Color.White.copy(alpha = 0.18f), Offset(size.width - 26f, py), Size(16f, 2f))
                    drawText(textMeasurer, "$ry", Offset(size.width - 52f, py - 14f), rulerStyle)
                }
                ry++
            }

            // Best height line.
            if (state.bestHeight > 0f) {
                val py = sy(state.bestHeight)
                if (py > 44.sp.toPx() && py < size.height) {
                    drawLine(
                        Palette.Lemon.copy(alpha = 0.55f), Offset(20f, py), Offset(size.width - 70f, py),
                        strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)),
                    )
                    drawText(textMeasurer, "BEST", Offset(24f, py - 34f), bestStyle)
                }
            }

            // Ground.
            val ground = sy(0f)
            drawRect(Color(0xFF2E2563), Offset(0f, ground), Size(size.width, size.height - ground))
            drawRect(Color(0xFF4A3E95), Offset(0f, ground), Size(size.width, 4f))

            // Drop guide under whatever is coming down.
            val sprint = state.sprint
            val guideX = when {
                incoming != null -> incoming.x * morph.value
                sprint != null -> 0f
                else -> null
            }
            if (guideX != null) {
                val fromY = if (incoming != null) dropY.value else spawnY
                drawLine(
                    Color.White.copy(alpha = 0.28f), Offset(sx(guideX), sy(fromY)), Offset(sx(guideX), sy(towerHeight)),
                    strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f)),
                )
            }

            // Settled tower; the part above a failing block tilts around its edge.
            val blocks = state.tower.blocks
            val bases = Physics.baseHeights(blocks)
            val fail = if (tipping != null && state.phase == Phase.Tipping) tipping.failIndex else -1
            blocks.forEachIndexed { i, b ->
                if (fail < 0 || i <= fail) drawBlock(b, sx(b.x - b.width / 2f), sy(bases[i] + b.height), b.width * unit, b.height * unit, unit)
            }
            if (fail >= 0 && tipping != null) {
                val support = blocks[fail]
                val pivot = Offset(
                    sx(support.x + tipping.toppleDirection * support.width / 2f),
                    sy(bases[fail] + support.height),
                )
                rotate(tipping.toppleDirection * (5f + 1.5f * wobble), pivot) {
                    for (i in fail + 1 until blocks.size) {
                        val b = blocks[i]
                        drawBlock(b, sx(b.x - b.width / 2f), sy(bases[i] + b.height), b.width * unit, b.height * unit, unit)
                    }
                }
                drawCircle(Palette.Hazard, 8f, pivot)
            }

            // Falling bodies.
            for (body in bodies) {
                val w = body.block.width * unit
                val h = body.block.height * unit
                val c = Offset(sx(body.cx), sy(body.cy))
                rotate(body.angle, c) { drawBlock(body.block, c.x - w / 2f, c.y - h / 2f, w, h, unit) }
            }

            // Incoming block.
            if (incoming != null) {
                val from = state.preset.size
                val m = morph.value
                val w = from + (incoming.width - from) * m
                val h = from + (incoming.height - from) * m
                val x = incoming.x * m
                drawBlock(incoming, sx(x - w / 2f), sy(dropY.value + h), w * unit, h * unit, unit)
            }

            // Charging block while a sprint runs.
            if (sprint != null && incoming == null) {
                val side = sprint.preset.size * unit
                val left = sx(-sprint.preset.size / 2f)
                val top = sy(spawnY + sprint.preset.size)
                val color = Palette.Blocks[blocks.count { it.kind == BlockKind.SQUARE } % Palette.Blocks.size]
                val r = CornerRadius(12f)
                val clip = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, r)) }
                clipPath(clip) {
                    drawRect(Color.White.copy(alpha = 0.04f), Offset(left, top), Size(side, side))
                    drawRect(color.copy(alpha = 0.9f), Offset(left, top + side * (1f - charge)), Size(side, side * charge))
                }
                drawRoundRect(
                    color, Offset(left, top), Size(side, side), r,
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
                )
                val label = "${(charge * 100).toInt()}%"
                val layout = textMeasurer.measure(label, pctStyle.copy(color = if (charge > 0.55f) Color(0xFF1A1030) else Palette.Text))
                drawText(layout, topLeft = Offset(left + (side - layout.size.width) / 2f, top + (side - layout.size.height) / 2f))
            }
        }
    }
}

private fun DrawScope.drawBlock(b: Block, left: Float, top: Float, w: Float, h: Float, unit: Float) {
    val penalty = b.kind == BlockKind.PENALTY
    val color = if (penalty) Palette.Hazard else Palette.Blocks[b.colorIndex % Palette.Blocks.size]
    val r = CornerRadius(min(18f, min(w * 0.1f, h * 0.16f)))
    drawRoundRect(color, Offset(left, top), Size(w, h), r)
    val shape = Path().apply { addRoundRect(RoundRect(left, top, left + w, top + h, r)) }
    clipPath(shape) {
        if (penalty) {
            val stripe = Color(0x8C6E0A23)
            val step = unit * 0.24f
            var k = -h
            while (k < w + h) {
                drawLine(stripe, Offset(left + k, top + h), Offset(left + k + h, top), strokeWidth = max(8f, unit * 0.09f))
                k += step
            }
        }
        val bevel = max(4f, unit * 0.07f)
        drawRect(Color.White.copy(alpha = 0.3f), Offset(left, top), Size(w, bevel))
        drawRect(Color.White.copy(alpha = 0.3f), Offset(left, top), Size(bevel, h))
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(left, top + h - bevel), Size(w, bevel))
        drawRect(Color.Black.copy(alpha = 0.25f), Offset(left + w - bevel, top), Size(bevel, h))
    }
    if (!penalty) {
        drawRect(Color.White.copy(alpha = 0.4f), Offset(left + w * 0.3f, top + h * 0.3f), Size(w * 0.4f, h * 0.4f), style = Stroke(3f))
    }
    drawRoundRect(Color(0xB3080518), Offset(left, top), Size(w, h), r, style = Stroke(3f))
}
