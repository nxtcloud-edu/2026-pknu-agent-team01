package com.pknu.running.tracking.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceCalculatorTest {

    @Test
    fun `pace for 1km in 5 minutes is 300 sec per km`() {
        val pace = PaceCalculator.paceSecPerKm(distanceMeter = 1_000.0, elapsedSec = 300.0)
        assertThat(pace).isWithin(1e-6).of(300.0)
    }

    @Test
    fun `pace for 500m in 150 sec is 300 sec per km`() {
        val pace = PaceCalculator.paceSecPerKm(distanceMeter = 500.0, elapsedSec = 150.0)
        assertThat(pace).isWithin(1e-6).of(300.0)
    }

    @Test
    fun `zero distance returns null`() {
        assertThat(PaceCalculator.paceSecPerKm(0.0, 60.0)).isNull()
    }

    @Test
    fun `zero time returns null`() {
        assertThat(PaceCalculator.paceSecPerKm(100.0, 0.0)).isNull()
    }

    @Test
    fun `ema with null previous returns current`() {
        assertThat(PaceCalculator.ema(current = 300.0, previousSmoothed = null, alpha = 0.2))
            .isWithin(1e-6).of(300.0)
    }

    @Test
    fun `ema with null current keeps previous`() {
        assertThat(PaceCalculator.ema(current = null, previousSmoothed = 280.0, alpha = 0.2))
            .isWithin(1e-6).of(280.0)
    }

    @Test
    fun `ema blends current and previous`() {
        // 0.2*400 + 0.8*300 = 320
        assertThat(PaceCalculator.ema(current = 400.0, previousSmoothed = 300.0, alpha = 0.2))
            .isWithin(1e-6).of(320.0)
    }

    @Test
    fun `ema converges toward stable input`() {
        var s: Double? = null
        repeat(50) {
            s = PaceCalculator.ema(current = 300.0, previousSmoothed = s, alpha = 0.2)
        }
        assertThat(s!!).isWithin(0.5).of(300.0)
    }
}
