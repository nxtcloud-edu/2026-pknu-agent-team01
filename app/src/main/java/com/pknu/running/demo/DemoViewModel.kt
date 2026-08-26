package com.pknu.running.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pknu.running.tracking.RunningTracker
import com.pknu.running.tracking.location.FakeLocationProvider
import com.pknu.running.tracking.location.LocationProvider
import com.pknu.running.tracking.model.RunningEvent
import com.pknu.running.tracking.model.RunningMetrics
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 데모 화면용 ViewModel. RunningTracker를 위치 제공자와 연결하고 UI 상태를 노출한다.
 *
 * 이 클래스는 기능 1 자체가 아니라, 기능 1을 눈으로 확인하기 위한 얇은 데모 래퍼다.
 */
class DemoViewModel : ViewModel() {

    private val tracker = RunningTracker(scope = viewModelScope)

    val metrics: StateFlow<RunningMetrics> = tracker.metrics
    val state: StateFlow<RunningState> = tracker.state

    private val _eventLog = MutableStateFlow<List<String>>(emptyList())
    val eventLog: StateFlow<List<String>> = _eventLog.asStateFlow()

    private val _summaryText = MutableStateFlow<String?>(null)
    val summaryText: StateFlow<String?> = _summaryText.asStateFlow()

    private var provider: LocationProvider? = null
    private var feedJob: Job? = null
    private var providerJob: Job? = null

    init {
        // 이벤트를 사람이 읽을 수 있는 로그로 변환
        viewModelScope.launch {
            tracker.events.collect { event ->
                _eventLog.value = _eventLog.value + formatEvent(event)
            }
        }
    }

    /**
     * Fake GPS 시나리오로 데모 러닝을 시작한다.
     * 6'30" → 5'30" → 4'50" 로 점점 빨라지는 시나리오 (application-design.md 예시 기반).
     */
    fun startFakeRun() {
        reset()
        val fake = FakeLocationProvider(
            segments = listOf(
                FakeLocationProvider.Segment(durationSec = 60, paceSecPerKm = 390.0),  // 6'30"
                FakeLocationProvider.Segment(durationSec = 60, paceSecPerKm = 330.0),  // 5'30"
                FakeLocationProvider.Segment(durationSec = 90, paceSecPerKm = 290.0),  // 4'50"
            ),
            emitIntervalMs = 1000,
            realDelay = true, // 데모에서 실제 시간 흐름으로 관찰
        )
        provider = fake
        connectProvider(fake)
        tracker.start(RunningTarget(distanceMeter = 1000.0, paceSecPerKm = 330.0))
    }

    /**
     * 외부에서 주입한 실제 LocationProvider(FusedLocationProvider 등)로 러닝을 시작한다.
     */
    fun startRealRun(realProvider: LocationProvider, target: RunningTarget) {
        reset()
        provider = realProvider
        connectProvider(realProvider)
        tracker.start(target)
    }

    private fun connectProvider(p: LocationProvider) {
        feedJob = viewModelScope.launch {
            p.samples.collect { tracker.onLocation(it) }
        }
        providerJob = viewModelScope.launch { p.start() }
    }

    fun pause() = tracker.pause()

    fun resume() = tracker.resume()

    fun finish() {
        // 시작 전(READY)이거나 이미 종료된 상태면 무시한다.
        if (tracker.state.value != RunningState.RUNNING &&
            tracker.state.value != RunningState.PAUSED
        ) {
            return
        }
        provider?.stop()
        feedJob?.cancel()
        providerJob?.cancel()
        val summary = tracker.finish()
        _summaryText.value = buildString {
            appendLine("=== 러닝 요약 ===")
            appendLine("거리: ${"%.0f".format(summary.totalDistanceMeter)} m")
            appendLine("시간: ${formatDuration(summary.elapsedTimeSec)}")
            appendLine("평균 페이스: ${formatPace(summary.averagePaceSecPerKm)}")
            appendLine("최고 페이스: ${formatPace(summary.bestPaceSecPerKm)}")
            appendLine("이벤트 수: ${summary.events.size}")
        }
    }

    private fun reset() {
        provider?.stop()
        feedJob?.cancel()
        providerJob?.cancel()
        // 이미 러닝 중(RUNNING/PAUSED)이면 먼저 종료하여 tracker를 FINISHED 상태로 만든다.
        // 그래야 이어지는 start()의 전제조건(READY 또는 FINISHED)을 만족한다.
        if (tracker.state.value == RunningState.RUNNING ||
            tracker.state.value == RunningState.PAUSED
        ) {
            tracker.finish()
        }
        _eventLog.value = emptyList()
        _summaryText.value = null
    }

    private fun formatEvent(e: RunningEvent): String {
        val time = formatDuration(e.elapsedTimeSec)
        val extra = e.metadata.entries.joinToString(", ") { (k, v) ->
            when (v) {
                is Double -> "$k=${"%.0f".format(v)}"
                else -> "$k=$v"
            }
        }
        return "[$time] ${e.type}${if (extra.isNotBlank()) " ($extra)" else ""}"
    }

    override fun onCleared() {
        super.onCleared()
        provider?.stop()
    }

    companion object {
        fun formatDuration(totalSec: Long): String {
            val m = totalSec / 60
            val s = totalSec % 60
            return "%d:%02d".format(m, s)
        }

        fun formatPace(secPerKm: Double?): String {
            if (secPerKm == null || secPerKm.isInfinite() || secPerKm.isNaN()) return "--'--\""
            val m = (secPerKm / 60).toInt()
            val s = (secPerKm % 60).toInt()
            return "%d'%02d\"/km".format(m, s)
        }
    }
}
