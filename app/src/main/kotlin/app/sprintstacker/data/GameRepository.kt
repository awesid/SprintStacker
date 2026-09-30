package app.sprintstacker.data

import app.sprintstacker.core.Block
import app.sprintstacker.core.BlockKind

class GameRepository(private val dao: GameDao) {

    suspend fun load(): Pair<GameStateEntity, List<Block>> =
        (dao.state() ?: GameStateEntity()) to dao.blocks().map { it.toBlock() }

    suspend fun save(state: GameStateEntity, blocks: List<Block>) {
        dao.saveAll(state, blocks.mapIndexed { i, b -> b.toEntity(i) })
    }

    private fun BlockEntity.toBlock() = Block(
        id = id,
        kind = runCatching { BlockKind.valueOf(kind) }.getOrDefault(BlockKind.SQUARE),
        width = width,
        height = height,
        x = x,
        points = points,
        colorIndex = colorIndex,
    )

    private fun Block.toEntity(position: Int) = BlockEntity(
        id = id,
        position = position,
        kind = kind.name,
        width = width,
        height = height,
        x = x,
        points = points,
        colorIndex = colorIndex,
    )
}
