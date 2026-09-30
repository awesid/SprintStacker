package app.sprintstacker.core

/** Immutable tower of blocks, bottom first. */
data class Tower(val blocks: List<Block> = emptyList()) {

    val size: Int get() = blocks.size
    val height: Float get() = Physics.height(blocks)
    val stability: Stability get() = Physics.analyze(blocks)
    val hasPenalty: Boolean get() = blocks.any { it.kind == BlockKind.PENALTY }

    fun add(block: Block): Tower = Tower(blocks + block)

    /** Blocks that will fall if the tower topples now. */
    fun atRisk(stability: Stability = this.stability): List<Block> =
        if (stability.isToppling) blocks.drop(stability.failIndex + 1) else emptyList()

    /** Removes the toppling blocks. Returns the new tower and the blocks that fell. */
    fun topple(stability: Stability = this.stability): Pair<Tower, List<Block>> {
        if (!stability.isToppling) return this to emptyList()
        val keep = stability.failIndex + 1
        return Tower(blocks.take(keep)) to blocks.drop(keep)
    }

    /** Rewarded: slides every block back onto the center column. */
    fun stabilized(): Tower = Tower(blocks.map { it.copy(x = 0f) })

    /** Rewarded: removes the highest penalty block; blocks above it settle down. */
    fun withoutLastPenalty(): Tower {
        val idx = blocks.indexOfLast { it.kind == BlockKind.PENALTY }
        if (idx < 0) return this
        return Tower(blocks.filterIndexed { i, _ -> i != idx })
    }
}
