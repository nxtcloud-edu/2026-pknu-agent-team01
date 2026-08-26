package com.pknu.running.tracking.location

import com.pknu.running.tracking.model.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * GPS 위치 소스 추상화. 실제 구현([FusedLocationProvider])과 시뮬레이션 구현
 * ([FakeLocationProvider])이 이 인터페이스를 구현한다.
 */
interface LocationProvider {

    /** 위치 샘플 스트림. */
    val samples: Flow<LocationSample>

    /** 위치 수신을 시작한다. */
    suspend fun start()

    /** 위치 수신을 중지한다. */
    fun stop()
}
