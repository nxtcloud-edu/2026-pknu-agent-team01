package com.pknu.running.tracking.filter

import com.google.common.truth.Truth.assertThat
import com.pknu.running.tracking.model.LocationSample
import com.pknu.running.tracking.model.TrackingConfig
import org.junit.Test

class LocationFilterTest {

    private val config = TrackingConfig()

    @Test
    fun `first sample is anchor`() {
        val filter = LocationFilter(config)
        val result = filter.accept(LocationSample(35.0, 129.0, 0, 5f))
        assertThat(result).isInstanceOf(LocationFilter.Result.Anchor::class.java)
    }

    @Test
    fun `low accuracy sample is rejected`() {
        val filter = LocationFilter(config)
        filter.accept(LocationSample(35.0, 129.0, 0, 5f))
        val result = filter.accept(LocationSample(35.0001, 129.0, 1000, 50f)) // > 30m
        assertThat(result).isInstanceOf(LocationFilter.Result.RejectedLowAccuracy::class.java)
    }

    @Test
    fun `normal running speed is accepted`() {
        val filter = LocationFilter(config)
        filter.accept(LocationSample(35.0, 129.0, 0, 5f))
        // 0.0001도 ≈ 11.13m 를 3초 → 약 3.7 m/s (정상 러닝)
        val result = filter.accept(LocationSample(35.0001, 129.0, 3000, 5f))
        assertThat(result).isInstanceOf(LocationFilter.Result.Accepted::class.java)
        val accepted = result as LocationFilter.Result.Accepted
        assertThat(accepted.deltaMeter).isWithin(0.5).of(11.13)
    }

    @Test
    fun `teleport jump is rejected`() {
        val filter = LocationFilter(config)
        filter.accept(LocationSample(35.0, 129.0, 0, 5f))
        // 0.01도 ≈ 1113m 를 1초 → 1113 m/s (비현실적)
        val result = filter.accept(LocationSample(35.01, 129.0, 1000, 5f))
        assertThat(result).isInstanceOf(LocationFilter.Result.RejectedJump::class.java)
    }

    @Test
    fun `non-increasing timestamp is rejected as jump`() {
        val filter = LocationFilter(config)
        filter.accept(LocationSample(35.0, 129.0, 5000, 5f))
        val result = filter.accept(LocationSample(35.0001, 129.0, 5000, 5f)) // dt = 0
        assertThat(result).isInstanceOf(LocationFilter.Result.RejectedJump::class.java)
    }

    @Test
    fun `low accuracy does not update anchor`() {
        val filter = LocationFilter(config)
        filter.accept(LocationSample(35.0, 129.0, 0, 5f))
        // 부정확 샘플은 앵커 갱신 안 함
        filter.accept(LocationSample(35.05, 129.0, 1000, 99f))
        // 다음 유효 샘플은 여전히 원래 앵커(35.0) 기준으로 계산
        val result = filter.accept(LocationSample(35.0001, 129.0, 5000, 5f))
        assertThat(result).isInstanceOf(LocationFilter.Result.Accepted::class.java)
        val accepted = result as LocationFilter.Result.Accepted
        assertThat(accepted.deltaMeter).isWithin(0.5).of(11.13)
    }
}
