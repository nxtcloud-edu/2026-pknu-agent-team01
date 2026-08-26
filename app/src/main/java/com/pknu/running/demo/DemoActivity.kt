package com.pknu.running.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pknu.running.tracking.location.FusedLocationProvider
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import kotlinx.coroutines.launch

/**
 * 기능 1(러닝 기록·상태 인식)을 눈으로 확인하기 위한 최소 데모 화면.
 *
 * - "Fake 러닝 시작": 실기기/GPS 없이 시뮬레이션으로 지표·이벤트를 관찰
 * - "실제 GPS 시작": 위치 권한 승인 후 FusedLocationProvider로 실제 러닝
 *
 * XML 리소스 없이 코드로 UI를 구성한다 (데모 목적, 의존성 최소화).
 */
class DemoActivity : ComponentActivity() {

    private val vm: DemoViewModel by viewModels()

    private lateinit var metricsView: TextView
    private lateinit var eventView: TextView
    private lateinit var summaryView: TextView

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRealRun() else metricsView.text = "위치 권한이 거부되어 실제 GPS 러닝을 시작할 수 없습니다."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        observe()
    }

    private fun buildUi(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(title("러닝 트래킹 데모"))

        metricsView = mono(16f).apply { text = "대기 중" }
        root.addView(card(metricsView))

        // 버튼 줄 1: 시작
        root.addView(
            row(
                button("Fake 러닝 시작") { vm.startFakeRun() },
                button("실제 GPS 시작") { onRealClicked() },
            )
        )
        // 버튼 줄 2: 제어
        root.addView(
            row(
                button("일시정지") { vm.pause() },
                button("재개") { vm.resume() },
                button("종료") { vm.finish() },
            )
        )

        root.addView(label("이벤트 로그"))
        eventView = mono(13f).apply { text = "(없음)" }
        val eventScroll = ScrollView(this).apply {
            addView(eventView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            )
        }
        root.addView(card(eventScroll))

        summaryView = mono(14f)
        root.addView(card(summaryView))

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.metrics.collect { m ->
                        metricsView.text = buildString {
                            appendLine("상태: ${m.state}")
                            appendLine("시간: ${DemoViewModel.formatDuration(m.elapsedTimeSec)}")
                            appendLine("거리: ${"%.0f".format(m.totalDistanceMeter)} m")
                            appendLine("현재 페이스: ${DemoViewModel.formatPace(m.currentPaceSecPerKm)}")
                            appendLine("스무딩 페이스: ${DemoViewModel.formatPace(m.smoothedPaceSecPerKm)}")
                            appendLine("평균 페이스: ${DemoViewModel.formatPace(m.averagePaceSecPerKm)}")
                            append("GPS 정확도: ${"%.0f".format(m.gpsAccuracyMeter)} m")
                        }
                    }
                }
                launch {
                    vm.eventLog.collect { log ->
                        eventView.text = if (log.isEmpty()) "(없음)" else log.joinToString("\n")
                    }
                }
                launch {
                    vm.summaryText.collect { s ->
                        summaryView.visibility = if (s == null) View.GONE else View.VISIBLE
                        summaryView.text = s ?: ""
                    }
                }
            }
        }
    }

    private fun onRealClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRealRun() else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startRealRun() {
        val provider = FusedLocationProvider(this)
        vm.startRealRun(provider, RunningTarget(distanceMeter = 5000.0, paceSecPerKm = 360.0))
    }

    // --- UI 헬퍼 (코드 뷰 구성용) ---

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun mono(size: Float) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.MONOSPACE
    }

    private fun card(child: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(Color.parseColor("#F2F2F2"))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        layoutParams = lp
        addView(child)
    }

    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        buttons.forEach { b ->
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(b)
        }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }
}
