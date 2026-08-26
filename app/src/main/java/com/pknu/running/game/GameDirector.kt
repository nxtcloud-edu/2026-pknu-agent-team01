package com.pknu.running.game

import com.pknu.running.game.model.GameEvent
import com.pknu.running.game.model.IntervalPhase
import com.pknu.running.game.model.ModeConfig
import com.pknu.running.game.model.Narration
import com.pknu.running.tracking.RunningTracker
import com.pknu.running.tracking.model.RunningEvent
import com.pknu.running.tracking.model.RunningEventType
import com.pknu.running.tracking.model.RunningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 게임/연출 총괄 (기능 3의 조립점).
 *
 * 기능 1의 [RunningTracker] flow(metrics/events/state)를 구독하여
 * - 공통 러닝 이벤트(5분/1km/페이스저하/마지막500m)를 모드별 나레이션으로 변환하고
 * - 모드 엔진(인터벌 구간, 국가대표 순위)과 랜덤 이벤트 엔진을 구동하며
 * - 결과를 [narrations]와 [gameEvents]로 방출한다.
 *
 * 나레이션은 쿨다운(반복 억제)과 우선순위를 적용한다.
 *
 * @param tracker 소비할 러닝 트래커
 * @param scope 구독 코루틴 스코프
 * @param nowSecProvider 현재 경과초 제공자 (미사용 시 metrics 기준)
 */
class GameDirector(
    private val tracker: RunningTracker,
    private val scope: CoroutineScope,
) {

    private val _narrations = MutableSharedFlow<Narration>(extraBufferCapacity = 32)
    val narrations: SharedFlow<Narration> = _narrations.asSharedFlow()

    private val _gameEvents = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32)
    val gameEvents: SharedFlow<GameEvent> = _gameEvents.asSharedFlow()

    private lateinit var config: ModeConfig
    private lateinit var modeEngine: ModeEngine
    private lateinit var randomEngine: RandomEventEngine

    private var eventsJob: Job? = null
    private var metricsJob: Job? = null

    private var lastTickSec: Long = -1
    private val lastNarrationByKey = mutableMapOf<String, Long>()
    private var currentElapsedSec: Long = 0

    /** 나레이션 쿨다운 (초). 같은 cooldownKey는 이 시간 안에 재생하지 않는다. */
    private val narrationCooldownSec = 6L

    /**
     * 선택된 모드로 연출을 시작한다. 트래커 start와 함께 호출한다.
     */
    fun start(config: ModeConfig, randomEngine: RandomEventEngine = RandomEventEngine()) {
        this.config = config
        this.modeEngine = ModeEngine(config)
        this.randomEngine = randomEngine
        this.modeEngine.reset()
        this.randomEngine.reset()
        lastTickSec = -1
        lastNarrationByKey.clear()
        currentElapsedSec = 0

        subscribe()

        // 시작하자마자 1번: 모드 시작 나레이션 (TTS + 로그로 방출)
        narrate(TtsLibrary.modeStart(config.mode))
    }

    private fun subscribe() {
        eventsJob?.cancel()
        metricsJob?.cancel()

        // 기능 1의 러닝 이벤트 → 모드별 나레이션
        eventsJob = scope.launch {
            tracker.events.collect { handleRunningEvent(it) }
        }

        // 매 tick(metrics) → 모드 엔진 + 랜덤 이벤트
        metricsJob = scope.launch {
            tracker.metrics.collect { m ->
                if (m.state != RunningState.RUNNING) return@collect
                val sec = m.elapsedTimeSec
                if (sec == lastTickSec) return@collect // 같은 초 중복 방지
                lastTickSec = sec
                currentElapsedSec = sec

                // 모드 엔진
                modeEngine.tick(sec, m).forEach { handleModeEvent(it) }

                // 랜덤 이벤트
                if (config.randomEventEnabled) {
                    randomEngine.tick(sec)?.let { type ->
                        emit(GameEvent.Random(type))
                        narrate(TtsLibrary.randomEvent(type))
                    }
                }
            }
        }
    }

    private fun handleRunningEvent(e: RunningEvent) {
        val narration = when (e.type) {
            RunningEventType.TIME_MILESTONE -> {
                val minute = (e.metadata["minute"] as? Int) ?: (e.elapsedTimeSec / 60).toInt()
                val pace = e.metadata["currentPace"] as? Double
                TtsLibrary.timeMilestone(minute, pace, config.mode)
            }
            RunningEventType.DISTANCE_MILESTONE -> {
                val km = (e.metadata["km"] as? Int) ?: (e.totalDistanceMeter / 1000).toInt()
                TtsLibrary.distanceMilestone(km, config.mode)
            }
            RunningEventType.PACE_DROP -> TtsLibrary.paceDrop(config.mode)
            RunningEventType.LAST_STRETCH -> TtsLibrary.lastStretch(config.mode)
        }
        narrate(narration)
    }

    private fun handleModeEvent(event: GameEvent) {
        when (event) {
            is GameEvent.IntervalChanged -> {
                emit(event)
                val n = when (event.phase) {
                    IntervalPhase.WORK -> TtsLibrary.intervalWork(event.setIndex, event.totalSets)
                    IntervalPhase.RECOVERY -> TtsLibrary.intervalRecovery(event.setIndex, event.totalSets)
                    IntervalPhase.DONE -> TtsLibrary.intervalDone()
                }
                narrate(n)
            }
            is GameEvent.RankUpdate -> {
                emit(event)
                narrate(TtsLibrary.rankUpdate(event.rank, event.totalRunners, event.overtook))
            }
            else -> emit(event)
        }
    }

    /** 나레이션을 쿨다운 검사 후 방출한다. */
    private fun narrate(n: Narration) {
        val key = n.cooldownKey
        if (key != null) {
            val last = lastNarrationByKey[key]
            if (last != null && currentElapsedSec - last < narrationCooldownSec) return
            lastNarrationByKey[key] = currentElapsedSec
        }
        emit(GameEvent.Narrate(n))
        _narrations.tryEmit(n)
    }

    private fun emit(event: GameEvent) {
        _gameEvents.tryEmit(event)
    }

    /** 연출 종료. */
    fun stop() {
        eventsJob?.cancel()
        metricsJob?.cancel()
        eventsJob = null
        metricsJob = null
    }
}
