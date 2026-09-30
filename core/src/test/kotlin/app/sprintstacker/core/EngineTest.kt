package app.sprintstacker.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {

    private var nextId = 0L
    private fun sq(size: Float = 1f, x: Float = 0f) =
        Block(nextId++, BlockKind.SQUARE, size, size, x, 300, 0)
    private fun pen(w: Float, h: Float, x: Float) =
        Block(nextId++, BlockKind.PENALTY, w, h, x, 50, 0)

    @Test
    fun centeredSquaresAreSteady() {
        val s = Physics.analyze(List(6) { sq() })
        assertEquals(0f, s.lean)
        assertFalse(s.isToppling)
        assertFalse(s.isUnstable)
    }

    @Test
    fun offsetPenaltyOnTopLeans() {
        val s = Physics.analyze(List(5) { sq() } + pen(1f, 0.5f, 0.42f))
        assertEquals(0.84f, s.lean, 1e-4f)
        assertEquals(1, s.leanDirection)
        assertTrue(s.isUnstable)
        assertFalse(s.isToppling)
    }

    @Test
    fun squaresOnNarrowOffsetPenaltyTopple() {
        // Penalty spans 0.05..0.95; squares centered at 0 sit past its left edge.
        val blocks = List(4) { sq() } + pen(0.9f, 0.5f, 0.5f) + List(3) { sq() }
        val s = Physics.analyze(blocks)
        assertTrue(s.isToppling)
        assertEquals(4, s.failIndex)
        assertEquals(-1, s.toppleDirection)

        val (left, fell) = Tower(blocks).topple(s)
        assertEquals(5, left.size)
        assertEquals(3, fell.size)
        assertFalse(left.stability.isToppling)
    }

    @Test
    fun lowestFailingSupportWins() {
        // Two failures: the lower one should decide what falls.
        // Index 1 fails (load left of its edge) and index 2 fails (load right of it).
        val blocks = listOf(sq(), pen(0.6f, 0.4f, 0.3f), sq(0.6f, -0.2f), pen(0.4f, 0.3f, 0.5f))
        val s = Physics.analyze(blocks)
        assertEquals(1, s.failIndex)
        assertEquals(-1, s.toppleDirection)
    }

    @Test
    fun stabilizeAndUndoRecoverTheTower() {
        val blocks = List(4) { sq() } + pen(0.9f, 0.5f, 0.5f) + List(3) { sq() }
        val tower = Tower(blocks)
        assertTrue(tower.stability.isToppling)
        assertEquals(0f, tower.stabilized().stability.lean)
        val undone = tower.withoutLastPenalty()
        assertEquals(7, undone.size)
        assertFalse(undone.hasPenalty)
        assertFalse(undone.stability.isToppling)
    }

    @Test
    fun pointsFollowTimeFocused() {
        assertEquals(300, Scoring.completedPoints(Preset.FIVE_MIN))
        assertEquals(180, Scoring.completedPoints(Preset.THREE_MIN))
        assertEquals(60, Scoring.completedPoints(Preset.ONE_MIN))
        assertEquals(10, Scoring.completedPoints(Preset.TEST))
        assertEquals(50, Scoring.abandonedPoints(Preset.FIVE_MIN, 101_000))
        assertEquals(150, Scoring.abandonedPoints(Preset.FIVE_MIN, 10_000_000))
        assertEquals(0, Scoring.abandonedPoints(Preset.TEST, 1_500))
    }

    @Test
    fun blockSizeFollowsPreset() {
        val big = BlockFactory.square(1, Preset.FIVE_MIN, 0)
        val tiny = BlockFactory.square(2, Preset.TEST, 0)
        assertTrue(big.width > tiny.width)
        repeat(200) {
            val p = BlockFactory.penalty(3, Preset.ONE_MIN, 0, Random(it))
            assertTrue(p.width in 0.54f..0.96f)
            assertTrue(p.height in 0.21f..0.42f)
            assertTrue(kotlin.math.abs(p.x) in 0.072f..0.3f)
        }
    }

    @Test
    fun leavingTheAppAbandonsButLockingDoesNot() {
        val sprint = ActiveSprint(Preset.ONE_MIN, startedAtMs = 0)
        assertEquals(RecoveryAction.ABANDON, SprintRules.recover(sprint, 30_000, stoppedWhileLocked = false))
        assertEquals(RecoveryAction.RESUME, SprintRules.recover(sprint, 30_000, stoppedWhileLocked = true))
        assertEquals(RecoveryAction.COMPLETE, SprintRules.recover(sprint, 61_000, stoppedWhileLocked = true))

        val out = SprintRules.abandon(sprint, 40_000, AbandonReason.LEFT_APP, 9, Random(1))
        assertEquals(BlockKind.PENALTY, out.block.kind)
        assertEquals(20, out.block.points)
        assertEquals(AbandonReason.LEFT_APP, out.reason)
    }

    @Test
    fun interstitialEverySecondSprint() {
        val policy = InterstitialPolicy()
        assertFalse(policy.shouldShowAfterSprint(1))
        assertTrue(policy.shouldShowAfterSprint(2))
    }
}
