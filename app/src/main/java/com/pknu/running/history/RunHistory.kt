package com.pknu.running.history

/**
 * 캘린더에 표시할 러닝 기록 1건 (요약 수치만 저장).
 *
 * @property epochMillis 러닝 종료 시각 (정렬/표시용)
 * @property dateKey 로컬 날짜 키 "yyyy-MM-dd"
 * @property totalDistanceMeter 총 거리
 * @property elapsedTimeSec 경과 시간
 * @property averagePaceSecPerKm 평균 페이스 (없으면 null)
 * @property bestPaceSecPerKm 최고 페이스 (없으면 null)
 * @property eventCount 발생한 이벤트 수
 */
data class RunHistoryEntry(
    val epochMillis: Long,
    val dateKey: String,
    val totalDistanceMeter: Double,
    val elapsedTimeSec: Long,
    val averagePaceSecPerKm: Double?,
    val bestPaceSecPerKm: Double?,
    val eventCount: Int,
)
