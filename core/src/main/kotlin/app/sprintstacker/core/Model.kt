package app.sprintstacker.core

/**
 * Sprint lengths. [size] is the side of the square a completed sprint drops,
 * in tower units (1.0 = the width of the center column).
 */
enum class Preset(val seconds: Int, val size: Float, val label: String) {
    FIVE_MIN(300, 1.0f, "5m"),
    THREE_MIN(180, 0.8f, "3m"),
    ONE_MIN(60, 0.6f, "1m"),
    TEST(10, 0.45f, "10s");

    val durationMs: Long get() = seconds * 1000L
}

enum class BlockKind { SQUARE, PENALTY }

/**
 * One block in the tower. [x] is the horizontal offset of the block's center
 * from the center column. Vertical position is implied by stacking order.
 */
data class Block(
    val id: Long,
    val kind: BlockKind,
    val width: Float,
    val height: Float,
    val x: Float,
    val points: Int,
    val colorIndex: Int,
) {
    val mass: Float get() = width * height
}

enum class AbandonReason { GAVE_UP, LEFT_APP }

/** A sprint in progress, persisted so it survives the app being killed. */
data class ActiveSprint(val preset: Preset, val startedAtMs: Long) {
    fun elapsedMs(nowMs: Long): Long = (nowMs - startedAtMs).coerceIn(0, preset.durationMs)
    fun progress(nowMs: Long): Float = elapsedMs(nowMs).toFloat() / preset.durationMs
    fun isFinished(nowMs: Long): Boolean = nowMs - startedAtMs >= preset.durationMs
}
