package com.pknu.running.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit

/**
 * 앱의 단일 진입점. 하단 네비게이션(러닝/음악/캘린더)으로 3개 Fragment를 전환한다.
 *
 * show/hide 방식으로 전환하여 각 화면의 상태(러닝 진행, 음악 재생 등)를 유지한다.
 */
class MainActivity : AppCompatActivity() {

    private val runningFragment = RunningFragment()
    private val musicFragment = MusicFragment()
    private val calendarFragment = CalendarFragment()

    private lateinit var tabRunning: TextView
    private lateinit var tabMusic: TextView
    private lateinit var tabCalendar: TextView

    private var current: Fragment = runningFragment

    private val containerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(containerId, calendarFragment, "calendar").hide(calendarFragment)
                add(containerId, musicFragment, "music").hide(musicFragment)
                add(containerId, runningFragment, "running")
            }
            current = runningFragment
        } else {
            // 재생성 시 기존 인스턴스 복원
            supportFragmentManager.fragments.forEach { /* FM이 유지 */ }
        }
        selectTab(Tab.RUNNING)
    }

    private enum class Tab { RUNNING, MUSIC, CALENDAR }

    private fun selectTab(tab: Tab) {
        val target = when (tab) {
            Tab.RUNNING -> runningFragment
            Tab.MUSIC -> musicFragment
            Tab.CALENDAR -> calendarFragment
        }
        if (target !== current) {
            supportFragmentManager.commit {
                hide(current)
                show(target)
            }
            current = target
        }
        updateTabStyles(tab)
    }

    private fun updateTabStyles(tab: Tab) {
        styleTab(tabRunning, tab == Tab.RUNNING)
        styleTab(tabMusic, tab == Tab.MUSIC)
        styleTab(tabCalendar, tab == Tab.CALENDAR)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
        }

        // Fragment 컨테이너
        val container = FrameLayout(this).apply {
            id = containerId
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(container)

        // 하단 네비게이션 바
        root.addView(buildBottomNav())
        return root
    }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Palette.SURFACE)
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        tabRunning = tabItem("🏃", "러닝") { selectTab(Tab.RUNNING) }
        tabMusic = tabItem("🎵", "음악") { selectTab(Tab.MUSIC) }
        tabCalendar = tabItem("📅", "기록") { selectTab(Tab.CALENDAR) }
        bar.addView(tabRunning)
        bar.addView(tabMusic)
        bar.addView(tabCalendar)
        return bar
    }

    private fun tabItem(icon: String, label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = "$icon\n$label"
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setTextColor(if (selected) Palette.ACCENT else Palette.TEXT_SUB)
        tab.setTypeface(tab.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
