package com.pknu.running.tracking

import com.google.common.truth.Truth.assertThat
import com.pknu.running.tracking.location.FakeLocationProvider
import com.pknu.running.tracking.model.LocationSample
import com.pknu.running.tracking.model.RunningEvent
import com.pknu.running.tracking.model.RunningEventType
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import com.pknu.running.tracking.model.TrackingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * RunningTracker 통합 테스트.
 *
 * 주의: RunningTracker의 tick 루프는 finish() 전까지 무한히 도는 것이 정상 동작이다.
 * 따라서 테스트에서는 advanceUntilIdle()을 쓰지 않고 advanceTimeBy()로 정해진
 * 가상 시간만 진행한 뒤 finish()로 루프를 종료하고 수집 job을 취소한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunningTrackerTest {

    private val config = TrackingConfig()

    @Test
    fun `steady run accumulates distance and produces average pace`() = runTest {
        val tracker = RunningTracker(config, this) { testScheduler.currentTime }

        // 300초 동안 5'00"/km (=300 sec/km) → 1000m
        val provider = FakeLocationProvider(
            segments = listOf(FakeLocationProvider.Segment(durationSec = 300, paceSecPerKm = 300.0)),
            emitIntervalMs = 1000,
        )

        val collected = mutableListOf<RunningEvent>()
        val eventJob: Job = launch { tracker.events.collect { collected.add(it) } }
        val feedJob: Job = launch { provider.samples.collect { tracker.onLocation(it) } }
        runCurrent()

        tracker.start(RunningTarget(distanceMeter = 1000.0, paceSecPerKm = 300.0))
        launch { provider.start() }

        advanceTimeBy(301_000)
        runCurrent()

        val summary = tracker.finish()

        assertThat(summary.totalDistanceMeter).isWithin(30.0).of(1000.0)
        assertThat(summary.averagePaceSecPerKm!!).isWithin(15.0).of(300.0)

        eventJob.cancel()
        feedJob.cancel()
    }

    @Test
    fun `time distance and last stretch milestones fire during run`() = runTest {
        val tracker = RunningTracker(config, this) { testScheduler.currentTime }

        val provider = FakeLocationProvider(
            segments = listOf(FakeLocationProvider.Segment(durationSec = 360, paceSecPerKm = 300.0)),
            emitIntervalMs = 1000,
        )

        val collected = mutableListOf<RunningEvent>()
        val eventJob = launch { tracker.events.collect { collected.add(it) } }
        val feedJob = launch { provider.samples.collect { tracker.onLocation(it) } }
        runCurrent()

        tracker.start(RunningTarget(distanceMeter = 1200.0))
        launch { provider.start() }

        advanceTimeBy(361_000)
        runCurrent()
        tracker.finish()

        val types = collected.map { it.type }
        assertThat(types).contains(RunningEventType.TIME_MILESTONE)      // 5분 경과
        assertThat(types).contains(RunningEventType.DISTANCE_MILESTONE)  // 1km 통과
        assertThat(types).contains(RunningEventType.LAST_STRETCH)        // 목표 1200m의 마지막 500m 진입

        eventJob.cancel()
        feedJob.cancel()
    }

    @Test
    fun `pause stops time and distance accumulation`() = runTest {
        val tracker = RunningTracker(config, this) { testScheduler.currentTime }

        tracker.start(RunningTarget())

        advanceTimeBy(10_000)
        runCurrent()
        val elapsedBeforePause = tracker.metrics.value.elapsedTimeSec

        tracker.pause()
        assertThat(tracker.state.value).isEqualTo(RunningState.PAUSED)

        // pause 상태에서 시간 진행 → 경과시간 누적 안 됨
        advanceTimeBy(20_000)
        runCurrent()
        assertThat(tracker.metrics.value.elapsedTimeSec).isEqualTo(elapsedBeforePause)

        // pause 중 위치가 들어와도 거리 반영 안 됨
        tracker.onLocation(LocationSample(35.0, 129.0, 30_000, 5f))
        tracker.onLocation(LocationSample(35.001, 129.0, 31_000, 5f))
        assertThat(tracker.metrics.value.totalDistanceMeter).isEqualTo(0.0)

        tracker.resume()
        assertThat(tracker.state.value).isEqualTo(RunningState.RUNNING)

        tracker.finish()
    }

    @Test
    fun `state transitions follow lifecycle`() = runTest {
        val tracker = RunningTracker(config, this) { testScheduler.currentTime }

        assertThat(tracker.state.value).isEqualTo(RunningState.READY)
        tracker.start()
        assertThat(tracker.state.value).isEqualTo(RunningState.RUNNING)
        tracker.pause()
        assertThat(tracker.state.value).isEqualTo(RunningState.PAUSED)
        tracker.resume()
        assertThat(tracker.state.value).isEqualTo(RunningState.RUNNING)
        val summary = tracker.finish()
        assertThat(tracker.state.value).isEqualTo(RunningState.FINISHED)
        assertThat(summary).isNotNull()
    }
}
