package app.sprintstacker.core

import kotlin.random.Random

object Scoring {
    /** One point per second focused. A finished sprint earns its full length. */
    fun completedPoints(preset: Preset): Int = preset.seconds

    /** Giving up keeps half of the whole seconds focused. */
    fun abandonedPoints(preset: Preset, elapsedMs: Long): Int =
        (elapsedMs.coerceIn(0, preset.durationMs) / 1000L / 2L).toInt()
}

object BlockFactory {
    const val COLOR_COUNT = 4

    fun square(id: Long, preset: Preset, colorIndex: Int): Block = Block(
        id = id,
        kind = BlockKind.SQUARE,
        width = preset.size,
        height = preset.size,
        x = 0f,
        points = Scoring.completedPoints(preset),
        colorIndex = colorIndex % COLOR_COUNT,
    )

    /**
     * A malformed penalty rectangle. Its size and sideways offset scale with the
     * preset, so quitting a long sprint unbalances the tower more.
     */
    fun penalty(id: Long, preset: Preset, points: Int, random: Random): Block {
        val base = preset.size
        val side = if (random.nextBoolean()) 1f else -1f
        return Block(
            id = id,
            kind = BlockKind.PENALTY,
            width = base * (0.9f + random.nextFloat() * 0.7f),
            height = base * (0.35f + random.nextFloat() * 0.35f),
            x = side * base * (0.12f + random.nextFloat() * 0.38f),
            points = points,
            colorIndex = 0,
        )
    }
}

sealed interface SprintOutcome {
    val block: Block

    data class Completed(override val block: Block) : SprintOutcome
    data class Abandoned(override val block: Block, val reason: AbandonReason) : SprintOutcome
}

/**
 * What to do with a sprint the app finds on launch. Leaving the app counts as
 * giving up, except when the screen was only locked.
 */
enum class RecoveryAction { COMPLETE, RESUME, ABANDON }

object SprintRules {

    fun complete(sprint: ActiveSprint, id: Long, colorIndex: Int): SprintOutcome.Completed =
        SprintOutcome.Completed(BlockFactory.square(id, sprint.preset, colorIndex))

    fun abandon(
        sprint: ActiveSprint,
        nowMs: Long,
        reason: AbandonReason,
        id: Long,
        random: Random,
    ): SprintOutcome.Abandoned {
        val points = Scoring.abandonedPoints(sprint.preset, sprint.elapsedMs(nowMs))
        return SprintOutcome.Abandoned(BlockFactory.penalty(id, sprint.preset, points, random), reason)
    }

    /**
     * @param stoppedWhileLocked true when the app last went to the background
     *   because the screen turned off, not because the user left it.
     */
    fun recover(sprint: ActiveSprint, nowMs: Long, stoppedWhileLocked: Boolean): RecoveryAction = when {
        !stoppedWhileLocked -> RecoveryAction.ABANDON
        sprint.isFinished(nowMs) -> RecoveryAction.COMPLETE
        else -> RecoveryAction.RESUME
    }
}

/** Interstitials show after every [every]th completed sprint, and after Clear Board. */
class InterstitialPolicy(private val every: Int = 2) {
    fun shouldShowAfterSprint(completedSinceLastAd: Int): Boolean = completedSinceLastAd >= every
}
