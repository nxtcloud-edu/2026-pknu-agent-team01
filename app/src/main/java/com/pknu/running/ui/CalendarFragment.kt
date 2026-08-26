package com.pknu.running.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pknu.running.history.RunHistoryEntry
import com.pknu.running.history.RunHistoryStore
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 러닝 기록 캘린더 화면. (기존 CalendarActivity UI를 Fragment로 이전)
 */
class CalendarFragment : Fragment() {

    private lateinit var store: RunHistoryStore
    private val cal = Calendar.getInstance()
    private var selectedDateKey: String = ""
    private var durationByDate: Map<String, Long> = emptyMap()

    private lateinit var monthLabel: TextView
    private lateinit var grid: LinearLayout
    private lateinit var detailContainer: LinearLayout
    private lateinit var detailTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        store = RunHistoryStore(requireContext())
        selectedDateKey = RunHistoryStore.dateKeyOf(System.currentTimeMillis())
        cal.time = Date()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(24), ctx.dp(20), ctx.dp(24))
        }

        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.screenTitle("러닝 기록 캘린더").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            // 발표/데모용: 이번 달에 다양한 시간의 샘플 기록을 채운다
            addView(TextView(ctx).apply {
                text = "샘플"
                setTextColor(Palette.TEXT_MAIN)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(14), ctx.dp(7), ctx.dp(14), ctx.dp(7))
                background = ctx.strokeBg(Palette.SURFACE_2, ctx.dp(20).toFloat())
                isClickable = true
                setOnClickListener { fillSampleData() }
            })
        })
        root.addView(ctx.verticalSpacer(ctx.dp(16)))
        root.addView(buildLegend())
        root.addView(ctx.verticalSpacer(ctx.dp(16)))

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(navButton("‹") { changeMonth(-1) })
        monthLabel = TextView(ctx).apply {
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(monthLabel)
        header.addView(navButton("›") { changeMonth(1) })
        root.addView(header)
        root.addView(ctx.verticalSpacer(ctx.dp(14)))

        root.addView(buildWeekdayHeader())
        root.addView(ctx.verticalSpacer(ctx.dp(6)))

        grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(grid)
        root.addView(ctx.verticalSpacer(ctx.dp(24)))

        detailTitle = ctx.sectionLabel("")
        root.addView(detailTitle)
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        detailContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(detailContainer)

        return ScrollView(ctx).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(root)
        }
    }

    private fun buildWeekdayHeader(): View {
        val ctx = requireContext()
        val days = listOf("일", "월", "화", "수", "목", "금", "토")
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            days.forEachIndexed { i, d ->
                addView(TextView(ctx).apply {
                    text = d
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(
                        when (i) {
                            0 -> Palette.SUNDAY
                            6 -> Palette.SATURDAY
                            else -> Palette.TEXT_SUB
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
        }
    }

    // ---------------------------------------------------------------- 렌더링

    private fun refresh() {
        durationByDate = store.totalDurationByDate()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        monthLabel.text = "%d년 %d월".format(y, m)
        renderGrid()
        renderDetail()
    }

    private fun renderGrid() {
        val ctx = requireContext()
        grid.removeAllViews()

        val first = cal.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val firstWeekday = first.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        var day = 1
        var started = false
        for (week in 0 until 6) {
            if (day > daysInMonth) break
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(4); bottomMargin = ctx.dp(4) }
            }
            for (col in 0 until 7) {
                val cellIndex = week * 7 + col
                if (!started && cellIndex == firstWeekday) started = true
                if (!started || day > daysInMonth) {
                    row.addView(emptyCell())
                } else {
                    row.addView(dayCell(year, month, day))
                    day++
                }
            }
            grid.addView(row)
        }
    }

    private fun dayCell(year: Int, month: Int, day: Int): View {
        val ctx = requireContext()
        val dateKey = "%04d-%02d-%02d".format(year, month + 1, day)
        val durationSec = durationByDate[dateKey] ?: 0L
        val level = levelOf(durationSec) // 0=없음, 1=연함, 2=중간, 3=진함
        val isSelected = dateKey == selectedDateKey

        // 배경: 3단계 진하기. 기록 없으면 표면색.
        val cellBg = roundedBg(levelColor(level), ctx.dp(12).toFloat())
        // 선택 표시는 초록 테두리로 (배경 진하기와 겹쳐 보이게)
        if (isSelected) {
            (cellBg as android.graphics.drawable.GradientDrawable)
                .setStroke(ctx.dp(2), Palette.ACCENT)
        }

        // 글자색: 가장 진한 단계에서만 어두운 글자, 그 외엔 밝은 글자
        val dayTextColor = when {
            level >= 3 -> Palette.BG
            level == 0 && isSelected -> Palette.ACCENT
            else -> Palette.TEXT_MAIN
        }

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ctx.dp(48), 1f).apply {
                marginStart = ctx.dp(2); marginEnd = ctx.dp(2)
            }
            background = cellBg
            isClickable = true
            setOnClickListener {
                selectedDateKey = dateKey
                renderGrid()
                renderDetail()
            }
            addView(TextView(ctx).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(dayTextColor)
                if (isSelected || level > 0) setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    /**
     * 뛴 시간(초)을 3단계 레벨로 변환한다.
     * 0 = 기록 없음, 1 = 연함(짧게), 2 = 중간, 3 = 진함(길게).
     */
    private fun levelOf(durationSec: Long): Int = when {
        durationSec <= 0L -> 0
        durationSec < LEVEL2_SEC -> 1   // ~15분 미만
        durationSec < LEVEL3_SEC -> 2   // ~30분 미만
        else -> 3                       // 30분 이상
    }

    /** 레벨별 배경색. 3단계가 뚜렷이 구분되도록 색을 고정한다. */
    private fun levelColor(level: Int): Int = when (level) {
        1 -> LEVEL1_COLOR
        2 -> LEVEL2_COLOR
        3 -> LEVEL3_COLOR
        else -> Palette.SURFACE
    }

    private fun emptyCell(): View {
        val ctx = requireContext()
        return View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, ctx.dp(48), 1f) }
    }

    private fun renderDetail() {
        val ctx = requireContext()
        detailContainer.removeAllViews()
        val parts = selectedDateKey.split("-").map { it.toInt() }
        detailTitle.text = "%d월 %d일 기록".format(parts[1], parts[2])

        val entries = store.loadByDate(selectedDateKey)
        if (entries.isEmpty()) {
            detailContainer.addView(ctx.roundedCard(TextView(ctx).apply {
                text = "이 날의 러닝 기록이 없어요"
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }))
            return
        }
        entries.forEach { detailContainer.addView(entryCard(it)) }
    }

    private fun entryCard(e: RunHistoryEntry): View {
        val ctx = requireContext()
        val time = SIMPLE_TIME.format(Date(e.epochMillis))
        val text = TextView(ctx).apply {
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(ctx.dp(5).toFloat(), 1f)
            this.text = buildString {
                appendLine("$time 러닝")
                appendLine("거리  ${"%,.0f".format(e.totalDistanceMeter)} m")
                appendLine("시간  ${Format.duration(e.elapsedTimeSec)}")
                appendLine("평균 페이스  ${Format.pace(e.averagePaceSecPerKm)}")
                appendLine("최고 페이스  ${Format.pace(e.bestPaceSecPerKm)}")
                append("이벤트  ${e.eventCount}건")
            }
        }
        return ctx.roundedCard(text, radius = 16).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = ctx.dp(10)
        }
    }

    // ---------------------------------------------------------------- 헬퍼

    private fun changeMonth(delta: Int) {
        cal.add(Calendar.MONTH, delta)
        refresh()
    }

    /** 진하기 범례: 적게 → 많이 */
    private fun buildLegend(): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "적게 "
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            // 3단계 진하기 스와치
            listOf(1, 2, 3).forEach { lvl ->
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(14)).apply {
                        marginStart = ctx.dp(4); marginEnd = ctx.dp(4)
                    }
                    background = roundedBg(levelColor(lvl), ctx.dp(4).toFloat())
                })
            }
            addView(TextView(ctx).apply {
                text = " 많이"
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
        }
    }

    /**
     * 발표/데모용 샘플 데이터를 이번 달에 채운다. 여러 날에 서로 다른 러닝 시간을 넣어
     * 색 진하기 그라데이션을 한눈에 보여준다. 이미 채워져 있으면 추가하지 않는다.
     */
    private fun fillSampleData() {
        // 데모용: 기존 기록을 초기화하고 새로 채운다 (반복 실행해도 깔끔하게 유지).
        store.clear()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // (일자, 러닝 분) 샘플 — 거의 매일, 다양한 시간으로 진하기 그라데이션을 보여준다.
        // 일부 날은 리스트에 두 번 넣어 하루 여러 번 러닝(합산되어 더 진해짐)을 시연한다.
        // 4일·10일·19일·27일·30일은 비워 두어 "기록 없는 날"과의 대비를 보여준다.
        val samples = listOf(
            1 to 8, 2 to 6, 3 to 14, 5 to 12,
            6 to 33, 7 to 5, 8 to 18, 9 to 27,
            11 to 9, 12 to 24, 13 to 16, 14 to 25, 15 to 38,
            16 to 7, 17 to 30, 18 to 13, 20 to 15,
            21 to 42, 22 to 11, 23 to 35, 24 to 19, 25 to 28,
            26 to 22, 28 to 31, 29 to 40, 31 to 45,
            // 하루 여러 번 러닝(합산) 시연 — 6일/14일/23일에 추가 세션
            6 to 20, 14 to 15, 23 to 12,
        )
        samples.forEach { (day, minutes) ->
            if (day > maxDay) return@forEach
            // 같은 날 여러 세션은 시각을 달리해 중복 없이 누적되게 한다.
            val existingCount = store.loadByDate("%04d-%02d-%02d".format(year, month + 1, day)).size
            val hour = 8 + existingCount * 3 // 8시, 11시, 14시 …
            val c = Calendar.getInstance().apply {
                set(year, month, day, hour, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val epoch = c.timeInMillis
            val dateKey = RunHistoryStore.dateKeyOf(epoch)
            val distance = minutes * 1000.0 / 6.0 // 대략 6분/km 가정
            store.add(
                RunHistoryEntry(
                    epochMillis = epoch,
                    dateKey = dateKey,
                    totalDistanceMeter = distance,
                    elapsedTimeSec = minutes * 60L,
                    averagePaceSecPerKm = 360.0,
                    bestPaceSecPerKm = 330.0,
                    eventCount = minutes / 5,
                )
            )
        }
        refresh()
    }

    private fun navButton(text: String, onClick: () -> Unit): View {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBg(Palette.SURFACE, ctx.dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(40))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    companion object {
        private val SIMPLE_TIME = java.text.SimpleDateFormat("HH:mm", Locale.US)

        // 3단계 진하기 임계값 (초)
        private const val LEVEL2_SEC = 15 * 60L  // 15분 미만 = 연함(1), 이상 = 중간(2)
        private const val LEVEL3_SEC = 30 * 60L  // 30분 이상 = 진함(3)

        // 3단계 배경색 (뚜렷하게 구분)
        private val LEVEL1_COLOR = Color.parseColor("#1E4D3E") // 연한 어두운 초록
        private val LEVEL2_COLOR = Color.parseColor("#12A06E") // 중간 초록
        private val LEVEL3_COLOR = Color.parseColor("#00E5A0") // 밝은 초록(ACCENT)
    }
}
