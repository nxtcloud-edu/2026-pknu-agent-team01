package com.pknu.running.tracking.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `same point returns zero`() {
        val d = GeoDistance.haversineMeter(35.1341, 129.1055, 35.1341, 129.1055)
        assertThat(d).isWithin(1e-6).of(0.0)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        // 위도 1도 차이는 대략 111.19 km
        val d = GeoDistance.haversineMeter(0.0, 0.0, 1.0, 0.0)
        assertThat(d).isWithin(500.0).of(111_190.0)
    }

    @Test
    fun `known short distance is accurate within tolerance`() {
        // 위도 0.0001도 ≈ 11.13 m
        val d = GeoDistance.haversineMeter(35.0, 129.0, 35.0001, 129.0)
        assertThat(d).isWithin(0.5).of(11.13)
    }
}
