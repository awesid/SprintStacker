package app.sprintstacker.core

import kotlin.math.abs
import kotlin.math.sign

/**
 * Result of checking a tower's balance.
 *
 * @property lean worst ratio, over every block, of how far the center of mass
 *   of everything above it sits from its own center, relative to its half width.
 *   0 is perfectly centered, 1 is right on an edge, above 1 means it tips.
 * @property leanDirection -1 (left), 0 or 1 (right) for the worst lean.
 * @property failIndex index of the lowest block whose load has passed its edge,
 *   or -1. Every block above it topples.
 * @property toppleDirection side the failing blocks fall toward.
 */
data class Stability(
    val lean: Float,
    val leanDirection: Int,
    val failIndex: Int,
    val toppleDirection: Int,
) {
    val isToppling: Boolean get() = failIndex >= 0
    val isUnstable: Boolean get() = !isToppling && lean >= UNSTABLE_LEAN
    val isLeaning: Boolean get() = lean >= LEANING

    companion object {
        const val LEANING = 0.5f
        const val UNSTABLE_LEAN = 0.7f
        val STEADY = Stability(0f, 0, -1, 0)
    }
}

object Physics {

    fun analyze(blocks: List<Block>): Stability {
        if (blocks.size < 2) return Stability.STEADY
        var lean = 0f
        var leanDir = 0
        var fail = -1
        var failDir = 0
        // Walk from the top down so the load above each block is a running sum.
        var mass = 0f
        var moment = 0f
        for (i in blocks.size - 1 downTo 1) {
            val above = blocks[i]
            mass += above.mass
            moment += above.mass * above.x
            val support = blocks[i - 1]
            val offset = moment / mass - support.x
            val ratio = abs(offset) / (support.width / 2f)
            if (ratio > lean) {
                lean = ratio
                leanDir = offset.sign.toInt()
            }
            // Keep the lowest failing support: everything above it falls.
            if (ratio > 1f) {
                fail = i - 1
                failDir = if (offset >= 0f) 1 else -1
            }
        }
        return Stability(lean, leanDir, fail, failDir)
    }

    /** Bottom edge of each block, in tower units above the ground. */
    fun baseHeights(blocks: List<Block>): List<Float> {
        var y = 0f
        return blocks.map { b -> y.also { y += b.height } }
    }

    fun height(blocks: List<Block>): Float = blocks.sumOf { it.height.toDouble() }.toFloat()
}
