package app.sprintstacker.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sprintstacker.SprintStackerApp
import app.sprintstacker.core.AbandonReason
import app.sprintstacker.core.ActiveSprint
import app.sprintstacker.core.Block
import app.sprintstacker.core.BlockKind
import app.sprintstacker.core.InterstitialPolicy
import app.sprintstacker.core.Physics
import app.sprintstacker.core.Preset
import app.sprintstacker.core.RecoveryAction
import app.sprintstacker.core.SprintRules
import app.sprintstacker.core.Stability
import app.sprintstacker.core.Tower
import app.sprintstacker.data.GameStateEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.random.Random

enum class Phase { Idle, Running, Dropping, Tipping, Falling }

enum class RewardKind { STABILIZE, UNDO_PENALTY }

sealed interface AdRequest {
    data object Interstitial : AdRequest
    data class Rewarded(val kind: RewardKind) : AdRequest
}

enum class Tone { Good, Bad, Neutral }

data class Notice(val id: Long, val text: String, val tone: Tone)

/** A block sliding off the tower. [baseY] is where its bottom edge was. */
data class FallingBlock(val block: Block, val baseY: Float, val direction: Int)

data class GameUiState(
    val loaded: Boolean = false,
    val tower: Tower = Tower(),
    val score: Int = 0,
    val best: Int = 0,
    val bestHeight: Float = 0f,
    val preset: Preset = Preset.FIVE_MIN,
    val sprint: ActiveSprint? = null,
    val nowMs: Long = 0L,
    val phase: Phase = Phase.Idle,
    /** Block on its way down. It is not in [tower] until it lands. */
    val incoming: Block? = null,
    val tipping: Stability? = null,
    val falling: List<FallingBlock> = emptyList(),
    val fallKey: Long = 0L,
    val warnDismissed: Boolean = false,
    val notice: Notice? = null,
    val vibrate: Boolean = true,
) {
    val stability: Stability get() = tipping ?: tower.stability
    val showWarning: Boolean get() = phase == Phase.Idle && stability.isUnstable && !warnDismissed
    val progress: Float get() = sprint?.progress(nowMs) ?: 0f
    val remainingSeconds: Int
        get() = sprint?.let { ((it.preset.durationMs - it.elapsedMs(nowMs) + 999) / 1000).toInt() }
            ?: preset.seconds
}

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as SprintStackerApp).repository
    private val random = Random.Default
    private val interstitials = InterstitialPolicy()
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _ads = MutableSharedFlow<AdRequest>(extraBufferCapacity = 4)
    val ads: SharedFlow<AdRequest> = _ads.asSharedFlow()

    private var completedSinceAd = 0
    private var stoppedWhileLocked = false
    private var ticker: Job? = null
    private var landingNotice: Notice? = null

    init {
        viewModelScope.launch { load() }
    }

    private fun now() = System.currentTimeMillis()
    private fun newId() = random.nextLong() and Long.MAX_VALUE
    private fun makeNotice(text: String, tone: Tone) = Notice(newId(), text, tone)

    private suspend fun load() {
        val (saved, blocks) = repository.load()
        completedSinceAd = saved.completedSinceAd
        stoppedWhileLocked = saved.stoppedWhileLocked
        val preset = runCatching { Preset.valueOf(saved.preset) }.getOrDefault(Preset.FIVE_MIN)
        val sprint = saved.sprintPreset
            ?.let { runCatching { Preset.valueOf(it) }.getOrNull() }
            ?.let { ActiveSprint(it, saved.sprintStartedAt ?: now()) }
        _state.value = GameUiState(
            loaded = true,
            tower = Tower(blocks),
            score = saved.score,
            best = saved.best,
            bestHeight = saved.bestHeight,
            preset = preset,
            nowMs = now(),
            vibrate = saved.vibrate,
        )
        if (sprint == null) {
            settle()
            return
        }
        when (SprintRules.recover(sprint, now(), stoppedWhileLocked)) {
            RecoveryAction.ABANDON -> abandon(
                sprint, AbandonReason.LEFT_APP, animate = true,
                notice = makeNotice("You left the app mid-sprint, so it counted as giving up.", Tone.Bad),
            )
            RecoveryAction.COMPLETE -> complete(sprint)
            RecoveryAction.RESUME -> {
                _state.update { it.copy(sprint = sprint, phase = Phase.Running) }
                startTicker()
            }
        }
    }

    // ---- Sprint ----

    fun selectPreset(preset: Preset) {
        if (_state.value.phase != Phase.Idle) return
        _state.update { it.copy(preset = preset) }
        save()
    }

    fun startSprint() {
        val s = _state.value
        if (!s.loaded || s.phase != Phase.Idle || s.incoming != null) return
        _state.update { it.copy(sprint = ActiveSprint(it.preset, now()), nowMs = now(), phase = Phase.Running) }
        save()
        startTicker()
    }

    fun giveUp() {
        val sprint = _state.value.sprint ?: return
        abandon(sprint, AbandonReason.GAVE_UP, animate = true, notice = null)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                val sprint = _state.value.sprint ?: break
                val t = now()
                _state.update { it.copy(nowMs = t) }
                if (sprint.isFinished(t)) {
                    complete(sprint)
                    break
                }
                delay(200)
            }
        }
    }

    private fun complete(sprint: ActiveSprint) {
        ticker?.cancel()
        val squares = _state.value.tower.blocks.count { it.kind == BlockKind.SQUARE }
        val out = SprintRules.complete(sprint, newId(), squares)
        completedSinceAd++
        landingNotice = makeNotice("+${out.block.points} · Sprint complete", Tone.Good)
        deliver(out.block, animate = true)
    }

    private fun abandon(sprint: ActiveSprint, reason: AbandonReason, animate: Boolean, notice: Notice?) {
        ticker?.cancel()
        val out = SprintRules.abandon(sprint, now(), reason, newId(), random)
        landingNotice = notice ?: makeNotice("Penalty block · +${out.block.points} pts", Tone.Bad)
        deliver(out.block, animate)
    }

    private fun deliver(block: Block, animate: Boolean) {
        _state.update { it.copy(sprint = null, nowMs = now(), phase = Phase.Dropping, incoming = block) }
        save()
        if (!animate) land(block)
    }

    /** Called by the playfield when the drop animation reaches the tower. */
    fun onLanded(blockId: Long) {
        val block = _state.value.incoming ?: return
        if (block.id == blockId) land(block)
    }

    private fun land(block: Block) {
        _state.update {
            val tower = it.tower.add(block)
            val score = it.score + block.points
            it.copy(
                tower = tower,
                score = score,
                best = max(it.best, score),
                bestHeight = max(it.bestHeight, tower.height),
                incoming = null,
                warnDismissed = false,
                notice = landingNotice ?: it.notice,
            )
        }
        landingNotice = null
        settle()
        if (block.kind == BlockKind.SQUARE && _state.value.phase == Phase.Idle &&
            interstitials.shouldShowAfterSprint(completedSinceAd)
        ) {
            completedSinceAd = 0
            _ads.tryEmit(AdRequest.Interstitial)
        }
        save()
    }

    /** Re-checks balance and moves to Tipping or Idle. */
    private fun settle() {
        _state.update {
            val stability = it.tower.stability
            if (stability.isToppling) it.copy(phase = Phase.Tipping, tipping = stability)
            else it.copy(phase = Phase.Idle, tipping = null)
        }
    }

    // ---- Topple and rewards ----

    fun letFall() {
        val s = _state.value
        val stability = s.tipping ?: return
        val bases = Physics.baseHeights(s.tower.blocks)
        val (left, fell) = s.tower.topple(stability)
        val lost = fell.sumOf { it.points }
        _state.update {
            it.copy(
                tower = left,
                score = max(0, it.score - lost),
                tipping = null,
                phase = Phase.Falling,
                falling = fell.mapIndexed { i, b ->
                    FallingBlock(b, bases[stability.failIndex + 1 + i], stability.toppleDirection)
                },
                fallKey = newId(),
                notice = makeNotice(
                    "Lost ${fell.size} block${if (fell.size == 1) "" else "s"} · −$lost pts",
                    Tone.Bad,
                ),
            )
        }
        save()
    }

    /** Called by the playfield once every falling block has left the screen. */
    fun onFallFinished() {
        _state.update { it.copy(falling = emptyList()) }
        settle()
    }

    fun requestReward(kind: RewardKind) {
        _ads.tryEmit(AdRequest.Rewarded(kind))
    }

    fun onRewardEarned(kind: RewardKind) {
        _state.update {
            val tower = when (kind) {
                RewardKind.STABILIZE -> it.tower.stabilized()
                RewardKind.UNDO_PENALTY -> it.tower.withoutLastPenalty()
            }
            val text = if (kind == RewardKind.STABILIZE) "Tower stabilized" else "Last penalty block removed"
            it.copy(tower = tower, tipping = null, warnDismissed = false, notice = makeNotice(text, Tone.Good))
        }
        settle()
        save()
    }

    fun onRewardUnavailable() {
        _state.update { it.copy(notice = makeNotice("No video is ready right now. Try again in a moment.", Tone.Neutral)) }
    }

    fun dismissWarning() {
        _state.update { it.copy(warnDismissed = true) }
    }

    fun clearNotice(id: Long) {
        _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    // ---- Board and settings ----

    fun clearBoard() {
        val s = _state.value
        if (s.phase != Phase.Idle) return
        knockDown()
        _state.update { it.copy(score = 0, notice = makeNotice("Board cleared", Tone.Neutral)) }
        _ads.tryEmit(AdRequest.Interstitial)
        save()
    }

    fun resetAllData() {
        if (_state.value.phase != Phase.Idle) return
        knockDown()
        completedSinceAd = 0
        _state.update { it.copy(score = 0, best = 0, bestHeight = 0f, notice = makeNotice("All data reset", Tone.Neutral)) }
        save()
    }

    private fun knockDown() {
        _state.update {
            val bases = Physics.baseHeights(it.tower.blocks)
            it.copy(
                tower = Tower(),
                tipping = null,
                phase = if (it.tower.size > 0) Phase.Falling else Phase.Idle,
                falling = it.tower.blocks.mapIndexed { i, b -> FallingBlock(b, bases[i], 0) },
                fallKey = newId(),
            )
        }
    }

    fun setVibrate(on: Boolean) {
        _state.update { it.copy(vibrate = on) }
        save()
    }

    // ---- App lifecycle ----

    /**
     * The whole app went to the background. Leaving it (Home, another app,
     * Recents) gives up the sprint; a locked screen does not.
     */
    fun onAppStopped(screenInteractive: Boolean) {
        val s = _state.value
        if (!s.loaded) return
        val sprint = s.sprint ?: return
        if (screenInteractive) {
            abandon(
                sprint, AbandonReason.LEFT_APP, animate = false,
                notice = makeNotice("You left the app mid-sprint, so it counted as giving up.", Tone.Bad),
            )
        } else {
            stoppedWhileLocked = true
            save()
        }
    }

    fun onAppStarted() {
        if (!_state.value.loaded) return
        if (stoppedWhileLocked) {
            stoppedWhileLocked = false
            save()
        }
        val sprint = _state.value.sprint ?: return
        if (sprint.isFinished(now())) complete(sprint) else startTicker()
    }

    // ---- Persistence ----

    private fun save() {
        val s = _state.value
        if (!s.loaded) return
        // An incoming block is saved as already landed, so a process death
        // mid-drop cannot lose it.
        val pending = s.incoming
        val score = s.score + (pending?.points ?: 0)
        val entity = GameStateEntity(
            score = score,
            best = max(s.best, score),
            bestHeight = s.bestHeight,
            preset = s.preset.name,
            completedSinceAd = completedSinceAd,
            sprintPreset = s.sprint?.preset?.name,
            sprintStartedAt = s.sprint?.startedAtMs,
            stoppedWhileLocked = stoppedWhileLocked,
            vibrate = s.vibrate,
        )
        val blocks = s.tower.blocks + listOfNotNull(pending)
        viewModelScope.launch {
            saveMutex.withLock { repository.save(entity, blocks) }
        }
    }
}
