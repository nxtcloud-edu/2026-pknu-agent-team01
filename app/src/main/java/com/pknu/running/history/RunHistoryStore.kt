package com.pknu.running.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 러닝 기록을 날짜별로 영속 저장하는 저장소.
 *
 * SharedPreferences에 전체 기록을 JSON 배열로 저장한다(데모 규모에 충분).
 * 실제 서비스에서는 Room/DB로 대체할 수 있다. 이 저장소는 기능 4(기록/리포트)로
 * 이관되기 전까지의 로컬 임시 저장 역할이다.
 */
class RunHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 기록 1건을 추가한다. */
    fun add(entry: RunHistoryEntry) {
        val list = loadAll().toMutableList()
        list.add(entry)
        save(list)
    }

    /** 전체 기록을 종료 시각 내림차순으로 반환한다. */
    fun loadAll(): List<RunHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
            .sortedByDescending { it.epochMillis }
    }

    /** 특정 날짜("yyyy-MM-dd")의 기록만 반환한다. */
    fun loadByDate(dateKey: String): List<RunHistoryEntry> =
        loadAll().filter { it.dateKey == dateKey }

    /** 기록이 존재하는 날짜 키 집합을 반환한다 (캘린더 표시용). */
    fun datesWithRecords(): Set<String> =
        loadAll().map { it.dateKey }.toSet()

    /** 날짜별 총 러닝 시간(초) 합계를 반환한다 (캘린더 색 진하기용). */
    fun totalDurationByDate(): Map<String, Long> =
        loadAll().groupBy { it.dateKey }
            .mapValues { (_, list) -> list.sumOf { it.elapsedTimeSec } }

    /** 전체 기록 삭제 (테스트/초기화용). */
    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(list: List<RunHistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun toJson(e: RunHistoryEntry) = JSONObject().apply {
        put("epochMillis", e.epochMillis)
        put("dateKey", e.dateKey)
        put("totalDistanceMeter", e.totalDistanceMeter)
        put("elapsedTimeSec", e.elapsedTimeSec)
        put("averagePaceSecPerKm", e.averagePaceSecPerKm ?: JSONObject.NULL)
        put("bestPaceSecPerKm", e.bestPaceSecPerKm ?: JSONObject.NULL)
        put("eventCount", e.eventCount)
    }

    private fun fromJson(o: JSONObject) = RunHistoryEntry(
        epochMillis = o.getLong("epochMillis"),
        dateKey = o.getString("dateKey"),
        totalDistanceMeter = o.getDouble("totalDistanceMeter"),
        elapsedTimeSec = o.getLong("elapsedTimeSec"),
        averagePaceSecPerKm = if (o.isNull("averagePaceSecPerKm")) null else o.getDouble("averagePaceSecPerKm"),
        bestPaceSecPerKm = if (o.isNull("bestPaceSecPerKm")) null else o.getDouble("bestPaceSecPerKm"),
        eventCount = o.getInt("eventCount"),
    )

    companion object {
        private const val PREFS_NAME = "run_history"
        private const val KEY_ENTRIES = "entries"

        /** epoch millis를 로컬 날짜 키 "yyyy-MM-dd"로 변환한다. */
        fun dateKeyOf(epochMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMillis))
    }
}
