package com.pknu.running.tracking

import com.pknu.running.tracking.event.EventDetector
import com.pknu.running.tracking.filter.LocationFilter
import com.pknu.running.tracking.math.PaceCalculator
import com.pknu.running.tracking.math.PaceWindow
import com.pknu.running.tracking.model.LocationSample
import com.pknu.running.tracking.model.RunRecordSummary
import com.pknu.running.tracking.model.RunningEvent
import com.pknu.running.tracking.model.RunningMetrics
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import com.pknu.running.tracking.model.TrackingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 러닝 세션 엔진. GPS 입력을 받아 실시간 [RunningMetrics]와 [RunningEvent]를 방출한다.
 *
 * 다른 기능(음악/TTS/리포트)은 [metrics], [events], [state]를 구독하여 사용한다.
 *
 * @param config 튜닝 파라미터
 * @param scope tick 코루틴을 실행할 스코프 (테스트에서는 test scope 주입)
 * @param nowMs 현재 시각 제공자 (테스트에서 가상 시계 주입 가능)
 */
class RunningTracker(
    private val config: TrackingConfig = TrackingConfig(),
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    private val _metrics = MutableStateFlow(RunningMetrics())
    val metrics: StateFlow<RunningMetrics> = _metrics.asStateFlow()

    private val _events = MutableSharedFlow<RunningEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RunningEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(RunningState.READY)
    val state: StateFlow<RunningState> = _state.asStateFlow()

    private lateinit var target: RunningTarget
    private lateinit var filter: LocationFilter
    private lateinit var detector: EventDetector
    private lateinit var paceWindow: PaceWindow

    private var tickJob: Job? = null

    // 누적 상태
    private var totalDistanceMeter = 0.0
    private var elapsedTimeSec = 0L
    private var smoothedPace: Double? = null
    private var bestPaceSecPerKm: Double? = null
    private var lastAccuracyMeter = 0f
    private val firedEvents = mutableListOf<RunningEvent>()

    /**
     * 러닝을 시작한다. 상태를 초기화하고 1초 tick 루프를 돌린다.
     *
     * 이미 러닝 중(RUNNING/PAUSED)일 때 다시 호출하면, 진행 중인 tick 루프를 멈추고
     * 세션을 처음부터 새로 시작한다 (재시작). 따라서 언제 호출해도 안전하다.
     */
    fun start(target: RunningTarget = RunningTarget()) {
        // 이전 세션이 아직 살아있으면 정리 후 재시작한다.
        tickJob?.cancel()
        tickJob = null

        this.target = target
        this.filter = LocationFilter(config)
        this.detector = EventDetector(config, target)
        this.paceWindow = PaceWindow(config.currentPaceWindowSec)

        totalDistanceMeter = 0.0
        elapsedTimeSec = 0L
        smoothedPace = null
        bestPaceSecPerKm = null
        lastAccuracyMeter = 0f
        firedEvents.clear()

        _state.value = RunningState.RUNNING
        emitMetrics()
        startTicking()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(config.metricsTickMs)
                if (_state.value != RunningState.RUNNING) continue
                onTick()
            }
        }
    }

    private fun onTick() {
        elapsedTimeSec += config.metricsTickMs / 1_000

        // 현재 페이스 윈도우에 현재 상태 반영
        paceWindow.add(elapsedTimeSec.toDouble(), totalDistanceMeter)
        val currentPace = paceWindow.currentPaceSecPerKm()
        smoothedPace = PaceCalculator.ema(currentPace, smoothedPace, config.emaAlpha)

        val avgPace = PaceCalculator.paceSecPerKm(totalDistanceMeter, elapsedTimeSec.toDouble())
        if (currentPace != null) {
            bestPaceSecPerKm = when (val b = bestPaceSecPerKm) {
                null -> currentPace
                else -> minOf(b, currentPace) // 페이스는 작을수록 빠름
            }
        }

        val m = RunningMetrics(
            timestampMs = nowMs(),
            elapsedTimeSec = elapsedTimeSec,
            totalDistanceMeter = totalDistanceMeter,
            currentPaceSecPerKm = currentPace,
            smoothedPaceSecPerKm = smoothedPace,
            averagePaceSecPerKm = avgPace,
            gpsAccuracyMeter = lastAccuracyMeter,
            state = RunningState.RUNNING,
        )
        _metrics.value = m

        val detected = detector.detect(m)
        for (event in detected) {
            firedEvents.add(event)
            _events.tryEmit(event)
        }
    }

    private fun emitMetrics() {
        _metrics.value = RunningMetrics(
            timestampMs = nowMs(),
            elapsedTimeSec = elapsedTimeSec,
            totalDistanceMeter = totalDistanceMeter,
            currentPaceSecPerKm = paceWindow.currentPaceSecPerKm(),
            smoothedPaceSecPerKm = smoothedPace,
            averagePaceSecPerKm = PaceCalculator.paceSecPerKm(totalDistanceMeter, elapsedTimeSec.toDouble()),
            gpsAccuracyMeter = lastAccuracyMeter,
            state = _state.value,
        )
    }

    /**
     * 위치 샘플을 입력한다. LocationProvider가 호출한다.
     * RUNNING 상태에서만 거리에 반영한다 (PAUSED에서는 무시).
     */
    fun onLocation(sample: LocationSample) {
        if (_state.value != RunningState.RUNNING) return
        lastAccuracyMeter = sample.accuracyMeter
        when (val result = filter.accept(sample)) {
            is LocationFilter.Result.Accepted -> {
                totalDistanceMeter += result.deltaMeter
            }
            else -> {
                // Anchor / Rejected*: 거리 누적 없음
            }
        }
    }

    /** 일시정지. 시간/거리 누적과 이벤트 판정을 중지한다 (FR-04). */
    fun pause() {
        if (_state.value != RunningState.RUNNING) return
        _state.value = RunningState.PAUSED
        emitMetrics()
    }

    /** 재개. 필터 앵커를 리셋하여 정지 구간이 거리로 잡히지 않게 한다. */
    fun resume() {
        if (_state.value != RunningState.PAUSED) return
        filter.reset()
        _state.value = RunningState.RUNNING
        emitMetrics()
    }

    /**
     * 러닝을 종료하고 요약을 반환한다. DB 저장은 하지 않는다 (리포트 담당이 처리).
     */
    fun finish(): RunRecordSummary {
        tickJob?.cancel()
        tickJob = null
        _state.value = RunningState.FINISHED
        emitMetrics()

        return RunRecordSummary(
            totalDistanceMeter = totalDistanceMeter,
            elapsedTimeSec = elapsedTimeSec,
            averagePaceSecPerKm = PaceCalculator.paceSecPerKm(totalDistanceMeter, elapsedTimeSec.toDouble()),
            bestPaceSecPerKm = bestPaceSecPerKm,
            events = firedEvents.toList(),
        )
    }
}
